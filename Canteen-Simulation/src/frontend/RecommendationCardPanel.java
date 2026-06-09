package frontend;

import backend.optimize.SimRunResult;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Locale;

/**
 * Recommended scheme card. Values are fed from real optimizer output.
 */
public class RecommendationCardPanel extends JPanel {
    private final JLabel title = new JLabel("系统推荐方案");
    private final JLabel windowValue = new JLabel("待寻优");
    private final JLabel tableValue = new JLabel("待寻优");
    private final JLabel completionValue = new JLabel("待寻优");
    private final JLabel profitValue = new JLabel("待寻优");
    private final JLabel queueWaitValue = new JLabel("待寻优");
    private final JLabel seatWaitValue = new JLabel("待寻优");
    private final JTextArea reasonValue = new JTextArea("请点击启动寻优生成推荐方案");

    public RecommendationCardPanel() {
        super(new BorderLayout(0, 8));
        setPreferredSize(new Dimension(400, 300));
        setMinimumSize(new Dimension(380, 284));
        setBackground(ColorTheme.BG_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        title.setForeground(ColorTheme.TEXT_PRIMARY);
        title.setFont(ColorTheme.font(Font.BOLD, 15));
        add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(3, 2, 8, 8));
        grid.setOpaque(false);
        grid.add(createMetric("建议开放窗口", windowValue));
        grid.add(createMetric("建议设置餐桌", tableValue));
        grid.add(createMetric("预计完成率", completionValue));
        grid.add(createMetric("预计净收益", profitValue));
        grid.add(createMetric("平均排队等待", queueWaitValue));
        grid.add(createMetric("平均等座等待", seatWaitValue));
        add(grid, BorderLayout.CENTER);

        reasonValue.setForeground(ColorTheme.TEXT_SECONDARY);
        reasonValue.setFont(ColorTheme.font(Font.PLAIN, 12));
        reasonValue.setOpaque(false);
        reasonValue.setEditable(false);
        reasonValue.setFocusable(false);
        reasonValue.setLineWrap(true);
        reasonValue.setWrapStyleWord(false);
        reasonValue.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        reasonValue.setPreferredSize(new Dimension(0, 82));
        add(reasonValue, BorderLayout.SOUTH);
    }

    public void updateResult(SimRunResult result) {
        updateResult(null, result);
    }

    public void updateResult(SimRunResult current, SimRunResult result) {
        if (result == null) {
            showMessage("请启动寻优生成推荐方案。");
            return;
        }
        setMetricText(windowValue, String.valueOf(result.windowCount));
        setMetricText(tableValue, String.valueOf(result.tableCount));
        setMetricText(completionValue, String.format(Locale.US, "%.1f%%", result.finishRate * 100.0));
        setMetricText(profitValue, String.format(Locale.US, "%.0f 元", result.netProfit));
        setMetricText(queueWaitValue, String.format(Locale.US, "%.1f 分", result.avgWaitTimeMinutes));
        setMetricText(seatWaitValue, String.format(Locale.US, "%.1f 分", result.avgSeatWaitTimeMinutes));
        String reason = buildReason(current, result);
        reasonValue.setText(reason);
        reasonValue.setCaretPosition(0);
    }

    public void showMessage(String message) {
        setMetricText(windowValue, "待更新");
        setMetricText(tableValue, "待更新");
        setMetricText(completionValue, "--");
        setMetricText(profitValue, "--");
        setMetricText(queueWaitValue, "--");
        setMetricText(seatWaitValue, "--");
        reasonValue.setText(message);
        reasonValue.setCaretPosition(0);
    }

    private String buildReason(SimRunResult current, SimRunResult recommended) {
        if (current == null) {
            return "请先运行当前方案仿真，再生成对比说明。";
        }
        double completionDelta = recommended.finishRate - current.finishRate;
        int abandonDelta = recommended.abandonedStudents - current.abandonedStudents;
        double queueDelta = recommended.avgWaitTimeMinutes - current.avgWaitTimeMinutes;
        double seatDelta = recommended.avgSeatWaitTimeMinutes - current.avgSeatWaitTimeMinutes;
        double profitDelta = recommended.netProfit - current.netProfit;
        return String.format(Locale.US,
                "系统建议开放 %d 个窗口、设置 %d 张餐桌。相比当前 %d 个窗口、%d 张餐桌，预计完成率由 %.1f%% %s %.1f%%，放弃人数由 %d 人%s为 %d 人，平均排队等待由 %.1f 分%s至 %.1f 分，平均等座等待由 %.1f 分%s至 %.1f 分。%s预计净收益%s %.0f 元。",
                recommended.windowCount,
                recommended.tableCount,
                current.windowCount,
                current.tableCount,
                current.finishRate * 100.0,
                completionDelta >= 0 ? "提升至" : "下降至",
                recommended.finishRate * 100.0,
                current.abandonedStudents,
                abandonDelta <= 0 ? "减少" : "增加",
                recommended.abandonedStudents,
                current.avgWaitTimeMinutes,
                queueDelta <= 0 ? "下降" : "上升",
                recommended.avgWaitTimeMinutes,
                current.avgSeatWaitTimeMinutes,
                seatDelta <= 0 ? "下降" : "上升",
                recommended.avgSeatWaitTimeMinutes,
                resourceCostHint(current, recommended),
                profitDelta >= 0 ? "增加" : "减少",
                Math.abs(profitDelta));
    }

    private String resourceCostHint(SimRunResult current, SimRunResult recommended) {
        if (recommended.windowCount > current.windowCount || recommended.tableCount > current.tableCount) {
            return "虽然资源成本有所增加，但由于完成就餐人数和收入结构变化，";
        }
        if (recommended.windowCount < current.windowCount || recommended.tableCount < current.tableCount) {
            return "在减少部分资源投入的同时，";
        }
        return "";
    }

    private JPanel createMetric(String label, JLabel value) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        JLabel labelView = new JLabel(label);
        labelView.setForeground(ColorTheme.TEXT_SECONDARY);
        labelView.setFont(ColorTheme.font(Font.PLAIN, 12));
        value.setForeground(ColorTheme.ACCENT_CYAN);
        value.setFont(ColorTheme.font(Font.BOLD, 20));
        panel.add(labelView, BorderLayout.NORTH);
        panel.add(value, BorderLayout.CENTER);
        return panel;
    }

    private void setMetricText(JLabel label, String text) {
        label.setText(text);
        label.setToolTipText(text);
        int size = text.length() >= 9 ? 17 : 20;
        label.setFont(ColorTheme.font(Font.BOLD, size));
    }
}
