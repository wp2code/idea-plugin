package com.lsy.idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.lsy.idea.db.DictDao;
import com.lsy.idea.model.DictItem;
import com.lsy.idea.util.CommUtil;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.Nullable;

/**
 * 字典管理弹窗（资源类型 / 权限类型） 布局：左侧列表 + 右侧内嵌新增/编辑表单，支持直接维护字典项
 */
public class DictManageDialog extends DialogWrapper {
    
    private final DictDao dictDao;
    
    private final boolean isResourceType;
    
    private final String typeName;
    
    // ---- 列表区 ----
    private JBTable table;
    
    private DictTableModel tableModel;
    
    // ---- 右侧表单区 ----
    private JTextField codeField;
    
    private JTextField nameField;
    
    private JTextField sortField;
    
    private JButton saveBtn;       // 新增 / 保存
    
    private JButton deleteBtn;     // 删除选中行
    
    private JLabel formTitleLabel; // 动态标题："新增" / "编辑"
    
    /**
     * 当前正在编辑的记录，null 表示新增模式
     */
    private DictItem editingItem = null;
    
    public DictManageDialog(@Nullable Project project, DictDao dictDao, boolean isResourceType) {
        super(project);
        this.dictDao = dictDao;
        this.isResourceType = isResourceType;
        this.typeName = isResourceType ? "资源类型" : "权限类型";
        setTitle(typeName + "字典管理");
        setOKButtonText("关闭");
        setCancelButtonText("取消");
        init();
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setPreferredSize(new Dimension(680, 400));
        root.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        
        root.add(buildTablePanel(), BorderLayout.CENTER);
        root.add(buildFormPanel(), BorderLayout.EAST);
        
        return root;
    }
    
    // ===================== 左侧：列表面板 =====================
    
