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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;
import androidx.exifinterface.media.ExifInterface;
import androidx.preference.PreferenceManager;

import com.caverock.androidsvg.SVG;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;

class ImageHelper {
    private static final ExecutorService executor_1 =
            Helper.getBackgroundExecutor(1, "image_1");
    private static final ExecutorService executor_n =
            Helper.getBackgroundExecutor(0, "image_n");

    static final int DOWNLOAD_TIMEOUT = 15; // seconds
    private static final int MAX_PROBE = 128 * 1024; // bytes
    private static final int SLOW_CONNECTION = 2 * 1024; // Kbps
    private static final int MAX_BITMAP_SIZE = 100 * 1024 * 1024; // RecordingCanvas.MAX_BITMAP_SIZE

    // https://developer.android.com/guide/topics/media/media-formats#image-formats
    static final List<String> IMAGE_TYPES = Collections.unmodifiableList(Arrays.asList(
            "image/bmp",
            "image/gif",
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    ));

    static final List<String> IMAGE_TYPES8 = Collections.unmodifiableList(Arrays.asList(
            "image/heic",
            "image/heif"
    ));

    // https://developer.android.com/about/versions/12/features#avif
    static final List<String> IMAGE_TYPES12 = Collections.unmodifiableList(Arrays.asList(
            "image/avif"
    ));

    static boolean isImage(String mimeType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            if (IMAGE_TYPES8.contains(mimeType))
                return true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            if (IMAGE_TYPES12.contains(mimeType))
                return true;

        return IMAGE_TYPES.contains(mimeType);
    }

    static Bitmap generateIdenticon(@NonNull String email, int size, int pixels, Context context) {
        byte[] hash = getHash(email);
        float h = Math.abs(email.hashCode()) % 360;
        return generateIdenticon(hash, h, size, pixels, context);
    }

    static Bitmap generateIdenticon(byte[] hash, float h, int size, int pixels, Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int s = prefs.getInt("saturation", 100);
        int v = prefs.getInt("brightness", 100);

        int bg = Color.HSVToColor(new float[]{h, s / 100f, v / 100f});

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(bg);

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);

        float psize = (float) size / pixels;

        for (int x = 0; x < pixels; x++) {
            int i = (x > pixels / 2 ? pixels - x - 1 : x);
            for (int y = 0; y < pixels; y++) {
                if ((hash[i] >> y & 1) == 1) {
                    RectF rect = new RectF(x * psize, y * psize, (x + 1) * psize, (y + 1) * psize);
                    canvas.drawRect(rect, paint);
                }
            }
        }

