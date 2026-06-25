package com.lsy.idea.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.lsy.idea.db.DatabaseManager;
import com.lsy.idea.db.DictDao;
import com.lsy.idea.db.TemplateDao;
import com.lsy.idea.model.DictItem;
import com.lsy.idea.model.SqlTemplate;
import com.lsy.idea.model.VariableInfo;
import com.lsy.idea.ui.HttpHeadersPanel;
import com.lsy.idea.util.CommUtil;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.List;
import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/**
 * Settings → Tools → Api2Sql 提供模板管理（增删改导入导出）+ 字典管理两个 Tab
 */
public class PluginSettingsConfigurable implements Configurable {
    
    private JPanel mainPanel;
    
    // ===== 模板管理 Tab =====
    private DefaultListModel<String> templateListModel;
    
    private JList<String> templateList;
    
    private JTextField templateNameField;
    
    private JTextArea templateContentArea;
    
    private JTextField creatorIdField;
    
    private JTextField updaterIdField;
    
    private JTextField httpCodeApiField;
    
    private JRadioButton modeManualBtn;
    
    private JRadioButton modeHttpBtn;
    
    private HttpHeadersPanel httpHeadersPanel;
    
    private TemplateDao templateDao;
    
    private DictDao dictDao;
    
    private JButton testHttpBtn;
    
    private SqlTemplate currentTemplate;
    
    private DictTablePanel dictResourcePanel;
    
    private DictTablePanel dictPermissionPanel;
    
