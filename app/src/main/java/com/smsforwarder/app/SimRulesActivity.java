package com.smsforwarder.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

public final class SimRulesActivity extends AppScreen {
    private ForwardingStore store;
    private Switch bySim;
    private TextView summary;
    private LinearLayout perSimOptions;
    private Button firstChoice;
    private Button secondChoice;
    private String firstId;
    private String secondId;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = ForwardingStore.get(this);
        SimRules current = store.simRules();
        firstId = state == null ? current.sim1() : state.getString("first", "");
        secondId = state == null ? current.sim2() : state.getString("second", "");

        LinearLayout body = page("SIM 卡规则", true, null);
        LinearLayout mode = surface();
        bySim = new Switch(this);
        bySim.setText(R.string.sim_mode);
        bySim.setTextSize(16);
        bySim.setTextColor(INK);
        bySim.setMinHeight(dp(48));
        bySim.setChecked(state == null ? current.bySim() : state.getBoolean("bySim"));
        add(mode, bySim, 0);
        summary = text("", 14, MUTED, false);
        add(mode, summary, 4);
        add(body, mode, 0);

        perSimOptions = column();
        firstChoice = addSlot(perSimOptions, "SIM 1", 0, 0);
        secondChoice = addSlot(perSimOptions, "SIM 2", 1, 10);
        add(perSimOptions, text("按系统卡槽编号设置。无法识别来源的短信会跳过，换卡后请核对规则。",
                13, MUTED, false), 12);
        add(body, perSimOptions, 13);

        add(body, action("保存规则", ACCENT, WHITE, this::save), 16);
        bySim.setOnCheckedChangeListener((view, checked) -> refresh());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("first", firstId);
        state.putString("second", secondId);
        state.putBoolean("bySim", bySim.isChecked());
    }

    private Button addSlot(LinearLayout parent, String title, int slot, int margin) {
        LinearLayout card = surface();
        section(card, title, 0);
        Button choice = action("", PALE, ACCENT, () -> choose(slot));
        add(card, choice, 8);
        add(parent, card, margin);
        return choice;
    }

    private void refresh() {
        boolean enabled = bySim.isChecked();
        perSimOptions.setVisibility(enabled ? View.VISIBLE : View.GONE);
        summary.setText(enabled ? "为每张卡选择一个接收端，或选择不转发。"
                : "所有收到的短信发往首页的当前接收端。");
        ForwardingStore.Destinations destinations = store.destinations();
        firstChoice.setText(destinationLabel(destinations.find(firstId)));
        secondChoice.setText(destinationLabel(destinations.find(secondId)));
        firstChoice.setContentDescription(getString(R.string.sim_route_choice, 1, firstChoice.getText()));
        secondChoice.setContentDescription(getString(R.string.sim_route_choice, 2, secondChoice.getText()));
    }

    private String destinationLabel(ForwardingStore.Destination destination) {
        return destination == null ? "不转发" : destination.displayName();
    }

    private void choose(int slot) {
        List<ForwardingStore.Destination> choices = store.destinations().entries();
        if (choices.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("还没有接收端")
                    .setMessage("添加 Webhook 接收端后，就可以为这张卡设置转发。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("去添加", (dialog, button) ->
                            startActivity(new Intent(this, DestinationsActivity.class)))
                    .show();
            return;
        }
        String selected = slot == 0 ? firstId : secondId;
        String[] labels = new String[choices.size() + 1];
        labels[0] = "不转发";
        int checked = 0;
        for (int index = 0; index < choices.size(); index++) {
            ForwardingStore.Destination choice = choices.get(index);
            labels[index + 1] = choice.nickname().isEmpty() ? choice.displayName()
                    : getString(R.string.named_destination, choice.displayName(), choice.resolvedPlatform().label);
            if (choice.id().equals(selected)) {
                checked = index + 1;
            }
        }
        new AlertDialog.Builder(this).setTitle(getString(R.string.sim_choice_title, slot + 1))
                .setSingleChoiceItems(labels, checked, (dialog, index) -> {
                    String id = index == 0 ? "" : choices.get(index - 1).id();
                    if (slot == 0) {
                        firstId = id;
                    } else {
                        secondId = id;
                    }
                    refresh();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void save() {
        ForwardingStore.Destinations destinations = store.destinations();
        SimRules next = new SimRules(bySim.isChecked(),
                destinations.find(firstId) == null ? "" : firstId, destinations.find(secondId) == null ? "" : secondId);
        if (store.saveSimRules(next)) {
            finish();
        } else {
            showSaveError();
        }
    }
}
