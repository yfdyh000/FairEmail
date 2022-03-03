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

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static androidx.webkit.WebSettingsCompat.FORCE_DARK_OFF;
import static androidx.webkit.WebSettingsCompat.FORCE_DARK_ON;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.nio.charset.StandardCharsets;

public class FragmentDialogOpenFull extends FragmentDialogBase {
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.fullScreenDialog);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null)
            dialog.getWindow().setLayout(MATCH_PARENT, MATCH_PARENT);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Bundle args = getArguments();
        String html = args.getString("html");
        boolean overview_mode = args.getBoolean("overview_mode");
        boolean safe_browsing = args.getBoolean("safe_browsing");
        boolean force_light = args.getBoolean("force_light");

        final Context context = getContext();

        View view = inflater.inflate(R.layout.fragment_open_full, container, false);
        WebView wv = view.findViewById(R.id.wv);

        WebSettings settings = wv.getSettings();
        settings.setUserAgentString(WebViewEx.getUserAgent(context, wv));
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(overview_mode);

        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);

        settings.setAllowFileAccess(false);
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        if (WebViewEx.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE))
            WebSettingsCompat.setSafeBrowsingEnabled(settings, safe_browsing);

        boolean dark = (Helper.isDarkTheme(context) && !force_light);
        if (WebViewEx.isFeatureSupported(WebViewFeature.FORCE_DARK))
            WebSettingsCompat.setForceDark(settings, dark ? FORCE_DARK_ON : FORCE_DARK_OFF);

        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkLoads(false);
        settings.setBlockNetworkImage(false);

        wv.loadDataWithBaseURL(null, html, "text/html", StandardCharsets.UTF_8.name(), null);

        return view;
    }
}
