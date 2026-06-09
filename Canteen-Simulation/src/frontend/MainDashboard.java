package frontend;

import com.formdev.flatlaf.FlatLightLaf;
import backend.config.CanteenConfig;
import backend.dto.SimulationSnapshot;
import backend.engine.SimulationEngine;
import backend.model.Student;
import backend.module.ArrivalModule;
import backend.optimize.SimRunResult;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
public class MainDashboard extends JFrame implements SimulationEventListener {

    private static DiningAreaPanel myDiningPanel;
    private static QueueAreaPanel myQueuePanel;
    private static KpiDashboardPanel kpiDashboardPanel;
    private static WaitingSeatPanel waitingSeatPanel;
    private static LeavingAreaPanel leavingAreaPanel;
    private static TrendGraphPanel trendGraphPanel;
    private static RecommendationCardPanel recommendationCardPanel;
    private static ParameterPanel parameterPanel;
    private static AnalysisDashboardPanel analysisDashboardPanel;
    private static StatusFlowPanel statusFlowPanel;
    private static DiningAreaPanel expandedDiningPanel;
    private static QueueAreaPanel expandedQueuePanel;
    private static WaitingSeatPanel expandedWaitingSeatPanel;
    private static LeavingAreaPanel expandedLeavingAreaPanel;
    private static StatusFlowPanel expandedStatusFlowPanel;
    private static JDialog expandedSimulationDialog;

    private static JButton startButton;
    private static JButton manualSimulationPresetButton;
    private static JButton clearPresetButton;
    private static JButton stopButton;
    private static JLabel phaseLabel;
    private static final String VIEW_OPTIMIZATION = "optimization";
    private static final String VIEW_SIMULATION = "simulation";
    private static CardLayout dashboardCardLayout;
    private static JPanel dashboardCards;
    private static SimulationConfigDTO simulationPresetConfig;
    private static SimRunResult latestOptimizationContext;
    private static SimRunResult latestOptimizationCurrent;
    private static List<SimRunResult> latestOptimizationTopKResults = Collections.emptyList();
    private static List<SimRunResult> latestOptimizationAllResults = Collections.emptyList();
    private static Path latestSimulationReportPath;

    private static MainDashboard frame;

    private static Thread simulationThread = null;
    private static SimulationEngine simulationEngine = null;

    private static javax.swing.Timer delayTimer;
    private static javax.swing.Timer uiRefreshTimer;
    private static final int UI_REFRESH_INTERVAL_MS = 100;
    private static final int MAX_LOG_CHARACTERS = 100_000;
    private static final Object UI_EVENT_LOCK = new Object();
    private static final StringBuilder pendingLogText = new StringBuilder();
    private static final Map<Integer, Integer> pendingQueueLengths = new LinkedHashMap<>();
    private static final Map<Integer, int[]> pendingTableStates = new LinkedHashMap<>();
    private static SimulationSnapshot pendingSnapshot;
    private static SimulationSnapshot latestSnapshot;
    /** 当前餐段的起始秒数（分钟-of-day * 60），用于将仿真 tick 转为自然时间 */
    private static long timeBaseSeconds = 0;

    public static void main(String[] args) {
        FlatLightLaf.setup();
        installGlobalFonts();
        // 在 setup() 之后紧接着写
        UIManager.put( "Button.arc", 15 );       // 按钮圆角
        UIManager.put( "Component.arc", 15 );    // 输入框、下拉框圆角
        UIManager.put( "TextComponent.arc", 15 );
        UIManager.put( "ScrollBar.thumbArc", 999 ); // 药丸型滚动条

        SwingUtilities.invokeLater(MainDashboard::createAndShowGUI);
    }

