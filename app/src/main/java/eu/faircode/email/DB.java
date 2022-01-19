package eu.faircode.email;

import static eu.faircode.email.ServiceAuthenticator.AUTH_TYPE_PASSWORD;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.preference.PreferenceManager;
import androidx.room.Database;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.getkeepsafe.relinker.ReLinker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;

import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory;
import io.requery.android.database.sqlite.SQLiteDatabase;

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

// https://developer.android.com/topic/libraries/architecture/room.html

@Database(
        version = 221,
        entities = {
                EntityIdentity.class,
                EntityAccount.class,
                EntityFolder.class,
                EntityMessage.class,
                EntityAttachment.class,
                EntityOperation.class,
                EntityContact.class,
                EntityCertificate.class,
                EntityAnswer.class,
                EntityRule.class,
                EntitySearch.class,
                EntityLog.class
        },
        views = {
                TupleAccountView.class,
                TupleIdentityView.class,
                TupleFolderView.class
        }
)

@TypeConverters({DB.Converters.class})
public abstract class DB extends RoomDatabase {
    public abstract DaoAccount account();

    public abstract DaoIdentity identity();

    public abstract DaoFolder folder();

    public abstract DaoMessage message();

    public abstract DaoAttachment attachment();

    public abstract DaoOperation operation();

    public abstract DaoContact contact();

    public abstract DaoCertificate certificate();

    public abstract DaoAnswer answer();

    public abstract DaoRule rule();

    public abstract DaoSearch search();

    public abstract DaoLog log();

    private static int sPid;
    private static Context sContext;
    private static DB sInstance;

    static final int DEFAULT_QUERY_THREADS = 4; // AndroidX default thread count: 4
    static final int DEFAULT_CACHE_SIZE = 10; // percentage of memory class

    private static final String DB_NAME = "fairemail";
    private static final int DB_CHECKPOINT = 1000; // requery/sqlite-android default

    private static final String[] DB_TABLES = new String[]{
            "identity", "account", "folder", "message", "attachment", "operation", "contact", "certificate", "answer", "rule", "search", "log"};

    private static final List<String> DB_PRAGMAS = Collections.unmodifiableList(Arrays.asList(
            "synchronous", "journal_mode",
            "wal_checkpoint", "wal_autocheckpoint", "journal_size_limit",
            "page_count", "page_size", "max_page_count", "freelist_count",
            "cache_size", "cache_spill",
            "soft_heap_limit", "hard_heap_limit", "mmap_size",
            "foreign_keys"
    ));

    @Override
    public void init(@NonNull DatabaseConfiguration configuration) {
        // https://www.sqlite.org/pragma.html#pragma_wal_autocheckpoint
        if (BuildConfig.DEBUG) {
            File dbfile = configuration.context.getDatabasePath(DB_NAME);
            if (dbfile.exists()) {
                try (SQLiteDatabase db = SQLiteDatabase.openDatabase(dbfile.getPath(), null, SQLiteDatabase.OPEN_READWRITE)) {
                    Log.i("Set PRAGMA wal_autocheckpoint=" + DB_CHECKPOINT);
                    try (Cursor cursor = db.rawQuery("PRAGMA wal_autocheckpoint=" + DB_CHECKPOINT + ";", null)) {
                        cursor.moveToNext(); // required
                    }
                }
            }
        }

        super.init(configuration);
    }

    static void setupViewInvalidation(Context context) {
        // This needs to be done on a foreground thread
        DB db = DB.getInstance(context);

        db.account().liveAccountView().observeForever(new Observer<List<TupleAccountView>>() {
            private List<TupleAccountView> last = null;

            @Override
            public void onChanged(List<TupleAccountView> accounts) {
                if (accounts == null)
                    accounts = new ArrayList<>();

                boolean changed = false;
                if (last == null || last.size() != accounts.size())
                    changed = true;
                else
                    for (int i = 0; i < accounts.size(); i++)
                        if (!accounts.get(i).equals(last.get(i))) {
                            changed = true;
                            last = accounts;
                        }

                if (changed) {
                    Log.i("Invalidating account view");
                    last = accounts;
                    db.getInvalidationTracker().notifyObserversByTableNames("message");
                }
            }
        });

        db.identity().liveIdentityView().observeForever(new Observer<List<TupleIdentityView>>() {
            private List<TupleIdentityView> last = null;

            @Override
            public void onChanged(List<TupleIdentityView> identities) {
                if (identities == null)
                    identities = new ArrayList<>();

                boolean changed = false;
                if (last == null || last.size() != identities.size())
                    changed = true;
                else
                    for (int i = 0; i < identities.size(); i++)
                        if (!identities.get(i).equals(last.get(i))) {
                            changed = true;
                            last = identities;
                        }

                if (changed) {
                    Log.i("Invalidating identity view");
                    last = identities;
                    db.getInvalidationTracker().notifyObserversByTableNames("message");
                }
            }
        });

        db.folder().liveFolderView().observeForever(new Observer<List<TupleFolderView>>() {
            private List<TupleFolderView> last = null;

            @Override
            public void onChanged(List<TupleFolderView> folders) {
                if (folders == null)
                    folders = new ArrayList<>();

                boolean changed = false;
                if (last == null || last.size() != folders.size())
                    changed = true;
                else
                    for (int i = 0; i < folders.size(); i++)
                        if (!folders.get(i).equals(last.get(i))) {
                            changed = true;
                            last = folders;
                        }

                if (changed) {
                    Log.i("Invalidating folder view");
                    last = folders;
                    db.getInvalidationTracker().notifyObserversByTableNames("account", "message");
                }
            }
        });
    }

    static void createEmergencyBackup(Context context) {
        Log.i("Creating emergency backup");
        try {
            DB db = DB.getInstance(context);

            JSONArray jaccounts = new JSONArray();
            List<EntityAccount> accounts = db.account().getAccounts();
            for (EntityAccount account : accounts) {
                JSONObject jaccount = account.toJSON();

                JSONArray jfolders = new JSONArray();
                List<EntityFolder> folders = db.folder().getFolders(account.id, false, true);
                for (EntityFolder folder : folders)
                    jfolders.put(folder.toJSON());
                jaccount.put("folders", jfolders);

                JSONArray jidentities = new JSONArray();
                List<EntityIdentity> identities = db.identity().getIdentities(account.id);
                for (EntityIdentity identity : identities)
                    jidentities.put(identity.toJSON());
                jaccount.put("identities", jidentities);

                jaccounts.put(jaccount);
            }

            File emergency = new File(context.getFilesDir(), "emergency.json");
            Helper.writeText(emergency, jaccounts.toString(2));
        } catch (Throwable ex) {
            Log.e(ex);
        }
    }

