package com.smsforwarder.app;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;

public final class MainActivity extends Activity {
    private static final int SMS_PERMISSION_REQUEST = 10;
    private static final int BG = 0xfff4f7f5;
    private static final int INK = 0xff172a24;
    private static final int MUTED = 0xff4d6159;
    private static final int ACCENT = 0xff186a55;
    private static final int PALE = 0xffe7f3ec;
    private static final int STROKE = 0xffdce7e0;

    private ForwardingStore store;
    private EditText endpoint;
    private EditText token;
    private TextView state;
    private TextView permission;
    private Button toggle;
    private TextView lastResult;
    private TextView pending;
    private final SharedPreferences.OnSharedPreferenceChangeListener statusListener =
            (preferences, key) -> renderStatus();

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        store = ForwardingStore.get(this);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        buildScreen();
        ForwardingStore.Settings settings = store.settings();
        endpoint.setText(settings.endpoint());
        token.setText(settings.token());
    }

    @Override
    protected void onStart() {
        super.onStart();
        store.watchStatus(statusListener);
        store.recoverUnscheduled();
        renderStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderStatus();
    }

    @Override
    protected void onStop() {
        store.unwatchStatus(statusListener);
        super.onStop();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets padding = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(padding.left, padding.top, padding.right, padding.bottom);
                return insets;
            });
        }
        setContentView(scroll);

        LinearLayout screen = column();
        screen.setPadding(dp(22), dp(24), dp(22), dp(24));
        screen.setFocusableInTouchMode(true);
        scroll.addView(screen);

        add(screen, text("短信转发", 32, INK, true), 0);
        add(screen, text("将手机新收到的短信发送到你的 Webhook。", 15, MUTED, false), 6);

        LinearLayout statusCard = card(PALE, 0);
        add(screen, statusCard, 22);
        state = text("", 22, INK, true);
        add(statusCard, state, 0);
        permission = text("", 14, MUTED, false);
        add(statusCard, permission, 5);
        toggle = button("", true);
        toggle.setOnClickListener(view -> toggleForwarding());
        add(statusCard, toggle, 14);

        LinearLayout settingsCard = card(Color.WHITE, STROKE);
        add(screen, settingsCard, 14);
        add(settingsCard, text("接收端设置", 20, INK, true), 0);
        TextView urlLabel = text("Webhook 地址", 14, INK, true);
        add(settingsCard, urlLabel, 14);
        endpoint = input("https://example.com/webhook",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlLabel.setLabelFor(endpoint.getId());
        add(settingsCard, endpoint, 5);

        TextView tokenLabel = text("Bearer Token（可选）", 14, INK, true);
        add(settingsCard, tokenLabel, 12);
        token = input("接收端要求鉴权时填写",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenLabel.setLabelFor(token.getId());
        add(settingsCard, token, 5);

        Button save = button("保存设置", false);
        save.setOnClickListener(view -> {
            if (saveSettings(store.settings().enabled())) {
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            }
        });
        add(settingsCard, save, 14);

        Button test = button("发送测试消息", false);
        test.setOnClickListener(view -> sendTest());
        add(settingsCard, test, 4);

        LinearLayout resultCard = card(Color.WHITE, STROKE);
        add(screen, resultCard, 14);
        add(resultCard, text("最近一次状态", 20, INK, true), 0);
        lastResult = text("", 15, INK, false);
        add(resultCard, lastResult, 8);
        pending = text("", 14, MUTED, false);
        add(resultCard, pending, 5);

        add(screen, text("开启后，系统允许转发的短信正文和发件人会发送到你配置的地址。"
                + "只填写你信任的接收端。", 13, MUTED, false), 15);
        screen.requestFocus();
    }

    private void toggleForwarding() {
        if (store.settings().enabled()) {
            ForwardingStore.Settings current = store.settings();
            if (!store.save(new ForwardingStore.Settings(current.endpoint(), current.token(), false))) {
                showSaveError();
            }
            renderStatus();
            return;
        }

        if (!saveSettings(false)) {
            return;
        }
        if (store.hasSmsPermission()) {
            enableForwarding();
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
            enableForwarding();
        } else {
            Toast.makeText(this, "未获得短信权限。可在系统的应用权限设置中检查。", Toast.LENGTH_LONG).show();
        }
    }

    private void enableForwarding() {
        ForwardingStore.Settings current = store.settings();
        if (!store.save(new ForwardingStore.Settings(current.endpoint(), current.token(), true))) {
            showSaveError();
        }
        renderStatus();
    }

    private boolean saveSettings(boolean enabled) {
        endpoint.setError(null);
        token.setError(null);
        String url;
        String secret;
        try {
            url = Webhook.validateEndpoint(endpoint.getText().toString());
        } catch (IllegalArgumentException error) {
            endpoint.setError(error.getMessage());
            endpoint.requestFocus();
            return false;
        }
        try {
            secret = Webhook.validateToken(token.getText().toString());
        } catch (IllegalArgumentException error) {
            token.setError(error.getMessage());
            token.requestFocus();
            return false;
        }

        if (!store.save(new ForwardingStore.Settings(url, secret, enabled))) {
            showSaveError();
            return false;
        }
        endpoint.setText(url);
        token.setText(secret);
        renderStatus();
        return true;
    }

    private void sendTest() {
        ForwardingStore.Settings current = store.settings();
        if (current.endpoint().isEmpty()
                || !current.endpoint().equals(endpoint.getText().toString().trim())
                || !current.token().equals(token.getText().toString().trim())) {
            Toast.makeText(this, "请先保存接收端设置", Toast.LENGTH_SHORT).show();
            return;
        }
        store.enqueue(true, "test", "这是一条 Webhook 测试消息。", System.currentTimeMillis());
        renderStatus();
    }

    private void renderStatus() {
        boolean enabled = store.settings().enabled();
        boolean granted = store.hasSmsPermission();
        state.setText(enabled ? (granted ? "转发已开启" : "转发已暂停") : "转发已关闭");
        permission.setText(granted ? "短信接收权限已授予" : "短信接收权限未授予");
        toggle.setText(enabled ? "关闭转发并清空队列" : "开启短信转发");

        lastResult.setText(store.status());
        long time = store.statusTime();
        String timeLabel = time == 0 ? "" : "  ·  " + DateFormat.format("MM-dd HH:mm:ss", new Date(time));
        pending.setText("待处理 " + store.pendingCount() + " 条" + timeLabel);
    }

    private void showSaveError() {
        Toast.makeText(this, "保存失败，请检查设备存储空间", Toast.LENGTH_LONG).show();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout card(int color, int stroke) {
        LinearLayout layout = column();
        layout.setBackground(background(color, stroke, 18));
        layout.setPadding(dp(16), dp(15), dp(16), dp(15));
        return layout;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private EditText input(String hint, int type) {
        EditText field = new EditText(this);
        field.setId(View.generateViewId());
        field.setSingleLine(true);
        field.setInputType(type);
        field.setSaveEnabled(false);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        field.setTextSize(15);
        field.setTextColor(INK);
        field.setHintTextColor(MUTED);
        field.setHint(hint);
        field.setMinHeight(dp(48));
        field.setPadding(dp(12), dp(7), dp(12), dp(7));
        field.setBackground(background(BG, STROKE, 10));
        return field;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setTextSize(15);
        button.setTextColor(primary ? Color.WHITE : ACCENT);
        button.setBackgroundTintList(ColorStateList.valueOf(primary ? ACCENT : PALE));
        return button;
    }

    private GradientDrawable background(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private void add(LinearLayout parent, View child, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMargin);
        parent.addView(child, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
