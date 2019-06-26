package com.indeed.jiraactions.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeed.jiraactions.JiraActionsIndexBuilderConfig;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ApiCaller {
    protected final JiraActionsIndexBuilderConfig config;

    private static final Logger log = LoggerFactory.getLogger(ApiCaller.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient client = new OkHttpClient();
    private final String authentication;
    private String jsessionId = null;
    private String upstream = null;
    private String cookies = "";
    private String pinnedNode = null;

    public ApiCaller(final JiraActionsIndexBuilderConfig config) {
        this.config = config;
        this.authentication = getBasicAuth();
    }



    public JsonNode getJsonNode(final String url) throws IOException {
        client.connectionPool();
        Request request = new Request.Builder()
                .header("Authorization", this.authentication)
                .url(url)
                .build();
        Response response = client.newCall(request).execute();
        try (ResponseBody responseBody = response.body()){

            if (!response.isSuccessful()) {
                final StringBuilder sb = new StringBuilder();
                Headers requestHeaders = request.headers();
                Headers responseHeaders = response.headers();

                sb.append("{");
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
                sb.append("}");
                sb.append("}");

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
                sb.append("\"Error Body\": \"").append(responseBody.string()).append("\"");
                sb.append("}");
                sb.append("}");
                log.debug("Encountered connection error: " + sb);
                throw new IOException(String.valueOf(response));
            }
            return objectMapper.readTree(responseBody.string());
        } catch (IOException e) {
            throw e;
        }
    }


//    public JsonNode getJsonNode(final String url) throws IOException {
//        HttpsURLConnection urlConnection = null;
//        Map<String, List<String>> headers = null;
//        BufferedReader br = null;
//        String apiResults = null;
//        try {
//            urlConnection = getURLConnection(url);
//            headers = urlConnection.getRequestProperties();
//            final InputStream in = urlConnection.getInputStream();
//            br = new BufferedReader(new InputStreamReader(in));
//            apiResults = br.readLine();
//
//            final String anodeId = urlConnection.getHeaderField("X-ANODEID");
//
//            if(!Objects.equals(pinnedNode, anodeId)) {
//                if(pinnedNode != null) {
//                    log.warn("Expected X-ANODEID={} but found {}", pinnedNode, anodeId);
//                }
//                final Map<String, List<String>> responseHeaders = urlConnection.getHeaderFields();
//                final List<String> cookies = responseHeaders.get("Set-Cookie");
//                if(cookies != null) {
//                    for (final String cookie : cookies) {
//                        if (cookie.startsWith("JSESSIONID=")) {
//                            final int start = "JSESSIONID=".length();
//                            final int end = cookie.contains(";") ? cookie.indexOf(";") : cookie.length();
//                            jsessionId = cookie.substring(start, end);
//                        } else if (cookie.startsWith("upstream")) {
//                            final int start = "upstream=".length();
//                            final int end = cookie.contains(";") ? cookie.indexOf(";") : cookie.length();
//                            upstream = cookie.substring(start, end);
//                        }
//                    }
//                    if (jsessionId != null || upstream != null) {
//                        setCookies();
//                        pinnedNode = anodeId;
//                        log.info("Set JSESSION={};upstream={}. Pinning to X-ANODEID={}",
//                                jsessionId, upstream, anodeId);
//                    }
//                }
//            }
//            return objectMapper.readTree(apiResults);
//        } catch (final IOException e) {
//            final StringBuilder sb = new StringBuilder();
//
//            sb.append("{");
//            sb.append("\"Request\": {");
//
//            sb.append("\"URL\": \"").append(url).append("\",");
//            if (headers != null) {
//                sb.append("\"Headers\": {");
//                for (final Map.Entry<String, List<String>> header : headers.entrySet()) {
//                    final String key = header.getKey();
//                    final String value;
//                    if ("Authorization".equals(key)) {
//                        value = "<Omitted>";
//                    } else {
//                        value = String.join(",", header.getValue());
//                    }
//                    sb.append("\"").append(key).append("\": \"").append(value).append("\",");
//                }
//            }
//            sb.append("}");
//
//            sb.append("}");
//
//            if (urlConnection != null) {
//                sb.append(", \"Response\": {");
//                for (final Map.Entry<String, List<String>> header : urlConnection.getHeaderFields().entrySet()) {
//                    final String key = header.getKey();
//                    final String value;
//                    if ("Set-Cookie".equals(key)) {
//                        value = "<Omitted>";
//                    } else {
//                        value = String.join(",", header.getValue());
//                    }
//                    sb.append("\"").append(key).append("\": \"").append(value).append("\",");
//                }
//                sb.append("\"Code\": ").append(urlConnection.getResponseCode()).append(",");
//                sb.append("\"Message\": \"").append(urlConnection.getResponseMessage()).append("\",");
//            }
//            if (apiResults != null) {
//                sb.append("\"Body\": \"").append(apiResults).append("\"");
//            }
//            if (urlConnection != null) {
//                final InputStream error = urlConnection.getErrorStream();
//                if(error != null) {
//                    final BufferedReader errorBr = new BufferedReader(new InputStreamReader(error));
//                    sb.append("\"Error Body\": \"");
//                    String line = errorBr.readLine();
//                    while (line != null) {
//                        sb.append(line).append(System.lineSeparator());
//                        line = errorBr.readLine();
//                    }
//                    errorBr.close();
//                } else {
//                    sb.append("Unable to open error stream for reading");
//                }
//                sb.append("\"");
//            }
//
//            sb.append("}");
//            sb.append("}");
//            log.error("Encountered connection error: " + sb);
//            throw e;
//        } finally {
//            if (br != null) {
//                try {
//                    br.close();
//                } catch (final IOException ignored) {
//                }
//            }
//        }
//    }

    private HttpsURLConnection getURLConnection(final String urlString) throws IOException {
        final URL url = new URL(urlString);
        final HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        urlConnection.setRequestProperty("Authorization", authentication);

        if(cookies.length() > 0) {
            urlConnection.setRequestProperty("Cookie", cookies);
        }
        return urlConnection;
    }

    private void setCookies() {
        final StringBuilder sb = new StringBuilder();
        if(jsessionId != null) {
            sb.append("JSESSIONID=").append(jsessionId);
        }
        if(upstream != null) {
            if(sb.length() > 0) {
                sb.append(";");
            }
            sb.append("upstream=").append(upstream);
        }
        cookies = sb.toString();
    }

    private String getBasicAuth() {
        final String userPass = config.getJiraUsername() + ":" + config.getJiraPassword();
        final String basicAuth = "Basic " + new String(new Base64().encode(userPass.getBytes()));
        return basicAuth;
    }
}
