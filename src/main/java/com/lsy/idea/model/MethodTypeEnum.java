/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
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
