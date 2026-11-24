package com.smsforwarder.app;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class ForwardingStore {
    static final int MAX_PENDING = 40;
    private static final long RETRY_DELAY_MS = 30_000L;
    private static ForwardingStore instance;

    record Settings(String endpoint, String token, boolean enabled) {}
    record Delivery(int jobId, String eventId, boolean test, String sender, String body,
                    long receivedAt, long queuedAt, int attempts) {}

    private final Context context;
    private final SharedPreferences preferences;
    private final QueueDatabase database;
    private final JobScheduler scheduler;
    private volatile int queueVersion;

    // The screen, receiver and JobService all access the store on the main thread.
    static ForwardingStore get(Context context) {
        if (instance == null) {
            instance = new ForwardingStore(context.getApplicationContext());
        }
        return instance;
    }

    private ForwardingStore(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences("forwarding", Context.MODE_PRIVATE);
        database = new QueueDatabase(context);
        scheduler = context.getSystemService(JobScheduler.class);
    }

    Settings settings() {
        return new Settings(preferences.getString("endpoint", ""),
                preferences.getString("token", ""), preferences.getBoolean("enabled", false));
    }

    boolean save(Settings next) {
        Settings current = settings();
        if (current.equals(next)) {
            return true;
        }
        boolean clearQueue = !current.endpoint().equals(next.endpoint())
                || !current.token().equals(next.token()) || (current.enabled() && !next.enabled());
        if (clearQueue) {
            queueVersion++;
            scheduler.cancelAll();
            database.getWritableDatabase().delete("deliveries", null, null);
        }
        SharedPreferences.Editor editor = preferences.edit()
                .putString("endpoint", next.endpoint())
                .putString("token", next.token())
                .putBoolean("enabled", next.enabled());
        if (clearQueue) {
            editor.remove("status").remove("status_time");
        }
        return editor.commit();
    }

    int queueVersion() {
        return queueVersion;
    }

    boolean hasSmsPermission() {
        return context.checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    boolean canSend(Delivery delivery, Settings settings) {
        return !settings.endpoint().isEmpty()
                && (delivery.test() || (settings.enabled() && hasSmsPermission()));
    }

    void enqueue(boolean test, String sender, String body, long receivedAt) {
        if (pendingCount() >= MAX_PENDING) {
            report(test, "队列已满，本条消息未加入");
            return;
        }

        ContentValues values = new ContentValues();
        values.put("event_id", UUID.randomUUID().toString());
        values.put("is_test", test ? 1 : 0);
        values.put("sender", sender);
        values.put("body", body);
        values.put("received_at", receivedAt);
        values.put("queued_at", System.currentTimeMillis());
        int jobId = Math.toIntExact(database.getWritableDatabase().insertOrThrow("deliveries", null, values));
        if (schedule(jobId)) {
            report(test, "已加入发送队列");
        } else {
            delete(jobId);
            report(test, "系统未能安排发送");
        }
    }

    void recoverUnscheduled() {
        Set<Integer> scheduled = new HashSet<>();
        for (JobInfo job : scheduler.getAllPendingJobs()) {
            scheduled.add(job.getId());
        }
        try (Cursor cursor = database.getReadableDatabase().rawQuery("SELECT id FROM deliveries", null)) {
            while (cursor.moveToNext()) {
                int jobId = cursor.getInt(0);
                if (!scheduled.contains(jobId)) {
                    schedule(jobId);
                }
            }
        }
    }

    private boolean schedule(int jobId) {
        JobInfo job = new JobInfo.Builder(jobId, new ComponentName(context, DeliveryService.class))
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(RETRY_DELAY_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();
        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS;
    }

    Delivery find(int jobId) {
        try (Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT event_id, is_test, sender, body, received_at, queued_at, attempts "
                        + "FROM deliveries WHERE id = ?", new String[]{Integer.toString(jobId)})) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new Delivery(jobId, cursor.getString(0), cursor.getInt(1) == 1,
                    cursor.getString(2), cursor.getString(3), cursor.getLong(4),
                    cursor.getLong(5), cursor.getInt(6));
        }
    }

    int pendingCount() {
        try (Cursor cursor = database.getReadableDatabase().rawQuery("SELECT count(*) FROM deliveries", null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    void setAttempts(int jobId, int attempts) {
        ContentValues values = new ContentValues();
        values.put("attempts", attempts);
        database.getWritableDatabase().update("deliveries", values, "id = ?",
                new String[]{Integer.toString(jobId)});
    }

    void delete(int jobId) {
        database.getWritableDatabase().delete("deliveries", "id = ?", new String[]{Integer.toString(jobId)});
    }

    void report(boolean test, String message) {
        preferences.edit().putString("status", (test ? "测试 · " : "短信 · ") + message)
                .putLong("status_time", System.currentTimeMillis()).apply();
    }

    String status() {
        return preferences.getString("status", "暂无发送记录");
    }

    long statusTime() {
        return preferences.getLong("status_time", 0);
    }

    void watchStatus(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    void unwatchStatus(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    private static final class QueueDatabase extends SQLiteOpenHelper {
        QueueDatabase(Context context) {
            super(context, "delivery_queue.db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE deliveries (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "event_id TEXT NOT NULL, is_test INTEGER NOT NULL, sender TEXT NOT NULL, "
                    + "body TEXT NOT NULL, received_at INTEGER NOT NULL, queued_at INTEGER NOT NULL, "
                    + "attempts INTEGER NOT NULL DEFAULT 0)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            throw new IllegalStateException("No database migration for version " + oldVersion);
        }
    }
}
