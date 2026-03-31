import javax.swing.*;
import java.awt.*;

// 专门负责右侧窗口排队监控的面板
public class QueueAreaPanel extends JPanel {

    // 用一个数组把所有窗口的进度条保存下来，方便以后后端传数据时修改它
    private JProgressBar[] queueBars;

    public QueueAreaPanel(int windowCount) {
        this.setLayout(new BorderLayout());
        this.setBorder(BorderFactory.createTitledBorder("Window Queues Monitor (窗口排队监控)"));

        // 中间用一个网格布局，专门放各行窗口
        // 行数就是窗口数，列数是1
        JPanel listPanel = new JPanel(new GridLayout(windowCount, 1, 0, 15));
        listPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        queueBars = new JProgressBar[windowCount];

        for (int i = 0; i < windowCount; i++) {
            JPanel singleWindowPanel = new JPanel(new BorderLayout(10, 0));

            // 左边：窗口名字
            JLabel nameLabel = new JLabel("窗口 " + (i + 1));
            nameLabel.setPreferredSize(new Dimension(60, 30));
            singleWindowPanel.add(nameLabel, BorderLayout.WEST);

            // 中间：进度条 (代表排队人数)
            JProgressBar progressBar = new JProgressBar(0, 30); // 假设最多排30人
            progressBar.setValue(0); // 初始0人
            progressBar.setStringPainted(true); // 允许在进度条上显示文字
            progressBar.setString("排队中: 0 人");
            progressBar.setForeground(new Color(52, 199, 89)); // 默认绿色

            queueBars[i] = progressBar; // 存入数组，以后备用
            singleWindowPanel.add(progressBar, BorderLayout.CENTER);

            listPanel.add(singleWindowPanel);
        }

        this.add(listPanel, BorderLayout.CENTER);
    }

    // =========================================
    // 【核心接口】：未来给后端调用的方法
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