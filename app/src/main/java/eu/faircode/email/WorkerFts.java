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
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.requery.android.database.sqlite.SQLiteDatabase;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;

public class WorkerFts extends Worker {
    private static final int INDEX_DELAY = 30; // seconds
    private static final int INDEX_BATCH_SIZE = 100;

    public WorkerFts(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        Log.i("Instance " + getName());
    }

    @NonNull
    @Override
    public Result doWork() {
        Thread.currentThread().setPriority(THREAD_PRIORITY_BACKGROUND);

        try {
            Log.i("FTS index");
            Context context = getApplicationContext();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean checkpoints = prefs.getBoolean("checkpoints", true);

            int indexed = 0;
            List<Long> ids = new ArrayList<>(INDEX_BATCH_SIZE);
            DB db = DB.getInstance(context);

            SQLiteDatabase sdb = FtsDbHelper.getInstance(context);

            for (long id : db.message().getMessageFts())
                try {
                    Log.i("FTS index=" + id);

                    ids.add(id);

                    EntityMessage message = db.message().getMessage(id);
                    if (message == null) {
                        Log.i("FTS gone");
                        continue;
                    }

                    File file = message.getFile(context);
                    String text = HtmlHelper.getFullText(file);
                    if (text == null)
                        text = "";

                    boolean fts = prefs.getBoolean("fts", false);
                    if (!fts)
                        break;

                    try {
                        sdb.beginTransaction();
                        FtsDbHelper.insert(sdb, message, text);
                        sdb.setTransactionSuccessful();
                    } finally {
                        sdb.endTransaction();
                    }

                    indexed++;

                    if (ids.size() > INDEX_BATCH_SIZE)
                        markIndexed(db, ids);
                } catch (Throwable ex) {
                    Log.e(ex);
                }

            markIndexed(db, ids);

            if (checkpoints)
                DB.checkpoint(context);

            Log.i("FTS indexed=" + indexed);
            return Result.success();
        } catch (Throwable ex) {
            Log.e(ex);
            return Result.failure();
        }
    }

    private void markIndexed(DB db, List<Long> ids) {
        try {
            db.beginTransaction();
            for (Long id : ids)
                db.message().setMessageFts(id, true);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        ids.clear();
    }

    static void init(Context context, boolean immediately) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean fts = prefs.getBoolean("fts", true);
            boolean pro = ActivityBilling.isPro(context);
            if (fts && pro) {
                Log.i("Queuing " + getName());

                OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(WorkerFts.class);
                if (!immediately)
                    builder.setInitialDelay(INDEX_DELAY, TimeUnit.SECONDS);
                OneTimeWorkRequest workRequest = builder.build();

                WorkManager.getInstance(context)
                        .enqueueUniqueWork(getName(), ExistingWorkPolicy.REPLACE, workRequest);

                Log.i("Queued " + getName());
            } else if (immediately)
                cancel(context);
        } catch (IllegalStateException ex) {
            // https://issuetracker.google.com/issues/138465476
            Log.w(ex);
        }
    }

    static void cancel(Context context) {
        try {
            Log.i("Cancelling " + getName());
            WorkManager.getInstance(context).cancelUniqueWork(getName());
            Log.i("Cancelled " + getName());
        } catch (IllegalStateException ex) {
            Log.w(ex);
        }
    }

    private static String getName() {
        return WorkerFts.class.getSimpleName();
    }
}