    private static void checkEmergencyBackup(Context context) {
        try {
            File dbfile = context.getDatabasePath(DB_NAME);
            if (dbfile.exists()) {
                Log.i("Emergency restore /dbfile");
                return;
            }

            File emergency = new File(context.getFilesDir(), "emergency.json");
            if (!emergency.exists()) {
                Log.i("Emergency restore /json");
                return;
            }

            DB db = DB.getInstance(context);
            if (db.account().getAccounts().size() > 0) {
                Log.e("Emergency restore /accounts");
                return;
            }

            Log.e("Emergency restore");

            String json = Helper.readText(emergency);
            JSONArray jaccounts = new JSONArray(json);
            for (int a = 0; a < jaccounts.length(); a++) {
                JSONObject jaccount = jaccounts.getJSONObject(a);
                EntityAccount account = EntityAccount.fromJSON(jaccount);
                account.created = new Date().getTime();
                account.id = db.account().insertAccount(account);

                JSONArray jfolders = jaccount.getJSONArray("folders");
                for (int f = 0; f < jfolders.length(); f++) {
                    EntityFolder folder = EntityFolder.fromJSON(jfolders.getJSONObject(f));
                    folder.account = account.id;
                    db.folder().insertFolder(folder);
                }

                JSONArray jidentities = jaccount.getJSONArray("identities");
                for (int i = 0; i < jidentities.length(); i++) {
                    EntityIdentity identity = EntityIdentity.fromJSON(jidentities.getJSONObject(i));
                    identity.account = account.id;
                    db.identity().insertIdentity(identity);
                }
            }
        } catch (Throwable ex) {
            Log.e(ex);
        }
    }

