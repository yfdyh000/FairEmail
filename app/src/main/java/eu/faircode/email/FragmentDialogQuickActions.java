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

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

public class FragmentDialogQuickActions extends FragmentDialogBase {
    static final int MAX_QUICK_ACTIONS = 5;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final Context context = getContext();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final View dview = LayoutInflater.from(context).inflate(R.layout.dialog_quick_actions, null);
        final TextView tvHint = dview.findViewById(R.id.tvHint);
        final CheckBox cbSeen = dview.findViewById(R.id.cbSeen);
        final CheckBox cbUnseen = dview.findViewById(R.id.cbUnseen);
        final CheckBox cbSnooze = dview.findViewById(R.id.cbSnooze);
        final CheckBox cbHide = dview.findViewById(R.id.cbHide);
        final CheckBox cbFlag = dview.findViewById(R.id.cbFlag);
        final CheckBox cbFlagColor = dview.findViewById(R.id.cbFlagColor);
        final CheckBox cbImportanceLow = dview.findViewById(R.id.cbImportanceLow);
        final CheckBox cbImportanceNormal = dview.findViewById(R.id.cbImportanceNormal);
        final CheckBox cbImportanceHigh = dview.findViewById(R.id.cbImportanceHigh);
        final CheckBox cbInbox = dview.findViewById(R.id.cbInbox);
        final CheckBox cbArchive = dview.findViewById(R.id.cbArchive);
        final CheckBox cbJunk = dview.findViewById(R.id.cbJunk);
        final CheckBox cbTrash = dview.findViewById(R.id.cbTrash);
        final CheckBox cbDelete = dview.findViewById(R.id.cbDelete);
        final CheckBox cbMove = dview.findViewById(R.id.cbMove);
        final CheckBox cbClear = dview.findViewById(R.id.cbClear);

        tvHint.setText(getString(R.string.title_quick_actions_hint, MAX_QUICK_ACTIONS));
        cbSeen.setChecked(prefs.getBoolean("more_seen", true));
        cbUnseen.setChecked(prefs.getBoolean("more_unseen", false));
        cbSnooze.setChecked(prefs.getBoolean("more_snooze", false));
        cbHide.setChecked(prefs.getBoolean("more_hide", false));
        cbFlag.setChecked(prefs.getBoolean("more_flag", false));
        cbFlagColor.setChecked(prefs.getBoolean("more_flag_color", false));
        cbImportanceLow.setChecked(prefs.getBoolean("more_importance_low", false));
        cbImportanceNormal.setChecked(prefs.getBoolean("more_importance_normal", false));
        cbImportanceHigh.setChecked(prefs.getBoolean("more_importance_high", false));
        cbInbox.setChecked(prefs.getBoolean("more_inbox", true));
        cbArchive.setChecked(prefs.getBoolean("more_archive", true));
        cbJunk.setChecked(prefs.getBoolean("more_junk", true));
        cbTrash.setChecked(prefs.getBoolean("more_trash", true));
        cbDelete.setChecked(prefs.getBoolean("more_delete", false));
        cbMove.setChecked(prefs.getBoolean("more_move", true));
        cbClear.setChecked(prefs.getBoolean("more_clear", true));

        return new AlertDialog.Builder(getContext())
                .setView(dview)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("more_seen", cbSeen.isChecked());
                        editor.putBoolean("more_unseen", cbUnseen.isChecked());
                        editor.putBoolean("more_snooze", cbSnooze.isChecked());
                        editor.putBoolean("more_hide", cbHide.isChecked());
                        editor.putBoolean("more_flag", cbFlag.isChecked());
                        editor.putBoolean("more_flag_color", cbFlagColor.isChecked());
                        editor.putBoolean("more_importance_low", cbImportanceLow.isChecked());
                        editor.putBoolean("more_importance_normal", cbImportanceNormal.isChecked());
                        editor.putBoolean("more_importance_high", cbImportanceHigh.isChecked());
                        editor.putBoolean("more_inbox", cbInbox.isChecked());
                        editor.putBoolean("more_archive", cbArchive.isChecked());
                        editor.putBoolean("more_junk", cbJunk.isChecked());
                        editor.putBoolean("more_trash", cbTrash.isChecked());
                        editor.putBoolean("more_delete", cbDelete.isChecked());
                        editor.putBoolean("more_move", cbMove.isChecked());
                        editor.putBoolean("more_clear", cbClear.isChecked());
                        editor.apply();
                        sendResult(Activity.RESULT_OK);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        sendResult(Activity.RESULT_CANCELED);
                    }
                })
                .create();
    }
}
