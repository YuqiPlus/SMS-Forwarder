package com.smsforwarder.app;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class DeliveryService extends JobService {
    private static final int MAX_ATTEMPTS = 6;
    private static final long MAX_AGE_MS = 24L * 60 * 60 * 1000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newFixedThreadPool(2);
    private final Map<Integer, RunningJob> active = new HashMap<>();

    private record RunningJob(JobParameters parameters, Future<?> task) {}

    @Override
    public boolean onStartJob(JobParameters parameters) {
        int jobId = parameters.getJobId();
        ForwardingStore store = ForwardingStore.get(this);
        ForwardingStore.Delivery delivery = store.find(jobId);
        if (delivery == null) {
            return false;
        }

        ForwardingStore.Settings settings = store.settingsFor(delivery);
        if (settings == null) {
            store.deliveryResult(jobId, delivery.eventId(), delivery.test(), ForwardingHistory.State.CANCELLED,
                    "转发规则已更改、已关闭或短信权限已撤销");
            return false;
        }
        if (delivery.attempts() >= MAX_ATTEMPTS
                || System.currentTimeMillis() - delivery.queuedAt() >= MAX_AGE_MS) {
            store.deliveryResult(jobId, delivery.eventId(), delivery.test(), ForwardingHistory.State.FAILED,
                    "超过发送时限或重试次数");
            return false;
        }

        int attempt = delivery.attempts() + 1;
        int queueVersion = store.queueVersion();
        Future<?> task = network.submit(() -> {
            // A job stopped while waiting for a worker must not spend an attempt.
            Webhook.Result result = null;
            if (queueVersion == store.queueVersion() && !Thread.currentThread().isInterrupted()
                    && store.recordAttempt(jobId, attempt)
                    && queueVersion == store.queueVersion() && !Thread.currentThread().isInterrupted()) {
                result = Webhook.send(settings, delivery);
            }
            Webhook.Result completed = result;
            main.post(() -> complete(parameters, delivery, attempt, completed));
        });
        active.put(jobId, new RunningJob(parameters, task));
        return true;
    }

    private void complete(JobParameters parameters, ForwardingStore.Delivery delivery,
                          int attempt, Webhook.Result result) {
        int jobId = parameters.getJobId();
        RunningJob running = active.get(jobId);
        if (running == null || running.parameters() != parameters) {
            return;
        }
        active.remove(jobId);

        ForwardingStore store = ForwardingStore.get(this);
        ForwardingStore.Delivery current = store.find(jobId);
        boolean retry = current != null && (result == null || (result.retry() && attempt < MAX_ATTEMPTS));
        if (current != null && result != null) {
            String suffix = retry ? "，将自动重试" : result.retry() ? "，已停止重试" : "";
            ForwardingHistory.State state = result.success() ? ForwardingHistory.State.SENT
                    : retry ? ForwardingHistory.State.RETRYING : ForwardingHistory.State.FAILED;
            store.deliveryResult(jobId, delivery.eventId(), delivery.test(), state, result.message() + suffix);
        }
        jobFinished(parameters, retry);
    }

    @Override
    public boolean onStopJob(JobParameters parameters) {
        int jobId = parameters.getJobId();
        RunningJob running = active.get(jobId);
        if (running != null && running.parameters() == parameters) {
            active.remove(jobId);
            running.task().cancel(true);
        }
        return ForwardingStore.get(this).find(jobId) != null;
    }

    @Override
    public void onDestroy() {
        active.clear();
        network.shutdownNow();
        super.onDestroy();
    }
}
