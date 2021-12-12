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

    Copyright 2018-2021 by Marcel Bokhorst (M66B)
*/

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import java.util.Date;
import java.util.List;

public class ActivityMain extends ActivityBase implements FragmentManager.OnBackStackChangedListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private static final long SPLASH_DELAY = 1500L; // milliseconds
    private static final long RESTORE_STATE_INTERVAL = 3 * 60 * 1000L; // milliseconds
    private static final long SERVICE_START_DELAY = 5 * 1000L; // milliseconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean accept_unsupported = prefs.getBoolean("accept_unsupported", false);

        if (!accept_unsupported &&
                !Helper.isSupportedDevice() &&
                Helper.isPlayStoreInstall()) {
            setTheme(R.style.AppThemeBlueOrangeLight);
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_unsupported);

            Button btnContinue = findViewById(R.id.btnContinue);
            btnContinue.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.edit().putBoolean("accept_unsupported", true).commit();
                    ApplicationEx.restart(v.getContext());
                }
            });

            return;
        }

        Intent intent = getIntent();
        Uri data = (intent == null ? null : intent.getData());
        if (data != null &&
                "message".equals(data.getScheme()) &&
                ("email.faircode.eu".equals(data.getHost()) ||
                        BuildConfig.APPLICATION_ID.equals(data.getHost()))) {
            super.onCreate(savedInstanceState);

            Bundle args = new Bundle();
            args.putParcelable("data", data);

            new SimpleTask<EntityMessage>() {
                @Override
                protected EntityMessage onExecute(Context context, Bundle args) {
                    Uri data = args.getParcelable("data");
                    long id;
                    String f = data.getFragment();
                    if ("email.faircode.eu".equals(data.getHost()))
                        id = Long.parseLong(data.getFragment());
                    else {
                        String path = data.getPath();
                        if (path == null)
                            return null;
                        String[] parts = path.split("/");
                        if (parts.length < 1)
                            return null;
                        id = Long.parseLong(parts[1]);
                    }

                    DB db = DB.getInstance(context);
                    return db.message().getMessage(id);
                }

                @Override
                protected void onExecuted(Bundle args, EntityMessage message) {
                    finish();

                    if (message == null)
                        return;

                    Intent thread = new Intent(ActivityMain.this, ActivityView.class);
                    thread.setAction("thread:" + message.id);
                    thread.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    thread.putExtra("account", message.account);
                    thread.putExtra("folder", message.folder);
                    thread.putExtra("thread", message.thread);
                    thread.putExtra("filter_archive", true);
                    thread.putExtra("pinned", true);
                    thread.putExtra("msgid", message.msgid);

                    startActivity(thread);
                }

                @Override
                protected void onException(Bundle args, Throwable ex) {
                    // Ignored
                }
            }.execute(this, args, "message:linked");

            return;
        }

        boolean eula = prefs.getBoolean("eula", false);
        boolean sync_on_launch = prefs.getBoolean("sync_on_launch", false);

        prefs.registerOnSharedPreferenceChangeListener(this);

        if (eula) {
            try {
                super.onCreate(savedInstanceState);
            } catch (RuntimeException ex) {
                Log.e(ex);
                // https://issuetracker.google.com/issues/181805603
                finish();
                startActivity(getIntent());
                return;
            }

            long start = new Date().getTime();
            Log.i("Main boot");

            final Runnable splash = new Runnable() {
                @Override
                public void run() {
                    getWindow().setBackgroundDrawableResource(R.drawable.splash);
                }
            };

            final SimpleTask<Boolean> boot = new SimpleTask<Boolean>() {
                @Override
                protected void onPreExecute(Bundle args) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                        getMainHandler().postDelayed(splash, SPLASH_DELAY);
                }

                @Override
                protected void onPostExecute(Bundle args) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                        getMainHandler().removeCallbacks(splash);
                    getWindow().setBackgroundDrawable(null);
                }

                @Override
                protected Boolean onExecute(Context context, Bundle args) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    if (prefs.getBoolean("has_accounts", false))
                        return true;

                    DB db = DB.getInstance(context);
                    List<EntityAccount> accounts = db.account().getSynchronizingAccounts();
                    boolean hasAccounts = (accounts != null && accounts.size() > 0);

                    prefs.edit().putBoolean("has_accounts", hasAccounts).apply();

                    return hasAccounts;
                }

                @Override
                protected void onExecuted(Bundle args, Boolean hasAccounts) {
                    Bundle options = null;
                    try {
                        if (BuildConfig.DEBUG)
                            options = ActivityOptions.makeCustomAnimation(ActivityMain.this,
                                    R.anim.activity_open_enter, 0).toBundle();
                    } catch (Throwable ex) {
                        Log.e(ex);
                    }

                    if (hasAccounts) {
                        Intent view = new Intent(ActivityMain.this, ActivityView.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        // VX-N3
                        // https://developer.android.com/docs/quality-guidelines/core-app-quality
                        long now = new Date().getTime();
                        long last = prefs.getLong("last_launched", 0L);
                        if (!BuildConfig.PLAY_STORE_RELEASE &&
                                now - last > RESTORE_STATE_INTERVAL)
                            view.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);

                        Intent saved = args.getParcelable("intent");
                        if (saved == null) {
                            prefs.edit().putLong("last_launched", now).apply();
                            startActivity(view, options);
                            if (sync_on_launch)
                                ServiceUI.sync(ActivityMain.this, null);
                        } else
                            try {
                                startActivity(saved);
                            } catch (SecurityException ex) {
                                Log.w(ex);
                                startActivity(view);
                            }

                        getMainHandler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                ServiceSynchronize.watchdog(ActivityMain.this);
                                ServiceSend.watchdog(ActivityMain.this);
                            }
                        }, SERVICE_START_DELAY);
                    } else {
                        Intent setup = new Intent(ActivityMain.this, ActivitySetup.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(setup, options);
                    }

                    long end = new Date().getTime();
                    Log.i("Main booted " + (end - start) + " ms");

                    finish();
                }

                @Override
                protected void onException(Bundle args, Throwable ex) {
                    Log.unexpectedError(getSupportFragmentManager(), ex);
                }
            };

            if (Helper.shouldAuthenticate(this, false))
                Helper.authenticate(ActivityMain.this, ActivityMain.this, null,
                        new Runnable() {
                            @Override
                            public void run() {
                                Intent intent = getIntent();
                                Bundle args = new Bundle();
                                if (intent.hasExtra("intent"))
                                    args.putParcelable("intent", intent.getParcelableExtra("intent"));
                                boot.execute(ActivityMain.this, args, "main:accounts");
                            }
                        },
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    finish();
                                } catch (Throwable ex) {
                                    Log.w(ex);
                                    /*
                                    java.lang.NullPointerException: Attempt to invoke virtual method 'int com.android.server.fingerprint.ClientMonitor.stop(boolean)' on a null object reference
                                        at android.os.Parcel.createException(Parcel.java:1956)
                                        at android.os.Parcel.readException(Parcel.java:1918)
                                        at android.os.Parcel.readException(Parcel.java:1868)
                                        at android.app.IActivityManager$Stub$Proxy.finishActivity(IActivityManager.java:3797)
                                        at android.app.Activity.finish(Activity.java:5608)
                                        at android.app.Activity.finish(Activity.java:5632)
                                        at eu.faircode.email.ActivityMain$3.run(SourceFile:111)
                                        at eu.faircode.email.Helper$3$1.run(SourceFile:706)
                                        at android.os.Handler.handleCallback(Handler.java:873)
                                        at android.os.Handler.dispatchMessage(Handler.java:99)
                                        at android.os.Looper.loop(Looper.java:193)
                                        at android.app.ActivityThread.main(ActivityThread.java:6718)
                                        at java.lang.reflect.Method.invoke(Method.java:-2)
                                        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:493)
                                        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:858)
                                    Caused by: android.os.RemoteException: Remote stack trace:
                                        at com.android.server.fingerprint.FingerprintService$5.onTaskStackChanged(FingerprintService.java:239)
                                        at com.android.server.am.TaskChangeNotificationController.lambda$new$0(TaskChangeNotificationController.java:70)
                                        at com.android.server.am.-$$Lambda$TaskChangeNotificationController$kftD881t3KfWCASQEbeTkieVI2M.accept(Unknown Source:0)
                                        at com.android.server.am.TaskChangeNotificationController.forAllLocalListeners(TaskChangeNotificationController.java:263)
                                        at com.android.server.am.TaskChangeNotificationController.notifyTaskStackChanged(TaskChangeNotificationController.java:276)
                                    */
                                }
                            }
                        });
            else
                boot.execute(this, new Bundle(), "main:accounts");
        } else {
            SharedPreferences.Editor editor = prefs.edit();
            Configuration config = getResources().getConfiguration();

            // Default enable compact mode for smaller screens
            if (!config.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE))
                editor.putBoolean("compact", true);

            // Default disable landscape columns for small screens
            if (!config.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_NORMAL)) {
                editor.putBoolean("landscape", false);
                editor.putBoolean("landscape3", false);
            }

            editor.apply();

            if (Helper.isNight(this))
                setTheme(R.style.AppThemeBlueOrangeDark);
            else
                setTheme(R.style.AppThemeBlueOrangeLight);

            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportFragmentManager().addOnBackStackChangedListener(this);

            FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.content_frame, new FragmentEula()).addToBackStack("eula");
            fragmentTransaction.commit();
        }
    }

    @Override
    protected void onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public void onBackStackChanged() {
        int count = getSupportFragmentManager().getBackStackEntryCount();
        if (count == 0)
            finish();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if ("eula".equals(key))
            if (prefs.getBoolean(key, false))
                recreate();
    }
}
