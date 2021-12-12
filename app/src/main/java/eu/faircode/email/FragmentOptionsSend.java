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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.lifecycle.Lifecycle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FragmentOptionsSend extends FragmentBase implements SharedPreferences.OnSharedPreferenceChangeListener {
    private SwitchCompat swKeyboard;
    private SwitchCompat swKeyboardNoFullscreen;
    private SwitchCompat swSuggestNames;
    private SwitchCompat swSuggestSent;
    private SwitchCompat swSuggestReceived;
    private SwitchCompat swSuggestFrequently;
    private Button btnLocalContacts;
    private SwitchCompat swPrefixOnce;
    private RadioGroup rgRe;
    private RadioGroup rgFwd;
    private SwitchCompat swSendReminders;
    private Spinner spSendDelayed;
    private SwitchCompat swAttachNew;
    private SwitchCompat swReplyAll;
    private SwitchCompat swSendPending;

    private Spinner spComposeFont;
    private SwitchCompat swSeparateReply;
    private SwitchCompat swExtendedReply;
    private SwitchCompat swWriteBelow;
    private SwitchCompat swQuoteReply;
    private SwitchCompat swQuoteLimit;
    private SwitchCompat swResizeReply;
    private Spinner spSignatureLocation;
    private SwitchCompat swSignatureNew;
    private SwitchCompat swSignatureReply;
    private SwitchCompat swSignatureForward;
    private Button btnEditSignature;
    private SwitchCompat swDiscardDelete;
    private SwitchCompat swReplyMove;

    private SwitchCompat swAutoLink;
    private SwitchCompat swPlainOnly;
    private SwitchCompat swFormatFlowed;
    private SwitchCompat swUsenetSignature;
    private SwitchCompat swRemoveSignatures;
    private SwitchCompat swReceipt;
    private Spinner spReceiptType;
    private SwitchCompat swLookupMx;

    private final static String[] RESET_OPTIONS = new String[]{
            "keyboard", "keyboard_no_fullscreen",
            "suggest_names", "suggest_sent", "suggested_received", "suggest_frequently",
            "alt_re", "alt_fwd",
            "send_reminders", "send_delayed",
            "attach_new", "reply_all", "send_pending",
            "compose_font", "prefix_once", "separate_reply", "extended_reply", "write_below", "quote_reply", "quote_limit", "resize_reply",
            "signature_location", "signature_new", "signature_reply", "signature_forward",
            "discard_delete", "reply_move",
            "auto_link", "plain_only", "format_flowed", "usenet_signature", "remove_signatures",
            "receipt_default", "receipt_type", "lookup_mx"
    };

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        setSubtitle(R.string.title_setup);
        setHasOptionsMenu(true);

        View view = inflater.inflate(R.layout.fragment_options_send, container, false);

        // Get controls

        swKeyboard = view.findViewById(R.id.swKeyboard);
        swKeyboardNoFullscreen = view.findViewById(R.id.swKeyboardNoFullscreen);
        swSuggestNames = view.findViewById(R.id.swSuggestNames);
        swSuggestSent = view.findViewById(R.id.swSuggestSent);
        swSuggestReceived = view.findViewById(R.id.swSuggestReceived);
        swSuggestFrequently = view.findViewById(R.id.swSuggestFrequently);
        btnLocalContacts = view.findViewById(R.id.btnLocalContacts);
        swPrefixOnce = view.findViewById(R.id.swPrefixOnce);
        rgRe = view.findViewById(R.id.rgRe);
        rgFwd = view.findViewById(R.id.rgFwd);
        swSendReminders = view.findViewById(R.id.swSendReminders);
        spSendDelayed = view.findViewById(R.id.spSendDelayed);
        swAttachNew = view.findViewById(R.id.swAttachNew);
        swReplyAll = view.findViewById(R.id.swReplyAll);
        swSendPending = view.findViewById(R.id.swSendPending);

        spComposeFont = view.findViewById(R.id.spComposeFont);
        swSeparateReply = view.findViewById(R.id.swSeparateReply);
        swExtendedReply = view.findViewById(R.id.swExtendedReply);
        swWriteBelow = view.findViewById(R.id.swWriteBelow);
        swQuoteReply = view.findViewById(R.id.swQuoteReply);
        swQuoteLimit = view.findViewById(R.id.swQuoteLimit);
        swResizeReply = view.findViewById(R.id.swResizeReply);
        spSignatureLocation = view.findViewById(R.id.spSignatureLocation);
        swSignatureNew = view.findViewById(R.id.swSignatureNew);
        swSignatureReply = view.findViewById(R.id.swSignatureReply);
        swSignatureForward = view.findViewById(R.id.swSignatureForward);
        btnEditSignature = view.findViewById(R.id.btnEditSignature);
        swDiscardDelete = view.findViewById(R.id.swDiscardDelete);
        swReplyMove = view.findViewById(R.id.swReplyMove);

        swAutoLink = view.findViewById(R.id.swAutoLink);
        swPlainOnly = view.findViewById(R.id.swPlainOnly);
        swFormatFlowed = view.findViewById(R.id.swFormatFlowed);
        swUsenetSignature = view.findViewById(R.id.swUsenetSignature);
        swRemoveSignatures = view.findViewById(R.id.swRemoveSignatures);
        swReceipt = view.findViewById(R.id.swReceipt);
        spReceiptType = view.findViewById(R.id.spReceiptType);
        swLookupMx = view.findViewById(R.id.swLookupMx);

        String[] fontNameNames = getResources().getStringArray(R.array.fontNameNames);
        String[] fontNameValues = getResources().getStringArray(R.array.fontNameValues);

        List<CharSequence> fn = new ArrayList<>();
        fn.add("-");
        for (int i = 0; i < fontNameNames.length; i++) {
            SpannableStringBuilder ssb = new SpannableStringBuilderEx(fontNameNames[i]);
            ssb.setSpan(new TypefaceSpan(fontNameValues[i]), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            fn.add(ssb);
        }

        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, fn);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spComposeFont.setAdapter(adapter);

        setOptions();

        // Wire controls

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        swKeyboard.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("keyboard", checked).apply();
            }
        });

        swKeyboardNoFullscreen.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("keyboard_no_fullscreen", checked).apply();
            }
        });

        swSuggestNames.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("suggest_names", checked).apply();
            }
        });

        swSuggestSent.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("suggest_sent", checked).apply();
                swSuggestFrequently.setEnabled(swSuggestSent.isChecked() || swSuggestReceived.isChecked());
            }
        });

        swSuggestReceived.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("suggest_received", checked).apply();
                swSuggestFrequently.setEnabled(swSuggestSent.isChecked() || swSuggestReceived.isChecked());
            }
        });

        swSuggestFrequently.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("suggest_frequently", checked).apply();
            }
        });

        btnLocalContacts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getContext());
                lbm.sendBroadcast(new Intent(ActivitySetup.ACTION_MANAGE_LOCAL_CONTACTS));
            }
        });

        swPrefixOnce.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("prefix_once", checked).apply();
            }
        });

        rgRe.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                prefs.edit().putBoolean("alt_re", checkedId == R.id.rbRe2).apply();
            }
        });

        rgFwd.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                prefs.edit().putBoolean("alt_fwd", checkedId == R.id.rbFwd2).apply();
            }
        });

        swSendReminders.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("send_reminders", checked).apply();
            }
        });

        spSendDelayed.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                int[] values = getResources().getIntArray(R.array.sendDelayedValues);
                prefs.edit().putInt("send_delayed", values[position]).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                prefs.edit().remove("send_delayed").apply();
            }
        });

        swAttachNew.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("attach_new", checked).apply();
            }
        });

        swReplyAll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("reply_all", checked).apply();
            }
        });

        swSendPending.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("send_pending", checked).apply();
            }
        });

        spComposeFont.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                String value = (position == 0 ? "" : fontNameValues[position - 1]);
                boolean monospaced = prefs.getBoolean("monospaced", false);
                if (value.equals(monospaced ? "monospace" : "sans-serif"))
                    prefs.edit().remove("compose_font").apply();
                else
                    prefs.edit().putString("compose_font", value).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                prefs.edit().remove("compose_font").apply();
            }
        });

        swSeparateReply.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("separate_reply", checked).apply();
            }
        });

        swSeparateReply.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("separate_reply", checked).apply();
            }
        });

        swExtendedReply.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("extended_reply", checked).apply();
            }
        });

        swWriteBelow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("write_below", checked).apply();
            }
        });

        swQuoteReply.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("quote_reply", checked).apply();
            }
        });

        swQuoteLimit.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("quote_limit", checked).apply();
            }
        });

        swResizeReply.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("resize_reply", checked).apply();
            }
        });

        spSignatureLocation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                prefs.edit().putInt("signature_location", position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                prefs.edit().remove("signature_location").apply();
            }
        });

        swSignatureNew.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("signature_new", checked).apply();
            }
        });

        swSignatureReply.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("signature_reply", checked).apply();
            }
        });

        swSignatureForward.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("signature_forward", checked).apply();
            }
        });

        btnEditSignature.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(v.getContext());
                lbm.sendBroadcast(new Intent(ActivitySetup.ACTION_VIEW_IDENTITIES));
            }
        });

        swDiscardDelete.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("discard_delete", checked).apply();
            }
        });

        swReplyMove.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("reply_move", checked).apply();
            }
        });

        swAutoLink.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("auto_link", checked).apply();
            }
        });

        swPlainOnly.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("plain_only", checked).apply();
            }
        });

        swFormatFlowed.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("format_flowed", checked).apply();
            }
        });

        swUsenetSignature.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("usenet_signature", checked).apply();
            }
        });

        swRemoveSignatures.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("remove_signatures", checked).apply();
            }
        });

        swReceipt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("receipt_default", checked).apply();
            }
        });

        spReceiptType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                prefs.edit().putInt("receipt_type", position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                prefs.edit().remove("receipt_type").apply();
            }
        });

        swLookupMx.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("lookup_mx", checked).apply();
            }
        });

        // Initialize
        FragmentDialogTheme.setBackground(getContext(), view, false);

        String re1 = getString(R.string.title_subject_reply, "");
        String re2 = getString(R.string.title_subject_reply_alt, "");
        ((RadioButton) view.findViewById(R.id.rbRe1)).setText(re1);
        ((RadioButton) view.findViewById(R.id.rbRe2)).setText(re2);
        boolean re = !Objects.equals(re1, re2);
        for (int i = 0; i < rgRe.getChildCount(); i++)
            rgRe.getChildAt(i).setEnabled(re);

        String fwd1 = getString(R.string.title_subject_forward, "");
        String fwd2 = getString(R.string.title_subject_forward_alt, "");
        ((RadioButton) view.findViewById(R.id.rbFwd1)).setText(fwd1);
        ((RadioButton) view.findViewById(R.id.rbFwd2)).setText(fwd2);
        boolean fwd = !Objects.equals(fwd1, fwd2);
        for (int i = 0; i < rgFwd.getChildCount(); i++)
            rgFwd.getChildAt(i).setEnabled(fwd);

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
            FragmentOptions.reset(getContext(), RESET_OPTIONS, null);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setOptions() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        swKeyboard.setChecked(prefs.getBoolean("keyboard", true));
        swKeyboardNoFullscreen.setChecked(prefs.getBoolean("keyboard_no_fullscreen", false));
        swSuggestNames.setChecked(prefs.getBoolean("suggest_names", true));
        swSuggestSent.setChecked(prefs.getBoolean("suggest_sent", true));
        swSuggestReceived.setChecked(prefs.getBoolean("suggest_received", false));
        swSuggestFrequently.setChecked(prefs.getBoolean("suggest_frequently", false));
        swSuggestFrequently.setEnabled(swSuggestSent.isChecked() || swSuggestReceived.isChecked());

        swPrefixOnce.setChecked(prefs.getBoolean("prefix_once", true));
        rgRe.check(prefs.getBoolean("alt_re", false) ? R.id.rbRe2 : R.id.rbRe1);
        rgFwd.check(prefs.getBoolean("alt_fwd", false) ? R.id.rbFwd2 : R.id.rbFwd1);

        swSendReminders.setChecked(prefs.getBoolean("send_reminders", true));

        int send_delayed = prefs.getInt("send_delayed", 0);
        int[] sendDelayedValues = getResources().getIntArray(R.array.sendDelayedValues);
        for (int pos = 0; pos < sendDelayedValues.length; pos++)
            if (sendDelayedValues[pos] == send_delayed) {
                spSendDelayed.setSelection(pos);
                break;
            }

        swAttachNew.setChecked(prefs.getBoolean("attach_new", true));
        swReplyAll.setChecked(prefs.getBoolean("reply_all", false));
        swSendPending.setChecked(prefs.getBoolean("send_pending", true));

        boolean monospaced = prefs.getBoolean("monospaced", false);
        String compose_font = prefs.getString("compose_font", monospaced ? "monospace" : "sans-serif");
        String[] fontNameValues = getResources().getStringArray(R.array.fontNameValues);
        for (int pos = 0; pos < fontNameValues.length; pos++)
            if (fontNameValues[pos].equals(compose_font)) {
                spComposeFont.setSelection(pos + 1);
                break;
            }

        swSeparateReply.setChecked(prefs.getBoolean("separate_reply", false));
        swExtendedReply.setChecked(prefs.getBoolean("extended_reply", false));
        swWriteBelow.setChecked(prefs.getBoolean("write_below", false));
        swQuoteReply.setChecked(prefs.getBoolean("quote_reply", true));
        swQuoteLimit.setChecked(prefs.getBoolean("quote_limit", true));
        swResizeReply.setChecked(prefs.getBoolean("resize_reply", true));

        int signature_location = prefs.getInt("signature_location", 1);
        spSignatureLocation.setSelection(signature_location);

        swSignatureNew.setChecked(prefs.getBoolean("signature_new", true));
        swSignatureReply.setChecked(prefs.getBoolean("signature_reply", true));
        swSignatureForward.setChecked(prefs.getBoolean("signature_forward", true));
        swDiscardDelete.setChecked(prefs.getBoolean("discard_delete", true));
        swReplyMove.setChecked(prefs.getBoolean("reply_move", false));

        swAutoLink.setChecked(prefs.getBoolean("auto_link", false));
        swPlainOnly.setChecked(prefs.getBoolean("plain_only", false));
        swFormatFlowed.setChecked(prefs.getBoolean("format_flowed", false));
        swUsenetSignature.setChecked(prefs.getBoolean("usenet_signature", false));
        swRemoveSignatures.setChecked(prefs.getBoolean("remove_signatures", false));
        swReceipt.setChecked(prefs.getBoolean("receipt_default", false));

        int receipt_type = prefs.getInt("receipt_type", 2);
        spReceiptType.setSelection(receipt_type);

        swLookupMx.setChecked(prefs.getBoolean("lookup_mx", false));
    }
}
