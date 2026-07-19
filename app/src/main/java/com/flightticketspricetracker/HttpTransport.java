package com.flightticketspricetracker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

public final class HttpTransport {
    public static final class Response {
        public final int statusCode;
        public final String body;

        Response(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }
    }

    private HttpTransport() {}

    public static Response get(String url, Map<String, String> headers) throws IOException {
        return request("GET", url, headers, null, null);
    }

    public static Response post(String url, Map<String, String> headers, String contentType, String body) throws IOException {
        return request("POST", url, headers, contentType, body);
    }

    private static Response request(
            String method,
            String url,
            Map<String, String> headers,
            String contentType,
            String body
    ) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        for (Map.Entry<String, String> header : (headers == null ? Collections.<String, String>emptyMap() : headers).entrySet()) {
            if (header.getValue() != null && !header.getValue().isEmpty()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
        }

        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", contentType == null ? "application/json" : contentType);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream();
        String responseBody = read(stream);
        connection.disconnect();
        return new Response(status, responseBody);
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        return body.toString();
    }
}
