package com.lsy.idea.ui;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.lsy.idea.model.SqlTemplate;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * 可折叠的扩展字段配置面板
 * 支持动态添加/删除键值对行，Key 实时正则校验（仅英文/数字/下划线）
 */
public class ExtConfigPanel extends JPanel {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private JToggleButton toggleBtn;
    private JPanel contentPanel;
    private JTable extTable;
    private ExtTableModel extTableModel;
    
    public ExtConfigPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("扩展字段配置（可选）"));
        buildUI();
    }

    private void buildUI() {
        // ---- 折叠切换按钮 ----
        toggleBtn = new JToggleButton("▶ 展开扩展字段");
        toggleBtn.setHorizontalAlignment(SwingConstants.LEFT);
        toggleBtn.addActionListener(e -> toggleContent());
        add(toggleBtn, BorderLayout.NORTH);

        // ---- 内容面板（默认折叠） ----
        contentPanel = new JPanel(new BorderLayout(4, 4));

        // 键值对表格
        extTableModel = new ExtTableModel();
        extTable = new JTable(extTableModel);
        extTable.setRowHeight(24);
        extTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        extTable.getColumnModel().getColumn(1).setPreferredWidth(200);

        // Key 列自定义渲染器（校验不通过时标红）
        extTable.getColumnModel().getColumn(0).setCellRenderer(new KeyCellRenderer());

        JBScrollPane tableScroll = new JBScrollPane(extTable);
        tableScroll.setPreferredSize(new Dimension(400, 120));
        contentPanel.add(tableScroll, BorderLayout.CENTER);

        // 按钮行
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        final JButton addRowBtn = new JButton("+ 添加字段");
        final JButton removeRowBtn = new JButton("- 删除选中");
        addRowBtn.addActionListener(e -> extTableModel.addRow("", ""));
        removeRowBtn.addActionListener(e -> {
            int row = extTable.getSelectedRow();
            if (row >= 0) extTableModel.removeRow(row);
        });
        btnRow.add(addRowBtn);
        btnRow.add(removeRowBtn);
        contentPanel.add(btnRow, BorderLayout.SOUTH);

        contentPanel.setVisible(false);
        add(contentPanel, BorderLayout.CENTER);
    }

    private void toggleContent() {
        boolean visible = !contentPanel.isVisible();
        contentPanel.setVisible(visible);
        toggleBtn.setText(visible ? "▼ 收起扩展字段" : "▶ 展开扩展字段");

        // 通知父容器重新布局
        Container parent = getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    /**
     * 从模板加载 extConfig 数据到表格
     */
    public void populateFromTemplate(SqlTemplate template) {
        extTableModel.clear();
        if (template != null && template.getExtConfig() != null) {
            for (Map.Entry<String, String> entry : template.getExtConfig().entrySet()) {
                extTableModel.addRow(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 将当前表格数据写回模板的 extConfig
     */
    public void applyToTemplate(SqlTemplate template) {
        if (template == null) return;
        // 提交任何正在编辑的单元格
        if (extTable.isEditing()) {
            extTable.getCellEditor().stopCellEditing();
        }
        Map<String, String> ext = new LinkedHashMap<>();
        for (int i = 0; i < extTableModel.getRowCount(); i++) {
            String key = extTableModel.getKey(i);
            String val = extTableModel.getValue(i);
            if (key != null && !key.isBlank()) {
                ext.put(key.trim(), val != null ? val : "");
            }
        }
        template.setExtConfig(ext);
    }

    /**
     * 获取当前扩展字段 Map（用于实时读取）
     */
    public Map<String, String> getExtConfig() {
        if (extTable.isEditing()) {
            extTable.getCellEditor().stopCellEditing();
        }
        Map<String, String> ext = new LinkedHashMap<>();
        for (int i = 0; i < extTableModel.getRowCount(); i++) {
            String key = extTableModel.getKey(i);
            String val = extTableModel.getValue(i);
            if (key != null && !key.isBlank()) {
                ext.put(key.trim(), val != null ? val : "");
            }
        }
        return ext;
    }

    /**
     * 校验扩展字段 Key 合法性，返回校验错误信息列表（空表示通过）
     */
    public List<String> validateKeys() {
        List<String> errors = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < extTableModel.getRowCount(); i++) {
            String key = extTableModel.getKey(i);
            if (key == null || key.isBlank()) {
                errors.add("第 " + (i + 1) + " 行字段Key不能为空");
                continue;
            }
            if (!KEY_PATTERN.matcher(key.trim()).matches()) {
                errors.add("第 " + (i + 1) + " 行字段Key [" + key + "] 格式不合法（仅支持英文/数字/下划线）");
            }
            if (!keys.add(key.trim())) {
                errors.add("字段Key重复：" + key);
            }
        }
        return errors;
    }

    /** 清空所有行 */
    public void clear() {
        extTableModel.clear();
    }

    // ===================== 内部类 =====================

    /** 扩展字段表格模型 */
    private static class ExtTableModel extends AbstractTableModel {
        private final List<String[]> rows = new ArrayList<>();

        public void addRow(String key, String value) {
            rows.add(new String[]{key, value});
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

        public String getKey(int row) { return rows.get(row)[0]; }
        public String getValue(int row) { return rows.get(row)[1]; }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 2; }
        @Override public String getColumnName(int col) { return col == 0 ? "字段Key" : "字段Value"; }
        @Override public boolean isCellEditable(int row, int col) { return true; }

        @Override
        public Object getValueAt(int row, int col) { return rows.get(row)[col]; }

        @Override
        public void setValueAt(Object val, int row, int col) {
            rows.get(row)[col] = val != null ? val.toString() : "";
            fireTableCellUpdated(row, col);
        }
    }

    /** Key 列渲染器：非法 Key 标红背景 */
    private static class KeyCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String key = value != null ? value.toString() : "";
            if (!isSelected && !key.isBlank() && !KEY_PATTERN.matcher(key).matches()) {
                comp.setBackground(new JBColor(new Color(255, 200, 200), new Color(255, 200, 200)));
            } else if (!isSelected) {
                comp.setBackground(table.getBackground());
            }
            return comp;
        }
    }
}
