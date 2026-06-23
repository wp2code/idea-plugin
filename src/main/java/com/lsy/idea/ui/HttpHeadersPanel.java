package com.lsy.idea.ui;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.lsy.idea.model.SqlTemplate;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;

/**
 * 支持动态添加/删除 Header Key-Value 行 用于模板配置中自定义 HTTP 请求头（如 Authorization、Token 等）
 */
public class HttpHeadersPanel extends JPanel {
    
    private JBTable headersTable;
    
    private HeadersTableModel headersTableModel;
    
    
    public HttpHeadersPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), "请求头配置（可选）"));
        buildUI();
    }
    
    private void buildUI() {
        // ---- 内容面板（默认折叠） ----
        final JPanel contentPanel = new JPanel(new BorderLayout(4, 4));
        
        // 键值对表格
        headersTableModel = new HeadersTableModel();
        headersTable = new JBTable(headersTableModel);
        headersTable.setRowHeight(20);
        headersTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        headersTable.getColumnModel().getColumn(1).setPreferredWidth(260);
        
        JBScrollPane tableScroll = new JBScrollPane(headersTable);
        tableScroll.setPreferredSize(new Dimension(440, 120));
        contentPanel.add(tableScroll, BorderLayout.CENTER);
        
        // 说明标签
        JLabel hint = new JLabel("<html><small>常用请求头：Authorization、Content-Type、Token 等</small></html>");
        hint.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        
        // 按钮行
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        final JButton addRowBtn = new JButton("+ 添加请求头");
        final JButton removeRowBtn = new JButton("- 删除选中");
        addRowBtn.addActionListener(e -> headersTableModel.addRow("", ""));
        removeRowBtn.addActionListener(e -> {
            int row = headersTable.getSelectedRow();
            if (row >= 0) {
                headersTableModel.removeRow(row);
            }
        });
        btnRow.add(addRowBtn);
        btnRow.add(removeRowBtn);
        btnRow.add(hint);
        contentPanel.add(btnRow, BorderLayout.SOUTH);
        
        contentPanel.setVisible(true);
        add(contentPanel, BorderLayout.CENTER);
    }
    
    /**
     * 从模板加载 httpHeaders 数据到表格
     */
    public void populateFromTemplate(SqlTemplate template) {
        headersTableModel.clear();
        if (template != null && template.getHttpHeaders() != null) {
            for (Map.Entry<String, String> entry : template.getHttpHeaders().entrySet()) {
                headersTableModel.addRow(entry.getKey(), entry.getValue());
            }
        }
    }
    
    /**
     * 将当前表格数据写回模板的 httpHeaders
     */
    public void applyToTemplate(SqlTemplate template) {
        if (template == null) {
            return;
        }
        // 提交任何正在编辑的单元格
        if (headersTable.isEditing()) {
            headersTable.getCellEditor().stopCellEditing();
        }
        template.setHttpHeaders(getHeaders());
    }
    
    /**
     * 获取当前请求头 Map（用于实时读取）
     */
    public Map<String, String> getHeaders() {
        if (headersTable.isEditing()) {
            headersTable.getCellEditor().stopCellEditing();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 0; i < headersTableModel.getRowCount(); i++) {
            String key = headersTableModel.getKey(i);
            String val = headersTableModel.getValue(i);
            if (key != null && !key.isBlank()) {
                headers.put(key.trim(), val != null ? val : "");
            }
        }
        return headers;
    }
    
    /**
     * 清空所有行
     */
    public void clear() {
        headersTableModel.clear();
    }
    
    // ===================== 内部类 =====================
    
    /**
     * 请求头表格模型
     */
    private static class HeadersTableModel extends AbstractTableModel {
        
        private final List<String[]> rows = new ArrayList<>();
        
        public void addRow(String key, String value) {
            rows.add(new String[] {key, value});
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }
        
        public void removeRow(int row) {
            rows.remove(row);
            fireTableRowsDeleted(row, row);
        }
        
        public void clear() {
            rows.clear();
            fireTableDataChanged();
        }
        
        public String getKey(int row) {
            return rows.get(row)[0];
        }
        
        public String getValue(int row) {
            return rows.get(row)[1];
        }
        
        @Override
        public int getRowCount() {
            return rows.size();
        }
        
        @Override
        public int getColumnCount() {
            return 2;
        }
        
        @Override
        public String getColumnName(int col) {
            return col == 0 ? "请求头名称（Key）" : "请求头值（Value）";
        }
        
        @Override
        public boolean isCellEditable(int row, int col) {
            return true;
        }
        
        @Override
        public Object getValueAt(int row, int col) {
            return rows.get(row)[col];
        }
        
        @Override
        public void setValueAt(Object val, int row, int col) {
            rows.get(row)[col] = val != null ? val.toString() : "";
            fireTableCellUpdated(row, col);
        }
    }
}
