package com.lsy.idea.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.lsy.idea.generator.SqlGenerator;
import com.lsy.idea.model.SqlTemplate;
import com.lsy.idea.model.VariableInfo;
import com.lsy.idea.util.CommUtil;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.Nullable;

/**
 * 模板编辑弹窗（新建 / 编辑共用） 支持编辑：模板名称、模板内容、创建人 ID、更新人 ID、HTTP拉取地址、资源编码模式、HTTP请求头、扩展字段
 */
public class TemplateEditDialog extends DialogWrapper {
    
    private final boolean isEdit;
    
    private final SqlTemplate template;
    
    private JTextField nameField;
    
    private JTextArea contentArea;
    
    private JTextField creatorIdField;
    
    private JTextField updaterIdField;
    
    private JTextField httpCodeApiField;
    
    private JButton testHttpBtn;
    
    private JPanel httpApiRow;
    
    private JRadioButton modeHttpBtn;
    
    private HttpHeadersPanel httpHeadersPanel;
    
    /**
     * @param isEdit   true=编辑模式（名称只读），false=新建模式
     * @param template 编辑时传入现有模板，新建时传入 null
     */
    public TemplateEditDialog(boolean isEdit, @Nullable SqlTemplate template) {
        super(true);
        this.isEdit = isEdit;
        this.template = template;
        setTitle(isEdit ? "编辑模板" : "新建模板");
        setOKButtonText(isEdit ? "保存" : "创建");
        setCancelButtonText("取消");
        init();
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        
        // ---- 基本字段区 ----
        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        int row = 0;
        
        // 模板名称
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        fieldsPanel.add(new JLabel("模板名称："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        nameField = new JTextField(30);
        fieldsPanel.add(nameField, gbc);
        // 创建人ID + 更新人ID（同一行，内嵌子面板与nameField右对齐）
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        fieldsPanel.add(new JLabel("创建人ID："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        creatorIdField = new JTextField();
        updaterIdField = new JTextField();
        JPanel optUserRow = new JPanel(new GridBagLayout());
        GridBagConstraints subGbc = new GridBagConstraints();
        subGbc.insets = new Insets(0, 0, 0, 0);
        subGbc.fill = GridBagConstraints.HORIZONTAL;
        subGbc.gridx = 0;
        subGbc.weightx = 1;
        subGbc.gridy = 0;
        optUserRow.add(creatorIdField, subGbc);
        subGbc.gridx = 1;
        subGbc.weightx = 0;
        subGbc.gridy = 0;
        optUserRow.add(new JLabel("  更新人ID："), subGbc);
        subGbc.gridx = 2;
        subGbc.weightx = 1;
        subGbc.gridy = 0;
        optUserRow.add(updaterIdField, subGbc);
        fieldsPanel.add(optUserRow, gbc);
        // 资源编码模式
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        fieldsPanel.add(new JLabel("资源编码模式："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        final JRadioButton modeManualBtn = new JRadioButton("手动录入");
        modeHttpBtn = new JRadioButton("HTTP获取");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(modeManualBtn);
        modeGroup.add(modeHttpBtn);
        modeManualBtn.setSelected(true);
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        modePanel.add(modeManualBtn);
        modePanel.add(modeHttpBtn);
        fieldsPanel.add(modePanel, gbc);
        
        // ---- HTTP 配置区（地址 + 测试按钮 + 请求头，归组）----
        httpCodeApiField = new JTextField();
        testHttpBtn = new JButton("测试");
        httpApiRow = new JPanel(new BorderLayout(4, 0));
        httpApiRow.add(new JLabel("地址："), BorderLayout.WEST);
        httpApiRow.add(httpCodeApiField, BorderLayout.CENTER);
        httpApiRow.add(testHttpBtn, BorderLayout.EAST);
        httpHeadersPanel = new HttpHeadersPanel();
        JPanel httpConfigGroup = new JPanel(new BorderLayout(0, 4));
        httpConfigGroup.setBorder(BorderFactory.createTitledBorder("HTTP 配置"));
        httpConfigGroup.add(httpApiRow, BorderLayout.NORTH);
        httpConfigGroup.add(httpHeadersPanel, BorderLayout.CENTER);
        testHttpBtn.addActionListener(
                e -> CommUtil.doTestHttpRequest(httpCodeApiField.getText().trim(), httpHeadersPanel.getHeaders(), testHttpBtn, getContentPanel()));
        row++;
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        fieldsPanel.add(httpConfigGroup, gbc);
        
        // ---- 模板内容 ----
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        fieldsPanel.add(new JLabel("模板内容："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        contentArea = new JTextArea(10, 40);
        contentArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        contentArea.setLineWrap(false);
        JBScrollPane contentScroll = new JBScrollPane(contentArea);
        fieldsPanel.add(contentScroll, gbc);
        row++;
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        gbc.weighty = 1;
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
                JScrollPane scrollPane = new JScrollPane(new JTable(tableModel));
                scrollPane.setPreferredSize(new Dimension(500, 300));
                JOptionPane.showMessageDialog(getContentPanel(), scrollPane, "变量说明", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        fieldsPanel.add(hint, gbc);
        // ---- 填充数据 ----
        if (template != null) {
            nameField.setText(template.getTemplateName());
            contentArea.setText(template.getTemplateContent() != null ? SqlGenerator.compressSql(template.getTemplateContent()) : "");
            creatorIdField.setText(template.getCreatorIdValue());
            updaterIdField.setText(template.getUpdaterIdValue());
            httpCodeApiField.setText(template.getHttpCodeApi());
            modeManualBtn.setSelected(template.getDefaultCodeMode() == 0);
            modeHttpBtn.setSelected(template.getDefaultCodeMode() == 1);
            httpHeadersPanel.populateFromTemplate(template);
        }
        
        // 模式切换监听
        modeManualBtn.addActionListener(e -> updateHttpModeVisibility(false));
        modeHttpBtn.addActionListener(e -> updateHttpModeVisibility(true));
        updateHttpModeVisibility(modeHttpBtn.isSelected());
        
        if (isEdit) {
            nameField.setEditable(false);
        }
        panel.add(fieldsPanel, BorderLayout.CENTER);
        return panel;
    }
    
    /**
     * 根据模式控制HTTP配置区的显示/隐藏
     */
    private void updateHttpModeVisibility(boolean httpMode) {
        Container httpConfigGroup = httpApiRow.getParent();
        if (httpConfigGroup != null) {
            httpConfigGroup.setVisible(httpMode);
        }
        // 逐级刷新布局
        Container c = httpConfigGroup != null ? httpConfigGroup.getParent() : getContentPanel();
        while (c != null) {
            c.revalidate();
            c.repaint();
            c = c.getParent();
        }
        
        // 让顶层窗口重新 pack，自动扩展/收缩高度以适应内容
        Window window = SwingUtilities.getWindowAncestor(httpConfigGroup != null ? httpConfigGroup : getContentPanel());
        if (window != null) {
            // 记住当前宽度和位置，pack 后恢复
            int curWidth = window.getWidth();
            Point curLoc = window.getLocation();
            window.pack();
            Dimension packed = window.getSize();
            // 限制最大高度不超过屏幕可用高度
            Rectangle screenBounds = window.getGraphicsConfiguration().getBounds();
            int maxH = screenBounds.height - 80;
            int finalH = Math.min(packed.height, maxH);
            int finalW = Math.max(curWidth, packed.width);
            window.setSize(finalW, finalH);
            window.setLocation(curLoc);
        }
    }
    
    @Override
    protected void doOKAction() {
        if (validateInput()) {
            super.doOKAction();
        }
    }
    
    private boolean validateInput() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            Messages.showWarningDialog(getContentPanel(), "模板名称不能为空", "校验失败");
            return false;
        }
        String content = contentArea.getText().trim();
        if (content.isEmpty()) {
            Messages.showWarningDialog(getContentPanel(), "模板内容不能为空", "校验失败");
            return false;
        }
        return true;
    }
    
    /**
     * 从表单构建 SqlTemplate 对象
     */
    public SqlTemplate buildTemplate() {
        SqlTemplate t = (template != null) ? template : new SqlTemplate();
        t.setTemplateName(nameField.getText().trim());
        t.setTemplateContent(contentArea.getText().trim());
        t.setCreatorIdValue(creatorIdField.getText().trim());
        t.setUpdaterIdValue(updaterIdField.getText().trim());
        t.setHttpCodeApi(httpCodeApiField.getText().trim());
        t.setDefaultCodeMode(modeHttpBtn.isSelected() ? 1 : 0);
        httpHeadersPanel.applyToTemplate(t);
        return t;
    }
}
