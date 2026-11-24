package com.smsforwarder.app;

import android.Manifest;
import android.app.Application;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ForwardingStore {
    static final int MAX_PENDING = 40;
    private static final long RETRY_DELAY_MS = 30_000L;
    private static final String LEGACY_DESTINATION = "previous";
    private static ForwardingStore instance;

    record Settings(String endpoint, String token, boolean enabled, WebhookPlatform platform) {
        WebhookPlatform resolvedPlatform() {
            return platform.resolve(endpoint);
        }
    }
    record Destination(String id, String nickname, String endpoint, String token, WebhookPlatform platform) {
        WebhookPlatform resolvedPlatform() {
            return platform.resolve(endpoint);
        }

        String displayName() {
            return nickname.isEmpty() ? resolvedPlatform().label : nickname;
        }
    }
    record Destinations(List<Destination> entries, String currentId) {
        Destination find(String id) {
            for (Destination destination : entries) {
                if (destination.id().equals(id)) {
                    return destination;
                }
            }
            return null;
        }

        Destination current() {
            return find(currentId);
        }
    }
    record Delivery(int jobId, String eventId, boolean test, String sender, String body,
                    long receivedAt, long queuedAt, int attempts, int simSlot, String destinationId) {}

    private final Application application;
    private final SharedPreferences preferences;
    private final QueueDatabase database;
    private final JobScheduler scheduler;
    private volatile int queueVersion;

    // The screen, receiver and JobService get the store on the main thread.
    static ForwardingStore get(Context context) {
        if (instance == null) {
            instance = new ForwardingStore((Application) context.getApplicationContext());
        }
        return instance;
    }

    private ForwardingStore(Application application) {
        this.application = application;
        preferences = application.getSharedPreferences("forwarding", Context.MODE_PRIVATE);
        database = new QueueDatabase(application);
        scheduler = application.getSystemService(JobScheduler.class);
    }

    Settings settings() {
        String endpoint = preferences.getString("endpoint", "");
        WebhookPlatform platform = WebhookPlatform.restore(preferences.getString("platform", null), endpoint);
        return new Settings(endpoint, preferences.getString("token", ""),
                preferences.getBoolean("enabled", false), platform);
    }

    boolean setEnabled(boolean enabled) {
        Settings current = settings();
        if (enabled && !hasConfiguredRouting()) {
            return false;
        }
        if (current.enabled() == enabled) {
            return true;
        }
        Settings next = new Settings(current.endpoint(), current.token(), enabled, current.platform());
        return writeSettings(current, next, false, preferences.edit());
    }

    SimRules simRules() {
        return new SimRules(preferences.getBoolean("route_by_sim", false),
                preferences.getString("sim1_destination", ""), preferences.getString("sim2_destination", ""));
    }

    boolean hasConfiguredRouting() {
        return hasConfiguredRouting(simRules(), destinations());
    }

    private boolean hasConfiguredRouting(SimRules rules, Destinations destinations) {
        return rules.bySim() ? destinations.find(rules.sim1()) != null || destinations.find(rules.sim2()) != null
                : destinations.current() != null;
    }

    boolean saveSimRules(SimRules next) {
        SimRules current = simRules();
        if (current.equals(next)) {
            return true;
        }
        Destinations destinations = destinations();
        if ((!next.sim1().isEmpty() && destinations.find(next.sim1()) == null)
                || (!next.sim2().isEmpty() && destinations.find(next.sim2()) == null)) {
            return false;
        }
        Settings settings = settings();
        Settings updated = new Settings(settings.endpoint(), settings.token(),
                settings.enabled() && hasConfiguredRouting(next, destinations), settings.platform());
        return writeSettings(settings, updated, current.bySim() || next.bySim(),
                writeRules(preferences.edit(), next));
    }

    private SharedPreferences.Editor writeRules(SharedPreferences.Editor editor, SimRules rules) {
        return editor.putBoolean("route_by_sim", rules.bySim())
                .putString("sim1_destination", rules.sim1()).putString("sim2_destination", rules.sim2());
    }

    Destinations destinations() {
        String entries = preferences.getString("destinations", null);
        if (entries != null) {
            try {
                List<Destination> items = new ArrayList<>();
                JSONArray json = new JSONArray(entries);
                for (int index = 0; index < json.length(); index++) {
                    JSONObject item = json.getJSONObject(index);
                    String endpoint = item.getString("endpoint");
                    items.add(new Destination(item.getString("id"), item.optString("nickname"), endpoint,
                            item.optString("token"), WebhookPlatform.restore(item.optString("platform"), endpoint)));
                }
                return new Destinations(items, preferences.getString("current_destination", ""));
            } catch (JSONException ignored) {
                // The active settings can still be recovered independently.
            }
        }
        Settings current = settings();
        if (current.endpoint().isEmpty()) {
            return new Destinations(Collections.emptyList(), "");
        }
        Destination legacy = new Destination(LEGACY_DESTINATION, "", current.endpoint(),
                current.token(), current.platform());
        return new Destinations(Collections.singletonList(legacy), LEGACY_DESTINATION);
    }

    boolean saveDestination(Destination destination, boolean makeCurrent) {
        Destinations before = destinations();
        List<Destination> items = new ArrayList<>(before.entries());
        Destination existing = before.find(destination.id());
        if (existing == null) {
            items.add(destination);
        } else {
            items.set(items.indexOf(existing), destination);
        }
        String currentId = makeCurrent || before.entries().isEmpty() ? destination.id() : before.currentId();
        return saveDestinations(before, new Destinations(items, currentId));
    }

    boolean removeDestination(String id) {
        Destinations before = destinations();
        Destination existing = before.find(id);
        if (existing == null) {
            return false;
        }
        List<Destination> items = new ArrayList<>(before.entries());
        items.remove(existing);
        String currentId = before.currentId().equals(id) ? "" : before.currentId();
        return saveDestinations(before, new Destinations(items, currentId));
    }

    private boolean saveDestinations(Destinations before, Destinations after) {
        JSONArray entries = new JSONArray();
        try {
            for (Destination item : after.entries()) {
                entries.put(new JSONObject()
                        .put("id", item.id()).put("nickname", item.nickname())
                        .put("endpoint", item.endpoint()).put("token", item.token())
                        .put("platform", item.platform().name()));
            }
        } catch (JSONException ignored) {
            return false;
        }
        Settings current = settings();
        SimRules rules = simRules();
        SimRules nextRules = rules;
        for (Destination item : before.entries()) {
            if (after.find(item.id()) == null) {
                nextRules = nextRules.without(item.id());
            }
        }
        Destination target = after.current();
        boolean enabled = current.enabled() && hasConfiguredRouting(nextRules, after);
        Settings next = target == null ? new Settings("", "", enabled, WebhookPlatform.AUTO)
                : new Settings(target.endpoint(), target.token(), enabled, target.platform());
        SharedPreferences.Editor editor = preferences.edit()
                .putString("destinations", entries.toString()).putString("current_destination", after.currentId());
        if (!nextRules.equals(rules)) {
            writeRules(editor, nextRules);
        }
        boolean changedSimTarget = rules.bySim() && (changedConnection(before.find(rules.sim1()), after.find(rules.sim1()))
                || changedConnection(before.find(rules.sim2()), after.find(rules.sim2())));
        return writeSettings(current, next, !before.currentId().equals(after.currentId()) || changedSimTarget, editor);
    }

    private boolean changedConnection(Destination before, Destination after) {
        if (before == null || after == null) {
            return before != after;
        }
        return !before.endpoint().equals(after.endpoint()) || !before.token().equals(after.token())
                || before.resolvedPlatform() != after.resolvedPlatform();
    }

    private boolean writeSettings(Settings current, Settings next, boolean changedDestination,
                                  SharedPreferences.Editor editor) {
        boolean clearQueue = changedDestination || !current.endpoint().equals(next.endpoint())
                || !current.token().equals(next.token()) || current.resolvedPlatform() != next.resolvedPlatform()
                || (current.enabled() && !next.enabled());
        if (clearQueue) {
            queueVersion++;
            scheduler.cancelAll();
            SQLiteDatabase db = database.getWritableDatabase();
            db.beginTransaction();
            try {
                ForwardingHistory.cancelPending(db);
                db.delete("deliveries", null, null);
                ForwardingHistory.prune(db);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
        editor
                .putString("endpoint", next.endpoint())
                .putString("token", next.token())
                .putString("platform", next.platform().name())
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
        return application.checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    Settings settingsFor(Delivery delivery) {
        Settings active = settings();
        if (!delivery.test() && (!active.enabled() || !hasSmsPermission())) {
            return null;
        }
        Destinations destinations = destinations();
        String id = delivery.test() ? destinations.currentId()
                : simRules().destinationId(delivery.simSlot(), destinations.currentId());
        Destination destination = destinations.find(id);
        if (destination == null || (!delivery.destinationId().isEmpty() && !id.equals(delivery.destinationId()))) {
            return null;
        }
        return new Settings(destination.endpoint(), destination.token(), active.enabled(), destination.platform());
    }

    void enqueueSms(int slot, String sender, String body, long receivedAt) {
        Destinations destinations = destinations();
        SimRules rules = simRules();
        Destination target = destinations.find(rules.destinationId(slot, destinations.currentId()));
        if (target == null) {
            String reason = rules.bySim() && (slot < 0 || slot > 1) ? "未能识别短信所属的 SIM 卡"
                    : rules.bySim() ? "这张 SIM 卡未设置转发" : "未设置接收端";
            SQLiteDatabase db = database.getWritableDatabase();
            ForwardingHistory.add(db, UUID.randomUUID().toString(), historyTime(receivedAt), false, slot,
                    sender, null, ForwardingHistory.State.SKIPPED, reason);
            ForwardingHistory.prune(db);
            report(false, reason);
            return;
        }
        enqueue(false, slot, sender, body, receivedAt, target);
    }

    void enqueueTest() {
        Destination target = destinations().current();
        if (target != null) {
            enqueue(true, SimRules.UNKNOWN, "test", "这是一条 Webhook 测试消息。", System.currentTimeMillis(), target);
        }
    }

    private void enqueue(boolean test, int slot, String sender, String body, long receivedAt, Destination target) {
        String eventId = UUID.randomUUID().toString();
        SQLiteDatabase db = database.getWritableDatabase();
        if (pendingCount() >= MAX_PENDING) {
            String reason = "队列已满，本条消息未加入";
            ForwardingHistory.add(db, eventId, historyTime(receivedAt), test, slot, sender, target,
                    ForwardingHistory.State.FAILED, reason);
            ForwardingHistory.prune(db);
            report(test, reason);
            return;
        }
        String queued = "已加入发送队列";
        ContentValues values = new ContentValues();
        values.put("event_id", eventId);
        values.put("is_test", test ? 1 : 0);
        values.put("sender", sender);
        values.put("body", body);
        values.put("received_at", receivedAt);
        values.put("queued_at", System.currentTimeMillis());
        values.put("sim_slot", slot);
        values.put("destination_id", target.id());
        int jobId;
        db.beginTransaction();
        try {
            jobId = Math.toIntExact(db.insertOrThrow("deliveries", null, values));
            ForwardingHistory.add(db, eventId, historyTime(receivedAt), test, slot, sender, target,
                    ForwardingHistory.State.QUEUED, queued);
            ForwardingHistory.prune(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (schedule(jobId)) {
            report(test, queued);
        } else {
            deliveryResult(jobId, eventId, test, ForwardingHistory.State.FAILED, "系统未能安排发送");
        }
    }

    private long historyTime(long receivedAt) {
        return receivedAt > 0 ? receivedAt : System.currentTimeMillis();
    }

    List<ForwardingHistory.Entry> history() {
        return ForwardingHistory.recent(database.getReadableDatabase());
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
        JobInfo job = new JobInfo.Builder(jobId, new ComponentName(application, DeliveryService.class))
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(RETRY_DELAY_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();
        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS;
    }

    Delivery find(int jobId) {
        try (Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT event_id, is_test, sender, body, received_at, queued_at, attempts, sim_slot, destination_id "
                        + "FROM deliveries WHERE id = ?", new String[]{Integer.toString(jobId)})) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new Delivery(jobId, cursor.getString(0), cursor.getInt(1) == 1,
                    cursor.getString(2), cursor.getString(3), cursor.getLong(4),
                    cursor.getLong(5), cursor.getInt(6), cursor.getInt(7), cursor.getString(8));
        }
    }

    int pendingCount() {
        try (Cursor cursor = database.getReadableDatabase().rawQuery("SELECT count(*) FROM deliveries", null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    boolean recordAttempt(int jobId, int attempt) {
        ContentValues values = new ContentValues();
        values.put("attempts", attempt);
        return database.getWritableDatabase().update("deliveries", values, "id = ? AND attempts = ?",
                new String[]{Integer.toString(jobId), Integer.toString(attempt - 1)}) == 1;
    }

    void deliveryResult(int jobId, String eventId, boolean test, ForwardingHistory.State state, String detail) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            ForwardingHistory.update(db, eventId, state, detail);
            if (state != ForwardingHistory.State.RETRYING) {
                db.delete("deliveries", "id = ?", new String[]{Integer.toString(jobId)});
                ForwardingHistory.prune(db);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        report(test, detail);
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
            super(context, "delivery_queue.db", null, 2);
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE deliveries (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "event_id TEXT NOT NULL, is_test INTEGER NOT NULL, sender TEXT NOT NULL, "
                    + "body TEXT NOT NULL, received_at INTEGER NOT NULL, queued_at INTEGER NOT NULL, "
                    + "attempts INTEGER NOT NULL DEFAULT 0, sim_slot INTEGER NOT NULL DEFAULT -1, "
                    + "destination_id TEXT NOT NULL DEFAULT '')");
            ForwardingHistory.create(database);
        }

        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            if (oldVersion != 1 || newVersion != 2) {
                throw new IllegalStateException("No database migration for version " + oldVersion);
            }
            database.execSQL("ALTER TABLE deliveries ADD COLUMN sim_slot INTEGER NOT NULL DEFAULT -1");
            database.execSQL("ALTER TABLE deliveries ADD COLUMN destination_id TEXT NOT NULL DEFAULT ''");
            ForwardingHistory.create(database);
            database.execSQL("INSERT INTO forwarding_history (event_id, occurred_at, is_test, sim_slot, "
                    + "sender_label, destination, platform, state, detail) "
                    + "SELECT event_id, CASE WHEN received_at > 0 THEN received_at ELSE queued_at END, is_test, -1, "
                    + "'发件人已隐藏', '', 'GENERIC', 'QUEUED', '已加入发送队列' FROM deliveries ORDER BY id");
        }
    }
}
