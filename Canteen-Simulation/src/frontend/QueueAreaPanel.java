package frontend;

import backend.dto.PressureLevel;
import backend.dto.WindowStat;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class QueueAreaPanel extends JPanel {

    private JProgressBar[] queueBars;
    private JLabel[] detailLabels;
    private final JLabel titleLabel = new JLabel("排队区");

    public QueueAreaPanel(int initialWindowCount) {
        this.setLayout(new BorderLayout(0, 8));
        this.setBackground(frontend.ColorTheme.BG_CARD);

        this.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        titleLabel.setFont(ColorTheme.font(Font.BOLD, 15));
        titleLabel.setForeground(frontend.ColorTheme.TEXT_PRIMARY);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 0));

        updateWindowCount(initialWindowCount);
    }

    public void updateWindowCount(int windowCount) {
        this.removeAll();

        this.add(titleLabel, BorderLayout.NORTH);

        queueBars = new JProgressBar[windowCount];
        detailLabels = new JLabel[windowCount];

        JPanel listPanel = new JPanel(new GridLayout(windowCount, 1, 0, 14));
        listPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        listPanel.setOpaque(false);

        for (int i = 0; i < windowCount; i++) {
            JPanel singleWindowPanel = new JPanel(new BorderLayout(10, 4));
            singleWindowPanel.setPreferredSize(new Dimension(0, 58));
            singleWindowPanel.setOpaque(false);

            JLabel nameLabel = new JLabel("窗口 " + (i + 1));
            nameLabel.setPreferredSize(new Dimension(64, 30));
            nameLabel.setFont(ColorTheme.font(Font.BOLD, 13));
            nameLabel.setForeground(frontend.ColorTheme.TEXT_PRIMARY);
            singleWindowPanel.add(nameLabel, BorderLayout.WEST);

            JProgressBar progressBar = new JProgressBar(0, 30);
            progressBar.setValue(0);
            progressBar.setStringPainted(true);
            progressBar.setString("排队中: 0 人");
            progressBar.setForeground(frontend.ColorTheme.ACCENT_GREEN);
            progressBar.setBackground(frontend.ColorTheme.QUEUE_TRACK);
            progressBar.setBorderPainted(false);
            progressBar.setFont(ColorTheme.font(Font.BOLD, 12));
            progressBar.putClientProperty(
                    "FlatLaf.style",
                    "arc: 18; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0"
            );

            queueBars[i] = progressBar;
            singleWindowPanel.add(progressBar, BorderLayout.CENTER);

            JLabel detail = new JLabel("空闲 | 平均等待 0.0 分");
            detail.setForeground(frontend.ColorTheme.TEXT_SECONDARY);
            detail.setFont(ColorTheme.font(Font.PLAIN, 11));
            detailLabels[i] = detail;
            singleWindowPanel.add(detail, BorderLayout.SOUTH);

            listPanel.add(singleWindowPanel);
        }

        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.setOpaque(false);
        wrapperPanel.add(listPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(wrapperPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        scrollPane.getVerticalScrollBar().putClientProperty("ScrollBar.showButtons", false);
        scrollPane.getVerticalScrollBar().putClientProperty("ScrollBar.thumbArc", 999);
        scrollPane.getVerticalScrollBar().putClientProperty("ScrollBar.thumbInsets", new Insets(2, 4, 2, 4));

        this.add(scrollPane, BorderLayout.CENTER);

        this.revalidate();
        this.repaint();
    }

    /**
     * 动态重置排队进度条的上限阈值
     * @param maxCapacity 理论最大排队人数 (如: 总人数 / 窗口数 * 拥挤系数)
     */
    public void setMaxQueueCapacity(int maxCapacity) {
        for (JProgressBar bar : queueBars) {
            bar.setMaximum(maxCapacity);
        }
    }


    public void updateQueueLength(int windowIndex, int currentLength) {
        if (windowIndex >= 0 && windowIndex < queueBars.length) {
            JProgressBar bar = queueBars[windowIndex];
            bar.setValue(currentLength);
            bar.setString("排队中: " + currentLength + " 人");

            // =========================================
            // 【视觉升级：动态相对阈值算法】
            // 动态获取当前进度条的最大承载量（Maximum）
            // =========================================
            int maxCapacity = bar.getMaximum();

            // 算出当前进度百分比
            double ratio = (double) currentLength / maxCapacity;

            // 【视觉升级：更敏锐的焦虑阈值】
            // 只要达到 30% 就开始黄牌警告，超过 60% 直接红牌！
            if (ratio < 0.3) {
                bar.setForeground(frontend.ColorTheme.ACCENT_GREEN);  // <30% 畅通
            } else if (ratio < 0.7) {
                bar.setForeground(frontend.ColorTheme.ACCENT_YELLOW); // 30%~70% 警告
            } else {
                bar.setForeground(frontend.ColorTheme.ACCENT_RED);    // >70% 极度拥挤
            }
        }
    }

    public void updateWindowStats(List<WindowStat> stats) {
        if (stats == null) {
            return;
        }
        for (WindowStat stat : stats) {
            int index = stat.windowId;
            if (index < 0 || index >= queueBars.length) {
                continue;
            }
            JProgressBar bar = queueBars[index];
            bar.setValue(stat.queueLength);
            bar.setString(stat.type + " | 排队 " + stat.queueLength + " 人");
            bar.setForeground(colorFor(stat.pressureLevel));
            detailLabels[index].setText(pressureText(stat.pressureLevel)
                    + " | " + (stat.serving ? "服务中" : "空闲")
                    + " | 平均等待 " + String.format("%.1f", stat.avgWaitMinutes)
                    + " 分 | 已服务 " + stat.servedCount);
        }
    }

    private String pressureText(PressureLevel pressureLevel) {
        if (pressureLevel == PressureLevel.MEDIUM) {
            return "中压";
        }
        if (pressureLevel == PressureLevel.HIGH) {
            return "高压";
        }
        if (pressureLevel == PressureLevel.OVERLOAD) {
            return "过载";
        }
        return "低压";
    }

    private Color colorFor(PressureLevel pressureLevel) {
        if (pressureLevel == PressureLevel.MEDIUM) {
            return ColorTheme.ACCENT_YELLOW;
        }
        if (pressureLevel == PressureLevel.HIGH) {
            return new Color(255, 145, 48);
        }
        if (pressureLevel == PressureLevel.OVERLOAD) {
            return ColorTheme.ACCENT_RED;
        }
        return ColorTheme.ACCENT_GREEN;
    }

    public void clearQueues() {
        if (queueBars == null) {
            return;
        }
        for (JProgressBar bar : queueBars) {
            bar.setValue(0);
            bar.setString("排队中: 0 人");
            bar.setForeground(frontend.ColorTheme.ACCENT_GREEN);
        }
    }
}
