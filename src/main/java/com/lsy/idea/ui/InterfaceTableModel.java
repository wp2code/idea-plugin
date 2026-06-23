package com.lsy.idea.ui;

import com.lsy.idea.model.DictItem;
import com.lsy.idea.model.InterfaceInfo;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.table.AbstractTableModel;

/**
 * 接口列表 AbstractTableModel 列：勾选 | 路由地址 | 资源名称 | 国际化标识 | 资源编码 | 资源类型 | 权限类型 | 备注
 */
public class InterfaceTableModel extends AbstractTableModel {
    
    public static final int COL_SELECTED = 0;
    
    public static final int COL_ROUTE = 1;
    
    public static final int COL_RESOURCE_NAME = 2;
    
    public static final int COL_I18N_CODE = 3;
    
    public static final int COL_RESOURCE_CODE = 4;
    
    public static final int COL_RESOURCE_TYPE = 5;
    
    public static final int COL_PERMISSION_TYPE = 6;
    
    public static final int COL_REMARK = 7;
    
    private static final String[] COLUMN_NAMES = {"选择", "路由地址", "资源名称", "国际化标识", "资源编码", "资源类型", "权限类型", "备注"};
    
    private final List<InterfaceInfo> interfaces;
    
    private List<DictItem> resourceTypes;
    
    private List<DictItem> permissionTypes;
    
    public InterfaceTableModel(List<InterfaceInfo> interfaces) {
        this.interfaces = interfaces;
    }
    
    public void setResourceTypes(List<DictItem> resourceTypes) {
        this.resourceTypes = resourceTypes;
    }
    
    public void setPermissionTypes(List<DictItem> permissionTypes) {
        this.permissionTypes = permissionTypes;
    }
    
    public List<InterfaceInfo> getInterfaces() {
        return interfaces;
    }
    
    public InterfaceInfo getInterface(int row) {
        return interfaces.get(row);
    }
    
    @Override
    public int getRowCount() {
        return interfaces.size();
    }
    
    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }
    
    @Override
    public String getColumnName(int col) {
        return COLUMN_NAMES[col];
    }
    
    @Override
    public Class<?> getColumnClass(int col) {
        return col == COL_SELECTED ? Boolean.class : String.class;
    }
    
    @Override
    public boolean isCellEditable(int row, int col) {
        // 路由列不可编辑
        //        return col != COL_ROUTE;
        return true;
    }
    
    @Override
    public Object getValueAt(int row, int col) {
        InterfaceInfo info = interfaces.get(row);
        return switch (col) {
            case COL_SELECTED -> info.isSelected();
            case COL_ROUTE -> "[" + info.getRequestMethod() + "] " + info.getRoutePath();
            case COL_RESOURCE_NAME -> info.getResourceName();
            case COL_I18N_CODE -> info.getI18nResourceCode();
            case COL_RESOURCE_CODE -> info.getResourceCode();
            case COL_RESOURCE_TYPE -> getDictName(resourceTypes, info.getResourceType());
            case COL_PERMISSION_TYPE -> getDictName(permissionTypes, info.getPermissionType());
            case COL_REMARK -> info.getRemark();
            default -> "";
        };
    }
    
    @Override
    public void setValueAt(Object value, int row, int col) {
        InterfaceInfo info = interfaces.get(row);
        switch (col) {
            case COL_SELECTED -> info.setSelected(Boolean.TRUE.equals(value));
            case COL_RESOURCE_NAME -> {
                info.setResourceName(value != null ? value.toString() : "");
            }
            case COL_I18N_CODE -> {
                // 国际化标识：不标记 userModified，独立配置
                info.setI18nResourceCode(value != null ? value.toString() : "");
            }
            case COL_RESOURCE_CODE -> {
                info.setResourceCode(value != null ? value.toString() : "");
                checkDuplicateCodes();
            }
            case COL_RESOURCE_TYPE -> {
                info.setResourceType(parseDictCode(resourceTypes, value));
            }
            case COL_PERMISSION_TYPE -> {
                info.setPermissionType(parseDictCode(permissionTypes, value));
            }
            case COL_REMARK -> info.setRemark(value != null ? value.toString() : "");
        }
        fireTableCellUpdated(row, col);
    }
    
    /**
     * 检查所有勾选接口的资源编码是否有重复，更新 duplicateCode 状态
     */
    public void checkDuplicateCodes() {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        
        for (InterfaceInfo info : interfaces) {
            if (!info.isSelected()) {
                continue;
            }
            String code = info.getResourceCode();
            if (code != null && !code.isBlank()) {
                if (!seen.add(code)) {
                    duplicates.add(code);
                }
            }
        }
        
        for (InterfaceInfo info : interfaces) {
            String code = info.getResourceCode();
            info.setDuplicateCode(code != null && !code.isBlank() && duplicates.contains(code));
        }
        fireTableDataChanged();
    }
    
    /**
     * 获取字典名称（用于显示）
     */
    private String getDictName(List<DictItem> items, Integer code) {
        if (items == null || code == null) {
            return "";
        }
        return items.stream().filter(d -> d.getTypeCode() == code).findFirst().map(DictItem::getTypeName).orElse(String.valueOf(code));
    }
    
    /**
     * 解析字典值（从下拉框选中值解析 typeCode）
     */
    private Integer parseDictCode(List<DictItem> items, Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        String str = value.toString();
        // 先尝试直接解析数字
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException ignored) {
        }
        // 再按名称匹配
        if (items != null) {
            return items.stream().filter(d -> d.getTypeName().equals(str)).findFirst().map(DictItem::getTypeCode).orElse(null);
        }
        return null;
    }
}
