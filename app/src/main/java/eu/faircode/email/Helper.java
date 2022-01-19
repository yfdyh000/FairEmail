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

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.UiModeManager;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.Settings;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.openintents.openpgp.util.OpenPgpApi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public class Helper {
    private static Boolean hasPlayStore = null;
    private static Boolean hasValidFingerprint = null;

    static final float LOW_LIGHT = 0.6f;

    static final int BUFFER_SIZE = 8192; // Same as in Files class
    static final long MIN_REQUIRED_SPACE = 250 * 1024L * 1024L;
    static final int MAX_REDIRECTS = 5; // https://www.freesoft.org/CIE/RFC/1945/46.htm
    static final int AUTOLOCK_GRACE = 7; // seconds
    static final long PIN_FAILURE_DELAY = 3; // seconds

    static final String PGP_BEGIN_MESSAGE = "-----BEGIN PGP MESSAGE-----";
    static final String PGP_END_MESSAGE = "-----END PGP MESSAGE-----";

    static final String PRIVACY_URI = "https://email.faircode.eu/privacy/";
    static final String XDA_URI = "https://forum.xda-developers.com/showthread.php?t=3824168";
    static final String SUPPORT_URI = "https://contact.faircode.eu/";
    static final String TEST_URI = "https://play.google.com/apps/testing/" + BuildConfig.APPLICATION_ID;
    static final String BIMI_PRIVACY_URI = "https://datatracker.ietf.org/doc/html/draft-brotman-ietf-bimi-guidance-03#section-7.4";
    static final String FAVICON_PRIVACY_URI = "https://en.wikipedia.org/wiki/Favicon";
    static final String GRAVATAR_PRIVACY_URI = "https://en.wikipedia.org/wiki/Gravatar";
    static final String LICENSE_URI = "https://www.gnu.org/licenses/gpl-3.0.html";
    static final String DONTKILL_URI = "https://dontkillmyapp.com/";
    static final String URI_SUPPORT_RESET_OPEN = "https://support.google.com/pixelphone/answer/6271667";
    static final String URI_SUPPORT_CONTACT_GROUP = "https://support.google.com/contacts/answer/30970";

    // https://developer.android.com/distribute/marketing-tools/linking-to-google-play#PerformingSearch
    private static final String PLAY_STORE_SEARCH = "https://play.google.com/store/search";

    private static final String[] ROMAN_1000 = {"", "M", "MM", "MMM"};
    private static final String[] ROMAN_100 = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
    private static final String[] ROMAN_10 = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
    private static final String[] ROMAN_1 = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};

    static final Pattern EMAIL_ADDRESS
            = Pattern.compile(
            "[\\S]{1,256}" +
                    "\\@" +
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                    "(" +
                    "\\." +
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                    ")+"
    );

    private static final ExecutorService executor = getBackgroundExecutor(1, "helper");

    static ExecutorService getBackgroundExecutor(int threads, final String name) {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger threadId = new AtomicInteger();

            @Override
            public Thread newThread(@NonNull Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setName("FairEmail_bg_" + name + "_" + threadId.getAndIncrement());
                thread.setPriority(THREAD_PRIORITY_BACKGROUND);
                return thread;
            }
        };

        if (threads == 0)
            return new ThreadPoolExecutorEx(
                    name,
                    0, Integer.MAX_VALUE,
                    60L, TimeUnit.SECONDS,
                    new SynchronousQueue<Runnable>(),
                    factory);
        else if (threads == 1)
            return new ThreadPoolExecutorEx(
                    name,
                    threads, threads,
                    0L, TimeUnit.MILLISECONDS,
                    new PriorityBlockingQueue<Runnable>(10, new PriorityComparator()),
                    factory) {
                private final AtomicLong sequenceId = new AtomicLong();

                @Override
                protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
                    RunnableFuture<T> task = super.newTaskFor(runnable, value);
                    if (runnable instanceof PriorityRunnable)
                        return new PriorityFuture<T>(task,
                                ((PriorityRunnable) runnable).getPriority(),
                                ((PriorityRunnable) runnable).getOrder());
                    else
                        return new PriorityFuture<>(task, 0, sequenceId.getAndIncrement());
                }
            };
        else
            return new ThreadPoolExecutorEx(
                    name,
                    threads, threads,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(),
                    factory);
    }

    private static class ThreadPoolExecutorEx extends ThreadPoolExecutor {
        private String name;

        public ThreadPoolExecutorEx(
                String name,
                int corePoolSize, int maximumPoolSize,
                long keepAliveTime, TimeUnit unit,
                BlockingQueue<Runnable> workQueue,
                ThreadFactory threadFactory) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
            this.name = name;
        }

        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            Log.d("Executing " + t.getName());
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            Log.d("Executed " + name + " pending=" + getQueue().size());
        }
    }

    private static class PriorityFuture<T> implements RunnableFuture<T> {
        private int priority;
        private long order;
        private RunnableFuture<T> wrapped;

        PriorityFuture(RunnableFuture<T> wrapped, int priority, long order) {
            this.wrapped = wrapped;
            this.priority = priority;
            this.order = order;
        }

        public int getPriority() {
            return this.priority;
        }

        public long getOrder() {
            return this.order;
        }

        @Override
        public void run() {
            wrapped.run();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return wrapped.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return wrapped.isCancelled();
        }

        @Override
        public boolean isDone() {
            return wrapped.isDone();
        }

        @Override
        public T get() throws ExecutionException, InterruptedException {
            return wrapped.get();
        }

        @Override
        public T get(long timeout, @NonNull TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
            return wrapped.get(timeout, unit);
        }
    }

    private static class PriorityComparator implements Comparator<Runnable> {
        @Override
        public int compare(Runnable r1, Runnable r2) {
            if (r1 instanceof PriorityFuture<?> && r2 instanceof PriorityFuture<?>) {
                Integer p1 = ((PriorityFuture<?>) r1).getPriority();
                Integer p2 = ((PriorityFuture<?>) r2).getPriority();
                int p = p1.compareTo(p2);
                if (p == 0) {
                    Long o1 = ((PriorityFuture<?>) r1).getOrder();
                    Long o2 = ((PriorityFuture<?>) r2).getOrder();
                    return o1.compareTo(o2);
                } else
                    return p;
            } else
                return 0;
        }
    }

    static class PriorityRunnable implements Runnable {
        private int priority;
        private long order;

        int getPriority() {
            return this.priority;
        }

        long getOrder() {
            return this.order;
        }

        PriorityRunnable(int priority, long order) {
            this.priority = priority;
            this.order = order;
        }

        @Override
        public void run() {
            Log.i("Run priority=" + priority);
        }
    }

    // Features

    static boolean hasPermission(Context context, String name) {
        return (ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED);
    }

    static boolean hasPermissions(Context context, String[] permissions) {
        for (String permission : permissions)
            if (!hasPermission(context, permission))
                return false;
        return true;
    }

    static String[] getOAuthPermissions() {
        List<String> permissions = new ArrayList<>();
        //permissions.add(Manifest.permission.READ_CONTACTS); // profile
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            permissions.add(Manifest.permission.GET_ACCOUNTS);
        return permissions.toArray(new String[0]);
    }

    static boolean hasCustomTabs(Context context, Uri uri) {
        String scheme = (uri == null ? null : uri.getScheme());
        if (!"http".equals(scheme) && !"https".equals(scheme))
            return false;

        PackageManager pm = context.getPackageManager();
        Intent view = new Intent(Intent.ACTION_VIEW, uri);

        List<ResolveInfo> ris = pm.queryIntentActivities(view, 0); // action whitelisted
        for (ResolveInfo info : ris) {
            Intent intent = new Intent();
            intent.setAction(ACTION_CUSTOM_TABS_CONNECTION);
            intent.setPackage(info.activityInfo.packageName);
            if (pm.resolveService(intent, 0) != null)
                return true;
        }

        return false;
    }

    static boolean hasWebView(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            if (pm.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)) {
                new WebView(context);
                return true;
            } else
                return false;
        } catch (Throwable ex) {
            /*
                Caused by: java.lang.RuntimeException: Package manager has died
                    at android.app.ApplicationPackageManager.hasSystemFeature(ApplicationPackageManager.java:414)
                    at eu.faircode.email.Helper.hasWebView(SourceFile:375)
                    at eu.faircode.email.ApplicationEx.onCreate(SourceFile:110)
                    at android.app.Instrumentation.callApplicationOnCreate(Instrumentation.java:1014)
                    at android.app.ActivityThread.handleBindApplication(ActivityThread.java:4751)
             */
            return false;
        }
    }

    static boolean canPrint(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.hasSystemFeature(PackageManager.FEATURE_PRINTING);
        } catch (Throwable ex) {
            Log.e(ex);
            return false;
        }
    }

    static Boolean isIgnoringOptimizations(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return null;

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null)
            return null;

        return pm.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID);
    }

    static boolean isOptimizing12(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || true)
            return false;

        Boolean ignoring = Helper.isIgnoringOptimizations(context);
        return (ignoring != null && !ignoring);
    }

    static Integer getBatteryLevel(Context context) {
        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm == null)
                return null;
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Throwable ex) {
            Log.e(ex);
            return null;
        }
    }

    static boolean isCharging(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return false;
        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm == null)
                return false;
            return bm.isCharging();
        } catch (Throwable ex) {
            Log.e(ex);
            return false;
        }
    }

    static boolean isPlayStoreInstall() {
        return BuildConfig.PLAY_STORE_RELEASE;
    }

    static boolean isAmazonInstall() {
        return BuildConfig.AMAZON_RELEASE;
    }

    static boolean hasPlayStore(Context context) {
        if (hasPlayStore == null)
            try {
                PackageManager pm = context.getPackageManager();
                pm.getPackageInfo("com.android.vending", 0);
                hasPlayStore = true;
            } catch (PackageManager.NameNotFoundException ex) {
                Log.i(ex);
                hasPlayStore = false;
            } catch (Throwable ex) {
                Log.e(ex);
                hasPlayStore = false;
            }
        return hasPlayStore;
    }

    static boolean isSecure(Context context) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                ContentResolver resolver = context.getContentResolver();
                int enabled = Settings.System.getInt(resolver, Settings.Secure.LOCK_PATTERN_ENABLED, 0);
                return (enabled != 0);
            } else {
                KeyguardManager kgm = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                return (kgm != null && kgm.isDeviceSecure());
            }
        } catch (Throwable ex) {
            Log.e(ex);
            return false;
        }
    }

    static String getOpenKeychainPackage(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("openpgp_provider", "org.sufficientlysecure.keychain");
    }

    static boolean isOpenKeychainInstalled(Context context) {
        String provider = getOpenKeychainPackage(context);

        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(OpenPgpApi.SERVICE_INTENT_2);
        intent.setPackage(provider);
        List<ResolveInfo> ris = pm.queryIntentServices(intent, 0);

        return (ris != null && ris.size() > 0);
    }

    static boolean isInstalled(Context context, String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    static boolean isComponentEnabled(Context context, Class<?> clazz) {
        PackageManager pm = context.getPackageManager();
        int state = pm.getComponentEnabledSetting(new ComponentName(context, clazz));
        return (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
    }

    static void enableComponent(Context context, Class<?> clazz, boolean whether) {
        enableComponent(context, clazz.getName(), whether);
    }

    static void enableComponent(Context context, String name, boolean whether) {
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(
                new ComponentName(context, name),
                whether
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    static void setKeyboardIncognitoMode(EditText view, Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean incognito_keyboard = prefs.getBoolean("incognito_keyboard", false);
        if (incognito_keyboard)
            try {
                view.setImeOptions(view.getImeOptions() | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
            } catch (Throwable ex) {
                Log.e(ex);
            }
    }

    static boolean isAccessibilityEnabled(Context context) {
        try {
            AccessibilityManager am =
                    (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            return (am != null && am.isEnabled());
        } catch (Throwable ex) {
            Log.e(ex);
            return false;
        }
    }

    static String getStandbyBucketName(int bucket) {
        switch (bucket) {
            case UsageStatsManager.STANDBY_BUCKET_ACTIVE:
                return "active";
            case UsageStatsManager.STANDBY_BUCKET_WORKING_SET:
                return "workingset";
            case UsageStatsManager.STANDBY_BUCKET_FREQUENT:
                return "frequent";
            case UsageStatsManager.STANDBY_BUCKET_RARE:
                return "rare";
            case UsageStatsManager.STANDBY_BUCKET_RESTRICTED:
                return "restricted";
            default:
                return Integer.toString(bucket);
        }
    }

    // View

    static int getActionBarHeight(Context context) {
        int actionBarHeight;
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            return TypedValue.complexToDimensionPixelSize(tv.data, dm);
        } else
            return Helper.dp2pixels(context, 56);
    }

    static int getBottomNavigationHeight(Context context) {
        int resid = context.getResources().getIdentifier("design_bottom_navigation_height", "dimen", context.getPackageName());
        if (resid <= 0)
            return Helper.dp2pixels(context, 56);
        else
            return context.getResources().getDimensionPixelSize(resid);
    }

    static ObjectAnimator getFabAnimator(View fab, LifecycleOwner owner) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(fab, "alpha", 0.9f, 1.0f);
        animator.setDuration(750L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.addUpdateListener(new ObjectAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (!owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                    return;
                fab.setScaleX((float) animation.getAnimatedValue());
                fab.setScaleY((float) animation.getAnimatedValue());
            }
        });
        return animator;
    }

    static Intent getChooser(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            PackageManager pm = context.getPackageManager();
            if (pm.queryIntentActivities(intent, 0).size() == 1)
                return intent;
            else
                return Intent.createChooser(intent, context.getString(R.string.title_select_app));
        } else
            return intent;
    }

    static void share(Context context, File file, String type, String name) {
        try {
            _share(context, file, type, name);
        } catch (Throwable ex) {
            // java.lang.IllegalArgumentException: Failed to resolve canonical path for ...
            Log.e(ex);
        }
    }

    static void _share(Context context, File file, String type, String name) {
        // https://developer.android.com/reference/androidx/core/content/FileProvider
        Uri uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID, file);
        Log.i("uri=" + uri + " type=" + type);

        // Build intent
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndTypeAndNormalize(uri, type);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (!("message/rfc822".equals(type) ||
                "message/delivery-status".equals(type) ||
                "message/disposition-notification".equals(type) ||
                "text/rfc822-headers".equals(type)))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        if (!TextUtils.isEmpty(name))
            intent.putExtra(Intent.EXTRA_TITLE, Helper.sanitizeFilename(name));
        Log.i("Intent=" + intent + " type=" + type);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Get targets
            List<ResolveInfo> ris = null;
            try {
                PackageManager pm = context.getPackageManager();
                ris = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo ri : ris) {
                    Log.i("Target=" + ri);
                    context.grantUriPermission(ri.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            } catch (Throwable ex) {
                Log.e(ex);
                /*
                    java.lang.RuntimeException: Package manager has died
                      at android.app.ApplicationPackageManager.queryIntentActivitiesAsUser(ApplicationPackageManager.java:571)
                      at android.app.ApplicationPackageManager.queryIntentActivities(ApplicationPackageManager.java:557)
                      at eu.faircode.email.Helper.share(SourceFile:489)
                 */
            }

            // Check if viewer available
            if (ris == null || ris.size() == 0)
                if (isTnef(type, null))
                    viewFAQ(context, 155);
                else
                    reportNoViewer(context, intent, null);
            else
                context.startActivity(intent);
        } else
            context.startActivity(intent);
    }

    static boolean isTnef(String type, String name) {
        // https://en.wikipedia.org/wiki/Transport_Neutral_Encapsulation_Format
        if ("application/ms-tnef".equals(type) ||
                "application/vnd.ms-tnef".equals(type))
            return true;

        if ("application/octet-stream".equals(type) &&
                "winmail.dat".equalsIgnoreCase(name))
            return true;

        return false;
    }

    static void view(Context context, Intent intent) {
        Uri uri = intent.getData();
        if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
            view(context, intent.getData(), false);
        else
            try {
                context.startActivity(intent);
            } catch (Throwable ex) {
                reportNoViewer(context, intent, ex);
            }
    }

    static void view(Context context, Uri uri, boolean browse) {
        view(context, uri, null, browse, false);
    }

    static void view(Context context, Uri uri, boolean browse, boolean task) {
        view(context, uri, null, browse, task);
    }

    static void view(Context context, Uri uri, String mimeType, boolean browse, boolean task) {
        if (context == null) {
            Log.e(new Throwable("view"));
            return;
        }

        boolean has = hasCustomTabs(context, uri);
        Log.i("View=" + uri + " browse=" + browse + " task=" + task + " has=" + has);

        if (browse || !has) {
            try {
                Intent view = new Intent(Intent.ACTION_VIEW);
                if (mimeType == null)
                    view.setData(uri);
                else
                    view.setDataAndType(uri, mimeType);
                if (task)
                    view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(view);
            } catch (Throwable ex) {
                reportNoViewer(context, uri, ex);
            }
        } else {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean navbar_colorize = prefs.getBoolean("navbar_colorize", false);
            int colorPrimary = resolveColor(context, R.attr.colorPrimary);
            int colorPrimaryDark = resolveColor(context, R.attr.colorPrimaryDark);

            CustomTabColorSchemeParams.Builder schemes = new CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(colorPrimary)
                    .setSecondaryToolbarColor(colorPrimaryDark);
            if (navbar_colorize)
                schemes.setNavigationBarColor(colorPrimaryDark);

            // https://developer.chrome.com/multidevice/android/customtabs
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder()
                    .setDefaultColorSchemeParams(schemes.build())
                    .setColorScheme(Helper.isDarkTheme(context)
                            ? CustomTabsIntent.COLOR_SCHEME_DARK
                            : CustomTabsIntent.COLOR_SCHEME_LIGHT)
                    .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                    .setUrlBarHidingEnabled(true)
                    .setStartAnimations(context, R.anim.activity_open_enter, R.anim.activity_open_exit)
                    .setExitAnimations(context, R.anim.activity_close_enter, R.anim.activity_close_exit);

            CustomTabsIntent customTabsIntent = builder.build();
            try {
                customTabsIntent.launchUrl(context, uri);
            } catch (Throwable ex) {
                reportNoViewer(context, uri, ex);
            }
        }
    }

    static void customTabsWarmup(Context context) {
        try {
            CustomTabsClient.bindCustomTabsService(context, "com.android.chrome", new CustomTabsServiceConnection() {
                @Override
                public void onCustomTabsServiceConnected(@NonNull ComponentName name, @NonNull CustomTabsClient client) {
                    Log.i("Warming up custom tabs");
                    try {
                        client.warmup(0);
                    } catch (Throwable ex) {
                        Log.w(ex);
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    // Do nothing
                }
            });
        } catch (Throwable ex) {
            Log.w(ex);
        }
    }

    static String getFAQLocale() {
        switch (Locale.getDefault().getLanguage()) {
            case "de":
                return "de-rDE";
            case "fr":
                return "fr-rFR";
            case "it":
                return "it-rIT";
            case "ro":
                return "ro-rRO";
            default:
                return null;
        }
    }

    static void viewFAQ(Context context, int question) {
        viewFAQ(context, question, true /* Google translate */);
    }

    static void viewFAQ(Context context, int question, boolean english) {
        // Redirection is done to prevent text editors from opening the link
        // https://email.faircode.eu/faq -> https://github.com/M66B/FairEmail/blob/master/FAQ.md
        // https://email.faircode.eu/docs -> https://github.com/M66B/FairEmail/tree/master/docs
        // https://github.com/M66B/FairEmail/blob/master/FAQ.md#user-content-faq1
        // https://github.com/M66B/FairEmail/blob/master/docs/FAQ-de-rDE.md#user-content-faq1

        String base;
        String locale = (english ? null : getFAQLocale());
        if (locale == null)
            base = "https://email.faircode.eu/faq";
        else
            base = "https://email.faircode.eu/docs/FAQ-" + locale + ".md";

        if (question == 0)
            view(context, Uri.parse(base + "#top"), "text/html", false, false);
        else
            view(context, Uri.parse(base + "#user-content-faq" + question), "text/html", false, false);
    }

    static Uri getPrivacyUri(Context context) {
        // https://translate.google.com/translate?sl=auto&tl=<language>&u=<url>
        return Uri.parse(PRIVACY_URI)
                .buildUpon()
                .appendQueryParameter("language", Locale.getDefault().getLanguage())
                .appendQueryParameter("tag", Locale.getDefault().toLanguageTag())
                .build();
    }

    static Uri getSupportUri(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String language = prefs.getString("language", null);
        Locale slocale = Resources.getSystem().getConfiguration().locale;

        return Uri.parse(SUPPORT_URI)
                .buildUpon()
                .appendQueryParameter("product", "fairemailsupport")
                .appendQueryParameter("version", BuildConfig.VERSION_NAME + BuildConfig.REVISION)
                .appendQueryParameter("locale", slocale.toString())
                .appendQueryParameter("language", language == null ? "" : language)
                .appendQueryParameter("installed", Helper.hasValidFingerprint(context) ? "" : "Other")
                .build();
    }

    static Intent getIntentIssue(Context context) {
        if (ActivityBilling.isPro(context)) {
            String version = BuildConfig.VERSION_NAME + BuildConfig.REVISION + "/" +
                    (Helper.hasValidFingerprint(context) ? "1" : "3") +
                    (BuildConfig.PLAY_STORE_RELEASE ? "p" : "") +
                    (BuildConfig.DEBUG ? "d" : "") +
                    (ActivityBilling.isPro(context) ? "+" : "");
            Intent intent = new Intent(Intent.ACTION_SEND);
            //intent.setPackage(BuildConfig.APPLICATION_ID);
            intent.setType("text/plain");

            try {
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{Log.myAddress().getAddress()});
            } catch (UnsupportedEncodingException ex) {
                Log.w(ex);
            }

            intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.title_issue_subject, version));

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String language = prefs.getString("language", null);
            boolean reporting = prefs.getBoolean("crash_reports", false);
            String uuid = prefs.getString("uuid", null);
            Locale slocale = Resources.getSystem().getConfiguration().locale;

            String html = "<br><br>";

            html += "<p style=\"font-size:small;\">";
            html += "Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")<br>";
            html += "Device: " + Build.MANUFACTURER + " " + Build.MODEL + " " + Build.DEVICE + "<br>";
            html += "Locale: " + Html.escapeHtml(slocale.toString()) + "<br>";
            if (language != null)
                html += "Language: " + Html.escapeHtml(language) + "<br>";
            if ((reporting || BuildConfig.TEST_RELEASE) && uuid != null)
                html += "UUID: " + Html.escapeHtml(uuid) + "<br>";
            html += "</p>";

            intent.putExtra(Intent.EXTRA_TEXT, HtmlHelper.getText(context, html));
            intent.putExtra(Intent.EXTRA_HTML_TEXT, html);

            return intent;
        } else {
            if (Helper.hasValidFingerprint(context))
                return new Intent(Intent.ACTION_VIEW, getSupportUri(context));
            else
                return new Intent(Intent.ACTION_VIEW, Uri.parse(XDA_URI));
        }
    }

    static Intent getIntentRate(Context context) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + BuildConfig.APPLICATION_ID));
    }

    static long getInstallTime(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            if (pi != null)
                return pi.firstInstallTime;
        } catch (Throwable ex) {
            Log.e(ex);
        }
        return 0;
    }

    static boolean isSupportedDevice() {
        if ("Amazon".equals(Build.BRAND) && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        /*
            java.lang.IllegalArgumentException: Comparison method violates its general contract!
            java.lang.IllegalArgumentException: Comparison method violates its general contract!
            at java.util.TimSort.mergeHi(TimSort.java:864)
            at java.util.TimSort.mergeAt(TimSort.java:481)
            at java.util.TimSort.mergeCollapse(TimSort.java:406)
            at java.util.TimSort.sort(TimSort.java:210)
            at java.util.TimSort.sort(TimSort.java:169)
            at java.util.Arrays.sort(Arrays.java:2010)
            at java.util.Collections.sort(Collections.java:1883)
            at android.view.ViewGroup$ChildListForAccessibility.init(ViewGroup.java:7181)
            at android.view.ViewGroup$ChildListForAccessibility.obtain(ViewGroup.java:7138)
            at android.view.ViewGroup.dispatchPopulateAccessibilityEventInternal(ViewGroup.java:2734)
            at android.view.View.dispatchPopulateAccessibilityEvent(View.java:5617)
            at android.view.View.sendAccessibilityEventUncheckedInternal(View.java:5582)
            at android.view.View.sendAccessibilityEventUnchecked(View.java:5566)
            at android.view.View.sendAccessibilityEventInternal(View.java:5543)
            at android.view.View.sendAccessibilityEvent(View.java:5512)
            at android.view.View.onFocusChanged(View.java:5449)
            at android.view.View.handleFocusGainInternal(View.java:5229)
            at android.view.ViewGroup.handleFocusGainInternal(ViewGroup.java:651)
            at android.view.View.requestFocusNoSearch(View.java:7950)
            at android.view.View.requestFocus(View.java:7929)
            at android.view.ViewGroup.requestFocus(ViewGroup.java:2612)
            at android.view.ViewGroup.onRequestFocusInDescendants(ViewGroup.java:2657)
            at android.view.ViewGroup.requestFocus(ViewGroup.java:2613)
            at android.view.View.requestFocus(View.java:7896)
            at android.view.View.requestFocus(View.java:7875)
            at androidx.recyclerview.widget.RecyclerView.recoverFocusFromState(SourceFile:3788)
            at androidx.recyclerview.widget.RecyclerView.dispatchLayoutStep3(SourceFile:4023)
            at androidx.recyclerview.widget.RecyclerView.dispatchLayout(SourceFile:3652)
            at androidx.recyclerview.widget.RecyclerView.consumePendingUpdateOperations(SourceFile:1877)
            at androidx.recyclerview.widget.RecyclerView$w.run(SourceFile:5044)
            at android.view.Choreographer$CallbackRecord.run(Choreographer.java:781)
            at android.view.Choreographer.doCallbacks(Choreographer.java:592)
            at android.view.Choreographer.doFrame(Choreographer.java:559)
            at android.view.Choreographer$FrameDisplayEventReceiver.run(Choreographer.java:767)
         */
            return false;
        }

        return true;
    }

    static boolean isGoogle() {
        return "Google".equalsIgnoreCase(Build.MANUFACTURER);
    }

    static boolean isSamsung() {
        return "Samsung".equalsIgnoreCase(Build.MANUFACTURER);
    }

    static boolean isOnePlus() {
        return "OnePlus".equalsIgnoreCase(Build.MANUFACTURER);
    }

    static boolean isHuawei() {
        return "HUAWEI".equalsIgnoreCase(Build.MANUFACTURER);
    }

    static boolean isXiaomi() {
        return "Xiaomi".equalsIgnoreCase(Build.MANUFACTURER);
    }

    static boolean isMeizu() {
        return "Meizu".equalsIgnoreCase(Build.MANUFACTURER);
    }

    static boolean isAsus() {
        return "asus".equalsIgnoreCase(Build.MANUFACTURER);
    }

    static boolean isWiko() {
        return "WIKO".equalsIgnoreCase(Build.MANUFACTURER);
    }

    static boolean isLenovo() {
        return "LENOVO".equalsIgnoreCase(Build.MANUFACTURER);
    }

    static boolean isOppo() {
        return "OPPO".equalsIgnoreCase(Build.MANUFACTURER);
    }

    static boolean isRealme() {
        return "realme".equalsIgnoreCase(Build.MANUFACTURER);
    }

    static boolean isBlackview() {
        return "Blackview".equalsIgnoreCase(Build.MANUFACTURER);
    }

    static boolean isSony() {
        return "sony".equalsIgnoreCase(Build.MANUFACTURER);
    }

    static boolean isSurfaceDuo() {
        return ("Microsoft".equalsIgnoreCase(Build.MANUFACTURER) && "Surface Duo".equals(Build.MODEL));
    }

    static boolean isArc() {
        // https://github.com/google/talkback/blob/master/utils/src/main/java/com/google/android/accessibility/utils/FeatureSupport.java
        return (Build.DEVICE != null) && Build.DEVICE.matches(".+_cheets|cheets_.+");
    }

    static boolean isStaminaEnabled(Context context) {
        // https://dontkillmyapp.com/sony
        if (!isSony())
            return false;

        try {
            ContentResolver resolver = context.getContentResolver();
            return (Settings.Secure.getInt(resolver, "somc.stamina_mode", 0) > 0);
        } catch (Throwable ex) {
            Log.e(ex);
            return false;
        }
    }

    static boolean isKilling() {
        // https://dontkillmyapp.com/
        return (isSamsung() ||
                isOnePlus() ||
                isHuawei() ||
                isXiaomi() ||
                isMeizu() ||
                isAsus() ||
                isWiko() ||
                isLenovo() ||
                isOppo() ||
                // Vivo
                isRealme() ||
                isBlackview() ||
                isSony() ||
                BuildConfig.DEBUG);
    }

    static boolean isDozeRequired() {
        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.R && false);
    }

    static String getUiModeType(Context context) {
        try {
            UiModeManager uimm =
                    (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
            int uiModeType = uimm.getCurrentModeType();
            switch (uiModeType) {
                case Configuration.UI_MODE_TYPE_UNDEFINED:
                    return "undefined";
                case Configuration.UI_MODE_TYPE_NORMAL:
                    return "normal";
                case Configuration.UI_MODE_TYPE_DESK:
                    return "desk";
                case Configuration.UI_MODE_TYPE_CAR:
                    return "car";
                case Configuration.UI_MODE_TYPE_TELEVISION:
                    return "television";
                case Configuration.UI_MODE_TYPE_APPLIANCE:
                    return "applicance";
                case Configuration.UI_MODE_TYPE_WATCH:
                    return "watch";
                case Configuration.UI_MODE_TYPE_VR_HEADSET:
                    return "vr headset";
                default:
                    return Integer.toString(uiModeType);
            }
        } catch (Throwable ex) {
            Log.w(ex);
            return null;
        }
    }

    static void reportNoViewer(Context context, @NonNull Uri uri, @Nullable Throwable ex) {
        reportNoViewer(context, new Intent().setData(uri), ex);
    }

    static void reportNoViewer(Context context, @NonNull Intent intent, @Nullable Throwable ex) {
        if (ex != null) {
            if (ex instanceof ActivityNotFoundException && BuildConfig.PLAY_STORE_RELEASE)
                Log.w(ex);
            else
                Log.e(ex);
        }

        if (Helper.isTnef(intent.getType(), null)) {
            Helper.viewFAQ(context, 155);
            return;
        }

        View dview = LayoutInflater.from(context).inflate(R.layout.dialog_no_viewer, null);
        TextView tvName = dview.findViewById(R.id.tvName);
        TextView tvFullName = dview.findViewById(R.id.tvFullName);
        TextView tvType = dview.findViewById(R.id.tvType);
        TextView tvException = dview.findViewById(R.id.tvException);

        String title = intent.getStringExtra(Intent.EXTRA_TITLE);
        Uri data = intent.getData();
        String type = intent.getType();
        String fullName = (data == null ? intent.toString() : data.toString());
        String extension = (data == null ? null : getExtension(data.getLastPathSegment()));

        tvName.setText(title == null ? fullName : title);
        tvFullName.setText(fullName);
        tvFullName.setVisibility(title == null ? View.GONE : View.VISIBLE);

        tvType.setText(type);

        tvException.setText(ex == null ? null : ex.toString());
        tvException.setVisibility(ex == null ? View.GONE : View.VISIBLE);

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setView(dview)
                .setNegativeButton(android.R.string.cancel, null);

        if (hasPlayStore(context) && !TextUtils.isEmpty(extension)) {
            builder.setNeutralButton(R.string.title_no_viewer_search, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    try {
                        Uri search = Uri.parse(PLAY_STORE_SEARCH)
                                .buildUpon()
                                .appendQueryParameter("q", extension)
                                .build();
                        Intent intent = new Intent(Intent.ACTION_VIEW, search);
                        context.startActivity(intent);
                    } catch (Throwable ex) {
                        Log.e(ex);
                        ToastEx.makeText(context, ex.toString(), Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        builder.show();
    }

    static void excludeFromRecents(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null)
                return;

            List<ActivityManager.AppTask> tasks = am.getAppTasks();
            if (tasks == null || tasks.size() == 0)
                return;

            tasks.get(0).setExcludeFromRecents(true);
        } catch (Throwable ex) {
            Log.e(ex);
        }
    }

    static int getOffset(TextView widget, Spannable buffer, MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();

        x -= widget.getTotalPaddingLeft();
        y -= widget.getTotalPaddingTop();

        x += widget.getScrollX();
        y += widget.getScrollY();

        Layout layout = widget.getLayout();
        int line = layout.getLineForVertical(y);
        return layout.getOffsetForHorizontal(line, x);
    }

    static String getRequestKey(Fragment fragment) {
        String who;
        try {
            Class<?> cls = fragment.getClass();
            while (!cls.isAssignableFrom(Fragment.class))
                cls = cls.getSuperclass();
            Field f = cls.getDeclaredField("mWho");
            f.setAccessible(true);
            who = (String) f.get(fragment);
        } catch (Throwable ex) {
            Log.w(ex);
            String we = fragment.toString();
            int pa = we.indexOf('(');
            int sp = we.indexOf(' ', pa);
            who = we.substring(pa + 1, sp);
        }

        return fragment.getClass().getName() + ":result:" + who;
    }

    // Graphics

    static int dp2pixels(Context context, int dp) {
        float scale = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * scale);
    }

    static int pixels2dp(Context context, float pixels) {
        float scale = context.getResources().getDisplayMetrics().density;
        return Math.round(pixels / scale);
    }

    static float getTextSize(Context context, int zoom) {
        TypedArray ta = null;
        try {
            if (zoom == 0)
                ta = context.obtainStyledAttributes(
                        R.style.TextAppearance_AppCompat_Small, new int[]{android.R.attr.textSize});
            else if (zoom == 2)
                ta = context.obtainStyledAttributes(
                        R.style.TextAppearance_AppCompat_Large, new int[]{android.R.attr.textSize});
            else
                ta = context.obtainStyledAttributes(
                        R.style.TextAppearance_AppCompat_Medium, new int[]{android.R.attr.textSize});
            return ta.getDimension(0, 0);
        } finally {
            if (ta != null)
                ta.recycle();
        }
    }

    static int resolveColor(Context context, int attr) {
        return resolveColor(context, attr, 0xFF0000);
    }

    static int resolveColor(Context context, int attr, int def) {
        int[] attrs = new int[]{attr};
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs);
        int color = a.getColor(0, def);
        a.recycle();
        return color;
    }

    static void setViewsEnabled(ViewGroup view, boolean enabled) {
        for (int i = 0; i < view.getChildCount(); i++) {
            View child = view.getChildAt(i);
            if ("ignore".equals(child.getTag()))
                continue;
            if (child instanceof Spinner ||
                    child instanceof EditText ||
                    child instanceof CheckBox ||
                    child instanceof ImageView /* =ImageButton */ ||
                    child instanceof RadioButton ||
                    (child instanceof Button && "disable".equals(child.getTag())))
                child.setEnabled(enabled);
            else if (child instanceof BottomNavigationView) {
                Menu menu = ((BottomNavigationView) child).getMenu();
                menu.setGroupEnabled(0, enabled);
            } else if (child instanceof RecyclerView)
                ; // do nothing
            else if (child instanceof ViewGroup)
                setViewsEnabled((ViewGroup) child, enabled);
        }
    }

    static void hide(View view) {
        view.setPadding(0, 1, 0, 0);

        ViewGroup.LayoutParams lparam = view.getLayoutParams();
        lparam.width = 0;
        lparam.height = 1;
        if (lparam instanceof ConstraintLayout.LayoutParams)
            ((ConstraintLayout.LayoutParams) lparam).setMargins(0, 0, 0, 0);
        view.setLayoutParams(lparam);
    }

    static boolean isNight(Context context) {
        // https://developer.android.com/guide/topics/ui/look-and-feel/darktheme#configuration_changes
        int uiMode = context.getResources().getConfiguration().uiMode;
        Log.i("UI mode=0x" + Integer.toHexString(uiMode));
        return ((uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
    }

    static boolean isDarkTheme(Context context) {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.themeName, tv, true);
        return (tv.string != null && !"light".contentEquals(tv.string));
    }

    static void showKeyboard(final View view) {
        final Context context = view.getContext();
        InputMethodManager imm =
                (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm == null)
            return;

        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.i("showKeyboard view=" + view);
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 250);
    }

    static void hideKeyboard(final View view) {
        final Context context = view.getContext();
        InputMethodManager imm =
                (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm == null)
            return;

        Log.i("hideKeyboard view=" + view);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    static String getViewName(View view) {
        StringBuilder sb = new StringBuilder(_getViewName(view));
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof View)
                sb.insert(0, '/').insert(0, _getViewName((View) parent));
            parent = parent.getParent();
        }
        return sb.toString();
    }

    private static String _getViewName(View view) {
        if (view == null)
            return "<null>";
        int id = view.getId();
        if (id == View.NO_ID)
            return "";
        try {
            return view.getContext().getResources().getResourceEntryName(id);
        } catch (Throwable ex) {
            return ex.toString();
        }
    }

    static int getBytesPerPixel(Bitmap.Config config) {
        switch (config) {
            case ALPHA_8:
                return 1;
            case RGB_565:
                return 2;
            case ARGB_4444:
                return 4;
            case ARGB_8888:
                return 8;
            case RGBA_F16:
                return 8;
            case HARDWARE:
                return 0;
            default:
                return 8;
        }
    }

    // Formatting

    private static final DecimalFormat df = new DecimalFormat("@@");

    static String humanReadableByteCount(long bytes) {
        return humanReadableByteCount(bytes, true);
    }

    static String humanReadableByteCount(long bytes, boolean si) {
        int sign = (int) Math.signum(bytes);
        bytes = Math.abs(bytes);

        int unit = (si ? 1000 : 1024);
        if (bytes < unit)
            return sign * bytes + " B";

        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return df.format(sign * bytes / Math.pow(unit, exp)) + " " + pre + "B";
    }

    static boolean isPrintableChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        if (block == null || block == Character.UnicodeBlock.SPECIALS)
            return false;
        return !Character.isISOControl(c);
    }
    // https://issuetracker.google.com/issues/37054851

    static DateFormat getTimeInstance(Context context) {
        return getTimeInstance(context, SimpleDateFormat.MEDIUM);
    }

    static DateFormat getTimeInstance(Context context, int style) {
        if (context != null &&
                (style == SimpleDateFormat.SHORT || style == SimpleDateFormat.MEDIUM))
            return new SimpleDateFormat(getTimePattern(context, style));
        else
            return SimpleDateFormat.getTimeInstance(style);
    }

    static DateFormat getDateInstance(Context context) {
        return getDateInstance(context, SimpleDateFormat.MEDIUM);
    }

    private static DateFormat getDateInstance(Context context, int style) {
        return SimpleDateFormat.getDateInstance(style);
    }

    static DateFormat getDateTimeInstance(Context context) {
        return getDateTimeInstance(context, SimpleDateFormat.MEDIUM, SimpleDateFormat.MEDIUM);
    }

    static DateFormat getDateTimeInstance(Context context, int dateStyle, int timeStyle) {
        if (context != null &&
                (timeStyle == SimpleDateFormat.SHORT || timeStyle == SimpleDateFormat.MEDIUM)) {
            DateFormat dateFormat = getDateInstance(context, dateStyle);
            if (dateFormat instanceof SimpleDateFormat) {
                String datePattern = ((SimpleDateFormat) dateFormat).toPattern();
                String timePattern = getTimePattern(context, timeStyle);
                return new SimpleDateFormat(datePattern + " " + timePattern);
            }
        }

        return SimpleDateFormat.getDateTimeInstance(dateStyle, timeStyle);
    }

    private static String getTimePattern(Context context, int style) {
        // https://issuetracker.google.com/issues/37054851
        boolean is24Hour = android.text.format.DateFormat.is24HourFormat(context);
        String skeleton = (is24Hour ? "Hm" : "hm");
        if (style == SimpleDateFormat.MEDIUM)
            skeleton += "s";
        return android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
    }

    static CharSequence getRelativeTimeSpanString(Context context, long millis) {
        long now = System.currentTimeMillis();
        long span = Math.abs(now - millis);
        Time nowTime = new Time();
        Time thenTime = new Time();
        nowTime.set(now);
        thenTime.set(millis);
        if (span < DateUtils.DAY_IN_MILLIS && nowTime.weekDay == thenTime.weekDay)
            return getTimeInstance(context, SimpleDateFormat.SHORT).format(millis);
        else
            return DateUtils.getRelativeTimeSpanString(context, millis);
    }

    static void linkPro(final TextView tv) {
        if (ActivityBilling.isPro(tv.getContext()) && !BuildConfig.DEBUG)
            hide(tv);
        else {
            tv.getPaint().setUnderlineText(true);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    tv.getContext().startActivity(new Intent(tv.getContext(), ActivityBilling.class));
                }
            });
        }
    }

    static String getString(Context context, String language, int resid, Object... formatArgs) {
        if (language == null)
            return context.getString(resid, formatArgs);

        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(new Locale(language));
        Resources res = context.createConfigurationContext(configuration).getResources();
        return res.getString(resid, formatArgs);
    }

    static String[] getStrings(Context context, int resid, Object... formatArgs) {
        return getStrings(context, null, resid, formatArgs);
    }

    static String[] getStrings(Context context, String language, int resid, Object... formatArgs) {
        List<Locale> locales = new ArrayList<>();

        if (language != null)
            locales.add(new Locale(language));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Locale l = Locale.getDefault();
            locales.add(l);
            if (!"en".equals(language) && !"en".equals(l.getLanguage()))
                locales.add(new Locale("en"));
        } else {
            LocaleList ll = context.getResources().getConfiguration().getLocales();
            for (int i = 0; i < ll.size(); i++) {
                Locale l = ll.get(i);
                locales.add(l);
            }
        }

        List<String> result = new ArrayList<>();
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        for (Locale locale : locales) {
            configuration.setLocale(locale);
            Resources res = context.createConfigurationContext(configuration).getResources();
            String text = res.getString(resid, formatArgs);
            if (!result.contains(text))
                result.add(text);
        }

        return result.toArray(new String[0]);
    }

    static String getLocalizedAsset(Context context, String name) throws IOException {
        if (name == null || !name.contains("."))
            throw new IllegalArgumentException(name);

        String[] list = context.getResources().getAssets().list("");
        if (list == null)
            throw new IllegalArgumentException();

        List<String> names = new ArrayList<>();
        String[] c = name.split("\\.");
        List<String> assets = Arrays.asList(list);

        List<Locale> locales = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            locales.add(Locale.getDefault());
        else {
            LocaleList ll = context.getResources().getConfiguration().getLocales();
            for (int i = 0; i < ll.size(); i++)
                locales.add(ll.get(i));
        }

        for (Locale locale : locales) {
            String language = locale.getLanguage();
            String country = locale.getCountry();
            if ("en".equals(language) && "US".equals(country))
                names.add(name);
            else {
                String localized = c[0] + "-" + language + "-r" + country + "." + c[1];
                if (assets.contains(localized))
                    names.add(localized);
            }
        }

        for (Locale locale : locales) {
            String prefix = c[0] + "-" + locale.getLanguage();
            for (String asset : assets)
                if (asset.startsWith(prefix))
                    names.add(asset);
        }

        names.add(name);

        String asset = names.get(0);
        Log.i("Using " + asset +
                " of " + TextUtils.join(",", names) +
                " (" + TextUtils.join(",", locales) + ")");
        return asset;
    }

    static boolean containsWhiteSpace(String text) {
        return text.matches(".*\\s+.*");
    }

    static boolean containsControlChars(String text) {
        int codePoint;
        for (int offset = 0; offset < text.length(); ) {
            codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            switch (Character.getType(codePoint)) {
                case Character.CONTROL:     // \p{Cc}
                case Character.FORMAT:      // \p{Cf}
                case Character.PRIVATE_USE: // \p{Co}
                case Character.SURROGATE:   // \p{Cs}
                case Character.UNASSIGNED:  // \p{Cn}
                    return true;
            }
        }
        return false;
    }

    static boolean isSingleScript(String s) {
        // https://en.wikipedia.org/wiki/IDN_homograph_attack

        if (TextUtils.isEmpty(s))
            return true;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            return true;

        int codepoint;
        Character.UnicodeScript us;
        Character.UnicodeScript script = null;
        for (int i = 0; i < s.length(); ) {
            codepoint = s.codePointAt(i);
            i += Character.charCount(codepoint);
            us = Character.UnicodeScript.of(codepoint);
            if (us.equals(Character.UnicodeScript.COMMON))
                continue;
            if (script == null)
                script = us;
            else if (!us.equals(script))
                return false;
        }
        return true;
    }

    static Integer parseInt(String text) {
        if (TextUtils.isEmpty(text))
            return null;

        if (!TextUtils.isDigitsOnly(text))
            return null;

        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static String toRoman(int value) {
        if (value < 0 || value >= 4000)
            return Integer.toString(value);
        return ROMAN_1000[value / 1000] +
                ROMAN_100[(value % 1000) / 100] +
                ROMAN_10[(value % 100) / 10] +
                ROMAN_1[value % 10];
    }

    static ActionMode.Callback getActionModeWrapper(Context context) {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                for (int i = 0; i < menu.size(); i++) {
                    MenuItem item = menu.getItem(i);
                    Intent intent = item.getIntent();
                    if (intent != null) {
                        item.setIntent(null);
                        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem item) {
                                try {
                                    context.startActivity(intent);
                                } catch (Throwable ex) {
                                    reportNoViewer(context, intent, ex);
                                    /*
                                        java.lang.SecurityException: Permission Denial: starting Intent { act=android.intent.action.PROCESS_TEXT typ=text/plain cmp=com.microsoft.launcher/com.microsoft.bing.ProcessTextSearch launchParam=MultiScreenLaunchParams { mDisplayId=0 mFlags=0 } (has extras) } from ProcessRecord{befc028 15098:eu.faircode.email/u0a406} (pid=15098, uid=10406) not exported from uid 10021
                                            at android.os.Parcel.readException(Parcel.java:1693)
                                            at android.os.Parcel.readException(Parcel.java:1646)
                                            at android.app.ActivityManagerProxy.startActivity(ActivityManagerNative.java:3530)
                                            at android.app.Instrumentation.execStartActivity(Instrumentation.java:1645)
                                            at android.app.Activity.startActivityForResult(Activity.java:5033)
                                            at android.view.View.startActivityForResult(View.java:6413)
                                            at android.widget.Editor$ProcessTextIntentActionsHandler.fireIntent(Editor.java:7597)
                                            at android.widget.Editor$ProcessTextIntentActionsHandler.performMenuItemAction(Editor.java:7542)
                                            at android.widget.Editor$TextActionModeCallback.onActionItemClicked(Editor.java:4246)
                                            at com.android.internal.policy.DecorView$ActionModeCallback2Wrapper.onActionItemClicked(DecorView.java:2971)
                                            at com.android.internal.view.FloatingActionMode$3.onMenuItemSelected(FloatingActionMode.java:95)
                                            at com.android.internal.view.menu.MenuBuilder.dispatchMenuItemSelected(MenuBuilder.java:761)
                                            at com.android.internal.view.menu.MenuItemImpl.invoke(MenuItemImpl.java:157)
                                            at com.android.internal.view.menu.MenuBuilder.performItemAction(MenuBuilder.java:904)
                                            at com.android.internal.view.menu.MenuBuilder.performItemAction(MenuBuilder.java:894)
                                            at com.android.internal.view.FloatingActionMode$4.onMenuItemClick(FloatingActionMode.java:124)
                                            at com.android.internal.widget.FloatingToolbar$FloatingToolbarPopup$23.onItemClick(FloatingToolbar.java:1898)
                                            at android.widget.AdapterView.performItemClick(AdapterView.java:339)
                                            at android.widget.AbsListView.performItemClick(AbsListView.java:1705)
                                            at android.widget.AbsListView$PerformClick.run(AbsListView.java:4171)
                                            at android.widget.AbsListView$13.run(AbsListView.java:6735)
                                     */
                                }
                                return true;
                            }
                        });
                    }
                }

                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        };
    }

    // Files

    static String sanitizeFilename(String name) {
        if (name == null)
            return null;

        return name
                // Canonical files names cannot contain NUL
                .replace("\0", "")
                .replaceAll("[?:\"*|/\\\\<>]", "_");
    }

    static String getExtension(String filename) {
        if (filename == null)
            return null;
        int index = filename.lastIndexOf(".");
        if (index < 0)
            return null;
        return filename.substring(index + 1);
    }

    static String guessMimeType(String filename) {
        String type = null;

        String extension = Helper.getExtension(filename);
        if (extension != null) {
            extension = extension.toLowerCase(Locale.ROOT);
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }

        if (TextUtils.isEmpty(type))
            if ("csv".equals(extension))
                return "text/csv";
            else if ("eml".equals(extension))
                return "message/rfc822";
            else if ("gpx".equals(extension))
                return "application/gpx+xml";
            else if ("log".equals(extension))
                return "text/plain";
            else if ("ovpn".equals(extension))
                return "application/x-openvpn-profile";
            else if ("mbox".equals(extension))
                return "application/mbox"; // https://tools.ietf.org/html/rfc4155
            else
                return "application/octet-stream";

        return type;
    }

    static String guessExtension(String mimeType) {
        String extension = null;

        if (mimeType != null) {
            mimeType = mimeType.toLowerCase(Locale.ROOT);
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        }

        if (TextUtils.isEmpty(extension))
            if ("text/csv".equals(mimeType))
                return "csv";
            else if ("message/rfc822".equals(mimeType))
                return "eml";
            else if ("application/gpx+xml".equals(mimeType))
                return "gpx";
            else if ("application/x-openvpn-profile".equals(mimeType))
                return "ovpn";

        return extension;
    }

    static void writeText(File file, String content) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (content != null)
                out.write(content.getBytes());
        }
    }

    static String readStream(InputStream is) throws IOException {
        return readStream(is, StandardCharsets.UTF_8);
    }

    static String readStream(InputStream is, Charset charset) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream(Math.max(BUFFER_SIZE, is.available()));
        byte[] buffer = new byte[BUFFER_SIZE];
        for (int len = is.read(buffer); len != -1; len = is.read(buffer))
            os.write(buffer, 0, len);
        return new String(os.toByteArray(), charset);
    }

    static String readText(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            return readStream(in);
        }
    }

    public static void readBuffer(InputStream is, byte[] buffer) throws IOException {
        int left = buffer.length;
        while (left > 0) {
            int count = is.read(buffer, buffer.length - left, left);
            if (count < 0)
                throw new IOException("EOF");
            left -= count;
        }
    }

    static byte[] readBytes(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream(Math.max(BUFFER_SIZE, is.available()));
        byte[] buffer = new byte[BUFFER_SIZE];
        for (int len = is.read(buffer); len != -1; len = is.read(buffer))
            os.write(buffer, 0, len);
        return os.toByteArray();
    }

    static void copy(File src, File dst) throws IOException {
        try (InputStream is = new FileInputStream(src)) {
            try (OutputStream os = new FileOutputStream(dst)) {
                copy(is, os);
            }
        }
    }

    static long copy(Context context, Uri uri, File file) throws IOException {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            try (OutputStream os = new FileOutputStream(file)) {
                return copy(is, os);
            }
        }
    }

    static long copy(InputStream is, OutputStream os) throws IOException {
        long size = 0;
        byte[] buf = new byte[BUFFER_SIZE];
        int len;
        while ((len = is.read(buf)) > 0) {
            size += len;
            os.write(buf, 0, len);
        }
        return size;
    }

    static long getAvailableStorageSpace() {
        StatFs stats = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        return stats.getAvailableBlocksLong() * stats.getBlockSizeLong();
    }

    static long getTotalStorageSpace() {
        StatFs stats = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        return stats.getTotalBytes();
    }

    static long getSize(File dir) {
        long size = 0;
        File[] listed = dir.listFiles();
        if (listed != null)
            for (File file : listed)
                if (file.isDirectory())
                    size += getSize(file);
                else
                    size += file.length();
        return size;
    }

    static void openAdvanced(Intent intent) {
        // https://issuetracker.google.com/issues/72053350
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        intent.putExtra("android.content.extra.FANCY", true);
        intent.putExtra("android.content.extra.SHOW_FILESIZE", true);
        intent.putExtra("android.provider.extra.SHOW_ADVANCED", true);
        //File initial = Environment.getExternalStorageDirectory();
        //intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.fromFile(initial));
    }

    static HttpURLConnection openUrlRedirect(Context context, String source, int timeout) throws IOException {
        int redirects = 0;
        URL url = new URL(source);
        while (true) {
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setDoOutput(false);
            urlConnection.setReadTimeout(timeout);
            urlConnection.setConnectTimeout(timeout);
            urlConnection.setInstanceFollowRedirects(true);
            urlConnection.setRequestProperty("User-Agent", WebViewEx.getUserAgent(context));
            urlConnection.connect();

            try {
                int status = urlConnection.getResponseCode();

                if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                        status == HttpURLConnection.HTTP_MOVED_TEMP ||
                        status == HttpURLConnection.HTTP_SEE_OTHER ||
                        status == 307 /* Temporary redirect */ ||
                        status == 308 /* Permanent redirect */) {
                    if (++redirects > MAX_REDIRECTS)
                        throw new IOException("Too many redirects");

                    String header = urlConnection.getHeaderField("Location");
                    if (header == null)
                        throw new IOException("Location header missing");

                    String location = URLDecoder.decode(header, StandardCharsets.UTF_8.name());
                    url = new URL(url, location);
                    Log.i("Redirect #" + redirects + " to " + url);

                    urlConnection.disconnect();
                    continue;
                }

                if (status != HttpURLConnection.HTTP_OK)
                    throw new IOException("Error " + status + ": " + urlConnection.getResponseMessage());

                return urlConnection;
            } catch (IOException ex) {
                urlConnection.disconnect();
                throw ex;
            }
        }
    }

    // Cryptography

    static String sha256(String data) throws NoSuchAlgorithmException {
        return sha256(data.getBytes());
    }

    static String sha1(byte[] data) throws NoSuchAlgorithmException {
        return sha("SHA-1", data);
    }

    static String sha256(byte[] data) throws NoSuchAlgorithmException {
        return sha("SHA-256", data);
    }

    static String md5(byte[] data) throws NoSuchAlgorithmException {
        return sha("MD5", data);
    }

    static String sha(String digest, byte[] data) throws NoSuchAlgorithmException {
        byte[] bytes = MessageDigest.getInstance(digest).digest(data);
        return hex(bytes);
    }

    static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static String getFingerprint(Context context) {
        return getFingerprint(context, "SHA1");
    }

    static String getFingerprint(Context context, String hash) {
        try {
            PackageManager pm = context.getPackageManager();
            String pkg = context.getPackageName();
            PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
            byte[] cert = info.signatures[0].toByteArray();
            MessageDigest digest = MessageDigest.getInstance(hash);
            byte[] bytes = digest.digest(cert);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes)
                sb.append(String.format("%02X", b));
            return sb.toString();
        } catch (Throwable ex) {
            Log.e(ex);
            return null;
        }
    }

    static boolean hasValidFingerprint(Context context) {
        if (hasValidFingerprint == null) {
            hasValidFingerprint = false;

            String signed = getFingerprint(context);
            String[] fingerprints = new String[]{
                    context.getString(R.string.fingerprint),
                    context.getString(R.string.fingerprint_amazon)
            };

            for (String fingerprint : fingerprints)
                if (Objects.equals(signed, fingerprint)) {
                    hasValidFingerprint = true;
                    break;
                }
        }
        return hasValidFingerprint;
    }

    static boolean canAuthenticate(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String pin = prefs.getString("pin", null);
        if (!TextUtils.isEmpty(pin))
            return true;

        try {
            BiometricManager bm = BiometricManager.from(context);
            return (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS);
        } catch (Throwable ex) {
            /*
                java.lang.SecurityException: eu.faircode.email from uid 10377 not allowed to perform USE_FINGERPRINT
                  at android.os.Parcel.createException(Parcel.java:1953)
                  at android.os.Parcel.readException(Parcel.java:1921)
                  at android.os.Parcel.readException(Parcel.java:1871)
                  at android.hardware.fingerprint.IFingerprintService$Stub$Proxy.isHardwareDetected(IFingerprintService.java:460)
                  at android.hardware.fingerprint.FingerprintManager.isHardwareDetected(FingerprintManager.java:792)
                  at androidx.core.hardware.fingerprint.FingerprintManagerCompat.isHardwareDetected(SourceFile:3)
                  at androidx.biometric.BiometricManager.canAuthenticateWithFingerprint(SourceFile:3)
                  at androidx.biometric.BiometricManager.canAuthenticateWithFingerprintOrUnknownBiometric(SourceFile:2)
                  at androidx.biometric.BiometricManager.canAuthenticateCompat(SourceFile:10)
                  at androidx.biometric.BiometricManager.canAuthenticate(SourceFile:5)
             */
            Log.e(ex);
            return false;
        }
    }

    static boolean shouldAuthenticate(Context context, boolean pausing) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean biometrics = prefs.getBoolean("biometrics", false);
        String pin = prefs.getString("pin", null);

        if (biometrics || !TextUtils.isEmpty(pin)) {
            long now = new Date().getTime();
            long last_authentication = prefs.getLong("last_authentication", 0);
            long biometrics_timeout = prefs.getInt("biometrics_timeout", 2) * 60 * 1000L;
            boolean autolock_nav = prefs.getBoolean("autolock_nav", false);
            Log.i("Authentication valid until=" + new Date(last_authentication + biometrics_timeout));

            if (last_authentication + biometrics_timeout < now)
                return true;

            if (autolock_nav && pausing)
                last_authentication = now - biometrics_timeout + AUTOLOCK_GRACE * 1000L;
            else
                last_authentication = now;
            prefs.edit().putLong("last_authentication", last_authentication).apply();
        }

        return false;
    }

    static boolean shouldAutoLock(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean biometrics = prefs.getBoolean("biometrics", false);
        String pin = prefs.getString("pin", null);
        boolean autolock = prefs.getBoolean("autolock", true);
        return (autolock && (biometrics || !TextUtils.isEmpty(pin)));
    }

    static void authenticate(final FragmentActivity activity, final LifecycleOwner owner,
                             Boolean enabled, final
                             Runnable authenticated, final Runnable cancelled) {
        Log.i("Authenticate " + activity);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        String pin = prefs.getString("pin", null);

        if (enabled != null || TextUtils.isEmpty(pin)) {
            Log.i("Authenticate biometric enabled=" + enabled);
            BiometricPrompt.PromptInfo.Builder info = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(activity.getString(enabled == null ? R.string.app_name : R.string.title_setup_biometrics));

            KeyguardManager kgm = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && kgm != null && kgm.isDeviceSecure())
                info.setDeviceCredentialAllowed(true);
            else
                info.setNegativeButtonText(activity.getString(android.R.string.cancel));

            info.setConfirmationRequired(false);

            info.setSubtitle(activity.getString(enabled == null ? R.string.title_setup_biometrics_unlock
                    : enabled
                    ? R.string.title_setup_biometrics_disable
                    : R.string.title_setup_biometrics_enable));

            final BiometricPrompt prompt = new BiometricPrompt(activity, executor,
                    new BiometricPrompt.AuthenticationCallback() {
                        private int fails = 0;

                        @Override
                        public void onAuthenticationError(final int errorCode, @NonNull final CharSequence errString) {
                            Log.w("Authenticate biometric error " + errorCode + ": " + errString);

                            if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                    errorCode != BiometricPrompt.ERROR_CANCELED &&
                                    errorCode != BiometricPrompt.ERROR_USER_CANCELED)
                                ApplicationEx.getMainHandler().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        ToastEx.makeText(activity,
                                                "Error " + errorCode + ": " + errString,
                                                Toast.LENGTH_LONG).show();
                                    }
                                });

                            ApplicationEx.getMainHandler().post(cancelled);
                        }

                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            Log.i("Authenticate biometric succeeded");
                            setAuthenticated(activity);
                            ApplicationEx.getMainHandler().post(authenticated);
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            Log.w("Authenticate biometric failed");
                            if (++fails >= 3)
                                ApplicationEx.getMainHandler().post(cancelled);
                        }
                    });

            prompt.authenticate(info.build());

            final Runnable cancelPrompt = new Runnable() {
                @Override
                public void run() {
                    try {
                        prompt.cancelAuthentication();
                    } catch (Throwable ex) {
                        Log.e(ex);
                    }
                }
            };

            ApplicationEx.getMainHandler().postDelayed(cancelPrompt, 60 * 1000L);

            owner.getLifecycle().addObserver(new LifecycleObserver() {
                @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                public void onDestroy() {
                    Log.i("Authenticate destroyed");
                    ApplicationEx.getMainHandler().post(cancelPrompt);
                    owner.getLifecycle().removeObserver(this);
                }
            });

        } else {
            Log.i("Authenticate PIN");
            final View dview = LayoutInflater.from(activity).inflate(R.layout.dialog_pin_ask, null);
            final EditText etPin = dview.findViewById(R.id.etPin);

            etPin.setEnabled(false);

            final AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setView(dview)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                            String pin = prefs.getString("pin", "");
                            String entered = etPin.getText().toString();

                            Log.i("Authenticate PIN ok=" + pin.equals(entered));
                            if (pin.equals(entered)) {
                                prefs.edit()
                                        .remove("pin_failure_at")
                                        .remove("pin_failure_count")
                                        .apply();
                                setAuthenticated(activity);
                                ApplicationEx.getMainHandler().post(authenticated);
                            } else {
                                int count = prefs.getInt("pin_failure_count", 0) + 1;
                                prefs.edit()
                                        .putLong("pin_failure_at", new Date().getTime())
                                        .putInt("pin_failure_count", count)
                                        .apply();
                                ApplicationEx.getMainHandler().post(cancelled);
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.i("Authenticate PIN cancelled");
                            ApplicationEx.getMainHandler().post(cancelled);
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            Log.i("Authenticate PIN dismissed");
                            if (shouldAuthenticate(activity, false)) // Some Android versions call dismiss on OK
                                ApplicationEx.getMainHandler().post(cancelled);
                        }
                    })
                    .create();

            etPin.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                        return true;
                    } else
                        return false;
                }
            });

            etPin.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus)
                        try {
                            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                        } catch (Throwable ex) {
                            Log.e(ex);
                            /*
                                java.lang.IllegalArgumentException: View=DecorView@f197613[ActivityMain] not attached to window manager
                                        at android.view.WindowManagerGlobal.findViewLocked(WindowManagerGlobal.java:604)
                                        at android.view.WindowManagerGlobal.updateViewLayout(WindowManagerGlobal.java:493)
                                        at android.view.WindowManagerImpl.updateViewLayout(WindowManagerImpl.java:121)
                                        at android.app.Dialog.onWindowAttributesChanged(Dialog.java:1072)
                                        at androidx.appcompat.view.WindowCallbackWrapper.onWindowAttributesChanged(WindowCallbackWrapper:114)
                                        at android.view.Window.dispatchWindowAttributesChanged(Window.java:1236)
                                        at com.android.internal.policy.PhoneWindow.dispatchWindowAttributesChanged(PhoneWindow.java:3229)
                                        at android.view.Window.setSoftInputMode(Window.java:1123)
                                        at eu.faircode.email.Helper$15.onFocusChange(Helper:2169)
                                        at android.view.View.onFocusChanged(View.java:8828)
                                        at android.widget.TextView.onFocusChanged(TextView.java:12091)
                                        at android.widget.EditText.onFocusChanged(EditText.java:248)
                                        at android.view.View.handleFocusGainInternal(View.java:8498)
                                        at android.view.View.requestFocusNoSearch(View.java:14103)
                                        at android.view.View.requestFocus(View.java:14077)
                                        at android.view.View.requestFocus(View.java:14044)
                                        at android.view.View.requestFocus(View.java:13986)
                                        at eu.faircode.email.Helper$16.run(Helper:2187)
                             */
                        }
                }
            });

            try {
                dialog.show();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

                long pin_failure_at = prefs.getLong("pin_failure_at", 0);
                int pin_failure_count = prefs.getInt("pin_failure_count", 0);
                long wait = (long) Math.pow(PIN_FAILURE_DELAY, pin_failure_count) * 1000L;
                long delay = pin_failure_at + wait - new Date().getTime();
                Log.i("PIN wait=" + wait + " delay=" + delay);
                ApplicationEx.getMainHandler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        etPin.setCompoundDrawables(null, null, null, null);
                        etPin.setEnabled(true);
                        etPin.requestFocus();
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    }
                }, delay < 0 ? 0 : delay);

            } catch (Throwable ex) {
                Log.e(ex);
                /*
                    java.lang.RuntimeException: Unable to start activity ComponentInfo{eu.faircode.email/eu.faircode.email.ActivityMain}: java.lang.RuntimeException: InputChannel is not initialized.
                      at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:3477)
                      at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:3620)
                      at android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:83)
                      at android.app.servertransaction.TransactionExecutor.executeCallbacks(TransactionExecutor.java:135)
                      at android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:95)
                      at android.app.ActivityThread$H.handleMessage(ActivityThread.java:2183)
                      at android.os.Handler.dispatchMessage(Handler.java:107)
                      at android.os.Looper.loop(Looper.java:241)
                      at android.app.ActivityThread.main(ActivityThread.java:7604)
                      at java.lang.reflect.Method.invoke(Native Method)
                      at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:492)
                      at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:941)
                    Caused by: java.lang.RuntimeException: InputChannel is not initialized.
                      at android.view.InputEventReceiver.nativeInit(Native Method)
                      at android.view.InputEventReceiver.<init>(InputEventReceiver.java:71)
                      at android.view.ViewRootImpl$WindowInputEventReceiver.<init>(ViewRootImpl.java:7758)
                      at android.view.ViewRootImpl.setView(ViewRootImpl.java:1000)
                      at android.view.WindowManagerGlobal.addView(WindowManagerGlobal.java:393)
                      at android.view.WindowManagerImpl.addView(WindowManagerImpl.java:95)
                      at android.app.Dialog.show(Dialog.java:342)
                      at eu.faircode.email.Helper.authenticate(SourceFile:15)
                      at eu.faircode.email.ActivityMain.onCreate(SourceFile:24)
                      at android.app.Activity.performCreate(Activity.java:7822)
                 */
            }
        }
    }

    static void setAuthenticated(Context context) {
        Date now = new Date();
        Log.i("Authenticated now=" + now);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putLong("last_authentication", now.getTime()).apply();
    }

    static void clearAuthentication(Context context) {
        Log.i("Authenticate clear");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().remove("last_authentication").apply();
    }

    static void selectKeyAlias(final Activity activity, final LifecycleOwner owner, final String alias, final IKeyAlias intf) {
        final Context context = activity.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (alias != null)
                    try {
                        if (KeyChain.getPrivateKey(context, alias) != null) {
                            Log.i("Private key available alias=" + alias);
                            deliver(alias);
                            return;
                        }
                    } catch (KeyChainException ex) {
                        Log.w(ex);
                    } catch (Throwable ex) {
                        Log.e(ex);
                    }

                ApplicationEx.getMainHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        KeyChain.choosePrivateKeyAlias(activity, new KeyChainAliasCallback() {
                                    @Override
                                    public void alias(@Nullable final String alias) {
                                        Log.i("Selected key alias=" + alias);
                                        deliver(alias);
                                    }
                                },
                                null, null, null, -1, alias);
                    }
                });
            }

            private void deliver(final String selected) {
                ApplicationEx.getMainHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if (owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                            if (selected == null)
                                intf.onNothingSelected();
                            else
                                intf.onSelected(selected);
                        } else {
                            owner.getLifecycle().addObserver(new LifecycleObserver() {
                                @OnLifecycleEvent(Lifecycle.Event.ON_START)
                                public void onStart() {
                                    owner.getLifecycle().removeObserver(this);
                                    if (selected == null)
                                        intf.onNothingSelected();
                                    else
                                        intf.onSelected(selected);
                                }

                                @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                                public void onDestroy() {
                                    owner.getLifecycle().removeObserver(this);
                                }
                            });
                        }
                    }
                });
            }
        }).start();
    }

    interface IKeyAlias {
        void onSelected(String alias);

        void onNothingSelected();
    }

    public static String HMAC(String algo, int blocksize, byte[] key, byte[] text) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(algo);

        if (key.length > blocksize)
            key = md.digest(key);

        byte[] ipad = new byte[blocksize];
        byte[] opad = new byte[blocksize];

        for (int i = 0; i < key.length; i++) {
            ipad[i] = key[i];
            opad[i] = key[i];
        }

        for (int i = 0; i < blocksize; i++) {
            ipad[i] ^= 0x36;
            opad[i] ^= 0x5c;
        }

        byte[] digest;

        md.update(ipad);
        md.update(text);
        digest = md.digest();

        md.update(opad);
        md.update(digest);
        digest = md.digest();

        StringBuilder sb = new StringBuilder();
        for (byte b : digest)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // Miscellaneous

    static void gc() {
        if (BuildConfig.DEBUG) {
            Runtime.getRuntime().gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    static <T> List<List<T>> chunkList(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>(list.size() / size);
        for (int i = 0; i < list.size(); i += size)
            result.add(list.subList(i, i + size < list.size() ? i + size : list.size()));
        return result;
    }

    static long[] toLongArray(List<Long> list) {
        long[] result = new long[list.size()];
        for (int i = 0; i < list.size(); i++)
            result[i] = list.get(i);
        return result;
    }

    static List<Long> fromLongArray(long[] array) {
        List<Long> result = new ArrayList<>();
        for (int i = 0; i < array.length; i++)
            result.add(array[i]);
        return result;
    }

    static boolean equal(String[] a1, String[] a2) {
        if (a1 == null && a2 == null)
            return true;

        if (a1 == null || a2 == null)
            return false;

        if (a1.length != a2.length)
            return false;

        for (int i = 0; i < a1.length; i++)
            if (!a1[i].equals(a2[i]))
                return false;

        return true;
    }

    static int getSize(Bundle bundle) {
        Parcel p = Parcel.obtain();
        bundle.writeToParcel(p, 0);
        return p.dataSize();
    }
}
