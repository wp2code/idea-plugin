package com.lsy.idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.lsy.idea.db.DatabaseManager;
import com.lsy.idea.db.DictDao;
import com.lsy.idea.db.TemplateDao;
import com.lsy.idea.generator.SqlGenerator;
import com.lsy.idea.model.DictItem;
import com.lsy.idea.model.InterfaceInfo;
import com.lsy.idea.model.SqlTemplate;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.swing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 主对话框（三区布局）： 上部：模板选择区（下拉+操作+字典管理） 中部：接口列表（InterfaceListTable） 下部：SQL预览区（生成+复制+导出）
 */
public class MainDialog extends DialogWrapper {
    
    private final Project project;
    
    private final List<InterfaceInfo> interfaces;
    
    private TemplateDao templateDao;
    
    private DictDao dictDao;
    
    private List<SqlTemplate> allTemplates;
    
    private SqlTemplate currentTemplate;
    
    // ---- 模板选择区 ----
    private ComboBox<String> templateCombo;
    
    // ---- 接口列表 ----
    private InterfaceListTable interfaceListTable;
    
    // ---- SQL 预览区 ----
    private JLabel warningLabel;
    
    private JTextArea sqlPreviewArea;
    
    private JButton copyBtn;
    
    private JButton exportBtn;
    
