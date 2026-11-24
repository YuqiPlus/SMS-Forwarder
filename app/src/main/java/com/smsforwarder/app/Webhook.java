package com.smsforwarder.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

final class Webhook {
    record Result(String message, boolean retry) {}

    static String validateEndpoint(String input) {
        try {
            URI uri = new URI(input.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65_535) {
                throw new IllegalArgumentException();
            }
            return uri.toASCIIString();
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("请输入完整的 HTTPS Webhook 地址");
        }
    }

    static String validateToken(String input) {
        String token = input.trim();
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            if (character < 33 || character > 126) {
                throw new IllegalArgumentException("Token 只能包含可打印的 ASCII 字符，且不能包含空格");
            }
        }
        return token;
    }

    static Result send(ForwardingStore.Settings settings, ForwardingStore.Delivery delivery) {
        HttpsURLConnection connection = null;
        try {
            byte[] json = new JSONObject()
                    .put("id", delivery.eventId())
                    .put("event", delivery.test() ? "sms.test" : "sms.received")
                    .put("sender", delivery.sender())
                    .put("body", delivery.body())
                    .put("receivedAt", delivery.receivedAt())
                    .toString().getBytes(StandardCharsets.UTF_8);

            connection = (HttpsURLConnection) URI.create(settings.endpoint()).toURL().openConnection();
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(12_000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Idempotency-Key", delivery.eventId());
            if (!settings.token().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + settings.token());
            }
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(json.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(json);
            }

            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) {
                return new Result("发送成功（HTTP " + status + "）", false);
            }
            boolean retry = status == 408 || status == 425 || status == 429 || status >= 500;
            return new Result("服务器返回 HTTP " + status, retry);
        } catch (SSLHandshakeException | SSLPeerUnverifiedException exception) {
            return new Result("HTTPS 安全连接失败，请检查服务器证书与配置", false);
        } catch (IOException exception) {
            return new Result("网络请求失败", true);
        } catch (JSONException | IllegalArgumentException exception) {
            return new Result("请求配置或数据无效", false);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private Webhook() {}
}
