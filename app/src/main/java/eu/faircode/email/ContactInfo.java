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

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLSession;

public class ContactInfo {
    private String email;
    private Bitmap bitmap;
    private String type;
    private boolean verified;
    private String displayName;
    private Uri lookupUri;
    private boolean known;
    private long time;

    private static Map<String, Lookup> emailLookup = new ConcurrentHashMap<>();
    private static final Map<String, ContactInfo> emailContactInfo = new HashMap<>();

    private static final ExecutorService executorLookup =
            Helper.getBackgroundExecutor(1, "contact");

    private static final ExecutorService executorFavicon =
            Helper.getBackgroundExecutor(0, "favicon");

    private static final int GENERATED_ICON_SIZE = 96; // dp
    private static final int FAVICON_ICON_SIZE = 64; // dp
    private static final int GRAVATAR_TIMEOUT = 5 * 1000; // milliseconds
    private static final int FAVICON_CONNECT_TIMEOUT = 5 * 1000; // milliseconds
    private static final int FAVICON_READ_TIMEOUT = 10 * 1000; // milliseconds
    private static final int FAVICON_READ_BYTES = 4096;
    private static final long CACHE_CONTACT_DURATION = 2 * 60 * 1000L; // milliseconds
    private static final long CACHE_FAVICON_DURATION = 2 * 7 * 24 * 60 * 60 * 1000L; // milliseconds

    // https://css-tricks.com/prefetching-preloading-prebrowsing/
    // https://developer.mozilla.org/en-US/docs/Web/Performance/dns-prefetch
    private static final List<String> REL_EXCLUDE = Collections.unmodifiableList(Arrays.asList(
            "dns-prefetch", "preconnect", "prefetch", "preload", "prerender", "subresource",
            "respond-redirect"
    ));

    private ContactInfo() {
    }

    String getType() {
        return type;
    }

    boolean isVerified() {
        return (bitmap != null && verified);
    }

    boolean hasPhoto() {
        return (bitmap != null);
    }

    Bitmap getPhotoBitmap() {
        return bitmap;
    }

    String getEmailAddress() {
        return email;
    }

    String getDisplayName() {
        return displayName;
    }

    boolean hasLookupUri() {
        return (lookupUri != null);
    }

    Uri getLookupUri() {
        return lookupUri;
    }

    boolean isKnown() {
        return known;
    }

    private boolean isExpired() {
        return (new Date().getTime() - time > CACHE_CONTACT_DURATION);
    }

    static boolean canGravatars() {
        return !BuildConfig.PLAY_STORE_RELEASE;
    }

    static void cleanup(Context context) {
        long now = new Date().getTime();

        // Favicons
        Log.i("Cleanup favicons");
        File[] favicons = new File(context.getCacheDir(), "favicons").listFiles();
        if (favicons != null)
            for (File file : favicons)
                if (file.lastModified() + CACHE_FAVICON_DURATION < now) {
                    Log.i("Deleting " + file);
                    if (!file.delete())
                        Log.w("Error deleting " + file);
                }
    }

    static void clearCache(Context context) {
        clearCache(context, true);
    }

