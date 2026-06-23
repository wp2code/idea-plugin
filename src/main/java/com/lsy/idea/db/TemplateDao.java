package com.lsy.idea.db;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.lsy.idea.model.SqlTemplate;
import com.lsy.idea.util.JsonUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 模板表 CRUD 操作，支持 JSON 格式导入导出
 */
public class TemplateDao {
    
    private static final Logger LOG = Logger.getInstance(TemplateDao.class);
    
    private final Connection connection;
    
    public TemplateDao(@NotNull Connection connection) {
        this.connection = connection;
    }
    
    /**
     * 查询全部模板
     */
    public List<SqlTemplate> getAllTemplates() {
        List<SqlTemplate> list = new ArrayList<>();
        String sql = "SELECT id,template_name,template_content,creator_id_value,updater_id_value,"
                + "http_code_api,default_code_mode,ext_config,http_headers FROM script_template ORDER BY id ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql);
                ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOG.error("Failed to query templates: " + e.getMessage(), e);
        }
        return list;
    }
    
    /**
     * 按名称查询模板
     */
    @Nullable
    public SqlTemplate getByName(String name) {
        String sql = "SELECT id,template_name,template_content,creator_id_value,updater_id_value,"
                + "http_code_api,default_code_mode,ext_config,http_headers FROM script_template WHERE template_name=?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to query template by name: " + e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * 按 id 查询模板
     */
    @Nullable
    public SqlTemplate getById(long id) {
        String sql = "SELECT id,template_name,template_content,creator_id_value,updater_id_value,"
                + "http_code_api,default_code_mode,ext_config,http_headers FROM script_template WHERE id=?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to query template by id: " + e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * 新增模板
     */
    public void saveTemplate(@NotNull SqlTemplate template) {
        String sql = "INSERT INTO script_template(template_name,template_content,creator_id_value,"
                + "updater_id_value,http_code_api,default_code_mode,ext_config,http_headers) VALUES (?,?,?,?,?,?,?,?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            setTemplateParams(pstmt, template);
            pstmt.executeUpdate();
            // 回填生成的 id
            try (ResultSet rs = connection.createStatement().executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    template.setId(rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to save template: " + e.getMessage(), e);
            throw new RuntimeException("保存模板失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 更新模板
     */
    public void updateTemplate(@NotNull SqlTemplate template) {
        String sql = "UPDATE script_template SET template_name=?,template_content=?,creator_id_value=?,"
                + "updater_id_value=?,http_code_api=?,default_code_mode=?,ext_config=?,http_headers=? WHERE id=?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            setTemplateParams(pstmt, template);
            pstmt.setLong(9, template.getId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to update template: " + e.getMessage(), e);
            throw new RuntimeException("更新模板失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 删除模板
     */
    public void deleteTemplate(long id) {
        try (PreparedStatement pstmt = connection.prepareStatement("DELETE FROM script_template WHERE id=?")) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to delete template: " + e.getMessage(), e);
            throw new RuntimeException("删除模板失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 模板名称是否已存在（排除自身）
     */
    public boolean nameExists(String name, Long excludeId) {
        String sql = "SELECT COUNT(*) FROM script_template WHERE template_name=? AND id<>?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setLong(2, excludeId != null ? excludeId : -1L);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            LOG.error("Failed to check template name: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 将指定模板导出为 JSON 字符串（仅含业务字段，不含 id/时间）
     */
    public String exportTemplateAsJson(@NotNull SqlTemplate template) {
        JsonObject obj = new JsonObject();
        obj.addProperty("templateName", template.getTemplateName());
        obj.addProperty("templateContent", template.getTemplateContent());
        obj.addProperty("creatorIdValue", template.getCreatorIdValue());
        obj.addProperty("updaterIdValue", template.getUpdaterIdValue());
        obj.addProperty("httpCodeApi", template.getHttpCodeApi());
        obj.addProperty("defaultCodeMode", template.getDefaultCodeMode());
        
        // extConfig 作为嵌套 JSON 对象
        JsonObject extObj = new JsonObject();
        Map<String, String> ext = template.getExtConfig();
        if (ext != null) {
            ext.forEach(extObj::addProperty);
        }
        obj.add("extConfig", extObj);
        
        // httpHeaders 作为嵌套 JSON 对象
        JsonObject headersObj = new JsonObject();
        Map<String, String> headers = template.getHttpHeaders();
        if (headers != null) {
            headers.forEach(headersObj::addProperty);
        }
        obj.add("httpHeaders", headersObj);
        
        return JsonUtils.getGson().toJson(obj);
    }
    
    /**
     * 从 JSON 字符串导入单个模板
     *
     * @return 导入成功的模板名称
     * @throws RuntimeException 校验失败时抛出
     */
    public String importTemplateFromJson(@NotNull String json) {
        if (!JsonUtils.isValidJson(json)) {
            throw new RuntimeException("模板文件格式错误，请选择合法的JSON文件");
        }
        
        JsonObject obj;
        try {
            obj = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new RuntimeException("模板文件格式错误，请选择合法的JSON文件");
        }
        
        // 必填字段校验
        String[] required = {"templateName", "templateContent", "defaultCodeMode"};
        for (String field : required) {
            if (!obj.has(field) || obj.get(field).isJsonNull() || obj.get(field).getAsString().isBlank()) {
                throw new RuntimeException("模板文件缺少必填字段：" + field);
            }
        }
        
        String templateName = obj.get("templateName").getAsString().trim();
        // 重名校验
        if (nameExists(templateName, null)) {
            templateName = templateName + "_copy";
            if (nameExists(templateName, null)) {
                templateName = templateName + "_copy_" + System.currentTimeMillis();
            }
            //            throw new RuntimeException("模板名称已存在，请修改后重新导入：" + templateName);
        }
        
        // 构建模板对象
        SqlTemplate template = new SqlTemplate();
        template.setTemplateName(templateName);
        template.setTemplateContent(obj.get("templateContent").getAsString());
        template.setCreatorIdValue(getStringField(obj, "creatorIdValue", ""));
        template.setUpdaterIdValue(getStringField(obj, "updaterIdValue", ""));
        template.setHttpCodeApi(getStringField(obj, "httpCodeApi", ""));
        template.setDefaultCodeMode(obj.has("defaultCodeMode") ? obj.get("defaultCodeMode").getAsInt() : 0);
        
        // extConfig
        if (obj.has("extConfig") && obj.get("extConfig").isJsonObject()) {
            JsonObject extObj = obj.getAsJsonObject("extConfig");
            Map<String, String> extMap = new java.util.HashMap<>();
            for (Map.Entry<String, JsonElement> entry : extObj.entrySet()) {
                extMap.put(entry.getKey(), entry.getValue().isJsonNull() ? "" : entry.getValue().getAsString());
            }
            template.setExtConfig(extMap);
        }
        
        // httpHeaders
        if (obj.has("httpHeaders") && obj.get("httpHeaders").isJsonObject()) {
            JsonObject headersObj = obj.getAsJsonObject("httpHeaders");
            Map<String, String> headersMap = new java.util.HashMap<>();
            for (Map.Entry<String, JsonElement> entry : headersObj.entrySet()) {
                headersMap.put(entry.getKey(), entry.getValue().isJsonNull() ? "" : entry.getValue().getAsString());
            }
            template.setHttpHeaders(headersMap);
        }
        
        saveTemplate(template);
        return templateName;
    }
    
    // ===================== 内部工具方法 =====================
    
    private SqlTemplate mapRow(ResultSet rs) throws SQLException {
        SqlTemplate t = new SqlTemplate();
        t.setId(rs.getLong("id"));
        t.setTemplateName(rs.getString("template_name"));
        t.setTemplateContent(rs.getString("template_content"));
        t.setCreatorIdValue(rs.getString("creator_id_value"));
        t.setUpdaterIdValue(rs.getString("updater_id_value"));
        t.setHttpCodeApi(rs.getString("http_code_api"));
        t.setDefaultCodeMode(rs.getInt("default_code_mode"));
        t.setExtConfig(JsonUtils.parseExtConfig(rs.getString("ext_config")));
        t.setHttpHeaders(JsonUtils.parseExtConfig(rs.getString("http_headers")));
        return t;
    }
    
    private void setTemplateParams(PreparedStatement pstmt, SqlTemplate template) throws SQLException {
        pstmt.setString(1, template.getTemplateName());
        pstmt.setString(2, template.getTemplateContent());
        pstmt.setString(3, template.getCreatorIdValue());
        pstmt.setString(4, template.getUpdaterIdValue());
        pstmt.setString(5, template.getHttpCodeApi());
        pstmt.setInt(6, template.getDefaultCodeMode());
        pstmt.setString(7, JsonUtils.serializeExtConfig(template.getExtConfig()));
        pstmt.setString(8, JsonUtils.serializeExtConfig(template.getHttpHeaders()));
    }
    
    private String getStringField(JsonObject obj, String field, String defaultValue) {
        if (obj.has(field) && !obj.get(field).isJsonNull()) {
            return obj.get(field).getAsString();
        }
        return defaultValue;
    }
}
