package com.smsforwarder.app;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.UUID;

public final class DestinationEditorActivity extends AppScreen {
    static final String DESTINATION_ID = "destination_id";

    private record Draft(String nickname, String endpoint, String token, WebhookPlatform platform) {}

    private ForwardingStore store;
    private ForwardingStore.Destination original;
    private WebhookPlatform platform;
    private WebhookPlatform shownRoute;
    private LinearLayout platformPicker;
    private TextView detected;
    private EditText nickname;
    private EditText endpoint;
    private TextView tokenLabel;
    private EditText token;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = ForwardingStore.get(this);
        ForwardingStore.Destinations existing = store.destinations();
        String id = getIntent().getStringExtra(DESTINATION_ID);
        original = existing.find(id);
        if (id != null && original == null) {
            finish();
            return;
        }
        Draft draft = getLastNonConfigurationInstance() instanceof Draft retained ? retained
                : original == null ? new Draft("", "", "", WebhookPlatform.AUTO)
                : new Draft(original.nickname(), original.endpoint(), original.token(), original.platform());
        platform = draft.platform();
        LinearLayout body = page(original == null ? "添加接收端" : "编辑接收端", true, null);
        LinearLayout form = surface();
        add(body, form, 0);

        add(form, text("接收平台", 14, INK, true), 0);
        platformPicker = row();
        platformPicker.setMinimumHeight(dp(54));
        platformPicker.setPadding(dp(10), dp(6), dp(12), dp(6));
        platformPicker.setBackground(ripple(BG, STROKE, 11));
        platformPicker.setFocusable(true);
        platformPicker.setOnClickListener(view -> choosePlatform());
        add(form, platformPicker, 7);

        nickname = input("例如：工作通知群", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nickname.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
        field(form, "昵称（可选）", nickname, 15);

        endpoint = input("", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field(form, "Webhook 地址", endpoint, 15);
        detected = text("", 13, MUTED, false);
        add(form, detected, 5);

        token = input("接收端启用鉴权时填写",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenLabel = field(form, "", token, 15);

        nickname.setText(draft.nickname());
        endpoint.setText(draft.endpoint());
        token.setText(draft.token());
        refreshForm();
        endpoint.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { refreshForm(); }
            @Override public void afterTextChanged(Editable text) {}
        });

        add(body, action("保存接收端", ACCENT, WHITE, () -> save(false)), 16);
        if (original != null && !original.id().equals(existing.currentId())) {
            add(body, action("保存并设为当前", PALE, ACCENT, () -> save(true)), 8);
        }
        if (original != null) {
            add(body, action("删除接收端", BG, DANGER, this::confirmDelete), 12);
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // Keep the draft in memory through rotation without writing credentials to instance state.
        return endpoint == null ? null : new Draft(nickname.getText().toString(), endpoint.getText().toString(),
                token.getText().toString(), platform);
    }

    private TextView field(LinearLayout form, String label, EditText input, int top) {
        TextView title = text(label, 14, INK, true);
        title.setLabelFor(input.getId());
        add(form, title, top);
        add(form, input, 6);
        return title;
    }

    private EditText input(String hint, int inputType) {
        EditText field = new EditText(this);
        field.setId(View.generateViewId());
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setSaveEnabled(false);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        field.setTextSize(15);
        field.setTextColor(INK);
        field.setHintTextColor(MUTED);
        field.setHint(hint);
        field.setMinHeight(dp(50));
        field.setPadding(dp(12), dp(8), dp(12), dp(8));
        field.setBackground(shape(BG, STROKE, 11));
        return field;
    }

    private void choosePlatform() {
        WebhookPlatform[] options = WebhookPlatform.values();
        String[] labels = new String[options.length];
        for (int index = 0; index < options.length; index++) {
            labels[index] = options[index].label;
        }
        new AlertDialog.Builder(this).setTitle("选择接收平台")
                .setSingleChoiceItems(labels, platform.ordinal(), (dialog, index) -> {
                    platform = options[index];
                    endpoint.setError(null);
                    refreshForm();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void refreshForm() {
        String url = endpoint.getText().toString().trim();
        WebhookPlatform route = platform.resolve(url);
        if (shownRoute != null && shownRoute != route) {
            token.setText("");
            token.setError(null);
        }
        shownRoute = route;
        platformPicker.removeAllViews();
        addSide(platformPicker, platformBadge(platform, 33), 33, 33, 0);
        LinearLayout.LayoutParams label = new LinearLayout.LayoutParams(0, WRAP, 1);
        label.setMarginStart(dp(9));
        platformPicker.addView(text(platform.label, 15, INK, true), label);
        addSide(platformPicker, icon(Symbol.NEXT, MUTED), 18, 22, 8);
        platformPicker.setContentDescription(getString(R.string.platform_picker_label, platform.label));

        endpoint.setHint(platform == WebhookPlatform.AUTO ? "粘贴完整的 HTTPS 地址" : platform.endpointHint);
        detected.setVisibility(platform == WebhookPlatform.AUTO ? View.VISIBLE : View.GONE);
        detected.setText(url.isEmpty() ? getString(R.string.platform_automatic_hint)
                : getString(R.string.platform_detected, route.label));
        boolean showToken = route.acceptsCredential() && (platform != WebhookPlatform.AUTO || !url.isEmpty());
        tokenLabel.setVisibility(showToken ? View.VISIBLE : View.GONE);
        token.setVisibility(showToken ? View.VISIBLE : View.GONE);
        tokenLabel.setText(getString(R.string.webhook_optional_credential, route.credentialLabel));
    }

    private void save(boolean makeCurrent) {
        endpoint.setError(null);
        token.setError(null);
        String url;
        try {
            url = Webhook.validateEndpoint(endpoint.getText().toString(), platform);
        } catch (IllegalArgumentException error) {
            endpoint.setError(error.getMessage());
            endpoint.requestFocus();
            return;
        }
        String secret;
        try {
            secret = platform.resolve(url).acceptsCredential() ? Webhook.validateToken(token.getText().toString()) : "";
        } catch (IllegalArgumentException error) {
            token.setError(error.getMessage());
            token.requestFocus();
            return;
        }
        String id = original == null ? UUID.randomUUID().toString() : original.id();
        ForwardingStore.Destination destination = new ForwardingStore.Destination(
                id, nickname.getText().toString().trim(), url, secret, platform);
        if (store.saveDestination(destination, makeCurrent)) {
            finish();
        } else {
            showSaveError();
        }
    }

    private void confirmDelete() {
        boolean current = original.id().equals(store.destinations().currentId());
        SimRules rules = store.simRules();
        boolean usedBySim = rules.bySim() && (original.id().equals(rules.sim1()) || original.id().equals(rules.sim2()));
        String consequence = usedBySim ? "使用这个接收端的 SIM 卡会停止转发。"
                : current && !rules.bySim() ? "删除当前接收端会关闭转发并清空待发消息。" : "删除后可以重新添加。";
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.remove_destination, original.displayName()))
                .setMessage(consequence)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, button) -> {
                    if (store.removeDestination(original.id())) {
                        finish();
                    } else {
                        showSaveError();
                    }
                })
                .show();
    }
}