    static void clearCache(Context context, boolean files) {
        synchronized (emailContactInfo) {
            emailContactInfo.clear();
        }

        if (!files)
            return;

        final File dir = new File(context.getCacheDir(), "favicons");
        executorFavicon.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    File[] favicons = dir.listFiles();
                    if (favicons != null)
                        for (File favicon : favicons)
                            favicon.delete();
                } catch (Throwable ex) {
                    Log.w(ex);
                }
            }
        });
    }

    @NonNull
    static ContactInfo[] get(Context context, long account, String folderType, String selector, Address[] addresses) {
        return get(context, account, folderType, selector, addresses, false);
    }

    static ContactInfo[] getCached(Context context, long account, String folderType, String selector, Address[] addresses) {
        return get(context, account, folderType, selector, addresses, true);
    }

    private static ContactInfo[] get(Context context, long account, String folderType, String selector, Address[] addresses, boolean cacheOnly) {
        if (addresses == null || addresses.length == 0)
            return new ContactInfo[]{new ContactInfo()};

        ContactInfo[] result = new ContactInfo[addresses.length];
        for (int i = 0; i < addresses.length; i++) {
            result[i] = _get(context, account, folderType, selector, (InternetAddress) addresses[i], cacheOnly);
            if (result[i] == null)
                return null;
        }

        return result;
    }

    private static ContactInfo _get(
            Context context,
            long account, String folderType,
            String selector, InternetAddress address, boolean cacheOnly) {
        String key = MessageHelper.formatAddresses(new Address[]{address});
        synchronized (emailContactInfo) {
            ContactInfo info = emailContactInfo.get(key);
            if (info != null && !info.isExpired())
                return info;
        }

        if (cacheOnly)
            return null;

        ContactInfo info = new ContactInfo();
        info.email = address.getAddress();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean avatars = prefs.getBoolean("avatars", true);
        boolean bimi = prefs.getBoolean("bimi", false);
        boolean gravatars = prefs.getBoolean("gravatars", false);
        boolean favicons = prefs.getBoolean("favicons", false);
        boolean generated = prefs.getBoolean("generated_icons", true);
        boolean identicons = prefs.getBoolean("identicons", false);
        boolean circular = prefs.getBoolean("circular", true);

        // Contact photo
        if (!TextUtils.isEmpty(info.email) &&
                Helper.hasPermission(context, Manifest.permission.READ_CONTACTS)) {
            ContentResolver resolver = context.getContentResolver();
            Uri uri = Uri.withAppendedPath(
                    ContactsContract.CommonDataKinds.Email.CONTENT_LOOKUP_URI,
                    Uri.encode(info.email.toLowerCase(Locale.ROOT)));
            try (Cursor cursor = resolver.query(uri,
                    new String[]{
                            ContactsContract.CommonDataKinds.Photo.CONTACT_ID,
                            ContactsContract.Contacts.LOOKUP_KEY,
                            ContactsContract.Contacts.DISPLAY_NAME
                    },
                    null, null, null)) {

                if (cursor != null && cursor.moveToNext()) {
                    int colContactId = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Photo.CONTACT_ID);
                    int colLookupKey = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY);
                    int colDisplayName = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);

                    long contactId = cursor.getLong(colContactId);
                    String lookupKey = cursor.getString(colLookupKey);
                    Uri lookupUri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey);

                    if (avatars)
                        try (InputStream is = ContactsContract.Contacts.openContactPhotoInputStream(
                                resolver, lookupUri, false)) {
                            info.bitmap = BitmapFactory.decodeStream(is);
                            info.type = "contact";
                        } catch (Throwable ex) {
                            Log.e(ex);
                        }

                    info.displayName = cursor.getString(colDisplayName);
                    info.lookupUri = lookupUri;
                    info.known = true;
                }
            } catch (Throwable ex) {
                Log.e(ex);
            }
        }

        // Favicon
        if (info.bitmap == null &&
                !EntityFolder.JUNK.equals(folderType) &&
                (bimi || (gravatars && canGravatars()) || favicons)) {
            String d = UriHelper.getEmailDomain(info.email);
            if (d != null) {
                // Prevent using Doodles
                if ("google.com".equals(d) ||
                        "gmail.com".equals(d) ||
                        "googlemail.com".equals(d))
                    d = "support.google.com";

                // https://yahoo.fr redirects unsafely to http://fr.yahoo.com/favicon.ico
                String[] dparts = d.split("\\.");
                if (dparts.length > 1 && "yahoo".equals(dparts[dparts.length - 2]))
                    d = "yahoo.com";

                final String domain = d.toLowerCase(Locale.ROOT);
                final String email = info.email.toLowerCase(Locale.ROOT);

                File dir = new File(context.getCacheDir(), "favicons");
                if (!dir.exists())
                    dir.mkdir();

                try {
                    // check cache
                    File[] files = null;
                    if (gravatars && canGravatars()) {
                        File f = new File(dir, email + ".gravatar");
                        if (f.exists())
                            files = new File[]{f};
                    }
                    if (files == null)
                        files = dir.listFiles(new FilenameFilter() {
                            @Override
                            public boolean accept(File file, String name) {
                                return name.startsWith(domain);
                            }
                        });
                    if (files != null && files.length == 1) {
                        if (files[0].length() == 0)
                            Log.i("Avatar blacklisted cache" + files[0].getName());
                        else {
                            Log.i("Avatar from cache=" + files[0].getName());
                            String ext = Helper.getExtension(files[0].getName());
                            String[] data = (ext == null ? null : ext.split("_"));
                            info.bitmap = BitmapFactory.decodeFile(files[0].getAbsolutePath());
                            if (data != null) {
                                info.type = (data.length > 0 ? data[0] : "unknown");
                                info.verified = (data.length > 1 && "verified".equals(data[1]));
                            }
                        }
                    } else {
                        final int scaleToPixels = Helper.dp2pixels(context, FAVICON_ICON_SIZE);

                        List<Future<Favicon>> futures = new ArrayList<>();

                        if (bimi)
                            futures.add(executorFavicon.submit(new Callable<Favicon>() {
                                @Override
                                public Favicon call() throws Exception {
                                    Pair<Bitmap, Boolean> bimi =
                                            Bimi.get(context, domain, selector, scaleToPixels);
                                    return (bimi == null ? null : new Favicon(bimi.first, "vmc", bimi.second));
                                }
                            }));

                        if (gravatars && canGravatars())
                            futures.add(executorFavicon.submit(new Callable<Favicon>() {
                                @Override
                                public Favicon call() throws Exception {
                                    String hash = Helper.md5(email.getBytes());
                                    URL url = new URL(BuildConfig.GRAVATAR_URI + hash + "?d=404");
                                    Log.i("Gravatar key=" + email + " url=" + url);

                                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                                    urlConnection.setRequestMethod("GET");
                                    urlConnection.setReadTimeout(GRAVATAR_TIMEOUT);
                                    urlConnection.setConnectTimeout(GRAVATAR_TIMEOUT);
                                    urlConnection.setRequestProperty("User-Agent", WebViewEx.getUserAgent(context));
                                    urlConnection.connect();

                                    try {
                                        int status = urlConnection.getResponseCode();
                                        if (status == HttpURLConnection.HTTP_OK) {
                                            // Positive reply
                                            Bitmap bitmap = BitmapFactory.decodeStream(urlConnection.getInputStream());
                                            return (bitmap == null ? null : new Favicon(bitmap, "gravatar", false));
                                        } else if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                                            // Negative reply
                                            return null;
                                        } else
                                            throw new IOException("Error " + status + ": " + urlConnection.getResponseMessage());
                                    } finally {
                                        urlConnection.disconnect();
                                    }
                                }
                            }));

                        if (favicons) {
                            String host = domain;
                            while (host.indexOf('.') > 0) {
                                final URL base = new URL("https://" + host);
                                final URL www = new URL("https://www." + host);

                                futures.add(executorFavicon.submit(new Callable<Favicon>() {
                                    @Override
                                    public Favicon call() throws Exception {
                                        return parseFavicon(base, scaleToPixels, context);
                                    }
                                }));

                                futures.add(executorFavicon.submit(new Callable<Favicon>() {
                                    @Override
                                    public Favicon call() throws Exception {
                                        return parseFavicon(www, scaleToPixels, context);
                                    }
                                }));

                                futures.add(executorFavicon.submit(new Callable<Favicon>() {
                                    @Override
                                    public Favicon call() throws Exception {
                                        return getFavicon(new URL(base, "favicon.ico"), null, scaleToPixels, context);
                                    }
                                }));

                                futures.add(executorFavicon.submit(new Callable<Favicon>() {
                                    @Override
                                    public Favicon call() throws Exception {
                                        return getFavicon(new URL(www, "favicon.ico"), null, scaleToPixels, context);
                                    }
                                }));

                                int dot = host.indexOf('.');
                                host = host.substring(dot + 1);
                            }
                        }

                        Throwable ex = null;
                        for (Future<Favicon> future : futures)
                            try {
                                Favicon favicon = future.get();
                                if (favicon != null) {
                                    info.bitmap = favicon.bitmap;
                                    info.type = favicon.type;
                                    info.verified = favicon.verified;
                                    break;
                                }
                            } catch (ExecutionException exex) {
                                ex = exex.getCause();
                            } catch (Throwable exex) {
                                ex = exex;
                            }

                        if (info.bitmap == null)
                            if (ex == null)
                                throw new FileNotFoundException();
                            else
                                throw ex;

                        // Add to cache
                        File output = new File(dir,
                                ("gravatar".equals(info.type) ? email : domain) +
                                        "." + info.type +
                                        (info.verified ? "_verified" : ""));
                        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(output))) {
                            info.bitmap.compress(Bitmap.CompressFormat.PNG, 90, os);
                        }
                        Log.i("Avatar to cache=" + output.getName());
                    }
                } catch (Throwable ex) {
                    if (isRecoverable(ex, context))
                        Log.i(ex);
                    else {
                        if (ex instanceof FileNotFoundException ||
                                ex instanceof CertificateException ||
                                ex instanceof CertPathValidatorException ||
                                ex.getCause() instanceof CertPathValidatorException ||
                                ex.getCause() instanceof CertificateException ||
                                (ex instanceof SSLException &&
                                        "Unable to parse TLS packet header".equals(ex.getMessage())))
                            Log.i(ex);
                        else
                            Log.e(ex);
                        try {
                            new File(dir, domain).createNewFile();
                        } catch (IOException ex1) {
                            Log.e(ex1);
                        }
                    }
                }
            }
        }

        // Generated
        boolean identicon = false;
        if (info.bitmap == null && generated) {
            int dp = Helper.dp2pixels(context, GENERATED_ICON_SIZE);
            if (!TextUtils.isEmpty(info.email)) {
                if (identicons) {
                    identicon = true;
                    info.bitmap = ImageHelper.generateIdenticon(
                            info.email, dp, 5, context);
                    info.type = "identicon";
                } else {
                    info.bitmap = ImageHelper.generateLetterIcon(
                            info.email, address.getPersonal(), dp, context);
                    info.type = "letter";
                }
            }
        }

        info.bitmap = ImageHelper.makeCircular(info.bitmap,
                circular && !identicon ? null : Helper.dp2pixels(context, 3));

        if (info.displayName == null)
            info.displayName = address.getPersonal();

        if (!info.known && !TextUtils.isEmpty(info.email))
            try {
                DB db = DB.getInstance(context);
                EntityContact contact = db.contact().getContact(account, EntityContact.TYPE_TO, info.email);
                info.known = (contact != null);
            } catch (Throwable ex) {
                Log.e(ex);
            }

        synchronized (emailContactInfo) {
            emailContactInfo.put(key, info);
        }

        info.time = new Date().getTime();
        return info;
    }

    private static Favicon parseFavicon(URL base, int scaleToPixels, Context context) throws IOException {
        Log.i("PARSE favicon " + base);
        HttpsURLConnection connection = (HttpsURLConnection) base.openConnection();
        connection.setRequestMethod("GET");
        connection.setReadTimeout(FAVICON_READ_TIMEOUT);
        connection.setConnectTimeout(FAVICON_CONNECT_TIMEOUT);
        connection.setInstanceFollowRedirects(true);
        connection.setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });
        connection.setRequestProperty("User-Agent", WebViewEx.getUserAgent(context));
        connection.connect();

        String response;
        try {
            byte[] buffer = new byte[FAVICON_READ_BYTES];
            int len = 0;
            while (len < buffer.length) {
                int read = connection.getInputStream().read(buffer, len, buffer.length - len);
                if (read < 0)
                    break;
                else
                    len += read;
            }
            if (len < 0)
                throw new IOException("length");
            response = new String(buffer, 0, len, StandardCharsets.UTF_8.name());
        } finally {
            connection.disconnect();
        }

        Document doc = JsoupEx.parse(response);

        // Use canonical address
        Element canonical = doc.head().select("link[rel=canonical]").first();
        if (canonical != null) {
            String href = canonical.attr("href");
            if (!TextUtils.isEmpty(href))
                base = new URL(href);
        }

        // https://en.wikipedia.org/wiki/Favicon
        Elements imgs = new Elements();
        imgs.addAll(doc.head().select("link[href~=.+\\.(ico|png|gif|svg)]"));
        imgs.addAll(doc.head().select("meta[itemprop=image]"));

        // https://developer.mozilla.org/en-US/docs/Web/Manifest/icons
        if (imgs.size() == 0 || BuildConfig.DEBUG)
            for (Element manifest : doc.head().select("link[rel=manifest]"))
                try {
                    String href = manifest.attr("href");
                    if (TextUtils.isEmpty(href))
                        continue;

                    URL url = new URL(base, href);
                    Log.i("GET favicon manifest " + url);

                    HttpsURLConnection m = (HttpsURLConnection) url.openConnection();
                    m.setRequestMethod("GET");
                    m.setReadTimeout(FAVICON_READ_TIMEOUT);
                    m.setConnectTimeout(FAVICON_CONNECT_TIMEOUT);
                    m.setInstanceFollowRedirects(true);
                    m.setRequestProperty("User-Agent", WebViewEx.getUserAgent(context));
                    m.connect();

                    try {
                        String json = Helper.readStream(m.getInputStream());
                        JSONObject jroot = new JSONObject(json);
                        if (jroot.has("icons")) {
                            JSONArray jicons = jroot.getJSONArray("icons");
                            for (int i = 0; i < jicons.length(); i++) {
                                JSONObject jicon = jicons.getJSONObject(i);
                                String src = jicon.optString("src");
                                String sizes = jicon.optString("sizes", "");
                                String type = jicon.optString("type", "");
                                if (!TextUtils.isEmpty(src)) {
                                    Element img = doc.createElement("link")
                                            .attr("href", src)
                                            .attr("sizes", sizes)
                                            .attr("type", type);
                                    imgs.add(img);
                                }
                            }
                        }
                    } finally {
                        m.disconnect();
                    }
                } catch (Throwable ex) {
                    Log.w(ex);
                }

        Collections.sort(imgs, new Comparator<Element>() {
            @Override
            public int compare(Element img1, Element img2) {
                boolean l1 = "link".equals(img1.tagName());
                boolean l2 = "link".equals(img2.tagName());
                int l = Boolean.compare(l1, l2);
                if (l != 0)
                    return -l;

                boolean i1 = "icon".equalsIgnoreCase(img1.attr("rel")
                        .replace("shortcut", "").trim());
                boolean i2 = "icon".equalsIgnoreCase(img2.attr("rel")
                        .replace("shortcut", "").trim());
                int i = Boolean.compare(i1, i2);
                if (i != 0)
                    return -i;

                int t1 = getOrder(img1.attr("href"), img1.attr("type"));
                int t2 = getOrder(img2.attr("href"), img2.attr("type"));
                int t = Integer.compare(t1, t2);
                if (t != 0)
                    return t;

                int s1 = getSize(img1.attr("sizes"));
                int s2 = getSize(img2.attr("sizes"));
                return Integer.compare(
                        Math.abs(s1 - scaleToPixels),
                        Math.abs(s2 - scaleToPixels));
            }
        });

        for (int i = 0; i < imgs.size(); i++)
            Log.i("Favicon " + i + "=" + imgs.get(i) + " @" + base);

        List<Future<Favicon>> futures = new ArrayList<>();
        for (Element img : imgs) {
            String rel = img.attr("rel").trim().toLowerCase(Locale.ROOT);
            if (REL_EXCLUDE.contains(rel)) // dns-prefetch: gmx.net
                continue;

            String favicon = ("link".equals(img.tagName())
                    ? img.attr("href")
                    : img.attr("content"));
            if (TextUtils.isEmpty(favicon))
                continue;

            final URL url = new URL(base, favicon);
            futures.add(executorFavicon.submit(new Callable<Favicon>() {
                @Override
                public Favicon call() throws Exception {
                    return getFavicon(url, img.attr("type"), scaleToPixels, context);
                }
            }));
        }

        for (Future<Favicon> future : futures)
            try {
                return future.get();
            } catch (Throwable ex) {
                if (ex.getCause() instanceof FileNotFoundException ||
                        ex.getCause() instanceof CertPathValidatorException)
                    Log.i(ex);
                else
                    Log.e(ex);
            }

        return null;
    }

    private static int getOrder(String href, String type) {
        int order = -1;
        String h = (href == null ? "" : href.toLowerCase(Locale.ROOT));
        if (h.endsWith(".ico"))
            order = 2;
        else if (h.endsWith(".svg") || "image/svg+xml".equals(type))
            order = 1;

        return order;
    }

    private static int getSize(String sizes) {
        int max = 0;
        for (String size : sizes.split(" ")) {
            int min = Integer.MAX_VALUE;
            for (String p : size.trim().split("[x|X]")) {
                if (TextUtils.isEmpty(p) || "any".equalsIgnoreCase(p))
                    continue;

                try {
                    int x = Integer.parseInt(p);
                    if (x < min)
                        min = x;
                } catch (NumberFormatException ex) {
                    Log.w(ex);
                }
            }
            if (min != Integer.MAX_VALUE && min > max)
                max = min;
        }

        return max;
    }

    @NonNull
    private static Favicon getFavicon(URL url, String mimeType, int scaleToPixels, Context context) throws IOException {
        Log.i("GET favicon " + url);

        if (!"https".equals(url.getProtocol()))
            throw new FileNotFoundException("http");

        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setReadTimeout(FAVICON_READ_TIMEOUT);
        connection.setConnectTimeout(FAVICON_CONNECT_TIMEOUT);
        connection.setInstanceFollowRedirects(true);
        connection.setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });
        connection.setRequestProperty("User-Agent", WebViewEx.getUserAgent(context));
        connection.connect();

        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK)
                throw new FileNotFoundException("Error " + status + ": " + connection.getResponseMessage());

            Bitmap bitmap = ImageHelper.getScaledBitmap(connection.getInputStream(), url.toString(), mimeType, scaleToPixels);
            if (bitmap == null)
                throw new FileNotFoundException("decodeStream");
            else {
                Bitmap favicon = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
                favicon.eraseColor(Color.WHITE);
                Canvas canvas = new Canvas(favicon);
                canvas.drawBitmap(bitmap, 0, 0, null);
                bitmap.recycle();
                return new Favicon(favicon);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static boolean isRecoverable(Throwable ex, Context context) {
        if (ex instanceof SocketTimeoutException) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = (cm == null ? null : cm.getActiveNetworkInfo());
            return (ni == null || !ni.isConnected());
        }

        return !(ex instanceof ConnectException ||
                (ex instanceof UnknownHostException &&
                        ex.getMessage() != null &&
                        ex.getMessage().contains("No address associated with hostname")) ||
                ex instanceof FileNotFoundException ||
                ex instanceof SSLPeerUnverifiedException ||
                (ex instanceof SSLException &&
                        "Unable to parse TLS packet header".equals(ex.getMessage())) ||
                (ex instanceof SSLHandshakeException &&
                        ("connection closed".equals(ex.getMessage())) ||
                        "Connection closed by peer".equals(ex.getMessage())) ||
                (ex instanceof SSLHandshakeException &&
                        ex.getMessage() != null &&
                        (ex.getMessage().contains("usually a protocol error") ||
                                ex.getMessage().contains("Unacceptable certificate"))) ||
                (ex instanceof SSLHandshakeException &&
                        (ex.getCause() instanceof SSLProtocolException ||
                                ex.getCause() instanceof CertificateException ||
                                ex.getCause() instanceof CertPathValidatorException)));
    }

    static void init(final Context context) {
        if (Helper.hasPermission(context, Manifest.permission.READ_CONTACTS)) {
            ContentObserver observer = new ContentObserver(ApplicationEx.getMainHandler()) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    Log.i("Contact changed uri=" + uri);
                    executorLookup.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                emailLookup = getEmailLookup(context);
                            } catch (Throwable ex) {
                                Log.e(ex);
                            }
                        }
                    });
                }
            };

            executorLookup.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        emailLookup = getEmailLookup(context);
                    } catch (Throwable ex) {
                        Log.e(ex);
                    }
                }
            });

            try {
                Uri uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI;
                Log.i("Observing uri=" + uri);
                context.getContentResolver().registerContentObserver(uri, true, observer);
            } catch (SecurityException ex) {
                Log.w(ex);
                /*
                    Should never happen, but:
                    Caused by: android.os.RemoteException:
                      at com.android.server.content.ContentService.registerContentObserver (ContentService.java:340)
                      at android.content.IContentService$Stub.onTransact (IContentService.java:76)
                      at com.android.server.content.ContentService.onTransact (ContentService.java:262)
                      at android.os.Binder.execTransact (Binder.java:731)
                 */
            }
        }
    }

    static Uri getLookupUri(Address[] addresses) {
        if (addresses == null)
            return null;

        for (Address from : addresses) {
            String email = ((InternetAddress) from).getAddress();
            if (!TextUtils.isEmpty(email)) {
                Lookup lookup = emailLookup.get(email.toLowerCase(Locale.ROOT));
                if (lookup != null)
                    return lookup.uri;
            }
        }

        return null;
    }

    static Address[] fillIn(Address[] addresses, boolean prefer_contact, boolean only_contact) {
        if (addresses == null)
            return null;

        Address[] modified = new Address[addresses.length];
        for (int i = 0; i < addresses.length; i++) {
            InternetAddress address = (InternetAddress) addresses[i];
            String email = address.getAddress();
            String personal = (only_contact ? null : address.getPersonal());
            if (!TextUtils.isEmpty(email)) {
                Lookup lookup = emailLookup.get(email.toLowerCase(Locale.ROOT));
                if (lookup != null &&
                        (TextUtils.isEmpty(personal) || prefer_contact))
                    personal = lookup.displayName;
            }
            try {
                modified[i] = new InternetAddress(email, personal, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException ex) {
                Log.e(ex);
                modified[i] = address;
            }
        }

        return modified;
    }

    private static Map<String, Lookup> getEmailLookup(Context context) {
        Map<String, Lookup> all = new ConcurrentHashMap<>();

        if (Helper.hasPermission(context, Manifest.permission.READ_CONTACTS)) {
            Log.i("Reading email/uri");
            ContentResolver resolver = context.getContentResolver();

            try (Cursor cursor = resolver.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Photo.CONTACT_ID,
                            ContactsContract.Contacts.LOOKUP_KEY,
                            ContactsContract.CommonDataKinds.Email.ADDRESS,
                            ContactsContract.Contacts.DISPLAY_NAME
                    },
                    ContactsContract.CommonDataKinds.Email.ADDRESS + " <> ''",
                    null, null)) {
                while (cursor != null && cursor.moveToNext()) {
                    long contactId = cursor.getLong(0);
                    String lookupKey = cursor.getString(1);
                    String email = cursor.getString(2);
                    String displayName = cursor.getString(3);

                    Lookup lookup = new Lookup();
                    lookup.uri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
                    lookup.displayName = displayName;
                    all.put(email.toLowerCase(Locale.ROOT), lookup);
                }
            } catch (Throwable ex) {
                Log.e(ex);
            }
        }

        Log.i("Read email/uri=" + all.size());
        return all;
    }

    private static class Lookup {
        Uri uri;
        String displayName;
    }

    private static class Favicon {
        private Bitmap bitmap;
        private String type;
        private boolean verified;

        private Favicon(@NonNull Bitmap bitmap) {
            this(bitmap, "favicon", false);
        }

        private Favicon(@NonNull Bitmap bitmap, String type, boolean verified) {
            this.bitmap = bitmap;
            this.type = type;
            this.verified = verified;
        }
    }
}
