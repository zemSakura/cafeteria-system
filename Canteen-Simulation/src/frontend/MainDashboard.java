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
    private static JLabel phaseLabel;

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

        // 1. 顶部控制栏
        // =========================================
        // 【架构调整】：最外层包装盒 (不带滚动，负责左右分界)
        // =========================================
        JPanel topWrapper = new JPanel(new BorderLayout(10, 0));
        topWrapper.setOpaque(false);

        // =========================================
        // 【解耦升级：仅针对“就餐区”的智能滑动包装盒】
        // =========================================
        class DiningScrollWrapper extends JPanel implements javax.swing.Scrollable {
            // 【防挤压底线】：如果桌子变多（比如30桌），觉得被压扁了，可以把这个值调高（如500或600）
            private final int MIN_HEIGHT = 450;

            public DiningScrollWrapper() {
                super(new BorderLayout());
                setOpaque(false);
            }

            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (getParent() instanceof javax.swing.JViewport) {
                    // 仅缩放就餐区：视窗够大就拉伸，视窗被挤压到底线就锁死
                    d.height = Math.max(MIN_HEIGHT, getParent().getHeight());
                }
                return d;
            }

            @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
            @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 40; }
            @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 100; }
            @Override public boolean getScrollableTracksViewportWidth() { return true; }

            @Override public boolean getScrollableTracksViewportHeight() {
                if (getParent() instanceof javax.swing.JViewport) {
                    return getParent().getHeight() >= MIN_HEIGHT;
                }
                return false;
            }
        }

        // 1. 组装就餐区及专属滑动窗口
        DiningScrollWrapper diningWrapper = new DiningScrollWrapper();
        myDiningPanel = new DiningAreaPanel(30); // 你的30张桌子
        myDiningPanel.setBackground(ColorTheme.BG_CARD);
        diningWrapper.add(myDiningPanel, BorderLayout.CENTER);

        JScrollPane diningScroll = new JScrollPane(diningWrapper);
        diningScroll.setBorder(null);
        diningScroll.setOpaque(false);
        diningScroll.getViewport().setOpaque(false);
        diningScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        diningScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        // 2. 组装排队区 (独立在右侧，不受就餐区滚动影响)
        myQueuePanel = new QueueAreaPanel(5);
        myQueuePanel.setPreferredSize(new Dimension(250, 0));
        myQueuePanel.setBackground(ColorTheme.BG_CARD);

        // 3. 终极合体：左边是可以自己滚动+缩放的就餐区，右边是雷打不动的排队区
        topWrapper.add(diningScroll, BorderLayout.CENTER);
        topWrapper.add(myQueuePanel, BorderLayout.EAST);

        // =========================================
        // 【日志与分割面板】
        // =========================================
        JPanel logPanel = createLogAreaPanel();
        logPanel.setMinimumSize(new Dimension(0, 120)); // 日志区最少保留 120 像素

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(6);

        // 上面放包含一切的全局滑动视窗，下面放日志
        splitPane.setTopComponent(topWrapper);
        splitPane.setBottomComponent(logPanel);

        splitPane.setResizeWeight(1.0);
        // 将初始分割线设为 0.75 的比例，避免硬编码像素导致在不同屏幕上被截断
        splitPane.setDividerLocation(0.75);

        frame.add(splitPane, BorderLayout.CENTER);
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

        phaseLabel = new JLabel(" ");
        phaseLabel.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 14));
        phaseLabel.setForeground(ColorTheme.TEXT_SECONDARY);
        panel.add(phaseLabel);

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
                phaseLabel.setText(" ");
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

                        // 把拿到的新 students 连同阶段边界传给引擎
                        long timeScale = 20L; // 每 tick 20ms，比原来的 50ms 快 2.5x
                        simulationEngine = new SimulationEngine(
                                students, frame, timeScale,
                                result.getPhaseBoundaries());
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
            phaseLabel.setText(" ");
            phaseLabel.setForeground(ColorTheme.TEXT_SECONDARY);
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
    public void onTableOccupancyChanged(int tableIndex, int[] seatGroupIds) {
        SwingUtilities.invokeLater(() -> {
            int total = 0;
            for (int gid : seatGroupIds) {
                if (gid >= 0) total++;
            }
            myDiningPanel.updateTableOccupancy(tableIndex, seatGroupIds);
            logTextArea.append(String.format(
                    ">>> [桌位] 桌 %02d 当前入座人数: %d%n",
                    tableIndex + 1,
                    total
            ));
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    @Override
    public void onPhaseChanged(String phaseName, String label, long currentTime) {
        SwingUtilities.invokeLater(() -> {
            Color phaseColor;
            switch (phaseName) {
                case "早餐":   phaseColor = ColorTheme.ACCENT_CYAN;   break;
                case "午餐":   phaseColor = ColorTheme.ACCENT_YELLOW; break;
                case "晚餐":   phaseColor = ColorTheme.ACCENT_BLUE;   break;
                case "关闭中": phaseColor = ColorTheme.TEXT_SECONDARY; break;
                default:       phaseColor = ColorTheme.TEXT_PRIMARY;  break;
            }
            phaseLabel.setText(" ● " + label);
            phaseLabel.setForeground(phaseColor);
            appendLog(">>> [阶段] " + label + " (t=" + currentTime + ")");
        });
    }

    @Override
    public void onSimulationFinished() {
        SwingUtilities.invokeLater(() -> {
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            phaseLabel.setText(" ● 仿真结束");
            phaseLabel.setForeground(ColorTheme.TEXT_SECONDARY);
            logTextArea.append(">>> [系统] 本次仿真已结束。\n");
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
            JOptionPane.showMessageDialog(this, "本次仿真已圆满结束！");
        });
    }
}