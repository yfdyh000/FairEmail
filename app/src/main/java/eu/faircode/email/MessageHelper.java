package eu.faircode.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018-2022 by Marcel Bokhorst (M66B)
*/

import static android.system.OsConstants.ENOSPC;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.system.ErrnoException;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.net.MailTo;
import androidx.core.util.PatternsCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import com.sun.mail.gimap.GmailMessage;
import com.sun.mail.iap.ProtocolException;
import com.sun.mail.iap.Response;
import com.sun.mail.imap.IMAPBodyPart;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.Utility;
import com.sun.mail.imap.protocol.IMAPProtocol;
import com.sun.mail.imap.protocol.MessageSet;
import com.sun.mail.util.ASCIIUtility;
import com.sun.mail.util.BASE64DecoderStream;
import com.sun.mail.util.FolderClosedIOException;
import com.sun.mail.util.MessageRemovedIOException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.simplejavamail.outlookmessageparser.OutlookMessageParser;
import org.simplejavamail.outlookmessageparser.model.OutlookAttachment;
import org.simplejavamail.outlookmessageparser.model.OutlookFileAttachment;
import org.simplejavamail.outlookmessageparser.model.OutlookMessage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.IDN;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.activation.FileTypeMap;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.FolderClosedException;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessageRemovedException;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.ContentType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MailDateFormat;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePart;
import javax.mail.internet.MimeUtility;
import javax.mail.internet.ParameterList;
import javax.mail.internet.ParseException;

import biweekly.Biweekly;
import biweekly.ICalendar;

public class MessageHelper {
    private boolean ensuredEnvelope = false;
    private boolean ensuredHeaders = false;
    private boolean ensuredStructure = false;
    private MimeMessage imessage;
    private String hash = null;
    private String threadId = null;
    private InternetHeaders reportHeaders = null;

    private static File cacheDir = null;

    static final int SMALL_MESSAGE_SIZE = 192 * 1024; // bytes
    static final int DEFAULT_DOWNLOAD_SIZE = 4 * 1024 * 1024; // bytes
    static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
    static final int MAX_SUBJECT_AGE = 48; // hours

    static final List<String> RECEIVED_WORDS = Collections.unmodifiableList(Arrays.asList(
            "from", "by", "via", "with", "id", "for"
    ));

    private static final int MAX_HEADER_LENGTH = 998;
    private static final int MAX_MESSAGE_SIZE = 10 * 1024 * 1024; // bytes
    private static final long ATTACHMENT_PROGRESS_UPDATE = 1500L; // milliseconds
    private static final int MAX_META_EXCERPT = 1024; // characters
    private static final int FORMAT_FLOWED_LINE_LENGTH = 72; // characters
    private static final int MAX_DIAGNOSTIC = 250; // characters

    private static final String DOCTYPE = "<!DOCTYPE";
    private static final String HTML_START = "<html>";
    private static final String HTML_END = "</html>";
    private static final String SMTP_MAILFORM = "smtp.mailfrom";

    private static final List<Charset> CHARSET16 = Collections.unmodifiableList(Arrays.asList(
            StandardCharsets.UTF_16,
            StandardCharsets.UTF_16BE,
            StandardCharsets.UTF_16LE
    ));

    private static final List<String> DO_NOT_REPLY = Collections.unmodifiableList(Arrays.asList(
            "noreply",
            "no.reply",
            "no-reply",
            "donotreply",
            "do.not.reply",
            "do-not-reply"
    ));

    static final String FLAG_FORWARDED = "$Forwarded";
    static final String FLAG_JUNK = "$Junk";
    static final String FLAG_NOT_JUNK = "$NotJunk";
    static final String FLAG_CLASSIFIED = "$Classified";
    static final String FLAG_FILTERED = "$Filtered";
    static final String FLAG_DELIVERED = "$Delivered";
    static final String FLAG_NOT_DELIVERED = "$NotDelivered";
    static final String FLAG_DISPLAYED = "$Displayed";
    static final String FLAG_NOT_DISPLAYED = "$NotDisplayed";
    static final String FLAG_LOW_IMPORTANCE = "$LowImportance";
    static final String FLAG_HIGH_IMPORTANCE = "$HighImportance";

    // https://www.iana.org/assignments/imap-jmap-keywords/imap-jmap-keywords.xhtml
    // Not black listed: Gmail $Phishing
    private static final List<String> FLAG_BLACKLIST = Collections.unmodifiableList(Arrays.asList(
            FLAG_FORWARDED,
            FLAG_JUNK,
            FLAG_NOT_JUNK,
            FLAG_CLASSIFIED, // FairEmail
            FLAG_FILTERED, // FairEmail
            FLAG_LOW_IMPORTANCE, // FairEmail
            FLAG_HIGH_IMPORTANCE, // FairEmail
            "Sent",
            "$MDNSent", // https://tools.ietf.org/html/rfc3503
            "$SubmitPending",
            "$Submitted",
            "Junk",
            "NonJunk",
            "$recent",
            "DTAG_document",
            "DTAG_image",
            "$X-Me-Annot-1",
            "$X-Me-Annot-2",
            "\\Unseen", // Mail.ru
            "$sent", // Kmail
            "$attachment", // Kmail
            "$signed", // Kmail
            "$encrypted", // Kmail
            "$HasAttachment", // Dovecot
            "$HasNoAttachment", // Dovecot
            "$IsTrusted", // Fastmail
            "$X-ME-Annot-2" // Fastmail
    ));

    // https://tools.ietf.org/html/rfc4021

    static void setSystemProperties(Context context) {
        System.setProperty("mail.mime.decodetext.strict", "false");

        // https://docs.oracle.com/javaee/6/api/javax/mail/internet/package-summary.html
        System.setProperty("mail.mime.ignoreunknownencoding", "true"); // Content-Transfer-Encoding
        System.setProperty("mail.mime.base64.ignoreerrors", "true");
        System.setProperty("mail.mime.decodefilename", "true");
        System.setProperty("mail.mime.encodefilename", "false");
        System.setProperty("mail.mime.decodeparameters", "true");
        System.setProperty("mail.mime.encodeparameters", "true");
        System.setProperty("mail.mime.allowutf8", "false"); // InternetAddress, MimeBodyPart, MimeUtility
        System.setProperty("mail.mime.cachemultipart", "false");

        // https://docs.oracle.com/javaee/6/api/javax/mail/internet/MimeMultipart.html
        System.setProperty("mail.mime.multipart.ignoremissingboundaryparameter", "true"); // default true, javax.mail.internet.ParseException: In parameter list
        System.setProperty("mail.mime.multipart.ignoreexistingboundaryparameter", "true"); // default false
        System.setProperty("mail.mime.multipart.ignoremissingendboundary", "true"); // default true
        System.setProperty("mail.mime.multipart.allowempty", "true"); // default false
        System.setProperty("mail.mime.contentdisposition.strict", "false"); // default true
        //System.setProperty("mail.mime.contenttypehandler", "eu.faircode.email.ContentTypeHandler");

        //System.setProperty("mail.imap.parse.debug", "true");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean uid_command = prefs.getBoolean("uid_command", false);
        System.setProperty("fairemail.uid_command", Boolean.toString(uid_command));
    }

    static Properties getSessionProperties() {
        Properties props = new Properties();

        // MIME
        props.put("mail.mime.allowutf8", "false"); // SMTPTransport, MimeMessage
        props.put("mail.mime.address.strict", "false");

        return props;
    }

    static MimeMessageEx from(Context context, EntityMessage message, EntityIdentity identity, Session isession, boolean send)
            throws MessagingException, IOException {
        DB db = DB.getInstance(context);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int receipt_type = prefs.getInt("receipt_type", 2);
        boolean receipt_legacy = prefs.getBoolean("receipt_legacy", false);
        boolean hide_timezone = prefs.getBoolean("hide_timezone", true);
        boolean autocrypt = prefs.getBoolean("autocrypt", true);
        boolean mutual = prefs.getBoolean("autocrypt_mutual", true);
        boolean encrypt_subject = prefs.getBoolean("encrypt_subject", false);

        Map<String, String> c = new HashMap<>();
        c.put("id", message.id == null ? null : Long.toString(message.id));
        c.put("encrypt", message.encrypt + "/" + message.ui_encrypt);
        Log.breadcrumb("Build message", c);

        MimeMessageEx imessage = new MimeMessageEx(isession, message.msgid);

        // Flags
        imessage.setFlag(Flags.Flag.SEEN, message.seen);
        imessage.setFlag(Flags.Flag.FLAGGED, message.flagged);
        imessage.setFlag(Flags.Flag.ANSWERED, message.answered);

        // Priority
        if (EntityMessage.PRIORITIY_LOW.equals(message.priority)) {
            // Low
            imessage.addHeader("Importance", "Low");
            imessage.addHeader("Priority", "Non-Urgent");
            imessage.addHeader("X-Priority", "5"); // Lowest
            //imessage.addHeader("X-MSMail-Priority", "Low");
            // SpamAssassin Rule: MISSING_MIMEOLE
            // Standard description: Message has X-MSMail-Priority, but no X-MimeOLE
            // Explanation: The message is pretending to be generated by a Microsoft email program
            // which uses the extension header X-MSMail-Priority,
            // but is missing the extension header X-MimeOLE which is characteristic of Microsoft email.
            // This suggests that the sender is using badly-written mailout software,
            // rather than a genuine Microsoft email program.
        } else if (EntityMessage.PRIORITIY_HIGH.equals(message.priority)) {
            // High
            imessage.addHeader("Importance", "High");
            imessage.addHeader("Priority", "Urgent");
            imessage.addHeader("X-Priority", "1"); // Highest
            //imessage.addHeader("X-MSMail-Priority", "High");
        }

        // Sensitivity
        if (EntityMessage.SENSITIVITY_PERSONAL.equals(message.sensitivity))
            imessage.addHeader("Sensitivity", "Personal");
        else if (EntityMessage.SENSITIVITY_PRIVATE.equals(message.sensitivity))
            imessage.addHeader("Sensitivity", "Private");
        else if (EntityMessage.SENSITIVITY_CONFIDENTIAL.equals(message.sensitivity))
            imessage.addHeader("Sensitivity", "Company-Confidential");

        // References
        if (message.references != null) {
            // https://tools.ietf.org/html/rfc5322#section-2.1.1
            // Each line of characters MUST be no more than 998 characters ... , excluding the CRLF.
            String references = message.references;
            int maxlen = MAX_HEADER_LENGTH - "References: ".length();
            int sp = references.indexOf(' ');
            while (references.length() > maxlen && sp > 0) {
                Log.i("Dropping reference=" + references.substring(0, sp));
                references = references.substring(sp);
                sp = references.indexOf(' ');
            }
            imessage.addHeader("References", references);
        }
        if (message.inreplyto != null)
            imessage.addHeader("In-Reply-To", message.inreplyto);
        imessage.addHeader(HEADER_CORRELATION_ID, message.msgid);

        MailDateFormat mdf = new MailDateFormat();
        mdf.setTimeZone(hide_timezone ? TimeZone.getTimeZone("UTC") : TimeZone.getDefault());
        String ourDate = mdf.format(new Date(message.sent == null ? message.received : message.sent));

        Address ourFrom = null;
        if (message.from != null && message.from.length > 0)
            ourFrom = getFrom(message, identity);

        if (message.headers == null || !Boolean.TRUE.equals(message.resend)) {
            imessage.setHeader("Date", ourDate);

            // Addresses
            if (ourFrom != null)
                imessage.setFrom(ourFrom);

            if (message.to != null && message.to.length > 0)
                imessage.setRecipients(Message.RecipientType.TO, convertAddress(message.to, identity));

            if (message.cc != null && message.cc.length > 0)
                imessage.setRecipients(Message.RecipientType.CC, convertAddress(message.cc, identity));

            if (message.bcc != null && message.bcc.length > 0)
                imessage.setRecipients(Message.RecipientType.BCC, convertAddress(message.bcc, identity));
        } else {
            // https://datatracker.ietf.org/doc/html/rfc2822#section-3.6.6
            ByteArrayInputStream bis = new ByteArrayInputStream(message.headers.getBytes());
            List<Header> headers = Collections.list(new InternetHeaders(bis).getAllHeaders());

            for (Header header : headers)
                try {
                    String name = header.getName();
                    String value = header.getValue();
                    if (name == null || TextUtils.isEmpty(value))
                        continue;

                    switch (name.toLowerCase(Locale.ROOT)) {
                        case "date":
                            imessage.setHeader("Date", value);
                            break;
                        case "from":
                            imessage.setFrom(value);
                            break;
                        case "to":
                            imessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(value));
                            break;
                        case "cc":
                            imessage.setRecipients(Message.RecipientType.CC, InternetAddress.parse(value));
                            break;
                        case "bcc":
                            imessage.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(value));
                            break;
                        case "reply-to":
                            imessage.setReplyTo(InternetAddress.parse(value));
                            break;
                        case "message-id":
                            if (send) {
                                imessage.setHeader("Resent-Message-ID", message.msgid);
                                imessage.updateMessageID(value);
                            }
                            break;
                        case "references":
                            imessage.setHeader("References", value);
                            break;
                        case "in-reply-to":
                            imessage.setHeader("In-Reply-To", value);
                            break;
                        // Resent-Sender (=on behalf of)
                    }
                } catch (Throwable ex) {
                    Log.e(ex);
                }

            // The "Resent-Date:" indicates the date and time at which the resent
            //   message is dispatched by the resender of the message.
            imessage.addHeader("Resent-Date", ourDate);

            // a simple "Resent-From:" form which
            //   contains the mailbox of the individual doing the resending
            if (ourFrom != null)
                imessage.addHeader("Resent-From", ourFrom.toString());

            // The "Resent-To:", "Resent-Cc:", and "Resent-Bcc:" fields function
            //   identically to the "To:", "Cc:", and "Bcc:" fields respectively,
            //   except that they indicate the recipients of the resent message, not
            //   the recipients of the original message.
            if (message.to != null && message.to.length > 0)
                imessage.addHeader("Resent-To", InternetAddress.toString(message.to));

            if (message.cc != null && message.cc.length > 0)
                imessage.addHeader("Resent-Cc", InternetAddress.toString(message.cc));

            if (message.bcc != null && message.bcc.length > 0)
                imessage.addHeader("Resent-Bcc", InternetAddress.toString(message.bcc));

