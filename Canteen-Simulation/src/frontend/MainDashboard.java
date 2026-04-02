package frontend;

import javax.swing.*;
import java.awt.*;

import static java.lang.Thread.sleep;

public class MainDashboard {

    // 创建一个全局的 DiningAreaPanel 对象，用于后续的更新
    private static frontend.DiningAreaPanel myDiningPanel;

    // 声明线程变量，初始为 null
    private static Thread arrivalThread = null;

    // 日志区
    private static JTextArea logTextArea;
    private static javax.swing.Timer delayTimer;
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
        myDiningPanel = new frontend.DiningAreaPanel(20);
        centerPanel.add(myDiningPanel);


        // 右边依然保留占位符，等我们以后做排队列表
        centerPanel.add(new QueueAreaPanel(10));

        frame.add(centerPanel, BorderLayout.CENTER);

        // 3. 南部：日志区
        frame.add(createLogAreaPanel(), BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ---------------- 以下为保留的旧方法 ----------------
    private static JPanel createConfigPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Initialization Config"));

        // 1. 先给组件“上户口”（声明实体变量，有了名字才能被调用）
        JLabel tableLabel = new JLabel("Tables (桌子数):");
        JTextField tablesInputField = new JTextField("20", 5);
        JButton startButton = new JButton("▶ 开始仿真");
        JButton stopButton = new JButton("■ 停止仿真");

        // 2. 将带有名字的组件装进面板
        panel.add(tableLabel);
        panel.add(tablesInputField);
        panel.add(startButton);
        panel.add(stopButton);

        // 3. 在这里注入灵魂！为 startButton 绑定点击事件
        startButton.addActionListener(e -> {
            try {
                // --- 第一部分：【立即执行】参数检查与预热日志 ---
                String tableStr = tablesInputField.getText().trim();
                int tableCount = Integer.parseInt(tableStr);

                if (tableCount <= 0 || tableCount > 42) {
                    JOptionPane.showMessageDialog(panel, "桌子数量需在1-42之间！", "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 注入配置并更新画面
                backend.config.CanteenConfig.TOTAL_TABLES = tableCount;
                myDiningPanel.updateTableCount(tableCount);

                // 冻结界面，防止加载过程中用户乱点
                tablesInputField.setEnabled(false);
                startButton.setEnabled(false);

                // 打印前两行日志
                logTextArea.append(">>> 系统初始化完成，总桌子数已设定为: " + tableCount + "\n");
                logTextArea.append(">>> 正在点火，启动后端仿真引擎...\n");

                // --- 第二部分：【延迟执行】通过 Timer 制造 1s 的“加载感” ---
                // 参数：1000毫秒延迟，后面是延迟结束后的动作
                javax.swing.Timer delayTimer = new javax.swing.Timer(1000, event -> {

                    // 1. 打印最后一行点火成功的日志
                    logTextArea.append(">>> 仿真引擎启动成功！学生正在抵达...\n");

                    // 2. 真正启动后端线程
                    java.util.concurrent.BlockingQueue<backend.model.Student> arrivalQueue =
                            new java.util.concurrent.LinkedBlockingQueue<>(2000);

                    backend.module.ArrivalModule arrivalModule = new backend.module.ArrivalModule(arrivalQueue, logTextArea);
                    arrivalThread = new Thread(arrivalModule);
                    arrivalThread.start();

                    // 3. 激活停止按钮
                    stopButton.setEnabled(true);
                });

                delayTimer.setRepeats(false); // 极其重要：只运行一次，不要循环执行
                delayTimer.start(); // 启动倒计时

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "请输入有效的数字！", "格式错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        // 停止按钮
        stopButton.addActionListener(e -> {
            // 1. 【新增逻辑】检查是否正在“点火中”
            if (delayTimer != null && delayTimer.isRunning()) {
                delayTimer.stop(); // 剪断导火索，防止 1s 后引擎启动
                logTextArea.append(">>> [系统] 已取消点火，准备程序已拦截。\n");
            }

            // 2. 【原有逻辑】检查引擎是否已经在跑
            if (arrivalThread != null && arrivalThread.isAlive()) {
                arrivalThread.interrupt(); // 熄火
                logTextArea.append(">>> [系统] 收到停止指令，正在关闭引擎...\n");
            }

            // 3. 恢复 UI 状态
            tablesInputField.setEnabled(true);
            startButton.setEnabled(true);
            stopButton.setEnabled(false);

            System.out.println(">>> 仿真已手动终止。");
        });

        return panel;
    }




    private static JPanel createQueueAreaPlaceholder() {
        // ... (保持上一轮的代码不变)
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Window Queues Monitor"));
        panel.add(new JLabel("排队区占位", SwingConstants.CENTER));
        return panel;
    }


    // 4. 中部：日志区
    private static JPanel createLogAreaPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Real-time Logs (实时日志)"));

        // 2. 在这里初始化它
        logTextArea = new JTextArea(8, 20);
        logTextArea.setEditable(false); // 日志区通常设为不可手动编辑

        // 记得装进滚动面板，否则日志多了看不见
        JScrollPane scrollPane = new JScrollPane(logTextArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }
}