    private JPanel buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(typeName + "列表"));
        
        tableModel = new DictTableModel(loadItems());
        table = new JBTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(50);
        
        // 选中行 → 填入右侧表单（编辑模式）
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int row = table.getSelectedRow();
            if (row >= 0) {
                loadItemToForm(tableModel.getItem(row));
            }
        });
        
        JBScrollPane scrollPane = new JBScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // 底部工具栏
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton newBtn = new JButton("+ 新增");
        newBtn.addActionListener(e -> switchToAddMode());
        deleteBtn = new JButton("删除");
        deleteBtn.setEnabled(false);
        deleteBtn.addActionListener(e -> deleteSelectedItem());
        toolbar.add(newBtn);
        toolbar.add(deleteBtn);
        panel.add(toolbar, BorderLayout.SOUTH);
        
        return panel;
    }
    
    // ===================== 右侧：表单面板 =====================
    
    private JPanel buildFormPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setPreferredSize(new Dimension(230, 0));
        
        // 标题
        formTitleLabel = new JLabel("新增" + typeName);
        formTitleLabel.setFont(formTitleLabel.getFont().deriveFont(Font.BOLD, 13f));
        formTitleLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 0));
        
        // 表单字段
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = JBUI.insets(5, 0, 5, 6);
        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets = JBUI.insets(5, 0);
        
        codeField = new JTextField(10);
        nameField = new JTextField(10);
        sortField = new JTextField(10);
        
        // 行0：类型编码
        lc.gridx = 0;
        lc.gridy = 0;
        fields.add(new JLabel("类型编码："), lc);
        fc.gridx = 1;
        fc.gridy = 0;
        fields.add(codeField, fc);
        // 行1：类型名称
        lc.gridy = 1;
        fields.add(new JLabel("类型名称："), lc);
        fc.gridy = 1;
        fields.add(nameField, fc);
        // 行2：排序
        lc.gridy = 2;
        fields.add(new JLabel("排序："), lc);
        fc.gridy = 2;
        fields.add(sortField, fc);
        
        // 提示
        JLabel hint = new JLabel("编码和排序填整数");
        hint.setForeground(JBColor.YELLOW);
        hint.setFont(hint.getFont().deriveFont(11f));
        lc.gridx = 0;
        lc.gridy = 3;
        lc.gridwidth = 2;
        fields.add(hint, lc);
        
        // 按钮
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        saveBtn = new JButton("保存");
        // 清空（切换为新增状态）
        final JButton clearBtn = new JButton("清空");
        saveBtn.addActionListener(e -> saveForm());
        clearBtn.addActionListener(e -> switchToAddMode());
        btnPanel.add(saveBtn);
        btnPanel.add(clearBtn);
        
        // 状态提示标签
        JLabel statusHint = new JLabel("点击列表行可编辑");
        statusHint.setForeground(Gray._120);
        statusHint.setFont(statusHint.getFont().deriveFont(11f));
        statusHint.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
        
        JPanel inner = new JPanel(new BorderLayout(4, 6));
        inner.add(formTitleLabel, BorderLayout.NORTH);
        inner.add(fields, BorderLayout.CENTER);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(btnPanel, BorderLayout.NORTH);
        bottomPanel.add(statusHint, BorderLayout.SOUTH);
        inner.add(bottomPanel, BorderLayout.SOUTH);
        
        panel.add(inner, BorderLayout.NORTH);
        return panel;
    }
    
    // ===================== 表单状态切换 =====================
    
    /**
     * 切换到新增模式：清空表单、取消列表选中
     */
    private void switchToAddMode() {
        editingItem = null;
        codeField.setText("");
        codeField.setEnabled(true);
        nameField.setText("");
        sortField.setText("");
        table.clearSelection();
        deleteBtn.setEnabled(false);
        formTitleLabel.setText("新增" + typeName);
        saveBtn.setText("保存新增");
        codeField.requestFocus();
    }
    
    /**
     * 将选中的字典项加载到表单（编辑模式）
     */
    private void loadItemToForm(DictItem item) {
        editingItem = item;
        codeField.setText(String.valueOf(item.getTypeCode()));
        if (isResourceType && CommUtil.DEFAULT_RESOURCE_TYPES.contains(item.getTypeCode())) {
            codeField.setEnabled(false);
        }
        if (!isResourceType && CommUtil.DEFAULT_PERMISSION_TYPES.contains(item.getTypeCode())) {
            codeField.setEnabled(false);
        }
        nameField.setText(item.getTypeName());
        sortField.setText(String.valueOf(item.getSort()));
        deleteBtn.setEnabled(true);
        formTitleLabel.setText("编辑" + typeName);
        saveBtn.setText("保存修改");
    }
    
    // ===================== 数据操作 =====================
    
    private List<DictItem> loadItems() {
        return isResourceType ? dictDao.getResourceTypes() : dictDao.getPermissionTypes();
    }
    
    private void refreshTable() {
        tableModel.setItems(loadItems());
        tableModel.fireTableDataChanged();
    }
    
    /**
     * 保存表单（新增 or 编辑）
     */
    private void saveForm() {
        // 解析字段
        int code;
        String name = nameField.getText().trim();
        int sort;
        try {
            code = Integer.parseInt(codeField.getText().trim());
        } catch (NumberFormatException ex) {
            showError("类型编码必须是整数");
            return;
        }
        if (name.isEmpty()) {
            showError("类型名称不能为空");
            return;
        }
        try {
            sort = sortField.getText().trim().isEmpty() ? 0 : Integer.parseInt(sortField.getText().trim());
        } catch (NumberFormatException ex) {
            showError("排序必须是整数");
            return;
        }
        
        if (editingItem == null) {
            // ---- 新增 ----
            boolean exists = isResourceType ? dictDao.resourceTypeCodeExists(code, null) : dictDao.permissionTypeCodeExists(code, null);
            if (exists) {
                showError("类型编码已存在：" + code);
                return;
            }
            try {
                DictItem newItem = new DictItem(code, name, sort);
                if (isResourceType) {
                    dictDao.addResourceType(newItem);
                } else {
                    dictDao.addPermissionType(newItem);
                }
                refreshTable();
                switchToAddMode();
                showInfo("新增成功");
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        } else {
            // ---- 编辑 ----
            boolean exists = isResourceType ? dictDao.resourceTypeCodeExists(code, editingItem.getId())
                    : dictDao.permissionTypeCodeExists(code, editingItem.getId());
            if (exists) {
                showError("类型编码已存在：" + code);
                return;
            }
            try {
                DictItem updated = new DictItem(code, name, sort);
                updated.setId(editingItem.getId());
                if (isResourceType) {
                    dictDao.updateResourceType(updated);
                } else {
                    dictDao.updatePermissionType(updated);
                }
                editingItem = updated; // 更新引用
                refreshTable();
                showInfo("保存成功");
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        }
    }
    
    /**
     * 删除列表中选中的行
     */
    private void deleteSelectedItem() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        DictItem selected = tableModel.getItem(row);
        if (isResourceType) {
            if (CommUtil.DEFAULT_RESOURCE_TYPES.contains(selected.getTypeCode())) {
                Messages.showWarningDialog(getContentPanel(), "默认资源类型不能删除", "提示");
                return;
            }
        } else {
            if (CommUtil.DEFAULT_PERMISSION_TYPES.contains(selected.getTypeCode())) {
                Messages.showWarningDialog(getContentPanel(), "默认权限类型不能删除", "提示");
                return;
            }
        }
        int confirm = Messages.showYesNoDialog(getContentPanel(), "确定删除【" + selected.getTypeName() + "】？删除后不可恢复", "确认删除",
                Messages.getQuestionIcon());
        if (confirm != Messages.YES) {
            return;
        }
        try {
            if (isResourceType) {
                dictDao.deleteResourceType(selected.getId());
            } else {
                dictDao.deletePermissionType(selected.getId());
            }
            refreshTable();
            switchToAddMode();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }
    
    // ===================== 工具 =====================
    
    private void showError(String msg) {
        Messages.showErrorDialog(getContentPanel(), msg, "操作失败");
    }
    
    private void showInfo(String msg) {
        Messages.showInfoMessage(getContentPanel(), msg, "提示");
    }
    
    @Override
    protected void doOKAction() {
        close(OK_EXIT_CODE);
    }
    
    // ===================== 内部类：表格模型 =====================
    
    private static class DictTableModel extends AbstractTableModel {
        
        private static final String[] COLUMNS = {"ID", "编码", "名称", "排序"};
        
        private List<DictItem> items;
        
        public DictTableModel(List<DictItem> items) {
            this.items = items;
        }
        
        public void setItems(List<DictItem> items) {
            this.items = items;
        }
        
        public DictItem getItem(int row) {
            return items.get(row);
        }
        
        @Override
        public int getRowCount() {
            return items.size();
        }
        
        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }
        
        @Override
        public String getColumnName(int col) {
            return COLUMNS[col];
        }
        
        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
        
        @Override
        public Object getValueAt(int row, int col) {
            DictItem item = items.get(row);
            return switch (col) {
                case 0 -> item.getId();
                case 1 -> item.getTypeCode();
                case 2 -> item.getTypeName();
                case 3 -> item.getSort();
                default -> "";
            };
        }
    }
}
