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

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Group;
import androidx.preference.PreferenceManager;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.builder.ColorPickerClickListener;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ActivityWidgetUnified extends ActivityBase {
    private int appWidgetId;

    private Spinner spAccount;
    private Spinner spFolder;
    private CheckBox cbUnseen;
    private CheckBox cbFlagged;
    private CheckBox cbSemiTransparent;
    private ViewButtonColor btnColor;
    private Spinner spFontSize;
    private Spinner spPadding;
    private CheckBox cbRefresh;
    private CheckBox cbCompose;
    private Button btnSave;
    private ContentLoadingProgressBar pbWait;
    private Group grpReady;

    private ArrayAdapter<EntityAccount> adapterAccount;
    private ArrayAdapter<TupleFolderEx> adapterFolder;
    private ArrayAdapter<String> adapterFontSize;
    private ArrayAdapter<String> adapterPadding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            finish();
            return;
        }

        appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        long account = prefs.getLong("widget." + appWidgetId + ".account", -1L);
        long folder = prefs.getLong("widget." + appWidgetId + ".folder", -1L);
        boolean unseen = prefs.getBoolean("widget." + appWidgetId + ".unseen", false);
        boolean flagged = prefs.getBoolean("widget." + appWidgetId + ".flagged", false);
        boolean semi = prefs.getBoolean("widget." + appWidgetId + ".semi", true);
        int background = prefs.getInt("widget." + appWidgetId + ".background", Color.TRANSPARENT);
        int font = prefs.getInt("widget." + appWidgetId + ".font", 0);
        int padding = prefs.getInt("widget." + appWidgetId + ".padding", 0);
        boolean refresh = prefs.getBoolean("widget." + appWidgetId + ".refresh", false);
        boolean compose = prefs.getBoolean("widget." + appWidgetId + ".compose", false);

        getSupportActionBar().setSubtitle(R.string.title_widget_title_list);
        setContentView(R.layout.activity_widget_unified);

        spAccount = findViewById(R.id.spAccount);
        spFolder = findViewById(R.id.spFolder);
        cbUnseen = findViewById(R.id.cbUnseen);
        cbFlagged = findViewById(R.id.cbFlagged);
        cbSemiTransparent = findViewById(R.id.cbSemiTransparent);
        btnColor = findViewById(R.id.btnColor);
        spFontSize = findViewById(R.id.spFontSize);
        spPadding = findViewById(R.id.spPadding);
        cbRefresh = findViewById(R.id.cbRefresh);
        cbCompose = findViewById(R.id.cbCompose);
        btnSave = findViewById(R.id.btnSave);
        pbWait = findViewById(R.id.pbWait);
        grpReady = findViewById(R.id.grpReady);

        final Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        btnColor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int editTextColor = Helper.resolveColor(ActivityWidgetUnified.this, android.R.attr.editTextColor);

                ColorPickerDialogBuilder
                        .with(ActivityWidgetUnified.this)
                        .setTitle(R.string.title_widget_background)
                        .showColorEdit(true)
                        .setColorEditTextColor(editTextColor)
                        .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                        .density(6)
                        .lightnessSliderOnly()
                        .setPositiveButton(android.R.string.ok, new ColorPickerClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int selectedColor, Integer[] allColors) {
                                btnColor.setColor(selectedColor);
                            }
                        })
                        .setNegativeButton(R.string.title_transparent, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                cbSemiTransparent.setChecked(false);
                                btnColor.setColor(Color.TRANSPARENT);
                            }
                        })
                        .build()
                        .show();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EntityAccount account = (EntityAccount) spAccount.getSelectedItem();
                TupleFolderEx folder = (TupleFolderEx) spFolder.getSelectedItem();

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ActivityWidgetUnified.this);
                SharedPreferences.Editor editor = prefs.edit();

                if (account != null && account.id > 0)
                    if (folder != null && folder.id > 0)
                        editor.putString("widget." + appWidgetId + ".name", folder.getDisplayName(ActivityWidgetUnified.this));
                    else
                        editor.putString("widget." + appWidgetId + ".name", account.name);
                else
                    editor.remove("widget." + appWidgetId + ".name");

                int font = spFontSize.getSelectedItemPosition();
                int padding = spPadding.getSelectedItemPosition();

                editor.putLong("widget." + appWidgetId + ".account", account == null ? -1L : account.id);
                editor.putLong("widget." + appWidgetId + ".folder", folder == null ? -1L : folder.id);
                editor.putString("widget." + appWidgetId + ".type", folder == null ? null : folder.type);
                editor.putBoolean("widget." + appWidgetId + ".unseen", cbUnseen.isChecked());
                editor.putBoolean("widget." + appWidgetId + ".flagged", cbFlagged.isChecked());
                editor.putBoolean("widget." + appWidgetId + ".semi", cbSemiTransparent.isChecked());
                editor.putInt("widget." + appWidgetId + ".background", btnColor.getColor());
                editor.putInt("widget." + appWidgetId + ".font", tinyOut(font));
                editor.putInt("widget." + appWidgetId + ".padding", tinyOut(padding));
                editor.putBoolean("widget." + appWidgetId + ".refresh", cbRefresh.isChecked());
                editor.putBoolean("widget." + appWidgetId + ".compose", cbCompose.isChecked());
                editor.putInt("widget." + appWidgetId + ".version", BuildConfig.VERSION_CODE);

                editor.apply();

                WidgetUnified.init(ActivityWidgetUnified.this, appWidgetId);

                setResult(RESULT_OK, resultValue);
                finish();
            }
        });

        adapterAccount = new ArrayAdapter<>(this, R.layout.spinner_item1, android.R.id.text1, new ArrayList<EntityAccount>());
        adapterAccount.setDropDownViewResource(R.layout.spinner_item1_dropdown);
        spAccount.setAdapter(adapterAccount);

        adapterFolder = new ArrayAdapter<TupleFolderEx>(this, R.layout.spinner_item1, android.R.id.text1, new ArrayList<TupleFolderEx>()) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                return localize(position, super.getView(position, convertView, parent));
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                return localize(position, super.getDropDownView(position, convertView, parent));
            }

            private View localize(int position, View view) {
                TupleFolderEx folder = getItem(position);
                if (folder != null) {
                    TextView tv = view.findViewById(android.R.id.text1);
                    tv.setText(EntityFolder.localizeName(view.getContext(), folder.name));
                }
                return view;
            }
        };
        adapterFolder.setDropDownViewResource(R.layout.spinner_item1_dropdown);
        spFolder.setAdapter(adapterFolder);

        spAccount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                EntityAccount account = (EntityAccount) spAccount.getAdapter().getItem(position);
                setFolders(account.id);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                setFolders(-1);
            }

            private void setFolders(long account) {
                Bundle args = new Bundle();
                args.putLong("account", account);

                new SimpleTask<List<TupleFolderEx>>() {
                    @Override
                    protected List<TupleFolderEx> onExecute(Context context, Bundle args) {
                        long account = args.getLong("account");

                        DB db = DB.getInstance(context);
                        List<TupleFolderEx> folders = db.folder().getFoldersEx(account);
                        if (folders != null && folders.size() > 0)
                            Collections.sort(folders, folders.get(0).getComparator(context));
                        return folders;
                    }

                    @Override
                    protected void onExecuted(Bundle args, List<TupleFolderEx> folders) {
                        if (folders == null)
                            folders = new ArrayList<>();

                        TupleFolderEx unified = new TupleFolderEx();
                        unified.id = -1L;
                        unified.name = getString(R.string.title_widget_folder_unified);
                        folders.add(0, unified);

                        adapterFolder.clear();
                        adapterFolder.addAll(folders);

                        int select = 0;
                        for (int i = 0; i < folders.size(); i++)
                            if (folders.get(i).id.equals(folder)) {
                                select = i;
                                break;
                            }

                        spFolder.setSelection(select);
                    }

                    @Override
                    protected void onException(Bundle args, Throwable ex) {
                        Log.unexpectedError(getSupportFragmentManager(), ex);
                    }
                }.execute(ActivityWidgetUnified.this, args, "widget:folders");
            }
        });

        List<String> sizes = new ArrayList<>();
        sizes.addAll(Arrays.asList(getResources().getStringArray(R.array.fontSizeNames)));
        sizes.add(1, getString(R.string.title_size_tiny));

        adapterFontSize = new ArrayAdapter<>(this, R.layout.spinner_item1, android.R.id.text1, sizes);
        adapterFontSize.setDropDownViewResource(R.layout.spinner_item1_dropdown);
        spFontSize.setAdapter(adapterFontSize);

        adapterPadding = new ArrayAdapter<>(this, R.layout.spinner_item1, android.R.id.text1, sizes);
        adapterPadding.setDropDownViewResource(R.layout.spinner_item1_dropdown);
        spPadding.setAdapter(adapterPadding);

        // Initialize
        cbUnseen.setChecked(unseen);
        cbFlagged.setChecked(flagged);
        cbSemiTransparent.setChecked(semi);
        btnColor.setColor(background);
        spFontSize.setSelection(tinyIn(font));
        spPadding.setSelection(tinyIn(padding));
        cbRefresh.setChecked(refresh);
        cbCompose.setChecked(compose);

        grpReady.setVisibility(View.GONE);
        pbWait.setVisibility(View.VISIBLE);

        setResult(RESULT_CANCELED, resultValue);

        Bundle args = new Bundle();

        new SimpleTask<List<EntityAccount>>() {
            @Override
            protected List<EntityAccount> onExecute(Context context, Bundle args) {
                DB db = DB.getInstance(context);

                return db.account().getSynchronizingAccounts();
            }

            @Override
            protected void onExecuted(Bundle args, List<EntityAccount> accounts) {
                if (accounts == null)
                    accounts = new ArrayList<>();

                EntityAccount all = new EntityAccount();
                all.id = -1L;
                all.name = getString(R.string.title_widget_account_all);
                all.primary = false;
                accounts.add(0, all);

                adapterAccount.addAll(accounts);

                for (int i = 0; i < accounts.size(); i++)
                    if (accounts.get(i).id.equals(account)) {
                        spAccount.setSelection(i);
                        break;
                    }

                grpReady.setVisibility(View.VISIBLE);
                pbWait.setVisibility(View.GONE);
            }

            @Override
            protected void onException(Bundle args, Throwable ex) {
                Log.unexpectedError(getSupportFragmentManager(), ex);
            }
        }.execute(this, args, "widget:accounts");
    }

    private int tinyOut(int value) {
        if (value == 1) // tiny
            return 4;
        else if (value > 1)
            return value - 1;
        else
            return value;
    }

    private int tinyIn(int value) {
        if (value == 4)
            return 1;
        else if (value >= 1)
            return value + 1;
        else
            return value;
    }
}
