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

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.Group;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.builder.ColorPickerClickListener;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;

import java.text.NumberFormat;

public class FragmentOptionsDisplay extends FragmentBase implements SharedPreferences.OnSharedPreferenceChangeListener {
    private Button btnTheme;
    private Spinner spStartup;
    private SwitchCompat swCards;
    private SwitchCompat swBeige;
    private SwitchCompat swTabularBackground;
    private SwitchCompat swShadow;
    private SwitchCompat swShadowHighlight;
    private SwitchCompat swDate;
    private SwitchCompat swDateFixed;
    private SwitchCompat swDateBold;
    private SwitchCompat swNavBarColorize;
    private SwitchCompat swPortrait2;
    private SwitchCompat swPortrait2c;
    private SwitchCompat swLandscape;
    private SwitchCompat swNavOptions;
    private SwitchCompat swNavMessageCount;
    private SwitchCompat swNavUnseenDrafts;

    private SwitchCompat swThreading;
    private SwitchCompat swThreadingUnread;
    private SwitchCompat swIndentation;
    private SwitchCompat swSeekbar;
    private SwitchCompat swActionbar;
    private SwitchCompat swActionbarColor;

    private SwitchCompat swHighlightUnread;
    private ViewButtonColor btnHighlightColor;
    private SwitchCompat swColorStripe;
    private SwitchCompat swColorStripeWide;
    private SwitchCompat swAvatars;
    private ImageButton ibBimi;
    private TextView tvBimiHint;
    private TextView tvBimiUnverified;
    private SwitchCompat swBimi;
    private SwitchCompat swGravatars;
    private TextView tvGravatarsHint;
    private SwitchCompat swFavicons;
    private TextView tvFaviconsHint;
    private SwitchCompat swGeneratedIcons;
    private SwitchCompat swIdenticons;
    private SwitchCompat swCircular;
    private ImageView ivRed;
    private ImageView ivGreen;
    private ImageView ivBlue;
    private TextView tvSaturation;
    private SeekBar sbSaturation;
    private TextView tvBrightness;
    private SeekBar sbBrightness;
    private TextView tvThreshold;
    private SeekBar sbThreshold;

    private Spinner spNameEmail;
    private SwitchCompat swPreferContact;
    private SwitchCompat swOnlyContact;
    private SwitchCompat swDistinguishContacts;
    private SwitchCompat swShowRecipients;
    private Spinner spFontSizeSender;
    private Spinner spSenderEllipsize;

    private SwitchCompat swSubjectTop;
    private SwitchCompat swSubjectItalic;
    private SwitchCompat swHighlightSubject;
    private Spinner spFontSizeSubject;
    private Spinner spSubjectEllipsize;

    private SwitchCompat swKeywords;
    private SwitchCompat swLabels;
    private SwitchCompat swFlags;
    private SwitchCompat swFlagsBackground;
    private SwitchCompat swPreview;
    private SwitchCompat swPreviewItalic;
    private Spinner spPreviewLines;
    private TextView tvPreviewLinesHint;

    private SwitchCompat swAddresses;
    private TextView tvMessageZoom;
    private SeekBar sbMessageZoom;
    private SwitchCompat swOverviewMode;
    private SwitchCompat swOverrideWidth;

    private SwitchCompat swContrast;
    private SwitchCompat swMonospaced;
    private SwitchCompat swMonospacedPre;
    private SwitchCompat swBackgroundColor;
    private SwitchCompat swTextColor;
    private SwitchCompat swTextSize;
    private SwitchCompat swTextFont;
    private SwitchCompat swTextAlign;
    private SwitchCompat swTextSeparators;
    private SwitchCompat swCollapseQuotes;
    private SwitchCompat swImagesPlaceholders;
    private SwitchCompat swImagesInline;
    private SwitchCompat swButtonExtra;
    private SwitchCompat swAttachmentsAlt;
    private SwitchCompat swThumbnails;

    private SwitchCompat swParseClasses;
    private SwitchCompat swAuthentication;
    private SwitchCompat swAuthenticationIndicator;

    private Group grpGravatars;

    private NumberFormat NF = NumberFormat.getNumberInstance();

