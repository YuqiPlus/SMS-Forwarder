package com.smsforwarder.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public final class IncomingSmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        ForwardingStore store = ForwardingStore.get(context);
        if (!store.settings().enabled() || !store.hasSmsPermission()) {
            return;
        }

        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0 || parts[0] == null) {
            return;
        }

        String sender = parts[0].getOriginatingAddress();
        StringBuilder body = new StringBuilder();
        for (SmsMessage part : parts) {
            if (part != null && part.getMessageBody() != null) {
                body.append(part.getMessageBody());
            }
        }
        if (body.length() == 0) {
            return;
        }

        store.enqueueSms(SmsSim.slot(intent), sender == null ? "" : sender, body.toString(),
                parts[0].getTimestampMillis());
    }
}
