package com.smsforwarder.app;

import android.content.Intent;
import android.os.Build;
import android.telephony.SubscriptionManager;

final class SmsSim {
    private static final String[] SLOT_KEYS = {"android.telephony.extra.SLOT_INDEX", "slot", "phone"};

    static int slot(Intent intent) {
        int slot = SimRules.UNKNOWN;
        for (String key : SLOT_KEYS) {
            int value = intent.getIntExtra(key, SimRules.UNKNOWN);
            if (value >= 0) {
                if (slot >= 0 && slot != value) {
                    return SimRules.UNKNOWN;
                }
                slot = value;
            }
        }
        // Use the exact subscription as a fallback and reject conflicting slot metadata.
        if (Build.VERSION.SDK_INT >= 29) {
            int subscription = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX",
                    intent.getIntExtra("subscription", -1));
            if (SubscriptionManager.isValidSubscriptionId(subscription)) {
                try {
                    int resolved = SubscriptionManager.getSlotIndex(subscription);
                    if (resolved >= 0) {
                        return slot >= 0 && slot != resolved ? SimRules.UNKNOWN : resolved;
                    }
                } catch (SecurityException | UnsupportedOperationException | IllegalStateException ignored) {
                    // Explicit slot metadata is still available on devices without this API.
                }
            }
        }
        return slot;
    }

    private SmsSim() {}
}
