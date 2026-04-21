package frontend;

import javax.swing.*;
import java.awt.*;

public class QueueAreaPanel extends JPanel {

    private JProgressBar[] queueBars;

    public QueueAreaPanel(int initialWindowCount) {
        this.setLayout(new BorderLayout());

        // 【视觉优化 1：设置整体背景色为悬浮卡片色】
        this.setBackground(frontend.ColorTheme.BG_CARD);

        // 【视觉优化 2：用留白代替死板的线框，文字换成高级灰】
        javax.swing.border.TitledBorder titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(20, 20, 20, 20),
                "窗口排队监控"
        );
        titledBorder.setTitleColor(frontend.ColorTheme.TEXT_SECONDARY);
        this.setBorder(titledBorder);

        updateWindowCount(initialWindowCount);
    }

    public void updateWindowCount(int windowCount) {
        this.removeAll();

        queueBars = new JProgressBar[windowCount];

        JPanel listPanel = new JPanel(new GridLayout(windowCount, 1, 0, 15));
        listPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        // 极其重要：让里面的面板透明，露出底下的高级暗色
        listPanel.setOpaque(false);

        for (int i = 0; i < windowCount; i++) {
            JPanel singleWindowPanel = new JPanel(new BorderLayout(10, 0));
            singleWindowPanel.setPreferredSize(new Dimension(0, 35));
            singleWindowPanel.setOpaque(false); // 背景透明

            JLabel nameLabel = new JLabel("窗口 " + (i + 1));
            nameLabel.setPreferredSize(new Dimension(60, 30));
            // 文字变成主标题亮色
            nameLabel.setForeground(frontend.ColorTheme.TEXT_PRIMARY);
            singleWindowPanel.add(nameLabel, BorderLayout.WEST);

            JProgressBar progressBar = new JProgressBar(0, 30);
            progressBar.setValue(0);
            progressBar.setStringPainted(true);
            progressBar.setString("排队中: 0 人");

            // 【视觉优化 3：初始颜色换成深邃调色盘的霓虹青色】
            progressBar.setForeground(frontend.ColorTheme.ACCENT_CYAN);

            // 给进度条也加上圆角魔法 (结合 FlatLaf 引擎食用极佳)
            progressBar.putClientProperty("FlatLaf.style", "arc: 15");

            queueBars[i] = progressBar;
            singleWindowPanel.add(progressBar, BorderLayout.CENTER);

            listPanel.add(singleWindowPanel);
        }

        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.setOpaque(false); // 包装器透明
        wrapperPanel.add(listPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(wrapperPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null); // 去掉滚动条的边框
        scrollPane.setOpaque(false);
        // 【视觉优化 4 (驱逐白色刺客)：视口也必须设置透明！】
        scrollPane.getViewport().setOpaque(false);

        this.add(scrollPane, BorderLayout.CENTER);

        this.revalidate();
        this.repaint();
    }

    public void updateQueueLength(int windowIndex, int currentLength) {
        if (windowIndex >= 0 && windowIndex < queueBars.length) {
            JProgressBar bar = queueBars[windowIndex];
            bar.setValue(currentLength);
            bar.setString("排队中: " + currentLength + " 人");

            // 【视觉优化 5：动态切换为赛博霓虹色】
            if (currentLength < 10) {
                bar.setForeground(frontend.ColorTheme.ACCENT_CYAN);   // 安全：霓虹青
            } else if (currentLength < 20) {
                bar.setForeground(frontend.ColorTheme.ACCENT_YELLOW); // 警告：暖黄
            } else {
                bar.setForeground(frontend.ColorTheme.ACCENT_RED);    // 拥挤：樱桃红
            }
        }
    }
}