            // Each new set of resent fields is prepended to the message;
            //   that is, the most recent set of resent fields appear earlier in the message.
            for (Header header : headers) {
                String name = header.getName();
                String value = header.getValue();
                if (name == null || TextUtils.isEmpty(value))
                    continue;
                if (name.toLowerCase(Locale.ROOT).startsWith("resent-"))
                    imessage.addHeader(name, value);
            }
        }

        if (message.subject != null) {
            int maxlen = MAX_HEADER_LENGTH - "Subject: ".length();
            if (message.subject.length() > maxlen)
                message.subject = message.subject.substring(0, maxlen - 4) + " ...";
            imessage.setSubject(message.subject);
        }

        // Send message
        if (identity != null) {
            if ((message.headers == null || !Boolean.TRUE.equals(message.resend)) &&
                    (message.dsn == null || EntityMessage.DSN_NONE.equals(message.dsn))) {
                // Add reply to
                if (identity.replyto != null)
                    imessage.setReplyTo(convertAddress(InternetAddress.parse(identity.replyto), identity));

                // Add extra cc
                if (identity.cc != null)
                    addAddress(identity.cc, Message.RecipientType.CC, imessage, identity);

                // Add extra bcc
                if (identity.bcc != null)
                    addAddress(identity.bcc, Message.RecipientType.BCC, imessage, identity);
            }

            // Delivery/read request
            if (message.receipt_request != null && message.receipt_request) {
                String to = (identity.replyto == null ? identity.email : identity.replyto);

                // 0=Read receipt
                // 1=Delivery receipt
                // 2=Read+delivery receipt

                // defacto standard
                if (receipt_type == 1 || receipt_type == 2) {
                    // Delivery receipt
                    if (receipt_legacy)
                        imessage.addHeader("Return-Receipt-To", to);
                }

                // https://tools.ietf.org/html/rfc3798
                if (receipt_type == 0 || receipt_type == 2) {
                    // Read receipt
                    imessage.addHeader("Disposition-Notification-To", to);
                    imessage.addHeader("Read-Receipt-To", to);
                    imessage.addHeader("X-Confirm-Reading-To", to);
                }
            }
        }

        // Auto answer
        if (message.unsubscribe != null)
            imessage.addHeader("List-Unsubscribe", "<" + message.unsubscribe + ">");

        if (message.auto_submitted != null && message.auto_submitted)
            imessage.addHeader("Auto-Submitted", "auto-replied");

        List<EntityAttachment> attachments = db.attachment().getAttachments(message.id);

        if (message.dsn == null || EntityMessage.DSN_NONE.equals(message.dsn)) {
            if (message.from != null && message.from.length > 0)
                for (EntityAttachment attachment : attachments)
                    if (EntityAttachment.PGP_KEY.equals(attachment.encryption)) {
                        InternetAddress from = (InternetAddress) message.from[0];

                        if (autocrypt) {
                            String mode = (mutual ? "mutual" : "nopreference");

                            StringBuilder sb = new StringBuilder();
                            File file = attachment.getFile(context);
                            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                                String line = br.readLine();
                                while (line != null) {
                                    String data = null;
                                    if (line.length() > 0 &&
                                            !line.startsWith("-----BEGIN ") &&
                                            !line.startsWith("-----END "))
                                        data = line;

                                    line = br.readLine();

                                    // https://www.w3.org/Protocols/rfc822/3_Lexical.html#z0
                                    if (data != null &&
                                            line != null && !line.startsWith("-----END "))
                                        sb.append("\r\n ").append(data);
                                }
                            }

                            // https://autocrypt.org/level1.html#the-autocrypt-header
                            imessage.addHeader("Autocrypt",
                                    "addr=" + from.getAddress() + ";" +
                                            " prefer-encrypt=" + mode + ";" +
                                            " keydata=" + sb.toString());
                        }
                    }

            // PGP: https://tools.ietf.org/html/rfc3156
            // S/MIME: https://tools.ietf.org/html/rfc8551
            for (final EntityAttachment attachment : attachments)
                if (EntityAttachment.PGP_SIGNATURE.equals(attachment.encryption)) {
                    Log.i("Sending PGP signed message");

                    for (final EntityAttachment content : attachments)
                        if (EntityAttachment.PGP_CONTENT.equals(content.encryption)) {
                            BodyPart bpContent = new MimeBodyPart(new FileInputStream(content.getFile(context)));

                            final ContentType cts = new ContentType(attachment.type);
                            String micalg = cts.getParameter("micalg");
                            if (TextUtils.isEmpty(micalg)) {
                                // Some providers strip parameters
                                // https://tools.ietf.org/html/rfc3156#section-5
                                Log.w("PGP micalg missing type=" + attachment.type);
                            }
                            ParameterList params = cts.getParameterList();
                            if (params != null)
                                params.remove("micalg");
                            cts.setParameterList(params);

                            // Build signature
                            BodyPart bpSignature = new MimeBodyPart();
                            bpSignature.setFileName(attachment.name);
                            FileDataSource dsSignature = new FileDataSource(attachment.getFile(context));
                            dsSignature.setFileTypeMap(new FileTypeMap() {
                                @Override
                                public String getContentType(File file) {
                                    return cts.toString();
                                }

                                @Override
                                public String getContentType(String filename) {
                                    return cts.toString();
                                }
                            });
                            bpSignature.setDataHandler(new DataHandler(dsSignature));
                            bpSignature.setDisposition(Part.INLINE);

                            // Build message
                            ContentType ct = new ContentType("multipart/signed");
                            if (micalg != null)
                                ct.setParameter("micalg", micalg);
                            ct.setParameter("protocol", "application/pgp-signature");
                            String ctx = ct.toString();
                            int slash = ctx.indexOf("/");
                            Multipart multipart = new MimeMultipart(ctx.substring(slash + 1));
                            multipart.addBodyPart(bpContent);
                            multipart.addBodyPart(bpSignature);
                            imessage.setContent(multipart);

                            return imessage;
                        }
                    throw new IllegalStateException("PGP content not found");
                } else if (EntityAttachment.PGP_MESSAGE.equals(attachment.encryption)) {
                    Log.i("Sending PGP encrypted message");

                    // Build header
                    // https://tools.ietf.org/html/rfc3156
                    BodyPart bpHeader = new MimeBodyPart();
                    bpHeader.setContent("Version: 1\n", "application/pgp-encrypted");

                    // Build content
                    BodyPart bpContent = new MimeBodyPart();
                    bpContent.setFileName(attachment.name);
                    FileDataSource dsContent = new FileDataSource(attachment.getFile(context));
                    dsContent.setFileTypeMap(new FileTypeMap() {
                        @Override
                        public String getContentType(File file) {
                            return attachment.type;
                        }

                        @Override
                        public String getContentType(String filename) {
                            return attachment.type;
                        }
                    });
                    bpContent.setDataHandler(new DataHandler(dsContent));
                    bpContent.setDisposition(Part.INLINE);

                    // Build message
                    ContentType ct = new ContentType("multipart/encrypted");
                    ct.setParameter("protocol", "application/pgp-encrypted");
                    String ctx = ct.toString();
                    int slash = ctx.indexOf("/");
                    Multipart multipart = new MimeMultipart(ctx.substring(slash + 1));
                    multipart.addBodyPart(bpHeader);
                    multipart.addBodyPart(bpContent);
                    imessage.setContent(multipart);

                    if (encrypt_subject)
                        imessage.setSubject("...");

                    return imessage;
                } else if (EntityAttachment.SMIME_SIGNATURE.equals(attachment.encryption)) {
                    Log.i("Sending S/MIME signed message");

                    for (final EntityAttachment content : attachments)
                        if (EntityAttachment.SMIME_CONTENT.equals(content.encryption)) {
                            BodyPart bpContent = new MimeBodyPart(new FileInputStream(content.getFile(context)));

                            final ContentType cts = new ContentType(attachment.type);
                            String micalg = cts.getParameter("micalg");
                            if (TextUtils.isEmpty(micalg)) {
                                // Some providers strip parameters
                                Log.w("S/MIME micalg missing type=" + attachment.type);
                                micalg = "sha-256";
                            }
                            ParameterList params = cts.getParameterList();
                            if (params != null)
                                params.remove("micalg");
                            cts.setParameterList(params);

                            // Build signature
                            BodyPart bpSignature = new MimeBodyPart();
                            bpSignature.setFileName(attachment.name);
                            FileDataSource dsSignature = new FileDataSource(attachment.getFile(context));
                            dsSignature.setFileTypeMap(new FileTypeMap() {
                                @Override
                                public String getContentType(File file) {
                                    return cts.toString();
                                }

                                @Override
                                public String getContentType(String filename) {
                                    return cts.toString();
                                }
                            });
                            bpSignature.setDataHandler(new DataHandler(dsSignature));
                            bpSignature.setDisposition(Part.INLINE);

                            // Build message
                            ContentType ct = new ContentType("multipart/signed");
                            ct.setParameter("micalg", micalg);
                            ct.setParameter("protocol", "application/pkcs7-signature");
                            ct.setParameter("smime-type", "signed-data");
                            String ctx = ct.toString();
                            int slash = ctx.indexOf("/");
                            Multipart multipart = new MimeMultipart(ctx.substring(slash + 1));
                            multipart.addBodyPart(bpContent);
                            multipart.addBodyPart(bpSignature);
                            imessage.setContent(multipart);

                            return imessage;
                        }
                    throw new IllegalStateException("S/MIME content not found");
                } else if (EntityAttachment.SMIME_MESSAGE.equals(attachment.encryption)) {
                    Log.i("Sending S/MIME encrypted message");

                    // Build message
                    imessage.setDisposition(Part.ATTACHMENT);
                    imessage.setFileName(attachment.name);
                    imessage.setDescription("S/MIME Encrypted Message");

                    ContentType ct = new ContentType("application/pkcs7-mime");
                    ct.setParameter("name", attachment.name);
                    ct.setParameter("smime-type", "enveloped-data");

                    File file = attachment.getFile(context);
                    FileDataSource dataSource = new FileDataSource(file);
                    dataSource.setFileTypeMap(new FileTypeMap() {
                        @Override
                        public String getContentType(File file) {
                            return ct.toString();
                        }

                        @Override
                        public String getContentType(String filename) {
                            return ct.toString();
                        }
                    });

                    imessage.setDataHandler(new DataHandler(dataSource));

                    return imessage;
                }

            if (EntityMessage.PGP_SIGNENCRYPT.equals(message.ui_encrypt) ||
                    EntityMessage.SMIME_SIGNENCRYPT.equals(message.ui_encrypt)) {
                String msg = "Storing unencrypted message" +
                        " encrypt=" + message.encrypt + "/" + message.ui_encrypt;
                Log.w(msg);
                throw new IllegalArgumentException(msg);
            }
        }

        build(context, message, attachments, identity, send, imessage);

        return imessage;
    }

    static Address getFrom(EntityMessage message, EntityIdentity identity) throws UnsupportedEncodingException {
        InternetAddress from = ((InternetAddress) message.from[0]);
        String email = from.getAddress();
        String name = from.getPersonal();

        if (identity != null && identity.sender_extra &&
                email != null && message.extra != null) {
            String username = UriHelper.getEmailUser(identity.email);
            if (!message.extra.equals(username)) {
                email = addExtra(email, message.extra);

                if (!identity.sender_extra_name)
                    name = null;

                Log.i("extra=\"" + name + "\" <" + email + ">");
            }
        }

        if (EntityMessage.DSN_HARD_BOUNCE.equals(message.dsn))
            name = null;

        return new InternetAddress(email, name, StandardCharsets.UTF_8.name());
    }

    static String addExtra(String email, String extra) {
        int at = email.indexOf('@');
        if (at < 0)
            return email;

        if (extra.length() > 1 && extra.startsWith("+"))
            email = email.substring(0, at) + extra + email.substring(at);
        else if (extra.length() > 1 && extra.startsWith("@"))
            email = email.substring(0, at) + extra + '.' + email.substring(at + 1);
        else
            email = extra + email.substring(at);

        return email;
    }

    private static void addAddress(String email, Message.RecipientType type, MimeMessage imessage, EntityIdentity identity) throws MessagingException {
        List<Address> result = new ArrayList<>();

        Address[] existing = imessage.getRecipients(type);
        if (existing != null)
            result.addAll(Arrays.asList(existing));

        Address[] all = imessage.getAllRecipients(); // to, cc, bcc
        Address[] addresses = convertAddress(InternetAddress.parse(email), identity);
        for (Address address : addresses) {
            boolean found = false;
            if (all != null)
                for (Address a : all)
                    if (equalEmail(a, address)) {
                        found = true;
                        break;
                    }
            if (!found)
                result.add(address);
        }

        imessage.setRecipients(type, result.toArray(new Address[0]));
    }

    private static Address[] convertAddress(Address[] addresses, EntityIdentity identity) {
        if (identity != null && identity.unicode)
            return addresses;

        // https://en.wikipedia.org/wiki/International_email
        for (Address address : addresses) {
            String email = ((InternetAddress) address).getAddress();
            email = punyCode(email);
            ((InternetAddress) address).setAddress(email);
        }
        return addresses;
    }

    static void build(Context context, EntityMessage message, List<EntityAttachment> attachments, EntityIdentity identity, boolean send, MimeMessage imessage) throws IOException, MessagingException {
        if (EntityMessage.DSN_RECEIPT.equals(message.dsn)) {
            // https://www.ietf.org/rfc/rfc3798.txt
            Multipart report = new MimeMultipart("report; report-type=disposition-notification");

            String html = Helper.readText(message.getFile(context));
            String plainContent = HtmlHelper.getText(context, html);

            BodyPart plainPart = new MimeBodyPart();
            plainPart.setContent(plainContent, "text/plain; charset=" + Charset.defaultCharset().name());
            report.addBodyPart(plainPart);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean client_id = prefs.getBoolean("client_id", true);

            String from = null;
            if (message.from != null && message.from.length > 0)
                from = ((InternetAddress) message.from[0]).getAddress();

            StringBuilder sb = new StringBuilder();

            sb.append("Reporting-UA: ");
            if (client_id)
                sb.append(BuildConfig.APPLICATION_ID).append("; ")
                        .append(context.getString(R.string.app_name)).append(' ')
                        .append(BuildConfig.VERSION_NAME).append("\r\n");
            else
                sb.append("example.com").append("\r\n");

            if (from != null)
                sb.append("Original-Recipient: rfc822;").append(from).append("\r\n");

            sb.append("Disposition: manual-action/MDN-sent-manually; displayed").append("\r\n");

            BodyPart dnsPart = new MimeBodyPart();
            dnsPart.setContent(sb.toString(), "message/disposition-notification; name=\"MDNPart2.txt\"");
            dnsPart.setDisposition(Part.INLINE);
            report.addBodyPart(dnsPart);

            //BodyPart headersPart = new MimeBodyPart();
            //headersPart.setContent("", "text/rfc822-headers; name=\"MDNPart3.txt\"");
            //headersPart.setDisposition(Part.INLINE);
            //report.addBodyPart(headersPart);

            imessage.setContent(report);
            return;
        } else if (EntityMessage.DSN_HARD_BOUNCE.equals(message.dsn)) {
            // https://tools.ietf.org/html/rfc3464
            Multipart report = new MimeMultipart("report; report-type=delivery-status");

            String html = Helper.readText(message.getFile(context));
            String plainContent = HtmlHelper.getText(context, html);

            BodyPart plainPart = new MimeBodyPart();
            plainPart.setContent(plainContent, "text/plain; charset=" + Charset.defaultCharset().name());
            report.addBodyPart(plainPart);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean client_id = prefs.getBoolean("client_id", true);

            String from = null;
            if (message.from != null && message.from.length > 0)
                from = ((InternetAddress) message.from[0]).getAddress();

            StringBuilder sb = new StringBuilder();
            sb.append("Reporting-MTA: dns;");
            if (client_id)
                sb.append(EmailService.getDefaultEhlo()).append("\r\n");
            else
                sb.append("example.com").append("\r\n");
            sb.append("\r\n");

            if (from != null)
                sb.append("Final-Recipient: rfc822;").append(from).append("\r\n");

            sb.append("Action: failed").append("\r\n");
            sb.append("Status: 5.1.1").append("\r\n"); // https://tools.ietf.org/html/rfc3463
            sb.append("Diagnostic-Code: smtp; 550 user unknown").append("\r\n");

            MailDateFormat mdf = new MailDateFormat();
            mdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            sb.append("Last-Attempt-Date: ").append(mdf.format(message.received)).append("\r\n");

            BodyPart dnsPart = new MimeBodyPart();
            dnsPart.setContent(sb.toString(), "message/delivery-status");
            dnsPart.setDisposition(Part.INLINE);
            report.addBodyPart(dnsPart);

            imessage.setContent(report);
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean format_flowed = prefs.getBoolean("format_flowed", false);
        String compose_font = prefs.getString("compose_font", "");
        boolean auto_link = prefs.getBoolean("auto_link", false);

        // Build html body
        Document document = JsoupEx.parse(message.getFile(context));

        if (message.headers != null && Boolean.TRUE.equals(message.resend)) {
            Element body = document.body();
            if (body.children().size() == 1) {
                // Restore original body
                Element ref = body.children().get(0);
                body.replaceWith(ref.tagName("body").removeAttr("fairemail"));
            }
        }

        // https://developer.mozilla.org/en-US/docs/Web/HTML/Global_attributes/lang
        if (message.language != null)
            document.body().attr("lang", message.language);

        // When sending message
        if (identity != null && send) {
            if (auto_link)
                HtmlHelper.autoLink(document);

            if (!TextUtils.isEmpty(compose_font)) {
                List<Node> childs = new ArrayList<>();
                for (Node child : document.body().childNodes())
                    if (TextUtils.isEmpty(child.attr("fairemail"))) {
                        childs.add(child);
                        child.remove();
                    } else
                        break;

                Element div = document.createElement("div").attr("style",
                        "font-family:" + StyleHelper.getFamily(compose_font));
                for (Node child : childs)
                    div.appendChild(child);
                document.body().prependChild(div);
            }

            document.select("div[fairemail=signature]").removeAttr("fairemail");
            document.select("div[fairemail=reference]").removeAttr("fairemail");

            Elements reply = document.select("div[fairemail=reply]");
            if (message.isPlainOnly())
                reply.select("strong").tagName("span");
            reply.removeAttr("fairemail");

            DB db = DB.getInstance(context);
            try {
                db.beginTransaction();

                for (Element img : document.select("img")) {
                    String source = img.attr("src");
                    if (!source.startsWith("content:"))
                        continue;

                    Uri uri = Uri.parse(source);
                    DocumentFile dfile = DocumentFile.fromSingleUri(context, uri);
                    if (dfile == null)
                        continue;

                    String name = dfile.getName();
                    String type = dfile.getType();

                    if (TextUtils.isEmpty(name))
                        name = uri.getLastPathSegment();
                    if (TextUtils.isEmpty(type))
                        type = "image/*";

                    String cid = BuildConfig.APPLICATION_ID + ".content." + Math.abs(source.hashCode());
                    String acid = "<" + cid + ">";

                    if (db.attachment().getAttachment(message.id, acid) == null) {
                        EntityAttachment attachment = new EntityAttachment();
                        attachment.message = message.id;
                        attachment.sequence = db.attachment().getAttachmentSequence(message.id) + 1;
                        attachment.name = name;
                        attachment.type = type;
                        attachment.disposition = Part.INLINE;
                        attachment.cid = acid;
                        attachment.related = true;
                        attachment.size = null;
                        attachment.progress = 0;
                        attachment.id = db.attachment().insertAttachment(attachment);

                        attachment.size = Helper.copy(context, uri, attachment.getFile(context));
                        attachment.progress = null;
                        attachment.available = true;
                        db.attachment().setDownloaded(attachment.id, attachment.size);

                        attachments.add(attachment);
                    }

                    img.attr("src", "cid:" + cid);
                }

                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.w(ex);
            } finally {
                db.endTransaction();
            }
        }

        // multipart/mixed
        //   multipart/related
        //     multipart/alternative
        //       text/plain
        //       text/html
        //     inlines
        //  attachments

        String htmlContent = document.html();
        String htmlContentType = "text/html; charset=" + Charset.defaultCharset().name();

        String plainContent = HtmlHelper.getText(context, document.html());
        String plainContentType = "text/plain; charset=" + Charset.defaultCharset().name();

        if (format_flowed) {
            List<String> flowed = new ArrayList<>();
            for (String line : plainContent.split("\\r?\\n")) {
                if (line.contains(" ") && !"-- ".equals(line)) {
                    StringBuilder sb = new StringBuilder();
                    for (String word : line.split(" ")) {
                        if (sb.length() + word.length() > FORMAT_FLOWED_LINE_LENGTH) {
                            sb.append(' ');
                            flowed.add(sb.toString());

                            // https://tools.ietf.org/html/rfc3676#section-4.5
                            int i = 0;
                            if (sb.length() > 0 && sb.charAt(0) == '>') {
                                i++;
                                while (i < sb.length() &&
                                        (sb.charAt(i) == '>' || sb.charAt(i) == ' '))
                                    i++;
                            }
                            String prefix = sb.substring(0, i).trim();

                            sb = new StringBuilder(prefix);
                        }
                        if (sb.length() > 0)
                            sb.append(' ');
                        sb.append(word);
                    }
                    if (sb.length() > 0)
                        flowed.add(sb.toString());
                } else
                    flowed.add(line);
            }
            plainContent = TextUtils.join("\r\n", flowed);
            plainContentType += "; format=flowed";
        }

        BodyPart plainPart = new MimeBodyPart();
        plainPart.setContent(plainContent, plainContentType);

        BodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlContent, htmlContentType);

        Multipart altMultiPart = new MimeMultipart("alternative");
        altMultiPart.addBodyPart(plainPart);
        altMultiPart.addBodyPart(htmlPart);

        int availableAttachments = 0;
        boolean hasInline = false;
        for (EntityAttachment attachment : attachments)
            if (attachment.available) {
                availableAttachments++;
                if (attachment.isInline())
                    hasInline = true;
            }

        if (availableAttachments == 0)
            if (message.isPlainOnly())
                imessage.setContent(plainContent, plainContentType);
            else
                imessage.setContent(altMultiPart);
        else {
            Multipart mixedMultiPart = new MimeMultipart("mixed");
            Multipart relatedMultiPart = new MimeMultipart("related");

            BodyPart bodyPart;
            if (message.isPlainOnly())
                bodyPart = plainPart;
            else {
                bodyPart = new MimeBodyPart();
                bodyPart.setContent(altMultiPart);
            }

            if (hasInline) {
                relatedMultiPart.addBodyPart(bodyPart);
                MimeBodyPart relatedPart = new MimeBodyPart();
                relatedPart.setContent(relatedMultiPart);
                mixedMultiPart.addBodyPart(relatedPart);
            } else
                mixedMultiPart.addBodyPart(bodyPart);

            for (final EntityAttachment attachment : attachments)
                if (attachment.available) {
                    BodyPart attachmentPart = new MimeBodyPart();

                    File file = attachment.getFile(context);

                    FileDataSource dataSource = new FileDataSource(file);
                    dataSource.setFileTypeMap(new FileTypeMap() {
                        @Override
                        public String getContentType(File file) {
                            // https://tools.ietf.org/html/rfc6047
                            if ("text/calendar".equals(attachment.type))
                                try {
                                    ICalendar icalendar = Biweekly.parse(file).first();
                                    if (icalendar != null &&
                                            icalendar.getMethod() != null &&
                                            icalendar.getMethod().isReply())
                                        return "text/calendar" +
                                                "; method=REPLY" +
                                                "; charset=" + Charset.defaultCharset().name();
                                } catch (IOException ex) {
                                    Log.e(ex);
                                }

                            return attachment.type;
                        }

                        @Override
                        public String getContentType(String filename) {
                            return getContentType(new File(filename));
                        }
                    });
                    attachmentPart.setDataHandler(new DataHandler(dataSource));

                    if (attachment.name != null)
                        attachmentPart.setFileName(attachment.name);
                    if (attachment.disposition != null)
                        attachmentPart.setDisposition(attachment.disposition);
                    if (attachment.cid != null)
                        attachmentPart.setHeader("Content-ID", attachment.cid);

                    if (attachment.isInline())
                        relatedMultiPart.addBodyPart(attachmentPart);
                    else
                        mixedMultiPart.addBodyPart(attachmentPart);
                }

            imessage.setContent(mixedMultiPart);
        }
    }

    static void overrideContentTransferEncoding(Multipart mp) throws MessagingException, IOException {
        for (int i = 0; i < mp.getCount(); i++) {
            Part part = mp.getBodyPart(i);
            Object content = part.getContent();
            if (content instanceof Multipart) {
                part.setHeader("Content-Transfer-Encoding", "7bit");
                overrideContentTransferEncoding((Multipart) content);
            } else
                part.setHeader("Content-Transfer-Encoding", "base64");
        }
    }

    MessageHelper(MimeMessage message, Context context) throws IOException {
        long cake = Helper.getAvailableStorageSpace();
        if (cake < Helper.MIN_REQUIRED_SPACE)
            throw new IOException(context.getString(R.string.app_cake),
                    new ErrnoException(context.getPackageName(), ENOSPC));
        if (cacheDir == null)
            cacheDir = context.getCacheDir();
        this.imessage = message;
    }

    boolean isReport() {
        try {
            return imessage.isMimeType("multipart/report");
        } catch (Throwable ex) {
            Log.w(ex);
            return false;
        }
    }

    boolean getSeen() throws MessagingException {
        return imessage.isSet(Flags.Flag.SEEN);
    }

    boolean getAnswered() throws MessagingException {
        return imessage.isSet(Flags.Flag.ANSWERED);
    }

    boolean getFlagged() throws MessagingException {
        return imessage.isSet(Flags.Flag.FLAGGED);
    }

    boolean getDeleted() throws MessagingException {
        return imessage.isSet(Flags.Flag.DELETED);
    }

    String getFlags() throws MessagingException {
        if (!BuildConfig.DEBUG)
            return null;

        Flags flags = imessage.getFlags();
        flags.clearUserFlags();
        return flags.toString();
    }

    @NonNull
    String[] getKeywords() throws MessagingException {
        List<String> keywords = Arrays.asList(imessage.getFlags().getUserFlags());
        Collections.sort(keywords);
        return keywords.toArray(new String[0]);
    }

    static boolean showKeyword(String keyword) {
        if (BuildConfig.DEBUG)
            return true;

        int len = FLAG_BLACKLIST.size();
        for (int i = 0; i < len; i++)
            if (FLAG_BLACKLIST.get(i).equalsIgnoreCase(keyword))
                return false;

        return true;
    }

    String getMessageID() throws MessagingException {
        ensureEnvelope();

        // Outlook outbox -> sent
        //   x-microsoft-original-message-id
        String header = imessage.getHeader(HEADER_CORRELATION_ID, null);
        if (header == null)
            header = imessage.getHeader("Message-ID", null);
        return (header == null ? null : MimeUtility.unfold(header));
    }

    List<Header> getAllHeaders() throws MessagingException {
        ensureHeaders();
        return Collections.list(imessage.getAllHeaders());
    }

    String[] getReferences() throws MessagingException {
        ensureHeaders();

        List<String> result = new ArrayList<>();
        String refs = imessage.getHeader("References", null);
        if (refs != null)
            result.addAll(Arrays.asList(getReferences(refs)));

        // Merge references of reported message for threading
        InternetHeaders iheaders = getReportHeaders();
        if (iheaders != null) {
            String arefs = iheaders.getHeader("References", null);
            if (arefs != null)
                for (String ref : getReferences(arefs))
                    if (!result.contains(ref)) {
                        Log.i("rfc822 ref=" + ref);
                        result.add(ref);
                    }

            String amsgid = iheaders.getHeader("Message-Id", null);
            if (amsgid != null) {
                String msgid = MimeUtility.unfold(amsgid);
                if (!result.contains(msgid)) {
                    Log.i("rfc822 id=" + msgid);
                    result.add(msgid);
                }
            }
        }

        return result.toArray(new String[0]);
    }

    private String[] getReferences(String header) {
        return MimeUtility.unfold(header).split("[,\\s]+");
    }

    String getDeliveredTo() throws MessagingException {
        ensureHeaders();

        String header = imessage.getHeader("Delivered-To", null);
        if (header == null)
            header = imessage.getHeader("X-Delivered-To", null);
        if (header == null)
            header = imessage.getHeader("Envelope-To", null);
        if (header == null)
            header = imessage.getHeader("X-Envelope-To", null);
        if (header == null)
            header = imessage.getHeader("X-Original-To", null);

        return (header == null ? null : MimeUtility.unfold(header));
    }

    String getInReplyTo() throws MessagingException {
        ensureHeaders();

        String header = imessage.getHeader("In-Reply-To", null);
        if (header != null)
            header = MimeUtility.unfold(header);

        if (header == null) {
            // Use reported message ID as synthetic in-reply-to
            InternetHeaders iheaders = getReportHeaders();
            if (iheaders != null) {
                header = iheaders.getHeader("Message-Id", null);
                if (header != null)
                    Log.i("rfc822 id=" + header);
            }
        }

        return header;
    }

    private InternetHeaders getReportHeaders() {
        try {
            ensureStructure();

            if (imessage.isMimeType("multipart/report")) {
                ContentType ct = new ContentType(imessage.getContentType());
                String reportType = ct.getParameter("report-type");
                if ("delivery-status".equalsIgnoreCase(reportType) ||
                        "disposition-notification".equalsIgnoreCase(reportType)) {
                    MessageParts parts = new MessageParts();
                    getMessageParts(null, imessage, parts, null);
                    for (AttachmentPart apart : parts.attachments)
                        if ("text/rfc822-headers".equalsIgnoreCase(apart.attachment.type)) {
                            reportHeaders = new InternetHeaders(apart.part.getInputStream());
                            break;
                        } else if ("message/rfc822".equalsIgnoreCase(apart.attachment.type)) {
                            Properties props = MessageHelper.getSessionProperties();
                            Session isession = Session.getInstance(props, null);
                            MimeMessage amessage = new MimeMessage(isession, apart.part.getInputStream());
                            reportHeaders = amessage.getHeaders();
                            break;
                        }
                }
            }
        } catch (Throwable ex) {
            Log.w(ex);
        }

        return reportHeaders;
    }

    String getThreadId(Context context, long account, long folder, long uid) throws MessagingException {
        if (threadId == null)
            if (true)
                threadId = _getThreadIdAlt(context, account, folder, uid);
            else
                threadId = _getThreadId(context, account, folder, uid);
        return threadId;
    }

    private String _getThreadId(Context context, long account, long folder, long uid) throws MessagingException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (imessage instanceof GmailMessage) {
            // https://developers.google.com/gmail/imap/imap-extensions#access_to_the_gmail_thread_id_x-gm-thrid
            boolean gmail_thread_id = prefs.getBoolean("gmail_thread_id", false);
            if (gmail_thread_id) {
                long thrid = ((GmailMessage) imessage).getThrId();
                if (thrid > 0)
                    return "gmail:" + thrid;
            }
        }

        String thread = null;
        String msgid = getMessageID();

        List<String> refs = new ArrayList<>();
        for (String ref : getReferences())
            if (!TextUtils.isEmpty(ref) && !refs.contains(ref))
                refs.add(ref);

        String inreplyto = getInReplyTo();
        if (!TextUtils.isEmpty(inreplyto) && !refs.contains(inreplyto))
            refs.add(inreplyto);

        DB db = DB.getInstance(context);
        List<EntityMessage> before = new ArrayList<>();
        for (String ref : refs)
            before.addAll(db.message().getMessagesByMsgId(account, ref));

        for (EntityMessage message : before)
            if (!TextUtils.isEmpty(message.thread)) {
                thread = message.thread;
                break;
            }

        if (thread == null) {
            List<EntityMessage> similar = db.message().getMessagesByMsgId(account, msgid);
            for (EntityMessage message : similar)
                if (!TextUtils.isEmpty(message.thread) && Objects.equals(message.hash, getHash())) {
                    thread = message.thread;
                    break;
                }
        }

        // Common reference
        if (thread == null && refs.size() > 0) {
            String ref = refs.get(0);
            if (!Objects.equals(ref, msgid))
                thread = ref;
        }

        if (thread == null)
            thread = getHash() + ":" + uid;

        for (EntityMessage message : before)
            if (!thread.equals(message.thread)) {
                Log.w("Updating before thread from " + message.thread + " to " + thread);
                db.message().updateMessageThread(message.account, message.thread, thread, null);
            }

        List<EntityMessage> after = db.message().getMessagesByInReplyTo(account, msgid);
        for (EntityMessage message : after)
            if (!thread.equals(message.thread)) {
                Log.w("Updating after thread from " + message.thread + " to " + thread);
                db.message().updateMessageThread(message.account, message.thread, thread, null);
            }

        boolean subject_threading = prefs.getBoolean("subject_threading", false);
        if (subject_threading && !isReport()) {
            String sender = getSortKey(getFrom());
            String subject = getSubject();
            long since = new Date().getTime() - MAX_SUBJECT_AGE * 3600 * 1000L;
            if (!TextUtils.isEmpty(sender) && !TextUtils.isEmpty(subject)) {
                List<EntityMessage> subjects = db.message().getMessagesBySubject(account, sender, subject, since);
                for (EntityMessage message : subjects)
                    if (!thread.equals(message.thread)) {
                        Log.w("Updating subject thread from " + message.thread + " to " + thread);
                        db.message().updateMessageThread(message.account, message.thread, thread, since);
                    }
            }
        }

        return thread;
    }

    private String _getThreadIdAlt(Context context, long account, long folder, long uid) throws MessagingException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (imessage instanceof GmailMessage) {
            // https://developers.google.com/gmail/imap/imap-extensions#access_to_the_gmail_thread_id_x-gm-thrid
            boolean gmail_thread_id = prefs.getBoolean("gmail_thread_id", false);
            if (gmail_thread_id) {
                long thrid = ((GmailMessage) imessage).getThrId();
                Log.i("Gmail thread=" + thrid);
                if (thrid > 0)
                    return "gmail:" + thrid;
            }
        }

        // https://docs.microsoft.com/en-us/openspecs/exchange_server_protocols/ms-oxomsg/9e994fbb-b839-495f-84e3-2c8c02c7dd9b
        if (BuildConfig.DEBUG)
            try {
                String tindex = imessage.getHeader("Thread-Index", null);
                if (tindex != null) {
                    boolean outlook_thread_id = prefs.getBoolean("outlook_thread_id", false);
                    if (outlook_thread_id) {
                        byte[] data = Base64.decode(tindex, Base64.DEFAULT);
                        if (data.length >= 22) {
                            long msb = 0, lsb = 0;
                            for (int i = 0 + 6; i < 8 + 6; i++)
                                msb = (msb << 8) | (data[i] & 0xff);
                            for (int i = 8 + 6; i < 16 + 6; i++)
                                lsb = (lsb << 8) | (data[i] & 0xff);
                            UUID guid = new UUID(msb, lsb);
                            Log.i("Outlook thread=" + guid);
                            return "outlook:" + guid;
                        }
                    }
                }
            } catch (Throwable ex) {
                Log.w(ex);
            }

        String thread = null;
        String msgid = getMessageID();

        List<String> refs = new ArrayList<>();
        for (String ref : getReferences())
            if (!TextUtils.isEmpty(ref) && !refs.contains(ref))
                refs.add(ref);

        String inreplyto = getInReplyTo();
        if (!TextUtils.isEmpty(inreplyto) && !refs.contains(inreplyto))
            refs.add(inreplyto);

        DB db = DB.getInstance(context);

        List<String> all = new ArrayList<>(refs);
        all.add(msgid);
        List<TupleThreadInfo> infos = (all.size() == 0
                ? new ArrayList<>()
                : db.message().getThreadInfo(account, all));

        // References, In-Reply-To (sent before)
        for (TupleThreadInfo info : infos)
            if (info.isReferencing(msgid) && !TextUtils.isEmpty(info.thread)) {
                thread = info.thread;
                break;
            }

        // Similar
        if (thread == null) {
            for (TupleThreadInfo info : infos)
                if (info.isSelf(msgid) && !TextUtils.isEmpty(info.thread) &&
                        Objects.equals(info.hash, getHash())) {
                    thread = info.thread;
                    break;
                }
        }

        if (thread == null && BuildConfig.DEBUG) {
            String awsses = imessage.getHeader("X-SES-Outgoing", null);
            if (!TextUtils.isEmpty(awsses)) {
                Address[] froms = getFrom();
                if (froms != null && froms.length > 0) {
                    String from = ((InternetAddress) froms[0]).getAddress();
                    if (!TextUtils.isEmpty(from) && from.endsWith("@faircode.eu")) {
                        Address[] rr = getReply();
                        Address[] tos = (rr != null && rr.length > 0 ? rr : getTo());
                        if (tos != null && tos.length > 0) {
                            String email = ((InternetAddress) tos[0]).getAddress();
                            if (!TextUtils.isEmpty(email))
                                thread = "ses:" + email;
                        }
                    }
                }
            }
        }

        // Common reference
        if (thread == null && refs.size() > 0) {
            String ref = refs.get(0);
            if (!Objects.equals(ref, msgid))
                thread = ref;
        }

        if (thread == null)
            thread = getHash() + ":" + uid;

        // Sent before
        for (TupleThreadInfo info : infos)
            if (info.isReferencing(msgid) && !thread.equals(info.thread)) {
                Log.w("Updating before thread from " + info.thread + " to " + thread);
                db.message().updateMessageThread(account, info.thread, thread, null);
            }

        // Sent after
        for (TupleThreadInfo info : infos)
            if (info.isReferenced(msgid) && !thread.equals(info.thread)) {
                Log.w("Updating after thread from " + info.thread + " to " + thread);
                db.message().updateMessageThread(account, info.thread, thread, null);
            }

        boolean subject_threading = prefs.getBoolean("subject_threading", false);
        if (subject_threading && !isReport()) {
            String sender = getSortKey(getFrom());
            String subject = getSubject();
            long since = new Date().getTime() - MAX_SUBJECT_AGE * 3600 * 1000L;
            if (!TextUtils.isEmpty(sender) && !TextUtils.isEmpty(subject)) {
                List<EntityMessage> subjects = db.message().getMessagesBySubject(account, sender, subject, since);
                for (EntityMessage message : subjects)
                    if (!thread.equals(message.thread)) {
                        Log.w("Updating subject thread from " + message.thread + " to " + thread);
                        db.message().updateMessageThread(message.account, message.thread, thread, since);
                    }
            }
        }

        return thread;
    }

    String[] getLabels() throws MessagingException {
        //ensureMessage(false);

        List<String> labels = new ArrayList<>();
        if (imessage instanceof GmailMessage)
            for (String label : ((GmailMessage) imessage).getLabels())
                if (!label.startsWith("\\"))
                    labels.add(label);

        Collections.sort(labels);

        return labels.toArray(new String[0]);
    }

    Integer getPriority() throws MessagingException {
        Integer priority = null;

        ensureHeaders();

        // https://tools.ietf.org/html/rfc2156
        // https://docs.microsoft.com/en-us/openspecs/exchange_server_protocols/ms-oxcmail/2bb19f1b-b35e-4966-b1cb-1afd044e83ab
        String header = imessage.getHeader("Importance", null);
        if (header == null)
            header = imessage.getHeader("Priority", null);
        if (header == null)
            header = imessage.getHeader("X-Priority", null);
        if (header == null)
            header = imessage.getHeader("X-MSMail-Priority", null);

        if (header != null) {
            header = decodeMime(header);

            int sp = header.indexOf(" ");
            if (sp >= 0)
                header = header.substring(0, sp); // "2 (High)"

            header = header.replaceAll("[^A-Za-z0-9\\-]", "");
        }

        if ("high".equalsIgnoreCase(header) ||
                "highest".equalsIgnoreCase(header) ||
                "u".equalsIgnoreCase(header) || // Urgent?
                "urgent".equalsIgnoreCase(header) ||
                "critical".equalsIgnoreCase(header) ||
                "yes".equalsIgnoreCase(header))
            priority = EntityMessage.PRIORITIY_HIGH;
        else if ("normal".equalsIgnoreCase(header) ||
                "medium".equalsIgnoreCase(header) ||
                "med".equalsIgnoreCase(header))
            priority = EntityMessage.PRIORITIY_NORMAL;
        else if ("low".equalsIgnoreCase(header) ||
                "lowest".equalsIgnoreCase(header) ||
                "non-urgent".equalsIgnoreCase(header) ||
                "marketing".equalsIgnoreCase(header) ||
                "bulk".equalsIgnoreCase(header) ||
                "batch".equalsIgnoreCase(header) ||
                "mass".equalsIgnoreCase(header) ||
                "none".equalsIgnoreCase(header))
            priority = EntityMessage.PRIORITIY_LOW;
        else if ("a".equalsIgnoreCase(header) ||
                "b".equalsIgnoreCase(header) ||
                "c".equalsIgnoreCase(header) ||
                "aplus".equalsIgnoreCase(header))
            ; // Ignore unknown
        else if (!TextUtils.isEmpty(header))
            try {
                priority = Integer.parseInt(header);
                if (priority < 3)
                    priority = EntityMessage.PRIORITIY_HIGH;
                else if (priority > 3)
                    priority = EntityMessage.PRIORITIY_LOW;
                else
                    priority = EntityMessage.PRIORITIY_NORMAL;
            } catch (NumberFormatException ex) {
                Log.w("priority=" + header);
            }

        if (EntityMessage.PRIORITIY_NORMAL.equals(priority))
            priority = null;

        return priority;
    }

    Integer getSensitivity() throws MessagingException {
        ensureHeaders();

        // https://www.rfc-editor.org/rfc/rfc4021.html#section-2.1.55
        String header = imessage.getHeader("Sensitivity", null);

        if (TextUtils.isEmpty(header))
            return null;

        header = header.toLowerCase(Locale.ROOT);

        if (header.contains("personal"))
            return EntityMessage.SENSITIVITY_PERSONAL;
        if (header.contains("private"))
            return EntityMessage.SENSITIVITY_PRIVATE;
        if (header.contains("company")) // company-confidential
            return EntityMessage.SENSITIVITY_CONFIDENTIAL;

        return null;
    }

    Boolean getAutoSubmitted() throws MessagingException {
        // https://tools.ietf.org/html/rfc3834
        String header = imessage.getHeader("Auto-Submitted", null);
        if (header == null) {
            // https://www.arp242.net/autoreply.html
            // https://github.com/jpmckinney/multi_mail/wiki/Detecting-autoresponders

            // Microsoft
            header = imessage.getHeader("X-Auto-Response-Suppress", null);
            if ("DR".equalsIgnoreCase(header)) // Suppress delivery reports
                return true;
            if ("AutoReply".equalsIgnoreCase(header)) // Suppress autoreply messages other than OOF notifications
                return true;
            if ("All".equalsIgnoreCase(header))
                return true;

            // Google
            header = imessage.getHeader("Feedback-ID", null);
            if (header != null)
                return true;

            header = imessage.getHeader("Precedence", null);
            if ("bulk".equalsIgnoreCase(header)) // Used by Amazon
                return true;
            if ("auto_reply".equalsIgnoreCase(header))
                return true;
            if ("list".equalsIgnoreCase(header))
                return true;

            // Lists
            header = imessage.getHeader("List-Id", null);
            if (header != null)
                return true;
            header = imessage.getHeader("List-Unsubscribe", null);
            if (header != null)
                return true;

            return null;
        }

        return !"no".equalsIgnoreCase(header);
    }

    boolean getReceiptRequested() throws MessagingException {
        Address[] headers = getReceiptTo();
        return (headers != null && headers.length > 0);
    }

    Address[] getReceiptTo() throws MessagingException {
        // Return-Receipt-To = delivery receipt
        // Disposition-Notification-To = read receipt
        Address[] receipt = getAddressHeader("Disposition-Notification-To");
        if (receipt == null || receipt.length == 0)
            receipt = getAddressHeader("Read-Receipt-To");
        if (receipt == null || receipt.length == 0)
            receipt = getAddressHeader("X-Confirm-Reading-To");
        return receipt;
    }

    String getBimiSelector() throws MessagingException {
        ensureHeaders();

        // BIMI-Selector: v=BIMI1; s=selector;
        String header = imessage.getHeader("BIMI-Selector", null);
        if (header == null)
            return null;

        header = header.toLowerCase(Locale.ROOT);

        int s = header.indexOf("s=");
        if (s < 0)
            return null;

        int e = header.indexOf(';', s + 2);
        if (e < 0)
            e = header.length();

        String selector = header.substring(s + 2, e);
        if (TextUtils.isEmpty(selector))
            return null;

        Log.i("BIMI selector=" + selector);
        return selector;
    }

    String[] getAuthentication() throws MessagingException {
        ensureHeaders();

        List<String> all = new ArrayList<>();

        String[] results = imessage.getHeader("Authentication-Results");
        if (results != null)
            all.addAll(Arrays.asList(results));

        String[] aresults = imessage.getHeader("ARC-Authentication-Results");
        if (aresults != null)
            all.addAll(Arrays.asList(aresults));

        if (all.size() == 0)
            return null;

        String[] headers = new String[all.size()];
        for (int i = 0; i < all.size(); i++)
            headers[i] = MimeUtility.unfold(all.get(i));

        return headers;
    }

    static Boolean getAuthentication(String type, String[] headers) {
        if (headers == null)
            return null;

        // https://tools.ietf.org/html/rfc7601
        Boolean result = null;
        for (String header : headers) {
            String v = getKeyValues(header).get(type);
            if (v == null)
                continue;
            String[] val = v.split("\\s+");
            if (val.length > 0) {
                if ("fail".equals(val[0]))
                    result = false;
                else if ("pass".equals(val[0]))
                    if (result == null)
                        result = true;
            }
        }

        return result;
    }

    boolean getSPF() throws MessagingException {
        ensureHeaders();

        // http://www.open-spf.org/RFC_4408/#header-field
        String[] headers = imessage.getHeader("Received-SPF");
        if (headers == null || headers.length < 1)
            return false;

        String spf = MimeUtility.unfold(headers[0]);
        return (spf.trim().toLowerCase(Locale.ROOT).startsWith("pass"));
    }

    Address[] getMailFrom(String[] headers) {
        if (headers == null)
            return null;

        Address[] mailfrom = null;
        for (String header : headers) {
            String spf = getKeyValues(header).get("spf");
            if (spf == null)
                continue;

            int i = spf.indexOf(SMTP_MAILFORM + "=");
            if (i < 0)
                continue;

            String v = spf.substring(i + SMTP_MAILFORM.length() + 1);
            int s = v.indexOf(' ');
            if (s > 0)
                v = v.substring(0, s);

            if (v.startsWith("\"") && v.endsWith("\""))
                v = v.substring(1, v.length() - 1);

            try {
                mailfrom = InternetAddress.parseHeader(v, false);
            } catch (Throwable ex) {
                Log.w(ex);
            }
        }

        return mailfrom;
    }

    private String fixEncoding(String name, String header) {
        if (header.trim().startsWith("=?"))
            return header;

        Charset detected = CharsetHelper.detect(header, StandardCharsets.ISO_8859_1);
        if (detected == null && CharsetHelper.isUTF8(header))
            detected = StandardCharsets.UTF_8;
        if (detected == null ||
                CHARSET16.contains(detected) ||
                StandardCharsets.US_ASCII.equals(detected) ||
                StandardCharsets.ISO_8859_1.equals(detected))
            return header;

        Log.i("Converting " + name + " to " + detected);
        return new String(header.getBytes(StandardCharsets.ISO_8859_1), detected);
    }

    private Address[] getAddressHeader(String name) throws MessagingException {
        ensureHeaders();

        String header = imessage.getHeader(name, ",");
        if (header == null)
            return null;

        header = fixEncoding(name, header);
        header = header.replaceAll("\\?=[\\r\\n\\t ]+=\\?", "\\?==\\?");
        Address[] addresses = InternetAddress.parseHeader(header, false);

        List<Address> result = new ArrayList<>();
        for (Address address : addresses) {
            InternetAddress iaddress = (InternetAddress) address;
            String email = iaddress.getAddress();
            String personal = iaddress.getPersonal();

            if (TextUtils.isEmpty(email) && TextUtils.isEmpty(personal))
                continue;

            if (personal != null && personal.equals(email))
                try {
                    iaddress.setPersonal(null);
                    personal = null;
                } catch (UnsupportedEncodingException ex) {
                    Log.w(ex);
                }

            if (email != null) {
                email = decodeMime(email);
                email = punyCode(email);

                iaddress.setAddress(email);
            }

            if (personal != null) {
                try {
                    iaddress.setPersonal(decodeMime(personal));
                } catch (UnsupportedEncodingException ex) {
                    Log.w(ex);
                }
            }

            result.add(address);
        }

        return (result.size() == 0 ? null : result.toArray(new Address[0]));
    }

    Address[] getReturnPath() throws MessagingException {
        Address[] addresses = getAddressHeader("Return-Path");
        if (addresses == null)
            return null;

        List<Address> result = new ArrayList<>();
        for (int i = 0; i < addresses.length; i++) {
            boolean duplicate = false;
            for (int j = 0; j < i; j++)
                if (addresses[i].equals(addresses[j])) {
                    duplicate = true;
                    break;
                }
            if (!duplicate)
                result.add(addresses[i]);
        }

        return result.toArray(new Address[0]);
    }

    Address[] getSender() throws MessagingException {
        Address[] sender = getAddressHeader("X-Google-Original-From");
        if (sender == null)
            sender = getAddressHeader("Sender");

        return sender;
    }

    Address[] getFrom() throws MessagingException {
        Address[] address = getAddressHeader("From");
        if (address == null)
            address = getAddressHeader("Sender");
        return address;
    }

    Address[] getTo() throws MessagingException {
        return getAddressHeader("To");
    }

    Address[] getCc() throws MessagingException {
        return getAddressHeader("Cc");
    }

    Address[] getBcc() throws MessagingException {
        return getAddressHeader("Bcc");
    }

    Address[] getReply() throws MessagingException {
        return getAddressHeader("Reply-To");
    }

    Address[] getListPost() throws MessagingException {
        ensureHeaders();

        String list;
        try {
            // https://www.ietf.org/rfc/rfc2369.txt
            list = imessage.getHeader("List-Post", null);
            if (list == null)
                return null;

            list = MimeUtility.unfold(list);
            list = decodeMime(list);

            // List-Post: NO (posting not allowed on this list)
            if (list != null && list.startsWith("NO"))
                return null;

            // https://www.ietf.org/rfc/rfc2368.txt
            for (String entry : list.split(",")) {
                entry = entry.trim();
                int lt = entry.indexOf("<");
                int gt = entry.lastIndexOf(">");
                if (lt >= 0 && gt > lt)
                    try {
                        MailTo mailto = MailTo.parse(entry.substring(lt + 1, gt));
                        if (mailto.getTo() != null)
                            return new Address[]{new InternetAddress(mailto.getTo().split(",")[0], null)};
                    } catch (Throwable ex) {
                        Log.i(ex);
                    }
            }

            Log.i(new IllegalArgumentException("List-Post: " + list));
            return null;
        } catch (AddressException ex) {
            Log.w(ex);
            return null;
        }
    }

    String getListUnsubscribe() throws MessagingException {
        ensureHeaders();

        String list;
        try {
            // https://www.ietf.org/rfc/rfc2369.txt
            list = imessage.getHeader("List-Unsubscribe", null);
            if (list == null)
                return null;

            list = MimeUtility.unfold(list);
            list = decodeMime(list);

            if (list == null || list.startsWith("NO"))
                return null;

            String link = null;
            String mailto = null;
            int s = list.indexOf('<');
            int e = list.indexOf('>', s + 1);
            while (s >= 0 && e > s) {
                String unsubscribe = list.substring(s + 1, e);
                if (TextUtils.isEmpty(unsubscribe))
                    ; // Empty address
                else if (unsubscribe.toLowerCase(Locale.ROOT).startsWith("mailto:")) {
                    if (mailto == null) {
                        try {
                            MailTo.parse(unsubscribe);
                            mailto = unsubscribe;
                        } catch (Throwable ex) {
                            Log.w(new Throwable(unsubscribe, ex));
                        }
                    }
                } else if (Helper.EMAIL_ADDRESS.matcher(unsubscribe).matches())
                    mailto = "mailto:" + unsubscribe;
                else {
                    if (link == null) {
                        Uri uri = Uri.parse(unsubscribe);
                        String scheme = uri.getScheme();
                        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                            link = unsubscribe;
                        else {
                            Pattern p =
                                    Pattern.compile(PatternsCompat.AUTOLINK_WEB_URL.pattern() + "|" +
                                            PatternsCompat.AUTOLINK_EMAIL_ADDRESS.pattern());
                            Matcher m = p.matcher(unsubscribe);
                            if (m.find())
                                link = unsubscribe.substring(m.start(), m.end());
                            else
                                Log.w(new Throwable(unsubscribe));
                        }
                    }
                }

                s = list.indexOf('<', e + 1);
                e = list.indexOf('>', s + 1);
            }

            if (link != null)
                return link;
            if (mailto != null)
                return mailto;

            if (!BuildConfig.PLAY_STORE_RELEASE)
                Log.i(new IllegalArgumentException("List-Unsubscribe: " + list));
            return null;
        } catch (Throwable ex) {
            Log.e(ex);
            return null;
        }
    }

    String getAutocrypt() throws MessagingException {
        ensureHeaders();

        String autocrypt = imessage.getHeader("Autocrypt", null);
        if (autocrypt == null)
            return null;

        return MimeUtility.unfold(autocrypt);
    }

    String getSubject() throws MessagingException {
        ensureHeaders();

        String subject = imessage.getHeader("Subject", null);
        if (subject == null)
            return null;

        subject = fixEncoding("subject", subject);
        subject = subject.replaceAll("\\?=[\\r\\n\\t ]+=\\?", "\\?==\\?");
        subject = MimeUtility.unfold(subject);
        subject = decodeMime(subject);

        return subject
                .trim()
                .replace("\n", "")
                .replace("\r", "");
    }

    Long getSize() throws MessagingException {
        ensureEnvelope();

        long size = imessage.getSize();
        return (size < 0 ? null : size);
    }

    Long getReceived() throws MessagingException {
        ensureEnvelope();

        Date received = imessage.getReceivedDate();
        if (received == null)
            return null;

        return received.getTime();
    }

    Long getReceivedHeader() throws MessagingException {
        return getReceivedHeader(null);
    }

    Long getReceivedHeader(Long before) throws MessagingException {
        ensureHeaders();

        // https://tools.ietf.org/html/rfc5321#section-4.4
        // https://tools.ietf.org/html/rfc5322#section-3.6.7
        String[] received = imessage.getHeader("Received");
        if (received == null || received.length == 0)
            return null;

        // First header is last added header
        for (int i = 0; i < received.length; i++) {
            String header = MimeUtility.unfold(received[i]);
            int semi = header.lastIndexOf(';');
            if (semi < 0)
                return null;

            MailDateFormat mdf = new MailDateFormat();
            Date date = mdf.parse(header, new ParsePosition(semi + 1));
            if (date == null)
                return null;

            long time = date.getTime();
            if (before == null || time < before)
                return time;
        }

        return null;
    }

    Boolean getTLS() throws MessagingException {
        try {
            Boolean tls = _getTLS();
            Log.i("--- TLS=" + tls);
            return tls;
        } catch (Throwable ex) {
            Log.e(ex);
            return null;
        }
    }

    private Boolean _getTLS() throws MessagingException {
        // https://datatracker.ietf.org/doc/html/rfc2821#section-4.4

        // Time-stamp-line = "Received:" FWS Stamp <CRLF>
        // Stamp = From-domain By-domain Opt-info ";"  FWS date-time
        // From-domain = "FROM" FWS Extended-Domain CFWS
        // By-domain = "BY" FWS Extended-Domain CFWS
        // Opt-info = [Via] [With] [ID] [For]
        // Via = "VIA" FWS Link CFWS
        // With = "WITH" FWS Protocol CFWS
        // ID = "ID" FWS String / msg-id CFWS
        // For = "FOR" FWS 1*( Path / Mailbox ) CFWS

        // Extended-Domain = Domain / ( Domain FWS "(" TCP-info ")" ) / ( Address-literal FWS "(" TCP-info ")" )
        // TCP-info = Address-literal / ( Domain FWS Address-literal )
        // Link = "TCP" / Addtl-Link
        // Addtl-Link = Atom
        // Protocol = "ESMTP" / "SMTP" / Attdl-Protocol
        // Attdl-Protocol = Atom

        ensureHeaders();

        String[] received = imessage.getHeader("Received");
        if (received == null || received.length == 0)
            return null;

        // First header is last added header
        Log.i("=======");
        for (int i = 0; i < received.length; i++) {
            String header = MimeUtility.unfold(received[i]);
            Log.i("--- header=" + header);
            Boolean tls = isTLS(header, i == received.length - 1);
            if (!Boolean.TRUE.equals(tls))
                return tls;
        }

        return true;
    }

    static Boolean isTLS(String header, boolean first) {
        // Strip date
        int semi = header.lastIndexOf(';');
        if (semi > 0)
            header = header.substring(0, semi);

        if (header.contains("using TLS") ||
                header.contains("via HTTP") ||
                header.contains("version=TLS")) {
            Log.i("--- found TLS");
            return true;
        }

        // (qmail nnn invoked by uid nnn); 1 Jan 2022 00:00:00 -0000
        // by <host name> (Postfix, from userid nnn)
        if (header.matches(".*\\(qmail \\d+ invoked by uid \\d+\\).*") ||
                header.matches(".*\\(Postfix, from userid \\d+\\).*")) {
            Log.i("--- phrase");
            return true;
        }

        // Get key/values
        String[] parts = header.split("\\s+");
        Map<String, StringBuilder> kv = new HashMap<>();
        String key = null;
        for (int p = 0; p < parts.length; p++) {
            String k = parts[p].toLowerCase(Locale.ROOT);
            if (RECEIVED_WORDS.contains(k)) {
                key = k;
                if (!kv.containsKey(key))
                    kv.put(key, new StringBuilder());
            } else if (key != null) {
                StringBuilder sb = kv.get(key);
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append(parts[p]);
            }
        }

        // Dump
        for (String k : kv.keySet())
            Log.i("--- " + k + "=" + kv.get(k));

        // Check if 'by' local address
        if (kv.containsKey("by")) {
            String by = kv.get("by").toString();
            if (by.matches(".*\\.google\\.com"))
                return true;
            if (isLocal(by)) {
                Log.i("--- local by=" + by);
                return true;
            }
        }

        // Check if 'from' local address
        if (kv.containsKey("from")) {
            String from = kv.get("from").toString();
            if (isLocal(from)) {
                Log.i("--- local from=" + from);
                return true;
            }
        }

        // Check Microsoft front end transport (proxy)
        // https://social.technet.microsoft.com/wiki/contents/articles/50370.exchange-2016-what-is-the-front-end-transport-service-on-the-mailbox-role.aspx
        if (kv.containsKey("via")) {
            String via = kv.get("via").toString();
            if ("Frontend Transport".equals(via)) {
                Log.i("--- frontend via=" + via);
                return true;
            }
        }

        // Check protocol
        if (!kv.containsKey("with")) {
            Log.i("--- with missing");
            return null;
        }

        // https://datatracker.ietf.org/doc/html/rfc3848
        // https://www.iana.org/assignments/mail-parameters/mail-parameters.txt
        String with = kv.get("with").toString();
        int w = with.indexOf(' ');
        String protocol = (w < 0 ? with : with.substring(0, w)).toLowerCase(Locale.ROOT);

        if (with.contains("TLS"))
            return true;

        if ("local".equals(protocol)) {
            // Exim
            Log.i("--- local with=" + with);
            return true;
        }

        if (protocol.startsWith("lmtp")) {
            // https://en.wikipedia.org/wiki/Local_Mail_Transfer_Protocol
            Log.i("--- lmtp with=" + with);
            return true;
        }

        if ("mapi".equals(protocol)) {
            // https://en.wikipedia.org/wiki/MAPI
            Log.i("--- mapi with=" + with);
            return true;
        }

        if ("http".equals(protocol) ||
                "https".equals(protocol) ||
                "httprest".equals(protocol)) {
            // https: Outlook
            // httprest: by gmailapi.google.com
            Log.i("--- http with=" + with);
            return true;
        }

        if (!protocol.contains("mtp")) {
            Log.i("--- unknown with=" + with);
            return null;
        }

        if (protocol.contains("mtps")) {
            Log.i("--- insecure with=" + with);
            return true;
        }

        return false;
    }

    private static boolean isLocal(String value) {
        if (value.contains("localhost") ||
                value.contains("127.0.0.1") ||
                value.contains("[::1]"))
            return true;

        int s = value.indexOf('[');
        int e = value.indexOf(']', s + 1);
        if (s >= 0 && e > 0) {
            String ip = value.substring(s + 1, e);
            if (ip.toLowerCase(Locale.ROOT).startsWith("ipv6:"))
                ip = ip.substring(5);
            if (ConnectionHelper.isNumericAddress(ip) &&
                    ConnectionHelper.isLocalAddress(ip))
                return true;
        }

        int f = value.indexOf(' ');
        String host = (f < 0 ? value : value.substring(0, f));
        if (ConnectionHelper.isNumericAddress(host)) {
            if (ConnectionHelper.isLocalAddress(host))
                return true;
        }

        return false;
    }

    Long getSent() throws MessagingException {
        ensureEnvelope();

        Date sent = imessage.getSentDate();
        if (sent == null)
            return null;

        return sent.getTime();
    }

    Long getResent() throws MessagingException {
        ensureHeaders();

        String resent = imessage.getHeader("Resent-Date", null);
        if (resent == null)
            return null;

        MailDateFormat mdf = new MailDateFormat();
        Date date = mdf.parse(resent, new ParsePosition(0));
        if (date == null)
            return null;

        return date.getTime();
    }

    String getHeaders() throws MessagingException {
        ensureHeaders();

        StringBuilder sb = new StringBuilder();
        Enumeration<Header> headers = imessage.getAllHeaders();
        while (headers.hasMoreElements()) {
            Header header = headers.nextElement();
            sb.append(header.getName()).append(": ").append(header.getValue()).append('\n');
        }
        return sb.toString();
    }

    String getInfrastructure() throws MessagingException {
        ensureHeaders();

        String awsses = imessage.getHeader("X-SES-Outgoing", null);
        if (!TextUtils.isEmpty(awsses))
            return "awsses";

        String sendgrid = imessage.getHeader("X-SG-EID", null);
        if (!TextUtils.isEmpty(sendgrid))
            return "sendgrid";

        String mailgun = imessage.getHeader("X-Mailgun-Sid", null);
        if (!TextUtils.isEmpty(mailgun))
            return "mailgun";

        String mandrill = imessage.getHeader("X-Mandrill-User", null);
        if (!TextUtils.isEmpty(mandrill))
            return "mandrill";

        String mailchimp = imessage.getHeader("X-MC-User", null);
        if (!TextUtils.isEmpty(mailchimp))
            return "mailchimp";

        String postmark = imessage.getHeader("X-PM-Message-Id", null);
        if (!TextUtils.isEmpty(postmark))
            return "postmark";

        String salesforce = imessage.getHeader("X-SFDC-User", null);
        if (!TextUtils.isEmpty(salesforce))
            return "salesforce";

        String mailjet = imessage.getHeader("X-MJ-Mid", null);
        if (!TextUtils.isEmpty(mailjet))
            return "mailjet";

        String sendinblue = imessage.getHeader("X-sib-id", null);
        if (!TextUtils.isEmpty(sendinblue))
            return "sendinblue";

        String sparkpost = imessage.getHeader("X-MSFBL", null);
        if (!TextUtils.isEmpty(sparkpost))
            return "sparkpost";

        String netcore = imessage.getHeader("X-FNCID", null);
        if (!TextUtils.isEmpty(netcore))
            return "netcore";

        String elastic = imessage.getHeader("X-Msg-EID", null);
        if (!TextUtils.isEmpty(elastic))
            return "elastic";

        String zeptomail = imessage.getHeader("X-JID", null); // TM-MAIL-JID
        if (!TextUtils.isEmpty(zeptomail))
            return "zeptomail";

        String gmail = imessage.getHeader("X-Gm-Message-State", null);
        if (!TextUtils.isEmpty(gmail))
            return "gmail";

        String outlook = imessage.getHeader("x-ms-publictraffictype", null);
        if (!TextUtils.isEmpty(outlook))
            return "outlook";

        String yahoo = imessage.getHeader("X-Sonic-MF", null);
        if (!TextUtils.isEmpty(yahoo))
            return "yahoo";

        String icloud = imessage.getHeader("X-Proofpoint-Spam-Details", null);
        if (!TextUtils.isEmpty(icloud))
            return "icloud";

        //String zoho = imessage.getHeader("X-ZohoMailClient", null);
        //if (!TextUtils.isEmpty(zoho))
        //    return "zoho";

        String icontact = imessage.getHeader("X-SFMC-Stack", null);
        if (!TextUtils.isEmpty(icontact))
            return "icontact";

        String paypal = imessage.getHeader("X-Email-Type-Id", null);
        if (!TextUtils.isEmpty(paypal))
            return "paypal";

        String xmailer = imessage.getHeader("X-Mailer", null);
        if (!TextUtils.isEmpty(xmailer)) {
            //if (xmailer.contains("iPhone Mail"))
            //    return "icloud";
            if (xmailer.contains("PHPMailer"))
                return "phpmailer";
            //if (xmailer.contains("Zoho Mail"))
            //    return "zoho";
        }

        String return_path = imessage.getHeader("Return-Path", null);
        if (!TextUtils.isEmpty(return_path)) {
            if (return_path.contains("pdmailservice.com"))
                return "icontact";
            if (return_path.contains("flowmailer.com"))
                return "flowmailer";
        }

        return null;
    }

    String getHash() throws MessagingException {
        try {
            if (hash == null)
                hash = Helper.sha1(getHeaders().getBytes());
            return hash;
        } catch (NoSuchAlgorithmException ex) {
            Log.e(ex);
            return null;
        }
    }

    enum AddressFormat {NAME_ONLY, EMAIL_ONLY, NAME_EMAIL}

    static AddressFormat getAddressFormat(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean name_email = prefs.getBoolean("name_email", false);
        int email_format = prefs.getInt("email_format", name_email
                ? MessageHelper.AddressFormat.NAME_EMAIL.ordinal()
                : MessageHelper.AddressFormat.NAME_ONLY.ordinal());
        if (email_format < MessageHelper.AddressFormat.values().length)
            return MessageHelper.AddressFormat.values()[email_format];
        else
            return MessageHelper.AddressFormat.NAME_ONLY;
    }

    static String formatAddresses(Address[] addresses) {
        return formatAddresses(addresses, true, false);
    }

    static String formatAddressesShort(Address[] addresses) {
        return formatAddresses(addresses, false, false);
    }

    static String formatAddressesCompose(Address[] addresses) {
        String result = formatAddresses(addresses, true, true);
        if (!TextUtils.isEmpty(result))
            result += ", ";
        return result;
    }

    static String formatAddresses(Address[] addresses, boolean full, boolean compose) {
        return formatAddresses(addresses, full ? AddressFormat.NAME_EMAIL : AddressFormat.NAME_ONLY, compose);
    }

    static String formatAddresses(Address[] addresses, AddressFormat format, boolean compose) {
        if (addresses == null || addresses.length == 0)
            return "";

        List<String> formatted = new ArrayList<>();
        for (int i = 0; i < addresses.length; i++) {
            boolean duplicate = false;
            for (int j = 0; j < i; j++)
                if (addresses[i].equals(addresses[j])) {
                    duplicate = true;
                    break;
                }
            if (duplicate)
                continue;

            if (addresses[i] instanceof InternetAddress) {
                InternetAddress address = (InternetAddress) addresses[i];
                String email = address.getAddress();
                String personal = address.getPersonal();

                if (TextUtils.isEmpty(personal) || format == AddressFormat.EMAIL_ONLY)
                    formatted.add(TextUtils.isEmpty(email) ? "<>" : email);
                else {
                    if (compose) {
                        boolean quote = false;
                        personal = personal.replace("\"", "");
                        for (int c = 0; c < personal.length(); c++)
                            // https://tools.ietf.org/html/rfc822
                            if ("()<>@,;:\\\".[]".indexOf(personal.charAt(c)) >= 0) {
                                quote = true;
                                break;
                            }
                        if (quote)
                            personal = "\"" + personal + "\"";
                    }

                    if (format == AddressFormat.NAME_EMAIL && !TextUtils.isEmpty(email))
                        formatted.add(personal + " <" + email + ">");
                    else
                        formatted.add(personal);
                }
            } else
                formatted.add(addresses[i].toString());
        }
        return TextUtils.join(compose ? ", " : "; ", formatted);
    }

    static String punyCode(String email) {
        int at = email.indexOf('@');
        if (at > 0) {
            String user = email.substring(0, at);
            String domain = email.substring(at + 1);

            try {
                user = IDN.toASCII(user, IDN.ALLOW_UNASSIGNED);
            } catch (Throwable ex) {
                Log.i(ex);
            }

            String[] parts = domain.split("\\.");
            for (int p = 0; p < parts.length; p++)
                try {
                    parts[p] = IDN.toASCII(parts[p], IDN.ALLOW_UNASSIGNED);
                } catch (Throwable ex) {
                    Log.i(ex);
                }

            email = user + '@' + TextUtils.join(".", parts);
        }
        return email;
    }

    static String decodeMime(String text) {
        if (text == null)
            return null;

        // https://tools.ietf.org/html/rfc2045
        // https://tools.ietf.org/html/rfc2047
        // encoded-word = "=?" charset "?" encoding "?" encoded-text "?="

        int s, q1, q2, e, i = 0;
        List<MimeTextPart> parts = new ArrayList<>();
        while (i < text.length()) {
            s = text.indexOf("=?", i);
            if (s < 0)
                break;

            q1 = text.indexOf("?", s + 2);
            if (q1 < 0)
                break;

            q2 = text.indexOf("?", q1 + 1);
            if (q2 < 0)
                break;

            e = text.indexOf("?=", q2 + 1);
            if (e < 0)
                break;

            String plain = text.substring(i, s);
            if (!TextUtils.isEmpty(plain))
                parts.add(new MimeTextPart(plain));

            parts.add(new MimeTextPart(
                    text.substring(s + 2, q1),
                    text.substring(q1 + 1, q2),
                    text.substring(q2 + 1, e)));

            i = e + 2;
        }

        if (i < text.length())
            parts.add(new MimeTextPart(text.substring(i)));

        // Fold words to not break encoding
        int p = 0;
        while (p + 1 < parts.size()) {
            MimeTextPart p1 = parts.get(p);
            MimeTextPart p2 = parts.get(p + 1);
            // https://bugzilla.mozilla.org/show_bug.cgi?id=1374149
            if (!"ISO-2022-JP".equalsIgnoreCase(p1.charset) &&
                    p1.charset != null && p1.charset.equalsIgnoreCase(p2.charset) &&
                    p1.encoding != null && p1.encoding.equalsIgnoreCase(p2.encoding)) {
                try {
                    byte[] b1 = decodeWord(p1.text, p1.encoding, p1.charset);
                    byte[] b2 = decodeWord(p2.text, p2.encoding, p2.charset);
                    byte[] b = new byte[b1.length + b2.length];
                    System.arraycopy(b1, 0, b, 0, b1.length);
                    System.arraycopy(b2, 0, b, b1.length, b2.length);
                    p1.text = new String(b, p1.charset);
                    p1.charset = null;
                    p2.encoding = null;
                    parts.remove(p + 1);
                    continue;
                } catch (Throwable ex) {
                    Log.w(ex);
                }
                p1.text += p2.text;
                parts.remove(p + 1);
            } else
                p++;
        }

        StringBuilder sb = new StringBuilder();
        for (MimeTextPart part : parts)
            sb.append(part);
        return sb.toString();
    }

    static byte[] decodeWord(String word, String encoding, String charset) throws IOException {
        String e = encoding.trim();
        ByteArrayInputStream bis = new ByteArrayInputStream(ASCIIUtility.getBytes(word));

        InputStream is;
        if (e.equalsIgnoreCase("B"))
            is = new BASE64DecoderStream(bis);
        else if (e.equalsIgnoreCase("Q"))
            is = new QDecoderStreamEx(bis);
        else {
            Log.e(new UnsupportedEncodingException("Encoding=" + encoding));
            return word.getBytes(charset);
        }

        int count = bis.available();
        byte[] bytes = new byte[count];
        count = is.read(bytes, 0, count);

        return Arrays.copyOf(bytes, count);
    }

    private static class MimeTextPart {
        String charset;
        String encoding;
        String text;

        MimeTextPart(String text) {
            this.text = text;
        }

        MimeTextPart(String charset, String encoding, String text) {
            this.charset = charset;
            this.encoding = encoding;
            this.text = text;
        }

        @Override
        public String toString() {
            if (charset == null)
                return text;

            try {
                return decodeMime(new String(decodeWord(text, encoding, charset), charset));
            } catch (Throwable ex) {
                String word = "=?" + charset + "?" + encoding + "?" + text + "?=";
                Log.e(new IllegalArgumentException(word, ex));
                return word;
            }
        }
    }

    static String getSortKey(Address[] addresses) {
        if (addresses == null || addresses.length == 0)
            return null;
        InternetAddress address = (InternetAddress) addresses[0];
        // Sort on name will result in inconsistent results
        // because the sender name and sender contact name can differ
        return address.getAddress();
    }

    class PartHolder {
        Part part;
        ContentType contentType;

        PartHolder(Part part, ContentType contentType) {
            this.part = part;
            this.contentType = contentType;
        }

        boolean isPlainText() {
            return "text/plain".equalsIgnoreCase(contentType.getBaseType());
        }

        boolean isHtml() {
            return "text/html".equalsIgnoreCase(contentType.getBaseType());
        }

        boolean isReport() {
            String ct = contentType.getBaseType();
            return Report.isDeliveryStatus(ct) || Report.isDispositionNotification(ct);
        }
    }

    class MessageParts {
        private String protected_subject;
        private List<PartHolder> text = new ArrayList<>();
        private List<PartHolder> extra = new ArrayList<>();
        private List<AttachmentPart> attachments = new ArrayList<>();
        private ArrayList<String> warnings = new ArrayList<>();

        String getProtectedSubject() {
            return protected_subject;
        }

        Integer isPlainOnly(boolean download_plain) {
            Integer plain = isPlainOnly();
            if (plain == null)
                return null;
            if (download_plain && plain == 0x80)
                plain |= 1;
            return plain;
        }

        Integer isPlainOnly() {
            int html = 0;
            int plain = 0;
            for (PartHolder h : text) {
                if (h.isHtml())
                    html++;
                if (h.isPlainText())
                    plain++;
            }

            if (html + plain == 0)
                return null;
            if (html == 0)
                return 1;
            return (plain > 0 ? 0x80 : 0);
        }

        boolean hasBody() throws MessagingException {
            List<PartHolder> all = new ArrayList<>();
            all.addAll(text);
            all.addAll(extra);

            for (PartHolder h : all)
                if (h.part.getSize() > 0)
                    return true;

            return false;
        }

        void normalize() {
            Integer plain = isPlainOnly();
            if (plain != null && (plain & 1) != 0)
                for (AttachmentPart apart : attachments)
                    if (!TextUtils.isEmpty(apart.attachment.cid) ||
                            !Part.ATTACHMENT.equals(apart.attachment.disposition)) {
                        Log.i("Normalizing " + apart.attachment);
                        apart.attachment.cid = null;
                        apart.attachment.related = false;
                        apart.attachment.disposition = Part.ATTACHMENT;
                    }
        }

        Long getBodySize() throws MessagingException {
            Long size = null;

            List<PartHolder> all = new ArrayList<>();
            all.addAll(text);
            for (PartHolder h : all) {
                int s = h.part.getSize();
                if (s >= 0)
                    if (size == null)
                        size = (long) s;
                    else
                        size += (long) s;
            }

            for (EntityAttachment attachment : getAttachments())
                if (attachment.size != null &&
                        (EntityAttachment.PGP_MESSAGE.equals(attachment.encryption) ||
                                EntityAttachment.SMIME_MESSAGE.equals(attachment.encryption) ||
                                EntityAttachment.SMIME_SIGNED_DATA.equals(attachment.encryption)))
                    if (size == null)
                        size = attachment.size;
                    else
                        size += attachment.size;

            return size;
        }

        String getHtml(Context context) throws MessagingException, IOException {
            return getHtml(context, false);
        }

        String getHtml(Context context, boolean plain_text) throws MessagingException, IOException {
            if (text.size() == 0) {
                Log.i("No body part");
                return null;
            }

            StringBuilder sb = new StringBuilder();

            List<PartHolder> parts = new ArrayList<>();

            Integer plain = isPlainOnly();
            if (plain != null && (plain & 1) != 0)
                // Plain only
                parts.addAll(text);
            else {
                // Either plain and HTML or HTML only
                boolean hasPlain = (plain != null && (plain & 0x80) != 0);
                for (PartHolder h : text)
                    if (plain_text && hasPlain) {
                        if (h.isPlainText())
                            parts.add(h);
                    } else {
                        if (h.isHtml())
                            parts.add(h);
                    }
            }

            parts.addAll(extra);

            for (PartHolder h : parts) {
                int size = h.part.getSize();
                if (size > 100 * 1024 * 1024)
                    Log.e("Unreasonable message size=" + size);
                if (size > MAX_MESSAGE_SIZE && size != Integer.MAX_VALUE) {
                    warnings.add(context.getString(R.string.title_insufficient_memory, size));
                    return null;
                }

                String result;

                try {
                    Object content = h.part.getContent();
                    Log.i("Content class=" + (content == null ? null : content.getClass().getName()));

                    if (content == null) {
                        warnings.add(context.getString(R.string.title_no_body));
                        return null;
                    }

                    if (content instanceof String)
                        result = (String) content;
                    else if (content instanceof InputStream) {
                        // java.io.ByteArrayInputStream
                        // Typically com.sun.mail.util.QPDecoderStream
                        if (BuildConfig.DEBUG && false)
                            warnings.add(content.getClass().getName());
                        Charset charset;
                        try {
                            String cs = h.contentType.getParameter("charset");
                            charset = (cs == null ? StandardCharsets.ISO_8859_1 : Charset.forName(cs));
                        } catch (Throwable ex) {
                            Log.w(ex);
                            charset = StandardCharsets.ISO_8859_1;
                        }
                        result = Helper.readStream((InputStream) content, charset);
                    } else {
                        Log.e(content.getClass().getName());
                        result = content.toString();
                    }
                } catch (IOException | FolderClosedException | MessageRemovedException ex) {
                    throw ex;
                } catch (Throwable ex) {
                    Log.e(ex);
                    if (BuildConfig.TEST_RELEASE)
                        warnings.add(ex + "\n" + android.util.Log.getStackTraceString(ex));
                    else
                        warnings.add(Log.formatThrowable(ex, false));
                    return null;
                }

                // Check character set
                String charset = h.contentType.getParameter("charset");
                if (UnknownCharsetProvider.charsetForMime(charset) == null)
                    warnings.add(context.getString(R.string.title_no_charset, charset));

                if ((TextUtils.isEmpty(charset) || charset.equalsIgnoreCase(StandardCharsets.US_ASCII.name())))
                    charset = null;

                Charset cs = null;
                try {
                    if (charset != null)
                        cs = Charset.forName(charset);
                } catch (UnsupportedCharsetException ignored) {
                }

                if (h.isPlainText()) {
                    if (charset == null || StandardCharsets.ISO_8859_1.equals(cs)) {
                        if (StandardCharsets.ISO_8859_1.equals(cs) && CharsetHelper.isUTF8(result)) {
                            Log.i("Charset upgrade=UTF8");
                            result = new String(result.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                        } else {
                            Charset detected = CharsetHelper.detect(result, StandardCharsets.ISO_8859_1);
                            if (detected == null) {
                                if (CharsetHelper.isUTF8(result)) {
                                    Log.i("Charset plain=UTF8");
                                    result = new String(result.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                                }
                            } else {
                                Log.i("Charset plain=" + detected.name());
                                result = new String(result.getBytes(StandardCharsets.ISO_8859_1), detected);
                            }
                        }
                    } else if (StandardCharsets.UTF_8.equals(cs))
                        result = CharsetHelper.utf8toW1252(result);

                    // https://datatracker.ietf.org/doc/html/rfc3676
                    if ("flowed".equalsIgnoreCase(h.contentType.getParameter("format")))
                        result = HtmlHelper.flow(result,
                                "yes".equalsIgnoreCase(h.contentType.getParameter("delsp")));

                    // https://www.w3.org/QA/2002/04/valid-dtd-list.html
                    if (result.length() > DOCTYPE.length()) {
                        String doctype = result.substring(0, DOCTYPE.length()).toUpperCase(Locale.ROOT);
                        if (doctype.startsWith(DOCTYPE)) {
                            String[] words = result.split("\\s+");
                            if (words.length > 1 &&
                                    "HTML".equals(words[1].toUpperCase(Locale.ROOT)))
                                return result;
                        }
                    }

                    int s = 0;
                    while (s < result.length() && Character.isWhitespace(result.charAt(s)))
                        s++;
                    int e = result.length();
                    while (e > 0 && Character.isWhitespace(result.charAt(e - 1)))
                        e--;
                    if (s + HTML_START.length() < result.length() && e - HTML_END.length() >= 0 &&
                            result.substring(s, s + HTML_START.length()).equalsIgnoreCase(HTML_START) &&
                            result.substring(e - HTML_END.length(), e).equalsIgnoreCase(HTML_END))
                        return result;

                    result = "<div x-plain=\"true\">" + HtmlHelper.formatPlainText(result) + "</div>";
                } else if (h.isHtml()) {
                    // Conditionally upgrade to UTF8
                    if ((cs == null ||
                            StandardCharsets.US_ASCII.equals(cs) ||
                            StandardCharsets.ISO_8859_1.equals(cs)) &&
                            CharsetHelper.isUTF8(result))
                        result = new String(result.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);

                    //if (StandardCharsets.UTF_8.equals(cs))
                    //    result = CharsetHelper.utf8w1252(result);

                    // Fix incorrect UTF16
                    try {
                        if (CHARSET16.contains(cs)) {
                            Charset detected = CharsetHelper.detect(result, cs);
                            if (!CHARSET16.contains(detected))
                                Log.w(new Throwable("Charset=" + cs + " detected=" + detected));
                            if (StandardCharsets.US_ASCII.equals(detected) ||
                                    StandardCharsets.UTF_8.equals(detected)) {
                                charset = null;
                                result = new String(result.getBytes(cs), detected);
                            }
                        }
                    } catch (Throwable ex) {
                        Log.w(ex);
                    }

                    if (charset == null) {
                        // <meta charset="utf-8" />
                        // <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
                        String excerpt = result.substring(0, Math.min(MAX_META_EXCERPT, result.length()));
                        Document d = JsoupEx.parse(excerpt);
                        for (Element meta : d.select("meta")) {
                            if ("Content-Type".equalsIgnoreCase(meta.attr("http-equiv"))) {
                                try {
                                    ContentType ct = new ContentType(meta.attr("content"));
                                    charset = ct.getParameter("charset");
                                } catch (ParseException ex) {
                                    Log.w(ex);
                                }
                            } else
                                charset = meta.attr("charset");

                            if (!TextUtils.isEmpty(charset))
                                try {
                                    Log.i("Charset meta=" + meta);
                                    Charset c = Charset.forName(charset);

                                    // US-ASCII is a subset of ISO8859-1
                                    if (StandardCharsets.US_ASCII.equals(c))
                                        break;

                                    // Check if really UTF-8
                                    if (StandardCharsets.UTF_8.equals(c) && !CharsetHelper.isUTF8(result)) {
                                        Log.w("Charset meta=" + meta + " !isUTF8");
                                        break;
                                    }

                                    // 16 bits charsets cannot be converted to 8 bits
                                    if (CHARSET16.contains(c)) {
                                        Log.w("Charset meta=" + meta);
                                        break;
                                    }

                                    Charset detected = CharsetHelper.detect(result, c);
                                    if (c.equals(detected))
                                        break;

                                    // Common detected/meta
                                    // - windows-1250, windows-1257 / ISO-8859-1
                                    // - ISO-8859-1 / windows-1252
                                    // - US-ASCII / windows-1250, windows-1252, ISO-8859-1, ISO-8859-15, UTF-8

                                    if (StandardCharsets.US_ASCII.equals(detected) &&
                                            ("ISO-8859-15".equals(c.name()) ||
                                                    "windows-1250".equals(c.name()) ||
                                                    "windows-1252".equals(c.name()) ||
                                                    StandardCharsets.UTF_8.equals(c) ||
                                                    StandardCharsets.ISO_8859_1.equals(c)))
                                        break;

                                    // Convert
                                    Log.w("Converting detected=" + detected + " meta=" + c);
                                    result = new String(result.getBytes(StandardCharsets.ISO_8859_1), c);
                                    break;
                                } catch (Throwable ex) {
                                    Log.e(ex);
                                }
                        }
                    }
                } else if (h.isReport()) {
                    Report report = new Report(h.contentType.getBaseType(), result);
                    result = report.html;

                    StringBuilder w = new StringBuilder();

                    if (report.isDeliveryStatus() && !report.isDelivered()) {
                        if (report.diagnostic != null) {
                            String diag = report.diagnostic;
                            if (diag.length() > MAX_DIAGNOSTIC)
                                diag = diag.substring(0, MAX_DIAGNOSTIC) + "…";
                            w.append(diag);
                        }
                        if (report.action != null) {
                            if (w.length() == 0)
                                w.append(report.action);
                            else
                                w.append(" (").append(report.action).append(')');
                        }
                    }

                    if (report.isDispositionNotification() && !report.isMdnDisplayed()) {
                        if (report.disposition != null)
                            w.append(report.disposition);
                    }

                    if (w.length() > 0)
                        warnings.add(w.toString());
                } else
                    Log.w("Unexpected content type=" + h.contentType);

                sb.append(result);
            }

            return sb.toString();
        }

        Report getReport() throws MessagingException, IOException {
            for (PartHolder h : extra)
                if (h.isReport()) {
                    String result;
                    Object content = h.part.getContent();
                    if (content instanceof String)
                        result = (String) content;
                    else if (content instanceof InputStream)
                        result = Helper.readStream((InputStream) content);
                    else
                        result = content.toString();
                    return new Report(h.contentType.getBaseType(), result);
                }
            return null;
        }

        List<AttachmentPart> getAttachmentParts() {
            return attachments;
        }

        List<EntityAttachment> getAttachments() {
            List<EntityAttachment> result = new ArrayList<>();
            for (AttachmentPart apart : attachments)
                result.add(apart.attachment);
            return result;
        }

        Integer getEncryption() {
            for (AttachmentPart apart : attachments)
                if (EntityAttachment.PGP_SIGNATURE.equals(apart.attachment.encryption))
                    return EntityMessage.PGP_SIGNONLY;
                else if (EntityAttachment.PGP_MESSAGE.equals(apart.attachment.encryption))
                    return EntityMessage.PGP_SIGNENCRYPT;
                else if (EntityAttachment.SMIME_SIGNATURE.equals(apart.attachment.encryption) ||
                        EntityAttachment.SMIME_SIGNED_DATA.equals(apart.attachment.encryption))
                    return EntityMessage.SMIME_SIGNONLY;
                else if (EntityAttachment.SMIME_MESSAGE.equals(apart.attachment.encryption))
                    return EntityMessage.SMIME_SIGNENCRYPT;
            return null;
        }

        void downloadAttachment(Context context, EntityAttachment local) throws IOException, MessagingException {
            List<EntityAttachment> remotes = getAttachments();

            // Some servers order attachments randomly

            int index = -1;
            boolean warning = false;

            // Get attachment by position
            if (local.sequence <= remotes.size()) {
                EntityAttachment remote = remotes.get(local.sequence - 1);
                if (Objects.equals(remote.name, local.name) &&
                        Objects.equals(remote.type, local.type) &&
                        Objects.equals(remote.disposition, local.disposition) &&
                        Objects.equals(remote.cid, local.cid) &&
                        Objects.equals(remote.size, local.size))
                    index = local.sequence - 1;
            }

            // Match attachment by name/cid
            if (index < 0 && !(local.name == null && local.cid == null)) {
                warning = true;
                Log.w("Matching attachment by name/cid");
                for (int i = 0; i < remotes.size(); i++) {
                    EntityAttachment remote = remotes.get(i);
                    if (Objects.equals(remote.name, local.name) &&
                            Objects.equals(remote.cid, local.cid)) {
                        index = i;
                        break;
                    }
                }
            }

            // Match attachment by type/size
            if (index < 0) {
                warning = true;
                Log.w("Matching attachment by type/size");
                for (int i = 0; i < remotes.size(); i++) {
                    EntityAttachment remote = remotes.get(i);
                    if (Objects.equals(remote.type, local.type) &&
                            Objects.equals(remote.size, local.size)) {
                        index = i;
                        break;
                    }
                }
            }

            if (index < 0 || warning) {
                Map<String, String> crumb = new HashMap<>();
                crumb.put("local", local.toString());
                Log.w("Attachment not found local=" + local);
                for (int i = 0; i < remotes.size(); i++) {
                    EntityAttachment remote = remotes.get(i);
                    crumb.put("remote:" + i, remote.toString());
                    Log.w("Attachment remote=" + remote);
                }
                Log.breadcrumb("attachments", crumb);
            }

            if (index < 0)
                throw new IllegalArgumentException("Attachment not found");

            downloadAttachment(context, index, local);

            if (Helper.isTnef(local.type, local.name))
                decodeTNEF(context, local);

            if ("msg".equalsIgnoreCase(Helper.getExtension(local.name)))
                decodeOutlook(context, local);
        }

        void downloadAttachment(Context context, int index, EntityAttachment local) throws MessagingException, IOException {
            Log.i("downloading attachment id=" + local.id + " index=" + index + " " + local);

            DB db = DB.getInstance(context);

            // Get data
            AttachmentPart apart = attachments.get(index);

            // Download attachment
            File file = EntityAttachment.getFile(context, local.id, local.name);
            db.attachment().setProgress(local.id, 0);

            if (EntityAttachment.PGP_CONTENT.equals(apart.encrypt) ||
                    EntityAttachment.SMIME_CONTENT.equals(apart.encrypt)) {
                ContentType ct = new ContentType(apart.part.getContentType());
                String boundary = ct.getParameter("boundary");
                if (TextUtils.isEmpty(boundary))
                    throw new ParseException("Signed boundary missing");

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                apart.part.writeTo(bos);
                String raw = new String(bos.toByteArray());
                String[] parts = raw.split("\\r?\\n" + Pattern.quote("--" + boundary) + "\\r?\\n");
                if (parts.length < 2)
                    throw new ParseException("Signed part missing");

                // PGP: https://datatracker.ietf.org/doc/html/rfc3156#section-5
                // S/MIME: https://datatracker.ietf.org/doc/html/rfc8551#section-3.1.1
                String c = parts[1]
                        .replaceAll("\\r?\\n", "\r\n"); // normalize new lines
                if (EntityAttachment.PGP_CONTENT.equals(apart.encrypt))
                    c = c.replaceAll(" +$", ""); // trim trailing spaces

                try (OutputStream os = new FileOutputStream(file)) {
                    os.write(c.getBytes());
                }

                db.attachment().setDownloaded(local.id, file.length());
            } else {
                try (InputStream is = apart.part.getInputStream()) {
                    long size = 0;
                    long total = apart.part.getSize();
                    long lastprogress = System.currentTimeMillis();

                    try (OutputStream os = new FileOutputStream(file)) {
                        byte[] buffer = new byte[Helper.BUFFER_SIZE];
                        for (int len = is.read(buffer); len != -1; len = is.read(buffer)) {
                            size += len;
                            os.write(buffer, 0, len);

                            // Update progress
                            if (total > 0) {
                                long now = System.currentTimeMillis();
                                if (now - lastprogress > ATTACHMENT_PROGRESS_UPDATE) {
                                    lastprogress = now;
                                    db.attachment().setProgress(local.id, (int) (size * 100 / total));
                                }
                            }
                        }
                    }

                    // Store attachment data
                    db.attachment().setDownloaded(local.id, size);

                    Log.i("Downloaded attachment size=" + size);
                } catch (FolderClosedIOException ex) {
                    db.attachment().setError(local.id, Log.formatThrowable(ex));
                    throw new FolderClosedException(ex.getFolder(), "downloadAttachment", ex);
                } catch (MessageRemovedIOException ex) {
                    db.attachment().setError(local.id, Log.formatThrowable(ex));
                    throw new MessagingException("downloadAttachment", ex);
                } catch (Throwable ex) {
                    // Reset progress on failure
                    if (ex instanceof IOException)
                        Log.i(ex);
                    else
                        Log.e(ex);
                    db.attachment().setError(local.id, Log.formatThrowable(ex));
                    throw ex;
                }

                if ("message/rfc822".equals(local.type))
                    try (FileInputStream fis = new FileInputStream(local.getFile(context))) {
                        Properties props = MessageHelper.getSessionProperties();
                        Session isession = Session.getInstance(props, null);
                        MimeMessage imessage = new MimeMessage(isession, fis);
                        MessageHelper helper = new MessageHelper(imessage, context);
                        MessageHelper.MessageParts parts = helper.getMessageParts();

                        int subsequence = 1;
                        for (AttachmentPart epart : parts.getAttachmentParts())
                            try {
                                Log.i("Embedded attachment seq=" + local.sequence + ":" + subsequence);
                                epart.attachment.message = local.message;
                                epart.attachment.sequence = local.sequence;
                                epart.attachment.subsequence = subsequence++;
                                epart.attachment.id = db.attachment().insertAttachment(epart.attachment);

                                File efile = epart.attachment.getFile(context);
                                Log.i("Writing to " + efile);

                                try (InputStream is = epart.part.getInputStream()) {
                                    try (OutputStream os = new FileOutputStream(efile)) {
                                        byte[] buffer = new byte[Helper.BUFFER_SIZE];
                                        for (int len = is.read(buffer); len != -1; len = is.read(buffer))
                                            os.write(buffer, 0, len);
                                    }
                                }

                                db.attachment().setDownloaded(epart.attachment.id, efile.length());
                            } catch (Throwable ex) {
                                db.attachment().setError(epart.attachment.id, Log.formatThrowable(ex));
                                db.attachment().setAvailable(epart.attachment.id, true); // unrecoverable
                            }
                    } catch (Throwable ex) {
                        Log.e(ex);
                    }
            }
        }

        private void decodeTNEF(Context context, EntityAttachment local) {
            try {
                DB db = DB.getInstance(context);
                int subsequence = 0;

                // https://poi.apache.org/components/hmef/index.html
                File file = local.getFile(context);
                org.apache.poi.hmef.HMEFMessage msg = new org.apache.poi.hmef.HMEFMessage(new FileInputStream(file));

                String subject = msg.getSubject();
                if (!TextUtils.isEmpty(subject)) {
                    EntityAttachment attachment = new EntityAttachment();
                    attachment.message = local.message;
                    attachment.sequence = local.sequence;
                    attachment.subsequence = ++subsequence;
                    attachment.name = "subject.txt";
                    attachment.type = "text/plain";
                    attachment.disposition = Part.ATTACHMENT;
                    attachment.id = db.attachment().insertAttachment(attachment);

                    Helper.writeText(attachment.getFile(context), subject);
                    db.attachment().setDownloaded(attachment.id, (long) subject.length());
                }

                String body = msg.getBody();
                if (TextUtils.isEmpty(body)) {
                    org.apache.poi.hmef.attribute.MAPIAttribute attr =
                            msg.getMessageMAPIAttribute(org.apache.poi.hsmf.datatypes.MAPIProperty.BODY_HTML);
                    if (attr == null)
                        attr = msg.getMessageMAPIAttribute(org.apache.poi.hsmf.datatypes.MAPIProperty.BODY);
                    if (attr != null) {
                        EntityAttachment attachment = new EntityAttachment();
                        attachment.message = local.message;
                        attachment.sequence = local.sequence;
                        attachment.subsequence = ++subsequence;
                        if (attr.getProperty().equals(org.apache.poi.hsmf.datatypes.MAPIProperty.BODY_HTML)) {
                            attachment.name = "body.html";
                            attachment.type = "text/html";
                        } else {
                            attachment.name = "body.txt";
                            attachment.type = "text/plain";
                        }
                        attachment.disposition = Part.ATTACHMENT;
                        attachment.id = db.attachment().insertAttachment(attachment);

                        byte[] data = attr.getData();
                        Helper.writeText(attachment.getFile(context), new String(data));
                        db.attachment().setDownloaded(attachment.id, (long) data.length);
                    }
                } else {
                    EntityAttachment attachment = new EntityAttachment();
                    attachment.message = local.message;
                    attachment.sequence = local.sequence;
                    attachment.subsequence = ++subsequence;
                    attachment.name = "body.rtf";
                    attachment.type = "application/rtf";
                    attachment.disposition = Part.ATTACHMENT;
                    attachment.id = db.attachment().insertAttachment(attachment);

                    Helper.writeText(attachment.getFile(context), body);
                    db.attachment().setDownloaded(attachment.id, (long) body.length());
                }

                for (org.apache.poi.hmef.Attachment at : msg.getAttachments())
                    try {
                        String filename = at.getLongFilename();
                        if (filename == null)
                            filename = at.getFilename();
                        if (filename == null) {
                            String ext = at.getExtension();
                            if (ext != null)
                                filename = "document." + ext;
                        }

                        EntityAttachment attachment = new EntityAttachment();
                        attachment.message = local.message;
                        attachment.sequence = local.sequence;
                        attachment.subsequence = ++subsequence;
                        attachment.name = filename;
                        attachment.type = Helper.guessMimeType(attachment.name);
                        attachment.disposition = Part.ATTACHMENT;
                        attachment.id = db.attachment().insertAttachment(attachment);

                        byte[] data = at.getContents();
                        try (OutputStream os = new FileOutputStream(attachment.getFile(context))) {
                            os.write(data);
                        }

                        db.attachment().setDownloaded(attachment.id, (long) data.length);
                    } catch (Throwable ex) {
                        // java.lang.IllegalArgumentException: Attachment corrupt - no Data section
                        Log.e(ex);
                    }

                StringBuilder sb = new StringBuilder();
                for (org.apache.poi.hmef.attribute.TNEFAttribute attr : msg.getMessageAttributes())
                    sb.append(attr.toString()).append("\r\n");
                for (org.apache.poi.hmef.attribute.MAPIAttribute attr : msg.getMessageMAPIAttributes())
                    if (!org.apache.poi.hsmf.datatypes.MAPIProperty.RTF_COMPRESSED.equals(attr.getProperty()) &&
                            !org.apache.poi.hsmf.datatypes.MAPIProperty.BODY_HTML.equals(attr.getProperty()))
                        sb.append(attr.toString()).append("\r\n");
                if (sb.length() > 0) {
                    EntityAttachment attachment = new EntityAttachment();
                    attachment.message = local.message;
                    attachment.sequence = local.sequence;
                    attachment.subsequence = ++subsequence;
                    attachment.name = "attributes.txt";
                    attachment.type = "text/plain";
                    attachment.disposition = Part.ATTACHMENT;
                    attachment.id = db.attachment().insertAttachment(attachment);

                    Helper.writeText(attachment.getFile(context), sb.toString());
                    db.attachment().setDownloaded(attachment.id, (long) sb.length());
                }
            } catch (Throwable ex) {
                Log.w(ex);
            }
        }

        private void decodeOutlook(Context context, EntityAttachment local) {
            try {
                DB db = DB.getInstance(context);
                int subsequence = 0;

                // https://poi.apache.org/components/hmef/index.html
                File file = local.getFile(context);
                OutlookMessage msg = new OutlookMessageParser().parseMsg(file);

                String headers = msg.getHeaders();
                if (!TextUtils.isEmpty(headers)) {
                    EntityAttachment attachment = new EntityAttachment();
                    attachment.message = local.message;
                    attachment.sequence = local.sequence;
                    attachment.subsequence = ++subsequence;
                    attachment.name = "headers.txt";
                    attachment.type = "text/rfc822-headers";
                    attachment.disposition = Part.ATTACHMENT;
                    attachment.id = db.attachment().insertAttachment(attachment);

                    Helper.writeText(attachment.getFile(context), headers);
                    db.attachment().setDownloaded(attachment.id, (long) headers.length());
                }

                String html = msg.getBodyHTML();
                if (!TextUtils.isEmpty(html)) {
                    EntityAttachment attachment = new EntityAttachment();
                    attachment.message = local.message;
                    attachment.sequence = local.sequence;
                    attachment.subsequence = ++subsequence;
                    attachment.name = "body.html";
                    attachment.type = "text/html";
                    attachment.disposition = Part.ATTACHMENT;
                    attachment.id = db.attachment().insertAttachment(attachment);

                    File a = attachment.getFile(context);
                    Helper.writeText(a, html);
                    db.attachment().setDownloaded(attachment.id, a.length());
                }

                if (TextUtils.isEmpty(html)) {
                    String text = msg.getBodyText();
                    if (!TextUtils.isEmpty(text)) {
                        EntityAttachment attachment = new EntityAttachment();
                        attachment.message = local.message;
                        attachment.sequence = local.sequence;
                        attachment.subsequence = ++subsequence;
                        attachment.name = "body.txt";
                        attachment.type = "text/plain";
                        attachment.disposition = Part.ATTACHMENT;
                        attachment.id = db.attachment().insertAttachment(attachment);

                        File a = attachment.getFile(context);
                        Helper.writeText(a, text);
                        db.attachment().setDownloaded(attachment.id, a.length());
                    }
                }

                String rtf = msg.getBodyRTF();
                if (!TextUtils.isEmpty(rtf)) {
                    EntityAttachment attachment = new EntityAttachment();
                    attachment.message = local.message;
                    attachment.sequence = local.sequence;
                    attachment.subsequence = ++subsequence;
                    attachment.name = "body.rtf";
                    attachment.type = "application/rtf";
                    attachment.disposition = Part.ATTACHMENT;
                    attachment.id = db.attachment().insertAttachment(attachment);

                    File a = attachment.getFile(context);
                    Helper.writeText(a, rtf);
                    db.attachment().setDownloaded(attachment.id, a.length());
                }

                List<OutlookAttachment> attachments = msg.getOutlookAttachments();
                for (OutlookAttachment oa : attachments)
                    if (oa instanceof OutlookFileAttachment) {
                        OutlookFileAttachment ofa = (OutlookFileAttachment) oa;

                        EntityAttachment attachment = new EntityAttachment();
                        attachment.message = local.message;
                        attachment.sequence = local.sequence;
                        attachment.subsequence = ++subsequence;
                        attachment.name = ofa.getFilename();
                        attachment.type = ofa.getMimeTag();
                        attachment.disposition = Part.ATTACHMENT;
                        attachment.id = db.attachment().insertAttachment(attachment);

                        if (TextUtils.isEmpty(attachment.type))
                            attachment.type = Helper.guessMimeType(attachment.name);

                        byte[] data = ofa.getData();
                        try (OutputStream os = new FileOutputStream(attachment.getFile(context))) {
                            os.write(data);
                        }

                        db.attachment().setDownloaded(attachment.id, (long) data.length);
                    }

            } catch (Throwable ex) {
                Log.w(ex);
            }
        }

        String getWarnings(String existing) {
            if (existing != null)
                warnings.add(0, existing);
            if (warnings.size() == 0)
                return null;
            else
                return TextUtils.join(", ", warnings);
        }
    }

    class AttachmentPart {
        String disposition;
        String filename;
        Integer encrypt;
        Part part;
        EntityAttachment attachment;
    }

    MessageParts getMessageParts() throws IOException, MessagingException {
        return getMessageParts(true);
    }

    MessageParts getMessageParts(boolean normalize) throws IOException, MessagingException {
        MessageParts parts = new MessageParts();

        try {
            ensureStructure();

            try {
                MimePart part = imessage;

                if (part.isMimeType("multipart/mixed")) {
                    Object content = part.getContent();
                    if (content instanceof Multipart) {
                        Multipart mp = (Multipart) content;
                        for (int i = 0; i < mp.getCount(); i++) {
                            BodyPart bp = mp.getBodyPart(i);
                            if (bp.isMimeType("multipart/signed") || bp.isMimeType("multipart/encrypted")) {
                                part = (MimePart) bp;
                                break;
                            }
                        }
                    } else
                        throw new MessagingStructureException(content);
                }

                if (part.isMimeType("multipart/signed")) {
                    ContentType ct = new ContentType(part.getContentType());
                    String protocol = ct.getParameter("protocol");
                    if ("application/pgp-signature".equals(protocol) ||
                            "application/pkcs7-signature".equals(protocol) ||
                            "application/x-pkcs7-signature".equals(protocol)) {
                        Object content = part.getContent();
                        if (content instanceof Multipart) {
                            Multipart multipart = (Multipart) content;
                            if (multipart.getCount() == 2) {
                                getMessageParts(part, multipart.getBodyPart(0), parts, null);
                                getMessageParts(part, multipart.getBodyPart(1), parts,
                                        "application/pgp-signature".equals(protocol)
                                                ? EntityAttachment.PGP_SIGNATURE
                                                : EntityAttachment.SMIME_SIGNATURE);

                                AttachmentPart apart = new AttachmentPart();
                                apart.disposition = Part.INLINE;
                                apart.filename = "content.asc";
                                apart.encrypt = "application/pgp-signature".equals(protocol)
                                        ? EntityAttachment.PGP_CONTENT
                                        : EntityAttachment.SMIME_CONTENT;
                                apart.part = part;

                                apart.attachment = new EntityAttachment();
                                apart.attachment.disposition = apart.disposition;
                                apart.attachment.name = apart.filename;
                                apart.attachment.type = "text/plain";
                                apart.attachment.size = getSize();
                                apart.attachment.encryption = apart.encrypt;

                                parts.attachments.add(apart);

                                return parts;
                            } else {
                                StringBuilder sb = new StringBuilder();
                                sb.append(ct).append(" parts=").append(multipart.getCount()).append("/2");
                                for (int i = 0; i < multipart.getCount(); i++)
                                    sb.append(' ').append(i).append('=').append(multipart.getBodyPart(i).getContentType());
                                Log.e(sb.toString());
                            }
                        } else
                            throw new MessagingStructureException(content);
                    } else
                        Log.e(ct.toString());
                } else if (part.isMimeType("multipart/encrypted")) {
                    ContentType ct = new ContentType(part.getContentType());
                    String protocol = ct.getParameter("protocol");
                    if ("application/pgp-encrypted".equals(protocol) || protocol == null) {
                        Object content = part.getContent();
                        if (content instanceof Multipart) {
                            Multipart multipart = (Multipart) content;
                            if (multipart.getCount() == 2) {
                                // Ignore header
                                getMessageParts(part, multipart.getBodyPart(1), parts, EntityAttachment.PGP_MESSAGE);
                                return parts;
                            } else {
                                StringBuilder sb = new StringBuilder();
                                sb.append(ct).append(" parts=").append(multipart.getCount()).append("/2");
                                for (int i = 0; i < multipart.getCount(); i++)
                                    sb.append(' ').append(i).append('=').append(multipart.getBodyPart(i).getContentType());
                                Log.e(sb.toString());
                            }
                        } else
                            throw new MessagingStructureException(content);
                    } else
                        Log.e(ct.toString());
                } else if (part.isMimeType("application/pkcs7-mime") ||
                        part.isMimeType("application/x-pkcs7-mime")) {
                    ContentType ct = new ContentType(part.getContentType());
                    String smimeType = ct.getParameter("smime-type");
                    if ("enveloped-data".equalsIgnoreCase(smimeType)) {
                        getMessageParts(null, part, parts, EntityAttachment.SMIME_MESSAGE);
                        return parts;
                    } else if ("signed-data".equalsIgnoreCase(smimeType)) {
                        getMessageParts(null, part, parts, EntityAttachment.SMIME_SIGNED_DATA);
                        return parts;
                    } else if ("signed-receipt".equalsIgnoreCase(smimeType)) {
                        // https://datatracker.ietf.org/doc/html/rfc2634#section-2
                    } else {
                        if (TextUtils.isEmpty(smimeType)) {
                            String name = ct.getParameter("name");
                            if ("smime.p7m".equalsIgnoreCase(name)) {
                                getMessageParts(null, part, parts, EntityAttachment.SMIME_MESSAGE);
                                return parts;
                            } else if ("smime.p7s".equalsIgnoreCase(name)) {
                                getMessageParts(null, part, parts, EntityAttachment.SMIME_SIGNED_DATA);
                                return parts;
                            }
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append("Unexpected smime-type=").append(ct);
                        Log.e(sb.toString());
                    }
                }
            } catch (ParseException ex) {
                Log.w(ex);
            }

            getMessageParts(null, imessage, parts, null);
        } catch (OutOfMemoryError ex) {
            Log.e(ex);
            parts.warnings.add(Log.formatThrowable(ex, false));
            /*
                java.lang.OutOfMemoryError: Failed to allocate a xxx byte allocation with yyy free bytes and zzMB until OOM
                        at java.io.ByteArrayOutputStream.expand(ByteArrayOutputStream.java:91)
                        at java.io.ByteArrayOutputStream.write(ByteArrayOutputStream.java:201)
                        at com.sun.mail.util.ASCIIUtility.getBytes(ASCIIUtility:279)
                        at javax.mail.internet.MimeMessage.parse(MimeMessage:336)
                        at javax.mail.internet.MimeMessage.<init>(MimeMessage:199)
                        at eu.faircode.email.MimeMessageEx.<init>(MimeMessageEx:44)
                        at eu.faircode.email.MessageHelper._ensureMessage(MessageHelper:2732)
                        at eu.faircode.email.MessageHelper.ensureStructure(MessageHelper:2685)
                        at eu.faircode.email.MessageHelper.getMessageParts(MessageHelper:2368)
             */
        }

        if (normalize)
            parts.normalize();

        return parts;
    }

    private void getMessageParts(Part parent, Part part, MessageParts parts, Integer encrypt) throws IOException, MessagingException {
        try {
            Log.d("Part class=" + part.getClass() + " type=" + part.getContentType());

            // https://github.com/autocrypt/protected-headers
            try {
                ContentType ct = new ContentType(part.getContentType());
                if ("v1".equals(ct.getParameter("protected-headers"))) {
                    String[] subject = part.getHeader("subject");
                    if (subject != null && subject.length != 0) {
                        subject[0] = subject[0].replaceAll("\\?=[\\r\\n\\t ]+=\\?", "\\?==\\?");
                        parts.protected_subject = decodeMime(subject[0]);
                    }
                }
            } catch (Throwable ex) {
                Log.e(ex);
            }

            if (part.isMimeType("multipart/*")) {
                Multipart multipart;
                Object content = part.getContent(); // Should always be Multipart
                if (content instanceof Multipart)
                    multipart = (Multipart) part.getContent();
                else
                    throw new MessagingStructureException(content);

                int count = multipart.getCount();
                for (int i = 0; i < count; i++)
                    try {
                        BodyPart child = multipart.getBodyPart(i);
                        getMessageParts(part, child, parts, encrypt);
                    } catch (ParseException ex) {
                        // Nested body: try to continue
                        // ParseException: In parameter list boundary="...">, expected parameter name, got ";"
                        Log.w(ex);
                        parts.warnings.add(Log.formatThrowable(ex, false));
                    }
            } else {
                // https://www.iana.org/assignments/cont-disp/cont-disp.xhtml
                String disposition;
                try {
                    // From the body structure
                    disposition = part.getDisposition();
                    if (disposition != null)
                        disposition = disposition.toLowerCase(Locale.ROOT);
                } catch (MessagingException ex) {
                    Log.w(ex);
                    parts.warnings.add(Log.formatThrowable(ex, false));
                    disposition = null;
                }

                String filename;
                try {
                    // From the body structure:
                    // 1. disposition filename
                    // 2. content type name
                    filename = part.getFileName(); // IMAPBodyPart/BODYSTRUCTURE
                    if (filename != null) {
                        // https://tools.ietf.org/html/rfc2231
                        // http://kb.mozillazine.org/Attachments_renamed
                        // https://blog.nodemailer.com/2017/01/27/the-mess-that-is-attachment-filenames/
                        int q1 = filename.indexOf('\'');
                        int q2 = filename.indexOf('\'', q1 + 1);
                        if (q1 >= 0 && q2 > 0) {
                            try {
                                String charset = filename.substring(0, q1);
                                String language = filename.substring(q1 + 1, q2);
                                String name = filename.substring(q2 + 1)
                                        .replace("+", "%2B");

                                if (!TextUtils.isEmpty(charset))
                                    filename = URLDecoder.decode(name, charset);
                            } catch (Throwable ex) {
                                Log.e(ex);
                            }
                        }

                        filename = decodeMime(filename);
                    }
                } catch (MessagingException ex) {
                    Log.w(ex);
                    parts.warnings.add(Log.formatThrowable(ex, false));
                    filename = null;
                }

                ContentType contentType;
                try {
                    // From the body structure
                    contentType = new ContentType(part.getContentType());
                } catch (ParseException ex) {
                    if (part instanceof MimeMessage)
                        Log.w("MimeMessage content type=" + ex.getMessage());
                    else
                        Log.w(ex);
                    contentType = new ContentType(Helper.guessMimeType(filename));
                }

                String ct = contentType.getBaseType();
                if (("text/plain".equalsIgnoreCase(ct) || "text/html".equalsIgnoreCase(ct)) &&
                        !Part.ATTACHMENT.equalsIgnoreCase(disposition) && TextUtils.isEmpty(filename)) {
                    parts.text.add(new PartHolder(part, contentType));
                } else {
                    if (Report.isDeliveryStatus(ct) || Report.isDispositionNotification(ct))
                        parts.extra.add(new PartHolder(part, contentType));

                    AttachmentPart apart = new AttachmentPart();
                    apart.disposition = disposition;
                    apart.filename = filename;
                    apart.encrypt = encrypt;
                    apart.part = part;

                    String cid = null;
                    try {
                        if (apart.part instanceof IMAPBodyPart)
                            cid = ((IMAPBodyPart) apart.part).getContentID();
                        if (TextUtils.isEmpty(cid)) {
                            String[] cids = apart.part.getHeader("Content-ID");
                            if (cids != null && cids.length > 0)
                                cid = MimeUtility.unfold(cids[0]);
                        }
                    } catch (MessagingException ex) {
                        Log.w(ex);
                        if (!"Failed to fetch headers".equals(ex.getMessage()))
                            parts.warnings.add(Log.formatThrowable(ex, false));
                    }

                    Boolean related = null;
                    if (parent != null)
                        try {
                            related = parent.isMimeType("multipart/related");
                        } catch (MessagingException ex) {
                            Log.w(ex);
                        }

                    apart.attachment = new EntityAttachment();
                    apart.attachment.disposition = apart.disposition;
                    apart.attachment.name = apart.filename;
                    apart.attachment.type = contentType.getBaseType().toLowerCase(Locale.ROOT);
                    apart.attachment.size = (long) apart.part.getSize();
                    apart.attachment.cid = cid;
                    apart.attachment.related = related;
                    apart.attachment.encryption = apart.encrypt;

                    if ("text/calendar".equalsIgnoreCase(apart.attachment.type) &&
                            TextUtils.isEmpty(apart.attachment.name))
                        apart.attachment.name = "invite.ics";

                    if (apart.attachment.size <= 0)
                        apart.attachment.size = null;

                    // https://tools.ietf.org/html/rfc2392
                    if (apart.attachment.cid != null) {
                        if (!apart.attachment.cid.startsWith("<"))
                            apart.attachment.cid = "<" + apart.attachment.cid;
                        if (!apart.attachment.cid.endsWith(">"))
                            apart.attachment.cid += ">";
                    }

                    parts.attachments.add(apart);
                }
            }
        } catch (FolderClosedException ex) {
            throw ex;
        } catch (MessagingException ex) {
            if (ex instanceof ParseException)
                Log.e(ex);
            else
                Log.w(ex);
            parts.warnings.add(Log.formatThrowable(ex, false));
        }
    }

    private void ensureEnvelope() throws MessagingException {
        _ensureMessage(false, false);
    }

    private void ensureHeaders() throws MessagingException {
        _ensureMessage(false, true);
    }

    private void ensureStructure() throws MessagingException {
        _ensureMessage(true, true);
    }

    private void _ensureMessage(boolean structure, boolean headers) throws MessagingException {
        if (structure) {
            if (ensuredStructure)
                return;
            ensuredStructure = true;
        } else if (headers) {
            if (ensuredHeaders)
                return;
            ensuredHeaders = true;
        } else {
            if (ensuredEnvelope)
                return;
            ensuredEnvelope = true;
        }

        Log.i("Ensure structure=" + structure + " headers=" + headers);

        try {
            if (imessage instanceof IMAPMessage) {
                if (structure)
                    imessage.getContentType(); // force loadBODYSTRUCTURE
                else {
                    if (headers)
                        imessage.getAllHeaders(); // force loadHeaders
                    else
                        imessage.getMessageID(); // force loadEnvelope
                }
            }
        } catch (MessagingException ex) {
            // https://javaee.github.io/javamail/FAQ#imapserverbug
            if ("Failed to load IMAP envelope".equals(ex.getMessage()) ||
                    "Unable to load BODYSTRUCTURE".equals(ex.getMessage()))
                try {
                    if (false)
                        ((IMAPFolder) imessage.getFolder()).doCommand(new IMAPFolder.ProtocolCommand() {
                            @Override
                            public Object doCommand(IMAPProtocol p) throws ProtocolException {
                                MessageSet[] set = Utility.toMessageSet(new Message[]{imessage}, null);
                                Response[] r = p.fetch(set, p.isREV1() ? "BODY.PEEK[]" : "RFC822");
                                p.notifyResponseHandlers(r);
                                p.handleResult(r[r.length - 1]);
                                return null;
                            }
                        });

                    Log.w("Fetching raw message");
                    File file = File.createTempFile("serverbug", null, cacheDir);
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file))) {
                        imessage.writeTo(os);
                    }

                    if (file.length() == 0)
                        throw new IOException("NIL");

                    Properties props = MessageHelper.getSessionProperties();
                    Session isession = Session.getInstance(props, null);

                    Log.w("Decoding raw message");
                    try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                        imessage = new MimeMessageEx(isession, is, imessage);
                    }

                    file.delete();
                } catch (IOException ex1) {
                    Log.e(ex1);
                    throw ex;
                }
            else
                throw ex;
        }
    }

    static int getMessageCount(Folder folder) {
        try {
            // Prevent pool lock
            if (folder instanceof IMAPFolder) {
                int count = ((IMAPFolder) folder).getCachedCount();
                Log.i(folder.getFullName() + " total count=" + count);
                return count;
            }

            int count = 0;
            for (Message message : folder.getMessages())
                if (!message.isExpunged())
                    count++;

            return count;
        } catch (Throwable ex) {
            Log.e(ex);
            return -1;
        }
    }

    static boolean hasCapability(IMAPFolder ifolder, final String capability) throws MessagingException {
        // Folder can have different capabilities than the store
        return (boolean) ifolder.doCommand(new IMAPFolder.ProtocolCommand() {
            @Override
            public Object doCommand(IMAPProtocol protocol) throws ProtocolException {
                return protocol.hasCapability(capability);
            }
        });
    }

    static String sanitizeKeyword(String keyword) {
        // https://tools.ietf.org/html/rfc3501
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keyword.length(); i++) {
            // flag-keyword    = atom
            // atom            = 1*ATOM-CHAR
            // ATOM-CHAR       = <any CHAR except atom-specials>
            char kar = keyword.charAt(i);
            // atom-specials   = "(" / ")" / "{" / SP / CTL / list-wildcards / quoted-specials / resp-specials
            if (kar == '(' || kar == ')' || kar == '{' || kar == ' ' || Character.isISOControl(kar))
                continue;
            // list-wildcards  = "%" / "*"
            if (kar == '%' || kar == '*')
                continue;
            // quoted-specials = DQUOTE / "\"
            if (kar == '"' || kar == '\\')
                continue;
            // resp-specials   = "]"
            if (kar == ']')
                continue;
            sb.append(kar);
        }

        return Normalizer.normalize(sb.toString(), Normalizer.Form.NFKD)
                .replaceAll("[^\\p{ASCII}]", "");
    }

    static String sanitizeEmail(String email) {
        if (email == null)
            return null;

        if (email.contains("<") && email.contains(">"))
            try {
                InternetAddress address = new InternetAddress(email);
                return address.getAddress();
            } catch (AddressException ex) {
                Log.e(ex);
            }

        return email;
    }

    static InternetAddress[] parseAddresses(Context context, String text) throws AddressException {
        if (TextUtils.isEmpty(text))
            return null;

        int skip = 0;
        StringBuilder sb = new StringBuilder();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char kar = text.charAt(i);
            if (kar == '(' && text.indexOf(')', i) > 0)
                skip++;
            else if (kar == ')' && skip > 0)
                skip--;
            else if (skip == 0)
                sb.append(kar);
        }
        text = sb.toString();

        InternetAddress[] addresses = InternetAddress.parseHeader(text, false);
        if (addresses.length == 0)
            return null;

        for (InternetAddress address : addresses) {
            String email = address.getAddress();
            if (email != null)
                address.setAddress(email.replace(" ", ""));
        }

        return addresses;
    }

    static InternetAddress[] dedup(InternetAddress[] addresses) {
        if (addresses == null)
            return null;

        List<String> emails = new ArrayList<>();
        List<Address> result = new ArrayList<>();
        for (InternetAddress address : addresses) {
            String email = address.getAddress();
            if (!emails.contains(email)) {
                emails.add(email);
                result.add(address);
            }
        }

        return result.toArray(new InternetAddress[0]);
    }

    static boolean isRemoved(Throwable ex) {
        while (ex != null) {
            if (ex instanceof MessageRemovedException ||
                    ex instanceof MessageRemovedIOException)
                return true;
            ex = ex.getCause();
        }
        return false;
    }

    static boolean isNoReply(Address address) {
        if (address instanceof InternetAddress) {
            String email = ((InternetAddress) address).getAddress();
            String username = UriHelper.getEmailUser(email);
            String domain = UriHelper.getEmailDomain(email);

            if (!TextUtils.isEmpty(username)) {
                username = username.toLowerCase(Locale.ROOT);
                for (String value : DO_NOT_REPLY)
                    if (username.contains(value))
                        return true;
            }
            if (!TextUtils.isEmpty(domain)) {
                domain = domain.toLowerCase(Locale.ROOT);
                for (String value : DO_NOT_REPLY)
                    if (domain.startsWith(value))
                        return true;
            }
        }

        return false;
    }

    static boolean equalEmail(Address a1, Address a2) {
        String email1 = ((InternetAddress) a1).getAddress();
        String email2 = ((InternetAddress) a2).getAddress();
        if (email1 != null)
            email1 = email1.toLowerCase(Locale.ROOT);
        if (email2 != null)
            email2 = email2.toLowerCase(Locale.ROOT);
        return Objects.equals(email1, email2);
    }

    static boolean equalEmail(Address[] a1, Address[] a2) {
        if (a1 == null && a2 == null)
            return true;

        if (a1 == null || a2 == null)
            return false;

        if (a1.length != a2.length)
            return false;

        for (int i = 0; i < a1.length; i++)
            if (!equalEmail(a1[i], a2[i]))
                return false;

        return true;
    }

    static String[] equalDomain(Context context, Address[] a1, Address[] a2) {
        if (a1 == null || a1.length == 0)
            return null;
        if (a2 == null || a2.length == 0)
            return null;

        for (Address _a1 : a1) {
            String r = UriHelper.getEmailDomain(((InternetAddress) _a1).getAddress());
            if (r == null)
                continue;
            String d1 = UriHelper.getParentDomain(context, r);

            for (Address _a2 : a2) {
                String f = UriHelper.getEmailDomain(((InternetAddress) _a2).getAddress());
                if (f == null)
                    continue;
                String d2 = UriHelper.getParentDomain(context, f);

                if (!d1.equalsIgnoreCase(d2))
                    return new String[]{d2, d1};
            }
        }

        return null;
    }

    static boolean equal(Address[] a1, Address[] a2) {
        if (a1 == null && a2 == null)
            return true;

        if (a1 == null || a2 == null)
            return false;

        if (a1.length != a2.length)
            return false;

        for (int i = 0; i < a1.length; i++)
            if (!a1[i].toString().equals(a2[i].toString()))
                return false;

        return true;
    }

    static Map<String, String> getKeyValues(String value) {
        Map<String, String> values = new HashMap<>();
        if (TextUtils.isEmpty(value))
            return values;

        String[] params = value.split(";");
        for (String param : params) {
            String k, v;
            int eq = param.indexOf('=');
            if (eq < 0) {
                k = param.trim().toLowerCase(Locale.ROOT);
                v = "";
            } else {
                k = param.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                v = param.substring(eq + 1).trim();
            }
            values.put(k, v);
        }

        return values;
    }

    static class MessagingStructureException extends MessagingException {
        private String className;

        MessagingStructureException(Object content) {
            super();
            if (content != null)
                this.className = content.getClass().getName();
        }

        @Nullable
        @Override
        public String getMessage() {
            return className;
        }
    }

    static class Report {
        String type;
        String reporter;
        String action;
        String recipient;
        String status;
        String diagnostic;
        String disposition;
        String html;

        Report(String type, String content) {
            this.type = type;
            StringBuilder report = new StringBuilder();
            report.append("<hr><div style=\"font-family: monospace; font-size: small;\">");
            content = content.replaceAll("(\\r?\\n)+", "\n");
            ByteArrayInputStream bis = new ByteArrayInputStream(content.getBytes());
            try {
                Enumeration<Header> headers = new InternetHeaders(bis).getAllHeaders();
                while (headers.hasMoreElements()) {
                    Header header = headers.nextElement();
                    String name = header.getName();
                    String value = header.getValue();
                    value = decodeMime(value);
                    report
                            .append("<strong>")
                            .append(TextUtils.htmlEncode(name))
                            .append("</strong>")
                            .append(": ")
                            .append(TextUtils.htmlEncode(value))
                            .append("<br>");

                    if (isDeliveryStatus(type)) {
                        // https://datatracker.ietf.org/doc/html/rfc3464#section-2.3
                        switch (name) {
                            case "Reporting-MTA":
                                this.reporter = value;
                                break;
                            case "Action":
                                this.action = value;
                                break;
                            case "Final-Recipient":
                                this.recipient = value;
                                break;
                            case "Status":
                                this.status = value;
                                break;
                            case "Diagnostic-Code":
                                this.diagnostic = value;
                                break;
                        }
                    } else if (isDispositionNotification(type)) {
                        //https://datatracker.ietf.org/doc/html/rfc3798#section-3.2.6
                        switch (name) {
                            case "Reporting-UA":
                                this.reporter = value;
                                break;
                            case "Original-Recipient":
                                this.recipient = value;
                                break;
                            case "Disposition":
                                this.disposition = value;
                                break;
                        }
                    }
                }
            } catch (Throwable ex) {
                Log.e(ex);
                report.append(TextUtils.htmlEncode(ex.toString()));
            }
            report.append("</div>");
            this.html = report.toString();
        }

        boolean isDeliveryStatus() {
            return isDeliveryStatus(type);
        }

        boolean isDispositionNotification() {
            return isDispositionNotification(type);
        }

        boolean isDelivered() {
            return ("delivered".equals(action) || "relayed".equals(action) || "expanded".equals(action));
        }

        boolean isMdnManual() {
            return "manual-action".equals(getAction(0));
        }

        boolean isMdnAutomatic() {
            return "automatic-action".equals(getAction(0));
        }

        boolean isMdnManualSent() {
            return "MDN-sent-manually".equals(getAction(1));
        }

        boolean isMdnAutomaticSent() {
            return "MDN-sent-automatically".equals(getAction(1));
        }

        boolean isMdnDisplayed() {
            return "displayed".equalsIgnoreCase(getType());
        }

        boolean isMdnDeleted() {
            return "deleted".equalsIgnoreCase(getType());
        }

        private String getAction(int index) {
            if (disposition == null)
                return null;
            int semi = disposition.lastIndexOf(';');
            if (semi < 0)
                return null;
            String[] action = disposition.substring(0, semi).trim().split("/");
            return (index < action.length ? action[index] : null);
        }

        private String getType() {
            // manual-action/MDN-sent-manually; displayed
            if (disposition == null)
                return null;
            int semi = disposition.lastIndexOf(';');
            if (semi < 0)
                return null;
            return disposition.substring(semi + 1).trim();
        }

        static boolean isDeliveryStatus(String type) {
            return "message/delivery-status".equalsIgnoreCase(type);
        }

        static boolean isDispositionNotification(String type) {
            return "message/disposition-notification".equalsIgnoreCase(type);
        }
    }
}
