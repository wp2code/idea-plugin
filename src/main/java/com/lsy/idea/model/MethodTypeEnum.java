package com.lsy.idea.model;

/**
 * @author liuweiping
 * @date 2026-06-23
 **/
public enum MethodTypeEnum {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH;
    
    public static MethodTypeEnum getInstance(String method) {
        return MethodTypeEnum.valueOf(method.toUpperCase());
    }
}