        return bitmap;
    }

    static Bitmap generateLetterIcon(@NonNull String email, String name, int size, Context context) {
        if (TextUtils.isEmpty(name))
            name = email;

        String letter = null;
        int len = name.length();
        for (int i = 0; i < len; i++) {
            char kar = name.charAt(i);
            if (Character.isLetter(kar)) {
                letter = name.substring(i, i + 1).toUpperCase();
                break;
            }
        }
        if (letter == null)
            letter = (len > 0 ? name.substring(0, 1) : "?");

        float h = Math.abs(email.hashCode()) % 360f;
        return generateLetterIcon(letter, h, size, context);
    }

    static Bitmap generateLetterIcon(String letter, float h, int size, Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        float s = prefs.getInt("saturation", 100) / 100f;
        float v = prefs.getInt("brightness", 100) / 100f;
        float t = prefs.getInt("threshold", 50) / 100f;

        int bg = Color.HSVToColor(new float[]{h, s, v});
        double lum = ColorUtils.calculateLuminance(bg);

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(bg);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(lum < t ? Color.WHITE : Color.BLACK);
        paint.setTextSize(size / 2f);
        try {
            paint.setTypeface(Typeface.DEFAULT_BOLD);
        } catch (Throwable ex) {
            Log.e(ex);
            /*
                java.lang.NullPointerException: Attempt to invoke interface method 'android.graphics.Typeface miui.util.font.IFontManager.getBaseFont(int)' on a null object reference
                  at miui.util.TypefaceUtils.updateDefaultFont(TypefaceUtils.java:190)
                  at miui.util.TypefaceUtils.loadFontManager(TypefaceUtils.java:168)
                  at miui.util.TypefaceUtils.loadFontSettings(TypefaceUtils.java:64)
                  at miui.util.TypefaceUtils.useVarFont(TypefaceUtils.java:107)
                  at android.graphics.Paint.useMiuiVarFont(Paint.java:1460)
                  at android.graphics.Paint.setTypeface(Paint.java:1443)
             */
        }

        canvas.drawText(letter,
                size / 2f - paint.measureText(letter) / 2,
                size / 2f - (paint.descent() + paint.ascent()) / 2, paint);

        return bitmap;
    }

    static byte[] getHash(String email) {
        try {
            return MessageDigest.getInstance("MD5").digest(email.getBytes());
        } catch (NoSuchAlgorithmException ignored) {
            return email.getBytes();
        }
    }

    static Bitmap makeCircular(Bitmap bitmap, Integer radius) {
        if (bitmap == null || bitmap.isRecycled())
            return null;

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        Rect source;
        if (w > h) {
            int off = (w - h) / 2;
            source = new Rect(off, 0, w - off, h);
        } else if (w < h) {
            int off = (h - w) / 2;
            source = new Rect(0, off, w, h - off);
        } else
            source = new Rect(0, 0, w, h);

        Rect dest = new Rect(0, 0, source.width(), source.height());

        Bitmap round = Bitmap.createBitmap(source.width(), source.height(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(round);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(Color.GRAY);
        if (radius == null)
            canvas.drawOval(new RectF(dest), paint); // round
        else
            canvas.drawRoundRect(new RectF(dest), radius, radius, paint); // rounded
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        try {
            canvas.drawBitmap(bitmap, source, dest, paint);
        } catch (RuntimeException ex) {
            Log.e(ex);
            /*
                java.lang.RuntimeException: Canvas: trying to use a recycled bitmap android.graphics.Bitmap@d25d3f9
                java.lang.RuntimeException: Canvas: trying to use a recycled bitmap android.graphics.Bitmap@d25d3f9
                  at android.graphics.BaseCanvas.throwIfCannotDraw(BaseCanvas.java:66)
                  at android.graphics.BaseCanvas.drawBitmap(BaseCanvas.java:131)
                  at android.graphics.Canvas.drawBitmap(Canvas.java:1608)
                  at eu.faircode.email.ImageHelper.makeCircular(SourceFile:205)
                  at eu.faircode.email.ContactInfo._get(SourceFile:403)
                  at eu.faircode.email.ContactInfo.get(SourceFile:177)
                  at eu.faircode.email.ContactInfo.get(SourceFile:164)
             */
        }

        bitmap.recycle();
        return round;
    }

    @NonNull
    static Bitmap renderSvg(InputStream is, int fillColor, int scaleToPixels) throws IOException {
        try {
            SVG svg = SVG.getFromInputStream(is);
            float w = svg.getDocumentWidth();
            float h = svg.getDocumentHeight();
            if (w < 0 || h < 0) {
                w = scaleToPixels;
                h = scaleToPixels;
            }

            Bitmap bm = Bitmap.createBitmap((int) w, (int) h, Bitmap.Config.ARGB_8888);
            bm.eraseColor(fillColor);
            Canvas canvas = new Canvas(bm);
            svg.renderToCanvas(canvas);
            return bm;
        } catch (Throwable ex) {
            throw new IOException("SVG, ex");
        }
    }

    static Drawable decodeImage(final Context context, final long id, String source, boolean show, int zoom, final float scale, final TextView view) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean inline = prefs.getBoolean("inline_images", false);

        final int px = Helper.dp2pixels(context, (zoom + 1) * 24);
        final Resources res = context.getResources();

        try {
            final AnnotatedSource a = new AnnotatedSource(source);

            if (TextUtils.isEmpty(a.source)) {
                Drawable d = context.getDrawable(R.drawable.twotone_broken_image_24);
                d.setBounds(0, 0, px, px);
                return d;
            }

            boolean embedded = a.source.startsWith("cid:");
            boolean data = a.source.startsWith("data:");
            boolean content = a.source.startsWith("content:");

            Log.d("Image show=" + show + " inline=" + inline + " source=" + a.source);

            // Embedded images
            if (embedded && (show || inline)) {
                DB db = DB.getInstance(context);
                String cid = "<" + a.source.substring(4) + ">";
                EntityAttachment attachment = db.attachment().getAttachment(id, cid);
                if (attachment == null) {
                    Log.i("Image not found CID=" + cid);
                    Drawable d = context.getDrawable(R.drawable.twotone_broken_image_24);
                    d.setBounds(0, 0, px, px);
                    return d;
                } else if (!attachment.available) {
                    Log.i("Image not available CID=" + cid);
                    Drawable d = context.getDrawable(R.drawable.twotone_photo_library_24);
                    d.setBounds(0, 0, px, px);
                    return d;
                } else {
                    int scaleToPixels = res.getDisplayMetrics().widthPixels;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        try {
                            Drawable d = getScaledDrawable(context,
                                    attachment.getFile(context),
                                    attachment.getMimeType(),
                                    scaleToPixels);
                            if (view != null)
                                fitDrawable(d, a, scale, view);
                            return d;
                        } catch (IOException ex) {
                            Log.w(ex);
                            Drawable d = context.getDrawable(R.drawable.twotone_broken_image_24);
                            d.setBounds(0, 0, px, px);
                            return d;
                        }
                    } else {
                        Bitmap bm = decodeImage(
                                attachment.getFile(context),
                                attachment.getMimeType(),
                                scaleToPixels);
                        if (bm == null) {
                            Log.i("Image not decodable CID=" + cid);
                            Drawable d = context.getDrawable(R.drawable.twotone_broken_image_24);
                            d.setBounds(0, 0, px, px);
                            return d;
                        } else {
                            Drawable d = new BitmapDrawable(res, bm);
                            d.setBounds(0, 0, bm.getWidth(), bm.getHeight());
                            if (view != null)
                                fitDrawable(d, a, scale, view);
                            return d;
                        }
                    }
                }
            }

            // Data URI
            if (data && (show || inline || a.tracking))
                try {
                    int scaleToPixels = res.getDisplayMetrics().widthPixels;
                    String mimeType = getDataUriType(a.source);
                    ByteArrayInputStream bis = getDataUriStream(a.source);
                    Bitmap bm = getScaledBitmap(bis, "data:" + mimeType, mimeType, scaleToPixels);
                    if (bm == null)
                        throw new IllegalArgumentException("decode byte array failed");

                    Drawable d = new BitmapDrawable(context.getResources(), bm);
                    d.setBounds(0, 0, bm.getWidth(), bm.getHeight());

                    if (view != null)
                        fitDrawable(d, a, scale, view);
                    return d;
                } catch (IllegalArgumentException ex) {
                    Log.i(ex);
                    Drawable d = context.getDrawable(R.drawable.twotone_broken_image_24);
                    d.setBounds(0, 0, px, px);
                    return d;
                }

            if (content && (show || inline))
                try {
                    Uri uri = Uri.parse(a.source);
                    Log.i("Loading image source=" + uri);

                    Bitmap bm;
                    int scaleToPixels = res.getDisplayMetrics().widthPixels;
                    try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                        bm = getScaledBitmap(is, a.source, null, scaleToPixels);
                        if (bm == null)
                            throw new FileNotFoundException(a.source);
                    }

                    Drawable d = new BitmapDrawable(res, bm);
                    d.setBounds(0, 0, bm.getWidth(), bm.getHeight());

                    if (view != null)
                        fitDrawable(d, a, scale, view);
                    return d;
                } catch (Throwable ex) {
                    // FileNotFound, Security
                    Log.w(ex);
                    Drawable d = context.getDrawable(R.drawable.twotone_broken_image_24);
                    d.setBounds(0, 0, px, px);
                    return d;
                }

            if (!show) {
                // Show placeholder icon
                int resid = (embedded || data ? R.drawable.twotone_photo_library_24 : R.drawable.twotone_image_24);
                Drawable d = context.getDrawable(resid);
                d.setBounds(0, 0, px, px);
                return d;
            }

            // Check cache
            Drawable cached = getCachedImage(context, id, a.source);
            if (cached != null || view == null) {
                if (view == null)
                    if (cached == null) {
                        Drawable d = context.getDrawable(R.drawable.twotone_hourglass_top_24);
                        d.setBounds(0, 0, px, px);
                        return d;
                    } else
                        return cached;
                else
                    fitDrawable(cached, a, scale, view);
                return cached;
            }

            final LevelListDrawable lld = new LevelListDrawable();
            Drawable wait = context.getDrawable(R.drawable.twotone_hourglass_top_24);
            lld.addLevel(1, 1, wait);
            lld.setBounds(0, 0, px, px);
            lld.setLevel(1);

            boolean slow = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                try {
                    // 2G GSM ~14.4 Kbps
                    // G GPRS ~26.8 Kbps
                    // E EDGE ~108.8 Kbps
                    // 3G UMTS ~128 Kbps
                    // H HSPA ~3.6 Mbps
                    // H+ HSPA+ ~14.4 Mbps-23.0 Mbps
                    // 4G LTE ~50 Mbps
                    // 4G LTE-A ~500 Mbps
                    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                    Network active = (cm == null ? null : cm.getActiveNetwork());
                    NetworkCapabilities caps = (active == null ? null : cm.getNetworkCapabilities(active));
                    if (caps != null) {
                        int kbps = caps.getLinkDownstreamBandwidthKbps();
                        slow = (kbps < SLOW_CONNECTION);
                    }
                } catch (Throwable ex) {
                    Log.e(ex);
                }

            ExecutorService executor = (slow ? executor_1 : executor_n);

            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Check cache again
                        Drawable cached = getCachedImage(context, id, a.source);
                        if (cached != null) {
                            fitDrawable(cached, a, scale, view);
                            post(cached, a.source);
                            return;
                        }

                        // Download image
                        Drawable d = downloadImage(context, id, a.source, null);
                        fitDrawable(d, a, scale, view);
                        post(d, a.source);
                    } catch (Throwable ex) {
                        // Show broken icon
                        Log.i(ex);
                        int resid = (ex instanceof IOException && !(ex instanceof FileNotFoundException)
                                ? R.drawable.twotone_cloud_off_24
                                : R.drawable.twotone_broken_image_24);
                        Drawable d = context.getDrawable(resid);
                        d.setBounds(0, 0, px, px);
                        post(d, a.source);
                    }
                }

                private void post(final Drawable d, String source) {
                    Log.i("Posting image=" + source);

                    view.post(new Runnable() {
                        @Override
                        public void run() {
                            Rect bounds = d.getBounds();

                            lld.addLevel(0, 0, d);
                            lld.setBounds(0, 0, bounds.width(), bounds.height());
                            lld.setLevel(0);

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                if (d instanceof AnimatedImageDrawable)
                                    ((AnimatedImageDrawable) d).start();
                            }

                            view.setText(view.getText());

                            //view.invalidate();
                            //view.requestLayout();
                        }
                    });
                }
            });

            return lld;
        } catch (Throwable ex) {
            Log.e(ex);

            Drawable d = context.getDrawable(R.drawable.twotone_broken_image_24);
            d.setBounds(0, 0, px, px);
            return d;
        }
    }

    private static Map<Drawable, Rect> drawableBounds = new WeakHashMap<>();

    static void fitDrawable(final Drawable d, final AnnotatedSource a, float scale, final View view) {
        synchronized (drawableBounds) {
            if (drawableBounds.containsKey(d))
                d.setBounds(drawableBounds.get(d));
            else
                drawableBounds.put(d, d.copyBounds());
        }

        Rect bounds = d.getBounds();
        int w = Math.round(Helper.dp2pixels(view.getContext(), bounds.width()) * scale);
        int h = Math.round(Helper.dp2pixels(view.getContext(), bounds.height()) * scale);

        if (a.width == 0 && a.height != 0)
            a.width = Math.round(a.height * w / (float) h);
        if (a.height == 0 && a.width != 0)
            a.height = Math.round(a.width * h / (float) w);

        if (a.width != 0 && a.height != 0) {
            boolean swap = ((w > h) != (a.width > a.height)) && false;
            w = Math.round(Helper.dp2pixels(view.getContext(), swap ? a.height : a.width) * scale);
            h = Math.round(Helper.dp2pixels(view.getContext(), swap ? a.width : a.height) * scale);
        }

        float width = view.getContext().getResources().getDisplayMetrics().widthPixels;
        View v = view;
        while (v != null) {
            width -= v.getPaddingStart() + v.getPaddingEnd();
            if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams lparam = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                width -= lparam.leftMargin + lparam.rightMargin;
            }

            ViewParent parent = v.getParent();
            v = (parent instanceof View ? (View) parent : null);
        }

        if (w > width) {
            float s = width / w;
            w = Math.round(w * s);
            h = Math.round(h * s);
        }

        d.setBounds(0, 0, w, h);

        if (view instanceof TextView) {
            int tc = ((TextView) view).getCurrentTextColor();
            int bg = ColorUtils.setAlphaComponent(tc, Math.round(255 * 0.05f));
            d.setColorFilter(bg, PorterDuff.Mode.DST_OVER);
        }
    }

    static String getDataUriType(String source) {
        int colon = source.indexOf(':');
        if (colon < 0)
            return null;
        int semi = source.indexOf(';');
        if (semi < 0)
            return null;

        return source.substring(colon + 1, semi);
    }

    static ByteArrayInputStream getDataUriStream(String source) {
        // "<img src=\"data:image/png;base64,iVBORw0KGgoAAA" +
        // "ANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4" +
        // "//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU" +
        // "5ErkJggg==\" alt=\"Red dot\" />";

        // https://en.wikipedia.org/wiki/Data_URI_scheme
        try {
            int comma = source.indexOf(',');
            if (comma < 0)
                throw new IllegalArgumentException("Comma missing");

            String base64 = source.substring(comma + 1);
            byte[] bytes = Base64.decode(base64.getBytes(), 0);
            return new ByteArrayInputStream(bytes);
        } catch (IllegalArgumentException ex) {
            String excerpt = source.substring(0, Math.min(100, source.length()));
            throw new IllegalArgumentException(excerpt, ex);
        }
    }

    private static Drawable getCachedImage(Context context, long id, String source) {
        if (id < 0)
            return null;

        File file = getCacheFile(context, id, source,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.P ? ".png" : ".blob");
        if (file.exists()) {
            Log.i("Using cached " + file);
            file.setLastModified(new Date().getTime());

            DisplayMetrics dm = context.getResources().getDisplayMetrics();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                try {
                    return getScaledDrawable(context, file, null, dm.widthPixels);
                } catch (IOException ex) {
                    Log.i(ex);
                    return null;
                }

            Bitmap bm = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bm != null) {
                Drawable d = new BitmapDrawable(context.getResources(), bm);
                d.setBounds(0, 0, Math.round(bm.getWidth() * dm.density), Math.round(bm.getHeight() * dm.density));
                return d;
            }
        }

        return null;
    }

    @NonNull
    private static Drawable downloadImage(Context context, long id, String source, String mimeType) throws IOException {
        Resources res = context.getResources();
        DisplayMetrics dm = res.getDisplayMetrics();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int timeout = prefs.getInt("timeout", ImageHelper.DOWNLOAD_TIMEOUT) * 1000;

        Bitmap bm;
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = Helper.openUrlRedirect(context, source, timeout);

            if (id > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                File file = getCacheFile(context, id, source, ".blob");
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    Helper.copy(urlConnection.getInputStream(), fos);
                }
                return getScaledDrawable(context, file, null, dm.widthPixels);
            }

            bm = getScaledBitmap(
                    urlConnection.getInputStream(),
                    source, mimeType,
                    Math.max(dm.widthPixels, dm.heightPixels));
        } finally {
            if (urlConnection != null)
                urlConnection.disconnect();
        }

        if (bm == null)
            throw new FileNotFoundException("Download image failed source=" + source);

        Log.i("Downloaded image source=" + source);

        if (id >= 0) {
            File file = getCacheFile(context, id, source, ".png");
            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file))) {
                bm.compress(Bitmap.CompressFormat.PNG, 90, os);
            }
        }

        Drawable d = new BitmapDrawable(res, bm);
        d.setBounds(0, 0, Math.round(bm.getWidth() * dm.density), Math.round(bm.getHeight() * dm.density));
        return d;
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    static Drawable getScaledDrawable(Context context, File file, String mimeType, int scaleToPixels) throws IOException {
        Drawable d;

        try {
            ImageDecoder.Source isource = ImageDecoder.createSource(file);
            d = ImageDecoder.decodeDrawable(isource, new ImageDecoder.OnHeaderDecodedListener() {
                @Override
                public void onHeaderDecoded(
                        @NonNull ImageDecoder decoder,
                        @NonNull ImageDecoder.ImageInfo info,
                        @NonNull ImageDecoder.Source source) {
                    int factor = 1;
                    while (info.getSize().getWidth() / factor > scaleToPixels)
                        factor *= 2;

                    decoder.setTargetSampleSize(factor);
                }
            });
        } catch (Throwable ex) {
            Log.i(ex);
            if (!"android.graphics.ImageDecoder$DecodeException".equals(ex.getClass().getName()))
                throw ex;
            /*
                Samsung:
                android.graphics.ImageDecoder$DecodeException: Failed to create image decoder with message 'unimplemented'Input contained an error.
                        at android.graphics.ImageDecoder.nCreate(ImageDecoder.java:-2)
                        at android.graphics.ImageDecoder.createFromFile(ImageDecoder.java:311)
                        at android.graphics.ImageDecoder.access$600(ImageDecoder.java:173)
                        at android.graphics.ImageDecoder$FileSource.createImageDecoder(ImageDecoder.java:543)
                        at android.graphics.ImageDecoder.decodeDrawableImpl(ImageDecoder.java:1758)
                        at android.graphics.ImageDecoder.decodeDrawable(ImageDecoder.java:1751)
             */
            Bitmap bm = _decodeImage(file, mimeType, scaleToPixels);
            if (bm == null)
                throw new FileNotFoundException(file.getAbsolutePath());
            d = new BitmapDrawable(context.getResources(), bm);
        }

        d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        return d;
    }

    static Bitmap getScaledBitmap(InputStream is, String source, String mimeType, int scaleToPixels) throws IOException {
        if (TextUtils.isEmpty(mimeType))
            mimeType = Helper.guessMimeType(source);

        if ("image/svg+xml".equals(mimeType))
            return ImageHelper.renderSvg(is, Color.WHITE, scaleToPixels);

        // ImageDecoder cannot decode streams

        BufferedInputStream bis = new BufferedInputStream(is);

        Log.i("Probe " + source);
        bis.mark(MAX_PROBE);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(bis, null, options);

        int factor = 1;
        while (options.outWidth / factor > scaleToPixels)
            factor *= 2;

        Log.i("Download " + source + " factor=" + factor);
        bis.reset();
        if (factor > 1) {
            options.inJustDecodeBounds = false;
            options.inSampleSize = factor;
            return BitmapFactory.decodeStream(bis, null, options);
        } else
            return BitmapFactory.decodeStream(bis);
    }

    @NonNull
    static File getCacheFile(Context context, long id, String source, String extension) {
        File dir = new File(context.getCacheDir(), "images");
        if (!dir.exists())
            dir.mkdir();
        return new File(dir, id + "_" + Math.abs(source.hashCode()) + extension);
    }

    static Bitmap decodeImage(File file, String mimeType, int scaleToPixels) {
        try {
            return _decodeImage(file, mimeType, scaleToPixels);
        } catch (IOException | OutOfMemoryError ex) {
            Log.e(ex);
            return null;
        }
    }

    private static Bitmap _decodeImage(File file, String mimeType, int scaleToPixels) throws IOException {
        if (mimeType == null)
            mimeType = Helper.guessMimeType(file.getName());

        if ("image/svg+xml".equals(mimeType))
            try (FileInputStream fis = new FileInputStream(file)) {
                return ImageHelper.renderSvg(fis, Color.WHITE, scaleToPixels);
            }

        Bitmap bm = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            try {
                ImageDecoder.Source isource = ImageDecoder.createSource(file);
                bm = ImageDecoder.decodeBitmap(isource, new ImageDecoder.OnHeaderDecodedListener() {
                    @Override
                    public void onHeaderDecoded(
                            @NonNull ImageDecoder decoder,
                            @NonNull ImageDecoder.ImageInfo info,
                            @NonNull ImageDecoder.Source source) {
                        int factor = 1;
                        while (info.getSize().getWidth() / factor > scaleToPixels ||
                                info.getSize().getWidth() * info.getSize().getHeight() * 8 / factor > MAX_BITMAP_SIZE)
                            factor *= 2;

                        Log.i("Decode image (decoder) factor=" + factor);
                        decoder.setTargetSampleSize(factor);
                    }
                });
            } catch (Throwable ex) {
                Log.i(ex);
            }

        if (bm == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);

            int factor = 1;
            while (options.outWidth / factor > scaleToPixels ||
                    options.outWidth * options.outHeight * 8 / factor > MAX_BITMAP_SIZE)
                factor *= 2;

            if (factor > 1) {
                Log.i("Decode image (factory) factor=" + factor);
                options.inJustDecodeBounds = false;
                options.inSampleSize = factor;
                bm = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            } else
                bm = BitmapFactory.decodeFile(file.getAbsolutePath());
        }

        if (bm != null) {
            Matrix rotation = getImageRotation(file);
            if (rotation != null) {
                Bitmap rotated = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), rotation, true);
                bm.recycle();
                bm = rotated;
            }
        }

        return bm;
    }

    static Matrix getImageRotation(File file) {
        try {
            ExifInterface exif = new ExifInterface(file);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_NORMAL:
                    return null;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.setScale(-1, 1);
                    return matrix;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.setRotate(180);
                    matrix.postScale(-1, 1);
                    return matrix;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    matrix.setRotate(90);
                    matrix.postScale(-1, 1);
                    return matrix;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    matrix.setRotate(-90);
                    matrix.postScale(-1, 1);
                    return matrix;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.setRotate(90);
                    return matrix;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.setRotate(180);
                    return matrix;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.setRotate(-90);
                    return matrix;
                default:
                    return null;
            }
        } catch (Throwable ex /* IOException */) {
            /*
                java.lang.RuntimeException: setDataSourceCallback failed: status = 0x80000000
                java.lang.RuntimeException: setDataSourceCallback failed: status = 0x80000000
                  at android.media.MediaMetadataRetriever._setDataSource(Native Method)
                  at android.media.MediaMetadataRetriever.setDataSource(MediaMetadataRetriever.java:226)
                  at androidx.exifinterface.media.ExifInterface.getHeifAttributes(SourceFile:5716)
                  at androidx.exifinterface.media.ExifInterface.loadAttributes(SourceFile:4556)
                  at androidx.exifinterface.media.ExifInterface.initForFilename(SourceFile:5195)
                  at androidx.exifinterface.media.ExifInterface.<init>(SourceFile:3926)
             */
            Log.w(ex);
            return null;
        }
    }

    static class AnnotatedSource {
        private String source;
        private int width = 0;
        private int height = 0;
        private boolean tracking = false;

        // Encapsulate some ugliness

        AnnotatedSource(String source) {
            this.source = source;

            if (source != null && source.endsWith("###")) {
                int pos = source.substring(0, source.length() - 3).lastIndexOf("###");
                if (pos > 0) {
                    int x = source.indexOf("x", pos + 3);
                    int s = source.indexOf(":", pos + 3);
                    if (x > 0 && s > x)
                        try {
                            this.width = Integer.parseInt(source.substring(pos + 3, x));
                            this.height = Integer.parseInt(source.substring(x + 1, s));
                            this.tracking = Boolean.parseBoolean(source.substring(s + 1, source.length() - 3));
                            this.source = source.substring(0, pos);
                        } catch (NumberFormatException ex) {
                            Log.e(ex);
                        }
                }
            }
        }

        AnnotatedSource(String source, int width, int height, boolean tracking) {
            this.source = source;
            this.width = width;
            this.height = height;
            this.tracking = tracking;
        }

        public String getSource() {
            return this.source;
        }

        public boolean isTracking() {
            return this.tracking;
        }

        String getAnnotated() {
            return (width == 0 && height == 0
                    ? source
                    : source + "###" + width + "x" + height + ":" + tracking + "###");
        }
    }
}