    private JPanel httpConfigGroup;
    
    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Api2Sql";
    }
    
    @Override
    public @Nullable JComponent createComponent() {
        // 获取数据库连接
        Project project = getActiveProject();
        if (project != null) {
            DatabaseManager db = DatabaseManager.getInstance(project);
            if (db != null && db.getConnection() != null) {
                Connection conn = db.getConnection();
                templateDao = new TemplateDao(conn);
                dictDao = new DictDao(conn);
            }
        }
        
        mainPanel = new JPanel(new BorderLayout());
        final JBTabbedPane tabbedPane = new JBTabbedPane();
        
        tabbedPane.addTab("模板管理", buildTemplateTab());
        tabbedPane.addTab("字典管理", buildDictTab());
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        return mainPanel;
    }
    
    // ===================== 模板管理 Tab =====================
    
    private JPanel buildTemplateTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        
        // ---- 左侧：模板列表 + 按钮 ----
        templateListModel = new DefaultListModel<>();
        templateList = new JBList<>(templateListModel);
        templateList.setVisibleRowCount(15);
        templateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refreshTemplateList();
        
        JScrollPane listScroll = new JBScrollPane(templateList);
        listScroll.setPreferredSize(new Dimension(180, 300));
        
        JPanel btnPanel = new JPanel();
        btnPanel.setLayout(new BoxLayout(btnPanel, BoxLayout.Y_AXIS));
        
        JButton addBtn = createBtn("新建模板", e -> addTemplate());
        JButton saveBtn = createBtn("保存修改", e -> saveCurrentTemplate());
        JButton deleteBtn = createBtn("删除模板", e -> deleteTemplate());
        JButton exportBtn = createBtn("导出模板", e -> exportTemplate());
        JButton importBtn = createBtn("导入模板", e -> importTemplate());
        
        for (JButton btn : new JButton[] {addBtn, saveBtn, deleteBtn, exportBtn, importBtn}) {
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        btnPanel.add(addBtn);
        btnPanel.add(Box.createVerticalStrut(4));
        btnPanel.add(importBtn);
        //左边面板
        JPanel leftPanel = new JPanel(new BorderLayout(4, 4));
        leftPanel.add(listScroll, BorderLayout.CENTER);
        leftPanel.add(btnPanel, BorderLayout.SOUTH);
        //右边边面板
        JPanel rightPanel = new JPanel(new BorderLayout(4, 0));
        rightPanel.setPreferredSize(new Dimension(400, 500));
        JPanel formPanel = new JPanel(new GridBagLayout());
        JPanel optBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        optBtnPanel.add(saveBtn);
        optBtnPanel.add(deleteBtn);
        optBtnPanel.add(exportBtn);
        rightPanel.add(optBtnPanel, BorderLayout.NORTH);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        int row = 0;
        // 模板名称
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        formPanel.add(new JLabel("模板名称："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        templateNameField = new JTextField();
        formPanel.add(templateNameField, gbc);
        
        // 创建人ID
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        formPanel.add(new JLabel("创建人ID："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        creatorIdField = new JTextField();
        formPanel.add(creatorIdField, gbc);
        // 更新人ID
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        formPanel.add(new JLabel("更新人ID："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        updaterIdField = new JTextField();
        formPanel.add(updaterIdField, gbc);
        // 默认编码模式
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        formPanel.add(new JLabel("默认编码模式："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        modeManualBtn = new JRadioButton("手动录入");
        modeHttpBtn = new JRadioButton("HTTP获取");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(modeManualBtn);
        modeGroup.add(modeHttpBtn);
        modeManualBtn.setSelected(true);
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        modePanel.add(modeManualBtn);
        modePanel.add(modeHttpBtn);
        formPanel.add(modePanel, gbc);
        // ---- HTTP 配置区（地址 + 测试按钮 + 请求头，归组）----
        httpCodeApiField = new JTextField();
        testHttpBtn = new JButton("测试");
        final JPanel httpApiRow = new JPanel(new BorderLayout(0, 0));
        httpApiRow.add(new JLabel("地址："), BorderLayout.WEST);
        httpApiRow.add(httpCodeApiField, BorderLayout.CENTER);
        httpApiRow.add(testHttpBtn, BorderLayout.EAST);
        httpHeadersPanel = new HttpHeadersPanel();
        httpConfigGroup = new JPanel(new BorderLayout(0, 0));
        httpConfigGroup.setBorder(BorderFactory.createTitledBorder("HTTP 配置"));
        httpConfigGroup.add(httpApiRow, BorderLayout.NORTH);
        httpConfigGroup.add(httpHeadersPanel, BorderLayout.CENTER);
        //        httpConfigGroup.setPreferredSize(new Dimension(200, 300));
        httpConfigGroup.setVisible(false);
        testHttpBtn.addActionListener(
                e -> CommUtil.doTestHttpRequest(httpCodeApiField.getText().trim(), httpHeadersPanel.getHeaders(), testHttpBtn, mainPanel));
        row++;
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        formPanel.add(httpConfigGroup, gbc);
        // 模板内容
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTH;
        formPanel.add(new JLabel("模板内容："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        templateContentArea = new JTextArea(8, 40);
        templateContentArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        templateContentArea.setLineWrap(false);
        JBScrollPane contentScroll = new JBScrollPane(templateContentArea);
        contentScroll.setMinimumSize(new Dimension(300, 120));
        formPanel.add(contentScroll, gbc);
        final JLabel hint = new JLabel("内置变量说明");
        hint.setForeground(new JBColor(JBColor.BLUE, JBColor.BLUE));
        hint.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        hint.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                hint.setText("<html><u>内置变量说明</u></html>");
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                hint.setText("内置变量说明");
            }
            
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                final List<VariableInfo> variableInfos = CommUtil.defaultVariable();
                final String[] COLUMN_NAMES = {"变量", "说明"};
                final AbstractTableModel tableModel = new AbstractTableModel() {
                    @Override
                    public int getRowCount() {
                        return variableInfos.size();
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
                        return String.class;
                    }
                    
                    @Override
                    public Object getValueAt(int rowIndex, int columnIndex) {
                        final VariableInfo variableInfo = variableInfos.get(rowIndex);
                        return switch (columnIndex) {
                            case 0 -> "${" + variableInfo.getName() + "}";
                            case 1 -> variableInfo.getDescription();
                            default -> "";
                        };
                    }
                };
                JBScrollPane scrollPane = new JBScrollPane(new JTable(tableModel));
                scrollPane.setPreferredSize(new Dimension(500, 300));
                JOptionPane.showMessageDialog(mainPanel, scrollPane, "变量说明", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        row++;
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        gbc.weighty = 1;
        formPanel.add(hint, gbc);
        // 模式切换监听
        modeManualBtn.addActionListener(e -> updateHttpModeVisibility(false));
        modeHttpBtn.addActionListener(e -> updateHttpModeVisibility(true));
        // ---- 列表选中事件 ----
        ListSelectionListener selectionListener = e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            loadSelectedTemplate();
        };
        templateList.addListSelectionListener(selectionListener);
        rightPanel.add(new JBScrollPane(formPanel), BorderLayout.CENTER);
        // ---- 分割面板 ----
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(150);
        splitPane.setDividerSize(5);
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }
    
    private void updateHttpModeVisibility(boolean httpMode) {
        httpConfigGroup.setVisible(httpMode);
        // 逐级刷新布局
        Container c = httpConfigGroup.getParent();
        while (c != null) {
            c.revalidate();
            c.repaint();
            c = c.getParent();
        }
    }
    
    private void refreshTemplateList() {
        templateListModel.clear();
        if (templateDao == null) {
            return;
        }
        for (SqlTemplate t : templateDao.getAllTemplates()) {
            templateListModel.addElement(t.getTemplateName());
        }
    }
    
    private void loadSelectedTemplate() {
        String name = templateList.getSelectedValue();
        if (name == null || templateDao == null) {
            clearForm();
            return;
        }
        currentTemplate = templateDao.getByName(name);
        if (currentTemplate == null) {
            clearForm();
            return;
        }
        
        templateNameField.setText(currentTemplate.getTemplateName());
        creatorIdField.setText(currentTemplate.getCreatorIdValue());
        updaterIdField.setText(currentTemplate.getUpdaterIdValue());
        httpCodeApiField.setText(currentTemplate.getHttpCodeApi());
        modeManualBtn.setSelected(currentTemplate.getDefaultCodeMode() == 0);
        modeHttpBtn.setSelected(currentTemplate.getDefaultCodeMode() == 1);
        templateContentArea.setText(currentTemplate.getTemplateContent());
        templateContentArea.setCaretPosition(0);
        httpHeadersPanel.populateFromTemplate(currentTemplate);
        httpConfigGroup.setVisible(currentTemplate.getDefaultCodeMode() == 1);
        //        extConfigPanel.populateFromTemplate(currentTemplate);
    }
    
    private void clearForm() {
        currentTemplate = null;
        templateNameField.setText("");
        creatorIdField.setText("");
        updaterIdField.setText("");
        httpCodeApiField.setText("");
        modeManualBtn.setSelected(true);
        httpConfigGroup.setVisible(false);
        templateContentArea.setText("");
        httpHeadersPanel.clear();
    }
    
    private void addTemplate() {
        if (templateDao == null) {
            Messages.showWarningDialog(mainPanel, "数据库未初始化，请重启IDE后重试", "提示");
            return;
        }
        String name = Messages.showInputDialog(mainPanel, "请输入新模板名称：", "新建模板", Messages.getQuestionIcon());
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        name = name.trim();
        if (templateDao.nameExists(name, null)) {
            Messages.showWarningDialog(mainPanel, "模板名称已存在：" + name, "名称冲突");
            return;
        }
        SqlTemplate t = new SqlTemplate(name, "");
        templateDao.saveTemplate(t);
        refreshTemplateList();
        templateList.setSelectedValue(name, true);
    }
    
    private void saveCurrentTemplate() {
        if (currentTemplate == null || templateDao == null) {
            return;
        }
        String name = templateNameField.getText().trim();
        if (name.isEmpty()) {
            Messages.showWarningDialog(mainPanel, "模板名称不能为空", "校验失败");
            return;
        }
        if (templateDao.nameExists(name, currentTemplate.getId())) {
            Messages.showWarningDialog(mainPanel, "模板名称已存在：" + name, "名称冲突");
            return;
        }
        currentTemplate.setTemplateName(name);
        currentTemplate.setTemplateContent(templateContentArea.getText());
        currentTemplate.setCreatorIdValue(creatorIdField.getText().trim());
        currentTemplate.setUpdaterIdValue(updaterIdField.getText().trim());
        currentTemplate.setHttpCodeApi(httpCodeApiField.getText().trim());
        currentTemplate.setDefaultCodeMode(modeHttpBtn.isSelected() ? 1 : 0);
        httpHeadersPanel.applyToTemplate(currentTemplate);
        templateDao.updateTemplate(currentTemplate);
        final String templateName = currentTemplate.getTemplateName();
        refreshTemplateList();
        templateList.setSelectedValue(templateName, true);
        Messages.showInfoMessage(mainPanel, "模板保存成功", "成功");
    }
    
    private void deleteTemplate() {
        String name = templateList.getSelectedValue();
        if (name == null || templateDao == null) {
            return;
        }
        int confirm = Messages.showYesNoDialog(mainPanel, "确定删除模板【" + name + "】？删除后无法恢复", "确认删除", Messages.getQuestionIcon());
        if (confirm != Messages.YES) {
            return;
        }
        if (currentTemplate != null) {
            templateDao.deleteTemplate(currentTemplate.getId());
        }
        refreshTemplateList();
        clearForm();
    }
    
    private void exportTemplate() {
        if (currentTemplate == null || templateDao == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(currentTemplate.getTemplateName() + "_template.json"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));
        if (chooser.showSaveDialog(mainPanel) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File file = chooser.getSelectedFile();
        String json = templateDao.exportTemplateAsJson(currentTemplate);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(json);
            Messages.showInfoMessage(mainPanel, "模板导出成功：" + file.getAbsolutePath(), "成功");
        } catch (IOException e) {
            Messages.showErrorDialog(mainPanel, "文件保存失败：" + e.getMessage(), "导出失败");
        }
    }
    
    private void importTemplate() {
        if (templateDao == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));
        if (chooser.showOpenDialog(mainPanel) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File file = chooser.getSelectedFile();
        if (file.length() > 1024 * 1024) {
            Messages.showWarningDialog(mainPanel, "文件过大，请选择1MB以内的JSON文件", "文件过大");
            return;
        }
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String importedName = templateDao.importTemplateFromJson(json);
            refreshTemplateList();
            templateList.setSelectedValue(importedName, true);
            Messages.showInfoMessage(mainPanel, "模板导入成功：" + importedName, "成功");
        } catch (RuntimeException e) {
            Messages.showErrorDialog(mainPanel, e.getMessage(), "导入失败");
        } catch (IOException e) {
            Messages.showErrorDialog(mainPanel, "读取文件失败：" + e.getMessage(), "导入失败");
        }
    }
    
    // ===================== 字典管理 Tab =====================
    
    private JPanel buildDictTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        
        JBTabbedPane dictTabs = new JBTabbedPane(SwingConstants.LEFT);
        dictTabs.addTab("资源类型", buildDictSubPanel("resource"));
        dictTabs.addTab("权限类型", buildDictSubPanel("permission"));
        
        panel.add(dictTabs, BorderLayout.CENTER);
        return panel;
    }
    
    private JPanel buildDictSubPanel(String type) {
        final boolean isResource = "resource".equals(type);
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        // 表格模型：ID / 编码 / 名称 / 排序，编码/名称/排序可编辑
        final AbstractTableModel tableModel = new AbstractTableModel() {
            private final String[] COLUMNS = {"ID", "编码", "名称", "排序"};
            
            private List<DictItem> items = loadDictItems(isResource);
            
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
                final DictItem dictItem = items.get(row);
                if (dictItem != null && col == 1) {
                    if (isResource && CommUtil.DEFAULT_RESOURCE_TYPES.contains(dictItem.getTypeCode())) {
                        return false;
                    }
                    if (!isResource && CommUtil.DEFAULT_PERMISSION_TYPES.contains(dictItem.getTypeCode())) {
                        return false;
                    }
                }
                return col >= 1;
            }
            
            @Override
            public Object getValueAt(int row, int col) {
                DictItem item = items.get(row);
                return switch (col) {
                    case 0 -> item.getId() != null ? item.getId() : "(新)";
                    case 1 -> item.getTypeCode();
                    case 2 -> item.getTypeName();
                    case 3 -> item.getSort();
                    default -> "";
                };
            }
            
            @Override
            public void setValueAt(Object value, int row, int col) {
                DictItem item = items.get(row);
                try {
                    switch (col) {
                        case 1 -> item.setTypeCode(Integer.parseInt(value.toString().trim()));
                        case 2 -> item.setTypeName(value.toString().trim());
                        case 3 -> item.setSort(Integer.parseInt(value.toString().trim()));
                    }
                } catch (NumberFormatException ex) {
                    Messages.showErrorDialog(panel, "请输入有效的整数", "格式错误");
                    return;
                }
                fireTableCellUpdated(row, col);
            }
            
            public DictItem getItem(int row) {
                return items.get(row);
            }
            
            public List<DictItem> getItems() {
                return items;
            }
            
            public void refresh() {
                items = loadDictItems(isResource);
                fireTableDataChanged();
            }
        };
        
        JBTable table = new JBTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);
        table.getColumnModel().getColumn(3).setPreferredWidth(50);
        // 隐藏 ID 列
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);
        
        // 单元格编辑失去焦点时自动保存
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        // 编辑提交时保存到数据库
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
        });
        table.addPropertyChangeListener("tableCellEditor", evt -> {
            if (evt.getNewValue() == null) {
                saveCurrentRow(table, tableModel, isResource);
            }
        });
        
        JBScrollPane scrollPane = new JBScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // 工具栏：新增 / 删除
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton addBtn = new JButton("+ 新增");
        JButton deleteBtn = new JButton("删除");
        addBtn.addActionListener(e -> addDictRow(table, tableModel, isResource));
        deleteBtn.addActionListener(e -> deleteDictRow(table, tableModel, isResource));
        toolbar.add(addBtn);
        toolbar.add(deleteBtn);
        panel.add(toolbar, BorderLayout.NORTH);
        
        // 保存面板引用，供 reset() 刷新
        DictTablePanel dictPanel = new DictTablePanel(table, tableModel, isResource);
        if (isResource) {
            dictResourcePanel = dictPanel;
        } else {
            dictPermissionPanel = dictPanel;
        }
        
        return panel;
    }
    
    /**
     * 加载字典数据
     */
    private List<DictItem> loadDictItems(boolean isResource) {
        if (dictDao == null) {
            return new java.util.ArrayList<>();
        }
        return isResource ? dictDao.getResourceTypes() : dictDao.getPermissionTypes();
    }
    
    /**
     * 保存当前编辑行到数据库
     */
    private void saveCurrentRow(JBTable table, AbstractTableModel tableModel, boolean isResource) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= tableModel.getRowCount()) {
            return;
        }
        
        // 获取编辑后的数据
        Object codeObj = tableModel.getValueAt(row, 1);
        Object nameObj = tableModel.getValueAt(row, 2);
        Object sortObj = tableModel.getValueAt(row, 3);
        
        int typeCode;
        String typeName;
        int sort;
        try {
            typeCode = Integer.parseInt(codeObj.toString().trim());
            typeName = nameObj.toString().trim();
            sort = Integer.parseInt(sortObj.toString().trim());
        } catch (NumberFormatException ex) {
            Messages.showErrorDialog(table, "编码和排序必须是整数", "格式错误");
            return;
        }
        if (typeName.isEmpty()) {
            Messages.showErrorDialog(table, "名称不能为空", "校验失败");
            return;
        }
        
        Object idObj = tableModel.getValueAt(row, 0);
        Long id = (idObj instanceof Long) ? (Long) idObj : null;
        
        try {
            if (id == null) {
                // 新增
                boolean exists = isResource ? dictDao.resourceTypeCodeExists(typeCode, null) : dictDao.permissionTypeCodeExists(typeCode, null);
                if (exists) {
                    Messages.showErrorDialog(table, "编码已存在：" + typeCode, "冲突");
                    return;
                }
                DictItem newItem = new DictItem(typeCode, typeName, sort);
                if (isResource) {
                    dictDao.addResourceType(newItem);
                } else {
                    dictDao.addPermissionType(newItem);
                }
            } else {
                // 更新
                boolean exists = isResource ? dictDao.resourceTypeCodeExists(typeCode, id) : dictDao.permissionTypeCodeExists(typeCode, id);
                if (exists) {
                    Messages.showErrorDialog(table, "编码已存在：" + typeCode, "冲突");
                    return;
                }
                DictItem updated = new DictItem(typeCode, typeName, sort);
                updated.setId(id);
                if (isResource) {
                    dictDao.updateResourceType(updated);
                } else {
                    dictDao.updatePermissionType(updated);
                }
            }
            // 刷新表格
            refreshDictTable(table, tableModel, isResource);
        } catch (RuntimeException ex) {
            Messages.showErrorDialog(table, ex.getMessage(), "保存失败");
        }
    }
    
    /**
     * 新增一行（空数据），用户直接在表格中编辑
     */
    private void addDictRow(JTable table, AbstractTableModel tableModel, boolean isResource) {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        
        DictItem newItem = new DictItem(10, "", 10);
        ((java.util.List<DictItem>) getDictItems(tableModel)).add(newItem);
        tableModel.fireTableRowsInserted(tableModel.getRowCount() - 1, tableModel.getRowCount() - 1);
        
        // 选中新增行并开始编辑编码列
        int lastRow = tableModel.getRowCount() - 1;
        table.setRowSelectionInterval(lastRow, lastRow);
        table.editCellAt(lastRow, 1);
        table.scrollRectToVisible(table.getCellRect(lastRow, 1, true));
    }
    
    @SuppressWarnings("unchecked")
    private List<DictItem> getDictItems(AbstractTableModel tableModel) {
        try {
            java.lang.reflect.Method m = tableModel.getClass().getMethod("getItems");
            return (List<DictItem>) m.invoke(tableModel);
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }
    
    /**
     * 删除选中行
     */
    private void deleteDictRow(JTable table, AbstractTableModel tableModel, boolean isResource) {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        
        Object idObj = tableModel.getValueAt(row, 0);
        Long id = (idObj instanceof Long) ? (Long) idObj : null;
        
        if (id == null) {
            // 未保存的新行，直接从模型移除
            @SuppressWarnings("unchecked") List<DictItem> items = getDictItems(tableModel);
            items.remove(row);
            tableModel.fireTableRowsDeleted(row, row);
            return;
        }
        if (isResource) {
            final DictItem resourceType = dictDao.getResourceType(id);
            if (resourceType == null) {
                Messages.showErrorDialog(table, "资源类型不存在：" + id, "错误");
                return;
            }
            if (CommUtil.DEFAULT_RESOURCE_TYPES.contains(resourceType.getTypeCode())) {
                Messages.showWarningDialog(mainPanel, "默认资源类型不能删除", "提示");
                return;
            }
        } else {
            final DictItem permissionType = dictDao.getPermissionType(id);
            if (permissionType == null) {
                Messages.showErrorDialog(table, "权限类型不存在：" + id, "错误");
                return;
            }
            if (CommUtil.DEFAULT_PERMISSION_TYPES.contains(permissionType.getTypeCode())) {
                Messages.showWarningDialog(mainPanel, "默认权限类型不能删除", "提示");
                return;
            }
        }
        String name = tableModel.getValueAt(row, 2).toString();
        int confirm = Messages.showYesNoDialog(mainPanel, "确定删除【" + name + "】？删除后不可恢复", "确认删除", Messages.getQuestionIcon());
        if (confirm != Messages.YES) {
            return;
        }
        
        try {
            if (isResource) {
                dictDao.deleteResourceType(id);
            } else {
                dictDao.deletePermissionType(id);
            }
            refreshDictTable(table, tableModel, isResource);
        } catch (RuntimeException ex) {
            Messages.showErrorDialog(table, ex.getMessage(), "删除失败");
        }
    }
    
    /**
     * 刷新字典表格数据
     */
    private void refreshDictTable(JTable table, AbstractTableModel tableModel, boolean isResource) {
        @SuppressWarnings("unchecked") List<DictItem> items = getDictItems(tableModel);
        items.clear();
        items.addAll(loadDictItems(isResource));
        tableModel.fireTableDataChanged();
    }
    
    /**
     * 字典表格面板封装（用于 reset 时刷新）
     */
    private static class DictTablePanel {
        
        final JTable table;
        
        final AbstractTableModel tableModel;
        
        final boolean isResource;
        
        DictTablePanel(JTable table, AbstractTableModel tableModel, boolean isResource) {
            this.table = table;
            this.tableModel = tableModel;
            this.isResource = isResource;
        }
        
        void refresh() {
            tableModel.fireTableDataChanged();
        }
    }
    
    // ===================== 工具方法 =====================
    
    private JButton createBtn(String text, java.awt.event.ActionListener listener) {
        JButton btn = new JButton(text);
        btn.addActionListener(listener);
        return btn;
    }
    
    
    @Nullable
    private Project getActiveProject() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        return projects.length > 0 ? projects[0] : null;
    }
    
    @Override
    public boolean isModified() {
        return false;
    }
    
    @Override
    public void apply() throws ConfigurationException {
    }
    
    @Override
    public void reset() {
        refreshTemplateList();
        if (dictResourcePanel != null) {
            refreshDictTable(dictResourcePanel.table, dictResourcePanel.tableModel, true);
        }
        if (dictPermissionPanel != null) {
            refreshDictTable(dictPermissionPanel.table, dictPermissionPanel.tableModel, false);
        }
    }
}
