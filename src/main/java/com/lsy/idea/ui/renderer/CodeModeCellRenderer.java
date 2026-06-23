package com.lsy.idea.ui.renderer;

import com.lsy.idea.model.InterfaceInfo;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * 资源编码列渲染器
 * - 手动模式（codeMode=0）：显示文本输入框样式（白色背景）
 * - HTTP模式（codeMode=1）：显示带 [HTTP] 前缀标签
 * - 重复编码：红色边框提示
 */
public class CodeModeCellRenderer extends DefaultTableCellRenderer {

    private final List<InterfaceInfo> interfaces;

    public CodeModeCellRenderer(List<InterfaceInfo> interfaces) {
        this.interfaces = interfaces;
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

        Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (row >= 0 && row < interfaces.size()) {
            InterfaceInfo info = interfaces.get(row);
            String displayText = value != null ? value.toString() : "";
            if (info.getCodeMode() == 1) {
                setText("[HTTP] " + displayText);
            } else {
                setText(displayText);
            }
            // 重复编码标红
            if (!isSelected && info.isDuplicateCode()) {
                comp.setBackground(new Color(255, 200, 200));
                setBorder(BorderFactory.createLineBorder(Color.RED));
                setToolTipText("资源编码重复");
            } else if (!isSelected) {
                comp.setBackground(table.getBackground());
                setBorder(null);
                setToolTipText(null);
            }
        }
        return comp;
    }
}
