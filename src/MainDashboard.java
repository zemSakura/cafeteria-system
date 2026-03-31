import javax.swing.*;
import java.awt.*;

public class MainDashboard {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> createAndShowGUI());
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("北京交通大学就餐仿真系统 - 总控台大屏");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800);
        frame.setLayout(new BorderLayout(10, 10));

        // 1. 北部：配置区
        frame.add(createConfigPanel(), BorderLayout.NORTH);

        // 2. 中部：拼装你的零件！
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        // 【核心联动在此！】直接 new 你刚才写的类，传入比如 20 张桌子
        centerPanel.add(new DiningAreaPanel(20));

        // 右边依然保留占位符，等我们以后做排队列表
        centerPanel.add(new QueueAreaPanel(15));

        frame.add(centerPanel, BorderLayout.CENTER);

        // 3. 南部：日志区
        frame.add(createLogAreaPanel(), BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ---------------- 以下为保留的旧方法 ----------------
    private static JPanel createConfigPanel() {
        // ... (保持上一轮的代码不变)
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Initialization Config"));
        panel.add(new JLabel("Tables (桌子数):")); panel.add(new JTextField("20", 5));
        panel.add(new JButton("▶ 开始仿真"));
        return panel;
    }

    private static JPanel createQueueAreaPlaceholder() {
        // ... (保持上一轮的代码不变)
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Window Queues Monitor"));
        panel.add(new JLabel("排队区占位", SwingConstants.CENTER));
        return panel;
    }

    private static JPanel createLogAreaPanel() {
        // ... (保持上一轮的代码不变)
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Real-time Logs"));
        panel.add(new JScrollPane(new JTextArea(8, 20)));
        return panel;
    }
}