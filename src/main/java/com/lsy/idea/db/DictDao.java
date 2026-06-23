package com.lsy.idea.db;

import com.intellij.openapi.diagnostic.Logger;
import com.lsy.idea.model.DictItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * 字典表 CRUD 操作（资源类型 + 权限类型）
 */
public class DictDao {
    
    private static final Logger LOG = Logger.getInstance(DictDao.class);
    
    private final Connection connection;
    
    public DictDao(@NotNull Connection connection) {
        this.connection = connection;
    }
    
    // ===================== 资源类型 dict_resource_type =====================
    
    /**
     * 查询全部资源类型，按 sort 升序
     */
    public List<DictItem> getResourceTypes() {
        return queryDictItems("SELECT id,type_code,type_name,sort FROM dict_resource_type ORDER BY sort ASC");
    }
    
    /**
     * 新增资源类型
     */
    public void addResourceType(@NotNull DictItem item) {
        executeDictInsert("INSERT INTO dict_resource_type(type_code,type_name,sort) VALUES (?,?,?)", item);
    }
    
    /**
     * 更新资源类型
     */
    public void updateResourceType(@NotNull DictItem item) {
        executeDictUpdate("UPDATE dict_resource_type SET type_code=?,type_name=?,sort=? WHERE id=?", item);
    }
    
    /**
     * 删除资源类型（按 id）
     */
    public void deleteResourceType(long id) {
        executeDelete("DELETE FROM dict_resource_type WHERE id=?", id);
    }
    
    /**
     * 资源类型编码是否已存在（排除自身）
     */
    public boolean resourceTypeCodeExists(int typeCode, Long excludeId) {
        return dictCodeExists("SELECT COUNT(*) FROM dict_resource_type WHERE type_code=? AND id<>?", typeCode, excludeId);
    }
    
    /**
     * 根据ID查询资源类型
     */
    public DictItem getResourceType(long id) {
        return queryDictItemById("SELECT id,type_code,type_name,sort FROM dict_resource_type WHERE id=?", id);
    }
    
    // ===================== 权限类型 dict_permission_type =====================
    
    /**
     * 根据ID查询资源类型
     *
     * @param id 权限类型ID
     * @return 权限类型
     */
    public DictItem getPermissionType(long id) {
        return queryDictItemById("SELECT id,type_code,type_name,sort FROM dict_permission_type WHERE id=?", id);
    }
    
    /**
     * 查询全部权限类型，按 sort 升序
     */
    public List<DictItem> getPermissionTypes() {
        return queryDictItems("SELECT id,type_code,type_name,sort FROM dict_permission_type ORDER BY sort ASC");
    }
    
    /**
     * 新增权限类型
     */
    public void addPermissionType(@NotNull DictItem item) {
        executeDictInsert("INSERT INTO dict_permission_type(type_code,type_name,sort) VALUES (?,?,?)", item);
    }
    
    /**
     * 更新权限类型
     */
    public void updatePermissionType(@NotNull DictItem item) {
        executeDictUpdate("UPDATE dict_permission_type SET type_code=?,type_name=?,sort=? WHERE id=?", item);
    }
    
    /**
     * 删除权限类型（按 id）
     */
    public void deletePermissionType(long id) {
        executeDelete("DELETE FROM dict_permission_type WHERE id=?", id);
    }
    
    /**
     * 权限类型编码是否已存在（排除自身）
     */
    public boolean permissionTypeCodeExists(int typeCode, Long excludeId) {
        return dictCodeExists("SELECT COUNT(*) FROM dict_permission_type WHERE type_code=? AND id<>?", typeCode, excludeId);
    }
    
    // ===================== 内部工具方法 =====================
    
    private List<DictItem> queryDictItems(String sql) {
        List<DictItem> list = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql);
                ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                DictItem item = new DictItem();
                item.setId(rs.getLong("id"));
                item.setTypeCode(rs.getInt("type_code"));
                item.setTypeName(rs.getString("type_name"));
                item.setSort(rs.getInt("sort"));
                list.add(item);
            }
        } catch (SQLException e) {
            LOG.error("Failed to query dict items: " + e.getMessage(), e);
        }
        return list;
    }
    
    private DictItem queryDictItemById(String sql, long id) {
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    DictItem item = new DictItem();
                    item.setId(rs.getLong("id"));
                    item.setTypeCode(rs.getInt("type_code"));
                    item.setTypeName(rs.getString("type_name"));
                    item.setSort(rs.getInt("sort"));
                    return item;
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to query dict item by id: " + e.getMessage(), e);
        }
        return null;
    }
    
    private void executeDictInsert(String sql, DictItem item) {
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, item.getTypeCode());
            pstmt.setString(2, item.getTypeName());
            pstmt.setInt(3, item.getSort());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to insert dict item: " + e.getMessage(), e);
            throw new RuntimeException("新增字典项失败：" + e.getMessage(), e);
        }
    }
    
    private void executeDictUpdate(String sql, DictItem item) {
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, item.getTypeCode());
            pstmt.setString(2, item.getTypeName());
            pstmt.setInt(3, item.getSort());
            pstmt.setLong(4, item.getId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to update dict item: " + e.getMessage(), e);
            throw new RuntimeException("更新字典项失败：" + e.getMessage(), e);
        }
    }
    
    private void executeDelete(String sql, long id) {
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to delete dict item: " + e.getMessage(), e);
            throw new RuntimeException("删除字典项失败：" + e.getMessage(), e);
        }
    }
    
    private boolean dictCodeExists(String sql, int typeCode, Long excludeId) {
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, typeCode);
            pstmt.setLong(2, excludeId != null ? excludeId : -1L);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            LOG.error("Failed to check dict code: " + e.getMessage(), e);
            return false;
        }
    }
}
