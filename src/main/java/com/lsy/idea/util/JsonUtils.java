package com.lsy.idea.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * JSON 工具类，基于 Gson 封装，用于 extConfig 序列化/反序列化及模板导入导出
 */
public final class JsonUtils {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private static final Type MAP_STRING_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private JsonUtils() {}

    /**
     * 对象转 JSON 字符串（格式化输出）
     */
    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    /**
     * JSON 字符串转对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) return null;
        return GSON.fromJson(json, clazz);
    }

    /**
     * JSON 字符串转泛型对象
     */
    public static <T> T fromJson(String json, Type type) {
        if (json == null || json.isBlank()) return null;
        return GSON.fromJson(json, type);
    }

    /**
     * 解析 extConfig JSON 字符串为 Map<String, String>
     * 返回空 Map 而不是 null（安全处理）
     */
    public static Map<String, String> parseExtConfig(String json) {
        if (json == null || json.isBlank() || json.equals("{}") || json.equals("null")) {
            return new HashMap<>();
        }
        try {
            Map<String, String> result = GSON.fromJson(json, MAP_STRING_TYPE);
            return result != null ? result : new HashMap<>();
        } catch (JsonSyntaxException e) {
            return new HashMap<>();
        }
    }

    /**
     * 将 extConfig Map 序列化为 JSON 字符串
     */
    public static String serializeExtConfig(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "{}";
        // 用紧凑格式存储，减少数据库体积
        return new Gson().toJson(map);
    }

    /**
     * 判断 JSON 字符串格式是否合法
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.isBlank()) return false;
        try {
            GSON.fromJson(json, Object.class);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    /**
     * 获取 Gson 实例（供外部直接使用）
     */
    public static Gson getGson() {
        return GSON;
    }
}
