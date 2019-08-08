package com.indeed.jiraactions.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeed.jiraactions.JiraActionsIndexBuilderConfig;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ApiCaller {
    protected final JiraActionsIndexBuilderConfig config;

    private static final Logger log = LoggerFactory.getLogger(ApiCaller.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client;
    private final String authentication;

    private String pinnedNode = null;

    public ApiCaller(final JiraActionsIndexBuilderConfig config) {
        this.config = config;
        this.authentication = getBasicAuth();
        this.client = new OkHttpClient.Builder()
                .cookieJar(setupCookieJar())
                .build();
    }

    public JsonNode getJsonNode(final String url) throws IOException {
        final Request request = new Request.Builder()
                .header("Authorization", this.authentication)
                .header("Cache-Control", "no-store")
                .url(url)
                .build();
        final Response response = client.newCall(request).execute();

        try (final ResponseBody responseBody = response.body()) {
            
            final String anodeId = response.header("X-ANODEID");

            if(!Objects.equals(pinnedNode, anodeId)) {
                if (pinnedNode != null) {
                    log.warn("Expected X-ANODEID={} but found {}", pinnedNode, anodeId);
                }
                pinnedNode = anodeId;
            }
            if (!response.isSuccessful()) {
                final StringBuilder sb = new StringBuilder();
                final Headers requestHeaders = request.headers();
                final Headers responseHeaders = response.headers();

                sb.append('{');
                sb.append("\"Request\": {");
                sb.append("\"URL\": \"").append(url).append("\",");

                sb.append("\"Headers\": {");
                for (int i = 0, size = requestHeaders.size(); i < size; i++) {
                    final String key = requestHeaders.name(i);
                    final String value;
                    if ("Authorization".equals(key)) {
                        value = "<Omitted>";
                    } else {
                        value = String.join(",", requestHeaders.value(i));
                    }
                    sb.append("\"").append(key).append("\": \"").append(value).append("\",");
                }
                sb.append('}');
                sb.append('}');

                sb.append(", \"Response\": {");
                for (int i = 0, size = responseHeaders.size(); i < size; i++) {
                    final String key = responseHeaders.name(i);
                    final String value;
                    if ("Set-Cookie".equals(key)) {
                        value = "<Omitted>";
                    } else {
                        value = String.join(",", responseHeaders.value(i));
                    }
                    sb.append("\"").append(key).append("\": \"").append(value).append("\",");
                }
                sb.append("\"Code\": ").append(response.code()).append(",");
                sb.append("\"Message\": \"").append(response.message()).append("\",");
                sb.append("\"Error Body\": \"").append(response.body().string()).append("\"");
                sb.append('}');
                sb.append('}');
                log.error("Encountered connection error: " + sb);
            }
            return objectMapper.readTree(responseBody.string());
        }
    }

    private String getBasicAuth() {
        final String userPass = config.getJiraUsername() + ":" + config.getJiraPassword();
        final String basicAuth = "Basic " + new String(new Base64().encode(userPass.getBytes()));
        return basicAuth;
    }

    private static CookieJar setupCookieJar() {
        return new CookieJar() {
            private final Map<String, Cookie> cookies = new HashMap<>();
            @Override
            public void saveFromResponse(final HttpUrl url, final List<Cookie> cookies) {
                cookies.forEach(cookie -> {
                    this.cookies.put(cookie.name(), cookie);
                });
            }

            @Override
            public List<Cookie> loadForRequest(final HttpUrl url) {
                return new ArrayList<>(cookies.values());
            }
        };
    }
}
