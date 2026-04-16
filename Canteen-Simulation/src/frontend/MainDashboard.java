package frontend;

import javax.swing.*;
import java.awt.*;

import static java.lang.Thread.sleep;

public class MainDashboard extends JFrame implements SimulationEventListener {

    // 创建一个全局的 DiningAreaPanel 对象，用于后续的更新
    private static frontend.DiningAreaPanel myDiningPanel;
    private static frontend.QueueAreaPanel myQueuePanel;
    private static javax.swing.JButton startButton;
    private static MainDashboard frame;

    // 声明线程变量，初始为 null
    private static Thread arrivalThread = null;

    // 日志区
    private static JTextArea logTextArea;
    private static javax.swing.Timer delayTimer;
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> createAndShowGUI());
    }

    private static void createAndShowGUI() {
        frame = new MainDashboard();
        frame.setTitle("北京交通大学就餐仿真系统 - 总控台大屏");
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


        frame.add(centerPanel, BorderLayout.CENTER);

        // 3. 东部：窗口排队监控区
        myQueuePanel = new frontend.QueueAreaPanel(5); // 赋值给全局静态变量
        myQueuePanel.setPreferredSize(new Dimension(250, 0)); // 限制一下宽度
        frame.add(myQueuePanel, BorderLayout.EAST); // 挂在整个窗口的最右边

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
        startButton = new javax.swing.JButton("▶ 开始仿真");
        JButton stopButton = new JButton("■ 停止仿真");

        // 2. 将带有名字的组件装进面板
        panel.add(startButton);
        panel.add(stopButton);

        // 3. 在这里注入灵魂！为 startButton 绑定点击事件
        startButton.addActionListener(e -> {
            // 1. 拦截：弹出配置对话框
            // 假设你的大屏主 JFrame 变量名是 frame（如果不是，请换成实际的 JFrame 引用，或者填 null）
            SimulationConfigDialog configDialog = new SimulationConfigDialog(null);
            configDialog.setVisible(true); // 代码会在这里“暂停”，等待用户操作弹窗

            // 2. 判断用户操作：如果用户点了“取消”或者直接关了弹窗，就什么都不做
            if (!configDialog.isConfirmed()) {
                return;
            }

            // 3. 拿到用户确认后的数据包！
            frontend.SimulationConfigDTO dto = configDialog.getConfigData();

            // 4. 【过渡期操作】：在后端组员改造完之前，我们先手动把这些值硬塞给后端的静态变量
            config.CanteenConfig.TOTAL_TABLES = dto.totalTables;
            config.CanteenConfig.OPEN_DURATION = dto.openDuration;

            int wCount = dto.windowCount;
            config.CanteenConfig.WINDOW_DISTANCES = new int[wCount];
            config.CanteenConfig.WINDOW_AVG_SERVE_TIME = new int[wCount];

            for (int i = 0; i < wCount; i++) {
                config.CanteenConfig.WINDOW_DISTANCES[i] = 10 + (i * 5);
                config.CanteenConfig.WINDOW_AVG_SERVE_TIME[i] = 2;
            }

            // 5. 更新物理画面
            myDiningPanel.updateTableCount(dto.totalTables);
            myQueuePanel.updateWindowCount(dto.windowCount);

            // 6. 冻结界面按钮
            startButton.setEnabled(false);
            // ... 如果你那个 tablesInputField 还在界面上，也可以 setEnabled(false)

            // 7. 打印预热日志
            logTextArea.append(">>> 初始化配置注入成功！桌数: " + dto.totalTables + " | 窗口: " + dto.windowCount + "\n");
            logTextArea.append(">>> 正在点火，启动后端仿真引擎...\n");

            // 8. 触发 Timer 延迟启动（复用你昨天的完美逻辑）
            delayTimer = new javax.swing.Timer(1000, event -> {
                logTextArea.append(">>> 仿真引擎启动成功！学生正在抵达...\n");

                java.util.concurrent.BlockingQueue<model.Student> arrivalQueue =
                        new java.util.concurrent.LinkedBlockingQueue<>(2000);

                backend.module.ArrivalModule arrivalModule = new backend.module.ArrivalModule(arrivalQueue, logTextArea);
                arrivalThread = new Thread(arrivalModule);
                arrivalThread.start();

                stopButton.setEnabled(true);
            });

            delayTimer.setRepeats(false);
            delayTimer.start();
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

    @Override
    public void onStudentArrived(int studentId, long time) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            logTextArea.append(String.format(">>> [%d] 学生 %d 抵达食堂\n", time, studentId));
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    @Override
    public void onWindowQueueUpdated(int windowIndex, int queueLength) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            myQueuePanel.updateQueueLength(windowIndex, queueLength);
        });
    }

    @Override
    public void onTableStatusChanged(int tableIndex, boolean isOccupied) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            myDiningPanel.updateTableStatus(tableIndex, isOccupied);
        });
    }

    @Override
    public void onSimulationFinished() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            javax.swing.JOptionPane.showMessageDialog(this, "本次仿真已圆满结束！");
            startButton.setEnabled(true);
        });
    }
}