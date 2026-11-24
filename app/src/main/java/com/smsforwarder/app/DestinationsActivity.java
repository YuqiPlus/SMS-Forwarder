package com.smsforwarder.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class DestinationsActivity extends AppScreen {
    private ForwardingStore store;
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = ForwardingStore.get(this);
        body = page("接收端设置", true,
                iconButton(Symbol.ADD, "添加接收端", true, () -> openEditor(null)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderDestinations();
    }

    private void renderDestinations() {
        body.removeAllViews();
        ForwardingStore.Destinations destinations = store.destinations();
        if (destinations.entries().isEmpty()) {
            LinearLayout empty = surface();
            addSide(empty, platformBadge(WebhookPlatform.GENERIC, 48), 48, 48, 0);
            add(empty, text("还没有接收端", 20, INK, true), 14);
            add(empty, text("添加 Webhook 后，就可以转发短信。", 14, MUTED, false), 5);
            add(empty, action("添加接收端", PALE, ACCENT, () -> openEditor(null)), 16);
            add(body, empty, 0);
            return;
        }
        ForwardingStore.Destination current = destinations.current();
        if (current != null) {
            add(body, destinationCard(current, true), 0);
        } else {
            add(body, text("点开卡片，将一个接收端设为当前。", 14, MUTED, false), 0);
        }
        for (ForwardingStore.Destination destination : destinations.entries()) {
            if (!destination.id().equals(destinations.currentId())) {
                add(body, destinationCard(destination, false), 10);
            }
        }
    }

    private LinearLayout destinationCard(ForwardingStore.Destination destination, boolean current) {
        WebhookPlatform platform = destination.resolvedPlatform();
        LinearLayout card = row();
        card.setPadding(dp(14), dp(13), dp(13), dp(13));
        clickableCard(card, () -> openEditor(destination.id()));
        addSide(card, platformBadge(platform, 48), 48, 48, 0);

        LinearLayout labels = column();
        TextView name = text(destination.displayName(), 16, INK, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        add(labels, name, 0);
        if (!destination.nickname().isEmpty()) {
            add(labels, text(platform.label, 13, MUTED, false), 2);
        }
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, WRAP, 1);
        labelParams.setMarginStart(dp(12));
        card.addView(labels, labelParams);

        if (current) {
            TextView badge = text("当前", 12, ACCENT, true);
            badge.setPadding(dp(9), dp(4), dp(9), dp(4));
            badge.setBackground(shape(PALE, 0, 12));
            addSide(card, badge, WRAP, WRAP, 8);
        }
        addSide(card, icon(Symbol.NEXT, MUTED), 18, 22, 8);
        return card;
    }

    private void openEditor(String id) {
        Intent intent = new Intent(this, DestinationEditorActivity.class);
        if (id != null) {
            intent.putExtra(DestinationEditorActivity.DESTINATION_ID, id);
        }
        startActivity(intent);
    }
}
