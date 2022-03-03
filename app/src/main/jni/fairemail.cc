#include <jni.h>
#include <android/log.h>
#include <cstdio>

#include <errno.h>
#include <sys/socket.h>
#include <netinet/tcp.h>
#include <sys/ioctl.h>
#include <netdb.h>

#include "compact_enc_det/compact_enc_det.h"
#include "cld_3/src/nnet_language_identifier.h"

void log_android(int prio, const char *fmt, ...) {
    if (prio >= ANDROID_LOG_DEBUG) {
        char line[1024];
        va_list argptr;
        va_start(argptr, fmt);
        vsprintf(line, fmt, argptr);
        __android_log_print(prio, "fairemail.jni", "%s", line);
        va_end(argptr);
    }
}

extern "C"
JNIEXPORT jobject JNICALL

Java_eu_faircode_email_CharsetHelper_jni_1detect_1charset(
        JNIEnv *env, jclass type,
        jbyteArray _octets, jstring _ref, jstring _lang) {
    int len = env->GetArrayLength(_octets);
    jbyte *octets = env->GetByteArrayElements(_octets, nullptr);
    const char *ref = env->GetStringUTFChars(_ref, 0);
    const char *lang = env->GetStringUTFChars(_lang, 0);

    // ISO-8859-1 is unknown
    Encoding encoding_hint;
    EncodingFromName(ref, &encoding_hint);

    Language language_hint;
    LanguageFromCode(lang, &language_hint);

    // https://github.com/google/compact_enc_det

    bool is_reliable;
    int bytes_consumed;

    Encoding encoding = CompactEncDet::DetectEncoding(
            (const char *) octets, len,
            nullptr, nullptr, nullptr,
            encoding_hint,
            language_hint,
            CompactEncDet::EMAIL_CORPUS,
            false,
            &bytes_consumed,
            &is_reliable);
    // TODO: PreferredWebOutputEncoding?
    const char *name = MimeEncodingName(encoding);

    log_android(ANDROID_LOG_DEBUG,
                "detect=%d/%s bytes=%d reliable=%d"
                " ref=%s/%s lang=%s/%s",
                encoding, name, bytes_consumed, is_reliable,
                EncodingName(encoding_hint), ref, LanguageCode(language_hint), lang);

    // https://developer.android.com/training/articles/perf-jni#primitive-arrays
    env->ReleaseByteArrayElements(_octets, octets, JNI_ABORT);
    env->ReleaseStringUTFChars(_ref, ref);
    env->ReleaseStringUTFChars(_lang, lang);

    jclass cls = env->FindClass("eu/faircode/email/CharsetHelper$DetectResult");
    jmethodID ctor = env->GetMethodID(cls, "<init>", "(Ljava/lang/String;IIZ)V");
    jstring jname = env->NewStringUTF(name);
    return env->NewObject(
            cls, ctor,
            jname,
            (jint) len,
            (jint) bytes_consumed,
            (jboolean) is_reliable);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_eu_faircode_email_TextHelper_jni_1detect_1language(
        JNIEnv *env, jclass clazz,
        jbyteArray _octets) {
    int len = env->GetArrayLength(_octets);
    jbyte *octets = env->GetByteArrayElements(_octets, nullptr);

    std::string text(reinterpret_cast<char const *>(octets), len);

    chrome_lang_id::NNetLanguageIdentifier lang_id(0, 1000);
    const chrome_lang_id::NNetLanguageIdentifier::Result result = lang_id.FindLanguage(text);

    env->ReleaseByteArrayElements(_octets, octets, JNI_ABORT);

    jclass cls = env->FindClass("eu/faircode/email/TextHelper$DetectResult");
    jmethodID ctor = env->GetMethodID(cls, "<init>", "(Ljava/lang/String;FZF)V");
    jstring jlanguage = env->NewStringUTF(result.language.c_str());
    return env->NewObject(
            cls, ctor,
            jlanguage,
            (jfloat) result.probability,
            (jint) result.is_reliable,
            (jfloat) result.is_reliable);
}

extern "C"
JNIEXPORT jint JNICALL
Java_eu_faircode_email_ConnectionHelper_jni_1socket_1keep_1alive(
        JNIEnv *env, jclass clazz,
        jint fd, jint seconds) {
    // https://tldp.org/HOWTO/html_single/TCP-Keepalive-HOWTO/#setsockopt

    int optval;
    socklen_t optlen = sizeof(optval);

    if (getsockopt(fd, SOL_TCP, TCP_KEEPCNT, &optval, &optlen) == 0)
        log_android(ANDROID_LOG_DEBUG, "Default TCP_KEEPCNT=%d", optval);
    if (getsockopt(fd, SOL_TCP, TCP_KEEPINTVL, &optval, &optlen) == 0)
        log_android(ANDROID_LOG_DEBUG, "Default TCP_KEEPINTVL=%d", optval);
    if (getsockopt(fd, SOL_TCP, TCP_KEEPIDLE, &optval, &optlen) == 0)
        log_android(ANDROID_LOG_DEBUG, "Default TCP_KEEPIDLE=%d", optval);
    if (getsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, &optval, &optlen) == 0)
        log_android(ANDROID_LOG_DEBUG, "Default SO_KEEPALIVE=%d", optval);

    int res;
    int on = 1;
    int tcp_keepalive_probes = 9;
    int tcp_keepalive_intvl = 75;
    int tcp_keepalive_time = seconds; // default 7200

    log_android(ANDROID_LOG_DEBUG, "Set TCP_KEEPCNT=%d", tcp_keepalive_probes);
    res = setsockopt(fd, SOL_TCP, TCP_KEEPCNT, &tcp_keepalive_probes, sizeof(tcp_keepalive_probes));
    if (res < 0)
        return errno;

    log_android(ANDROID_LOG_DEBUG, "Set TCP_KEEPINTVL=%d", tcp_keepalive_intvl);
    res = setsockopt(fd, SOL_TCP, TCP_KEEPINTVL, &tcp_keepalive_intvl, sizeof(tcp_keepalive_intvl));
    if (res < 0)
        return errno;

    log_android(ANDROID_LOG_DEBUG, "Set TCP_KEEPIDLE=%d", tcp_keepalive_time);
    res = setsockopt(fd, SOL_TCP, TCP_KEEPIDLE, &tcp_keepalive_time, sizeof(tcp_keepalive_time));
    if (res < 0)
        return errno;

    log_android(ANDROID_LOG_DEBUG, "Set SO_KEEPALIVE=%d", on);
    res = setsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, &on, sizeof(on));
    if (res < 0)
        return errno;

    if (getsockopt(fd, SOL_TCP, TCP_KEEPCNT, &optval, &optlen) == 0)
        log_android(ANDROID_LOG_DEBUG, "Check TCP_KEEPCNT=%d", optval);
    if (getsockopt(fd, SOL_TCP, TCP_KEEPINTVL, &optval, &optlen) == 0)
        log_android(ANDROID_LOG_DEBUG, "Check TCP_KEEPINTVL=%d", optval);
    if (getsockopt(fd, SOL_TCP, TCP_KEEPIDLE, &optval, &optlen) == 0)
        log_android(ANDROID_LOG_DEBUG, "Check TCP_KEEPIDLE=%d", optval);
    if (getsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, &optval, &optlen) == 0)
        log_android(ANDROID_LOG_DEBUG, "Check SO_KEEPALIVE=%d", optval);

    return res;
}

