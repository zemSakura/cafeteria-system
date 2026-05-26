package frontend;

import com.formdev.flatlaf.FlatDarkLaf;
import backend.config.CanteenConfig;
import backend.engine.SimulationEngine;
import backend.model.Student;
import backend.module.ArrivalModule;
import backend.optimize.SimRunResult;

import javax.swing.*;
import java.awt.*;
import java.util.List;
public class MainDashboard extends JFrame implements SimulationEventListener {

    private static DiningAreaPanel myDiningPanel;
    private static QueueAreaPanel myQueuePanel;

    private static JButton startButton;
    private static JButton manualReplayButton;
    private static JButton stopButton;
    private static JLabel phaseLabel;
    private static final String VIEW_OPTIMIZATION = "optimization";
    private static final String VIEW_REPLAY = "replay";
    private static CardLayout dashboardCardLayout;
    private static JPanel dashboardCards;
    private static SimulationConfigDTO replayPresetConfig;
    private static SimRunResult latestOptimizationContext;

    private static MainDashboard frame;

    private static Thread simulationThread = null;
    private static SimulationEngine simulationEngine = null;

    private static JTextArea logTextArea;
    private static javax.swing.Timer delayTimer;
    /** 当前餐段的起始秒数（分钟-of-day * 60），用于将仿真 tick 转为自然时间 */
    private static long timeBaseSeconds = 0;

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

        frame.add(createViewSwitchPanel(), BorderLayout.NORTH);
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
        // =========================================
        // 【解耦升级：仅针对“就餐区”的智能滑动包装盒】
        // =========================================
        class DiningScrollWrapper extends JPanel implements javax.swing.Scrollable {
            private final int MIN_HEIGHT = 450;

            public DiningScrollWrapper() {
                super(new BorderLayout());
                setOpaque(false);
            }

            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (getParent() instanceof javax.swing.JViewport) {
                    // 取 桌子真实总高度、保底高度、屏幕高度 的最大值
                    d.height = Math.max(d.height, Math.max(MIN_HEIGHT, getParent().getHeight()));
                }
                return d;
            }

