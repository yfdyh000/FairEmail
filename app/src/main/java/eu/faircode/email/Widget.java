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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.graphics.ColorUtils;
import androidx.preference.PreferenceManager;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class Widget extends AppWidgetProvider {
    private static final ExecutorService executor =
            Helper.getBackgroundExecutor(1, "widget");

    @Override
    public void onUpdate(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                boolean unseen_ignored = prefs.getBoolean("unseen_ignored", false);

                DB db = DB.getInstance(context);
                NumberFormat nf = NumberFormat.getIntegerInstance();

                for (int appWidgetId : appWidgetIds) {
                    String name = prefs.getString("widget." + appWidgetId + ".name", null);
                    long account = prefs.getLong("widget." + appWidgetId + ".account", -1L);
                    boolean semi = prefs.getBoolean("widget." + appWidgetId + ".semi", true);
                    int background = prefs.getInt("widget." + appWidgetId + ".background", Color.TRANSPARENT);
                    int layout = prefs.getInt("widget." + appWidgetId + ".layout", 0);
                    int version = prefs.getInt("widget." + appWidgetId + ".version", 0);

                    if (version <= 1550)
                        semi = true; // Legacy

                    List<EntityFolder> folders = db.folder().getNotifyingFolders(account);
                    if (folders == null)
                        folders = new ArrayList<>();

                    PendingIntent pi;
                    if (folders.size() == 1) {
                        Intent view = new Intent(context, ActivityView.class);
                        view.setAction("folder:" + folders.get(0).id);
                        view.putExtra("account", account);
                        view.putExtra("type", folders.get(0).type);
                        view.putExtra("refresh", true);
                        view.putExtra("version", version);
                        view.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        pi = PendingIntentCompat.getActivity(
                                context, appWidgetId, view, PendingIntent.FLAG_UPDATE_CURRENT);
                    } else {
                        if (account < 0) {
                            Intent view = new Intent(context, ActivityView.class);
                            view.setAction("unified");
                            view.putExtra("refresh", true);
                            view.putExtra("version", version);
                            view.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            pi = PendingIntentCompat.getActivity(
                                    context, ActivityView.PI_UNIFIED, view, PendingIntent.FLAG_UPDATE_CURRENT);
                        } else {
                            Intent view = new Intent(context, ActivityView.class);
                            view.setAction("folders:" + account);
                            view.putExtra("refresh", true);
                            view.putExtra("version", version);
                            view.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            pi = PendingIntentCompat.getActivity(
                                    context, appWidgetId, view, PendingIntent.FLAG_UPDATE_CURRENT);
                        }
                    }

                    TupleMessageStats stats = db.message().getWidgetUnseen(account < 0 ? null : account);
                    EntityLog.log(context, "Widget account=" + account + " ignore=" + unseen_ignored + " " + stats);

                    Integer unseen = (unseen_ignored ? stats.notifying : stats.unseen);
                    if (unseen == null)
                        unseen = 0;

                    RemoteViews views = new RemoteViews(context.getPackageName(),
                            layout == 0 ? R.layout.widget : R.layout.widget_new);

                    views.setOnClickPendingIntent(android.R.id.background, pi);

                    if (layout == 1)
                        views.setImageViewResource(R.id.ivMessage, unseen == 0
                                ? R.drawable.baseline_mail_outline_widget_24
                                : R.drawable.baseline_mail_widget_24);
                    else
                        views.setImageViewResource(R.id.ivMessage, unseen == 0
                                ? R.drawable.twotone_mail_outline_24
                                : R.drawable.baseline_mail_24);
                    views.setTextViewText(R.id.tvCount, unseen < 100 ? nf.format(unseen) : "99+");
                    views.setViewVisibility(R.id.tvCount, layout == 1 && unseen == 0 ? View.GONE : View.VISIBLE);

                    if (!TextUtils.isEmpty(name)) {
                        views.setTextViewText(R.id.tvAccount, name);
                        views.setViewVisibility(R.id.tvAccount, ViewStripe.VISIBLE);
                    }

                    if (background == Color.TRANSPARENT) {
                        if (semi)
                            views.setInt(android.R.id.background, "setBackgroundResource", R.drawable.widget_background);
                        else
                            views.setInt(android.R.id.background, "setBackgroundColor", background);

                        int colorWidgetForeground = context.getResources().getColor(R.color.colorWidgetForeground);
                        views.setInt(R.id.ivMessage, "setColorFilter", colorWidgetForeground);
                        views.setTextColor(R.id.tvCount, colorWidgetForeground);
                        views.setTextColor(R.id.tvAccount, colorWidgetForeground);
                    } else {
                        float lum = (float) ColorUtils.calculateLuminance(background);

                        if (semi)
                            background = ColorUtils.setAlphaComponent(background, 127);

                        views.setInt(android.R.id.background, "setBackgroundColor", background);

                        if (lum > 0.7f) {
                            views.setInt(R.id.ivMessage, "setColorFilter", Color.BLACK);
                            views.setTextColor(R.id.tvCount, Color.BLACK);
                            views.setTextColor(R.id.tvAccount, Color.BLACK);
                        }
                    }

                    int pad = Helper.dp2pixels(context, layout == 0 ? 3 : 6);
                    views.setViewPadding(R.id.content, pad, pad, pad, pad);

                    appWidgetManager.updateAppWidget(appWidgetId, views);
                }
            }
        });
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = prefs.edit();
            for (int appWidgetId : appWidgetIds) {
                String prefix = "widget." + appWidgetId + ".";
                for (String key : prefs.getAll().keySet())
                    if (key.startsWith(prefix))
                        editor.remove(key);
            }
            editor.apply();
        } catch (Throwable ex) {
            Log.e(ex);
        }
    }

    static void init(Context context, int appWidgetId) {
        update(context);
    }

    static void update(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        if (appWidgetManager == null) {
            Log.w("No app widget manager"); // Fairphone FP2
            return;
        }

        try {
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, Widget.class));

            Intent intent = new Intent(context, Widget.class);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
            context.sendBroadcast(intent);
        } catch (Throwable ex) {
            Log.e(ex);
            /*
                java.lang.RuntimeException: system server dead?
                  at android.appwidget.AppWidgetManager.getAppWidgetIds(AppWidgetManager.java:1053)
                  at eu.faircode.email.Widget.update(SourceFile:111)
                  at eu.faircode.email.ServiceSynchronize$6.onChanged(SourceFile:460)
                  at eu.faircode.email.ServiceSynchronize$6.onChanged(SourceFile:439)
                  at androidx.lifecycle.LiveData.considerNotify(SourceFile:131)
                  at androidx.lifecycle.LiveData.dispatchingValue(SourceFile:149)
                  at androidx.lifecycle.LiveData.setValue(SourceFile:307)
                  at androidx.lifecycle.LiveData$1.run(SourceFile:91)
                Caused by: android.os.DeadObjectException
             */
        }
    }
}
