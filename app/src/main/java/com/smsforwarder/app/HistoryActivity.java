package com.smsforwarder.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Collections;
import java.util.Date;
import java.util.List;

public final class HistoryActivity extends AppScreen {
    private ForwardingStore store;
    private ListView list;
    private HistoryAdapter adapter;
    private final Runnable refresh = () -> adapter.replace(store.history());
    private final SharedPreferences.OnSharedPreferenceChangeListener historyListener = (preferences, key) -> {
        if ("status_time".equals(key) || "status".equals(key)) {
            list.removeCallbacks(refresh);
            list.post(refresh);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = ForwardingStore.get(this);
        LinearLayout body = page("转发历史", true, null, false);
        add(body, text(getString(R.string.history_privacy, ForwardingHistory.LIMIT), 13, MUTED, false), 0);

        FrameLayout content = new FrameLayout(this);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(MATCH, 0, 1);
        contentParams.topMargin = dp(9);
        body.addView(content, contentParams);
        list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        content.addView(list, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout empty = surface();
        add(empty, text("还没有转发记录", 18, INK, true), 0);
        add(empty, text("接收短信或发送测试后，会在这里看到处理结果。", 14, MUTED, false), 4);
        content.addView(empty, new FrameLayout.LayoutParams(MATCH, WRAP));
        adapter = new HistoryAdapter();
        list.setEmptyView(empty);
        list.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        refresh.run();
        store.watchStatus(historyListener);
    }

    @Override
    protected void onStop() {
        store.unwatchStatus(historyListener);
        list.removeCallbacks(refresh);
        super.onStop();
    }

    private final class HistoryAdapter extends BaseAdapter {
        private List<ForwardingHistory.Entry> entries = Collections.emptyList();

        void replace(List<ForwardingHistory.Entry> next) {
            entries = next;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return entries.size(); }
        @Override public ForwardingHistory.Entry getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean isEnabled(int position) { return false; }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            HistoryRow row = view instanceof HistoryRow existing ? existing : new HistoryRow();
            row.bind(getItem(position));
            return row;
        }
    }

    private final class HistoryRow extends LinearLayout {
        final FrameLayout platformMark;
        final TextView title;
        final TextView badge;
        final TextView metadata;
        final TextView detail;

        HistoryRow() {
            super(HistoryActivity.this);
            setOrientation(VERTICAL);
            setPadding(0, 0, 0, dp(9));
            LinearLayout card = surface();
            add(this, card, 0);
            LinearLayout heading = row();
            platformMark = new FrameLayout(HistoryActivity.this);
            addSide(heading, platformMark, 32, 32, 0);
            title = text("", 16, INK, true);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, WRAP, 1);
            titleParams.setMarginStart(dp(8));
            heading.addView(title, titleParams);
            badge = text("", 12, INK, true);
            badge.setPadding(dp(9), dp(4), dp(9), dp(4));
            addSide(heading, badge, WRAP, WRAP, 8);
            add(card, heading, 0);
            metadata = text("", 13, MUTED, false);
            detail = text("", 14, INK, false);
            add(card, metadata, 5);
            add(card, detail, 7);
        }

        void bind(ForwardingHistory.Entry entry) {
            platformMark.removeAllViews();
            platformMark.addView(platformBadge(entry.platform(), 32), new FrameLayout.LayoutParams(MATCH, MATCH));
            title.setText(entry.destination().isEmpty() ? (entry.test() ? "测试消息" : "收到短信") : entry.destination());
            String source = entry.test() ? "测试消息" : entry.slot() < 0 ? "SIM 未知"
                    : getString(R.string.sim_number, entry.slot() + 1);
            CharSequence time = DateFormat.format("MM-dd HH:mm:ss", new Date(entry.time()));
            String context = entry.test() ? source : getString(R.string.history_metadata, source, entry.sender());
            metadata.setText(getString(R.string.history_metadata, context, time));
            detail.setText(entry.detail());
            badge.setText(entry.state().label);
            int fill = switch (entry.state()) {
                case SENT -> PALE;
                case FAILED -> 0xfff9e9e9;
                case QUEUED, RETRYING -> 0xfffbf0dc;
                case SKIPPED, CANCELLED -> BG;
            };
            int ink = switch (entry.state()) {
                case SENT -> ACCENT;
                case FAILED -> DANGER;
                case QUEUED, RETRYING -> 0xff80540d;
                case SKIPPED, CANCELLED -> MUTED;
            };
            badge.setTextColor(ink);
            badge.setBackground(shape(fill, 0, 11));
        }
    }
}
