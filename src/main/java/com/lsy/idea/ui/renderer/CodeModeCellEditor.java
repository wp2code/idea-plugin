package com.lsy.idea.ui.renderer;

import com.intellij.openapi.ui.Messages;
import com.lsy.idea.http.HttpCodeFetcher;
import com.lsy.idea.model.InterfaceInfo;
import com.lsy.idea.model.SqlTemplate;
import java.awt.*;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import javax.swing.*;

/**
 * 资源编码列编辑器 - 手动模式（codeMode=0）：普通 JTextField - HTTP模式（codeMode=1）：面板包含 JTextField + "拉取" 按鈕，拉取时携带模板请求头
 */
public class CodeModeCellEditor extends DefaultCellEditor {
    
    private final List<InterfaceInfo> interfaces;
    
    private final JTextField textField;
    
    private final JPanel httpPanel;
    
    private final JTextField httpValueField;
    
    private final JButton fetchBtn;
    
    /**
     * 当前模板引用（用于获取请求头）
     */
    private final SqlTemplate[] currentTemplateHolder;
    
    private int currentRow = -1;
    
    private JTable currentTable;
    
    /**
     * @param interfaces            接口列表
     * @param currentTemplateHolder 长度为1的数组，元紤0为当前模板引用（可为 null）
     */
    public CodeModeCellEditor(List<InterfaceInfo> interfaces, SqlTemplate[] currentTemplateHolder) {
        super(new JTextField());
        this.interfaces = interfaces;
        this.currentTemplateHolder = currentTemplateHolder;
        this.textField = (JTextField) getComponent();
        
        // HTTP 模式面板
        httpPanel = new JPanel(new BorderLayout(2, 0));
        httpValueField = new JTextField();
        fetchBtn = new JButton("拉取");
        fetchBtn.setMargin(new Insets(0, 4, 0, 4));
        httpPanel.add(httpValueField, BorderLayout.CENTER);
        httpPanel.add(fetchBtn, BorderLayout.EAST);
        
        fetchBtn.addActionListener(e -> doFetchCode());
    }
    
    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        
        this.currentRow = row;
        this.currentTable = table;
        String strValue = value != null ? value.toString() : "";
        
        if (row >= 0 && row < interfaces.size()) {
            InterfaceInfo info = interfaces.get(row);
            if (info.getCodeMode() == 1) {
                // HTTP 模式
                httpValueField.setText(strValue);
                return httpPanel;
            }
        }
        
        // 手动模式
        textField.setText(strValue);
        return textField;
    }
    
    @Override
    public Object getCellEditorValue() {
        if (currentRow >= 0 && currentRow < interfaces.size()) {
            if (interfaces.get(currentRow).getCodeMode() == 1) {
                return httpValueField.getText();
            }
        }
        return textField.getText();
    }
    
    @Override
    public boolean isCellEditable(EventObject event) {
        return true;
    }
    
    private void doFetchCode() {
        if (currentRow < 0 || currentRow >= interfaces.size()) {
            return;
        }
        InterfaceInfo info = interfaces.get(currentRow);
        String url = info.getHttpCodeApi();
        if (url == null || url.isBlank()) {
            Messages.showWarningDialog(fetchBtn, "HTTP 拉取地址未配置，请先在模板或接口行中填写地址", "提示");
            return;
        }
        
        // 获取当前模板请求头
        Map<String, String> headers = null;
        if (currentTemplateHolder != null && currentTemplateHolder[0] != null) {
            Map<String, String> tHeaders = currentTemplateHolder[0].getHttpHeaders();
            if (tHeaders != null && !tHeaders.isEmpty()) {
                headers = tHeaders;
            }
        }
        
        fetchBtn.setEnabled(false);
        fetchBtn.setText("拉取中...");
        
        final Map<String, String> finalHeaders = headers;
        SwingUtilities.invokeLater(() -> {
            try {
                String code = HttpCodeFetcher.fetchCode(url, finalHeaders, false);
                httpValueField.setText(code);
                info.setResourceCode(code);
                if (currentTable != null) {
                    currentTable.repaint();
                }
            } catch (HttpCodeFetcher.HttpFetchException ex) {
                Messages.showErrorDialog(fetchBtn, ex.getMessage(), "编码拉取失败");
            } finally {
                fetchBtn.setEnabled(true);
                fetchBtn.setText("拉取");
            }
        });
    }
}