            @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
            @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 40; } // 滚轮速度
            @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 100; }
            @Override public boolean getScrollableTracksViewportWidth() { return true; }

            @Override
            public boolean getScrollableTracksViewportHeight() {
                // 【致命 Bug 修复点】
                // 只有当桌子的真实自然高度（super.getPreferredSize().height）小于视窗高度时，才去贴合屏幕压缩。
                // 一旦桌子变多，真实高度超过了视窗，立刻返回 false，拒绝压缩，强行撑开滚动条！
                if (getParent() instanceof javax.swing.JViewport) {
                    return super.getPreferredSize().height <= getParent().getHeight();
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

        JPanel replayPanel = new JPanel(new BorderLayout(10, 10));
        replayPanel.setBackground(ColorTheme.BG_MAIN);
        replayPanel.add(createConfigPanel(), BorderLayout.NORTH);
        replayPanel.add(splitPane, BorderLayout.CENTER);

        dashboardCardLayout = new CardLayout();
        dashboardCards = new JPanel(dashboardCardLayout);
        dashboardCards.setBackground(ColorTheme.BG_MAIN);
        dashboardCards.add(new OptimizationPanel(MainDashboard::applyOptimizationPreset,
                MainDashboard::rememberOptimizationContext), VIEW_OPTIMIZATION);
        dashboardCards.add(replayPanel, VIEW_REPLAY);

        frame.add(dashboardCards, BorderLayout.CENTER);
        dashboardCardLayout.show(dashboardCards, VIEW_OPTIMIZATION);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JPanel createViewSwitchPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        panel.setBackground(ColorTheme.BG_MAIN);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

        JButton optimizeViewButton = new JButton("寻优模式");
        JButton replayViewButton = new JButton("复盘模式");
        styleNavButton(optimizeViewButton, ColorTheme.ACCENT_CYAN);
        styleNavButton(replayViewButton, ColorTheme.ACCENT_YELLOW);

        optimizeViewButton.addActionListener(e -> dashboardCardLayout.show(dashboardCards, VIEW_OPTIMIZATION));
        replayViewButton.addActionListener(e -> dashboardCardLayout.show(dashboardCards, VIEW_REPLAY));

        panel.add(optimizeViewButton);
        panel.add(replayViewButton);
        return panel;
    }

    private static void styleNavButton(JButton button, Color accentColor) {
        button.setFocusPainted(false);
        button.setForeground(ColorTheme.BG_CARD);
        button.setBackground(accentColor);
    }

    private static void rememberOptimizationContext(SimRunResult result) {
        latestOptimizationContext = result == null ? null : result.copyBasic();
        if (manualReplayButton != null) {
            manualReplayButton.setEnabled(latestOptimizationContext != null);
            manualReplayButton.setToolTipText(latestOptimizationContext == null
                    ? "请先完成一次寻优"
                    : "在最近一次寻优范围内手动选择复盘参数");
        }
    }

    private static void applyOptimizationPreset(SimRunResult result) {
        latestOptimizationContext = result.copyBasic();
        SimulationConfigDTO preset = buildLockedReplayConfig(result);
        replayPresetConfig = preset;
        if (startButton != null) {
            startButton.setEnabled(true);
            startButton.setToolTipText("使用已导入的复盘方案启动仿真");
        }
        if (manualReplayButton != null) {
            manualReplayButton.setEnabled(true);
        }

        dashboardCardLayout.show(dashboardCards, VIEW_REPLAY);
        JOptionPane.showMessageDialog(frame,
                "已导入复盘参数：窗口 " + result.windowCount + "，桌子 " + result.tableCount
                        + "。复盘参数已锁定，请确认后运行。",
                "导入成功",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static SimulationConfigDTO buildLockedReplayConfig(SimRunResult result) {
        SimulationConfigDTO preset = new SimulationConfigDTO();
        boolean hasOptimizationMetadata = result.requestedPopulation > 0;
        preset.totalTables = result.tableCount;
        preset.windowCount = result.windowCount;
        preset.totalStudents = result.requestedPopulation > 0 ? result.requestedPopulation : preset.totalStudents;
        preset.openDuration = result.openDuration > 0 ? result.openDuration : CanteenConfig.OPEN_DURATION;
        preset.probSolo = hasOptimizationMetadata ? result.probSolo : CanteenConfig.PROB_SOLO;
        preset.randomSeed = result.randomSeed == 0L ? preset.randomSeed : result.randomSeed;
        preset.simulationMode = result.simulationModeCode == null ? CanteenConfig.SIMULATION_MODE.getCode() : result.simulationModeCode;
        preset.mealPeriod = result.mealPeriodCode == null ? CanteenConfig.MEAL_PERIOD.getCode() : result.mealPeriodCode;
        preset.lockedFromOptimization = true;
        preset.lockedWindowDistances = CanteenConfig.WINDOW_DISTANCES.clone();
        preset.lockedWindowAvgServeTime = CanteenConfig.WINDOW_AVG_SERVE_TIME.clone();
        preset.minWindowCount = result.minWindowCount > 0 ? result.minWindowCount : result.windowCount;
        preset.maxWindowCount = result.maxWindowCount > 0 ? result.maxWindowCount : result.windowCount;
        preset.minTableCount = result.minTableCount > 0 ? result.minTableCount : result.tableCount;
        preset.maxTableCount = result.maxTableCount > 0 ? result.maxTableCount : result.tableCount;
        return preset;
    }

    private static JPanel createConfigPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        panel.setBackground(ColorTheme.BG_CARD);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        startButton = new JButton("▶ 开始仿真");
        manualReplayButton = new JButton("手动导入复盘参数");
        stopButton = new JButton("■ 停止仿真");
        startButton.setEnabled(false);
        startButton.setToolTipText("请先导入一份复盘参数");
        manualReplayButton.setEnabled(false);
        manualReplayButton.setToolTipText("请先完成一次寻优");
        stopButton.setEnabled(false);

        panel.add(manualReplayButton);
        panel.add(startButton);
        panel.add(stopButton);

        phaseLabel = new JLabel(" ");
        phaseLabel.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 14));
        phaseLabel.setForeground(ColorTheme.TEXT_SECONDARY);
        panel.add(phaseLabel);

        manualReplayButton.addActionListener(e -> openManualReplayImportDialog());

        startButton.addActionListener(e -> {
            if (replayPresetConfig == null || !replayPresetConfig.lockedFromOptimization) {
                JOptionPane.showMessageDialog(frame,
                        "请先从寻优结果导入一个候选方案，或点击“手动导入复盘参数”。",
                        "复盘参数未锁定",
                        JOptionPane.INFORMATION_MESSAGE);
                startButton.setEnabled(false);
                return;
            }

            SimulationConfigDialog configDialog = new SimulationConfigDialog(frame, replayPresetConfig);
            configDialog.setVisible(true);

            if (!configDialog.isConfirmed()) {
                return;
            }

            SimulationConfigDTO dto = configDialog.getConfigData();
            replayPresetConfig = dto;

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
                double solo = dto.probSolo;
                request.setProbSolo(solo);

                double remainder = 1.0 - solo;

                // 结伴中：双人 50%，三人 30%，四人 20%
                double duo = remainder * 0.5;
                request.setProbDuo(duo);

                double trio = remainder * 0.3;
                request.setProbTrio(trio);

                double team = 1.0 - solo - duo - trio;
                request.setProbTeam(team);
                // =========================================

                // 接上新加的三个维度
                request.setTotalPopulation(dto.totalStudents);
                request.setSimulationMode(dto.simulationMode);
                request.setMealPeriod(dto.mealPeriod);
                if (dto.lockedFromOptimization
                        && dto.lockedWindowDistances != null
                        && dto.lockedWindowAvgServeTime != null) {
                    request.setWindowDistances(resizeLockedWindowDistances(dto.lockedWindowDistances, dto.windowCount));
                    request.setWindowAvgServeTime(resizeLockedServeTimes(dto.lockedWindowAvgServeTime, dto.windowCount));
                }

                // 强制更新后端的全局配置
                backend.config.CanteenConfig.updateAllConfigs(request);

                myDiningPanel.updateTableCount(dto.totalTables);
                myQueuePanel.updateWindowCount(dto.windowCount);

                // =========================================
                // 【核心闭环：物理常识与动态波动的结合】
                // =========================================
                // 假设最高峰只有 5%~10% 的学生同时处于“正在排队”状态
                int peakFactor = (int) (dto.totalStudents * 0.08);
                int calculatedMax = Math.max(20, peakFactor / dto.windowCount);

                // 【关键防御】：强制物理封顶！一个窗口最多容忍 120 人排队，再多就爆表了
                int maxQueuePerWindow = Math.min(120, calculatedMax);

                myQueuePanel.setMaxQueueCapacity(maxQueuePerWindow);
                // =========================================

                logTextArea.setText("");
                phaseLabel.setText(" ");
                appendLog(">>> 初始化配置注入成功！桌数: " + dto.totalTables + " | 窗口: " + dto.windowCount);
                if ("fullDay".equals(dto.simulationMode)) {
                    appendLog(">>> 仿真模式: 全天无缝聚合仿真 (早中晚连续时间轴)");
                } else {
                    appendLog(">>> 仿真模式: 单时段仿真 | 目标餐段: " + displayMealPeriod(dto.mealPeriod));
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
                        startButton.setEnabled(replayPresetConfig != null && replayPresetConfig.lockedFromOptimization);
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

            startButton.setEnabled(replayPresetConfig != null && replayPresetConfig.lockedFromOptimization);
            stopButton.setEnabled(false);
            phaseLabel.setText(" ");
            phaseLabel.setForeground(ColorTheme.TEXT_SECONDARY);
        });

        return panel;
    }

    private static void openManualReplayImportDialog() {
        if (latestOptimizationContext == null) {
            JOptionPane.showMessageDialog(frame,
                    "请先在寻优模式中完成一次寻优，系统需要使用该次寻优的窗口和桌子范围。",
                    "缺少寻优范围",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JTextField windowField = new JTextField(String.valueOf(latestOptimizationContext.windowCount), 8);
        JTextField tableField = new JTextField(String.valueOf(latestOptimizationContext.tableCount), 8);
        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        form.add(new JLabel("窗口数范围："
                + latestOptimizationContext.minWindowCount + " - " + latestOptimizationContext.maxWindowCount));
        form.add(windowField);
        form.add(new JLabel("桌子数范围："
                + latestOptimizationContext.minTableCount + " - " + latestOptimizationContext.maxTableCount));
        form.add(tableField);
        form.add(new JLabel("就餐人数："));
        form.add(new JLabel(String.valueOf(latestOptimizationContext.requestedPopulation)));
        form.add(new JLabel("仿真时长："));
        form.add(new JLabel(latestOptimizationContext.openDuration + " 分钟"));

        int result = JOptionPane.showConfirmDialog(
                frame,
                form,
                "手动导入复盘参数",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            int windowCount = Integer.parseInt(windowField.getText().trim());
            int tableCount = Integer.parseInt(tableField.getText().trim());
            validateManualReplayRange(windowCount, tableCount, latestOptimizationContext);

            SimRunResult selected = latestOptimizationContext.copyBasic();
            selected.windowCount = windowCount;
            selected.tableCount = tableCount;
            if (selected.baseRandomSeed != 0L && selected.repeatTimes > 0) {
                selected.randomSeed = deriveReplaySeed(selected.baseRandomSeed, windowCount, tableCount, selected.repeatTimes);
            }
            applyOptimizationPreset(selected);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(frame, "窗口数和桌子数必须是整数。", "格式错误", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "参数越界", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void validateManualReplayRange(int windowCount, int tableCount, SimRunResult context) {
        if (windowCount < context.minWindowCount || windowCount > context.maxWindowCount) {
            throw new IllegalArgumentException("窗口数必须在寻优范围 "
                    + context.minWindowCount + " 到 " + context.maxWindowCount + " 之间。");
        }
        if (tableCount < context.minTableCount || tableCount > context.maxTableCount) {
            throw new IllegalArgumentException("桌子数必须在寻优范围 "
                    + context.minTableCount + " 到 " + context.maxTableCount + " 之间。");
        }
    }

    private static long deriveReplaySeed(long baseSeed, int windowCount, int tableCount, int repeatIndex) {
        long seed = baseSeed;
        seed ^= 0x9E3779B97F4A7C15L + windowCount * 1000003L;
        seed ^= Long.rotateLeft(tableCount * 10007L, 21);
        seed ^= Long.rotateLeft(repeatIndex * 1009L, 42);
        return seed;
    }

    private static void applyConfig(SimulationConfigDTO dto) {
        CanteenConfig.TOTAL_TABLES = dto.totalTables;
        CanteenConfig.OPEN_DURATION = dto.openDuration;
        CanteenConfig.RANDOM_SEED = dto.randomSeed;

        if (dto.lockedFromOptimization
                && dto.lockedWindowDistances != null
                && dto.lockedWindowAvgServeTime != null) {
            CanteenConfig.updateWindowConfigs(
                    resizeLockedWindowDistances(dto.lockedWindowDistances, dto.windowCount),
                    resizeLockedServeTimes(dto.lockedWindowAvgServeTime, dto.windowCount)
            );
        } else {
            CanteenConfig.initWindowsConfig(dto.windowCount);
        }

        CanteenConfig.PROB_SOLO = dto.probSolo;
        double remain = 1.0 - dto.probSolo;
        CanteenConfig.PROB_DUO = remain * 0.5;
        CanteenConfig.PROB_TRIO = remain * 0.3;
        CanteenConfig.PROB_TEAM = 1.0 - dto.probSolo - CanteenConfig.PROB_DUO - CanteenConfig.PROB_TRIO;

        CanteenConfig.validate();
    }

    private static int[] resizeLockedWindowDistances(int[] lockedDistances, int windowCount) {
        int[] source = lockedDistances == null || lockedDistances.length == 0
                ? CanteenConfig.DEFAULT_WINDOW_DISTANCES
                : lockedDistances;
        int[] result = new int[windowCount];
        for (int i = 0; i < windowCount; i++) {
            if (i < source.length) {
                result[i] = source[i];
            } else {
                int previous = i == 0 ? 10 : result[i - 1];
                result[i] = previous + 5;
            }
        }
        return result;
    }

    private static int[] resizeLockedServeTimes(int[] lockedServeTimes, int windowCount) {
        int[] source = lockedServeTimes == null || lockedServeTimes.length == 0
                ? CanteenConfig.DEFAULT_WINDOW_AVG_SERVE_TIME
                : lockedServeTimes;
        int[] result = new int[windowCount];
        for (int i = 0; i < windowCount; i++) {
            result[i] = Math.max(1, source[i % source.length]);
        }
        return result;
    }

    private static String displayMealPeriod(String code) {
        if ("breakfast".equals(code)) {
            return "早餐";
        }
        if ("dinner".equals(code)) {
            return "晚餐";
        }
        return "午餐";
    }

    private static JPanel createLogAreaPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("实时日志"));


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

    private static String formatTime(long elapsedSeconds) {
        long total = timeBaseSeconds + elapsedSeconds;
        long hh = (total / 3600) % 24;
        long mm = (total % 3600) / 60;
        long ss = total % 60;
        return String.format("%02d:%02d:%02d", hh, mm, ss);
    }

    private static void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            logTextArea.append(msg + "\n");
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    @Override
    public void onStudentArrived(int studentId, int groupId, long time) {
        SwingUtilities.invokeLater(() -> {
            logTextArea.append(String.format(">>> [%s] 学生 %03d（组 %d）抵达食堂%n", formatTime(time), studentId, groupId));
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    @Override
    public void onStudentQueuedAtWindow(int studentId, int groupId, int windowIndex, int queueLength, long time) {
        SwingUtilities.invokeLater(() -> {
            logTextArea.append(String.format(">>> [%s] 学生 %03d（组 %d）到窗口 %d 排队打饭（当前等候: %d人）%n",
                    formatTime(time), studentId, groupId, windowIndex + 1, queueLength));
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
    public void onStudentSeatedAtTable(int studentId, int groupId, int tableIndex, long time) {
        SwingUtilities.invokeLater(() -> {
            logTextArea.append(String.format(">>> [%s] 学生 %03d（组 %d）在桌 %02d 入座就餐%n", formatTime(time), studentId, groupId, tableIndex + 1));
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    @Override
    public void onStudentLeft(int studentId, int groupId, int tableIndex, String reason, long time) {
        SwingUtilities.invokeLater(() -> {
            if (tableIndex >= 0) {
                logTextArea.append(String.format(">>> [%s] 学生 %03d（组 %d）离开桌 %02d（%s）%n", formatTime(time), studentId, groupId, tableIndex + 1, reason));
            } else {
                logTextArea.append(String.format(">>> [%s] 学生 %03d（组 %d）放弃离开（%s）%n", formatTime(time), studentId, groupId, reason));
            }
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    @Override
    public void onPhaseChanged(String phaseName, String label, long currentTime) {
        SwingUtilities.invokeLater(() -> {
            // 根据餐段名称设置自然时间基准
            switch (phaseName) {
                case "早餐": timeBaseSeconds =  6 * 3600L + 30 * 60; break;  // 06:30
                case "午餐": timeBaseSeconds = 11 * 3600L;            break;  // 11:00
                case "晚餐": timeBaseSeconds = 17 * 3600L;            break;  // 17:00
                default:     timeBaseSeconds = 0;                     break;
            }
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
            appendLog(">>> [阶段] " + label + " (" + formatTime(currentTime) + ")");
        });
    }

    @Override
    public void onSimulationFinished() {
        SwingUtilities.invokeLater(() -> {
            startButton.setEnabled(replayPresetConfig != null && replayPresetConfig.lockedFromOptimization);
            stopButton.setEnabled(false);
            phaseLabel.setText(" ● 仿真结束");
            phaseLabel.setForeground(ColorTheme.TEXT_SECONDARY);
            logTextArea.append(">>> [系统] 本次仿真已结束。\n");
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
            JOptionPane.showMessageDialog(this, "本次仿真已圆满结束！");
        });
    }
}
