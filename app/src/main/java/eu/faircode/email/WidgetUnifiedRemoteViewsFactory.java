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

    Copyright 2018-2023 by Marcel Bokhorst (M66B)
*/

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

import javax.mail.Address;

public class WidgetUnifiedRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private Context context;
    private int appWidgetId;

    private boolean threading;
    private boolean subject_top;
    private boolean subject_italic;
    private boolean color_stripe;
    private long folder;
    private long account;
    private boolean unseen;
    private boolean flagged;
    private boolean daynight;
    private boolean highlight;
    private int highlight_color;
    private boolean separators;
    private boolean semi;
    private int background;
    private int font;
    private int padding;
    private boolean avatars;
    private int subject_lines;
    private boolean prefer_contact;
    private boolean only_contact;
    private boolean distinguish_contacts;
    private int colorStripeWidth;
    private int colorWidgetForeground;
    private int colorWidgetUnread;
    private int colorWidgetRead;
    private int colorSeparator;
    private boolean pro;
    private boolean hasColor;
    private boolean allColors;
    private List<TupleMessageWidget> messages = new ArrayList<>();

    private static final int MAX_WIDGET_MESSAGES = 500;

    WidgetUnifiedRemoteViewsFactory(final Context context, Intent intent) {
        this.context = context;
        this.appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    @Override
    public void onCreate() {
        Log.i("Widget factory create id=" + appWidgetId);
    }

    @Override
    public void onDataSetChanged() {
        Log.i("Widget factory changed id=" + appWidgetId);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        threading = prefs.getBoolean("threading", true);
        subject_top = prefs.getBoolean("subject_top", false);
        subject_italic = prefs.getBoolean("subject_italic", true);
        color_stripe = prefs.getBoolean("color_stripe", true);

        account = prefs.getLong("widget." + appWidgetId + ".account", -1L);
        folder = prefs.getLong("widget." + appWidgetId + ".folder", -1L);
        unseen = prefs.getBoolean("widget." + appWidgetId + ".unseen", false);
        flagged = prefs.getBoolean("widget." + appWidgetId + ".flagged", false);
        daynight = prefs.getBoolean("widget." + appWidgetId + ".daynight", false);
        highlight = prefs.getBoolean("widget." + appWidgetId + ".highlight", false);
        highlight_color = prefs.getInt("widget." + appWidgetId + ".highlight_color", Color.TRANSPARENT);
        semi = prefs.getBoolean("widget." + appWidgetId + ".semi", true);
        background = prefs.getInt("widget." + appWidgetId + ".background", Color.TRANSPARENT);
        separators = prefs.getBoolean("widget." + appWidgetId + ".separators", true);
        font = prefs.getInt("widget." + appWidgetId + ".font", 0);
        padding = prefs.getInt("widget." + appWidgetId + ".padding", 0);
        avatars = prefs.getBoolean("widget." + appWidgetId + ".avatars", false);
        subject_lines = prefs.getInt("widget." + appWidgetId + ".subject_lines", 1);

        prefer_contact = prefs.getBoolean("prefer_contact", false);
        only_contact = prefs.getBoolean("only_contact", false);
        distinguish_contacts = prefs.getBoolean("distinguish_contacts", false);

        boolean color_stripe_wide = prefs.getBoolean("color_stripe_wide", false);
        this.colorStripeWidth = Helper.dp2pixels(context, color_stripe_wide ? 12 : 6);

        colorWidgetForeground = ContextCompat.getColor(context, R.color.colorWidgetForeground);
        colorWidgetRead = ContextCompat.getColor(context, R.color.colorWidgetRead);
        colorSeparator = ContextCompat.getColor(context, R.color.lightColorSeparator);

        float lum = (float) ColorUtils.calculateLuminance(background);
        if (lum > 0.7f) {
            colorWidgetForeground = ColorUtils.blendARGB(colorWidgetForeground, Color.BLACK, 1.0f);
            colorWidgetRead = ColorUtils.blendARGB(colorWidgetRead, Color.BLACK, 1.0f);
            colorSeparator = ContextCompat.getColor(context, R.color.darkColorSeparator);
        }

        if (highlight) {
            if (highlight_color == Color.TRANSPARENT)
                highlight_color = prefs.getInt("highlight_color", colorWidgetForeground);
            colorWidgetUnread = ColorUtils.setAlphaComponent(highlight_color, 255);
        } else
            colorWidgetUnread = colorWidgetForeground;

        pro = ActivityBilling.isPro(context);

        DB db = DB.getInstance(context);
        try {
            db.beginTransaction();

            messages = db.message().getWidgetUnified(
                    account < 0 ? null : account,
                    folder < 0 ? null : folder,
                    threading, unseen, flagged,
                    MAX_WIDGET_MESSAGES);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        hasColor = false;
        allColors = color_stripe;
        if (account < 0)
            for (TupleMessageWidget message : messages)
                if (message.accountColor == null)
                    allColors = false;
                else
                    hasColor = true;
    }

    @Override
    public void onDestroy() {
        Log.i("Widget factory destroy id=" + appWidgetId);
    }

    @Override
    public int getCount() {
        return messages.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.item_widget_unified);
        int idFrom = (subject_top ? R.id.tvSubject : R.id.tvFrom);
        int idTime = (subject_top ? R.id.tvAccount : R.id.tvTime);
        int idSubject = (subject_top ? R.id.tvFrom : R.id.tvSubject);
        int idAccount = (subject_top ? R.id.tvTime : R.id.tvAccount);

        if (font == 0)
            font = 1; // Default small

        int sp = WidgetUnified.getFontSizeSp(font);
        views.setTextViewTextSize(idFrom, TypedValue.COMPLEX_UNIT_SP, sp);
        views.setTextViewTextSize(idTime, TypedValue.COMPLEX_UNIT_SP, sp);
        views.setTextViewTextSize(idSubject, TypedValue.COMPLEX_UNIT_SP, sp);
        views.setTextViewTextSize(idAccount, TypedValue.COMPLEX_UNIT_SP, sp);

        // Default no padding
        int px = (padding == 0 ? 0 : WidgetUnified.getPaddingPx(padding, context));
        views.setViewPadding(R.id.llMessage, px, px, px, px);

        if (position >= messages.size())
            return views;

        try {
            TupleMessageWidget message = messages.get(position);

            Intent thread = new Intent(context, ActivityView.class);
            thread.putExtra("account", message.account);
            thread.putExtra("folder", message.folder);
            thread.putExtra("thread", message.thread);
            thread.putExtra("id", message.id);
            views.setOnClickFillInIntent(R.id.llMessage, thread);

            int colorBackground =
                    (message.accountColor == null || !pro ? colorSeparator : message.accountColor);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                views.setViewLayoutWidth(R.id.stripe, colorStripeWidth, TypedValue.COMPLEX_UNIT_PX);
            views.setInt(R.id.stripe, "setBackgroundColor", colorBackground);
            views.setViewVisibility(R.id.stripe, hasColor && color_stripe ? View.VISIBLE : View.GONE);

            if (avatars) {
                ContactInfo[] info = ContactInfo.get(context, message.account, null, message.bimi_selector, message.from);
                views.setImageViewBitmap(R.id.avatar, info.length == 0 ? null : info[0].getPhotoBitmap());
            }
            views.setViewVisibility(R.id.avatar, avatars ? View.VISIBLE : View.GONE);

            Address[] recipients = ContactInfo.fillIn(message.from, prefer_contact, only_contact);
            boolean known = (distinguish_contacts && ContactInfo.getLookupUri(message.from) != null);

            SpannableString ssFrom = new SpannableString(pro
                    ? MessageHelper.formatAddressesShort(recipients)
                    : context.getString(R.string.title_pro_feature));
            SpannableString ssTime = new SpannableString(
                    Helper.getRelativeTimeSpanString(context, message.received));
            SpannableString ssSubject = new SpannableString(pro
                    ? TextUtils.isEmpty(message.subject) ? "" : message.subject
                    : context.getString(R.string.title_pro_feature));
            SpannableString ssAccount = new SpannableString(
                    TextUtils.isEmpty(message.accountName) ? "" : message.accountName);

            if (message.ui_seen) {
                if (subject_italic)
                    ssSubject.setSpan(new StyleSpan(Typeface.ITALIC), 0, ssSubject.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            } else {
                ssFrom.setSpan(new StyleSpan(Typeface.BOLD), 0, ssFrom.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                ssTime.setSpan(new StyleSpan(Typeface.BOLD), 0, ssTime.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                ssSubject.setSpan(new StyleSpan(subject_italic ? Typeface.BOLD_ITALIC : Typeface.BOLD), 0, ssSubject.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                ssAccount.setSpan(new StyleSpan(Typeface.BOLD), 0, ssAccount.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            }

            if (known)
                ssFrom.setSpan(new UnderlineSpan(), 0, ssFrom.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

            views.setTextViewText(idFrom, ssFrom);
            views.setTextViewText(idTime, ssTime);
            views.setTextViewText(idSubject, ssSubject);
            views.setTextViewText(idAccount, ssAccount);

            if (!daynight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateListAttr(R.id.separator, "setBackgroundTintList", 0);
            }

            if (daynight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                int textColorAttr = (message.ui_seen ? android.R.attr.textColorPrimary : android.R.attr.textColorLink);
                views.setColorStateListAttr(idFrom, "setTextColor", textColorAttr);
                views.setColorStateListAttr(idTime, "setTextColor", textColorAttr);
                views.setColorStateListAttr(idSubject, "setTextColor", textColorAttr);
                views.setColorStateListAttr(idAccount, "setTextColor", textColorAttr);
                views.setInt(R.id.separator, "setBackgroundColor", Color.WHITE);
                views.setColorStateListAttr(R.id.separator, "setBackgroundTintList", android.R.attr.colorControlNormal);
            } else {
                int textColor = (message.ui_seen ? colorWidgetRead : colorWidgetUnread);
                views.setTextColor(idFrom, textColor);
                views.setTextColor(idTime, textColor);
                views.setTextColor(idSubject, textColor);
                views.setTextColor(idAccount, textColor);
                views.setInt(R.id.separator, "setBackgroundColor", colorSeparator);
            }

            try {
                views.setInt(idSubject, "setMaxLines", subject_lines);
            } catch (Throwable ex) {
                Log.e(ex);
            }

            views.setViewVisibility(R.id.separator, separators ? View.VISIBLE : View.GONE);

            views.setViewVisibility(idAccount, account < 0 && !allColors ? View.VISIBLE : View.GONE);

        } catch (Throwable ex) {
            Log.e(ex);
        }

        return views;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        if (position >= messages.size())
            return -1;
        return messages.get(position).id;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}