    private static void installGlobalFonts() {
        Font baseFont = ColorTheme.font(Font.PLAIN, 13);
        java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof Font) {
                UIManager.put(key, baseFont);
            }
        }
        UIManager.put("Button.font", ColorTheme.font(Font.BOLD, 14));
        UIManager.put("Label.font", baseFont);
        UIManager.put("TextField.font", ColorTheme.font(Font.PLAIN, 14));
        UIManager.put("ComboBox.font", ColorTheme.font(Font.PLAIN, 14));
        UIManager.put("Table.font", ColorTheme.font(Font.PLAIN, 12));
        UIManager.put("TableHeader.font", ColorTheme.font(Font.BOLD, 12));
    }

    private static void styleSplitPane(JSplitPane splitPane) {
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setBackground(ColorTheme.BG_MAIN);
        splitPane.setOpaque(true);

        if (splitPane.getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI) {
            javax.swing.plaf.basic.BasicSplitPaneUI ui =
                    (javax.swing.plaf.basic.BasicSplitPaneUI) splitPane.getUI();

            if (ui.getDivider() != null) {
                ui.getDivider().setBackground(ColorTheme.BG_MAIN);
                ui.getDivider().setBorder(BorderFactory.createEmptyBorder());
            }
        }
    }

    private static void createAndShowGUI() {
        frame = new MainDashboard();
        frame.setTitle("北京交通大学就餐仿真系统 - 总控台大屏");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1700, 960);
        frame.setMinimumSize(new Dimension(1280, 760));
        frame.setLayout(new BorderLayout(0, 0));

        frame.add(createViewSwitchPanel(), BorderLayout.NORTH);
        frame.getContentPane().setBackground(ColorTheme.BG_MAIN);

        dashboardCardLayout = new CardLayout();
        dashboardCards = new JPanel(dashboardCardLayout);
        dashboardCards.setBackground(ColorTheme.BG_MAIN);
        dashboardCards.add(new OptimizationPanel(MainDashboard::applyOptimizationPreset,
                MainDashboard::rememberOptimizationContext,
                MainDashboard::rememberOptimizationSummary), VIEW_OPTIMIZATION);
        dashboardCards.add(createSimulationDashboardPanel(), VIEW_SIMULATION);
        frame.add(dashboardCards, BorderLayout.CENTER);
        dashboardCardLayout.show(dashboardCards, VIEW_SIMULATION);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        startUiRefreshTimer();
    }

    private static JPanel createSimulationDashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(ColorTheme.BG_MAIN);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        parameterPanel = new ParameterPanel(new ParameterPanel.Actions() {
            @Override
            public void startSimulation(SimulationConfigDTO dto) {
                simulationPresetConfig = dto.lockedFromOptimization ? dto : null;
                updatePresetButtons();
                startSimulationWithConfig(dto);
            }

            @Override
            public void stopSimulation() {
                stopCurrentSimulation();
            }

            @Override
            public void openOptimizer() {
                dashboardCardLayout.show(dashboardCards, VIEW_OPTIMIZATION);
            }

            @Override
            public void chooseOptimizationPreset() {
                openManualSimulationPresetDialog();
            }

            @Override
            public void clearPreset() {
                clearOptimizationPreset();
            }

            @Override
            public void parametersChanged() {
                markParametersChanged();
            }
        });
        startButton = parameterPanel.getStartButton();
        stopButton = parameterPanel.getStopButton();
        manualSimulationPresetButton = parameterPanel.getChoosePresetButton();
        clearPresetButton = parameterPanel.getClearPresetButton();
        phaseLabel = parameterPanel.getPhaseLabel();

        analysisDashboardPanel = new AnalysisDashboardPanel();

        panel.add(parameterPanel, BorderLayout.WEST);
        panel.add(createSimulationScenePanel(), BorderLayout.CENTER);
        panel.add(createDecisionPanel(), BorderLayout.EAST);
        panel.add(analysisDashboardPanel, BorderLayout.SOUTH);
        return panel;
    }

    private static JPanel createSimulationScenePanel() {
        myDiningPanel = new DiningAreaPanel(30);
        myDiningPanel.setBackground(ColorTheme.BG_CARD);
        myQueuePanel = new QueueAreaPanel(5);
        myQueuePanel.setPreferredSize(new Dimension(240, 0));
        myQueuePanel.setBackground(ColorTheme.BG_CARD);
        waitingSeatPanel = new WaitingSeatPanel();
        leavingAreaPanel = new LeavingAreaPanel();

        JPanel scene = new JPanel(new BorderLayout(10, 10));
        scene.setBackground(ColorTheme.BG_CARD);
        scene.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        JPanel header = new JPanel(new BorderLayout(8, 4));
        header.setOpaque(false);
        JLabel title = new JLabel("2D 仿真场景");
        title.setForeground(ColorTheme.TEXT_PRIMARY);
        title.setFont(ColorTheme.font(Font.BOLD, 18));
        JLabel subtitle = new JLabel("窗口队列、等座区、桌位矩阵和离开区均由后端快照实时驱动");
        subtitle.setForeground(ColorTheme.TEXT_SECONDARY);
        subtitle.setFont(ColorTheme.font(Font.PLAIN, 12));
        JPanel titleBox = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBox.setOpaque(false);
        titleBox.add(title);
        titleBox.add(subtitle);
        header.add(titleBox, BorderLayout.WEST);
        header.add(createStatusLegendPanel(), BorderLayout.CENTER);
        JButton expandButton = new JButton("放大");
        styleSceneToolButton(expandButton);
        expandButton.setToolTipText("打开更大的实时 2D 仿真场景");
        expandButton.addActionListener(e -> showExpandedSimulationScene());
        header.add(expandButton, BorderLayout.EAST);
        scene.add(header, BorderLayout.NORTH);

        JPanel mapPanel = new JPanel(new BorderLayout(10, 0));
        mapPanel.setOpaque(false);
        mapPanel.add(myQueuePanel, BorderLayout.WEST);
        mapPanel.add(myDiningPanel, BorderLayout.CENTER);

        JPanel sceneBody = new JPanel(new BorderLayout(0, 8));
        sceneBody.setOpaque(false);
        sceneBody.add(createEntrancePanel(), BorderLayout.NORTH);
        sceneBody.add(mapPanel, BorderLayout.CENTER);
        scene.add(sceneBody, BorderLayout.CENTER);

        JPanel flowStatusPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        flowStatusPanel.setOpaque(false);
        flowStatusPanel.add(waitingSeatPanel);
        flowStatusPanel.add(leavingAreaPanel);

        statusFlowPanel = new StatusFlowPanel();
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 8));
        bottomPanel.setOpaque(false);
        bottomPanel.add(statusFlowPanel, BorderLayout.NORTH);
        bottomPanel.add(flowStatusPanel, BorderLayout.CENTER);
        scene.add(bottomPanel, BorderLayout.SOUTH);
        return scene;
    }

    private static void showExpandedSimulationScene() {
        if (expandedSimulationDialog != null && expandedSimulationDialog.isDisplayable()) {
            expandedSimulationDialog.toFront();
            expandedSimulationDialog.requestFocus();
            return;
        }

        expandedSimulationDialog = new JDialog(frame, "实时 2D 仿真场景", false);
        expandedSimulationDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        expandedSimulationDialog.setContentPane(createExpandedSimulationSceneContent());
        expandedSimulationDialog.setSize(1180, 820);
        expandedSimulationDialog.setLocationRelativeTo(frame);
        expandedSimulationDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                expandedDiningPanel = null;
                expandedQueuePanel = null;
                expandedWaitingSeatPanel = null;
                expandedLeavingAreaPanel = null;
                expandedStatusFlowPanel = null;
                expandedSimulationDialog = null;
            }
        });

        if (latestSnapshot != null) {
            applySnapshotToScene(latestSnapshot, expandedQueuePanel, expandedDiningPanel,
                    expandedWaitingSeatPanel, expandedLeavingAreaPanel, expandedStatusFlowPanel);
        }
        expandedSimulationDialog.setVisible(true);
    }

    private static JPanel createExpandedSimulationSceneContent() {
        expandedDiningPanel = new DiningAreaPanel(Math.max(1, CanteenConfig.TOTAL_TABLES));
        expandedDiningPanel.setBackground(ColorTheme.BG_CARD);
        expandedQueuePanel = new QueueAreaPanel(Math.max(1, CanteenConfig.getWindowCount()));
        expandedQueuePanel.setPreferredSize(new Dimension(320, 0));
        expandedQueuePanel.setBackground(ColorTheme.BG_CARD);
        expandedWaitingSeatPanel = new WaitingSeatPanel();
        expandedLeavingAreaPanel = new LeavingAreaPanel();
        expandedStatusFlowPanel = new StatusFlowPanel();

        JPanel scene = new JPanel(new BorderLayout(12, 12));
        scene.setBackground(ColorTheme.BG_MAIN);
        scene.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        JLabel title = new JLabel("实时 2D 仿真场景");
        title.setForeground(ColorTheme.TEXT_PRIMARY);
        title.setFont(ColorTheme.font(Font.BOLD, 20));
        header.add(title, BorderLayout.WEST);
        header.add(createStatusLegendPanel(), BorderLayout.CENTER);

        JPanel topPanel = new JPanel(new BorderLayout(0, 10));
        topPanel.setOpaque(false);
        topPanel.add(header, BorderLayout.NORTH);
        topPanel.add(createEntrancePanel(), BorderLayout.SOUTH);
        scene.add(topPanel, BorderLayout.NORTH);

        JPanel mapPanel = new JPanel(new BorderLayout(12, 0));
        mapPanel.setOpaque(false);
        mapPanel.add(expandedQueuePanel, BorderLayout.WEST);
        mapPanel.add(expandedDiningPanel, BorderLayout.CENTER);

        JPanel flowStatusPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        flowStatusPanel.setOpaque(false);
        flowStatusPanel.add(expandedWaitingSeatPanel);
        flowStatusPanel.add(expandedLeavingAreaPanel);

        JPanel bottomPanel = new JPanel(new BorderLayout(0, 10));
        bottomPanel.setOpaque(false);
        bottomPanel.add(expandedStatusFlowPanel, BorderLayout.NORTH);
        bottomPanel.add(flowStatusPanel, BorderLayout.CENTER);

        scene.add(mapPanel, BorderLayout.CENTER);
        scene.add(bottomPanel, BorderLayout.SOUTH);
        return scene;
    }

    private static void styleSceneToolButton(JButton button) {
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setBackground(ColorTheme.BG_CONTROL);
        button.setForeground(ColorTheme.ACCENT_BLUE);
        button.setFont(ColorTheme.font(Font.BOLD, 13));
        button.putClientProperty(
                "FlatLaf.style",
                "arc: 16; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; margin: 6,14,6,14"
        );
    }

    private static JPanel createEntrancePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JLabel entrance = new JLabel("入口流转：学生进入食堂后分流到窗口队列");
        entrance.setForeground(ColorTheme.TEXT_PRIMARY);
        entrance.setFont(ColorTheme.font(Font.BOLD, 13));
        JLabel hint = new JLabel("请配置参数并点击开始仿真");
        hint.setForeground(ColorTheme.TEXT_SECONDARY);
        hint.setFont(ColorTheme.font(Font.PLAIN, 12));
        panel.add(entrance, BorderLayout.WEST);
        panel.add(hint, BorderLayout.EAST);
        return panel;
    }

    private static JPanel createViewSwitchPanel() {
        JPanel panel = new JPanel(new BorderLayout(16, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gradient = new GradientPaint(
                        0, 0, new Color(0, 82, 184),
                        getWidth(), 0, new Color(22, 119, 255)
                );
                g2.setPaint(gradient);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        panel.setPreferredSize(new Dimension(0, 68));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));

        JButton optimizeViewButton = new JButton("寻优模式");
        JButton simulationViewButton = new JButton("实时仿真");
        JButton reportButton = new JButton("导出报告");
        JButton adminButton = new JButton("管理员");
        styleNavButton(optimizeViewButton, new Color(235, 245, 255));
        styleNavButton(simulationViewButton, Color.WHITE);
        styleNavButton(reportButton, new Color(235, 245, 255));
        styleNavButton(adminButton, new Color(235, 245, 255));

        optimizeViewButton.addActionListener(e -> dashboardCardLayout.show(dashboardCards, VIEW_OPTIMIZATION));
        simulationViewButton.addActionListener(e -> dashboardCardLayout.show(dashboardCards, VIEW_SIMULATION));
        reportButton.addActionListener(e -> exportLatestSimulationReport());

        JLabel title = new JLabel("北京交通大学就餐仿真与资源配置优化系统");
        title.setForeground(Color.WHITE);
        title.setFont(ColorTheme.font(Font.BOLD, 22));
        JLabel subtitle = new JLabel("Cafeteria Simulation Decision Dashboard");
        subtitle.setForeground(new Color(219, 234, 254));
        subtitle.setFont(ColorTheme.font(Font.PLAIN, 12));
        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 2));
        titlePanel.setOpaque(false);
        titlePanel.add(title);
        titlePanel.add(subtitle);

        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        navPanel.setOpaque(false);
        navPanel.add(simulationViewButton);
        navPanel.add(optimizeViewButton);
        navPanel.add(reportButton);
        navPanel.add(adminButton);

        panel.add(titlePanel, BorderLayout.WEST);
        panel.add(navPanel, BorderLayout.EAST);
        return panel;
    }

    private static JPanel createStatusLegendPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        panel.setBackground(ColorTheme.BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        panel.add(createLegendItem("低压", ColorTheme.ACCENT_GREEN));
        panel.add(createLegendItem("中压", ColorTheme.ACCENT_YELLOW));
        panel.add(createLegendItem("高压", new Color(249, 115, 22)));
        panel.add(createLegendItem("过载", ColorTheme.ACCENT_RED));
        panel.add(createLegendItem("等座", ColorTheme.ACCENT_PURPLE));
        panel.add(createLegendItem("占用", ColorTheme.ACCENT_BLUE));
        JLabel mode = new JLabel("大客流采用聚合快照渲染，不绘制单个学生");
        mode.setForeground(ColorTheme.TEXT_SECONDARY);
        panel.add(mode);
        return panel;
    }

    private static JPanel createLegendItem(String label, Color color) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        item.setOpaque(false);
        JLabel dot = new JLabel("●");
        dot.setForeground(color);
        JLabel text = new JLabel(label);
        text.setForeground(ColorTheme.TEXT_SECONDARY);
        item.add(dot);
        item.add(text);
        return item;
    }

    private static void styleNavButton(JButton button, Color accentColor) {
        button.setFocusPainted(false);
        button.setBackground(accentColor);
        button.setForeground(ColorTheme.ACCENT_BLUE);
        button.setBorderPainted(false);
        button.setContentAreaFilled(true);
        button.putClientProperty(
                "FlatLaf.style",
                "arc: 18; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; margin: 7,18,7,18"
        );
    }

    private static void exportLatestSimulationReport() {
        Path reportPath = resolveLatestReportPath();
        if (reportPath == null) {
            String message = simulationThread != null && simulationThread.isAlive()
                    ? "当前仿真仍在生成报告，请在仿真结束后再导出。"
                    : "还没有可导出的仿真报告，请先完成一次仿真。";
            JOptionPane.showMessageDialog(frame, message, "暂无报告", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择报告导出文件夹");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        int result = chooser.showOpenDialog(frame);
        if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }

        Path targetDir = chooser.getSelectedFile().toPath();
        try {
            Files.createDirectories(targetDir);
            List<Path> exportedFiles = copyReportBundle(reportPath, targetDir);
            Path exportedHtml = targetDir.resolve(reportPath.getFileName());
            latestSimulationReportPath = exportedHtml;
            int openResult = JOptionPane.showConfirmDialog(
                    frame,
                    "报告已导出到：\n" + targetDir.toAbsolutePath()
                            + "\n\n共导出 " + exportedFiles.size() + " 个文件，是否立即打开 HTML 报告？",
                    "导出完成",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE
            );
            if (openResult == JOptionPane.YES_OPTION) {
                openReportFile(exportedHtml);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame,
                    "导出报告失败：" + ex.getMessage(),
                    "导出失败",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Path resolveLatestReportPath() {
        Path engineReportPath = simulationEngine == null ? null : simulationEngine.getReportFilePath();
        if (isUsableReport(engineReportPath)) {
            return engineReportPath;
        }
        if (isUsableReport(latestSimulationReportPath)) {
            return latestSimulationReportPath;
        }
        return findLatestReportFile();
    }

    private static boolean isUsableReport(Path path) {
        return path != null && Files.isRegularFile(path);
    }

    private static Path findLatestReportFile() {
        Path outputDir = Paths.get("simulation-output").toAbsolutePath();
        if (!Files.isDirectory(outputDir)) {
            return null;
        }

        Path latest = null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputDir, "simulation_report_*.html")) {
            for (Path file : stream) {
                if (latest == null || Files.getLastModifiedTime(file).compareTo(Files.getLastModifiedTime(latest)) > 0) {
                    latest = file;
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return latest;
    }

    private static List<Path> copyReportBundle(Path reportPath, Path targetDir) throws IOException {
        List<Path> exportedFiles = new ArrayList<>();
        Path sourceDir = reportPath.getParent();
        String timestamp = reportTimestamp(reportPath.getFileName().toString());
        List<Path> sources = new ArrayList<>();
        sources.add(reportPath);
        if (timestamp != null && sourceDir != null) {
            addIfExists(sources, sourceDir.resolve("simulation_summary_" + timestamp + ".txt"));
            addIfExists(sources, sourceDir.resolve("simulation_timeline_" + timestamp + ".csv"));
            addIfExists(sources, sourceDir.resolve("simulation_events_" + timestamp + ".csv"));
        }

        for (Path source : sources) {
            Path target = targetDir.resolve(source.getFileName());
            if (!source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            exportedFiles.add(target);
        }
        return exportedFiles;
    }

    private static void addIfExists(List<Path> sources, Path path) {
        if (Files.isRegularFile(path)) {
            sources.add(path);
        }
    }

    private static String reportTimestamp(String fileName) {
        String prefix = "simulation_report_";
        String suffix = ".html";
        if (fileName == null || !fileName.startsWith(prefix) || !fileName.endsWith(suffix)) {
            return null;
        }
        return fileName.substring(prefix.length(), fileName.length() - suffix.length());
    }

    private static void openReportFile(Path reportPath) {
        if (reportPath == null || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().browse(reportPath.toUri());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame,
                    "报告已导出，但自动打开失败：" + ex.getMessage(),
                    "打开失败",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void rememberOptimizationContext(SimRunResult result) {
        rememberOptimizationSummary(null, result, null, null);
    }

    private static void rememberOptimizationSummary(SimRunResult current,
                                                    SimRunResult best,
                                                    List<SimRunResult> topKResults,
                                                    List<SimRunResult> allResults) {
        latestOptimizationCurrent = current == null ? null : current.copyBasic();
        latestOptimizationContext = best == null ? null : best.copyBasic();
        if (topKResults != null) {
            latestOptimizationTopKResults = copyResults(topKResults);
        }
        if (allResults != null) {
            latestOptimizationAllResults = copyResults(allResults);
        }
        if (recommendationCardPanel != null && latestOptimizationContext != null) {
            recommendationCardPanel.updateResult(latestOptimizationCurrent, latestOptimizationContext);
        }
        if (analysisDashboardPanel != null) {
            analysisDashboardPanel.updateOptimization(
                    latestOptimizationCurrent,
                    latestOptimizationContext,
                    latestOptimizationTopKResults,
                    latestOptimizationAllResults
            );
        }
        if (manualSimulationPresetButton != null) {
            manualSimulationPresetButton.setEnabled(latestOptimizationContext != null);
            manualSimulationPresetButton.setToolTipText(latestOptimizationContext == null
                    ? "请先完成一次寻优"
                    : "在最近一次寻优范围内手动选择仿真参数");
        }
    }

    private static List<SimRunResult> copyResults(List<SimRunResult> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<SimRunResult> copies = new ArrayList<>(source.size());
        for (SimRunResult result : source) {
            if (result != null) {
                copies.add(result.copyBasic());
            }
        }
        return copies;
    }

    private static JPanel createDecisionPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setPreferredSize(new Dimension(410, 0));
        panel.setMinimumSize(new Dimension(390, 0));
        panel.setBackground(ColorTheme.BG_MAIN);

        recommendationCardPanel = new RecommendationCardPanel();
        kpiDashboardPanel = new KpiDashboardPanel();

        JPanel kpiCard = new JPanel(new BorderLayout(0, 8));
        kpiCard.setBackground(ColorTheme.BG_CARD);
        kpiCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        JLabel title = new JLabel("实时核心指标");
        title.setForeground(ColorTheme.TEXT_PRIMARY);
        title.setFont(ColorTheme.font(Font.BOLD, 15));
        kpiCard.add(title, BorderLayout.NORTH);
        kpiCard.add(kpiDashboardPanel, BorderLayout.CENTER);

        panel.add(recommendationCardPanel, BorderLayout.NORTH);
        panel.add(kpiCard, BorderLayout.CENTER);
        return panel;
    }

    private static void applyOptimizationPreset(SimRunResult result) {
        latestOptimizationContext = result.copyBasic();
        SimulationConfigDTO preset = buildLockedSimulationConfig(result);
        simulationPresetConfig = preset;

        if (startButton != null) {
            startButton.setEnabled(true);
            startButton.setToolTipText("使用已导入的最佳方案启动仿真");
        }

        if (manualSimulationPresetButton != null) {
            manualSimulationPresetButton.setEnabled(true);
        }

        if (clearPresetButton != null) {
            clearPresetButton.setEnabled(true);
            clearPresetButton.setToolTipText("清除当前导入的寻优方案，恢复自定义初始化");
        }

        dashboardCardLayout.show(dashboardCards, VIEW_SIMULATION);
        if (parameterPanel != null) {
            parameterPanel.applyPreset(preset);
        }
        if (recommendationCardPanel != null) {
            recommendationCardPanel.updateResult(latestOptimizationCurrent, latestOptimizationContext);
        }
        if (analysisDashboardPanel != null) {
            analysisDashboardPanel.updateOptimization(
                    latestOptimizationCurrent,
                    latestOptimizationContext,
                    latestOptimizationTopKResults,
                    latestOptimizationAllResults
            );
        }
        JOptionPane.showMessageDialog(frame,
                "已导入仿真参数：窗口 " + result.windowCount + "，桌子 " + result.tableCount
                        + "。参数已锁定，请确认后运行。",
                "导入成功",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static SimulationConfigDTO buildLockedSimulationConfig(SimRunResult result) {
        SimulationConfigDTO preset = new SimulationConfigDTO();
        boolean hasOptimizationMetadata = result.requestedPopulation > 0;
        preset.totalTables = result.tableCount;
        preset.windowCount = result.windowCount;
        preset.totalStudents = result.requestedPopulation > 0 ? result.requestedPopulation : preset.totalStudents;
        preset.openDuration = result.openDuration > 0 ? result.openDuration : CanteenConfig.OPEN_DURATION;
        preset.probSolo = hasOptimizationMetadata ? result.probSolo : CanteenConfig.PROB_SOLO;
        preset.randomSeed = result.randomSeed == 0L ? preset.randomSeed : result.randomSeed;
        preset.avgMealPrice = result.avgMealPrice > 0.0 ? result.avgMealPrice : CanteenConfig.AVG_MEAL_PRICE;
        preset.windowCostPerHour = result.windowCostPerHour > 0.0 ? result.windowCostPerHour : CanteenConfig.WINDOW_COST_PER_HOUR;
        preset.tableCost = result.tableCost > 0.0 ? result.tableCost : CanteenConfig.TABLE_COST;
        preset.lostStudentPenalty = result.lostStudentPenalty > 0.0 ? result.lostStudentPenalty : CanteenConfig.LOST_STUDENT_PENALTY;
        preset.breakfastPopulationRatio = CanteenConfig.BREAKFAST_POPULATION_RATIO;
        preset.lunchPopulationRatio = CanteenConfig.LUNCH_POPULATION_RATIO;
        preset.dinnerPopulationRatio = CanteenConfig.DINNER_POPULATION_RATIO;
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

    private static void styleActionButton(JButton button, Color bg, Color fg) {
        button.setFocusPainted(false);
        button.setBackground(bg);
        button.setForeground(fg);
        button.setBorderPainted(false);
        button.setContentAreaFilled(true);
        button.putClientProperty(
                "FlatLaf.style",
                "arc: 18; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; margin: 8,18,8,18"
        );
    }

    private static void clearOptimizationPreset() {
        simulationPresetConfig = null;
        if (parameterPanel != null) {
            parameterPanel.clearPresetState();
        }

        if (startButton != null) {
            startButton.setEnabled(true);
            startButton.setToolTipText("直接配置参数并启动仿真");
        }

        if (clearPresetButton != null) {
            clearPresetButton.setEnabled(false);
            clearPresetButton.setToolTipText("当前未导入寻优方案");
        }

        if (manualSimulationPresetButton != null) {
            manualSimulationPresetButton.setEnabled(latestOptimizationContext != null);
            manualSimulationPresetButton.setToolTipText(latestOptimizationContext == null
                    ? "请先完成一次寻优"
                    : "在最近一次寻优范围内手动选择仿真参数");
        }

        if (phaseLabel != null) {
            phaseLabel.setText("等待运行");
            phaseLabel.setForeground(ColorTheme.TEXT_SECONDARY);
        }

        appendLog(">>> [系统] 已恢复自定义初始化模式。");
    }

    private static void markParametersChanged() {
        boolean hadRecommendation = latestOptimizationContext != null;
        String message = hadRecommendation
                ? "参数已变化，请重新仿真或启动寻优；推荐方案已过期，请重新寻优"
                : "参数已变化，请重新仿真或启动寻优";
        simulationPresetConfig = null;
        latestOptimizationContext = null;
        latestOptimizationCurrent = null;
        latestOptimizationTopKResults = Collections.emptyList();
        latestOptimizationAllResults = Collections.emptyList();
        if (recommendationCardPanel != null) {
            recommendationCardPanel.showMessage(message);
        }
        if (analysisDashboardPanel != null) {
            analysisDashboardPanel.markParametersChanged(message);
        }
        if (phaseLabel != null) {
            phaseLabel.setText("参数已变化");
            phaseLabel.setForeground(ColorTheme.ACCENT_YELLOW);
        }
        updatePresetButtons();
    }

    private static SimRunResult currentResultFromSnapshot(SimulationSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        SimRunResult result = new SimRunResult();
        result.windowCount = CanteenConfig.getWindowCount();
        result.tableCount = CanteenConfig.TOTAL_TABLES;
        result.requestedPopulation = snapshot.totalStudents;
        result.finishRate = snapshot.completionRate;
        result.abandonedStudents = snapshot.abandonedCount;
        result.netProfit = snapshot.netProfit;
        result.avgWaitTimeSeconds = snapshot.avgQueueWaitSeconds;
        result.avgWaitTimeMinutes = snapshot.avgQueueWaitSeconds / 60.0;
        result.avgSeatWaitTimeSeconds = snapshot.avgSeatWaitSeconds;
        result.avgSeatWaitTimeMinutes = snapshot.avgSeatWaitSeconds / 60.0;
        result.seatUtilization = snapshot.seatUtilizationRate;
        result.windowUtilization = snapshot.windowUtilizationRate;
        return result;
    }

    private static void updatePresetButtons() {
        if (clearPresetButton != null) {
            clearPresetButton.setEnabled(simulationPresetConfig != null);
            clearPresetButton.setToolTipText(simulationPresetConfig == null
                    ? "当前未导入寻优方案"
                    : "清除当前导入的寻优方案，恢复自定义初始化");
        }
        if (manualSimulationPresetButton != null) {
            manualSimulationPresetButton.setEnabled(latestOptimizationContext != null);
            manualSimulationPresetButton.setToolTipText(latestOptimizationContext == null
                    ? "请先完成一次寻优"
                    : "在最近一次寻优范围内手动选择仿真参数");
        }
    }

    private static void startSimulationWithConfig(SimulationConfigDTO dto) {
        try {
            applyConfig(dto);

            backend.config.SimulationConfigRequest request = new backend.config.SimulationConfigRequest();
            request.setTableCount(dto.totalTables);
            request.setOpenDuration(dto.openDuration);
            request.setWindowCount(dto.windowCount);
            request.setRandomSeed(dto.randomSeed);
            request.setProbSolo(dto.probSolo);

            double remainder = 1.0 - dto.probSolo;
            double duo = remainder * 0.5;
            double trio = remainder * 0.3;
            double team = 1.0 - dto.probSolo - duo - trio;
            request.setProbDuo(duo);
            request.setProbTrio(trio);
            request.setProbTeam(team);
            request.setTotalPopulation(dto.totalStudents);
            request.setSimulationMode(dto.simulationMode);
            request.setMealPeriod(dto.mealPeriod);
            request.setBreakfastPopulationRatio(dto.breakfastPopulationRatio);
            request.setLunchPopulationRatio(dto.lunchPopulationRatio);
            request.setDinnerPopulationRatio(dto.dinnerPopulationRatio);
            request.setAvgMealPrice(dto.avgMealPrice);
            request.setWindowCostPerHour(dto.windowCostPerHour);
            request.setTableCost(dto.tableCost);
            request.setLostStudentPenalty(dto.lostStudentPenalty);
            if (dto.lockedFromOptimization
                    && dto.lockedWindowDistances != null
                    && dto.lockedWindowAvgServeTime != null) {
                request.setWindowDistances(resizeLockedWindowDistances(dto.lockedWindowDistances, dto.windowCount));
                request.setWindowAvgServeTime(resizeLockedServeTimes(dto.lockedWindowAvgServeTime, dto.windowCount));
            }

            backend.config.CanteenConfig.updateAllConfigs(request);

            resetSceneForConfig(dto);
            latestSimulationReportPath = null;
            if (analysisDashboardPanel != null) {
                analysisDashboardPanel.clear();
                analysisDashboardPanel.updateCurrentPlan(dto.windowCount, dto.totalTables);
                if (latestOptimizationContext != null) {
                    analysisDashboardPanel.updateRecommendation(
                            latestOptimizationContext,
                            latestOptimizationTopKResults,
                            latestOptimizationAllResults
                    );
                }
            }

            clearPendingUiEvents();
            if (phaseLabel != null) {
                phaseLabel.setText("准备启动");
                phaseLabel.setForeground(ColorTheme.ACCENT_YELLOW);
            }
            appendLog(">>> 初始化配置注入成功！桌数: " + dto.totalTables + " | 窗口: " + dto.windowCount);
            if ("fullDay".equals(dto.simulationMode)) {
                appendLog(">>> 仿真模式: 全天无缝聚合仿真 (早中晚连续时间轴)");
            } else {
                appendLog(">>> 仿真模式: 单时段仿真 | 目标餐段: " + displayMealPeriod(dto.mealPeriod));
            }
            appendLog(">>> 正在点火，启动正式仿真引擎...");

            if (startButton != null) {
                startButton.setEnabled(false);
            }
            if (stopButton != null) {
                stopButton.setEnabled(false);
                stopButton.setVisible(true);
            }

            delayTimer = new javax.swing.Timer(800, event -> {
                try {
                    appendLog(">>> 仿真引擎启动成功！正在生成复杂客流剧本...");
                    ArrivalModule arrivalModule = new ArrivalModule(backend.config.CanteenConfig.RANDOM_SEED);
                    backend.model.ArrivalGenerationResult result = arrivalModule.generateArrivalPlan(
                            backend.config.CanteenConfig.TOTAL_POPULATION,
                            backend.config.CanteenConfig.SIMULATION_MODE,
                            backend.config.CanteenConfig.MEAL_PERIOD
                    );

                    List<Student> students = result.getStudents();
                    appendLog(">>> 学生数据生成完成，共 " + students.size() + " 名学生。");

                    long timeScale = 20L;
                    simulationEngine = new SimulationEngine(
                            students, frame, timeScale,
                            result.getPhaseBoundaries());
                    simulationThread = new Thread(simulationEngine, "SimulationEngine");
                    simulationThread.start();

                    if (stopButton != null) {
                        stopButton.setEnabled(true);
                        stopButton.setVisible(true);
                    }
                } catch (Exception ex) {
                    appendLog(">>> [错误] 启动失败：" + ex.getMessage());
                    if (startButton != null) {
                        startButton.setEnabled(true);
                    }
                    if (stopButton != null) {
                        stopButton.setEnabled(false);
                        stopButton.setVisible(false);
                    }
                    ex.printStackTrace();
                }
            });

            delayTimer.setRepeats(false);
            delayTimer.start();
        } catch (Exception ex) {
            appendLog(">>> [错误] 配置非法：" + ex.getMessage());
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "配置错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void resetSceneForConfig(SimulationConfigDTO dto) {
        resetScenePanels(dto, myDiningPanel, myQueuePanel);
        resetScenePanels(dto, expandedDiningPanel, expandedQueuePanel);
        latestSnapshot = null;
    }

    private static void resetScenePanels(SimulationConfigDTO dto, DiningAreaPanel diningPanel, QueueAreaPanel queuePanel) {
        if (diningPanel != null) {
            diningPanel.updateTableCount(dto.totalTables);
            diningPanel.clearOccupancy();
        }
        if (queuePanel != null) {
            queuePanel.updateWindowCount(dto.windowCount);
            int peakFactor = (int) (dto.totalStudents * 0.08);
            int calculatedMax = Math.max(20, peakFactor / Math.max(1, dto.windowCount));
            queuePanel.setMaxQueueCapacity(Math.min(120, calculatedMax));
            queuePanel.clearQueues();
        }
    }

    private static void stopCurrentSimulation() {
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

        if (startButton != null) {
            startButton.setEnabled(true);
        }
        if (stopButton != null) {
            stopButton.setEnabled(false);
            stopButton.setVisible(false);
        }
        if (phaseLabel != null) {
            phaseLabel.setText("等待运行");
            phaseLabel.setForeground(ColorTheme.TEXT_SECONDARY);
        }
    }

    private static JPanel createConfigPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        panel.setBackground(ColorTheme.BG_CARD);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

        startButton = new JButton("▶ 配置并启动仿真");
        manualSimulationPresetButton = new JButton("从寻优范围选择参数");
        clearPresetButton = new JButton("恢复自定义初始化");
        stopButton = new JButton("■ 停止仿真");
        startButton.setEnabled(true);
        startButton.setToolTipText("直接配置参数并启动仿真");

        manualSimulationPresetButton.setEnabled(false);
        manualSimulationPresetButton.setToolTipText("请先完成一次寻优");

        clearPresetButton.setEnabled(false);
        clearPresetButton.setToolTipText("当前未导入寻优方案");

        stopButton.setEnabled(false);

        panel.add(manualSimulationPresetButton);
        panel.add(startButton);
        panel.add(clearPresetButton);
        panel.add(stopButton);

        styleActionButton(manualSimulationPresetButton, ColorTheme.BG_CONTROL, ColorTheme.TEXT_PRIMARY);
        styleActionButton(startButton, ColorTheme.ACCENT_CYAN, ColorTheme.BG_CARD);
        styleActionButton(clearPresetButton, ColorTheme.BG_CONTROL, ColorTheme.TEXT_PRIMARY);
        styleActionButton(stopButton, ColorTheme.BG_CONTROL, ColorTheme.TEXT_PRIMARY);

        phaseLabel = new JLabel(" ");
        phaseLabel.setFont(ColorTheme.font(Font.BOLD, 15));
        phaseLabel.setForeground(ColorTheme.ACCENT_YELLOW);
        phaseLabel.setOpaque(true);
        phaseLabel.setBackground(ColorTheme.BG_PANEL);
        phaseLabel.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        panel.add(phaseLabel);

        manualSimulationPresetButton.addActionListener(e -> openManualSimulationPresetDialog());

        startButton.addActionListener(e -> {
            SimulationConfigDialog configDialog = new SimulationConfigDialog(frame, simulationPresetConfig);
            configDialog.setVisible(true);

            if (!configDialog.isConfirmed()) {
                return;
            }

            SimulationConfigDTO dto = configDialog.getConfigData();
            simulationPresetConfig = dto.lockedFromOptimization ? dto : null;

            if (clearPresetButton != null) {
                clearPresetButton.setEnabled(simulationPresetConfig != null);
                clearPresetButton.setToolTipText(simulationPresetConfig == null
                        ? "当前未导入寻优方案"
                        : "清除当前导入的寻优方案，恢复自定义初始化");
            }

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
                request.setBreakfastPopulationRatio(dto.breakfastPopulationRatio);
                request.setLunchPopulationRatio(dto.lunchPopulationRatio);
                request.setDinnerPopulationRatio(dto.dinnerPopulationRatio);
                request.setAvgMealPrice(dto.avgMealPrice);
                request.setWindowCostPerHour(dto.windowCostPerHour);
                request.setTableCost(dto.tableCost);
                request.setLostStudentPenalty(dto.lostStudentPenalty);
                if (dto.lockedFromOptimization
                        && dto.lockedWindowDistances != null
                        && dto.lockedWindowAvgServeTime != null) {
                    request.setWindowDistances(resizeLockedWindowDistances(dto.lockedWindowDistances, dto.windowCount));
                    request.setWindowAvgServeTime(resizeLockedServeTimes(dto.lockedWindowAvgServeTime, dto.windowCount));
                }

                // 强制更新后端的全局配置
                backend.config.CanteenConfig.updateAllConfigs(request);

                resetSceneForConfig(dto);

                clearPendingUiEvents();
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

        clearPresetButton.addActionListener(e -> clearOptimizationPreset());

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

    private static void openManualSimulationPresetDialog() {
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
                "从寻优范围选择仿真参数",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            int windowCount = Integer.parseInt(windowField.getText().trim());
            int tableCount = Integer.parseInt(tableField.getText().trim());
            validateManualSimulationRange(windowCount, tableCount, latestOptimizationContext);

            SimRunResult selected = latestOptimizationContext.copyBasic();
            selected.windowCount = windowCount;
            selected.tableCount = tableCount;
            if (selected.baseRandomSeed != 0L && selected.repeatTimes > 0) {
                selected.randomSeed = deriveReplaySeed(selected.baseRandomSeed, selected.repeatTimes);
            }
            applyOptimizationPreset(selected);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(frame, "窗口数和桌子数必须是整数。", "格式错误", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "参数越界", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void validateManualSimulationRange(int windowCount, int tableCount, SimRunResult context) {
        if (windowCount < context.minWindowCount || windowCount > context.maxWindowCount) {
            throw new IllegalArgumentException("窗口数必须在寻优范围 "
                    + context.minWindowCount + " 到 " + context.maxWindowCount + " 之间。");
        }
        if (tableCount < context.minTableCount || tableCount > context.maxTableCount) {
            throw new IllegalArgumentException("桌子数必须在寻优范围 "
                    + context.minTableCount + " 到 " + context.maxTableCount + " 之间。");
        }
    }

    private static long deriveReplaySeed(long baseSeed, int repeatIndex) {
        long seed = baseSeed;
        seed ^= 0x9E3779B97F4A7C15L + repeatIndex * 1009L;
        seed ^= Long.rotateLeft(seed, 21);
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
        CanteenConfig.AVG_MEAL_PRICE = dto.avgMealPrice;
        CanteenConfig.WINDOW_COST_PER_HOUR = dto.windowCostPerHour;
        CanteenConfig.TABLE_COST = dto.tableCost;
        CanteenConfig.LOST_STUDENT_PENALTY = dto.lostStudentPenalty;
        CanteenConfig.BREAKFAST_POPULATION_RATIO = dto.breakfastPopulationRatio;
        CanteenConfig.LUNCH_POPULATION_RATIO = dto.lunchPopulationRatio;
        CanteenConfig.DINNER_POPULATION_RATIO = dto.dinnerPopulationRatio;

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

    private static String formatTime(long elapsedSeconds) {
        long total = timeBaseSeconds + elapsedSeconds;
        long hh = (total / 3600) % 24;
        long mm = (total % 3600) / 60;
        long ss = total % 60;
        return String.format("%02d:%02d:%02d", hh, mm, ss);
    }

    private static void appendLog(String msg) {
        synchronized (UI_EVENT_LOCK) {
            pendingLogText.append(msg);
            if (!msg.endsWith("\n")) {
                pendingLogText.append('\n');
            }
            trimPendingLogText();
        }
    }

    private static void enqueueQueueLength(int windowIndex, int queueLength) {
        synchronized (UI_EVENT_LOCK) {
            pendingQueueLengths.put(windowIndex, queueLength);
        }
    }

    private static void enqueueTableState(int tableIndex, int[] seatGroupIds) {
        synchronized (UI_EVENT_LOCK) {
            pendingTableStates.put(tableIndex, seatGroupIds.clone());
        }
    }

    private static void startUiRefreshTimer() {
        if (uiRefreshTimer != null) {
            return;
        }
        uiRefreshTimer = new javax.swing.Timer(UI_REFRESH_INTERVAL_MS, event -> flushPendingUiEvents());
        uiRefreshTimer.start();
    }

    private static void clearPendingUiEvents() {
        synchronized (UI_EVENT_LOCK) {
            pendingLogText.setLength(0);
            pendingQueueLengths.clear();
            pendingTableStates.clear();
            pendingSnapshot = null;
        }
        latestSnapshot = null;
    }

    private static void flushPendingUiEvents() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(MainDashboard::flushPendingUiEvents);
            return;
        }

        String logs;
        Map<Integer, Integer> queueLengths;
        Map<Integer, int[]> tableStates;
        SimulationSnapshot snapshot;
        synchronized (UI_EVENT_LOCK) {
            logs = pendingLogText.toString();
            pendingLogText.setLength(0);
            queueLengths = new LinkedHashMap<>(pendingQueueLengths);
            pendingQueueLengths.clear();
            tableStates = new LinkedHashMap<>(pendingTableStates);
            pendingTableStates.clear();
            snapshot = pendingSnapshot;
            pendingSnapshot = null;
        }

        if (myQueuePanel != null) {
            for (Map.Entry<Integer, Integer> entry : queueLengths.entrySet()) {
                myQueuePanel.updateQueueLength(entry.getKey(), entry.getValue());
            }
        }
        if (expandedQueuePanel != null) {
            for (Map.Entry<Integer, Integer> entry : queueLengths.entrySet()) {
                expandedQueuePanel.updateQueueLength(entry.getKey(), entry.getValue());
            }
        }
        if (myDiningPanel != null) {
            for (Map.Entry<Integer, int[]> entry : tableStates.entrySet()) {
                myDiningPanel.updateTableOccupancy(entry.getKey(), entry.getValue());
            }
        }
        if (expandedDiningPanel != null) {
            for (Map.Entry<Integer, int[]> entry : tableStates.entrySet()) {
                expandedDiningPanel.updateTableOccupancy(entry.getKey(), entry.getValue());
            }
        }
        if (snapshot != null) {
            latestSnapshot = snapshot;
            applySnapshotToScene(snapshot, myQueuePanel, myDiningPanel,
                    waitingSeatPanel, leavingAreaPanel, statusFlowPanel);
            applySnapshotToScene(snapshot, expandedQueuePanel, expandedDiningPanel,
                    expandedWaitingSeatPanel, expandedLeavingAreaPanel, expandedStatusFlowPanel);
            if (kpiDashboardPanel != null) {
                kpiDashboardPanel.updateSnapshot(snapshot);
            }
            if (trendGraphPanel != null) {
                trendGraphPanel.setTrendPoints(snapshot.trendPoints);
            }
            if (analysisDashboardPanel != null) {
                analysisDashboardPanel.updateSnapshot(snapshot);
            }
            if (recommendationCardPanel != null && latestOptimizationContext != null) {
                latestOptimizationCurrent = currentResultFromSnapshot(snapshot);
                recommendationCardPanel.updateResult(latestOptimizationCurrent, latestOptimizationContext);
            }
        }
    }

    private static void applySnapshotToScene(SimulationSnapshot snapshot,
                                             QueueAreaPanel queuePanel,
                                             DiningAreaPanel diningPanel,
                                             WaitingSeatPanel waitingPanel,
                                             LeavingAreaPanel leavingPanel,
                                             StatusFlowPanel flowPanel) {
        if (snapshot == null) {
            return;
        }
        if (queuePanel != null) {
            queuePanel.updateWindowStats(snapshot.windowStats);
        }
        if (diningPanel != null) {
            diningPanel.updateTableStats(snapshot.tableStats);
        }
        if (waitingPanel != null) {
            waitingPanel.updateSnapshot(snapshot);
        }
        if (leavingPanel != null) {
            leavingPanel.updateSnapshot(snapshot);
        }
        if (flowPanel != null) {
            flowPanel.updateSnapshot(snapshot);
        }
    }

    private static void trimPendingLogText() {
        int excess = pendingLogText.length() - MAX_LOG_CHARACTERS;
        if (excess > 0) {
            pendingLogText.delete(0, excess);
        }
    }

    @Override
    public void onStudentArrived(int studentId, int groupId, long time) {
        appendLog(String.format(">>> [%s] 学生 %03d（组 %d）抵达食堂%n", formatTime(time), studentId, groupId));
    }

    @Override
    public void onStudentQueuedAtWindow(int studentId, int groupId, int windowIndex, int queueLength, long time) {
        appendLog(String.format(">>> [%s] 学生 %03d（组 %d）到窗口 %d 排队打饭（当前等候: %d人）%n",
                formatTime(time), studentId, groupId, windowIndex + 1, queueLength));
    }

    @Override
    public void onWindowQueueUpdated(int windowIndex, int queueLength) {
        enqueueQueueLength(windowIndex, queueLength);
    }

    @Override
    public void onTableOccupancyChanged(int tableIndex, int[] seatGroupIds) {
        int total = 0;
        for (int gid : seatGroupIds) {
            if (gid >= 0) total++;
        }
        enqueueTableState(tableIndex, seatGroupIds);
        appendLog(String.format(
                ">>> [桌位] 桌 %02d 当前入座人数: %d%n",
                tableIndex + 1,
                total
        ));
    }

    @Override
    public void onStudentSeatedAtTable(int studentId, int groupId, int tableIndex, long time) {
        appendLog(String.format(">>> [%s] 学生 %03d（组 %d）在桌 %02d 入座就餐%n", formatTime(time), studentId, groupId, tableIndex + 1));
    }

    @Override
    public void onStudentLeft(int studentId, int groupId, int tableIndex, String reason, long time) {
        if (tableIndex >= 0) {
            appendLog(String.format(">>> [%s] 学生 %03d（组 %d）离开桌 %02d（%s）%n", formatTime(time), studentId, groupId, tableIndex + 1, reason));
        } else {
            appendLog(String.format(">>> [%s] 学生 %03d（组 %d）放弃离开（%s）%n", formatTime(time), studentId, groupId, reason));
        }
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
            if (phaseLabel != null) {
                phaseLabel.setText(" ● " + label);
                phaseLabel.setForeground(phaseColor);
            }
            appendLog(">>> [阶段] " + label + " (" + formatTime(currentTime) + ")");
        });
    }

    @Override
    public void onSnapshot(SimulationSnapshot snapshot) {
        synchronized (UI_EVENT_LOCK) {
            pendingSnapshot = snapshot;
        }
    }

    @Override
    public void onSimulationFinished() {
        SwingUtilities.invokeLater(() -> {
            if (simulationEngine != null) {
                latestSimulationReportPath = simulationEngine.getReportFilePath();
            }
            if (startButton != null) {
                startButton.setEnabled(true);
            }
            if (stopButton != null) {
                stopButton.setEnabled(false);
                stopButton.setVisible(false);
            }
            if (phaseLabel != null) {
                phaseLabel.setText(" ● 仿真结束");
                phaseLabel.setForeground(ColorTheme.TEXT_SECONDARY);
            }
            appendLog(">>> [系统] 本次仿真已结束。");
            flushPendingUiEvents();
            JOptionPane.showMessageDialog(this, "本次仿真已圆满结束！");
        });
    }
}