    private final static String[] RESET_OPTIONS = new String[]{
            "theme", "startup", "cards", "beige", "tabular_card_bg", "shadow_unread", "shadow_highlight",
            "date", "date_fixed", "date_bold",
            "portrait2", "portrait2c", "landscape", "nav_options", "nav_count", "nav_unseen_drafts", "navbar_colorize",
            "threading", "threading_unread", "indentation", "seekbar", "actionbar", "actionbar_color",
            "highlight_unread", "highlight_color", "color_stripe", "color_stripe_wide",
            "avatars", "bimi", "gravatars", "favicons", "generated_icons", "identicons", "circular", "saturation", "brightness", "threshold",
            "email_format", "prefer_contact", "only_contact", "distinguish_contacts", "show_recipients",
            "font_size_sender", "sender_ellipsize",
            "subject_top", "subject_italic", "highlight_subject", "font_size_subject", "subject_ellipsize",
            "keywords_header", "labels_header", "flags", "flags_background",
            "preview", "preview_italic", "preview_lines",
            "addresses",
            "message_zoom", "overview_mode", "override_width", "contrast", "monospaced", "monospaced_pre",
            "background_color", "text_color", "text_size", "text_font", "text_align", "text_separators",
            "collapse_quotes", "image_placeholders", "inline_images", "button_extra", "attachments_alt", "thumbnails",
            "parse_classes",
            "authentication", "authentication_indicator"
    };

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        setSubtitle(R.string.title_setup);
        setHasOptionsMenu(true);

        View view = inflater.inflate(R.layout.fragment_options_display, container, false);

        // Get controls

        btnTheme = view.findViewById(R.id.btnTheme);
        spStartup = view.findViewById(R.id.spStartup);
        swCards = view.findViewById(R.id.swCards);
        swBeige = view.findViewById(R.id.swBeige);
        swTabularBackground = view.findViewById(R.id.swTabularCardBackground);
        swShadow = view.findViewById(R.id.swShadow);
        swShadowHighlight = view.findViewById(R.id.swShadowHighlight);
        swDate = view.findViewById(R.id.swDate);
        swDateFixed = view.findViewById(R.id.swDateFixed);
        swDateBold = view.findViewById(R.id.swDateBold);
        swPortrait2 = view.findViewById(R.id.swPortrait2);
        swPortrait2c = view.findViewById(R.id.swPortrait2c);
        swLandscape = view.findViewById(R.id.swLandscape);
        swNavOptions = view.findViewById(R.id.swNavOptions);
        swNavMessageCount = view.findViewById(R.id.swNavMessageCount);
        swNavUnseenDrafts = view.findViewById(R.id.swNavUnseenDrafts);
        swNavBarColorize = view.findViewById(R.id.swNavBarColorize);

        swThreading = view.findViewById(R.id.swThreading);
        swThreadingUnread = view.findViewById(R.id.swThreadingUnread);
        swIndentation = view.findViewById(R.id.swIndentation);
        swSeekbar = view.findViewById(R.id.swSeekbar);
        swActionbar = view.findViewById(R.id.swActionbar);
        swActionbarColor = view.findViewById(R.id.swActionbarColor);

        swHighlightUnread = view.findViewById(R.id.swHighlightUnread);
        btnHighlightColor = view.findViewById(R.id.btnHighlightColor);
        swColorStripe = view.findViewById(R.id.swColorStripe);
        swColorStripeWide = view.findViewById(R.id.swColorStripeWide);
        swAvatars = view.findViewById(R.id.swAvatars);
        swBimi = view.findViewById(R.id.swBimi);
        tvBimiHint = view.findViewById(R.id.tvBimiHint);
        tvBimiUnverified = view.findViewById(R.id.tvBimiUnverified);
        ibBimi = view.findViewById(R.id.ibBimi);
        swGravatars = view.findViewById(R.id.swGravatars);
        tvGravatarsHint = view.findViewById(R.id.tvGravatarsHint);
        swFavicons = view.findViewById(R.id.swFavicons);
        tvFaviconsHint = view.findViewById(R.id.tvFaviconsHint);
        swGeneratedIcons = view.findViewById(R.id.swGeneratedIcons);
        swIdenticons = view.findViewById(R.id.swIdenticons);
        swCircular = view.findViewById(R.id.swCircular);
        ivRed = view.findViewById(R.id.ivRed);
        ivGreen = view.findViewById(R.id.ivGreen);
        ivBlue = view.findViewById(R.id.ivBlue);
        tvSaturation = view.findViewById(R.id.tvSaturation);
        sbSaturation = view.findViewById(R.id.sbSaturation);
        tvBrightness = view.findViewById(R.id.tvBrightness);
        sbBrightness = view.findViewById(R.id.sbBrightness);
        tvThreshold = view.findViewById(R.id.tvThreshold);
        sbThreshold = view.findViewById(R.id.sbThreshold);

