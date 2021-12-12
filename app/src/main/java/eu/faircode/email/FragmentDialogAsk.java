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

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

public class FragmentDialogAsk extends FragmentDialogBase {
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        String question = args.getString("question");
        String remark = args.getString("remark");
        String confirm = args.getString("confirm");
        String notagain = args.getString("notagain");
        String accept = args.getString("accept");
        boolean warning = args.getBoolean("warning");
        int faq = args.getInt("faq");

        final Context context = getContext();
        final int colorError = Helper.resolveColor(context, R.attr.colorError);

        View dview = LayoutInflater.from(context).inflate(R.layout.dialog_ask_again, null);
        TextView tvMessage = dview.findViewById(R.id.tvMessage);
        TextView tvRemark = dview.findViewById(R.id.tvRemark);
        CheckBox cbConfirm = dview.findViewById(R.id.cbConfirm);
        CheckBox cbNotAgain = dview.findViewById(R.id.cbNotAgain);
        TextView tvAccept = dview.findViewById(R.id.tvAccept);
        ImageButton ibInfo = dview.findViewById(R.id.ibInfo);

        tvMessage.setText(question);
        tvRemark.setText(remark);
        tvRemark.setVisibility(remark == null ? View.GONE : View.VISIBLE);
        cbConfirm.setText(confirm);
        cbConfirm.setVisibility(confirm == null ? View.GONE : View.VISIBLE);
        cbNotAgain.setVisibility(notagain == null ? View.GONE : View.VISIBLE);
        tvAccept.setText(accept);
        tvAccept.setVisibility(View.GONE);
        ibInfo.setVisibility(faq == 0 ? View.GONE : View.VISIBLE);

        if (warning) {
            Drawable w = context.getDrawable(R.drawable.twotone_warning_24);
            w.setBounds(0, 0, w.getIntrinsicWidth(), w.getIntrinsicHeight());
            w.setTint(colorError);
            tvMessage.setCompoundDrawablesRelative(w, null, null, null);
            tvMessage.setCompoundDrawablePadding(Helper.dp2pixels(context, 12));
        }

        if (notagain != null)
            cbNotAgain.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (accept == null) {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                        prefs.edit().putBoolean(notagain, isChecked).apply();
                    }
                    tvAccept.setVisibility(isChecked && accept != null ? View.VISIBLE : View.GONE);
                }
            });

        if (faq != 0)
            ibInfo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Helper.viewFAQ(v.getContext(), faq);
                }
            });

        EntityLog.log(context, "Ask " + TextUtils.join(" ", Log.getExtras(args)));

        return new AlertDialog.Builder(context)
                .setView(dview)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean confirmed = (confirm == null || cbConfirm.isChecked());
                        EntityLog.log(context, "Ask confirmed=" + confirmed);
                        if (confirmed) {
                            if (notagain != null && accept != null) {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                                prefs.edit().putBoolean(notagain, cbNotAgain.isChecked()).apply();
                            }
                            sendResult(Activity.RESULT_OK);
                        } else
                            sendResult(Activity.RESULT_CANCELED);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        EntityLog.log(context, "Ask canceled");
                        sendResult(Activity.RESULT_CANCELED);
                    }
                })
                .create();
    }
}
