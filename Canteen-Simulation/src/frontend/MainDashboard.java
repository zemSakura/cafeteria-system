package frontend;

import com.formdev.flatlaf.FlatDarkLaf;
import backend.config.CanteenConfig;
import backend.engine.SimulationEngine;
import backend.model.Student;
import backend.module.ArrivalModule;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class MainDashboard extends JFrame implements SimulationEventListener {

    private static DiningAreaPanel myDiningPanel;
    private static QueueAreaPanel myQueuePanel;

    private static JButton startButton;
    private static JButton stopButton;

    private static MainDashboard frame;

    private static Thread simulationThread = null;
    private static SimulationEngine simulationEngine = null;

    private static JTextArea logTextArea;
    private static javax.swing.Timer delayTimer;

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        // 在 setup() 之后紧接着写
        UIManager.put( "Button.arc", 15 );       // 按钮圆角
        UIManager.put( "Component.arc", 15 );    // 输入框、下拉框圆角
        UIManager.put( "TextComponent.arc", 15 );
        UIManager.put( "ScrollBar.thumbArc", 999 ); // 药丸型滚动条

        SwingUtilities.invokeLater(MainDashboard::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        frame = new MainDashboard();
        frame.setTitle("北京交通大学就餐仿真系统 - 总控台大屏");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800);
        frame.setLayout(new BorderLayout(10, 10));

        frame.add(createConfigPanel(), BorderLayout.NORTH);

        frame.getContentPane().setBackground(ColorTheme.BG_MAIN);
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));


        myDiningPanel = new DiningAreaPanel(20);
        centerPanel.add(myDiningPanel);
        frame.add(centerPanel, BorderLayout.CENTER);

        myQueuePanel = new QueueAreaPanel(5);
        myQueuePanel.setPreferredSize(new Dimension(250, 0));
        frame.add(myQueuePanel, BorderLayout.EAST);

        myDiningPanel.setBackground(ColorTheme.BG_CARD);
        myQueuePanel.setBackground(ColorTheme.BG_CARD);

        frame.add(createLogAreaPanel(), BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JPanel createConfigPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Initialization Config"));

        startButton = new JButton("▶ 开始仿真");
        stopButton = new JButton("■ 停止仿真");
        stopButton.setEnabled(false);

        panel.add(startButton);
        panel.add(stopButton);

        startButton.addActionListener(e -> {
            SimulationConfigDialog configDialog = new SimulationConfigDialog(null);
            configDialog.setVisible(true);

            if (!configDialog.isConfirmed()) {
                return;
            }

            SimulationConfigDTO dto = configDialog.getConfigData();

            try {
                // [原有的前端自身配置可能还需要保留]
                applyConfig(dto);

                // =========================================
                // 【新增 1：把 DTO 转换成后端的 Request 格式】
                // =========================================
                backend.config.SimulationConfigRequest request = new backend.config.SimulationConfigRequest();
                request.setTableCount(dto.totalTables);
                request.setOpenDuration(dto.openDuration);
                request.setWindowCount(dto.windowCount);
                request.setRandomSeed(dto.randomSeed);

                // =========================================
                // 【智能概率分配算法】：绝对无损的浮点数分配法
                // =========================================
                double solo = dto.probSolo; // 比如界面传过来 0.7 或 0.8
                request.setProbSolo(solo);

                // 算出剩余的结伴概率池 (比如 1.0 - 0.8 = 0.2)
                double remainder = 1.0 - solo;

                // 假设在结伴的人中，双人占 70%，三人及以上团队占 30%
                double duo = remainder * 0.7;
                request.setProbDuo(duo);

                // 【终极防御】：最后一个 team 概率直接用 1.0 减去前两个，彻底斩断精度丢失！
                double team = 1.0 - solo - duo;
                request.setProbTeam(team);
                // =========================================

                // 接上新加的三个维度
                request.setTotalPopulation(dto.totalStudents);
                request.setSimulationMode(dto.simulationMode);
                request.setMealPeriod(dto.mealPeriod);

                // 强制更新后端的全局配置
                backend.config.CanteenConfig.updateAllConfigs(request);

                myDiningPanel.updateTableCount(dto.totalTables);
                myQueuePanel.updateWindowCount(dto.windowCount);

                logTextArea.setText("");
                appendLog(">>> 初始化配置注入成功！桌数: " + dto.totalTables + " | 窗口: " + dto.windowCount);
                if ("fullDay".equals(dto.simulationMode)) {
                    appendLog(">>> 仿真模式: 全天无缝聚合仿真 (早中晚连续时间轴)");
                } else {
                    appendLog(">>> 仿真模式: 单时段仿真 | 目标餐段: " + dto.mealPeriod);
                }
                appendLog(">>> 正在点火，启动正式仿真引擎...");

                startButton.setEnabled(false);
                stopButton.setEnabled(false);

                delayTimer = new javax.swing.Timer(800, event -> {
                    try {
                        appendLog(">>> 仿真引擎启动成功！正在生成复杂客流剧本...");

                        // =========================================
                        // 【新增 3：调用后端的全新到达计划接口】
                        // =========================================
                        ArrivalModule arrivalModule = new ArrivalModule(backend.config.CanteenConfig.RANDOM_SEED);

                        // 获取包含峰值、三餐和事件的超级结果包
                        backend.model.ArrivalGenerationResult result = arrivalModule.generateArrivalPlan(
                                backend.config.CanteenConfig.TOTAL_POPULATION,
                                backend.config.CanteenConfig.SIMULATION_MODE,
                                backend.config.CanteenConfig.MEAL_PERIOD
                        );

                        // 从结果包里提取出我们要的 students 列表
                        List<Student> students = result.getStudents();
                        // =========================================

                        appendLog(">>> 学生数据生成完成，共 " + students.size() + " 名学生。");

                        // 把拿到的新 students 原封不动传给你的引擎
                        simulationEngine = new SimulationEngine(students, frame, 50L);
                        simulationThread = new Thread(simulationEngine, "SimulationEngine");
                        simulationThread.start();

                        stopButton.setEnabled(true);
                    } catch (Exception ex) {
                        appendLog(">>> [错误] 启动失败：" + ex.getMessage());
                        startButton.setEnabled(true);
                        stopButton.setEnabled(false);
                        ex.printStackTrace();
                    }
                });

                delayTimer.setRepeats(false);
                delayTimer.start();

            } catch (Exception ex) {
                appendLog(">>> [错误] 配置非法：" + ex.getMessage());
                JOptionPane.showMessageDialog(frame, ex.getMessage(), "配置错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        stopButton.addActionListener(e -> {
            if (delayTimer != null && delayTimer.isRunning()) {
                delayTimer.stop();
                appendLog(">>> [系统] 已取消点火，准备程序已拦截。");
            }

            if (simulationEngine != null) {
                simulationEngine.requestStop();
            }

            if (simulationThread != null && simulationThread.isAlive()) {
                simulationThread.interrupt();
                appendLog(">>> [系统] 收到停止指令，正在关闭引擎...");
            }

            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        });

        return panel;
    }

    private static void applyConfig(SimulationConfigDTO dto) {
        CanteenConfig.TOTAL_TABLES = dto.totalTables;
        CanteenConfig.OPEN_DURATION = dto.openDuration;
        CanteenConfig.RANDOM_SEED = dto.randomSeed;

        CanteenConfig.initWindowsConfig(dto.windowCount);

        CanteenConfig.PROB_SOLO = dto.probSolo;
        double remain = 1.0 - dto.probSolo;
        CanteenConfig.PROB_DUO = remain * 2.0 / 3.0;
        CanteenConfig.PROB_TEAM = remain / 3.0;

        CanteenConfig.validate();
    }

    private static JPanel createLogAreaPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Real-time Logs (实时日志)"));


        logTextArea = new JTextArea(8, 20);
        logTextArea.setEditable(false);
        // 确保日志文本域的底色和文字颜色也使用调色盘
        logTextArea.setBackground(frontend.ColorTheme.BG_CARD); // 设置为极暗黑
        logTextArea.setForeground(frontend.ColorTheme.TEXT_PRIMARY); // 字体设为亮灰白

        JScrollPane scrollPane = new JScrollPane(logTextArea);
        // 【新增】：剥夺滚动条的默认线框
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        logTextArea.putClientProperty("JComponent.focusWidth", 0);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private static void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            logTextArea.append(msg + "\n");
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    @Override
    public void onStudentArrived(int studentId, long time) {
        SwingUtilities.invokeLater(() -> {
            logTextArea.append(String.format(">>> [%03d] 学生 %03d 抵达食堂%n", time, studentId));
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    @Override
    public void onWindowQueueUpdated(int windowIndex, int queueLength) {
        SwingUtilities.invokeLater(() -> myQueuePanel.updateQueueLength(windowIndex, queueLength));
    }

    @Override
    public void onTableOccupancyChanged(int tableIndex, int occupiedSeats) {
        SwingUtilities.invokeLater(() -> {
            myDiningPanel.updateTableOccupancy(tableIndex, occupiedSeats);
            logTextArea.append(String.format(
                    ">>> [桌位] 桌 %02d 当前入座人数: %d%n",
                    tableIndex + 1,
                    occupiedSeats
            ));
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    @Override
    public void onSimulationFinished() {
        SwingUtilities.invokeLater(() -> {
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            logTextArea.append(">>> [系统] 本次仿真已结束。\n");
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
            JOptionPane.showMessageDialog(this, "本次仿真已圆满结束！");
        });
    }
}