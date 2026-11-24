package com.smsforwarder.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

final class ForwardingHistory {
    static final int LIMIT = 200;

    enum State {
        QUEUED("待发送"), RETRYING("重试中"), SENT("已发送"), FAILED("失败"), SKIPPED("已跳过"), CANCELLED("已取消");

        final String label;

        State(String label) {
            this.label = label;
        }
    }

    record Entry(long time, boolean test, int slot, String sender, String destination,
                 WebhookPlatform platform, State state, String detail) {}

    static void create(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE forwarding_history (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "event_id TEXT NOT NULL UNIQUE, occurred_at INTEGER NOT NULL, is_test INTEGER NOT NULL, "
                + "sim_slot INTEGER NOT NULL, sender_label TEXT NOT NULL, destination TEXT NOT NULL, "
                + "platform TEXT NOT NULL, state TEXT NOT NULL, detail TEXT NOT NULL)");
    }

    static List<Entry> recent(SQLiteDatabase database) {
        List<Entry> results = new ArrayList<>();
        try (Cursor cursor = database.rawQuery(
                "SELECT occurred_at, is_test, sim_slot, sender_label, destination, platform, state, detail "
                        + "FROM forwarding_history ORDER BY id DESC LIMIT " + LIMIT, null)) {
            while (cursor.moveToNext()) {
                results.add(new Entry(cursor.getLong(0), cursor.getInt(1) != 0, cursor.getInt(2),
                        cursor.getString(3), cursor.getString(4), WebhookPlatform.restore(cursor.getString(5), ""),
                        State.valueOf(cursor.getString(6)), cursor.getString(7)));
            }
        }
        return results;
    }

    static void add(SQLiteDatabase database, String eventId, long time, boolean test, int slot,
                    String sender, ForwardingStore.Destination destination, State state, String detail) {
        ContentValues values = status(state, detail);
        values.put("event_id", eventId);
        values.put("occurred_at", time);
        values.put("is_test", test ? 1 : 0);
        values.put("sim_slot", slot);
        values.put("sender_label", mask(sender));
        values.put("destination", destination == null ? "" : destination.displayName());
        values.put("platform", destination == null ? WebhookPlatform.GENERIC.name()
                : destination.resolvedPlatform().name());
        database.insertOrThrow("forwarding_history", null, values);
    }

    static void update(SQLiteDatabase database, String eventId, State state, String detail) {
        database.update("forwarding_history", status(state, detail), "event_id = ?", new String[]{eventId});
    }

    static void cancelPending(SQLiteDatabase database) {
        database.update("forwarding_history", status(State.CANCELLED, "设置已更改或转发已关闭"),
                "event_id IN (SELECT event_id FROM deliveries)", null);
    }

    static void prune(SQLiteDatabase database) {
        database.execSQL("DELETE FROM forwarding_history WHERE id NOT IN "
                + "(SELECT id FROM forwarding_history ORDER BY id DESC LIMIT " + LIMIT + ") "
                + "AND event_id NOT IN (SELECT event_id FROM deliveries)");
    }

    private static ContentValues status(State state, String detail) {
        ContentValues values = new ContentValues();
        values.put("state", state.name());
        values.put("detail", detail);
        return values;
    }

    private static String mask(String sender) {
        if (sender.matches("\\+?[0-9]{5,}")) {
            return "•••• " + sender.substring(sender.length() - 4);
        }
        return sender.isEmpty() ? "未知发件人" : "发件人已隐藏";
    }

    private ForwardingHistory() {}
}
