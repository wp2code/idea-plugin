package com.lsy.idea.util;

import com.intellij.openapi.ui.Messages;
import com.lsy.idea.http.HttpCodeFetcher;
import com.lsy.idea.model.MethodTypeEnum;
import com.lsy.idea.model.VariableInfo;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.swing.*;

/**
 * @author liuweiping
 * @date 2026-06-23
 **/
public class CommUtil {
    
    /**
     * 默认资源类型列表
     */
    public static List<Integer> DEFAULT_RESOURCE_TYPES = List.of(1);
    
    /**
     * 默认权限类型列表
     */
    public static List<Integer> DEFAULT_PERMISSION_TYPES = Arrays.asList(0, 1, 2, 3, 4);
    
    public static List<String> ADD_PERMISSION_TYPES = Arrays.asList("add", "save", "create");
    
    public static List<String> EDIT_PERMISSION_TYPES = Arrays.asList("edit", "update", "modify");
    
    public static List<String> DELETE_PERMISSION_TYPES = Arrays.asList("delete", "remove");
    
    public static List<String> QUERY_PERMISSION_TYPES = Arrays.asList("query", "get");
    
    
    /**
     * 获取默认权限类型 (不一定准确)
     *
     * @param url                    请求地址
     * @param methodType             请求方法类型
     * @param allPermissionTypeCodes 所有权限类型代码
     * @return 权限类型
     */
    public static Integer getDefaultPermissionType(String url, MethodTypeEnum methodType, List<Integer> allPermissionTypeCodes) {
        Integer type = null;
        if (MethodTypeEnum.DELETE == methodType) {
            type = 3;
        } else if (MethodTypeEnum.PUT == methodType) {
            type = 2;
        }
        if (isContains(QUERY_PERMISSION_TYPES, url)) {
            type = 0;
        }
        if (isContains(ADD_PERMISSION_TYPES, url)) {
            type = 1;
        }
        if (isContains(EDIT_PERMISSION_TYPES, url)) {
            type = 2;
        }
        if (isContains(DELETE_PERMISSION_TYPES, url)) {
            type = 3;
        }
        if (allPermissionTypeCodes != null && !allPermissionTypeCodes.isEmpty()) {
            if (type != null && allPermissionTypeCodes.contains(type)) {
                return type;
            }
            return allPermissionTypeCodes.getFirst();
        }
        return null;
    }
    
    
    /**
     * 获取默认变量列表
     *
     * @return 默认变量列表
     */
    public static List<VariableInfo> defaultVariable() {
        return Arrays.asList(new VariableInfo("resourceAddress", "路由地址"), new VariableInfo("resourceName", "资源名称"),
                new VariableInfo("i18nResourceCode", "国际化标识"), new VariableInfo("resourceCode", "资源编码"),
                new VariableInfo("resourceType", "资源类型"), new VariableInfo("permissionType", "权限类型"), new VariableInfo("remark", "备注"),
                new VariableInfo("creatorId", "创建人ID"), new VariableInfo("updaterId", "更新人ID"));
    }
    
    /**
     * @param url
     * @param headers
     * @param testHttpBtn
     * @param mainPanel
     */
    public static void doTestHttpRequest(String url, Map<String, String> headers, JButton testHttpBtn, JComponent mainPanel) {
        if (url.isEmpty()) {
            Messages.showWarningDialog(mainPanel, "地址不能为空", "测试失败");
            return;
        }
        testHttpBtn.setEnabled(false);
        testHttpBtn.setText("请求中...");
        // 异步执行，避免阻塞 EDT
        new Thread(() -> {
            String result;
            boolean success;
            try {
                result = HttpCodeFetcher.fetchCode(url, headers.isEmpty() ? null : headers, true);
                success = true;
            } catch (Exception ex) {
                result = ex.getMessage();
                success = false;
            }
            final String finalResult = result;
            final boolean finalSuccess = success;
            SwingUtilities.invokeLater(() -> {
                testHttpBtn.setEnabled(true);
                testHttpBtn.setText("测试");
                CommUtil.showTestResultDialog(finalResult, finalSuccess, mainPanel);
            });
        }, "http-test-thread").start();
    }
    
    /**
     * 展示测试结果弹窗
     */
    public static void showTestResultDialog(String result, boolean success, JComponent jComponent) {
        String res = result != null ? result.trim() : null;
        if (!success || HttpCodeFetcher.ERROR.equals(result) || res == null || res.isEmpty()) {
            Messages.showErrorDialog(jComponent, res == null || res.isEmpty() ? "响应为空" : res, "失败");
        } else {
            Messages.showInfoMessage(jComponent, "测试结果：" + res, "成功");
        }
    }
    
    private static boolean isContains(List<String> list, String url) {
        for (final String s : list) {
            if (url.contains(s)) {
                return true;
            }
        }
        return false;
    }
}
