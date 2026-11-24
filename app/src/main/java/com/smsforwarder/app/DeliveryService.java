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

        ForwardingStore.Settings settings = store.settings();
        if (!store.canSend(delivery, settings)) {
            store.delete(jobId);
            store.report(delivery.test(), "配置已关闭或短信权限已撤销");
            return false;
        }
        if (delivery.attempts() >= MAX_ATTEMPTS
                || System.currentTimeMillis() - delivery.queuedAt() >= MAX_AGE_MS) {
            store.delete(jobId);
            store.report(delivery.test(), "超过发送时限或重试次数");
            return false;
        }

        int attempt = delivery.attempts() + 1;
        store.setAttempts(jobId, attempt);
        int queueVersion = store.queueVersion();
        Future<?> task = network.submit(() -> {
            boolean cancelled = queueVersion != store.queueVersion() || Thread.currentThread().isInterrupted();
            Webhook.Result result = cancelled ? null : Webhook.send(settings, delivery);
            main.post(() -> complete(parameters, delivery, attempt, result));
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
        boolean retry = current != null && result != null && result.retry() && attempt < MAX_ATTEMPTS;
        if (current != null && result != null) {
            if (!retry) {
                store.delete(jobId);
            }
            String suffix = retry ? "，将自动重试" : result.retry() ? "，已停止重试" : "";
            store.report(delivery.test(), result.message() + suffix);
        }
        jobFinished(parameters, retry);
    }

    @Override
    public boolean onStopJob(JobParameters parameters) {
        RunningJob running = active.remove(parameters.getJobId());
        if (running != null) {
            running.task().cancel(true);
        }
        return ForwardingStore.get(this).find(parameters.getJobId()) != null;
    }

    @Override
    public void onDestroy() {
        active.clear();
        network.shutdownNow();
        super.onDestroy();
    }
}
