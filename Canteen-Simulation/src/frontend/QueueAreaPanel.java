package frontend;

import javax.swing.*;
import java.awt.*;

// 专门负责右侧窗口排队监控的面板
public class QueueAreaPanel extends JPanel {

    // 用一个数组把所有窗口的进度条保存下来，方便以后后端传数据时修改它
    private JProgressBar[] queueBars;

    public QueueAreaPanel(int initialWindowCount) {
        this.setLayout(new BorderLayout());
        this.setBorder(BorderFactory.createTitledBorder("Window Queues Monitor (窗口排队监控)"));

        // 构造时直接调用刷新方法
        updateWindowCount(initialWindowCount);
    }

    // =========================================
    // 【新增核心接口】：响应配置弹窗，擦除重画所有窗口
    // =========================================
    public void updateWindowCount(int windowCount) {
        this.removeAll();

        queueBars = new JProgressBar[windowCount];

        // 1. 核心列表面板（负责把所有窗口排成一列）
        JPanel listPanel = new JPanel(new GridLayout(windowCount, 1, 0, 15));
        listPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        for (int i = 0; i < windowCount; i++) {
            JPanel singleWindowPanel = new JPanel(new BorderLayout(10, 0));
            // 锁定单个窗口的完美高度（比如 35 像素），防止被拉伸变形
            singleWindowPanel.setPreferredSize(new Dimension(0, 35));

            JLabel nameLabel = new JLabel("窗口 " + (i + 1));
            nameLabel.setPreferredSize(new Dimension(60, 30));
            singleWindowPanel.add(nameLabel, BorderLayout.WEST);

            JProgressBar progressBar = new JProgressBar(0, 30);
            progressBar.setValue(0);
            progressBar.setStringPainted(true);
            progressBar.setString("排队中: 0 人");
            progressBar.setForeground(new Color(52, 199, 89));

            queueBars[i] = progressBar;
            singleWindowPanel.add(progressBar, BorderLayout.CENTER);

            listPanel.add(singleWindowPanel);
        }

        // =========================================
        // 【防变形魔法】：用一个空的 BorderLayout 把列表顶在最上方 (NORTH)
        // 这样即使只有 2 个窗口，它们也不会被撑满全屏
        // =========================================
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(listPanel, BorderLayout.NORTH);

        // =========================================
        // 【滚动条魔法】：给包装好的面板套上滚动视口
        // =========================================
        JScrollPane scrollPane = new JScrollPane(wrapperPanel);
        // 只在需要时显示垂直滚动条
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        // 永远不显示难看的水平滚动条
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        // 去掉默认的边框，让它和原来的 UI 完美融合
        scrollPane.setBorder(null);

        // 2. 把带有滚动条的画框加进主面板
        this.add(scrollPane, BorderLayout.CENTER);

        this.revalidate();
        this.repaint();
    }

    // =========================================
    // 【老核心接口】：未来给后端调用的方法，更新进度条数值
    // =========================================
    public void updateQueueLength(int windowIndex, int currentLength) {
        if (windowIndex >= 0 && windowIndex < queueBars.length) {
            JProgressBar bar = queueBars[windowIndex];
            bar.setValue(currentLength);
            bar.setString("排队中: " + currentLength + " 人");

            // 动态变色逻辑：人少绿色，中等橙色，快满了红色
            if (currentLength < 10) {
                bar.setForeground(new Color(52, 199, 89)); // 绿
            } else if (currentLength < 20) {
                bar.setForeground(new Color(255, 149, 0)); // 橙
            } else {
                bar.setForeground(new Color(255, 59, 48)); // 红
            }
        }
    }
}