extern "C"
JNIEXPORT jint JNICALL
Java_eu_faircode_email_ConnectionHelper_jni_1socket_1get_1send_1buffer(
        JNIEnv *env, jclass clazz,
        jint fd) {
    int queued = 0;
    int res = ioctl(fd, TIOCOUTQ, &queued);
    if (res != 0)
        log_android(ANDROID_LOG_DEBUG, "ioctl(TIOCOUTQ) res=%d queued=%d", res, queued);
    return (res == 0 ? queued : 0);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_eu_faircode_email_ConnectionHelper_jni_1is_1numeric_1address(
        JNIEnv *env, jclass clazz,
        jstring _ip) {
    jboolean numeric = 0;
    const char *ip = env->GetStringUTFChars(_ip, 0);

    // https://linux.die.net/man/3/getaddrinfo
    struct addrinfo hints;
    memset(&hints, 0, sizeof(struct addrinfo));
    hints.ai_family = AF_UNSPEC;
    hints.ai_flags = AI_NUMERICHOST; // suppresses any potentially lengthy network host address lookups
    struct addrinfo *result;
    int err = getaddrinfo(ip, nullptr, &hints, &result);
    if (err)
        log_android(ANDROID_LOG_DEBUG, "getaddrinfo(%s) error %d: %s", ip, err, gai_strerror(err));
    else
        numeric = (jboolean) (result != nullptr);

    if (result != nullptr)
        freeaddrinfo(result);

    env->ReleaseStringUTFChars(_ip, ip);
    return numeric;
}