    public static synchronized DB getInstance(Context context) {
        int apid = android.os.Process.myPid();
        Context acontext = context.getApplicationContext();
        if (sInstance != null &&
                (sPid != apid || !Objects.equals(sContext, acontext)))
            try {
                Log.e("Orphan database instance pid=" + apid + "/" + sPid);
                sInstance = null;
            } catch (Throwable ex) {
                Log.e(ex);
            }
        sPid = apid;
        sContext = acontext;

        if (sInstance == null) {
            Log.i("Creating database instance pid=" + sPid);

            sInstance = migrate(sContext, getBuilder(sContext)).build();

            sInstance.getQueryExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    checkEmergencyBackup(sContext);
                }
            });

            try {
                Log.i("Disabling view invalidation");

                Field fmViewTables = InvalidationTracker.class.getDeclaredField("mViewTables");
                fmViewTables.setAccessible(true);

                Map<String, Set<String>> mViewTables = (Map) fmViewTables.get(sInstance.getInvalidationTracker());
                mViewTables.get("account_view").clear();
                mViewTables.get("identity_view").clear();
                mViewTables.get("folder_view").clear();

                Log.i("Disabled view invalidation");
            } catch (ReflectiveOperationException ex) {
                Log.e(ex);
            }

            sInstance.getInvalidationTracker().addObserver(new InvalidationTracker.Observer(DB_TABLES) {
                @Override
                public void onInvalidated(@NonNull Set<String> tables) {
                    Log.d("ROOM invalidated=" + TextUtils.join(",", tables));
                }
            });
        }

        return sInstance;
    }

    private static RoomDatabase.Builder<DB> getBuilder(Context context) {
        try {
            ReLinker.log(new ReLinker.Logger() {
                @Override
                public void log(String message) {
                    Log.i("Relinker: " + message);
                }
            }).loadLibrary(context, "sqlite3x");
        } catch (Throwable ex) {
            Log.e(ex);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int threads = prefs.getInt("query_threads", DEFAULT_QUERY_THREADS);
        boolean wal = prefs.getBoolean("wal", true);
        Log.i("DB query threads=" + threads + " wal=" + wal);
        ExecutorService executorQuery = Helper.getBackgroundExecutor(threads, "query");
        ExecutorService executorTransaction = Helper.getBackgroundExecutor(0, "transaction");

        return Room
                .databaseBuilder(context, DB.class, DB_NAME)
                .openHelperFactory(new RequerySQLiteOpenHelperFactory())
                .setQueryExecutor(executorQuery)
                .setTransactionExecutor(executorTransaction)
                .setJournalMode(wal ? JournalMode.WRITE_AHEAD_LOGGING : JournalMode.TRUNCATE) // using the latest sqlite
                .addCallback(new Callback() {
                    @Override
                    public void onOpen(@NonNull SupportSQLiteDatabase db) {
                        Map<String, String> crumb = new HashMap<>();
                        crumb.put("version", Integer.toString(db.getVersion()));
                        crumb.put("WAL", Boolean.toString(db.isWriteAheadLoggingEnabled()));
                        Log.breadcrumb("Database", crumb);

                        // https://www.sqlite.org/pragma.html#pragma_cache_size
                        Integer cache_size = getCacheSizeKb(context);
                        if (cache_size != null) {
                            cache_size = -cache_size; // kibibytes
                            Log.i("Set PRAGMA cache_size=" + cache_size);
                            try (Cursor cursor = db.query("PRAGMA cache_size=" + cache_size + ";", null)) {
                                cursor.moveToNext(); // required
                            }
                        }

                        // Prevent long running operations from getting an exclusive lock
                        // https://www.sqlite.org/pragma.html#pragma_cache_spill
                        Log.i("Set PRAGMA cache_spill=0");
                        try (Cursor cursor = db.query("PRAGMA cache_spill=0;", null)) {
                            cursor.moveToNext(); // required
                        }

                        // https://www.sqlite.org/pragma.html
                        for (String pragma : DB_PRAGMAS)
                            try (Cursor cursor = db.query("PRAGMA " + pragma + ";")) {
                                Log.i("Get PRAGMA " + pragma + "=" + (cursor.moveToNext() ? cursor.getString(0) : "?"));
                            }

                        if (BuildConfig.DEBUG && false) {
                            db.execSQL("DROP TRIGGER IF EXISTS `attachment_insert`");
                            db.execSQL("DROP TRIGGER IF EXISTS `attachment_delete`");
                        }
                        createTriggers(db);
                    }
                });
    }

    static Integer getCacheSizeKb(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            int sqlite_cache = prefs.getInt("sqlite_cache", DEFAULT_CACHE_SIZE);

            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            int class_mb = am.getMemoryClass();
            int cache_size = sqlite_cache * class_mb * 1024 / 100;

            return (cache_size > 2000 ? cache_size : null);
        } catch (Throwable ex) {
            Log.e(ex);
            return null;
        }
    }

    private static void createTriggers(@NonNull SupportSQLiteDatabase db) {
        List<String> image = new ArrayList<>();
        for (String img : ImageHelper.IMAGE_TYPES)
            image.add("'" + img + "'");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            for (String img : ImageHelper.IMAGE_TYPES8)
                image.add("'" + img + "'");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            for (String img : ImageHelper.IMAGE_TYPES12)
                image.add("'" + img + "'");
        String images = TextUtils.join(",", image);

        db.execSQL("CREATE TRIGGER IF NOT EXISTS attachment_insert" +
                " AFTER INSERT ON attachment" +
                " BEGIN" +
                "  UPDATE message SET attachments = attachments + 1" +
                "  WHERE message.id = NEW.message" +
                "  AND NEW.encryption IS NULL" +
                "  AND NOT ((NEW.disposition = 'inline' OR NEW.cid IS NOT NULL) AND NEW.type IN (" + images + "));" +
                " END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS attachment_delete" +
                " AFTER DELETE ON attachment" +
                " BEGIN" +
                "  UPDATE message SET attachments = attachments - 1" +
                "  WHERE message.id = OLD.message" +
                "  AND OLD.encryption IS NULL" +
                "  AND NOT ((OLD.disposition = 'inline' OR OLD.cid IS NOT NULL) AND OLD.type IN (" + images + "));" +
                " END");
    }

    private static void logMigration(int startVersion, int endVersion) {
        Map<String, String> crumb = new HashMap<>();
        crumb.put("startVersion", Integer.toString(startVersion));
        crumb.put("endVersion", Integer.toString(endVersion));
        Log.breadcrumb("Migration", crumb);
    }

    private static RoomDatabase.Builder<DB> migrate(final Context context, RoomDatabase.Builder<DB> builder) {
        // https://www.sqlite.org/lang_altertable.html
        return builder
                .addMigrations(new Migration(1, 2) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` RENAME COLUMN `after` TO `sync_days`");
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `keep_days` INTEGER NOT NULL DEFAULT 30");
                        db.execSQL("UPDATE `folder` SET keep_days = sync_days");
                    }
                })
                .addMigrations(new Migration(2, 3) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `signature` TEXT");
                        db.execSQL("UPDATE `identity` SET signature =" +
                                " (SELECT account.signature FROM account WHERE account.id = identity.account)");
                    }
                })
                .addMigrations(new Migration(3, 4) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `forwarding` INTEGER" +
                                " REFERENCES `message`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL");
                        db.execSQL("CREATE INDEX `index_message_forwarding` ON `message` (`forwarding`)");
                    }
                })
                .addMigrations(new Migration(4, 5) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `last_connected` INTEGER");
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `last_attempt` INTEGER");
                    }
                })
                .addMigrations(new Migration(5, 6) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `notify` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(6, 7) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `answered` INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `ui_answered` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(7, 8) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `keywords` TEXT");
                    }
                })
                .addMigrations(new Migration(8, 9) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `keywords` TEXT");
                    }
                })
                .addMigrations(new Migration(9, 10) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `ui_browsed` INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("CREATE INDEX `index_message_ui_browsed` ON `message` (`ui_browsed`)");
                    }
                })
                .addMigrations(new Migration(10, 11) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `operation` ADD COLUMN `error` TEXT");
                    }
                })
                .addMigrations(new Migration(11, 12) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("DROP INDEX `index_operation_folder`");
                        db.execSQL("DROP INDEX `index_operation_message`");
                        db.execSQL("DROP TABLE `operation`");
                        db.execSQL("CREATE TABLE `operation`" +
                                " (`id` INTEGER PRIMARY KEY AUTOINCREMENT" +
                                ", `folder` INTEGER NOT NULL" +
                                ", `message` INTEGER" +
                                ", `name` TEXT NOT NULL" +
                                ", `args` TEXT NOT NULL" +
                                ", `created` INTEGER NOT NULL" +
                                ", `error` TEXT" +
                                ", FOREIGN KEY(`folder`) REFERENCES `folder`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
                                ", FOREIGN KEY(`message`) REFERENCES `message`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
                        db.execSQL("CREATE INDEX `index_operation_folder` ON `operation` (`folder`)");
                        db.execSQL("CREATE INDEX `index_operation_message` ON `operation` (`message`)");
                    }
                })
                .addMigrations(new Migration(12, 13) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("CREATE INDEX `index_message_ui_flagged` ON `message` (`ui_flagged`)");
                    }
                })
                .addMigrations(new Migration(13, 14) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `level` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(14, 15) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `sync_state` TEXT");
                    }
                })
                .addMigrations(new Migration(15, 16) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `poll` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(16, 17) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("DELETE FROM `message` WHERE ui_found");
                        db.execSQL("DROP INDEX `index_message_folder_uid_ui_found`");
                        db.execSQL("DROP INDEX `index_message_msgid_folder_ui_found`");
                        db.execSQL("CREATE UNIQUE INDEX `index_message_folder_uid` ON `message` (`folder`, `uid`)");
                        db.execSQL("CREATE UNIQUE INDEX `index_message_msgid_folder` ON `message` (`msgid`, `folder`)");
                    }
                })
                .addMigrations(new Migration(17, 18) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `tbd` INTEGER");
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `tbd` INTEGER");
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `tbd` INTEGER");
                    }
                })
                .addMigrations(new Migration(18, 19) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `delivery_receipt` INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `read_receipt` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(19, 20) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `notify` INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("UPDATE `folder` SET notify = unified");
                    }
                })
                .addMigrations(new Migration(20, 21) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `display` TEXT");
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `bcc` TEXT");
                    }
                })
                .addMigrations(new Migration(21, 22) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `initialize` INTEGER NOT NULL DEFAULT 1");
                        db.execSQL("UPDATE `folder` SET sync_days = 1");
                    }
                })
                .addMigrations(new Migration(22, 23) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `download` INTEGER NOT NULL DEFAULT 1");
                    }
                })
                .addMigrations(new Migration(23, 24) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `tbc` INTEGER");
                    }
                })
                .addMigrations(new Migration(24, 25) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `prefix` TEXT");
                    }
                })
                .addMigrations(new Migration(25, 26) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                        int browse = (prefs.getBoolean("browse", true) ? 1 : 0);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `browse` INTEGER NOT NULL DEFAULT " + browse);
                    }
                })
                .addMigrations(new Migration(26, 27) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `sender` TEXT");
                        db.execSQL("CREATE INDEX `index_message_sender` ON `message` (`sender`)");
                    }
                })
                .addMigrations(new Migration(27, 28) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);

                        try (Cursor cursor = db.query("SELECT `id`, `from` FROM message")) {
                            while (cursor.moveToNext())
                                try {
                                    long id = cursor.getLong(0);
                                    String json = cursor.getString(1);
                                    Address[] from = Converters.decodeAddresses(json);
                                    String sender = MessageHelper.getSortKey(from);
                                    db.execSQL(
                                            "UPDATE message SET sender = ? WHERE id = ?",
                                            new Object[]{sender, id});
                                } catch (Throwable ex) {
                                    Log.e(ex);
                                }

                        }
                    }
                })
                .addMigrations(new Migration(28, 29) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `last_sync` INTEGER");
                    }
                })
                .addMigrations(new Migration(29, 30) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `attachment` ADD COLUMN `encryption` INTEGER");
                        db.execSQL("UPDATE attachment SET encryption = " + EntityAttachment.PGP_MESSAGE + " where name = 'encrypted.asc'");
                    }
                })
                .addMigrations(new Migration(30, 31) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `attachment` ADD COLUMN `disposition` TEXT");
                    }
                })
                .addMigrations(new Migration(31, 32) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `ui_snoozed` INTEGER");
                        db.execSQL("CREATE INDEX `index_message_ui_snoozed` ON `message` (`ui_snoozed`)");
                    }
                })
                .addMigrations(new Migration(32, 33) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `realm` TEXT");
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `realm` TEXT");
                    }
                })
                .addMigrations(new Migration(33, 34) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `raw` INTEGER");
                    }
                })
                .addMigrations(new Migration(34, 35) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `attachment` ADD COLUMN `error` TEXT");
                    }
                })
                .addMigrations(new Migration(35, 36) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `warning` TEXT");
                    }
                })
                .addMigrations(new Migration(36, 37) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("CREATE TABLE `rule`" +
                                " (`id` INTEGER PRIMARY KEY AUTOINCREMENT," +
                                " `folder` INTEGER NOT NULL," +
                                " `name` TEXT NOT NULL," +
                                " `order` INTEGER NOT NULL," +
                                " `enabled` INTEGER NOT NULL," +
                                " `condition` TEXT NOT NULL," +
                                " `action` TEXT NOT NULL," +
                                " FOREIGN KEY(`folder`) REFERENCES `folder`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
                        db.execSQL("CREATE INDEX `index_rule_folder` ON `rule` (`folder`)");
                        db.execSQL("CREATE INDEX `index_rule_order` ON `rule` (`order`)");
                    }
                })
                .addMigrations(new Migration(37, 38) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `rule` ADD COLUMN `stop` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(38, 39) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `swipe_left` INTEGER");
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `swipe_right` INTEGER");
                    }
                })
                .addMigrations(new Migration(39, 40) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `last_connected` INTEGER");
                    }
                })
                .addMigrations(new Migration(40, 41) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `flags` TEXT");
                    }
                })
                .addMigrations(new Migration(41, 42) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `plain_only` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(42, 43) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `pop` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(43, 44) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("CREATE TABLE IF NOT EXISTS `contact`" +
                                " (`id` INTEGER PRIMARY KEY AUTOINCREMENT" +
                                ", `type` INTEGER NOT NULL" +
                                ", `email` TEXT NOT NULL" +
                                ", `name` TEXT)");
                        db.execSQL("CREATE UNIQUE INDEX `index_contact_email_type` ON `contact` (`email`, `type`)");
                        db.execSQL("CREATE INDEX `index_contact_name_type` ON `contact` (`name`, `type`)");
                    }
                })
                .addMigrations(new Migration(44, 45) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `ondemand` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(45, 46) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `encrypt` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(46, 47) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `use_ip` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(47, 48) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("UPDATE `identity` SET use_ip = 1");
                    }
                })
                .addMigrations(new Migration(48, 49) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("CREATE INDEX `index_operation_name` ON `operation` (`name`)");
                    }
                })
                .addMigrations(new Migration(49, 50) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("DROP INDEX `index_message_replying`");
                        db.execSQL("DROP INDEX `index_message_forwarding`");
                        db.execSQL("CREATE INDEX `index_message_subject` ON `message` (`subject`)");
                    }
                })
                .addMigrations(new Migration(50, 51) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("DELETE FROM operation WHERE name = 'wait'");
                    }
                })
                .addMigrations(new Migration(51, 52) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `total` INTEGER");
                    }
                })
                .addMigrations(new Migration(52, 53) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `operation` ADD COLUMN `account` INTEGER");
                    }
                })
                .addMigrations(new Migration(53, 54) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        File folder = new File(context.getFilesDir(), "attachments");
                        File[] attachments = folder.listFiles();
                        if (attachments != null)
                            for (File source : attachments) {
                                long id = Long.parseLong(source.getName().split("\\.")[0]);
                                Cursor cursor = null;
                                try {
                                    cursor = db.query("SELECT name FROM attachment WHERE id = ?", new Object[]{id});
                                    if (cursor != null && cursor.moveToNext()) {
                                        String name = cursor.getString(0);
                                        if (!TextUtils.isEmpty(name)) {
                                            File target = new File(folder, id + "." + Helper.sanitizeFilename(name));
                                            if (source.renameTo(target))
                                                Log.i("Renamed attachment=" + target.getName());
                                            else {
                                                Log.i("Unavailable attachment=" + source.getName());
                                                db.execSQL("UPDATE attachment SET available = 0 WHERE id = ?", new Object[]{id});
                                            }
                                        }
                                    }
                                } catch (Throwable ex) {
                                    if (cursor != null)
                                        cursor.close();
                                }
                            }
                    }
                })
                .addMigrations(new Migration(54, 55) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `contact` ADD COLUMN `avatar` TEXT");
                        db.execSQL("ALTER TABLE `contact` ADD COLUMN `times_contacted` INTEGER NOT NULL DEFAULT 1");
                        db.execSQL("ALTER TABLE `contact` ADD COLUMN `last_contacted` INTEGER");
                    }
                })
                .addMigrations(new Migration(55, 56) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `contact` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(56, 57) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("CREATE INDEX `index_contact_times_contacted` ON `contact` (`times_contacted`)");
                        db.execSQL("CREATE INDEX `index_contact_last_contacted` ON `contact` (`last_contacted`)");
                        db.execSQL("CREATE INDEX `index_contact_favorite` ON `contact` (`favorite`)");
                    }
                })
                .addMigrations(new Migration(57, 58) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("DROP TABLE `contact`");
                        db.execSQL("CREATE TABLE IF NOT EXISTS `contact`" +
                                " (`id` INTEGER PRIMARY KEY AUTOINCREMENT" +
                                ", `account` INTEGER NOT NULL" +
                                ", `type` INTEGER NOT NULL" +
                                ", `email` TEXT NOT NULL" +
                                ", `name` TEXT, `avatar` TEXT" +
                                ", `times_contacted` INTEGER NOT NULL" +
                                ", `first_contacted` INTEGER NOT NULL" +
                                ", `last_contacted` INTEGER NOT NULL" +
                                ", `state` INTEGER NOT NULL" +
                                ", FOREIGN KEY(`account`) REFERENCES `account`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                        db.execSQL("CREATE UNIQUE INDEX `index_contact_account_type_email` ON `contact` (`account`, `type`, `email`)");
                        db.execSQL("CREATE  INDEX `index_contact_email` ON `contact` (`email`)");
                        db.execSQL("CREATE  INDEX `index_contact_name` ON `contact` (`name`)");
                        db.execSQL("CREATE  INDEX `index_contact_times_contacted` ON `contact` (`times_contacted`)");
                        db.execSQL("CREATE  INDEX `index_contact_last_contacted` ON `contact` (`last_contacted`)");
                        db.execSQL("CREATE  INDEX `index_contact_state` ON `contact` (`state`)");
                    }
                })
                .addMigrations(new Migration(58, 59) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("CREATE  INDEX `index_contact_avatar` ON `contact` (`avatar`)");
                    }
                })
                .addMigrations(new Migration(59, 60) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `parent` INTEGER");
                    }
                })
                .addMigrations(new Migration(60, 61) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `collapsed` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(61, 62) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `warning` TEXT");
                    }
                })
                .addMigrations(new Migration(62, 63) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("DROP INDEX index_message_msgid_folder");
                        db.execSQL("CREATE INDEX `index_message_msgid` ON `message` (`msgid`)");
                    }
                })
                .addMigrations(new Migration(63, 64) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `dkim` INTEGER");
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `spf` INTEGER");
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `dmarc` INTEGER");
                    }
                })
                .addMigrations(new Migration(64, 65) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `sender_extra` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(65, 66) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `receipt_request` INTEGER");
                    }
                })
                .addMigrations(new Migration(66, 67) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `revision` INTEGER");
                    }
                })
                .addMigrations(new Migration(67, 68) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `revisions` INTEGER");
                        db.execSQL("UPDATE message SET revisions = revision");
                    }
                })
                .addMigrations(new Migration(68, 69) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `receipt_to` TEXT");
                    }
                })
                .addMigrations(new Migration(69, 70) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("UPDATE message SET uid = NULL WHERE uid < 0");
                    }
                })
                .addMigrations(new Migration(70, 71) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `answer` ADD COLUMN `hide` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(71, 72) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `list_post` TEXT");
                    }
                })
                .addMigrations(new Migration(72, 73) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `order` INTEGER");
                    }
                })
                .addMigrations(new Migration(73, 74) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `subscribed` INTEGER");
                    }
                })
                .addMigrations(new Migration(74, 75) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `navigation` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(75, 76) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `order` INTEGER");
                    }
                })
                .addMigrations(new Migration(76, 77) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `read_only` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(77, 78) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `auto_delete` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(78, 79) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `plain_only` INTEGER");
                    }
                })
                .addMigrations(new Migration(79, 80) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("DROP INDEX index_attachment_message_cid");
                        db.execSQL("CREATE INDEX `index_attachment_message_cid` ON `attachment` (`message`, `cid`)");
                    }
                })
                .addMigrations(new Migration(80, 81) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `operation` ADD COLUMN `state` TEXT");
                    }
                })
                .addMigrations(new Migration(81, 82) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("CREATE INDEX `index_operation_account` ON `operation` (`account`)");
                        db.execSQL("CREATE INDEX `index_operation_state` ON `operation` (`state`)");
                    }
                })
                .addMigrations(new Migration(82, 83) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `color` INTEGER");
                    }
                })
                .addMigrations(new Migration(83, 84) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("UPDATE attachment SET disposition = lower(disposition) WHERE NOT disposition IS NULL");
                    }
                })
                .addMigrations(new Migration(84, 85) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("UPDATE attachment SET size = NULL WHERE size = 0");
                    }
                })
                .addMigrations(new Migration(85, 86) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("CREATE VIEW `folderview` AS SELECT id, account, name, type, display, unified FROM folder");
                    }
                })
                .addMigrations(new Migration(86, 87) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("DROP VIEW IF EXISTS `folderview`");
                    }
                })
                .addMigrations(new Migration(87, 88) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `partial_fetch` INTEGER NOT NULL DEFAULT 1");
                    }
                })
                .addMigrations(new Migration(88, 89) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `separator` INTEGER");
                    }
                })
                .addMigrations(new Migration(89, 90) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `notifying` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(90, 91) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `selectable` INTEGER NOT NULL DEFAULT 1");
                    }
                })
                .addMigrations(new Migration(91, 92) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("UPDATE `account` SET poll_interval = 24");
                    }
                })
                .addMigrations(new Migration(92, 93) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `mx` INTEGER");
                    }
                })
                .addMigrations(new Migration(93, 94) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `encrypt` INTEGER");
                    }
                })
                .addMigrations(new Migration(94, 95) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `sign_key` INTEGER");
                    }
                })
                .addMigrations(new Migration(95, 96) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `attachments` INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("UPDATE message SET attachments =" +
                                " (SELECT COUNT(attachment.id) FROM attachment WHERE attachment.message = message.id)");
                    }
                })
                .addMigrations(new Migration(96, 97) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `uidv` INTEGER");
                    }
                })
                .addMigrations(new Migration(97, 98) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `rename` TEXT");
                    }
                })
                .addMigrations(new Migration(98, 99) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `signature` INTEGER NOT NULL DEFAULT 1");
                    }
                })
                .addMigrations(new Migration(99, 100) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `unsubscribe` TEXT");
                    }
                })
                .addMigrations(new Migration(100, 101) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `sender_extra_regex` TEXT");
                    }
                })
                .addMigrations(new Migration(101, 102) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `auto_seen` INTEGER NOT NULL DEFAULT 1");
                    }
                })
                .addMigrations(new Migration(102, 103) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("UPDATE `account` SET browse = 1 WHERE pop = 1");
                    }
                })
                .addMigrations(new Migration(103, 104) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("UPDATE `message` SET ui_hide = 1 WHERE ui_hide <> 0");
                    }
                })
                .addMigrations(new Migration(104, 105) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `priority` INTEGER");
                    }
                })
                .addMigrations(new Migration(105, 106) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `total` INTEGER");
                        db.execSQL("UPDATE `message` SET total = size");
                    }
                })
                .addMigrations(new Migration(106, 107) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `receipt` INTEGER");
                    }
                })
                .addMigrations(new Migration(107, 108) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `color` INTEGER");
                    }
                })
                .addMigrations(new Migration(108, 109) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `ignore_size` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(109, 110) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `ui_busy` INTEGER");
                    }
                })
                .addMigrations(new Migration(110, 111) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `rule` ADD COLUMN `applied` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(111, 112) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `move_to` INTEGER");
                    }
                })
                .addMigrations(new Migration(112, 113) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("CREATE TABLE IF NOT EXISTS `revision`" +
                                " (`id` INTEGER PRIMARY KEY AUTOINCREMENT" +
                                ", `message` INTEGER NOT NULL" +
                                ", `sequence` INTEGER NOT NULL" +
                                ", `reference` INTEGER NOT NULL" +
                                ", FOREIGN KEY(`message`) REFERENCES `message`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_revision_message` ON `revision` (`message`)");
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_revision_message_sequence` ON `revision` (`message`, `sequence`)");
                    }

                })
                .addMigrations(new Migration(113, 114) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("UPDATE message SET encrypt = 1 WHERE id IN " +
                                "(SELECT DISTINCT message FROM attachment" +
                                " WHERE encryption = " + EntityAttachment.PGP_MESSAGE + ")");
                    }
                })
                .addMigrations(new Migration(114, 115) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("DROP TABLE revision");
                    }
                })
                .addMigrations(new Migration(115, 116) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `use_date` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(116, 117) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("CREATE TABLE IF NOT EXISTS `certificate`" +
                                " (`id` INTEGER PRIMARY KEY AUTOINCREMENT" +
                                ", `subject` TEXT NOT NULL" +
                                ", `email` TEXT" +
                                ", `data` TEXT NOT NULL)");
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_certificate_subject` ON `certificate` (`subject`)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_certificate_email` ON `certificate` (`email`)");
                    }
                })
                .addMigrations(new Migration(117, 118) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("DROP TABLE IF EXISTS `certificate`");
                        db.execSQL("CREATE TABLE IF NOT EXISTS `certificate`" +
                                " (`id` INTEGER PRIMARY KEY AUTOINCREMENT" +
                                ", `fingerprint` TEXT NOT NULL" +
                                ", `email` TEXT NOT NULL" +
                                ", `subject` TEXT" +
                                ", `data` TEXT NOT NULL)");
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_certificate_fingerprint_email` ON `certificate` (`fingerprint`, `email`)");
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_certificate_email` ON `certificate` (`email`)");
                    }
                })
                .addMigrations(new Migration(118, 119) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `sign_key_alias` TEXT");
                    }
                })
                .addMigrations(new Migration(119, 120) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `certificate` ADD COLUMN `after` INTEGER");
                        db.execSQL("ALTER TABLE `certificate` ADD COLUMN `before` INTEGER");
                    }
                })
                .addMigrations(new Migration(120, 121) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("UPDATE `account` SET ondemand = 0");
                    }
                })
                .addMigrations(new Migration(121, 122) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("UPDATE `message` SET raw = NULL");

                        File[] raws = new File(context.getFilesDir(), "raw").listFiles();
                        if (raws != null)
                            for (File file : raws)
                                file.delete();
                    }
                })
                .addMigrations(new Migration(122, 123) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `fingerprint` TEXT");
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `fingerprint` TEXT");
                    }
                })
                .addMigrations(new Migration(123, 124) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `provider` TEXT");
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `provider` TEXT");
                    }
                })
                .addMigrations(new Migration(124, 125) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                        int previous_version = prefs.getInt("previous_version", -1);
                        if (previous_version <= 848 && Helper.isPlayStoreInstall()) {
                            // JavaMail didn't check server certificates
                            db.execSQL("UPDATE account SET insecure = 1 WHERE auth_type = " + AUTH_TYPE_PASSWORD);
                            db.execSQL("UPDATE identity SET insecure = 1 WHERE auth_type = " + AUTH_TYPE_PASSWORD);
                        }
                    }
                })
                .addMigrations(new Migration(125, 126) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `autocrypt` TEXT");
                    }
                })
                .addMigrations(new Migration(126, 127) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `keep_alive_ok` INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `keep_alive_failed` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(127, 128) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `quota_usage` INTEGER");
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `quota_limit` INTEGER");
                    }
                })
                .addMigrations(new Migration(128, 129) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `poll_exempted` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(129, 130) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `fts` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(130, 131) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `answer` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(131, 132) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `ui_encrypt` INTEGER");
                        db.execSQL("UPDATE `message` SET `ui_encrypt` = `encrypt`");
                    }
                })
                .addMigrations(new Migration(132, 133) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `leave_on_server` INTEGER NOT NULL DEFAULT 1");
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `leave_on_device` INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("UPDATE `account` SET `leave_on_server` = `browse` WHERE `pop` = " + EntityAccount.TYPE_POP);
                    }
                })
                .addMigrations(new Migration(133, 134) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("DROP TRIGGER IF EXISTS `attachment_insert`");
                        db.execSQL("DROP TRIGGER IF EXISTS `attachment_delete`");
                        createTriggers(db);
                    }
                })
                .addMigrations(new Migration(134, 135) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("CREATE VIEW IF NOT EXISTS `account_view` AS " +
                                TupleAccountView.query.replace(", category", ""));
                        db.execSQL("CREATE VIEW IF NOT EXISTS `identity_view` AS " + TupleIdentityView.query);
                        db.execSQL("CREATE VIEW IF NOT EXISTS `folder_view` AS " + TupleFolderView.query);
                    }
                })
                .addMigrations(new Migration(135, 136) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `certificate` ADD COLUMN `intermediate` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(136, 137) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `submitter` TEXT");
                    }
                })
                .addMigrations(new Migration(137, 138) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `importance` INTEGER");
                    }
                })
                .addMigrations(new Migration(138, 139) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("UPDATE `message` SET fts = 0");
                    }
                })
                .addMigrations(new Migration(139, 140) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("UPDATE `message` SET fts = 0");
                    }
                })
                .addMigrations(new Migration(140, 141) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `certificate` INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `certificate` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(141, 142) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `certificate_alias` TEXT");
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `certificate_alias` TEXT");
                    }
                })
                .addMigrations(new Migration(142, 143) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `operation` ADD COLUMN `tries` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(143, 144) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `inferiors` INTEGER NOT NULL DEFAULT 1");
                    }
                })
                .addMigrations(new Migration(144, 145) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `poll_factor` INTEGER NOT NULL DEFAULT 1");
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `poll_count` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(145, 146) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `max_messages` INTEGER");
                    }
                })
                .addMigrations(new Migration(146, 147) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `thread` INTEGER");
                    }
                })
                .addMigrations(new Migration(147, 148) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `cc` TEXT");
                    }
                })
                .addMigrations(new Migration(148, 149) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `leave_deleted` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(149, 150) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `language` TEXT");
                    }
                })
                .addMigrations(new Migration(150, 151) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `keep_alive_succeeded` INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("UPDATE account SET keep_alive_failed = 0, keep_alive_ok = 0");
                    }
                })
                .addMigrations(new Migration(151, 152) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `hash` TEXT");
                    }
                })
                .addMigrations(new Migration(152, 153) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `unicode` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(153, 154) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `ehlo` TEXT");
                    }
                })
                .addMigrations(new Migration(154, 155) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("UPDATE `folder` SET `poll` = 1 WHERE `synchronize` = 0");
                    }
                })
                .addMigrations(new Migration(155, 156) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_inreplyto` ON `message` (`inreplyto`)");
                    }
                })
                .addMigrations(new Migration(156, 157) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `wasforwardedfrom` TEXT");
                    }
                })
                .addMigrations(new Migration(157, 158) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `uidl` TEXT");
                    }
                })
                .addMigrations(new Migration(158, 159) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `ui_unsnoozed` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(159, 160) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("DROP TRIGGER attachment_insert");
                        db.execSQL("DROP TRIGGER attachment_delete");
                        createTriggers(db);
                    }
                })
                .addMigrations(new Migration(160, 161) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                        String encrypt_method = prefs.getString("default_encrypt_method", "pgp");
                        db.execSQL("UPDATE identity SET encrypt = " + ("pgp".equals(encrypt_method) ? 0 : 1));
                        prefs.edit().remove("default_encrypt_method").apply();
                    }
                })
                .addMigrations(new Migration(161, 162) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `verified` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(162, 163) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("DROP TRIGGER attachment_insert");
                        db.execSQL("DROP TRIGGER attachment_delete");
                        createTriggers(db);
                    }
                })
                .addMigrations(new Migration(163, 164) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("DROP TRIGGER attachment_insert");
                        db.execSQL("DROP TRIGGER attachment_delete");
                        createTriggers(db);
                    }
                })
                .addMigrations(new Migration(164, 165) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachment_message_type` ON `attachment` (`message`, `type`)");
                    }
                })
                .addMigrations(new Migration(165, 166) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        db.execSQL("DROP INDEX `index_attachment_message_type`");
                    }
                })
                .addMigrations(new Migration(166, 167) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `labels` TEXT");
                    }
                })
                .addMigrations(new Migration(167, 168) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `self` INTEGER NOT NULL DEFAULT 1");
                    }
                })
                .addMigrations(new Migration(168, 169) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `max_size` INTEGER");
                    }
                })
                .addMigrations(new Migration(169, 170) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `max_size` INTEGER");
                    }
                })
                .addMigrations(new Migration(170, 171) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("DROP TRIGGER IF EXISTS `attachment_insert`");
                        db.execSQL("DROP TRIGGER IF EXISTS `attachment_delete`");
                        createTriggers(db);
                    }
                })
                .addMigrations(new Migration(171, 172) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `use_received` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(172, 173) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `attachment` ADD COLUMN `subsequence` INTEGER");
                        db.execSQL("DROP INDEX `index_attachment_message_sequence`");
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attachment_message_sequence_subsequence` ON `attachment` (`message`, `sequence`, `subsequence`)");
                    }
                })
                .addMigrations(new Migration(173, 174) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `answer` ADD COLUMN `group` TEXT");
                    }
                })
                .addMigrations(new Migration(174, 175) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `answer` ADD COLUMN `standard` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(175, 176) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `auto_submitted` INTEGER");
                    }
                })
                .addMigrations(new Migration(176, 177) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `backoff_until` INTEGER");
                    }
                })
                .addMigrations(new Migration(177, 178) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("UPDATE folder" +
                                " SET poll = 1" +
                                " WHERE type <> '" + EntityFolder.INBOX + "'" +
                                " AND account IN" +
                                "  (SELECT id FROM account" +
                                "   WHERE host IN ('imap.arcor.de'))");
                    }
                })
                .addMigrations(new Migration(178, 179) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `local` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(179, 180) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `reply_domain` INTEGER");
                    }
                })
                .addMigrations(new Migration(180, 181) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `rule` ADD COLUMN `last_applied` INTEGER");
                    }
                })
                .addMigrations(new Migration(181, 182) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `auto_classify` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(182, 183) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `auto_classified` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(183, 184) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `auto_classify_source` INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE `folder` RENAME COLUMN `auto_classify` TO 'auto_classify_target'");
                        db.execSQL("UPDATE `folder`" +
                                " SET auto_classify_source = 1" +
                                " WHERE (SELECT pop FROM account WHERE id = folder.account) = " + EntityAccount.TYPE_IMAP +
                                " AND (auto_classify_target" +
                                " OR type = '" + EntityFolder.INBOX + "'" +
                                " OR type = '" + EntityFolder.JUNK + "')");
                    }
                })
                .addMigrations(new Migration(184, 185) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `notes` TEXT");
                    }
                })
                .addMigrations(new Migration(185, 186) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `return_path` TEXT");
                    }
                })
                .addMigrations(new Migration(186, 187) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `ui_deleted` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(187, 188) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `ui_silent` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(188, 189) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `sign_default` INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `encrypt_default` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(189, 190) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `selected_count` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(190, 191) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `selected_last` INTEGER NOT NULL DEFAULT 0");
                    }
                })
                .addMigrations(new Migration(191, 192) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `modseq` INTEGER");
                    }
                })
                .addMigrations(new Migration(192, 193) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `notes_color` INTEGER");
                    }
                })
                .addMigrations(new Migration(193, 194) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `sender_extra_name` INTEGER NOT NULL DEFAULT 0");
                    }
                }).addMigrations(new Migration(194, 195) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `answer` ADD COLUMN `receipt` INTEGER NOT NULL DEFAULT 0");
                    }
                }).addMigrations(new Migration(195, 196) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `identity` ADD COLUMN `internal` TEXT");
                    }
                }).addMigrations(new Migration(196, 197) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `answer` ADD COLUMN `applied` INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE `answer` ADD COLUMN `last_applied` INTEGER");
                    }
                }).addMigrations(new Migration(197, 198) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `show_images` INTEGER NOT NULL DEFAULT 0");
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `show_full` INTEGER NOT NULL DEFAULT 0");
                    }
                }).addMigrations(new Migration(198, 199) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `capability_idle` INTEGER");
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `capability_utf8` INTEGER");
                    }
                }).addMigrations(new Migration(199, 200) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `blocklist` INTEGER");
                    }
                }).addMigrations(new Migration(200, 201) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `bimi_selector` TEXT");
                    }
                }).addMigrations(new Migration(201, 202) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("UPDATE identity SET use_ip = 0 WHERE host = 'smtp.office365.com'");
                    }
                }).addMigrations(new Migration(202, 203) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `last_sync_count` INTEGER");
                    }
                }).addMigrations(new Migration(203, 204) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''");
                        Cursor cursor = null;
                        try {
                            cursor = db.query("SELECT id FROM account");
                            while (cursor != null && cursor.moveToNext()) {
                                long id = cursor.getLong(0);
                                String uuid = UUID.randomUUID().toString();
                                Log.i("MMM account=" + id + " uuid=" + uuid);
                                db.execSQL("UPDATE account SET uuid = ? WHERE id = ?",
                                        new Object[]{uuid, id});
                            }
                        } catch (Throwable ex) {
                            if (cursor != null)
                                cursor.close();
                        }
                    }
                }).addMigrations(new Migration(204, 205) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `answer` ADD COLUMN `external` INTEGER NOT NULL DEFAULT 0");
                    }
                }).addMigrations(new Migration(205, 206) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `capabilities` TEXT");
                    }
                }).addMigrations(new Migration(206, 207) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("DROP VIEW IF EXISTS `account_view`");
                        db.execSQL("CREATE VIEW IF NOT EXISTS `account_view` AS " +
                                TupleAccountView.query.replace(", category", ""));
                    }
                }).addMigrations(new Migration(207, 208) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `log` ADD COLUMN `type` INTEGER NOT NULL DEFAULT " + EntityLog.Type.General.ordinal());
                    }
                }).addMigrations(new Migration(208, 209) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `log` ADD COLUMN `account` INTEGER");
                        db.execSQL("ALTER TABLE `log` ADD COLUMN `folder` INTEGER");
                        db.execSQL("ALTER TABLE `log` ADD COLUMN `message` INTEGER");
                    }
                }).addMigrations(new Migration(209, 210) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `namespace` TEXT");
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `separator` INTEGER");
                        db.execSQL("UPDATE folder SET separator =" +
                                " (SELECT separator FROM account WHERE account.id = folder.account)");
                    }
                }).addMigrations(new Migration(210, 211) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("CREATE TABLE `search`" +
                                " (`id` INTEGER PRIMARY KEY AUTOINCREMENT" +
                                ", name TEXT NOT NULL" +
                                ", `data` TEXT NOT NULL)");
                    }
                }).addMigrations(new Migration(211, 212) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `search` ADD COLUMN `color` INTEGER");
                    }
                }).addMigrations(new Migration(212, 213) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `account` ADD COLUMN `category` TEXT");
                    }
                }).addMigrations(new Migration(213, 214) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("CREATE INDEX `index_account_synchronize` ON `account` (`synchronize`)");
                        db.execSQL("CREATE INDEX `index_account_category` ON `account` (`category`)");
                        db.execSQL("DROP VIEW IF EXISTS `account_view`");
                        db.execSQL("CREATE VIEW IF NOT EXISTS `account_view` AS " + TupleAccountView.query);
                    }
                }).addMigrations(new Migration(214, 215) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `auto_add` INTEGER");
                    }
                }).addMigrations(new Migration(215, 216) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `infrastructure` TEXT");
                    }
                }).addMigrations(new Migration(216, 217) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `folder` ADD COLUMN `last_sync_foreground` INTEGER");
                    }
                }).addMigrations(new Migration(217, 218) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `smtp_from` TEXT");
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `from_domain` INTEGER");
                    }
                }).addMigrations(new Migration(218, 219) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `resend` INTEGER");
                    }
                }).addMigrations(new Migration(219, 220) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `tls` INTEGER");
                    }
                }).addMigrations(new Migration(220, 221) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("ALTER TABLE `message` ADD COLUMN `sensitivity` INTEGER");
                    }
                }).addMigrations(new Migration(998, 999) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        logMigration(startVersion, endVersion);
                        db.execSQL("UPDATE account" +
                                " SET partial_fetch = 0" +
                                " WHERE host = 'imap.vodafonemail.de'" +
                                " OR host = 'imap.arcor.de'" +
                                " OR host = 'imap.nexgo.de'");
                    }
                });
    }

    public static void checkpoint(Context context) {
        if (!BuildConfig.DEBUG)
            return;

        // https://www.sqlite.org/pragma.html#pragma_wal_checkpoint
        DB db = getInstance(context);
        db.getQueryExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    long start = new Date().getTime();
                    StringBuilder sb = new StringBuilder();
                    SupportSQLiteDatabase sdb = db.getOpenHelper().getWritableDatabase();
                    try (Cursor cursor = sdb.query("PRAGMA wal_checkpoint(PASSIVE);")) {
                        if (cursor.moveToNext()) {
                            for (int i = 0; i < cursor.getColumnCount(); i++) {
                                if (i > 0)
                                    sb.append(",");
                                sb.append(cursor.getInt(i));
                            }
                        }
                    }

                    long elapse = new Date().getTime() - start;
                    Log.i("PRAGMA wal_checkpoint=" + sb + " elapse=" + elapse);
                } catch (Throwable ex) {
                    Log.e(ex);
                }
            }
        });
    }

    public static void shrinkMemory(Context context) {
        DB db = getInstance(context);
        db.getQueryExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    SupportSQLiteDatabase sdb = db.getOpenHelper().getWritableDatabase();
                    try (Cursor cursor = sdb.query("PRAGMA shrink_memory;")) {
                        cursor.moveToNext();
                    }
                } catch (Throwable ex) {
                    Log.e(ex);
                }
            }
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void beginTransaction() {
        super.beginTransaction();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setTransactionSuccessful() {
        super.setTransactionSuccessful();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void endTransaction() {
        super.endTransaction();
    }

    public static class Converters {
        @TypeConverter
        public static String[] toStringArray(String value) {
            if (value == null)
                return new String[0];
            else {
                String[] result = TextUtils.split(value, " ");
                for (int i = 0; i < result.length; i++)
                    result[i] = Uri.decode(result[i]);
                return result;
            }
        }

        @TypeConverter
        public static String fromStringArray(String[] value) {
            if (value == null || value.length == 0)
                return null;
            else {
                String[] copy = new String[value.length];
                System.arraycopy(value, 0, copy, 0, value.length);
                for (int i = 0; i < copy.length; i++)
                    copy[i] = Uri.encode(copy[i]);
                return TextUtils.join(" ", copy);
            }
        }

        @TypeConverter
        public static String encodeAddresses(Address[] addresses) {
            if (addresses == null)
                return null;
            JSONArray jaddresses = new JSONArray();
            for (Address address : addresses)
                try {
                    if (address instanceof InternetAddress) {
                        String a = ((InternetAddress) address).getAddress();
                        String p = ((InternetAddress) address).getPersonal();
                        JSONObject jaddress = new JSONObject();
                        if (a != null)
                            jaddress.put("address", a);
                        if (p != null)
                            jaddress.put("personal", p);
                        jaddresses.put(jaddress);
                    } else {
                        JSONObject jaddress = new JSONObject();
                        jaddress.put("address", address.toString());
                        jaddresses.put(jaddress);
                    }
                } catch (JSONException ex) {
                    Log.e(ex);
                }
            return jaddresses.toString();
        }

        @TypeConverter
        public static Address[] decodeAddresses(String json) {
            if (json == null)
                return null;

            List<Address> result = new ArrayList<>();
            try {
                JSONArray jroot = new JSONArray(json);
                for (int i = 0; i < jroot.length(); i++) {
                    Object item = jroot.get(i);
                    if (jroot.get(i) instanceof JSONArray)
                        for (int j = 0; j < ((JSONArray) item).length(); j++)
                            result.add(InternetAddressJson.from((JSONObject) ((JSONArray) item).get(j)));
                    else
                        result.add(InternetAddressJson.from((JSONObject) item));
                }
            } catch (Throwable ex) {
                // Compose can store invalid addresses
                Log.w(ex);
            }
            return result.toArray(new Address[0]);
        }

        @TypeConverter
        public static EntityLog.Type toLogType(int ordinal) {
            return EntityLog.Type.values()[ordinal];
        }

        @TypeConverter
        public static int fromLogType(EntityLog.Type type) {
            if (type == null)
                type = EntityLog.Type.General;
            return type.ordinal();
        }
    }
}
