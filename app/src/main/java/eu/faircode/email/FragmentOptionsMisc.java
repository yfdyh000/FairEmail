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
import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.SystemFonts;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.Group;
import androidx.lifecycle.Observer;
import androidx.preference.PreferenceManager;
import androidx.work.WorkManager;

import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;

public class FragmentOptionsMisc extends FragmentBase implements SharedPreferences.OnSharedPreferenceChangeListener {
    private boolean resumed = false;
    private List<Pair<String, String>> languages = new ArrayList<>();

    private View view;
    private ImageButton ibHelp;
    private SwitchCompat swPowerMenu;
    private SwitchCompat swSendSelf;
    private SwitchCompat swExternalSearch;
    private SwitchCompat swSortAnswers;
    private SwitchCompat swExternalAnswer;
    private SwitchCompat swShortcuts;
    private SwitchCompat swFts;
    private SwitchCompat swClassification;
    private TextView tvClassMinProbability;
    private SeekBar sbClassMinProbability;
    private TextView tvClassMinDifference;
    private SeekBar sbClassMinDifference;
    private SwitchCompat swShowFiltered;
    private ImageButton ibClassification;
    private TextView tvFtsIndexed;
    private TextView tvFtsPro;
    private Spinner spLanguage;
    private SwitchCompat swUpdates;
    private TextView tvGithubPrivacy;
    private ImageButton ibChannelUpdated;
    private SwitchCompat swCheckWeekly;
    private SwitchCompat swBeta;
    private TextView tvBitBucketPrivacy;
    private SwitchCompat swChangelog;
    private SwitchCompat swAnnouncements;
    private TextView tvAnnouncementsPrivacy;
    private SwitchCompat swCrashReports;
    private TextView tvUuid;
    private Button btnReset;
    private SwitchCompat swCleanupAttachments;
    private Button btnCleanup;
    private TextView tvLastCleanup;
    private TextView tvSdcard;

    private SwitchCompat swLanguageTool;
    private TextView tvLanguageToolPrivacy;
    private SwitchCompat swLanguageToolAuto;
    private SwitchCompat swLanguageToolPicky;
    private EditText etLanguageTool;
    private EditText etLanguageToolUser;
    private TextInputLayout tilLanguageToolKey;
    private ImageButton ibLanguageTool;
    private SwitchCompat swDeepL;
    private TextView tvDeepLPrivacy;
    private ImageButton ibDeepL;
    private SwitchCompat swVirusTotal;
    private TextView tvVirusTotalPrivacy;
    private TextInputLayout tilVirusTotal;
    private ImageButton ibVirusTotal;
    private SwitchCompat swSend;
    private EditText etSend;
    private ImageButton ibSend;
    private SwitchCompat swOpenAi;
    private TextView tvOpenAiPrivacy;
    private EditText etOpenAi;
    private TextInputLayout tilOpenAi;
    private EditText etOpenAiModel;
    private TextView tvOpenAiTemperature;
    private SeekBar sbOpenAiTemperature;
    private SwitchCompat swOpenAiModeration;
    private ImageButton ibOpenAi;

    private CardView cardAdvanced;
    private SwitchCompat swWatchdog;
    private SwitchCompat swExperiments;
    private TextView tvExperimentsHint;
    private SwitchCompat swMainLog;
    private SwitchCompat swMainLogMem;
    private SwitchCompat swProtocol;
    private SwitchCompat swLogInfo;
    private SwitchCompat swDebug;
    private SwitchCompat swCanary;
    private SwitchCompat swTest1;
    private SwitchCompat swTest2;
    private SwitchCompat swTest3;
    private SwitchCompat swTest4;
    private SwitchCompat swTest5;

    private Button btnRepair;
    private Button btnDaily;
    private TextView tvLastDaily;
    private SwitchCompat swAutostart;
    private SwitchCompat swEmergency;
    private SwitchCompat swWorkManager;
    private SwitchCompat swExternalStorage;
    private TextView tvExternalStorageFolder;
    private SwitchCompat swIntegrity;
    private SwitchCompat swWal;
    private SwitchCompat swCheckpoints;
    private SwitchCompat swAnalyze;
    private SwitchCompat swAutoVacuum;
    private SwitchCompat swSyncExtra;
    private TextView tvSqliteCache;
    private SeekBar sbSqliteCache;
    private TextView tvChunkSize;
    private SeekBar sbChunkSize;
    private TextView tvThreadRange;
    private SeekBar sbThreadRange;
    private ImageButton ibSqliteCache;
    private SwitchCompat swAutoScroll;
    private SwitchCompat swUndoManager;
    private SwitchCompat swBrowserZoom;
    private SwitchCompat swFakeDark;
    private SwitchCompat swShowRecent;
    private SwitchCompat swModSeq;
    private SwitchCompat swPreamble;
    private SwitchCompat swUid;
    private SwitchCompat swExpunge;
    private SwitchCompat swUidExpunge;
    private SwitchCompat swAuthPlain;
    private SwitchCompat swAuthLogin;
    private SwitchCompat swAuthNtlm;
    private SwitchCompat swAuthSasl;
    private SwitchCompat swAuthApop;
    private SwitchCompat swUseTop;
    private SwitchCompat swKeepAlivePoll;
    private SwitchCompat swEmptyPool;
    private SwitchCompat swIdleDone;
    private SwitchCompat swFastFetch;
    private TextView tvMaxBackoff;
    private SeekBar sbMaxBackOff;
    private SwitchCompat swLogarithmicBackoff;
    private SwitchCompat swExactAlarms;
    private SwitchCompat swNativeDkim;
    private SwitchCompat swNativeArc;
    private EditText etNativeArcWhitelist;
    private SwitchCompat swInfra;
    private SwitchCompat swDupMsgId;
    private SwitchCompat swThreadByRef;
    private EditText etKeywords;
    private SwitchCompat swTestIab;
    private Button btnImportProviders;
    private Button btnExportClassifier;
    private TextView tvProcessors;
    private TextView tvMemoryClass;
    private TextView tvMemoryUsage;
    private TextView tvStorageUsage;
    private TextView tvCacheUsage;
    private TextView tvContactInfo;
    private TextView tvSuffixes;
    private TextView tvAndroidId;
    private TextView tvFingerprint;
    private TextView tvCursorWindow;
    private Button btnGC;
    private Button btnCharsets;
    private Button btnFontMap;
    private Button btnFiles;
    private Button btnUris;
    private Button btnAllPermissions;
    private TextView tvPermissions;

    private Group grpVirusTotal;
    private Group grpSend;
    private Group grpOpenAi;
    private Group grpUpdates;
    private Group grpBitbucket;
    private Group grpAnnouncements;
    private Group grpTest;
    private CardView cardDebug;

    private NumberFormat NF = NumberFormat.getNumberInstance();

    private static final int REQUEST_CLASSIFIER = 1;
    private static final long MIN_FILE_SIZE = 1024 * 1024L;

    private final static String[] RESET_OPTIONS = new String[]{
            "sort_answers", "shortcuts", "fts",
            "classification", "class_min_probability", "class_min_difference",
            "show_filtered",
            "language",
            "lt_enabled", "lt_auto", "lt_picky", "lt_uri", "lt_user", "lt_key",
            "deepl_enabled",
            "vt_enabled", "vt_apikey",
            "send_enabled", "send_host", "send_dlimit", "send_tlimit",
            "openai_enabled", "openai_uri", "openai_apikey", "openai_model", "openai_temperature", "openai_moderation",
            "updates", "weekly", "beta", "show_changelog", "announcements",
            "crash_reports", "cleanup_attachments",
            "watchdog", "experiments", "main_log", "main_log_memory", "protocol", "log_level", "debug", "leak_canary",
            "test1", "test2", "test3", "test4", "test5",
            "emergency_file", "work_manager", // "external_storage",
            "sqlite_integrity_check", "wal", "sqlite_checkpoints", "sqlite_analyze", "sqlite_auto_vacuum", "sqlite_sync_extra", "sqlite_cache",
            "chunk_size", "thread_range",
            "autoscroll_editor", "undo_manager",
            "browser_zoom", "fake_dark",
            "show_recent",
            "use_modseq", "preamble", "uid_command", "perform_expunge", "uid_expunge",
            "auth_plain", "auth_login", "auth_ntlm", "auth_sasl", "auth_apop", "use_top",
            "keep_alive_poll", "empty_pool", "idle_done", "fast_fetch",
            "max_backoff_power", "logarithmic_backoff",
            "exact_alarms",
            "native_dkim", "native_arc", "native_arc_whitelist",
            "infra", "dup_msgids", "thread_byref", "global_keywords", "test_iab"
    };

