package com.lsy.idea.model;

/**
 * 字典项通用模型，对应 dict_resource_type / dict_permission_type 表
 */
public class DictItem {

    /** 主键 ID */
    private Long id;

    /** 类型编码（数字，唯一） */
    private int typeCode;

    /** 类型名称 */
    private String typeName;

    /** 排序值，越小越靠前 */
    private int sort;

    public DictItem() {}

    public DictItem(int typeCode, String typeName, int sort) {
        this.typeCode = typeCode;
        this.typeName = typeName;
        this.sort = sort;
    }

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getTypeCode() { return typeCode; }
    public void setTypeCode(int typeCode) { this.typeCode = typeCode; }

    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }

    public int getSort() { return sort; }
    public void setSort(int sort) { this.sort = sort; }

    @Override
    public String toString() { return typeName + " (" + typeCode + ")"; }
}
