package com.lsy.idea.generator;

import com.lsy.idea.model.InterfaceInfo;
import com.lsy.idea.model.SqlTemplate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * SQL 生成器（新变量名体系版本）
 * <p>
 * 支持的模板变量： 接口动态变量：${resourceAddress} ${resourceName} ${i18nResourceCode} ${resourceCode} ${resourceType} ${permissionType} ${remark}
 * 全局模板变量：${creatorId} ${updaterId} 扩展字段变量：${ext.xxx}
 * <p>
 * 空值处理：所有空字段替换为空字符串，不崩溃 未知扩展字段：替换为空字符串，收集警告
 */
public class SqlGenerator {
    
    /**
     * 匹配 ${xxx} 或 ${ext.xxx}
     */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([\\w.]+)\\}");
    
    /**
     * 批量生成 SQL（仅处理 selected==true 的接口）
     *
     * @param interfaces 接口列表
     * @param template   模板对象（含 extConfig）
     * @return 生成结果，包含 SQL 文本和警告列表
     */
    @NotNull
    public static GenerateResult generate(@NotNull List<InterfaceInfo> interfaces, @NotNull SqlTemplate template) {
        
        List<String> allWarnings = new ArrayList<>();
        String sql = interfaces.stream().filter(InterfaceInfo::isSelected).map(info -> {
            List<String> warnings = new ArrayList<>();
            String singleSql = generateSingle(info, template, warnings);
            allWarnings.addAll(warnings);
            return singleSql;
        }).filter(s -> !s.isBlank()).collect(Collectors.joining("\n"));
        
        return new GenerateResult(sql, allWarnings);
    }
    
    /**
     * 单条接口生成 SQL
     *
     * @param info     接口信息
     * @param template 模板
     * @param warnings 警告收集列表（可为 null）
     * @return 生成的单条 SQL
     */
    @NotNull
    public static String generateSingle(@NotNull InterfaceInfo info, @NotNull SqlTemplate template, @NotNull List<String> warnings) {
        
        // 构建标准变量映射
        Map<String, String> variables = buildVariables(info, template);
        Map<String, String> extConfig = template.getExtConfig();
        
        String templateContent = template.getTemplateContent();
        if (templateContent == null || templateContent.isBlank()) {
            return "";
        }
        
        // 压缩模板内容：去除换行和多余空白，使SQL尽量不换行
        templateContent = compressSql(templateContent);
        
        Matcher matcher = VAR_PATTERN.matcher(templateContent);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement;
            
            if (varName.startsWith("ext.")) {
                // 扩展字段变量 ${ext.xxx}
                String extKey = varName.substring(4);
                if (extConfig != null && extConfig.containsKey(extKey)) {
                    replacement = nullToEmpty(extConfig.get(extKey));
                } else {
                    replacement = "";
                    String warn = "存在未定义的扩展字段变量：${" + varName + "}";
                    if (!warnings.contains(warn)) {
                        warnings.add(warn);
                    }
                }
            } else {
                // 标准变量
                replacement = variables.getOrDefault(varName, "");
            }
            
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        if (sb.lastIndexOf(";") <= 0) {
            sb.append(";");
        }
        return sb.toString();
    }
    
    /**
     * 构建接口变量映射表
     */
    @NotNull
    private static Map<String, String> buildVariables(@NotNull InterfaceInfo info, @NotNull SqlTemplate template) {
        
        Map<String, String> vars = new HashMap<>();
        // 接口动态变量
        vars.put("resourceAddress", nullToEmpty(info.getRoutePath()));
        vars.put("resourceName", nullToEmpty(info.getResourceName()));
        vars.put("i18nResourceCode", nullToEmpty(info.getI18nResourceCode()));
        vars.put("resourceCode", nullToEmpty(info.getResourceCode()));
        vars.put("resourceType", info.getResourceType() != null ? String.valueOf(info.getResourceType()) : "");
        vars.put("permissionType", info.getPermissionType() != null ? String.valueOf(info.getPermissionType()) : "");
        vars.put("remark", nullToEmpty(info.getRemark()));
        // 全局模板变量
        vars.put("creatorId", nullToEmpty(template.getCreatorIdValue()));
        vars.put("updaterId", nullToEmpty(template.getUpdaterIdValue()));
        return vars;
    }
    
    @NotNull
    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
    
    
    /**
     * 压缩SQL文本：去除换行、多余空白，使SQL尽量单行展示 规则： 1. 将所有换行（含 \r\n、\r、\n）替换为单个空格 2. 将连续多个空白字符（空格、制表符等）压缩为单个空格 3. 去除行首尾空白
     */
    @NotNull
    public static String compressSql(@NotNull String sql) {
        if (sql == null || sql.isBlank()) {
            return "";
        }
        // 1. 将换行符替换为空格
        String result = sql.replaceAll("\\r\\n|\\r|\\n", " ");
        // 2. 将连续空白字符压缩为单个空格
        result = result.replaceAll("[ \\t]+", " ");
        // 3. 去除首尾空白
        return result.trim();
    }
    
    /**
     * 生成结果封装（SQL + 警告列表）
     */
    public static class GenerateResult {
        
        private final String sql;
        
        private final List<String> warnings;
        
        public GenerateResult(String sql, List<String> warnings) {
            this.sql = sql != null ? sql : "";
            this.warnings = warnings != null ? warnings : List.of();
        }
        
        public String getSql() {
            return sql;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }
}