    public MainDialog(@Nullable Project project, @NotNull List<InterfaceInfo> interfaces) {
        super(project);
        this.project = project;
        this.interfaces = interfaces;
        
        if (project != null) {
            DatabaseManager db = DatabaseManager.getInstance(project);
            if (db != null && db.getConnection() != null) {
                templateDao = new TemplateDao(db.getConnection());
                dictDao = new DictDao(db.getConnection());
            }
        }
        
        setTitle("生成权限SQL — 共 " + interfaces.size() + " 个接口");
        setOKButtonText("关闭");
        setModal(false);
        init();
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setMinimumSize(new Dimension(1100, 680));
        root.setPreferredSize(new Dimension(1100, 760));
        root.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        
        // ---- 上部：模板选择区 ----
        root.add(buildTemplateBar(), BorderLayout.NORTH);
        
        // ---- 中部：接口列表 ----
        List<DictItem> resourceTypes = dictDao != null ? dictDao.getResourceTypes() : List.of();
        List<DictItem> permissionTypes = dictDao != null ? dictDao.getPermissionTypes() : List.of();
        
        interfaceListTable = new InterfaceListTable(interfaces);
        interfaceListTable.setDictData(resourceTypes, permissionTypes);
        interfaceListTable.setBorder(BorderFactory.createTitledBorder("接口配置列表"));
        
        // ---- 下部：SQL 预览区 ----
        JPanel previewPanel = buildPreviewPanel();
        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, interfaceListTable, previewPanel);
        centerSplit.setDividerSize(5);
        centerSplit.setDividerLocation(320);
        centerSplit.setBorder(null);
        root.add(centerSplit, BorderLayout.CENTER);
        loadTemplates();
        return root;
    }
    
    // ===================== 模板选择区 =====================
    
    private JPanel buildTemplateBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        bar.setBorder(BorderFactory.createTitledBorder("模板配置"));
        
        bar.add(new JLabel("选择模板："));
        
        templateCombo = new ComboBox<>();
        templateCombo.setPreferredSize(new Dimension(220, 26));
        templateCombo.addActionListener(e -> onTemplateSelected());
        bar.add(templateCombo);
        
        ComboBox<String> actionCombo = new ComboBox<>();
        actionCombo.setPreferredSize(new Dimension(110, 26));
        actionCombo.addItem("模板操作");
        actionCombo.addItem("新建模板");
        actionCombo.addItem("编辑模板");
        actionCombo.addItem("删除模板");
        actionCombo.addItem("导出模板");
        actionCombo.addItem("导入模板");
        actionCombo.setSelectedIndex(0);
        actionCombo.addActionListener(e -> {
            if (e.getActionCommand().equals("comboBoxChanged")) {
                String action = (String) actionCombo.getSelectedItem();
                if (action != null && !action.equals("模板操作")) {
                    onTemplateAction(action);
                    actionCombo.setSelectedItem("模板操作");
                }
            }
        });
        bar.add(actionCombo);
        
        bar.add(Box.createHorizontalStrut(12));
        
        JButton dictBtn = new JButton("字典管理");
        dictBtn.addActionListener(e -> showDictManageMenu(dictBtn));
        bar.add(dictBtn);
        
        return bar;
    }
    
    // ===================== SQL 预览区 =====================
    
    private JPanel buildPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("SQL 预览"));
        
        warningLabel = new JLabel();
        warningLabel.setForeground(new JBColor(JBColor.YELLOW, JBColor.YELLOW));
        warningLabel.setVisible(false);
        panel.add(warningLabel, BorderLayout.NORTH);
        
        sqlPreviewArea = new JTextArea();
        sqlPreviewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        sqlPreviewArea.setEditable(false);
        sqlPreviewArea.setLineWrap(false);
        JBScrollPane scroll = new JBScrollPane(sqlPreviewArea);
        scroll.setPreferredSize(new Dimension(800, 160));
        panel.add(scroll, BorderLayout.CENTER);
        
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        
        JButton generateBtn = new JButton("生成脚本");
        generateBtn.setFont(generateBtn.getFont().deriveFont(Font.BOLD));
        generateBtn.addActionListener(e -> generateSql());
        
        copyBtn = new JButton("一键复制全部SQL");
        copyBtn.setEnabled(false);
        copyBtn.addActionListener(e -> copySql());
        
        exportBtn = new JButton("导出SQL文件");
        exportBtn.setEnabled(false);
        exportBtn.addActionListener(e -> exportSqlFile());
        
        btnRow.add(generateBtn);
        btnRow.add(copyBtn);
        btnRow.add(exportBtn);
        panel.add(btnRow, BorderLayout.SOUTH);
        
        return panel;
    }
    
    // ===================== 模板加载与切换 =====================
    
    private void loadTemplates() {
        templateCombo.removeAllItems();
        if (templateDao == null) {
            return;
        }
        
        allTemplates = templateDao.getAllTemplates();
        for (SqlTemplate t : allTemplates) {
            templateCombo.addItem(t.getTemplateName());
        }
        if (!allTemplates.isEmpty()) {
            templateCombo.setSelectedIndex(0);
        }
    }
    
    private void onTemplateSelected() {
        String name = (String) templateCombo.getSelectedItem();
        if (name == null || allTemplates == null) {
            return;
        }
        
        SqlTemplate selected = allTemplates.stream().filter(t -> t.getTemplateName().equals(name)).findFirst().orElse(null);
        if (selected == null) {
            return;
        }
        
        int newMode = selected.getDefaultCodeMode();
        for (InterfaceInfo info : interfaces) {
            if (!info.isUserModified()) {
                info.setCodeMode(newMode);
                info.setHttpCodeApi(selected.getHttpCodeApi());
            }
        }
        
        currentTemplate = selected;
        interfaceListTable.setCurrentTemplate(selected);
        interfaceListTable.refresh();
    }
    
    // ===================== 字典管理 =====================
    
    private void onTemplateAction(String action) {
        if (action == null) {
            return;
        }
        switch (action) {
            case "新建模板" -> createNewTemplate();
            case "编辑模板" -> editCurrentTemplate();
            case "删除模板" -> deleteCurrentTemplate();
            case "导出模板" -> exportCurrentTemplate();
            case "导入模板" -> importTemplate();
        }
    }
    
    private void showDictManageMenu(JButton sourceBtn) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem resourceItem = new JMenuItem("资源类型字典");
        JMenuItem permissionItem = new JMenuItem("权限类型字典");
        resourceItem.addActionListener(e -> openDictDialog(true));
        permissionItem.addActionListener(e -> openDictDialog(false));
        menu.add(resourceItem);
        menu.add(permissionItem);
        menu.show(sourceBtn, 0, sourceBtn.getHeight());
    }
    
    private void openDictDialog(boolean isResourceType) {
        if (dictDao == null || project == null) {
            Messages.showWarningDialog(getContentPanel(), "数据库未初始化", "提示");
            return;
        }
        DictManageDialog dialog = new DictManageDialog(project, dictDao, isResourceType);
        dialog.show();
        refreshDictData();
    }
    
    private void refreshDictData() {
        if (dictDao == null) {
            return;
        }
        List<DictItem> resourceTypes = dictDao.getResourceTypes();
        List<DictItem> permissionTypes = dictDao.getPermissionTypes();
        interfaceListTable.setDictData(resourceTypes, permissionTypes);
        interfaceListTable.refresh();
    }
    
    // ===================== 模板 CRUD =====================
    
    private void createNewTemplate() {
        if (templateDao == null) {
            return;
        }
        TemplateEditDialog dialog = new TemplateEditDialog(false, null);
        if (!dialog.showAndGet()) {
            return;
        }
        SqlTemplate t = dialog.buildTemplate();
        if (templateDao.nameExists(t.getTemplateName(), null)) {
            Messages.showWarningDialog(getContentPanel(), "模板名称已存在：" + t.getTemplateName(), "名称冲突");
            return;
        }
        templateDao.saveTemplate(t);
        loadTemplates();
        templateCombo.setSelectedItem(t.getTemplateName());
        showToast("模板创建成功");
    }
    
    private void editCurrentTemplate() {
        if (currentTemplate == null || templateDao == null) {
            return;
        }
        TemplateEditDialog dialog = new TemplateEditDialog(true, currentTemplate);
        if (!dialog.showAndGet()) {
            return;
        }
        SqlTemplate updated = dialog.buildTemplate();
        templateDao.updateTemplate(updated);
        currentTemplate = updated;
        interfaceListTable.setCurrentTemplate(updated);
        loadTemplates();
        templateCombo.setSelectedItem(updated.getTemplateName());
        showToast("模板编辑成功");
    }
    
    private void deleteCurrentTemplate() {
        if (currentTemplate == null || templateDao == null) {
            return;
        }
        int confirm = Messages.showYesNoDialog(getContentPanel(), "确定删除模板【" + currentTemplate.getTemplateName() + "】？", "确认删除",
                Messages.getQuestionIcon());
        if (confirm != Messages.YES) {
            return;
        }
        templateDao.deleteTemplate(currentTemplate.getId());
        currentTemplate = null;
        loadTemplates();
    }
    
    private void exportCurrentTemplate() {
        if (currentTemplate == null || templateDao == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(currentTemplate.getTemplateName() + "_template.json"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));
        if (chooser.showSaveDialog(getContentPanel()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(chooser.getSelectedFile()), StandardCharsets.UTF_8)) {
            w.write(templateDao.exportTemplateAsJson(currentTemplate));
            showToast("模板导出成功");
        } catch (IOException e) {
            Messages.showErrorDialog(getContentPanel(), "导出失败：" + e.getMessage(), "导出失败");
        }
    }
    
    private void importTemplate() {
        if (templateDao == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));
        if (chooser.showOpenDialog(getContentPanel()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file.length() > 1024 * 1024) {
            Messages.showWarningDialog(getContentPanel(), "文件过大，请选择1MB以内的JSON文件", "文件过大");
            return;
        }
        try {
            String json = java.nio.file.Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String importedName = templateDao.importTemplateFromJson(json);
            loadTemplates();
            templateCombo.setSelectedItem(importedName);
            showToast("模板导入成功：" + importedName);
        } catch (RuntimeException e) {
            Messages.showErrorDialog(getContentPanel(), e.getMessage(), "导入失败");
        } catch (IOException e) {
            Messages.showErrorDialog(getContentPanel(), "读取文件失败：" + e.getMessage(), "导入失败");
        }
    }
    
    // ===================== SQL 生成 / 复制 / 导出 =====================
    
    private void generateSql() {
        if (currentTemplate == null) {
            Messages.showWarningDialog(getContentPanel(), "请先选择一个模板", "提示");
            return;
        }
        
        // 校验必填字段
        List<String> emptyErrors = new java.util.ArrayList<>();
        for (InterfaceInfo info : interfaces) {
            if (!info.isSelected()) {
                continue;
            }
            List<String> missing = new java.util.ArrayList<>();
            if (info.getRoutePath() == null || info.getRoutePath().isBlank()) {
                missing.add("路由地址");
            }
            if (info.getResourceName() == null || info.getResourceName().isBlank()) {
                missing.add("资源名称");
            }
            if (info.getResourceCode() == null || info.getResourceCode().isBlank()) {
                missing.add("资源编码");
            }
            if (info.getResourceType() == null) {
                missing.add("资源类型");
            }
            if (info.getPermissionType() == null) {
                missing.add("权限类型");
            }
            if (!missing.isEmpty()) {
                String route = (info.getRoutePath() == null || info.getRoutePath().isBlank()) ? "(路由为空)" : info.getRoutePath();
                emptyErrors.add(route + " → 缺少：" + String.join("、", missing));
            }
        }
        if (!emptyErrors.isEmpty()) {
            Messages.showWarningDialog(getContentPanel(), "以下接口存在必填字段未填写，请补全后再生成：\n" + String.join("\n", emptyErrors),
                    "校验失败");
            return;
        }
        
        // 校验资源编码唯一性
        interfaceListTable.getTableModel().checkDuplicateCodes();
        if (interfaces.stream().anyMatch(i -> i.isSelected() && i.isDuplicateCode())) {
            Messages.showWarningDialog(getContentPanel(), "存在重复的资源编码，请修正后再生成", "校验失败");
            return;
        }
        
        SqlGenerator.GenerateResult result = SqlGenerator.generate(interfaces, currentTemplate);
        String sql = result.getSql();
        sqlPreviewArea.setText(sql);
        sqlPreviewArea.setCaretPosition(0);
        
        boolean hasContent = !sql.isBlank();
        copyBtn.setEnabled(hasContent);
        exportBtn.setEnabled(hasContent);
        
        if (result.hasWarnings()) {
            warningLabel.setText("<html><b>⚠ 警告：</b>" + String.join("；", result.getWarnings()) + "</html>");
            warningLabel.setVisible(true);
        } else {
            warningLabel.setVisible(false);
        }
    }
    
    private void copySql() {
        String sql = sqlPreviewArea.getText();
        if (sql.isBlank()) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sql), null);
        showToast("SQL 已复制到剪贴板");
    }
    
    private void exportSqlFile() {
        String sql = sqlPreviewArea.getText();
        if (sql.isBlank()) {
            return;
        }
        String defaultName = "permission_sql_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".sql";
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(defaultName));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SQL files", "sql"));
        if (chooser.showSaveDialog(getContentPanel()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(chooser.getSelectedFile()), StandardCharsets.UTF_8)) {
            w.write(sql);
            showToast("SQL 文件导出成功");
        } catch (IOException e) {
            Messages.showErrorDialog(getContentPanel(), "导出失败：" + e.getMessage(), "导出失败");
        }
    }
    
    // ===================== 通用工具 =====================
    
    private void showToast(String message) {
        Messages.showInfoMessage(getContentPanel(), message, "提示");
        //        if (project != null) {
        //            try {
        //                NotificationGroupManager.getInstance().getNotificationGroup("Api2Sql")
        //                        .createNotification(message, NotificationType.INFORMATION).notify(project);
        //            } catch (Exception ignored) {
        //                Messages.showInfoMessage(getContentPanel(), message, "提示");
        //            }
        //        }
    }
    
    @Override
    protected void doOKAction() {
        close(OK_EXIT_CODE);
    }
    
    @NotNull
    @Override
    protected Action[] createActions() {
        return new Action[] {getOKAction()};
    }
}
