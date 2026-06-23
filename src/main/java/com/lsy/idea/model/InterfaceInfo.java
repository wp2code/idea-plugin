package com.lsy.idea.model;

/**
 * 接口信息模型
 * 包含从 PSI 解析出的字段（路由/接口名/国际化标识）以及用户在 UI 列表中填写的配置字段
 */
public class InterfaceInfo {

    // ===== PSI 解析字段（只读，来自代码解析）=====

    /** 请求方式：GET/POST/PUT/DELETE/PATCH/ALL */
    private String requestMethod;

    /** 完整路由地址（类级别 + 方法级别合并后） */
    private String routePath;

    /** 接口名称：优先 @ApiOperation.value，其次 Javadoc 第一行，否则为空 */
    private String interfaceName;

    /** 国际化标识：来自 @I18nCode 注解，无注解则为空字符串 */
    private String i18nResourceCode;

    // ===== UI 配置字段（用户可编辑）=====

    /** 是否勾选参与 SQL 生成，默认 true */
    private boolean selected = true;

    /** 资源名称，默认等于 interfaceName，支持手动编辑，允许为空 */
    private String resourceName;

    /** 资源编码：手动输入 或 HTTP 拉取 */
    private String resourceCode = "";

    /** 资源编码模式：0-手动录入，1-HTTP获取 */
    private int codeMode = 0;

    /** HTTP 拉取地址（来自模板 httpCodeApi，支持手动修改） */
    private String httpCodeApi = "";

    /** 资源类型编码（字典 dict_resource_type.type_code） */
    private Integer resourceType;

    /** 权限类型编码（字典 dict_permission_type.type_code） */
    private Integer permissionType;

    /** 备注，允许为空 */
    private String remark = "";

    /** 是否被用户手动修改过（影响模板切换联动） */
    private boolean userModified = false;

    /** 资源编码是否与其他接口重复（用于 UI 标红提示） */
    private boolean duplicateCode = false;

    // ===== 构造器 =====

    public InterfaceInfo(String requestMethod, String routePath, String interfaceName, String i18nResourceCode) {
        this.requestMethod = requestMethod != null ? requestMethod : "";
        this.routePath = routePath != null ? routePath : "";
        this.interfaceName = interfaceName != null ? interfaceName : "";
        this.i18nResourceCode = i18nResourceCode != null ? i18nResourceCode : "";
        this.resourceName = this.interfaceName;
    }

    // ===== 静态工厂：合并类级路径和方法级路径 =====

    /**
     * 合并类级路径和方法级路径为完整路径
     */
    public static String mergePath(String classLevelPath, String methodPath) {
        String base = classLevelPath != null ? classLevelPath : "";
        String sub = methodPath != null ? methodPath : "";
        if (base.isEmpty()) return sub;
        if (sub.isEmpty()) return base;
        if (base.endsWith("/") && sub.startsWith("/")) {
            return base + sub.substring(1);
        }
        if (!base.endsWith("/") && !sub.startsWith("/")) {
            return base + "/" + sub;
        }
        return base + sub;
    }

    // ===== Getters & Setters =====

    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }

    public String getRoutePath() { return routePath; }
    public void setRoutePath(String routePath) { this.routePath = routePath; }

    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }

    public String getI18nResourceCode() { return i18nResourceCode; }
    public void setI18nResourceCode(String i18nResourceCode) { this.i18nResourceCode = i18nResourceCode; }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
        this.userModified = true;
    }

    public String getResourceCode() { return resourceCode; }
    public void setResourceCode(String resourceCode) { this.resourceCode = resourceCode; }

    public int getCodeMode() { return codeMode; }
    public void setCodeMode(int codeMode) { this.codeMode = codeMode; }

    public String getHttpCodeApi() { return httpCodeApi; }
    public void setHttpCodeApi(String httpCodeApi) { this.httpCodeApi = httpCodeApi; }

    public Integer getResourceType() { return resourceType; }
    public void setResourceType(Integer resourceType) {
        this.resourceType = resourceType;
        this.userModified = true;
    }
    /** 设置资源类型默认値，不标记 userModified（仅当当前値为 null 时生效） */
    public void initDefaultResourceType(Integer resourceType) {
        if (this.resourceType == null) this.resourceType = resourceType;
    }

    public Integer getPermissionType() { return permissionType; }
    public void setPermissionType(Integer permissionType) {
        this.permissionType = permissionType;
        this.userModified = true;
    }
    /** 设置权限类型默认値，不标记 userModified（仅当当前値为 null 时生效） */
    public void initDefaultPermissionType(Integer permissionType) {
        if (this.permissionType == null) this.permissionType = permissionType;
    }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public boolean isUserModified() { return userModified; }
    public void setUserModified(boolean userModified) { this.userModified = userModified; }

    public boolean isDuplicateCode() { return duplicateCode; }
    public void setDuplicateCode(boolean duplicateCode) { this.duplicateCode = duplicateCode; }

    @Override
    public String toString() {
        return requestMethod + " " + routePath + " (" + interfaceName + ")";
    }
}
