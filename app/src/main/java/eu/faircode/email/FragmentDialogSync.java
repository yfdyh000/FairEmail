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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.util.Arrays;
import java.util.List;

public class FragmentDialogSync extends FragmentDialogBase {
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        long fid = args.getLong("folder");
        String name = args.getString("name");
        String type = args.getString("type");

        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_sync, null);
        final TextView tvFolder = view.findViewById(R.id.tvFolder);
        final EditText etMonths = view.findViewById(R.id.etMonths);
        final TextView tvRemark = view.findViewById(R.id.tvRemark);

        if (fid < 0) {
            if (TextUtils.isEmpty(type))
                tvFolder.setText(R.string.title_folder_unified);
            else
                tvFolder.setText(EntityFolder.localizeType(getContext(), type));
        } else
            tvFolder.setText(name);

        etMonths.setText(null);

        tvRemark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Helper.viewFAQ(view.getContext(), 39);
            }
        });

        return new AlertDialog.Builder(getContext())
                .setView(view)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String months = etMonths.getText().toString();

                        Bundle args = getArguments();
                        if (TextUtils.isEmpty(months))
                            args.putInt("months", 0);
                        else
                            try {
                                args.putInt("months", Integer.parseInt(months));
                            } catch (NumberFormatException ex) {
                                Log.e(ex);
                                return;
                            }

                        new SimpleTask<Void>() {
                            @Override
                            protected Void onExecute(Context context, Bundle args) {
                                long fid = args.getLong("folder");
                                String type = args.getString("type");
                                int months = args.getInt("months", -1);

                                DB db = DB.getInstance(context);
                                try {
                                    db.beginTransaction();

                                    List<EntityFolder> folders;
                                    if (fid < 0)
                                        folders = db.folder().getFoldersUnified(type, false);
                                    else {
                                        EntityFolder folder = db.folder().getFolder(fid);
                                        if (folder == null)
                                            return null;
                                        folders = Arrays.asList(folder);
                                    }

                                    for (EntityFolder folder : folders)
                                        if (folder.selectable) {
                                            if (months == 0) {
                                                db.folder().setFolderInitialize(folder.id, Integer.MAX_VALUE);
                                                db.folder().setFolderKeep(folder.id, Integer.MAX_VALUE);
                                            } else if (months > 0) {
                                                db.folder().setFolderInitialize(folder.id, months * 30);
                                                db.folder().setFolderKeep(folder.id, Math.max(folder.keep_days, months * 30));
                                            }

                                            EntityOperation.sync(context, folder.id, true);
                                        }

                                    db.setTransactionSuccessful();
                                } finally {
                                    db.endTransaction();
                                }

                                ServiceSynchronize.eval(context, "folder:months");

                                return null;
                            }

                            @Override
                            protected void onException(Bundle args, Throwable ex) {
                                Log.unexpectedError(getParentFragmentManager(), ex);
                            }
                        }.execute(getContext(), getViewLifecycleOwner(), args, "folder:months");
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }
}