        spNameEmail = view.findViewById(R.id.spNameEmail);
        swPreferContact = view.findViewById(R.id.swPreferContact);
        swOnlyContact = view.findViewById(R.id.swOnlyContact);
        swDistinguishContacts = view.findViewById(R.id.swDistinguishContacts);
        swShowRecipients = view.findViewById(R.id.swShowRecipients);
        spFontSizeSender = view.findViewById(R.id.spFontSizeSender);
        spSenderEllipsize = view.findViewById(R.id.spSenderEllipsize);

        swSubjectTop = view.findViewById(R.id.swSubjectTop);
        swSubjectItalic = view.findViewById(R.id.swSubjectItalic);
        swHighlightSubject = view.findViewById(R.id.swHighlightSubject);
        spFontSizeSubject = view.findViewById(R.id.spFontSizeSubject);
        spSubjectEllipsize = view.findViewById(R.id.spSubjectEllipsize);

        swKeywords = view.findViewById(R.id.swKeywords);
        swLabels = view.findViewById(R.id.swLabels);
        swFlags = view.findViewById(R.id.swFlags);
        swFlagsBackground = view.findViewById(R.id.swFlagsBackground);
        swPreview = view.findViewById(R.id.swPreview);
        swPreviewItalic = view.findViewById(R.id.swPreviewItalic);
        spPreviewLines = view.findViewById(R.id.spPreviewLines);
        tvPreviewLinesHint = view.findViewById(R.id.tvPreviewLinesHint);
        swAddresses = view.findViewById(R.id.swAddresses);
        tvMessageZoom = view.findViewById(R.id.tvMessageZoom);
        sbMessageZoom = view.findViewById(R.id.sbMessageZoom);
        swOverviewMode = view.findViewById(R.id.swOverviewMode);
        swOverrideWidth = view.findViewById(R.id.swOverrideWidth);
        swContrast = view.findViewById(R.id.swContrast);
        swMonospaced = view.findViewById(R.id.swMonospaced);
        swMonospacedPre = view.findViewById(R.id.swMonospacedPre);
        swBackgroundColor = view.findViewById(R.id.swBackgroundColor);
        swTextColor = view.findViewById(R.id.swTextColor);
        swTextSize = view.findViewById(R.id.swTextSize);
        swTextFont = view.findViewById(R.id.swTextFont);
        swTextAlign = view.findViewById(R.id.swTextAlign);
        swTextSeparators = view.findViewById(R.id.swTextSeparators);
        swCollapseQuotes = view.findViewById(R.id.swCollapseQuotes);
        swImagesPlaceholders = view.findViewById(R.id.swImagesPlaceholders);
        swImagesInline = view.findViewById(R.id.swImagesInline);
        swButtonExtra = view.findViewById(R.id.swButtonExtra);
        swAttachmentsAlt = view.findViewById(R.id.swAttachmentsAlt);
        swThumbnails = view.findViewById(R.id.swThumbnails);
        swParseClasses = view.findViewById(R.id.swParseClasses);
        swAuthentication = view.findViewById(R.id.swAuthentication);
        swAuthenticationIndicator = view.findViewById(R.id.swAuthenticationIndicator);

        grpGravatars = view.findViewById(R.id.grpGravatars);

        setOptions();

