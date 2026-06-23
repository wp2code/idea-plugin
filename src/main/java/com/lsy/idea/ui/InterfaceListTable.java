package com.lsy.idea.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.lsy.idea.http.HttpCodeFetcher;
import com.lsy.idea.model.DictItem;
import com.lsy.idea.model.InterfaceInfo;
import com.lsy.idea.model.MethodTypeEnum;
import com.lsy.idea.model.SqlTemplate;
import com.lsy.idea.ui.renderer.CodeModeCellEditor;
import com.lsy.idea.ui.renderer.CodeModeCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.table.TableColumn;

/**
 * 接口列表组件（封装 JBTable） 包含：全选/取消全选、资源编码重复标红、资源类型/权限类型下拉框、双模式编码编辑器
 */
public class InterfaceListTable extends JPanel {
    
    private final JBTable table;
    
    private final InterfaceTableModel tableModel;
    
    private final List<InterfaceInfo> interfaces;
    
    /**
     * 当前模板引用容器（共享给 CodeModeCellEditor）
     */
    private final SqlTemplate[] currentTemplateHolder = new SqlTemplate[1];
    
    /**
     * 刷新资源编码按钮（仅HTTP模式时显示）
     */
    private final JButton refreshCodeBtn;
    
    public InterfaceListTable(List<InterfaceInfo> interfaces) {
        this.interfaces = interfaces;
        setLayout(new BorderLayout(4, 4));
        
        tableModel = new InterfaceTableModel(interfaces);
        table = new JBTable(tableModel);
        configureTable();
        
        // ---- 全选 / 取消全选 + 刷新资源编码 ----
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton selectAllBtn = new JButton("全选");
        JButton deselectAllBtn = new JButton("取消全选");
        selectAllBtn.addActionListener(e -> setAllSelected(true));
        deselectAllBtn.addActionListener(e -> setAllSelected(false));
        topBar.add(selectAllBtn);
        topBar.add(deselectAllBtn);
        
        refreshCodeBtn = new JButton("刷新获取资源编码");
        refreshCodeBtn.setVisible(false);
        refreshCodeBtn.addActionListener(e -> refreshHttpCodes());
        topBar.add(refreshCodeBtn);
        
        JBScrollPane scrollPane = new JBScrollPane(table);
        scrollPane.setMinimumSize(new Dimension(800, 200));
        
        add(topBar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }
    
    private void configureTable() {
        table.setRowHeight(26);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        
        // 列宽设置
        setColumnWidth(InterfaceTableModel.COL_SELECTED, 40);
        setColumnWidth(InterfaceTableModel.COL_ROUTE, 160);
        setColumnWidth(InterfaceTableModel.COL_RESOURCE_NAME, 110);
        setColumnWidth(InterfaceTableModel.COL_I18N_CODE, 120);
        setColumnWidth(InterfaceTableModel.COL_RESOURCE_CODE, 120);
        setColumnWidth(InterfaceTableModel.COL_RESOURCE_TYPE, 80);
        setColumnWidth(InterfaceTableModel.COL_PERMISSION_TYPE, 80);
        setColumnWidth(InterfaceTableModel.COL_REMARK, 100);
        
        // 资源编码列：自定义渲染器 + 编辑器
        TableColumn codeCol = table.getColumnModel().getColumn(InterfaceTableModel.COL_RESOURCE_CODE);
        codeCol.setCellRenderer(new CodeModeCellRenderer(interfaces));
        codeCol.setCellEditor(new CodeModeCellEditor(interfaces, currentTemplateHolder));
        
        // 资源类型列右键菜单（切换手动/HTTP 编码模式）
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }
            
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }
        });
    }
    
    /**
     * 为资源类型 / 权限类型列设置字典下拉框，并为未设置过类型的接口填充默认字典选项
     * <p>
     * - 资源类型默认：名称包含“接口”的首个字典项，找不到则取 sort 升序第一项 - 权限类型默认：名称包含“查看”的首个字典项，找不到则取 sort 升序第一项
     * </p>
     */
    public void setDictData(List<DictItem> resourceTypes, List<DictItem> permissionTypes) {
        tableModel.setResourceTypes(resourceTypes);
        tableModel.setPermissionTypes(permissionTypes);
        
        // 资源类型默认字典项：取列表第一项
        Integer defaultResourceType = (resourceTypes != null && !resourceTypes.isEmpty()) ? resourceTypes.getFirst().getTypeCode() : null;
        // 权限类型默认字典项：取列表第一项
        Integer defaultPermissionType = (permissionTypes != null && !permissionTypes.isEmpty()) ? permissionTypes.getFirst().getTypeCode() : null;
        final List<Integer> permissionTypeCodes =
                permissionTypes == null ? new ArrayList<>() : permissionTypes.stream().map(DictItem::getTypeCode).toList();
        // 对资源类型和权限类型均为 null 的接口填充默认字典选项
        for (InterfaceInfo info : interfaces) {
            final String requestMethod = info.getRequestMethod();
            MethodTypeEnum methodType = MethodTypeEnum.getInstance(requestMethod);
            if (MethodTypeEnum.DELETE == methodType && permissionTypeCodes.contains(3)) {
                defaultPermissionType = 3;
            } else if (MethodTypeEnum.PUT == methodType && permissionTypeCodes.contains(2)) {
                defaultPermissionType = 2;
            }
            info.initDefaultResourceType(defaultResourceType);
            info.initDefaultPermissionType(defaultPermissionType);
        }
        
        // 资源类型列下拉
        setDictComboBox(InterfaceTableModel.COL_RESOURCE_TYPE, resourceTypes);
        // 权限类型列下拉
        setDictComboBox(InterfaceTableModel.COL_PERMISSION_TYPE, permissionTypes);
        tableModel.fireTableDataChanged();
    }
    
    private void setDictComboBox(int col, List<DictItem> items) {
        if (items == null) {
            return;
        }
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.addItem("");
        for (DictItem item : items) {
            comboBox.addItem(item.getTypeName());
        }
        table.getColumnModel().getColumn(col).setCellEditor(new DefaultCellEditor(comboBox));
    }
    
    private void setAllSelected(boolean selected) {
        for (InterfaceInfo info : interfaces) {
            info.setSelected(selected);
        }
        tableModel.fireTableDataChanged();
    }
    
    private void showContextMenu(java.awt.event.MouseEvent e) {
        int row = table.rowAtPoint(e.getPoint());
        int col = table.columnAtPoint(e.getPoint());
        if (row < 0 || row >= interfaces.size()) {
            return;
        }
        
        InterfaceInfo info = interfaces.get(row);
        JPopupMenu menu = new JPopupMenu();
        
        if (col == InterfaceTableModel.COL_RESOURCE_CODE) {
            JMenuItem manualItem = new JMenuItem("切换为手动模式");
            JMenuItem httpItem = new JMenuItem("切换为HTTP模式");
            manualItem.addActionListener(ev -> {
                info.setCodeMode(0);
                tableModel.fireTableRowsUpdated(row, row);
            });
            httpItem.addActionListener(ev -> {
                info.setCodeMode(1);
                tableModel.fireTableRowsUpdated(row, row);
            });
            menu.add(manualItem);
            menu.add(httpItem);
            menu.show(table, e.getX(), e.getY());
        }
    }
    
    private void setColumnWidth(int col, int width) {
        TableColumn column = table.getColumnModel().getColumn(col);
        column.setPreferredWidth(width);
        column.setMinWidth(width / 2);
    }
    
    public InterfaceTableModel getTableModel() {
        return tableModel;
    }
    
    public JTable getTable() {
        return table;
    }
    
    /**
     * 更新当前模板引用（模板切换时调用，用于 HTTP 拉取时携带正确的请求头） 同时控制「刷新获取资源编码」按钮的显隐（仅 HTTP 模式时显示）
     */
    public void setCurrentTemplate(SqlTemplate template) {
        currentTemplateHolder[0] = template;
        if (refreshCodeBtn != null) {
            boolean isHttpMode = template != null && template.getDefaultCodeMode() == 1;
            refreshCodeBtn.setVisible(isHttpMode);
        }
    }
    
    /**
     * 刷新选中接口的资源编码：对所有勾选且 codeMode=1 的接口逐条独立发起 HTTP 请求 使用 SwingWorker 异步执行，避免阻塞 UI
     */
    private void refreshHttpCodes() {
        int confirm = Messages.showYesNoDialog(this, "确定刷新资源编码？", "确认刷新", Messages.getQuestionIcon());
        if (confirm != Messages.YES) {
            return;
        }
        SqlTemplate tpl = currentTemplateHolder[0];
        // 收集需要刷新的接口（已勾选 且 编码模式为HTTP）
        List<InterfaceInfo> toRefresh = new ArrayList<>();
        for (InterfaceInfo info : interfaces) {
            if (info.isSelected() && info.getCodeMode() == 1) {
                toRefresh.add(info);
            }
        }
        
        if (toRefresh.isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有需要刷新的接口（请确认已勾选且编码模式为 HTTP 的接口）", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 禁用按钮，防止重复点击
        refreshCodeBtn.setEnabled(false);
        refreshCodeBtn.setText("刷新中...");
        
        java.util.Map<String, String> headers = tpl != null ? tpl.getHttpHeaders() : null;
        
        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() {
                List<String> errors = new ArrayList<>();
                for (InterfaceInfo info : toRefresh) {
                    String url = info.getHttpCodeApi();
                    if (url == null || url.isBlank()) {
                        errors.add(info.getRoutePath() + " → HTTP拉取地址为空，已跳过");
                        continue;
                    }
                    try {
                        String code = HttpCodeFetcher.fetchCode(url, headers, false);
                        info.setResourceCode(code);
                    } catch (HttpCodeFetcher.HttpFetchException ex) {
                        errors.add(info.getRoutePath() + " → " + ex.getMessage());
                    }
                }
                return errors;
            }
            
            @Override
            protected void done() {
                refreshCodeBtn.setEnabled(true);
                refreshCodeBtn.setText("刷新获取资源编码");
                tableModel.fireTableDataChanged();
                
                try {
                    List<String> errors = get();
                    if (!errors.isEmpty()) {
                        String msg = "以下接口资源编码刷新失败：\n" + String.join("\n", errors);
                        JOptionPane.showMessageDialog(InterfaceListTable.this, msg, "部分失败", JOptionPane.WARNING_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(InterfaceListTable.this, "共刷新 " + toRefresh.size() + " 条资源编码成功", "刷新完成",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(InterfaceListTable.this, "刷新异常：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
    
    /**
     * 刷新表格显示
     */
    public void refresh() {
        tableModel.fireTableDataChanged();
    }
}