    private final static String[] RESET_QUESTIONS = new String[]{
            "first", "app_support", "notify_archive",
            "message_swipe", "message_select", "message_junk",
            "folder_actions", "folder_sync",
            "crash_reports_asked", "review_asked", "review_later", "why",
            "reply_hint", "html_always_images", "open_full_confirmed", "open_amp_confirmed",
            "ask_images", "ask_html",
            "print_html_confirmed", "print_html_header", "print_html_images",
            "reformatted_hint",
            "selected_folders", "move_1_confirmed", "move_n_confirmed",
            "last_search_senders", "last_search_recipients", "last_search_subject", "last_search_keywords", "last_search_message",
            "identities_asked", "identities_primary_hint",
            "raw_asked", "all_read_asked", "delete_asked",
            "cc_bcc", "inline_image_hint", "compose_reference", "send_dialog",
            "setup_reminder", "setup_advanced",
            "signature_images_hint",
            "gmail_checked",
            "eml_auto_confirm",
            "open_with_pkg", "open_with_tabs",
            "gmail_checked", "outlook_checked",
            "redmi_note",
            "accept_space", "accept_unsupported",
            "junk_hint",
            "last_update_check", "last_announcement_check"
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Locale slocale = Resources.getSystem().getConfiguration().locale;
        for (String tag : getResources().getAssets().getLocales())
            languages.add(new Pair<>(tag, Locale.forLanguageTag(tag).getDisplayName(slocale)));

        Collections.sort(languages, new Comparator<Pair<String, String>>() {
            @Override
            public int compare(Pair<String, String> l1, Pair<String, String> l2) {
                return l1.second.compareTo(l2.second);
            }
        });
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        setSubtitle(R.string.title_setup);
        setHasOptionsMenu(true);

        view = inflater.inflate(R.layout.fragment_options_misc, container, false);

        // Get controls

        ibHelp = view.findViewById(R.id.ibHelp);
        swPowerMenu = view.findViewById(R.id.swPowerMenu);
        swSendSelf = view.findViewById(R.id.swSendSelf);
        swExternalSearch = view.findViewById(R.id.swExternalSearch);
        swSortAnswers = view.findViewById(R.id.swSortAnswers);
        swExternalAnswer = view.findViewById(R.id.swExternalAnswer);
        swShortcuts = view.findViewById(R.id.swShortcuts);
        swFts = view.findViewById(R.id.swFts);
        swClassification = view.findViewById(R.id.swClassification);
        ibClassification = view.findViewById(R.id.ibClassification);
        tvClassMinProbability = view.findViewById(R.id.tvClassMinProbability);
        sbClassMinProbability = view.findViewById(R.id.sbClassMinProbability);
        tvClassMinDifference = view.findViewById(R.id.tvClassMinDifference);
        sbClassMinDifference = view.findViewById(R.id.sbClassMinDifference);
        swShowFiltered = view.findViewById(R.id.swShowFiltered);
        tvFtsIndexed = view.findViewById(R.id.tvFtsIndexed);
        tvFtsPro = view.findViewById(R.id.tvFtsPro);
        spLanguage = view.findViewById(R.id.spLanguage);
        swUpdates = view.findViewById(R.id.swUpdates);
        tvGithubPrivacy = view.findViewById(R.id.tvGithubPrivacy);
        ibChannelUpdated = view.findViewById(R.id.ibChannelUpdated);
        swCheckWeekly = view.findViewById(R.id.swWeekly);
        swBeta = view.findViewById(R.id.swBeta);
        tvBitBucketPrivacy = view.findViewById(R.id.tvBitBucketPrivacy);
        swChangelog = view.findViewById(R.id.swChangelog);
        swAnnouncements = view.findViewById(R.id.swAnnouncements);
        tvAnnouncementsPrivacy = view.findViewById(R.id.tvAnnouncementsPrivacy);
        swCrashReports = view.findViewById(R.id.swCrashReports);
        tvUuid = view.findViewById(R.id.tvUuid);
        btnReset = view.findViewById(R.id.btnReset);
        swCleanupAttachments = view.findViewById(R.id.swCleanupAttachments);
        btnCleanup = view.findViewById(R.id.btnCleanup);
        tvLastCleanup = view.findViewById(R.id.tvLastCleanup);
        tvSdcard = view.findViewById(R.id.tvSdcard);

        swLanguageTool = view.findViewById(R.id.swLanguageTool);
        tvLanguageToolPrivacy = view.findViewById(R.id.tvLanguageToolPrivacy);
        swLanguageToolAuto = view.findViewById(R.id.swLanguageToolAuto);
        swLanguageToolPicky = view.findViewById(R.id.swLanguageToolPicky);
        etLanguageTool = view.findViewById(R.id.etLanguageTool);
        etLanguageToolUser = view.findViewById(R.id.etLanguageToolUser);
        tilLanguageToolKey = view.findViewById(R.id.tilLanguageToolKey);
        ibLanguageTool = view.findViewById(R.id.ibLanguageTool);
        swDeepL = view.findViewById(R.id.swDeepL);
        tvDeepLPrivacy = view.findViewById(R.id.tvDeepLPrivacy);
        ibDeepL = view.findViewById(R.id.ibDeepL);
        swVirusTotal = view.findViewById(R.id.swVirusTotal);
        tvVirusTotalPrivacy = view.findViewById(R.id.tvVirusTotalPrivacy);
        tilVirusTotal = view.findViewById(R.id.tilVirusTotal);
        ibVirusTotal = view.findViewById(R.id.ibVirusTotal);
        swSend = view.findViewById(R.id.swSend);
        etSend = view.findViewById(R.id.etSend);
        ibSend = view.findViewById(R.id.ibSend);
        swOpenAi = view.findViewById(R.id.swOpenAi);
        tvOpenAiPrivacy = view.findViewById(R.id.tvOpenAiPrivacy);
        etOpenAi = view.findViewById(R.id.etOpenAi);
        tilOpenAi = view.findViewById(R.id.tilOpenAi);
        etOpenAiModel = view.findViewById(R.id.etOpenAiModel);
        tvOpenAiTemperature = view.findViewById(R.id.tvOpenAiTemperature);
        sbOpenAiTemperature = view.findViewById(R.id.sbOpenAiTemperature);
        swOpenAiModeration = view.findViewById(R.id.swOpenAiModeration);
        ibOpenAi = view.findViewById(R.id.ibOpenAi);

        cardAdvanced = view.findViewById(R.id.cardAdvanced);
        swWatchdog = view.findViewById(R.id.swWatchdog);
        swExperiments = view.findViewById(R.id.swExperiments);
        tvExperimentsHint = view.findViewById(R.id.tvExperimentsHint);
        swMainLog = view.findViewById(R.id.swMainLog);
        swMainLogMem = view.findViewById(R.id.swMainLogMem);
        swProtocol = view.findViewById(R.id.swProtocol);
        swLogInfo = view.findViewById(R.id.swLogInfo);
        swDebug = view.findViewById(R.id.swDebug);
        swCanary = view.findViewById(R.id.swCanary);
        swTest1 = view.findViewById(R.id.swTest1);
        swTest2 = view.findViewById(R.id.swTest2);
        swTest3 = view.findViewById(R.id.swTest3);
        swTest4 = view.findViewById(R.id.swTest4);
        swTest5 = view.findViewById(R.id.swTest5);

        btnRepair = view.findViewById(R.id.btnRepair);
        btnDaily = view.findViewById(R.id.btnDaily);
        tvLastDaily = view.findViewById(R.id.tvLastDaily);
        swAutostart = view.findViewById(R.id.swAutostart);
        swEmergency = view.findViewById(R.id.swEmergency);
        swWorkManager = view.findViewById(R.id.swWorkManager);
        swExternalStorage = view.findViewById(R.id.swExternalStorage);
        tvExternalStorageFolder = view.findViewById(R.id.tvExternalStorageFolder);
        swIntegrity = view.findViewById(R.id.swIntegrity);
        swWal = view.findViewById(R.id.swWal);
        swCheckpoints = view.findViewById(R.id.swCheckpoints);
        swAnalyze = view.findViewById(R.id.swAnalyze);
        swAutoVacuum = view.findViewById(R.id.swAutoVacuum);
        swSyncExtra = view.findViewById(R.id.swSyncExtra);
        tvSqliteCache = view.findViewById(R.id.tvSqliteCache);
        sbSqliteCache = view.findViewById(R.id.sbSqliteCache);
        ibSqliteCache = view.findViewById(R.id.ibSqliteCache);
        tvChunkSize = view.findViewById(R.id.tvChunkSize);
        sbChunkSize = view.findViewById(R.id.sbChunkSize);
        tvThreadRange = view.findViewById(R.id.tvThreadRange);
        sbThreadRange = view.findViewById(R.id.sbThreadRange);
        swAutoScroll = view.findViewById(R.id.swAutoScroll);
        swUndoManager = view.findViewById(R.id.swUndoManager);
        swBrowserZoom = view.findViewById(R.id.swBrowserZoom);
        swFakeDark = view.findViewById(R.id.swFakeDark);
        swShowRecent = view.findViewById(R.id.swShowRecent);
        swModSeq = view.findViewById(R.id.swModSeq);
        swPreamble = view.findViewById(R.id.swPreamble);
        swUid = view.findViewById(R.id.swUid);
        swExpunge = view.findViewById(R.id.swExpunge);
        swUidExpunge = view.findViewById(R.id.swUidExpunge);
        swAuthPlain = view.findViewById(R.id.swAuthPlain);
        swAuthLogin = view.findViewById(R.id.swAuthLogin);
        swAuthNtlm = view.findViewById(R.id.swAuthNtlm);
        swAuthSasl = view.findViewById(R.id.swAuthSasl);
        swAuthApop = view.findViewById(R.id.swAuthApop);
        swUseTop = view.findViewById(R.id.swUseTop);
        swKeepAlivePoll = view.findViewById(R.id.swKeepAlivePoll);
        swEmptyPool = view.findViewById(R.id.swEmptyPool);
        swIdleDone = view.findViewById(R.id.swIdleDone);
        swFastFetch = view.findViewById(R.id.swFastFetch);
        tvMaxBackoff = view.findViewById(R.id.tvMaxBackoff);
        sbMaxBackOff = view.findViewById(R.id.sbMaxBackOff);
        swLogarithmicBackoff = view.findViewById(R.id.swLogarithmicBackoff);
        swExactAlarms = view.findViewById(R.id.swExactAlarms);
        swNativeDkim = view.findViewById(R.id.swNativeDkim);
        swNativeArc = view.findViewById(R.id.swNativeArc);
        etNativeArcWhitelist = view.findViewById(R.id.etNativeArcWhitelist);
        swInfra = view.findViewById(R.id.swInfra);
        swDupMsgId = view.findViewById(R.id.swDupMsgId);
        swThreadByRef = view.findViewById(R.id.swThreadByRef);
        etKeywords = view.findViewById(R.id.etKeywords);
        swTestIab = view.findViewById(R.id.swTestIab);
        btnImportProviders = view.findViewById(R.id.btnImportProviders);
        btnExportClassifier = view.findViewById(R.id.btnExportClassifier);
        tvProcessors = view.findViewById(R.id.tvProcessors);
        tvMemoryClass = view.findViewById(R.id.tvMemoryClass);
        tvMemoryUsage = view.findViewById(R.id.tvMemoryUsage);
        tvStorageUsage = view.findViewById(R.id.tvStorageUsage);
        tvCacheUsage = view.findViewById(R.id.tvCacheUsage);
        tvContactInfo = view.findViewById(R.id.tvContactInfo);
        tvSuffixes = view.findViewById(R.id.tvSuffixes);
        tvAndroidId = view.findViewById(R.id.tvAndroidId);
        tvFingerprint = view.findViewById(R.id.tvFingerprint);
        tvCursorWindow = view.findViewById(R.id.tvCursorWindow);
        btnGC = view.findViewById(R.id.btnGC);
        btnCharsets = view.findViewById(R.id.btnCharsets);
        btnFontMap = view.findViewById(R.id.btnFontMap);
        btnFiles = view.findViewById(R.id.btnFiles);
        btnUris = view.findViewById(R.id.btnUris);
        btnAllPermissions = view.findViewById(R.id.btnAllPermissions);
        tvPermissions = view.findViewById(R.id.tvPermissions);

        grpVirusTotal = view.findViewById(R.id.grpVirusTotal);
        grpSend = view.findViewById(R.id.grpSend);
        grpOpenAi = view.findViewById(R.id.grpOpenAi);
        grpUpdates = view.findViewById(R.id.grpUpdates);
        grpBitbucket = view.findViewById(R.id.grpBitbucket);
        grpAnnouncements = view.findViewById(R.id.grpAnnouncements);
        grpTest = view.findViewById(R.id.grpTest);
        cardDebug = view.findViewById(R.id.cardDebug);

        setOptions();

        // Wire controls

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        ibHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.view(v.getContext(), Helper.getSupportUri(v.getContext(), "Options:misc"), false);
            }
        });

        swPowerMenu.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    Helper.enableComponent(getContext(), ServicePowerControl.class, checked);
            }
        });

        swSendSelf.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                Helper.enableComponent(getContext(), ActivitySendSelf.class, checked);
            }
        });

        swExternalSearch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                Helper.enableComponent(getContext(), ActivitySearch.class, checked);
            }
        });

        swSortAnswers.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("sort_answers", checked).apply();
            }
        });

        swExternalAnswer.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                Helper.enableComponent(getContext(), ActivityAnswer.class, checked);
            }
        });

        swShortcuts.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("shortcuts", checked).commit(); // apply won't work here
            }
        });

        swFts.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("fts", checked).apply();

                WorkerFts.init(getContext(), true);

                if (!checked) {
                    Bundle args = new Bundle();

                    new SimpleTask<Void>() {
                        @Override
                        protected Void onExecute(Context context, Bundle args) {
                            try {
                                SQLiteDatabase sdb = Fts4DbHelper.getInstance(context);
                                Fts4DbHelper.delete(sdb);
                                Fts4DbHelper.optimize(sdb);
                            } catch (SQLiteDatabaseCorruptException ex) {
                                Log.e(ex);
                                Fts4DbHelper.delete(context);
                            }

                            DB db = DB.getInstance(context);
                            db.message().resetFts();

                            return null;
                        }

                        @Override
                        protected void onException(Bundle args, Throwable ex) {
                            Log.unexpectedError(getParentFragmentManager(), ex);
                        }
                    }.execute(FragmentOptionsMisc.this, args, "fts:reset");
                }
            }
        });

        Helper.linkPro(tvFtsPro);

        swClassification.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            private int count = 0;

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean checked) {
                prefs.edit().putBoolean("classification", checked).apply();
                if (!checked) {
                    count++;
                    if (count >= 3) {
                        count = 0;
                        MessageClassifier.clear(buttonView.getContext());
                        ToastEx.makeText(buttonView.getContext(), R.string.title_reset, Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        ibClassification.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.viewFAQ(v.getContext(), 163);
            }
        });

        sbClassMinProbability.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("class_min_probability", progress).apply();
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

        sbClassMinDifference.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("class_min_difference", progress).apply();
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

        swShowFiltered.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean("show_filtered", isChecked).apply();
            }
        });

        spLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                String current = prefs.getString("language", null);
                String selected = (position == 0 ? null : languages.get(position - 1).first);
                if (Objects.equals(current, selected))
                    return;

                String title = (position == 0
                        ? getString(R.string.title_advanced_language_system)
                        : languages.get(position - 1).second);
                new AlertDialog.Builder(adapterView.getContext())
                        .setIcon(R.drawable.twotone_help_24)
                        .setTitle(title)
                        .setMessage(R.string.title_advanced_english_hint)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // apply won't work here
                                if (selected == null)
                                    prefs.edit().remove("language").commit();
                                else
                                    prefs.edit().putString("language", selected).commit();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Do nothing
                            }
                        })
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                setOptions();
                            }
                        })
                        .show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                prefs.edit().remove("language").commit();
            }
        });

        swUpdates.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("updates", checked).apply();
                swCheckWeekly.setEnabled(checked);
                swBeta.setEnabled(checked);
                if (!checked) {
                    NotificationManager nm =
                            Helper.getSystemService(getContext(), NotificationManager.class);
                    nm.cancel(NotificationHelper.NOTIFICATION_UPDATE);
                }
            }
        });

        tvGithubPrivacy.getPaint().setUnderlineText(true);
        tvGithubPrivacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.view(v.getContext(), Uri.parse(Helper.GITHUB_PRIVACY_URI), true);
            }
        });

        final Intent channelUpdate = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName())
                .putExtra(Settings.EXTRA_CHANNEL_ID, "update");

        ibChannelUpdated.setVisibility(View.GONE);
        ibChannelUpdated.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.getContext().startActivity(channelUpdate);
            }
        });

        swCheckWeekly.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("weekly", checked).apply();
            }
        });

        swBeta.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("beta", checked).apply();
            }
        });

        tvBitBucketPrivacy.getPaint().setUnderlineText(true);
        tvBitBucketPrivacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.view(v.getContext(), Uri.parse(Helper.BITBUCKET_PRIVACY_URI), true);
            }
        });

        swChangelog.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("show_changelog", checked).apply();
            }
        });

        swAnnouncements.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("announcements", checked).apply();
            }
        });

        tvAnnouncementsPrivacy.getPaint().setUnderlineText(true);
        tvAnnouncementsPrivacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.view(v.getContext(), Uri.parse(Helper.GITHUB_PRIVACY_URI), true);
            }
        });

        swCrashReports.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit()
                        .remove("crash_report_count")
                        .putBoolean("crash_reports", checked)
                        .apply();
                Log.setCrashReporting(checked);
            }
        });

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onResetQuestions();
            }
        });

        swCleanupAttachments.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("cleanup_attachments", checked).apply();
            }
        });

        btnCleanup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onCleanup();
            }
        });

        tvSdcard.setPaintFlags(tvSdcard.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvSdcard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.viewFAQ(v.getContext(), 93);
            }
        });

        swLanguageTool.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("lt_enabled", checked).apply();
                swLanguageToolAuto.setEnabled(checked);
                swLanguageToolPicky.setEnabled(checked);
            }
        });

        tvLanguageToolPrivacy.getPaint().setUnderlineText(true);
        tvLanguageToolPrivacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.view(v.getContext(), Uri.parse(Helper.LT_PRIVACY_URI), true);
            }
        });

        swLanguageToolAuto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("lt_auto", checked).apply();
            }
        });

        swLanguageToolPicky.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("lt_picky", checked).apply();
            }
        });

        etLanguageTool.setHint(LanguageTool.LT_URI);
        etLanguageTool.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Do nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                String apikey = s.toString().trim();
                if (TextUtils.isEmpty(apikey))
                    prefs.edit().remove("lt_uri").apply();
                else
                    prefs.edit().putString("lt_uri", apikey).apply();
            }
        });

        etLanguageToolUser.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Do nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                String apikey = s.toString().trim();
                if (TextUtils.isEmpty(apikey))
                    prefs.edit().remove("lt_user").apply();
                else
                    prefs.edit().putString("lt_user", apikey).apply();
            }
        });

        tilLanguageToolKey.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Do nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                String apikey = s.toString().trim();
                if (TextUtils.isEmpty(apikey))
                    prefs.edit().remove("lt_key").apply();
                else
                    prefs.edit().putString("lt_key", apikey).apply();
            }
        });

        ibLanguageTool.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.viewFAQ(v.getContext(), 180);
            }
        });

        tvDeepLPrivacy.getPaint().setUnderlineText(true);
        tvDeepLPrivacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.view(v.getContext(), Uri.parse(DeepL.PRIVACY_URI), true);
            }
        });

        swDeepL.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("deepl_enabled", checked).apply();
            }
        });

        ibDeepL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DeepL.FragmentDialogDeepL fragment = new DeepL.FragmentDialogDeepL();
                fragment.show(getParentFragmentManager(), "deepl:configure");
            }
        });

        swVirusTotal.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("vt_enabled", checked).apply();
            }
        });

        tvVirusTotalPrivacy.getPaint().setUnderlineText(true);
        tvVirusTotalPrivacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.view(v.getContext(), Uri.parse(VirusTotal.URI_PRIVACY), true);
            }
        });

        tilVirusTotal.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Do nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                String apikey = s.toString().trim();
                if (TextUtils.isEmpty(apikey))
                    prefs.edit().remove("vt_apikey").apply();
                else
                    prefs.edit().putString("vt_apikey", apikey).apply();
            }
        });

        ibVirusTotal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.viewFAQ(v.getContext(), 181);
            }
        });

        swSend.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("send_enabled", checked).apply();
            }
        });

        etSend.setHint(Send.DEFAULT_SERVER);
        etSend.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Do nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                String apikey = s.toString().trim();
                if (TextUtils.isEmpty(apikey))
                    prefs.edit().remove("send_host").apply();
                else
                    prefs.edit().putString("send_host", apikey).apply();
            }
        });

        ibSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.viewFAQ(v.getContext(), 183);
            }
        });

        swOpenAi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("openai_enabled", checked).apply();
            }
        });

        tvOpenAiPrivacy.getPaint().setUnderlineText(true);
        tvOpenAiPrivacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.view(v.getContext(), Uri.parse(BuildConfig.OPENAI_PRIVACY), true);
            }
        });

        etOpenAi.setHint(BuildConfig.OPENAI_ENDPOINT);
        etOpenAi.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Do nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                String apikey = s.toString().trim();
                if (TextUtils.isEmpty(apikey))
                    prefs.edit().remove("openai_uri").apply();
                else
                    prefs.edit().putString("openai_uri", apikey).apply();
            }
        });

        tilOpenAi.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Do nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                String apikey = s.toString().trim();
                if (TextUtils.isEmpty(apikey))
                    prefs.edit().remove("openai_apikey").apply();
                else
                    prefs.edit().putString("openai_apikey", apikey).apply();
            }
        });

        etOpenAiModel.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Do nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                String model = s.toString().trim();
                if (TextUtils.isEmpty(model))
                    prefs.edit().remove("openai_model").apply();
                else
                    prefs.edit().putString("openai_model", model).apply();
            }
        });

        sbOpenAiTemperature.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float temp = progress / 10f;
                prefs.edit().putFloat("openai_temperature", temp).apply();
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

        swOpenAiModeration.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("openai_moderation", checked).apply();
            }
        });

        ibOpenAi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.viewFAQ(v.getContext(), 190);
            }
        });

        swWatchdog.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("watchdog", checked).apply();
            }
        });

        tvExperimentsHint.setPaintFlags(tvExperimentsHint.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvExperimentsHint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.viewFAQ(v.getContext(), 125);
            }
        });

        swExperiments.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("experiments", checked).apply();
            }
        });

        swMainLog.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("main_log", checked).apply();
                swMainLogMem.setEnabled(checked);
            }
        });

        swMainLogMem.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("main_log_memory", checked).apply();
            }
        });

        swProtocol.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("protocol", checked).apply();
                if (checked)
                    prefs.edit()
                            .putLong("protocol_since", new Date().getTime())
                            .putInt("log_level", android.util.Log.INFO)
                            .apply();
                else
                    EntityLog.clear(compoundButton.getContext());
            }
        });

        swLogInfo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putInt("log_level", checked ? android.util.Log.INFO : android.util.Log.WARN).apply();
            }
        });

        swDebug.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("debug", checked).apply();
                cardDebug.setVisibility(checked || BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
                if (checked)
                    view.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                view.scrollTo(0, cardAdvanced.getTop() + swDebug.getTop());
                            } catch (Throwable ex) {
                                Log.e(ex);
                            }
                        }
                    });
            }
        });

        swCanary.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        swCanary.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("leak_canary", checked).apply();
                CoalMine.setup(checked);
            }
        });

        swTest1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("test1", checked).apply();
            }
        });

        swTest2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("test2", checked).apply();
            }
        });

        swTest3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("test3", checked).apply();
            }
        });

        swTest4.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("test4", checked).apply();
            }
        });

        swTest5.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("test5", checked).apply();
            }
        });

        btnRepair.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(view.getContext())
                        .setIcon(R.drawable.twotone_bug_report_24)
                        .setTitle(R.string.title_advanced_repair)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                new SimpleTask<Void>() {
                                    @Override
                                    protected void onPostExecute(Bundle args) {
                                        prefs.edit().remove("debug").apply();
                                    }

                                    @Override
                                    protected Void onExecute(Context context, Bundle args) throws Throwable {
                                        DB db = DB.getInstance(context);

                                        List<EntityAccount> accounts = db.account().getAccounts();
                                        if (accounts == null)
                                            return null;

                                        for (EntityAccount account : accounts) {
                                            if (account.protocol != EntityAccount.TYPE_IMAP)
                                                continue;

                                            List<EntityFolder> folders = db.folder().getFolders(account.id, false, false);
                                            if (folders == null)
                                                continue;

                                            EntityFolder inbox = db.folder().getFolderByType(account.id, EntityFolder.INBOX);
                                            for (EntityFolder folder : folders) {
                                                if (inbox == null && "inbox".equalsIgnoreCase(folder.name))
                                                    folder.type = EntityFolder.INBOX;

                                                if (!EntityFolder.USER.equals(folder.type) &&
                                                        !EntityFolder.SYSTEM.equals(folder.type)) {
                                                    EntityLog.log(context, "Repairing " + account.name + ":" + folder.type);
                                                    folder.setProperties();
                                                    folder.setSpecials(account);
                                                    db.folder().updateFolder(folder);
                                                }
                                            }
                                        }

                                        return null;
                                    }

                                    @Override
                                    protected void onExecuted(Bundle args, Void data) {
                                        ToastEx.makeText(v.getContext(), R.string.title_completed, Toast.LENGTH_LONG).show();
                                        ServiceSynchronize.reload(v.getContext(), null, true, "repair");
                                    }

                                    @Override
                                    protected void onException(Bundle args, Throwable ex) {
                                        Log.unexpectedError(getParentFragmentManager(), ex);
                                    }
                                }.execute(FragmentOptionsMisc.this, new Bundle(), "repair");
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Do nothing
                            }
                        })
                        .show();
            }

        });

        btnDaily.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new SimpleTask<Void>() {
                    @Override
                    protected Void onExecute(Context context, Bundle args) throws Throwable {
                        WorkerDailyRules.daily(context);
                        return null;
                    }

                    @Override
                    protected void onException(Bundle args, Throwable ex) {
                        Log.unexpectedError(getParentFragmentManager(), ex);
                    }
                }.execute(FragmentOptionsMisc.this, new Bundle(), "daily");
            }
        });

        swAutostart.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                Helper.enableComponent(v.getContext(), ReceiverAutoStart.class, checked);
            }
        });

        swEmergency.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                prefs.edit().putBoolean("emergency_file", checked).apply();
            }
        });

        swWorkManager.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean("work_manager", isChecked).apply();
            }
        });

        swExternalStorage.setEnabled(Helper.getExternalFilesDir(getContext()) != null);
        swExternalStorage.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("external_storage", isChecked);
                if (BuildConfig.DEBUG)
                    editor.putBoolean("external_storage_message", isChecked);
                editor.apply();

                Bundle args = new Bundle();
                args.putBoolean("external_storage", isChecked);

                new SimpleTask<Integer>() {
                    @Override
                    protected Integer onExecute(Context context, Bundle args) throws IOException {
                        boolean external_storage = args.getBoolean("external_storage");

                        File sourceRoot = (!external_storage
                                ? Helper.getExternalFilesDir(context)
                                : context.getFilesDir());

                        File targetRoot = (external_storage
                                ? Helper.getExternalFilesDir(context)
                                : context.getFilesDir());

                        File source = Helper.ensureExists(new File(sourceRoot, "attachments"));
                        File target = Helper.ensureExists(new File(targetRoot, "attachments"));

                        File[] attachments = source.listFiles();
                        if (attachments != null)
                            for (File attachment : attachments) {
                                File dest = new File(target, attachment.getName());
                                Log.i("Move " + attachment + " to " + dest);
                                Helper.copy(attachment, dest);
                                attachment.delete();
                            }

                        if (BuildConfig.DEBUG) {
                            source = Helper.ensureExists(new File(sourceRoot, "messages"));
                            target = Helper.ensureExists(new File(targetRoot, "messages"));
                            File[] dirs = source.listFiles();
                            if (dirs != null)
                                for (File dir : dirs) {
                                    File[] messages = dir.listFiles();
                                    if (messages != null)
                                        for (File message : messages) {
                                            String path = dir.getPath();
                                            path = path.substring(path.lastIndexOf(File.separator));
                                            File t = new File(target, path);
                                            if (!t.exists() && !t.mkdir())
                                                throw new IOException("Could not create dir=" + t);
                                            File dest = new File(t, message.getName());
                                            Log.i("Move " + message + " to " + dest);
                                            Helper.copy(message, dest);
                                            message.delete();
                                        }
                                    dir.delete();
                                }
                        }

                        return (attachments == null ? -1 : attachments.length);
                    }

                    @Override
                    protected void onExecuted(Bundle args, Integer count) {
                        String msg = String.format("Moved %d attachments", count);
                        ToastEx.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    protected void onException(Bundle args, Throwable ex) {
                        Log.unexpectedError(getParentFragmentManager(), ex);
                    }
                }.execute(FragmentOptionsMisc.this, args, "external");
            }
        });

        swIntegrity.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                prefs.edit()
                        .putBoolean("sqlite_integrity_check", checked)
                        .remove("debug")
                        .commit();
                ApplicationEx.restart(v.getContext(), "sqlite_integrity_check");
            }
        });

        swWal.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("wal", checked).commit(); // apply won't work here
            }
        });

        swCheckpoints.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("sqlite_checkpoints", checked).apply();
            }
        });

        swAnalyze.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("sqlite_analyze", checked).apply();
            }
        });

        swAutoVacuum.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                prefs.edit()
                        .putBoolean("sqlite_auto_vacuum", checked)
                        .remove("debug")
                        .commit();
                ApplicationEx.restart(v.getContext(), "sqlite_auto_vacuum");
            }
        });

        swSyncExtra.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                prefs.edit()
                        .putBoolean("sqlite_sync_extra", checked)
                        .remove("debug")
                        .commit();
                ApplicationEx.restart(v.getContext(), "sqlite_sync_extra");
            }
        });

        sbSqliteCache.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("sqlite_cache", progress).apply();
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

        ibSqliteCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().remove("debug").commit();
                ApplicationEx.restart(v.getContext(), "sqlite_cache");
            }
        });

        sbChunkSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress = progress / 10;
                if (progress < 1)
                    progress = 1;
                progress = progress * 10;
                prefs.edit().putInt("chunk_size", progress).apply();
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

        sbThreadRange.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("thread_range", progress).apply();
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

        swAutoScroll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("autoscroll_editor", checked).apply();
            }
        });

        swUndoManager.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("undo_manager", checked).apply();
            }
        });

        swBrowserZoom.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        swBrowserZoom.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("browser_zoom", checked).apply();
            }
        });

        swFakeDark.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("fake_dark", checked).apply();
            }
        });

        swShowRecent.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("show_recent", checked).apply();
            }
        });

        swModSeq.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("use_modseq", checked).apply();
                ServiceSynchronize.reload(compoundButton.getContext(), null, true, "use_modseq");
            }
        });

        swPreamble.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("preamble", checked).apply();
                System.setProperty("fairemail.preamble", Boolean.toString(checked));
            }
        });

        swUid.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("uid_command", checked).apply();
                System.setProperty("fairemail.uid_command", Boolean.toString(checked));
                ServiceSynchronize.reload(compoundButton.getContext(), null, true, "uid_command");
            }
        });

        swExpunge.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("perform_expunge", checked).apply();
                ServiceSynchronize.reload(compoundButton.getContext(), null, true, "perform_expunge");
            }
        });

        swUidExpunge.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("uid_expunge", checked).apply();
                ServiceSynchronize.reload(compoundButton.getContext(), null, true, "uid_expunge");
            }
        });

        swAuthPlain.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("auth_plain", checked).apply();
            }
        });

        swAuthLogin.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("auth_login", checked).apply();
            }
        });

        swAuthNtlm.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("auth_ntlm", checked).apply();
            }
        });

        swAuthSasl.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("auth_sasl", checked).apply();
            }
        });

        swAuthApop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("auth_apop", checked).apply();
            }
        });

        swUseTop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("use_top", checked).apply();
            }
        });

        swKeepAlivePoll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("keep_alive_poll", checked).apply();
            }
        });

        swEmptyPool.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("empty_pool", checked).apply();
            }
        });

        swIdleDone.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("idle_done", checked).apply();
            }
        });

        swFastFetch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("fast_fetch", checked).apply();
            }
        });

        sbMaxBackOff.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("max_backoff_power", progress).apply();
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

        swLogarithmicBackoff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("logarithmic_backoff", checked).apply();
            }
        });

        swExactAlarms.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("exact_alarms", checked).apply();
            }
        });

        swNativeDkim.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("native_dkim", checked).apply();
                swNativeArc.setEnabled(checked && swNativeDkim.isEnabled());
                etNativeArcWhitelist.setEnabled(checked && swNativeDkim.isEnabled());
            }
        });

        swNativeArc.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("native_arc", checked).apply();
            }
        });

        etNativeArcWhitelist.setHint(TextUtils.join(",", MessageHelper.ARC_WHITELIST_DEFAULT));
        etNativeArcWhitelist.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Do nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                prefs.edit().putString("native_arc_whitelist", s.toString().trim()).apply();
            }
        });

        swInfra.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("infra", checked).apply();
            }
        });

        swDupMsgId.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("dup_msgids", checked).apply();
            }
        });

        swThreadByRef.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("thread_byref", checked).apply();
            }
        });

        etKeywords.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Do nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                String keywords = s.toString().trim();
                String[] keyword = keywords.replaceAll("\\s+", " ").split(" ");
                for (int i = 0; i < keyword.length; i++)
                    keyword[i] = MessageHelper.sanitizeKeyword(keyword[i]);
                keywords = String.join(" ", keyword);

                if (TextUtils.isEmpty(keywords))
                    prefs.edit().remove("global_keywords").apply();
                else
                    prefs.edit().putString("global_keywords", keywords).apply();
            }
        });

        swTestIab.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("test_iab", checked).apply();
            }
        });

        btnImportProviders.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setType("*/*");
                Intent choose = Helper.getChooser(v.getContext(), intent);
                getActivity().startActivityForResult(choose, ActivitySetup.REQUEST_IMPORT_PROVIDERS);
            }
        });

        btnExportClassifier.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onExportClassifier(v.getContext());
            }
        });

        btnGC.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.gc(true);
                DB.shrinkMemory(v.getContext());
            }
        });

        btnCharsets.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new SimpleTask<SortedMap<String, Charset>>() {
                    @Override
                    protected void onPreExecute(Bundle args) {
                        btnCharsets.setEnabled(false);
                    }

                    @Override
                    protected void onPostExecute(Bundle args) {
                        btnCharsets.setEnabled(true);
                    }

                    @Override
                    protected SortedMap<String, Charset> onExecute(Context context, Bundle args) {
                        return Charset.availableCharsets();
                    }

                    @Override
                    protected void onExecuted(Bundle args, SortedMap<String, Charset> charsets) {
                        StringBuilder sb = new StringBuilder();
                        for (String key : charsets.keySet())
                            sb.append(charsets.get(key).displayName()).append("\r\n");
                        new AlertDialog.Builder(getContext())
                                .setIcon(R.drawable.twotone_info_24)
                                .setTitle(R.string.title_advanced_charsets)
                                .setMessage(sb.toString())
                                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        // Do nothing
                                    }
                                })
                                .show();
                    }

                    @Override
                    protected void onException(Bundle args, Throwable ex) {
                        Log.unexpectedError(getParentFragmentManager(), ex);
                    }
                }.execute(FragmentOptionsMisc.this, new Bundle(), "setup:charsets");
            }
        });

        btnFontMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SpannableStringBuilder ssb = new SpannableStringBuilderEx();

                try {
                    Typeface typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL);
                    Field f = Typeface.class.getDeclaredField("sSystemFontMap");
                    f.setAccessible(true);
                    Map<String, Typeface> sSystemFontMap = (Map<String, Typeface>) f.get(typeface);
                    for (String key : sSystemFontMap.keySet())
                        ssb.append(key).append("\n");
                } catch (Throwable ex) {
                    ssb.append(ex.toString());
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ssb.append("\n");
                    for (Font font : SystemFonts.getAvailableFonts())
                        ssb.append(font.getFile().getName()).append("\n");
                }

                new AlertDialog.Builder(getContext())
                        .setIcon(R.drawable.twotone_info_24)
                        .setTitle(R.string.title_advanced_font_map)
                        .setMessage(ssb)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Do nothing
                            }
                        })
                        .show();
            }
        });

        final String title = getString(R.string.title_advanced_files, Helper.humanReadableByteCount(MIN_FILE_SIZE));
        btnFiles.setText(title);

        btnFiles.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new SimpleTask<List<File>>() {
                    @Override
                    protected void onPreExecute(Bundle args) {
                        btnFiles.setEnabled(false);
                    }

                    @Override
                    protected void onPostExecute(Bundle args) {
                        btnFiles.setEnabled(true);
                    }

                    @Override
                    protected List<File> onExecute(Context context, Bundle args) {
                        List<File> files = new ArrayList<>();
                        files.addAll(Helper.listFiles(context.getFilesDir(), MIN_FILE_SIZE));
                        files.addAll(Helper.listFiles(context.getCacheDir(), MIN_FILE_SIZE));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            files.addAll(Helper.listFiles(context.getDataDir(), MIN_FILE_SIZE));
                        files.addAll(Helper.listFiles(Helper.getExternalFilesDir(context), MIN_FILE_SIZE));

                        Collections.sort(files, new Comparator<File>() {
                            @Override
                            public int compare(File f1, File f2) {
                                return -Long.compare(f1.length(), f2.length());
                            }
                        });

                        return files;
                    }

                    @Override
                    protected void onExecuted(Bundle args, List<File> files) {
                        SpannableStringBuilder ssb = new SpannableStringBuilderEx();

                        final Context context = getContext();
                        File dataDir = (Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                                ? null : context.getDataDir());
                        File filesDir = context.getFilesDir();
                        File cacheDir = context.getCacheDir();
                        File externalDir = Helper.getExternalFilesDir(context);

                        if (dataDir != null)
                            ssb.append("Data: ").append(dataDir.getAbsolutePath()).append("\r\n");
                        if (filesDir != null)
                            ssb.append("Files: ").append(filesDir.getAbsolutePath()).append("\r\n");
                        if (cacheDir != null)
                            ssb.append("Cache: ").append(cacheDir.getAbsolutePath()).append("\r\n");
                        if (externalDir != null)
                            ssb.append("External: ").append(externalDir.getAbsolutePath()).append("\r\n");
                        ssb.append("\r\n");

                        for (File file : files) {
                            int start = ssb.length();
                            ssb.append(Helper.humanReadableByteCount(file.length()));
                            ssb.setSpan(new StyleSpan(Typeface.BOLD), start, ssb.length(), 0);
                            ssb.append(' ')
                                    .append(file.getAbsolutePath())
                                    .append("\r\n");
                        }

                        ssb.setSpan(new RelativeSizeSpan(HtmlHelper.FONT_SMALL), 0, ssb.length(), 0);

                        new AlertDialog.Builder(context)
                                .setIcon(R.drawable.twotone_info_24)
                                .setTitle(title)
                                .setMessage(ssb)
                                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        // Do nothing
                                    }
                                })
                                .show();
                    }

                    @Override
                    protected void onException(Bundle args, Throwable ex) {
                        Log.unexpectedError(getParentFragmentManager(), ex);
                    }
                }.execute(FragmentOptionsMisc.this, new Bundle(), "setup:files");
            }
        });

        btnUris.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SpannableStringBuilder ssb = new SpannableStringBuilderEx();
                List<UriPermission> permissions = v.getContext().getContentResolver().getPersistedUriPermissions();
                for (UriPermission permission : permissions) {
                    ssb.append(permission.getUri().toString());
                    ssb.append('\u00a0');
                    if (permission.isReadPermission())
                        ssb.append("r");
                    if (permission.isWritePermission())
                        ssb.append("w");
                    ssb.append('\n');
                }

                new AlertDialog.Builder(v.getContext())
                        .setIcon(R.drawable.twotone_info_24)
                        .setTitle(R.string.title_advanced_all_permissions)
                        .setMessage(ssb)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Do nothing
                            }
                        })
                        .show();
            }
        });

        btnAllPermissions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new SimpleTask<Spanned>() {
                    @Override
                    protected Spanned onExecute(Context context, Bundle args) throws Throwable {
                        SpannableStringBuilder ssb = new SpannableStringBuilderEx();

                        PackageManager pm = context.getPackageManager();
                        List<PermissionGroupInfo> groups = pm.getAllPermissionGroups(0);
                        groups.add(0, null); // Ungrouped

                        for (PermissionGroupInfo group : groups) {
                            String name = (group == null ? null : group.name);
                            int start = ssb.length();
                            ssb.append(name == null ? "Ungrouped" : name);
                            ssb.setSpan(new StyleSpan(Typeface.BOLD), start, ssb.length(), 0);
                            ssb.append("\n\n");

                            try {
                                for (PermissionInfo permission : pm.queryPermissionsByGroup(name, 0)) {
                                    start = ssb.length();
                                    ssb.append(permission.name);
                                    ssb.setSpan(new RelativeSizeSpan(HtmlHelper.FONT_SMALL), start, ssb.length(), 0);
                                    ssb.append("\n");
                                }
                            } catch (PackageManager.NameNotFoundException ex) {
                                ssb.append(ex.toString()).append("\n")
                                        .append(android.util.Log.getStackTraceString(ex)).append("\n");
                            }

                            ssb.append("\n");
                        }

                        return ssb;
                    }

                    @Override
                    protected void onExecuted(Bundle args, Spanned ssb) {
                        new AlertDialog.Builder(v.getContext())
                                .setIcon(R.drawable.twotone_info_24)
                                .setTitle(R.string.title_advanced_all_permissions)
                                .setMessage(ssb)
                                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        // Do nothing
                                    }
                                })
                                .show();
                    }

                    @Override
                    protected void onException(Bundle args, Throwable ex) {
                        Log.unexpectedError(getParentFragmentManager(), ex);
                    }
                }.execute(FragmentOptionsMisc.this, new Bundle(), "misc:permissions");
            }
        });

        // Initialize
        FragmentDialogTheme.setBackground(getContext(), view, false);

        swPowerMenu.setVisibility(!BuildConfig.PLAY_STORE_RELEASE &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? View.VISIBLE : View.GONE);

        tvFtsIndexed.setText(null);

        swExternalAnswer.setVisibility(
                ActivityAnswer.canAnswer(getContext()) ? View.VISIBLE : View.GONE);

        DB db = DB.getInstance(getContext());
        db.message().liveFts().observe(getViewLifecycleOwner(), new Observer<TupleFtsStats>() {
            private TupleFtsStats last = null;

            @Override
            public void onChanged(TupleFtsStats stats) {
                if (stats == null)
                    tvFtsIndexed.setText(null);
                else if (last == null || !last.equals(stats))
                    tvFtsIndexed.setText(getString(R.string.title_advanced_fts_indexed,
                            stats.fts,
                            stats.total,
                            Helper.humanReadableByteCount(Fts4DbHelper.size(tvFtsIndexed.getContext()))));
                last = stats;
            }
        });

        grpUpdates.setVisibility(!BuildConfig.DEBUG &&
                (Helper.isPlayStoreInstall() || !Helper.hasValidFingerprint(getContext()))
                ? View.GONE : View.VISIBLE);
        grpBitbucket.setVisibility(View.GONE);
        grpAnnouncements.setVisibility(TextUtils.isEmpty(BuildConfig.ANNOUNCEMENT_URI)
                ? View.GONE : View.VISIBLE);
        grpVirusTotal.setVisibility(BuildConfig.PLAY_STORE_RELEASE ? View.GONE : View.VISIBLE);
        grpSend.setVisibility(BuildConfig.PLAY_STORE_RELEASE ? View.GONE : View.VISIBLE);
        grpOpenAi.setVisibility(TextUtils.isEmpty(BuildConfig.OPENAI_ENDPOINT) ? View.GONE : View.VISIBLE);
        grpTest.setVisibility(BuildConfig.TEST_RELEASE ? View.VISIBLE : View.GONE);

        setLastCleanup(prefs.getLong("last_cleanup", -1));

        if (prefs.contains("last_daily"))
            tvLastDaily.setText(new Date(prefs.getLong("last_daily", 0)).toString());
        else
            tvLastDaily.setText(("-"));

        File external = Helper.getExternalFilesDir(getContext());
        boolean emulated = (external != null && Environment.isExternalStorageEmulated(external));
        tvExternalStorageFolder.setText(
                (external == null ? null : external.getAbsolutePath()) + (emulated ? " emulated" : ""));

        swExactAlarms.setEnabled(AlarmManagerCompatEx.canScheduleExactAlarms(getContext()));
        swTestIab.setVisibility(BuildConfig.DEBUG && BuildConfig.TEST_RELEASE ? View.VISIBLE : View.GONE);

        PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setContactInfo();
        setSuffixes();
        setPermissionInfo();
    }

    @Override
    public void onResume() {
        super.onResume();
        resumed = true;

        if (!Helper.isPlayStoreInstall() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm =
                    Helper.getSystemService(getContext(), NotificationManager.class);

            NotificationChannel notification = nm.getNotificationChannel("update");
            if (notification != null) {
                boolean disabled = notification.getImportance() == NotificationManager.IMPORTANCE_NONE;
                ibChannelUpdated.setImageLevel(disabled ? 0 : 1);
                ibChannelUpdated.setVisibility(disabled ? View.VISIBLE : View.GONE);
            }
        }

        View view = getView();
        if (view != null)
            view.post(new Runnable() {
                @Override
                public void run() {
                    updateUsage();
                }
            });
    }

    @Override
    public void onPause() {
        super.onPause();
        resumed = false;
    }

    @Override
    public void onDestroyView() {
        PreferenceManager.getDefaultSharedPreferences(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        try {
            switch (requestCode) {
                case REQUEST_CLASSIFIER:
                    if (resultCode == Activity.RESULT_OK && data != null)
                        onHandleExportClassifier(data);
                    break;
            }
        } catch (Throwable ex) {
            Log.e(ex);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if ("last_cleanup".equals(key))
            setLastCleanup(prefs.getLong(key, -1));

        if ("last_daily".equals(key))
            tvLastDaily.setText(new Date(prefs.getLong(key, 0)).toString());

        if ("lt_uri".equals(key) ||
                "lt_user".equals(key) ||
                "lt_key".equals(key) ||
                "vt_apikey".equals(key) ||
                "send_host".equals(key) ||
                "openai_uri".equals(key) ||
                "openai_apikey".equals(key) ||
                "openai_model".equals(key))
            return;

        if ("global_keywords".equals(key))
            return;

        if ("native_arc_whitelist".equals(key))
            return;

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

    private void onResetQuestions() {
        final Context context = getContext();
        View dview = LayoutInflater.from(context).inflate(R.layout.dialog_reset_questions, null);
        final CheckBox cbGeneral = dview.findViewById(R.id.cbGeneral);
        final CheckBox cbLinks = dview.findViewById(R.id.cbLinks);
        final CheckBox cbFiles = dview.findViewById(R.id.cbFiles);
        final CheckBox cbImages = dview.findViewById(R.id.cbImages);
        final CheckBox cbFull = dview.findViewById(R.id.cbFull);

        new AlertDialog.Builder(context)
                .setView(dview)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                        SharedPreferences.Editor editor = prefs.edit();

                        if (cbGeneral.isChecked())
                            for (String key : RESET_QUESTIONS)
                                if (prefs.contains(key)) {
                                    Log.i("Removing option=" + key);
                                    editor.remove(key);
                                }

                        for (String key : prefs.getAll().keySet())
                            if ((!BuildConfig.DEBUG &&
                                    key.startsWith("translated_") && cbGeneral.isChecked()) ||
                                    key.startsWith("oauth.") ||
                                    (key.startsWith("announcement.") && cbGeneral.isChecked()) ||
                                    (key.endsWith(".confirm_link") && cbLinks.isChecked()) ||
                                    (key.endsWith(".link_view") && cbLinks.isChecked()) ||
                                    (key.endsWith(".link_sanitize") && cbLinks.isChecked()) ||
                                    (key.endsWith(".confirm_files") && cbFiles.isChecked()) ||
                                    (key.endsWith(".show_images") && cbImages.isChecked()) ||
                                    (key.endsWith(".show_full") && cbFull.isChecked())) {
                                Log.i("Removing option=" + key);
                                editor.remove(key);
                            }

                        editor.apply();

                        ToastEx.makeText(context, R.string.title_setup_done, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onCleanup() {
        new SimpleTask<Void>() {
            private Toast toast = null;

            @Override
            protected void onPreExecute(Bundle args) {
                btnCleanup.setEnabled(false);
                toast = ToastEx.makeText(getContext(), R.string.title_executing, Toast.LENGTH_LONG);
                toast.show();
            }

            @Override
            protected void onPostExecute(Bundle args) {
                btnCleanup.setEnabled(true);
                if (toast != null)
                    toast.cancel();
            }

            @Override
            protected Void onExecute(Context context, Bundle args) {
                WorkerCleanup.cleanup(context, true);
                return null;
            }

            @Override
            protected void onExecuted(Bundle args, Void data) {
                final Context context = getContext();
                WorkManager.getInstance(context).pruneWork();
                WorkerAutoUpdate.init(context);
                WorkerCleanup.init(context);
                WorkerDailyRules.init(context);
                WorkerSync.init(context);
                ToastEx.makeText(context, R.string.title_completed, Toast.LENGTH_LONG).show();
            }

            @Override
            protected void onException(Bundle args, Throwable ex) {
                Log.unexpectedError(getParentFragmentManager(), ex);
            }
        }.execute(this, new Bundle(), "cleanup:run");
    }

    private void setOptions() {
        try {
            if (view == null || getContext() == null)
                return;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

            ActivityManager am = Helper.getSystemService(getContext(), ActivityManager.class);
            int class_mb = am.getMemoryClass();
            int class_large_mb = am.getLargeMemoryClass();
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);

            swSortAnswers.setChecked(prefs.getBoolean("sort_answers", false));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                swPowerMenu.setChecked(Helper.isComponentEnabled(getContext(), ServicePowerControl.class));
            swSendSelf.setChecked(Helper.isComponentEnabled(getContext(), ActivitySendSelf.class));
            swExternalSearch.setChecked(Helper.isComponentEnabled(getContext(), ActivitySearch.class));
            swExternalAnswer.setChecked(Helper.isComponentEnabled(getContext(), ActivityAnswer.class));
            swShortcuts.setChecked(prefs.getBoolean("shortcuts", true));
            swFts.setChecked(prefs.getBoolean("fts", false));

            swClassification.setChecked(prefs.getBoolean("classification", false));

            int class_min_chance = prefs.getInt("class_min_probability", 5);
            tvClassMinProbability.setText(getString(R.string.title_advanced_class_min_chance, NF.format(class_min_chance)));
            sbClassMinProbability.setProgress(class_min_chance);

            int class_min_difference = prefs.getInt("class_min_difference", 40);
            tvClassMinDifference.setText(getString(R.string.title_advanced_class_min_difference, NF.format(class_min_difference)));
            sbClassMinDifference.setProgress(class_min_difference);

            swShowFiltered.setChecked(prefs.getBoolean("show_filtered", false));

            int selected = -1;
            String language = prefs.getString("language", null);
            List<String> display = new ArrayList<>();
            display.add(getString(R.string.title_advanced_language_system));
            for (int pos = 0; pos < languages.size(); pos++) {
                Pair<String, String> lang = languages.get(pos);
                display.add(lang.second);
                if (lang.first.equals(language))
                    selected = pos + 1;
            }

            swUpdates.setChecked(prefs.getBoolean("updates", true));
            swCheckWeekly.setChecked(prefs.getBoolean("weekly", Helper.hasPlayStore(getContext())));
            swCheckWeekly.setEnabled(swUpdates.isChecked());
            swBeta.setChecked(prefs.getBoolean("beta", false));
            swBeta.setEnabled(swUpdates.isChecked());
            swChangelog.setChecked(prefs.getBoolean("show_changelog", !BuildConfig.PLAY_STORE_RELEASE));
            swAnnouncements.setChecked(prefs.getBoolean("announcements", true));
            swExperiments.setChecked(prefs.getBoolean("experiments", false));
            swCrashReports.setChecked(prefs.getBoolean("crash_reports", false));
            tvUuid.setText(prefs.getString("uuid", null));
            swCleanupAttachments.setChecked(prefs.getBoolean("cleanup_attachments", false));

            ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, android.R.id.text1, display);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spLanguage.setAdapter(adapter);
            if (selected >= 0)
                spLanguage.setSelection(selected);

            swLanguageTool.setChecked(prefs.getBoolean("lt_enabled", false));
            swLanguageToolAuto.setChecked(prefs.getBoolean("lt_auto", true));
            swLanguageToolAuto.setEnabled(swLanguageTool.isChecked());
            swLanguageToolPicky.setChecked(prefs.getBoolean("lt_picky", false));
            swLanguageToolPicky.setEnabled(swLanguageTool.isChecked());
            etLanguageTool.setText(prefs.getString("lt_uri", null));
            etLanguageToolUser.setText(prefs.getString("lt_user", null));
            tilLanguageToolKey.getEditText().setText(prefs.getString("lt_key", null));
            swDeepL.setChecked(prefs.getBoolean("deepl_enabled", false));
            swVirusTotal.setChecked(prefs.getBoolean("vt_enabled", false));
            tilVirusTotal.getEditText().setText(prefs.getString("vt_apikey", null));
            swSend.setChecked(prefs.getBoolean("send_enabled", false));
            etSend.setText(prefs.getString("send_host", null));
            swOpenAi.setChecked(prefs.getBoolean("openai_enabled", false));
            etOpenAi.setText(prefs.getString("openai_uri", null));
            tilOpenAi.getEditText().setText(prefs.getString("openai_apikey", null));
            etOpenAiModel.setText(prefs.getString("openai_model", null));

            float temperature = prefs.getFloat("openai_temperature", 0.5f);
            tvOpenAiTemperature.setText(getString(R.string.title_advanced_openai_temperature, NF.format(temperature)));
            sbOpenAiTemperature.setProgress(Math.round(temperature * 10));
            swOpenAiModeration.setChecked(prefs.getBoolean("openai_moderation", false));

            swWatchdog.setChecked(prefs.getBoolean("watchdog", true));
            swMainLog.setChecked(prefs.getBoolean("main_log", true));
            swMainLogMem.setChecked(prefs.getBoolean("main_log_memory", false));
            swMainLogMem.setEnabled(swMainLog.isChecked());
            swProtocol.setChecked(prefs.getBoolean("protocol", false));
            swLogInfo.setChecked(prefs.getInt("log_level", android.util.Log.WARN) <= android.util.Log.INFO);
            swDebug.setChecked(prefs.getBoolean("debug", false));
            swCanary.setChecked(prefs.getBoolean("leak_canary", false));
            swTest1.setChecked(prefs.getBoolean("test1", false));
            swTest2.setChecked(prefs.getBoolean("test2", false));
            swTest3.setChecked(prefs.getBoolean("test3", false));
            swTest4.setChecked(prefs.getBoolean("test4", false));
            swTest5.setChecked(prefs.getBoolean("test5", false));

            swAutostart.setChecked(Helper.isComponentEnabled(getContext(), ReceiverAutoStart.class));
            swEmergency.setChecked(prefs.getBoolean("emergency_file", true));
            swWorkManager.setChecked(prefs.getBoolean("work_manager", true));
            swExternalStorage.setChecked(prefs.getBoolean("external_storage", false));

            swIntegrity.setChecked(prefs.getBoolean("sqlite_integrity_check", true));
            swWal.setChecked(prefs.getBoolean("wal", true));
            swCheckpoints.setChecked(prefs.getBoolean("sqlite_checkpoints", true));
            swAnalyze.setChecked(prefs.getBoolean("sqlite_analyze", true));
            swAutoVacuum.setChecked(prefs.getBoolean("sqlite_auto_vacuum", false));
            swSyncExtra.setChecked(prefs.getBoolean("sqlite_sync_extra", true));

            int sqlite_cache = prefs.getInt("sqlite_cache", DB.DEFAULT_CACHE_SIZE);
            Integer cache_size = DB.getCacheSizeKb(getContext());
            if (cache_size == null)
                cache_size = 2000;
            tvSqliteCache.setText(getString(R.string.title_advanced_sqlite_cache,
                    NF.format(sqlite_cache),
                    Helper.humanReadableByteCount(cache_size * 1024L)));
            sbSqliteCache.setProgress(sqlite_cache);

            int chunk_size = prefs.getInt("chunk_size", Core.DEFAULT_CHUNK_SIZE);
            tvChunkSize.setText(getString(R.string.title_advanced_chunk_size, chunk_size));
            sbChunkSize.setProgress(chunk_size);

            int thread_range = prefs.getInt("thread_range", MessageHelper.DEFAULT_THREAD_RANGE);
            int range = (int) Math.pow(2, thread_range);
            tvThreadRange.setText(getString(R.string.title_advanced_thread_range, range));
            sbThreadRange.setProgress(thread_range);

            swAutoScroll.setChecked(prefs.getBoolean("autoscroll_editor", false));
            swUndoManager.setChecked(prefs.getBoolean("undo_manager", false));
            swBrowserZoom.setChecked(prefs.getBoolean("browser_zoom", false));
            swFakeDark.setChecked(prefs.getBoolean("fake_dark", false));
            swShowRecent.setChecked(prefs.getBoolean("show_recent", false));
            swModSeq.setChecked(prefs.getBoolean("use_modseq", true));
            swPreamble.setChecked(prefs.getBoolean("preamble", false));
            swUid.setChecked(prefs.getBoolean("uid_command", false));
            swExpunge.setChecked(prefs.getBoolean("perform_expunge", true));
            swUidExpunge.setChecked(prefs.getBoolean("uid_expunge", false));
            swAuthPlain.setChecked(prefs.getBoolean("auth_plain", true));
            swAuthLogin.setChecked(prefs.getBoolean("auth_login", true));
            swAuthNtlm.setChecked(prefs.getBoolean("auth_ntlm", true));
            swAuthSasl.setChecked(prefs.getBoolean("auth_sasl", true));
            swAuthApop.setChecked(prefs.getBoolean("auth_apop", false));
            swUseTop.setChecked(prefs.getBoolean("use_top", true));
            swKeepAlivePoll.setChecked(prefs.getBoolean("keep_alive_poll", false));
            swEmptyPool.setChecked(prefs.getBoolean("empty_pool", true));
            swIdleDone.setChecked(prefs.getBoolean("idle_done", true));
            swFastFetch.setChecked(prefs.getBoolean("fast_fetch", false));

            int max_backoff_power = prefs.getInt("max_backoff_power", ServiceSynchronize.DEFAULT_BACKOFF_POWER - 3);
            int max_backoff = (int) Math.pow(2, max_backoff_power + 3);
            tvMaxBackoff.setText(getString(R.string.title_advanced_max_backoff, max_backoff));
            sbMaxBackOff.setProgress(max_backoff_power);

            swLogarithmicBackoff.setChecked(prefs.getBoolean("logarithmic_backoff", true));
            swExactAlarms.setChecked(prefs.getBoolean("exact_alarms", true));
            swNativeDkim.setEnabled(!BuildConfig.PLAY_STORE_RELEASE);
            swNativeDkim.setChecked(prefs.getBoolean("native_dkim", false));
            swNativeArc.setEnabled(swNativeDkim.isEnabled() && swNativeDkim.isChecked());
            swNativeArc.setChecked(prefs.getBoolean("native_arc", true));
            etNativeArcWhitelist.setEnabled(swNativeDkim.isEnabled() && swNativeDkim.isChecked());
            etNativeArcWhitelist.setText(prefs.getString("native_arc_whitelist", null));
            swInfra.setChecked(prefs.getBoolean("infra", false));
            swDupMsgId.setChecked(prefs.getBoolean("dup_msgids", false));
            swThreadByRef.setChecked(prefs.getBoolean("thread_byref", true));
            etKeywords.setText(prefs.getString("global_keywords", null));
            swTestIab.setChecked(prefs.getBoolean("test_iab", false));

            tvProcessors.setText(getString(R.string.title_advanced_processors, Runtime.getRuntime().availableProcessors()));
            tvMemoryClass.setText(getString(R.string.title_advanced_memory_class,
                    class_mb + " MB",
                    class_large_mb + " MB",
                    Helper.humanReadableByteCount(mi.totalMem)));

            String android_id;
            try {
                android_id = Settings.Secure.getString(
                        getContext().getContentResolver(),
                        Settings.Secure.ANDROID_ID);
                if (android_id == null)
                    android_id = "<null>";
            } catch (Throwable ex) {
                Log.w(ex);
                android_id = "?";
            }
            tvAndroidId.setText(getString(R.string.title_advanced_android_id, android_id));

            tvFingerprint.setText(Helper.getFingerprint(getContext()));

            Integer cursorWindowSize = null;
            try {
                //Field fCursorWindowSize = android.database.CursorWindow.class.getDeclaredField("sDefaultCursorWindowSize");
                //fCursorWindowSize.setAccessible(true);
                //cursorWindowSize = fCursorWindowSize.getInt(null);
            } catch (Throwable ex) {
                Log.w(ex);
            }
            tvCursorWindow.setText(getString(R.string.title_advanced_cursor_window,
                    cursorWindowSize == null ? "?" : Helper.humanReadableByteCount(cursorWindowSize, false)));

            cardDebug.setVisibility(swDebug.isChecked() || BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        } catch (Throwable ex) {
            Log.e(ex);
        }
    }

    private void updateUsage() {
        if (!resumed)
            return;

        try {
            Log.i("Update usage");

            Bundle args = new Bundle();

            new SimpleTask<StorageData>() {
                @Override
                protected StorageData onExecute(Context context, Bundle args) {
                    StorageData data = new StorageData();
                    Runtime rt = Runtime.getRuntime();
                    data.hused = rt.totalMemory() - rt.freeMemory();
                    data.hmax = rt.maxMemory();
                    data.nheap = Debug.getNativeHeapAllocatedSize();
                    data.available = Helper.getAvailableStorageSpace();
                    data.total = Helper.getTotalStorageSpace();
                    data.used = Helper.getSizeUsed(context.getFilesDir());
                    data.cache_used = Helper.getSizeUsed(context.getCacheDir());
                    data.cache_quota = Helper.getCacheQuota(context);
                    return data;
                }

                @Override
                protected void onExecuted(Bundle args, StorageData data) {
                    tvMemoryUsage.setText(getString(R.string.title_advanced_memory_usage,
                            Helper.humanReadableByteCount(data.hused),
                            Helper.humanReadableByteCount(data.hmax),
                            Helper.humanReadableByteCount(data.nheap)));

                    tvStorageUsage.setText(getString(R.string.title_advanced_storage_usage,
                            Helper.humanReadableByteCount(data.total - data.available),
                            Helper.humanReadableByteCount(data.total),
                            Helper.humanReadableByteCount(data.used)));
                    tvCacheUsage.setText(getString(R.string.title_advanced_cache_usage,
                            Helper.humanReadableByteCount(data.cache_used),
                            Helper.humanReadableByteCount(data.cache_quota)));

                    getView().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            updateUsage();
                        }
                    }, 2500);
                }

                @Override
                protected void onException(Bundle args, Throwable ex) {
                    Log.e(ex);
                }
            }.execute(this, args, "usage");
        } catch (Throwable ex) {
            Log.e(ex);
        }
    }

    private void setLastCleanup(long time) {
        if (getContext() == null)
            return;

        java.text.DateFormat DTF = Helper.getDateTimeInstance(getContext());
        tvLastCleanup.setText(
                getString(R.string.title_advanced_last_cleanup,
                        time < 0 ? "-" : DTF.format(time)));
    }

    private void setContactInfo() {
        int[] stats = ContactInfo.getStats();
        tvContactInfo.setText(getString(R.string.title_advanced_contact_info, stats[0], stats[1]));
    }

    private void setSuffixes() {
        new SimpleTask<Integer>() {
            @Override
            protected void onPreExecute(Bundle args) {
                tvSuffixes.setText(getString(R.string.title_advanced_suffixes, -1));
            }

            @Override
            protected Integer onExecute(Context context, Bundle args) {
                return UriHelper.getSuffixCount(context);
            }

            @Override
            protected void onExecuted(Bundle args, Integer count) {
                tvSuffixes.setText(getString(R.string.title_advanced_suffixes, count));
            }

            @Override
            protected void onException(Bundle args, Throwable ex) {
                Log.w(ex);
                tvSuffixes.setText(ex.toString());
            }
        }.execute(this, new Bundle(), "suffixes");
    }

    private void setPermissionInfo() {
        new SimpleTask<Spanned>() {
            @Override
            protected void onPreExecute(Bundle args) {
                tvPermissions.setText(null);
            }

            @Override
            protected Spanned onExecute(Context context, Bundle args) throws Throwable {
                int start = 0;
                int dp24 = Helper.dp2pixels(getContext(), 24);
                SpannableStringBuilder ssb = new SpannableStringBuilderEx();
                PackageManager pm = getContext().getPackageManager();
                PackageInfo pi = pm.getPackageInfo(BuildConfig.APPLICATION_ID, PackageManager.GET_PERMISSIONS);
                for (int i = 0; i < pi.requestedPermissions.length; i++) {
                    boolean granted = ((pi.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0);

                    PermissionInfo info;
                    try {
                        info = pm.getPermissionInfo(pi.requestedPermissions[i], PackageManager.GET_META_DATA);
                    } catch (Throwable ex) {
                        info = new PermissionInfo();
                        info.name = pi.requestedPermissions[i];
                        if (!(ex instanceof PackageManager.NameNotFoundException))
                            info.group = ex.toString();
                    }

                    ssb.append(info.name).append('\n');
                    if (granted)
                        ssb.setSpan(new StyleSpan(Typeface.BOLD), start, ssb.length(), 0);
                    start = ssb.length();

                    if (info.group != null) {
                        ssb.append(info.group).append('\n');
                        ssb.setSpan(new IndentSpan(dp24), start, ssb.length(), 0);
                        start = ssb.length();
                    }

                    CharSequence description = info.loadDescription(pm);
                    if (description != null) {
                        ssb.append(description).append('\n');
                        ssb.setSpan(new IndentSpan(dp24), start, ssb.length(), 0);
                        ssb.setSpan(new RelativeSizeSpan(HtmlHelper.FONT_SMALL), start, ssb.length(), 0);
                        start = ssb.length();
                    }

                    if (info.protectionLevel != 0) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                            switch (info.getProtection()) {
                                case PermissionInfo.PROTECTION_DANGEROUS:
                                    ssb.append("dangerous ");
                                    break;
                                case PermissionInfo.PROTECTION_NORMAL:
                                    ssb.append("normal ");
                                    break;
                                case PermissionInfo.PROTECTION_SIGNATURE:
                                    ssb.append("signature ");
                                    break;
                                case PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM:
                                    ssb.append("signatureOrSystem ");
                                    break;
                            }

                        ssb.append(Integer.toHexString(info.protectionLevel));

                        if (info.flags != 0)
                            ssb.append(' ').append(Integer.toHexString(info.flags));

                        ssb.append('\n');
                        ssb.setSpan(new IndentSpan(dp24), start, ssb.length(), 0);
                        start = ssb.length();
                    }

                    ssb.append('\n');
                }

                return ssb;
            }

            @Override
            protected void onExecuted(Bundle args, Spanned permissions) {
                tvPermissions.setText(permissions);
            }

            @Override
            protected void onException(Bundle args, Throwable ex) {
                Log.w(ex);
                tvPermissions.setText(ex.toString());
            }
        }.execute(this, new Bundle(), "permissions");
    }

    private void onExportClassifier(Context context) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, "classifier.json");
        Helper.openAdvanced(context, intent);
        startActivityForResult(intent, REQUEST_CLASSIFIER);
    }

    private void onHandleExportClassifier(Intent intent) {
        Bundle args = new Bundle();
        args.putParcelable("uri", intent.getData());

        new SimpleTask<Void>() {
            @Override
            protected Void onExecute(Context context, Bundle args) throws Throwable {
                Uri uri = args.getParcelable("uri");

                ContentResolver resolver = context.getContentResolver();
                File file = MessageClassifier.getFile(context, false);
                try (OutputStream os = resolver.openOutputStream(uri)) {
                    try (InputStream is = new FileInputStream(file)) {
                        Helper.copy(is, os);
                    }
                }

                return null;
            }

            @Override
            protected void onExecuted(Bundle args, Void data) {
                ToastEx.makeText(getContext(), R.string.title_setup_exported, Toast.LENGTH_LONG).show();
            }

            @Override
            protected void onException(Bundle args, Throwable ex) {
                Log.unexpectedError(getParentFragmentManager(), ex);
            }
        }.execute(this, args, "classifier");
    }

    private static class StorageData {
        private long hused;
        private long hmax;
        private long nheap;
        private long available;
        private long total;
        private long used;
        private long cache_used;
        private long cache_quota;
    }
}
