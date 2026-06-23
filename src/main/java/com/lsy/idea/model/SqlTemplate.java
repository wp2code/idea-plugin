package com.lsy.idea.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * SQL 脚本模板模型，对应数据库 script_template 表
 */
public class SqlTemplate {

    /** 主键 ID（自增） */
    private Long id;

    /** 模板名称（唯一） */
    private String templateName;

    /** SQL 模板内容，支持变量占位符 */
    private String templateContent;

    /** 默认创建人 ID 值 */
    private String creatorIdValue = "";

    /** 默认更新人 ID 值 */
    private String updaterIdValue = "";

    /** 资源编码 HTTP 拉取接口地址 */
    private String httpCodeApi = "";

    /** 资源编码默认模式：0-手动录入，1-HTTP 获取 */
    private int defaultCodeMode = 0;

    /** HTTP 请求头配置（JSON 存储，读取时反序列化为 Map） */
    private Map<String, String> httpHeaders = new HashMap<>();

    /** 扩展字段配置（JSON 存储，读取时反序列化为 Map） */
    private Map<String, String> extConfig = new HashMap<>();

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    public SqlTemplate() {}

    public SqlTemplate(String templateName, String templateContent) {
        this.templateName = templateName;
        this.templateContent = templateContent;
    }

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public String getTemplateContent() { return templateContent; }
    public void setTemplateContent(String templateContent) { this.templateContent = templateContent; }

    public String getCreatorIdValue() { return creatorIdValue != null ? creatorIdValue : ""; }
    public void setCreatorIdValue(String creatorIdValue) { this.creatorIdValue = creatorIdValue; }

    public String getUpdaterIdValue() { return updaterIdValue != null ? updaterIdValue : ""; }
    public void setUpdaterIdValue(String updaterIdValue) { this.updaterIdValue = updaterIdValue; }

    public String getHttpCodeApi() { return httpCodeApi != null ? httpCodeApi : ""; }
    public void setHttpCodeApi(String httpCodeApi) { this.httpCodeApi = httpCodeApi; }

    public int getDefaultCodeMode() { return defaultCodeMode; }
    public void setDefaultCodeMode(int defaultCodeMode) { this.defaultCodeMode = defaultCodeMode; }

    public Map<String, String> getHttpHeaders() { return httpHeaders != null ? httpHeaders : new HashMap<>(); }
    public void setHttpHeaders(Map<String, String> httpHeaders) { this.httpHeaders = httpHeaders != null ? httpHeaders : new HashMap<>(); }

    public Map<String, String> getExtConfig() { return extConfig != null ? extConfig : new HashMap<>(); }
    public void setExtConfig(Map<String, String> extConfig) { this.extConfig = extConfig != null ? extConfig : new HashMap<>(); }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() { return templateName; }
}
