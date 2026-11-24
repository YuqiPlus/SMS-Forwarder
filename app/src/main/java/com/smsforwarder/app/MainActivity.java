package com.smsforwarder.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;

public final class MainActivity extends AppScreen {
    private static final int SMS_PERMISSION_REQUEST = 10;
    private static final int HERO_START = 0xff153b34;
    private static final int HERO_END = 0xff216651;
    private static final int HERO_MUTED = 0xffd5eee4;

    private ForwardingStore store;
    private TextView state;
    private TextView permission;
    private Button toggle;
    private TextView destinationTitle;
    private LinearLayout currentCard;
    private TextView ruleDescription;
    private TextView lastResult;
    private TextView resultTime;
    private TextView pending;
    private Button test;
    private final SharedPreferences.OnSharedPreferenceChangeListener statusListener =
            (preferences, key) -> renderStatus();

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        store = ForwardingStore.get(this);
        LinearLayout body = page("短信转发", false, iconButton(Symbol.GEAR, "接收端设置", false, this::openSettings));

        LinearLayout hero = column();
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{HERO_START, HERO_END});
        gradient.setCornerRadius(dp(22));
        hero.setBackground(gradient);
        add(body, hero, 0);

        LinearLayout headline = row();
        state = text("", 24, WHITE, true);
        headline.addView(state, new LinearLayout.LayoutParams(0, WRAP, 1));
        addSide(headline, icon(Symbol.MESSAGE, HERO_MUTED), 32, 32, 8);
        add(hero, headline, 0);
        permission = text("", 14, HERO_MUTED, false);
        add(hero, permission, 5);
        toggle = action("", PALE, ACCENT, this::toggleForwarding);
        add(hero, toggle, 19);

        destinationTitle = text("", 15, INK, true);
        add(body, destinationTitle, 24);
        currentCard = row();
        currentCard.setPadding(dp(14), dp(13), dp(14), dp(13));
        clickableCard(currentCard, this::openSettings);
        add(body, currentCard, 9);

        ruleDescription = text("", 13, MUTED, false);
        add(body, shortcut(Symbol.SIM, "SIM 卡规则", ruleDescription, this::openSimRules), 9);

        section(body, "最近转发记录", 22);
        LinearLayout result = surface();
        lastResult = text("", 16, INK, true);
        lastResult.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(result, lastResult, 0);
        resultTime = text("", 13, MUTED, false);
        add(result, resultTime, 4);
        pending = text("", 13, MUTED, false);
        add(result, pending, 4);
        add(body, result, 9);

        add(body, shortcut(Symbol.HISTORY, "转发历史", null, this::openHistory), 9);
        test = action("发送测试消息", PALE, ACCENT, this::sendTest);
        add(body, test, 14);
    }

    @Override
    protected void onStart() {
        super.onStart();
        store.watchStatus(statusListener);
        store.recoverUnscheduled();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderDestination();
        renderStatus();
    }

    @Override
    protected void onStop() {
        store.unwatchStatus(statusListener);
        super.onStop();
    }

    private void renderDestination() {
        ForwardingStore.Destination current = store.destinations().current();
        boolean bySim = store.simRules().bySim();
        destinationTitle.setText(bySim ? "默认接收端" : "当前接收端");
        ruleDescription.setText(bySim ? "按 SIM 卡分开转发" : "所有 SIM 卡使用同一接收端");
        currentCard.removeAllViews();
        WebhookPlatform route = current == null ? WebhookPlatform.GENERIC : current.resolvedPlatform();
        addSide(currentCard, platformBadge(route, 48), 48, 48, 0);
        LinearLayout labels = column();
        TextView name = text(current == null ? "添加或选择接收端" : current.displayName(), 16, INK, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        add(labels, name, 0);
        if (current != null && !current.nickname().isEmpty()) {
            add(labels, text(route.label, 13, MUTED, false), 2);
        }
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, WRAP, 1);
        labelParams.setMarginStart(dp(12));
        currentCard.addView(labels, labelParams);
        addSide(currentCard, icon(Symbol.NEXT, MUTED), 20, 24, 8);
        test.setVisibility(current == null ? View.GONE : View.VISIBLE);
        test.setText(bySim ? "测试默认接收端" : "发送测试消息");
    }

    private void renderStatus() {
        ForwardingStore.Settings settings = store.settings();
        boolean enabled = settings.enabled();
        boolean granted = store.hasSmsPermission();
        boolean configured = store.hasConfiguredRouting();
        state.setText(enabled ? (granted ? "转发已开启" : "转发已暂停") : "转发已关闭");
        if (!configured) {
            permission.setText(store.simRules().bySim() ? "请选择要转发的 SIM 卡及接收端" : "添加接收端后即可开启转发");
        } else {
            permission.setText(granted ? "短信接收权限已授予" : "需要授权接收短信");
        }
        toggle.setText(!configured ? (store.simRules().bySim() ? "去设置 SIM 卡规则" : "去设置接收端")
                : enabled ? "关闭转发" : "开启转发");

        lastResult.setText(store.status());
        long time = store.statusTime();
        resultTime.setVisibility(time > 0 ? View.VISIBLE : View.GONE);
        if (time > 0) {
            resultTime.setText(DateFormat.format("MM-dd HH:mm:ss", new Date(time)));
        }
        pending.setText(getString(R.string.pending_count, store.pendingCount()));
    }

    private void toggleForwarding() {
        ForwardingStore.Settings current = store.settings();
        if (!store.hasConfiguredRouting()) {
            if (store.simRules().bySim()) {
                openSimRules();
            } else {
                openSettings();
            }
        } else if (current.enabled()) {
            setForwarding(false);
        } else if (store.hasSmsPermission()) {
            setForwarding(true);
        } else {
            requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, SMS_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(request, permissions, grantResults);
        if (request != SMS_PERMISSION_REQUEST) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setForwarding(true);
        } else {
            Toast.makeText(this, "未获得短信权限，可在系统的应用权限设置中检查。", Toast.LENGTH_LONG).show();
            renderStatus();
        }
    }

    private void setForwarding(boolean enabled) {
        if (!store.setEnabled(enabled)) {
            showSaveError();
        }
        renderStatus();
    }

    private void sendTest() {
        if (!store.settings().endpoint().isEmpty()) {
            store.enqueueTest();
            renderStatus();
        }
    }

    private void openSettings() {
        startActivity(new Intent(this, DestinationsActivity.class));
    }

    private void openSimRules() {
        startActivity(new Intent(this, SimRulesActivity.class));
    }

    private void openHistory() {
        startActivity(new Intent(this, HistoryActivity.class));
    }

    private LinearLayout shortcut(Symbol symbol, String title, TextView subtitle, Runnable clicked) {
        LinearLayout card = row();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        clickableCard(card, clicked);
        var badge = icon(symbol, ACCENT);
        badge.setBackground(shape(PALE, 0, 13));
        addSide(card, badge, 42, 42, 0);
        LinearLayout labels = column();
        add(labels, text(title, 16, INK, true), 0);
        if (subtitle != null) {
            add(labels, subtitle, 2);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, WRAP, 1);
        params.setMarginStart(dp(12));
        card.addView(labels, params);
        addSide(card, icon(Symbol.NEXT, MUTED), 20, 24, 8);
        return card;
    }
}
