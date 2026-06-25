package com.lsy.idea.model;

/**
 * @author liuweiping
 * @date 2026-06-23
 **/
public class VariableInfo {
    
    private String name;
    
    private String description;
    
    public VariableInfo(String name, String description) {
        this.name = name;
        this.description = description;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
}