        // Wire controls

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        btnTheme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bundle args = new Bundle();
                args.putBoolean("settings", true);
                FragmentDialogTheme dialog = new FragmentDialogTheme();
                dialog.setArguments(args);
                dialog.show(getParentFragmentManager(), "setup:theme");
            }
        });

        spStartup.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                String[] values = getResources().getStringArray(R.array.startupValues);
                prefs.edit().putString("startup", values[position]).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                prefs.edit().remove("startup").apply();
            }
        });

        swCards.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("cards", checked).apply();
                swBeige.setEnabled(checked);
                swTabularBackground.setEnabled(!checked);
                swShadow.setEnabled(checked);
                swShadowHighlight.setEnabled(swShadow.isEnabled() && checked);
                swIndentation.setEnabled(checked);
            }
        });

        swBeige.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                getActivity().getIntent().putExtra("tab", "display");
                prefs.edit().putBoolean("beige", checked).apply();
            }
        });

        swTabularBackground.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("tabular_card_bg", checked).apply();
            }
        });

        swShadow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("shadow_unread", checked).apply();
                swShadowHighlight.setEnabled(swShadow.isEnabled() && checked);
            }
        });

        swShadowHighlight.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("shadow_highlight", checked).apply();
            }
        });

        swDate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("date", checked).apply();
                swDateFixed.setEnabled(!checked);
                swDateBold.setEnabled(checked || swDateFixed.isChecked());
            }
        });

        swDateFixed.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("date_fixed", checked).apply();
                swDateBold.setEnabled(swDate.isChecked() || checked);
            }
        });

        swDateBold.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("date_bold", checked).apply();
            }
        });

        swPortrait2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("portrait2", checked).apply();
                if (checked)
                    prefs.edit().putBoolean("portrait2c", false).apply();
            }
        });

        swPortrait2c.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("portrait2c", checked).apply();
                if (checked)
                    prefs.edit().putBoolean("portrait2", false).apply();
            }
        });

        swLandscape.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("landscape", checked).apply();
            }
        });

        swNavOptions.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("nav_options", checked).apply();
            }
        });

        swNavMessageCount.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("nav_count", checked).apply();
            }
        });

        swNavUnseenDrafts.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("nav_unseen_drafts", checked).apply();
            }
        });

        swNavBarColorize.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("navbar_colorize", checked).apply();
                setNavigationBarColor(
                        checked ? Helper.resolveColor(getContext(), R.attr.colorPrimaryDark) : Color.BLACK);
            }
        });

        swThreading.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("threading", checked).apply();
                swThreadingUnread.setEnabled(checked);
                WidgetUnified.updateData(getContext());
            }
        });

        swThreadingUnread.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("threading_unread", checked).apply();
            }
        });

        swIndentation.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("indentation", checked).apply();
            }
        });

        swSeekbar.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("seekbar", checked).apply();
            }
        });

        swActionbar.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("actionbar", checked).apply();
                swActionbarColor.setEnabled(checked);
            }
        });

        swActionbarColor.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("actionbar_color", checked).apply();
            }
        });

        swHighlightUnread.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("highlight_unread", checked).apply();
            }
        });

        btnHighlightColor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Context context = getContext();
                int editTextColor = Helper.resolveColor(context, android.R.attr.editTextColor);
                int highlightColor = prefs.getInt("highlight_color", Helper.resolveColor(context, R.attr.colorAccent));

                ColorPickerDialogBuilder builder = ColorPickerDialogBuilder
                        .with(context)
                        .setTitle(R.string.title_advanced_highlight_color)
                        .initialColor(highlightColor)
                        .showColorEdit(true)
                        .setColorEditTextColor(editTextColor)
                        .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                        .density(6)
                        .lightnessSliderOnly()
                        .setPositiveButton(android.R.string.ok, new ColorPickerClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int selectedColor, Integer[] allColors) {
                                prefs.edit().putInt("highlight_color", selectedColor).apply();
                                btnHighlightColor.setColor(selectedColor);
                            }
                        })
                        .setNegativeButton(R.string.title_reset, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                prefs.edit().remove("highlight_color").apply();
                                btnHighlightColor.setColor(Helper.resolveColor(context, R.attr.colorAccent));
                            }
                        });

                builder.build().show();
            }
        });

        swColorStripe.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("color_stripe", checked).apply();
                //swColorStripeWide.setEnabled(checked);
                WidgetUnified.updateData(getContext());
            }
        });

        swColorStripeWide.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("color_stripe_wide", checked).apply();
                WidgetUnified.updateData(getContext());
            }
        });

        swAvatars.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("avatars", checked).apply();
                ContactInfo.clearCache(getContext());
            }
        });

        swBimi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("bimi", checked).apply();
                ContactInfo.clearCache(getContext());
            }
        });

        tvBimiHint.getPaint().setUnderlineText(true);
        tvBimiHint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.view(v.getContext(), Uri.parse(Helper.BIMI_PRIVACY_URI), true);
            }
        });

        ibBimi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.view(v.getContext(), Uri.parse("https://bimigroup.org/"), true);
            }
        });

        swGravatars.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("gravatars", checked).apply();
                ContactInfo.clearCache(getContext());
            }
        });

        tvGravatarsHint.getPaint().setUnderlineText(true);
        tvGravatarsHint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.view(v.getContext(), Uri.parse(Helper.GRAVATAR_PRIVACY_URI), true);
            }
        });

        swFavicons.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("favicons", checked).apply();
                ContactInfo.clearCache(getContext());
            }
        });

        tvFaviconsHint.getPaint().setUnderlineText(true);
        tvFaviconsHint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.view(v.getContext(), Uri.parse(Helper.FAVICON_PRIVACY_URI), true);
            }
        });

        swGeneratedIcons.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("generated_icons", checked).apply();
                swIdenticons.setEnabled(checked);
                sbSaturation.setEnabled(checked);
                sbBrightness.setEnabled(checked);
                sbThreshold.setEnabled(checked);
                ContactInfo.clearCache(getContext());
            }
        });

        swIdenticons.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("identicons", checked).apply();
                ContactInfo.clearCache(getContext());
            }
        });

        swCircular.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("circular", checked).apply();
                updateColor();
                ContactInfo.clearCache(getContext());
            }
        });

        sbSaturation.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("saturation", progress).apply();
                updateColor();
                ContactInfo.clearCache(getContext());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }
        });

        sbBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("brightness", progress).apply();
                updateColor();
                ContactInfo.clearCache(getContext());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }
        });

        sbThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("threshold", progress).apply();
                updateColor();
                ContactInfo.clearCache(getContext());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }
        });

        spNameEmail.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                prefs.edit().putInt("email_format", position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                prefs.edit().remove("email_format").apply();
            }
        });

        swPreferContact.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("prefer_contact", checked).apply();
            }
        });

        swOnlyContact.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("only_contact", checked).apply();
            }
        });

        swDistinguishContacts.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("distinguish_contacts", checked).apply();
            }
        });

        swShowRecipients.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("show_recipients", checked).apply();
            }
        });

        spFontSizeSender.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                int[] values = getResources().getIntArray(R.array.fontSizeValues);
                prefs.edit().putInt("font_size_sender", values[position]).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                prefs.edit().remove("font_size_sender").apply();
            }
        });

        spSenderEllipsize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                String[] values = getResources().getStringArray(R.array.ellipsizeValues);
                prefs.edit().putString("sender_ellipsize", values[position]).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                prefs.edit().remove("sender_ellipsize").apply();
            }
        });

        swSubjectTop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("subject_top", checked).apply();
                WidgetUnified.updateData(getContext());
            }
        });

        swSubjectItalic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("subject_italic", checked).apply();
                WidgetUnified.updateData(getContext());
            }
        });

        swHighlightSubject.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("highlight_subject", checked).apply();
            }
        });

        spFontSizeSubject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                int[] values = getResources().getIntArray(R.array.fontSizeValues);
                prefs.edit().putInt("font_size_subject", values[position]).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                prefs.edit().remove("font_size_subject").apply();
            }
        });

        spSubjectEllipsize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                String[] values = getResources().getStringArray(R.array.ellipsizeValues);
                prefs.edit().putString("subject_ellipsize", values[position]).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                prefs.edit().remove("subject_ellipsize").apply();
            }
        });

        swKeywords.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("keywords_header", checked).apply();
            }
        });

        swLabels.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("labels_header", checked).apply();
            }
        });

        swFlags.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("flags", checked).apply();
            }
        });

        swFlagsBackground.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("flags_background", checked).apply();
            }
        });

        swPreview.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("preview", checked).apply();
                swPreviewItalic.setEnabled(checked);
                spPreviewLines.setEnabled(checked);
            }
        });

        swPreviewItalic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("preview_italic", checked).apply();
            }
        });

        tvPreviewLinesHint.setText(getString(R.string.title_advanced_preview_lines_hint, NF.format(HtmlHelper.PREVIEW_SIZE)));

        spPreviewLines.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                prefs.edit().putInt("preview_lines", position + 1).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                prefs.edit().remove("preview_lines").apply();
            }
        });

        swAddresses.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("addresses", checked).apply();
            }
        });

        sbMessageZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int message_zoom = progress + 50;
                if (message_zoom == 100)
                    prefs.edit().remove("message_zoom").apply();
                else
                    prefs.edit().putInt("message_zoom", message_zoom).apply();
                tvMessageZoom.setText(getString(R.string.title_advanced_message_text_zoom2, NF.format(message_zoom)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }
        });

        swOverviewMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("overview_mode", checked).apply();
            }
        });

        swOverrideWidth.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("override_width", checked).apply();
            }
        });

        swContrast.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("contrast", checked).apply();
            }
        });

        swMonospaced.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("monospaced", checked).apply();
            }
        });

        swMonospacedPre.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("monospaced_pre", checked).apply();
            }
        });

        String theme = prefs.getString("theme", "blue_orange_system");
        boolean bw = "black_and_white".equals(theme);

        swBackgroundColor.setEnabled(!bw);
        swBackgroundColor.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("background_color", checked).apply();
            }
        });

        swTextColor.setEnabled(!bw);
        swTextColor.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("text_color", checked).apply();
            }
        });

        swTextSize.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("text_size", checked).apply();
            }
        });

        swTextFont.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("text_font", checked).apply();
            }
        });

        swTextAlign.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("text_align", checked).apply();
            }
        });

        swTextSeparators.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("text_separators", checked).apply();
            }
        });

        swCollapseQuotes.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("collapse_quotes", checked).apply();
            }
        });

        swImagesPlaceholders.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("image_placeholders", checked).apply();
            }
        });

        swImagesInline.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("inline_images", checked).apply();
            }
        });

        swButtonExtra.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("button_extra", checked).apply();
            }
        });

        swAttachmentsAlt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("attachments_alt", checked).apply();
            }
        });

        swThumbnails.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("thumbnails", checked).apply();
            }
        });

        swParseClasses.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("parse_classes", checked).apply();
            }
        });

        swAuthentication.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("authentication", checked).apply();
                swAuthenticationIndicator.setEnabled(checked);
            }
        });

        swAuthenticationIndicator.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("authentication_indicator", checked).apply();
            }
        });

        // Initialize
        FragmentDialogTheme.setBackground(getContext(), view, false);
        grpGravatars.setVisibility(ContactInfo.canGravatars() ? View.VISIBLE : View.GONE);
        tvBimiUnverified.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);

        PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);

        return view;
    }

    @Override
    public void onDestroyView() {
        PreferenceManager.getDefaultSharedPreferences(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroyView();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if ("message_zoom".equals(key))
            return;

        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
            setOptions();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_options, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_default) {
            FragmentOptions.reset(getContext(), RESET_OPTIONS, new Runnable() {
                @Override
                public void run() {
                    setNavigationBarColor(Color.BLACK);
                }
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setOptions() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        String startup = prefs.getString("startup", "unified");
        String[] startupValues = getResources().getStringArray(R.array.startupValues);
        for (int pos = 0; pos < startupValues.length; pos++)
            if (startupValues[pos].equals(startup)) {
                spStartup.setSelection(pos);
                break;
            }

        swCards.setChecked(prefs.getBoolean("cards", true));
        swBeige.setChecked(prefs.getBoolean("beige", true));
        swTabularBackground.setChecked(prefs.getBoolean("tabular_card_bg", false));
        swShadow.setChecked(prefs.getBoolean("shadow_unread", false));
        swShadowHighlight.setChecked(prefs.getBoolean("shadow_highlight", false));
        swBeige.setEnabled(swCards.isChecked());
        swTabularBackground.setEnabled(!swCards.isChecked());
        swShadow.setEnabled(swCards.isChecked());
        swShadowHighlight.setEnabled(swShadow.isEnabled() && swShadow.isChecked());
        swDate.setChecked(prefs.getBoolean("date", true));
        swDateFixed.setChecked(prefs.getBoolean("date_fixed", false));
        swDateFixed.setEnabled(!swDate.isChecked());
        swDateBold.setChecked(prefs.getBoolean("date_bold", false));
        swDateBold.setEnabled(swDate.isChecked() || swDateFixed.isChecked());
        swPortrait2.setChecked(prefs.getBoolean("portrait2", false));
        swPortrait2c.setChecked(prefs.getBoolean("portrait2c", false) && !swPortrait2.isChecked());
        swLandscape.setChecked(prefs.getBoolean("landscape", true));
        swNavOptions.setChecked(prefs.getBoolean("nav_options", true));
        swNavMessageCount.setChecked(prefs.getBoolean("nav_count", false));
        swNavUnseenDrafts.setChecked(prefs.getBoolean("nav_unseen_drafts", false));
        swNavBarColorize.setChecked(prefs.getBoolean("navbar_colorize", false));

        swThreading.setChecked(prefs.getBoolean("threading", true));
        swThreadingUnread.setChecked(prefs.getBoolean("threading_unread", false));
        swThreadingUnread.setEnabled(swThreading.isChecked());
        swIndentation.setChecked(prefs.getBoolean("indentation", false));
        swIndentation.setEnabled(swCards.isChecked() && swThreading.isChecked());
        swSeekbar.setChecked(prefs.getBoolean("seekbar", false));
        swActionbar.setChecked(prefs.getBoolean("actionbar", true));
        swActionbarColor.setChecked(prefs.getBoolean("actionbar_color", false));
        swActionbarColor.setEnabled(swActionbar.isChecked());

        swHighlightUnread.setChecked(prefs.getBoolean("highlight_unread", true));

        btnHighlightColor.setColor(prefs.getInt("highlight_color",
                Helper.resolveColor(getContext(), R.attr.colorUnreadHighlight)));

        swColorStripe.setChecked(prefs.getBoolean("color_stripe", true));
        swColorStripeWide.setChecked(prefs.getBoolean("color_stripe_wide", false));
        //swColorStripeWide.setEnabled(swColorStripe.isChecked());
        swAvatars.setChecked(prefs.getBoolean("avatars", true));
        swBimi.setChecked(prefs.getBoolean("bimi", false));
        swGravatars.setChecked(prefs.getBoolean("gravatars", false));
        swFavicons.setChecked(prefs.getBoolean("favicons", false));
        swGeneratedIcons.setChecked(prefs.getBoolean("generated_icons", true));
        swIdenticons.setChecked(prefs.getBoolean("identicons", false));
        swIdenticons.setEnabled(swGeneratedIcons.isChecked());
        swCircular.setChecked(prefs.getBoolean("circular", true));

        int saturation = prefs.getInt("saturation", 100);
        tvSaturation.setText(getString(R.string.title_advanced_color_saturation, NF.format(saturation)));
        sbSaturation.setProgress(saturation);
        sbSaturation.setEnabled(swGeneratedIcons.isChecked());

        int brightness = prefs.getInt("brightness", 100);
        tvBrightness.setText(getString(R.string.title_advanced_color_value, NF.format(brightness)));
        sbBrightness.setProgress(brightness);
        sbBrightness.setEnabled(swGeneratedIcons.isChecked());

        int threshold = prefs.getInt("threshold", 50);
        tvThreshold.setText(getString(R.string.title_advanced_color_threshold, NF.format(threshold)));
        sbThreshold.setProgress(threshold);
        sbThreshold.setEnabled(swGeneratedIcons.isChecked());

        MessageHelper.AddressFormat email_format = MessageHelper.getAddressFormat(getContext());
        spNameEmail.setSelection(email_format.ordinal());
        swPreferContact.setChecked(prefs.getBoolean("prefer_contact", false));
        swOnlyContact.setChecked(prefs.getBoolean("only_contact", false));
        swDistinguishContacts.setChecked(prefs.getBoolean("distinguish_contacts", false));
        swShowRecipients.setChecked(prefs.getBoolean("show_recipients", false));

        swSubjectTop.setChecked(prefs.getBoolean("subject_top", false));
        swSubjectItalic.setChecked(prefs.getBoolean("subject_italic", true));
        swHighlightSubject.setChecked(prefs.getBoolean("highlight_subject", false));

        int[] fontSizeValues = getResources().getIntArray(R.array.fontSizeValues);
        String[] ellipsizeValues = getResources().getStringArray(R.array.ellipsizeValues);

        int font_size_sender = prefs.getInt("font_size_sender", -1);
        for (int pos = 0; pos < fontSizeValues.length; pos++)
            if (fontSizeValues[pos] == font_size_sender) {
                spFontSizeSender.setSelection(pos);
                break;
            }

        int font_size_subject = prefs.getInt("font_size_subject", -1);
        for (int pos = 0; pos < fontSizeValues.length; pos++)
            if (fontSizeValues[pos] == font_size_subject) {
                spFontSizeSubject.setSelection(pos);
                break;
            }

        String sender_ellipsize = prefs.getString("sender_ellipsize", "end");
        for (int pos = 0; pos < startupValues.length; pos++)
            if (ellipsizeValues[pos].equals(sender_ellipsize)) {
                spSenderEllipsize.setSelection(pos);
                break;
            }

        String subject_ellipsize = prefs.getString("subject_ellipsize", "full");
        for (int pos = 0; pos < startupValues.length; pos++)
            if (ellipsizeValues[pos].equals(subject_ellipsize)) {
                spSubjectEllipsize.setSelection(pos);
                break;
            }

        swKeywords.setChecked(prefs.getBoolean("keywords_header", false));
        swLabels.setChecked(prefs.getBoolean("labels_header", true));
        swFlags.setChecked(prefs.getBoolean("flags", true));
        swFlagsBackground.setChecked(prefs.getBoolean("flags_background", false));
        swPreview.setChecked(prefs.getBoolean("preview", false));
        swPreviewItalic.setChecked(prefs.getBoolean("preview_italic", true));
        swPreviewItalic.setEnabled(swPreview.isChecked());
        spPreviewLines.setSelection(prefs.getInt("preview_lines", 2) - 1);
        spPreviewLines.setEnabled(swPreview.isChecked());

        swAddresses.setChecked(prefs.getBoolean("addresses", false));

        int message_zoom = prefs.getInt("message_zoom", 100);
        tvMessageZoom.setText(getString(R.string.title_advanced_message_text_zoom2, NF.format(message_zoom)));
        if (message_zoom >= 50 && message_zoom <= 250)
            sbMessageZoom.setProgress(message_zoom - 50);

        swOverviewMode.setChecked(prefs.getBoolean("overview_mode", false));
        swOverrideWidth.setChecked(prefs.getBoolean("override_width", false));

        swContrast.setChecked(prefs.getBoolean("contrast", false));
        swMonospaced.setChecked(prefs.getBoolean("monospaced", false));
        swMonospacedPre.setChecked(prefs.getBoolean("monospaced_pre", false));
        swBackgroundColor.setChecked(prefs.getBoolean("background_color", false));
        swTextColor.setChecked(prefs.getBoolean("text_color", true));
        swTextSize.setChecked(prefs.getBoolean("text_size", true));
        swTextFont.setChecked(prefs.getBoolean("text_font", true));
        swTextAlign.setChecked(prefs.getBoolean("text_align", true));
        swTextSeparators.setChecked(prefs.getBoolean("text_separators", true));
        swCollapseQuotes.setChecked(prefs.getBoolean("collapse_quotes", false));
        swImagesPlaceholders.setChecked(prefs.getBoolean("image_placeholders", true));
        swImagesInline.setChecked(prefs.getBoolean("inline_images", false));
        swButtonExtra.setChecked(prefs.getBoolean("button_extra", false));
        swAttachmentsAlt.setChecked(prefs.getBoolean("attachments_alt", false));
        swThumbnails.setChecked(prefs.getBoolean("thumbnails", true));

        swParseClasses.setChecked(prefs.getBoolean("parse_classes", true));
        swAuthentication.setChecked(prefs.getBoolean("authentication", true));
        swAuthenticationIndicator.setChecked(prefs.getBoolean("authentication_indicator", false));
        swAuthenticationIndicator.setEnabled(swAuthentication.isChecked());

        updateColor();
    }

    private void setNavigationBarColor(int color) {
        FragmentActivity activity = getActivity();
        if (activity == null)
            return;
        Window window = activity.getWindow();
        if (window == null)
            return;
        window.setNavigationBarColor(color);
    }

    private void updateColor() {
        Context context = getContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean identicons = prefs.getBoolean("identicons", false);
        boolean circular = prefs.getBoolean("circular", true);

        int size = Helper.dp2pixels(context, 36);
        byte[] ahash = ImageHelper.getHash("abc@example.com");
        byte[] bhash = ImageHelper.getHash("bcd@example.com");
        byte[] chash = ImageHelper.getHash("cde@example.com");
        Integer radius = (circular && !identicons ? null : Helper.dp2pixels(context, 3));

        Bitmap red = (identicons
                ? ImageHelper.generateIdenticon(ahash, 0f, size, 5, context)
                : ImageHelper.generateLetterIcon("A", 0f, size, context));

        Bitmap green = (identicons
                ? ImageHelper.generateIdenticon(bhash, 120f, size, 5, context)
                : ImageHelper.generateLetterIcon("B", 120f, size, context));

        Bitmap blue = (identicons
                ? ImageHelper.generateIdenticon(chash, 240f, size, 5, context)
                : ImageHelper.generateLetterIcon("C", 240f, size, context));

        ivRed.setImageBitmap(ImageHelper.makeCircular(red, radius));
        ivGreen.setImageBitmap(ImageHelper.makeCircular(green, radius));
        ivBlue.setImageBitmap(ImageHelper.makeCircular(blue, radius));
    }
}
