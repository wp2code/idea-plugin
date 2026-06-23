package com.lsy.idea.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * HTTP 拉取资源编码工具（Java 11 HttpClient） 发起 GET 请求，成功时返回响应体字符串
 */
public class HttpCodeFetcher {
    
    public static final String ERROR = "ERROR";
    
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    
    /**
     * 发起 GET 请求拉取资源编码（无自定义请求头）
     *
     * @param url 目标地址
     * @return 响应体字符串（trim 后）
     * @throws HttpFetchException 请求失败或 HTTP 错误时抛出
     */
    @NotNull
    public static String fetchCode(@NotNull String url) throws HttpFetchException {
        return fetchCode(url, null, false);
    }
    
    /**
     * 发起 GET 请求拉取资源编码（支持自定义请求头）
     *
     * @param url     目标地址
     * @param headers 自定义请求头（为 null 或空时不添加额外请求头）
     * @param isTest  是否测试
     * @return 响应体字符串（trim 后）
     * @throws HttpFetchException 请求失败或 HTTP 错误时抛出
     */
    @NotNull
    public static String fetchCode(@NotNull String url, @Nullable Map<String, String> headers, boolean isTest) throws HttpFetchException {
        if (url.isBlank()) {
            throw new HttpFetchException("HTTP 拉取地址不能为空");
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url.trim())).timeout(Duration.ofSeconds(10)).GET();
            // 添加自定义请求头
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (key != null && !key.isBlank() && value != null) {
                        builder.header(key.trim(), value);
                    }
                }
            }
            
            HttpRequest request = builder.build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                String body = response.body();
                return extractData(body != null ? body.trim() : "", isTest);
            } else {
                String body = response.body();
                String errorMsg = body != null && !body.isBlank() ? body.substring(0, Math.min(200, body.length())) : "无响应体";
                throw new HttpFetchException("编码拉取失败，接口返回 HTTP " + statusCode + "：" + errorMsg);
            }
        } catch (HttpFetchException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new HttpFetchException("URL 格式不正确：" + e.getMessage(), e);
        } catch (java.net.ConnectException e) {
            throw new HttpFetchException("网络请求失败，请检查地址和网络：" + e.getMessage(), e);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new HttpFetchException("请求超时，请检查接口是否正常", e);
        } catch (Exception e) {
            throw new HttpFetchException("网络请求失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 解析响应体：如果是 JSON 且包含 data 字段，取 data 的字符串值；否则返回原始内容
     *
     * @param body
     * @param isTest
     * @return
     */
    private static String extractData(String body, boolean isTest) {
        if (body == null || body.isBlank()) {
            return ERROR;
        }
        try {
            JsonElement root = JsonParser.parseString(body);
            if (isTest) {
                return body;
            }
            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("code")) {
                    JsonElement data = obj.get("code");
                    if (data.isJsonNull()) {
                        return ERROR;
                    }
                    if (!"0".equals(data.getAsString().trim())) {
                        return ERROR;
                    }
                }
                if (obj.has("data")) {
                    JsonElement data = obj.get("data");
                    if (data.isJsonNull()) {
                        return ERROR;
                    }
                    // data 是字符串直接返回，否则返回其 JSON 表示
                    if (data.isJsonPrimitive()) {
                        return data.getAsString().trim();
                    }
                    return data.toString().trim();
                }
            }
        } catch (JsonSyntaxException ignored) {
            return ERROR;
        }
        return body;
    }
    
    /**
     * HTTP 拉取异常
     */
    public static class HttpFetchException extends Exception {
        
        public HttpFetchException(String message) {
            super(message);
        }
        
        public HttpFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
