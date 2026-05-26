package frontend;

import backend.optimize.GridSearchOptimizer;
import backend.optimize.LossConfig;
import backend.optimize.OptimizeConfig;
import backend.optimize.OptimizeResult;
import backend.optimize.SimRunResult;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class OptimizationPanel extends JPanel {
    private final JTextField totalPopulationField = new JTextField("1000", 6);
    private final JTextField minWindowField = new JTextField("3", 6);
    private final JTextField maxWindowField = new JTextField("6", 6);
    private final JTextField minTableField = new JTextField("80", 6);
    private final JTextField maxTableField = new JTextField("100", 6);
    private final JTextField repeatTimesField = new JTextField("2", 6);
    private final JTextField topKField = new JTextField("10", 6);

    private final JButton startOptimizeButton = new JButton("开始寻优");
    private final JButton cancelOptimizeButton = new JButton("取消寻优");
    private final JButton importBestButton = new JButton("导入最佳方案到复盘");
    private final JButton importSelectedTopKButton = new JButton("导入选中方案");
    private final JLabel statusLabel = new JLabel("等待任务");
    private final JProgressBar progressBar = new JProgressBar();

    private final JLabel bestWindowValue = new JLabel("--");
    private final JLabel bestTableValue = new JLabel("--");
    private final JLabel avgWaitValue = new JLabel("--");
    private final JLabel lossValue = new JLabel("--");
    private final JLabel seatUseValue = new JLabel("--");
    private final JLabel windowUseValue = new JLabel("--");
    private final LossCurvePanel lossCurvePanel = new LossCurvePanel();
    private final HeatmapPanel heatmapPanel = new HeatmapPanel();
    private final Consumer<SimRunResult> replayPresetConsumer;
    private final Consumer<SimRunResult> replayContextConsumer;
    private List<SimRunResult> displayedTopKResults = Collections.emptyList();
    private SimRunResult currentBestResult;
    private int currentTopK = 10;

    private final DefaultTableModel topKTableModel = new DefaultTableModel(
            new Object[]{"排名", "窗口数", "桌子数", "等待(分钟)", "损失值", "座位利用率", "窗口利用率", "最大排队", "放弃率", "完成率"},
            0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable topKTable = new JTable(topKTableModel);

    private SwingWorker<OptimizeResult, ProgressUpdate> optimizeWorker;

    public OptimizationPanel() {
        this(null);
    }

    public OptimizationPanel(Consumer<SimRunResult> replayPresetConsumer) {
        this(replayPresetConsumer, null);
    }

    public OptimizationPanel(Consumer<SimRunResult> replayPresetConsumer,
                             Consumer<SimRunResult> replayContextConsumer) {
        super(new BorderLayout(14, 14));
        this.replayPresetConsumer = replayPresetConsumer;
        this.replayContextConsumer = replayContextConsumer;
        setBackground(ColorTheme.BG_MAIN);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(createInputArea(), BorderLayout.WEST);
        add(createCenterArea(), BorderLayout.CENTER);
    }

    private JPanel createInputArea() {
        JPanel inputArea = createCardPanel(new BorderLayout(0, 14));
        inputArea.setPreferredSize(new Dimension(260, 0));

        JLabel title = createTitleLabel("自动寻优控制台");
        inputArea.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        form.setOpaque(false);
        addFormRow(form, "就餐人数", totalPopulationField);
        addFormRow(form, "最小窗口数", minWindowField);
        addFormRow(form, "最大窗口数", maxWindowField);
        addFormRow(form, "最小桌子数", minTableField);
        addFormRow(form, "最大桌子数", maxTableField);
        addFormRow(form, "重复次数", repeatTimesField);
        addFormRow(form, "候选数量", topKField);
        inputArea.add(form, BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new BorderLayout(0, 10));
        actionPanel.setOpaque(false);
        startOptimizeButton.setForeground(ColorTheme.BG_CARD);
        startOptimizeButton.setBackground(ColorTheme.ACCENT_CYAN);
        startOptimizeButton.setFocusPainted(false);
        startOptimizeButton.addActionListener(e -> startOptimization());

        cancelOptimizeButton.setForeground(ColorTheme.BG_CARD);
        cancelOptimizeButton.setBackground(ColorTheme.ACCENT_RED);
        cancelOptimizeButton.setFocusPainted(false);
        cancelOptimizeButton.setEnabled(false);
        cancelOptimizeButton.addActionListener(e -> cancelOptimization());

        importBestButton.setForeground(ColorTheme.BG_CARD);
        importBestButton.setBackground(ColorTheme.ACCENT_CYAN);
        importBestButton.setFocusPainted(false);
        importBestButton.setEnabled(false);
        importBestButton.addActionListener(e -> importBestToReplay());

        importSelectedTopKButton.setForeground(ColorTheme.BG_CARD);
        importSelectedTopKButton.setBackground(ColorTheme.ACCENT_YELLOW);
        importSelectedTopKButton.setFocusPainted(false);
        importSelectedTopKButton.setEnabled(false);
        importSelectedTopKButton.addActionListener(e -> importSelectedTopKToReplay());

        statusLabel.setForeground(ColorTheme.TEXT_SECONDARY);
        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(true);
        progressBar.setString("就绪");
        progressBar.setForeground(ColorTheme.ACCENT_CYAN);

        JPanel buttonGrid = new JPanel(new GridLayout(0, 1, 0, 8));
        buttonGrid.setOpaque(false);
        buttonGrid.add(startOptimizeButton);
        buttonGrid.add(cancelOptimizeButton);
        buttonGrid.add(importBestButton);
        buttonGrid.add(importSelectedTopKButton);

        actionPanel.add(buttonGrid, BorderLayout.NORTH);
        actionPanel.add(progressBar, BorderLayout.CENTER);
        actionPanel.add(statusLabel, BorderLayout.SOUTH);
        inputArea.add(actionPanel, BorderLayout.SOUTH);

        return inputArea;
    }

    private JPanel createCenterArea() {
        JPanel center = new JPanel(new BorderLayout(14, 14));
        center.setOpaque(false);

        center.add(createKpiArea(), BorderLayout.NORTH);
        center.add(createTableArea(), BorderLayout.CENTER);
        center.add(createChartArea(), BorderLayout.SOUTH);

        return center;
    }

    private JPanel createKpiArea() {
        JPanel kpiArea = new JPanel(new GridLayout(1, 6, 10, 0));
        kpiArea.setOpaque(false);
        kpiArea.add(createKpiCard("推荐窗口", bestWindowValue, ColorTheme.ACCENT_CYAN));
        kpiArea.add(createKpiCard("推荐桌子", bestTableValue, ColorTheme.ACCENT_CYAN));
        kpiArea.add(createKpiCard("平均等待", avgWaitValue, ColorTheme.ACCENT_YELLOW));
        kpiArea.add(createKpiCard("综合损失", lossValue, ColorTheme.ACCENT_RED));
        kpiArea.add(createKpiCard("座位利用率", seatUseValue, ColorTheme.ACCENT_CYAN));
        kpiArea.add(createKpiCard("窗口利用率", windowUseValue, ColorTheme.ACCENT_CYAN));
        return kpiArea;
    }

    private JPanel createTableArea() {
        JPanel tableArea = createCardPanel(new BorderLayout(0, 10));
        tableArea.add(createTitleLabel("最优候选方案"), BorderLayout.NORTH);

        topKTable.setRowHeight(28);
        topKTable.setFillsViewportHeight(true);
        topKTable.setBackground(ColorTheme.BG_CARD);
        topKTable.setForeground(ColorTheme.TEXT_PRIMARY);
        topKTable.setGridColor(ColorTheme.BG_ITEM);
        topKTable.getTableHeader().setBackground(ColorTheme.BG_ITEM);
        topKTable.getTableHeader().setForeground(ColorTheme.TEXT_PRIMARY);
        topKTable.getTableHeader().setReorderingAllowed(false);

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setHorizontalAlignment(SwingConstants.CENTER);
        renderer.setBackground(ColorTheme.BG_CARD);
        renderer.setForeground(ColorTheme.TEXT_PRIMARY);
        for (int i = 0; i < topKTable.getColumnCount(); i++) {
            topKTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        JScrollPane scrollPane = new JScrollPane(topKTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(ColorTheme.BG_CARD);
        tableArea.add(scrollPane, BorderLayout.CENTER);
        return tableArea;
    }

    private JPanel createChartArea() {
        JPanel chartArea = new JPanel(new GridLayout(1, 2, 14, 0));
        chartArea.setOpaque(false);
        chartArea.setPreferredSize(new Dimension(0, 190));
        chartArea.add(createChartCard("损失值曲线", lossCurvePanel));
        chartArea.add(createChartCard("窗口-桌子热力图", heatmapPanel));
        return chartArea;
    }

    private JPanel createChartCard(String title, JPanel chart) {
        JPanel card = createCardPanel(new BorderLayout(0, 8));
        card.add(createTitleLabel(title), BorderLayout.NORTH);
        card.add(chart, BorderLayout.CENTER);
        return card;
    }

    private JPanel createKpiCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = createCardPanel(new BorderLayout(0, 8));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(ColorTheme.TEXT_SECONDARY);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        valueLabel.setForeground(accentColor);
        valueLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private void addFormRow(JPanel form, String labelText, JTextField field) {
        JLabel label = new JLabel(labelText);
        label.setForeground(ColorTheme.TEXT_SECONDARY);
        field.setHorizontalAlignment(SwingConstants.CENTER);
        field.setBackground(ColorTheme.BG_ITEM);
        field.setForeground(ColorTheme.TEXT_PRIMARY);
        field.setCaretColor(ColorTheme.ACCENT_CYAN);
        field.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        form.add(label);
        form.add(field);
    }

    private JPanel createCardPanel(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(ColorTheme.BG_CARD);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        return panel;
    }

    private JLabel createTitleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(ColorTheme.TEXT_PRIMARY);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        return label;
    }

    private void startOptimization() {
        final OptimizeConfig config;
        try {
            config = buildOptimizeConfigFromForm();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "参数错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        clearResultView();
        currentTopK = config.topK;
        setRunningState(true, config);

        optimizeWorker = new SwingWorker<OptimizeResult, ProgressUpdate>() {
            @Override
            protected OptimizeResult doInBackground() {
                LossConfig lossConfig = new LossConfig();
                return new GridSearchOptimizer().run(
                        config,
                        lossConfig,
                        (step, total, stepResult, currentResult) ->
                                publish(new ProgressUpdate(step, total, snapshot(currentResult.allResults),
                                        currentResult.bestResult == null ? null : currentResult.bestResult.copyBasic())),
                        this::isCancelled
                );
            }

            @Override
            protected void process(List<ProgressUpdate> chunks) {
                if (chunks.isEmpty()) {
                    return;
                }
                ProgressUpdate update = chunks.get(chunks.size() - 1);
                progressBar.setMaximum(update.total);
                progressBar.setValue(update.step);
                progressBar.setString(update.step + "/" + update.total);
                statusLabel.setText("寻优中：" + update.step + "/" + update.total);
                updateResultPreview(update.results, update.bestResult);
            }

            @Override
            protected void done() {
                try {
                    OptimizeResult result = get();
                    updateResultView(result);
                    if (isCancelled()) {
                        statusLabel.setText("寻优已取消，保留已完成候选结果");
                    } else {
                        statusLabel.setText("寻优完成：" + result.totalCandidateCount + " 个候选方案 / "
                                + result.totalSimulationCount + " 次仿真");
                    }
                } catch (CancellationException ex) {
                    statusLabel.setText("寻优已取消，保留已完成候选结果");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("任务已中断");
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    statusLabel.setText("寻优失败");
                    JOptionPane.showMessageDialog(OptimizationPanel.this,
                            cause.getMessage(),
                            "寻优失败",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    setRunningState(false, config);
                }
            }
        };
        optimizeWorker.execute();
    }

    private OptimizeConfig buildOptimizeConfigFromForm() {
        OptimizeConfig config = new OptimizeConfig();
        config.totalPopulation = parsePositiveInt(totalPopulationField, "就餐人数");
        config.minWindowCount = parsePositiveInt(minWindowField, "最小窗口数");
        config.maxWindowCount = parsePositiveInt(maxWindowField, "最大窗口数");
        config.minTableCount = parsePositiveInt(minTableField, "最小桌子数");
        config.maxTableCount = parsePositiveInt(maxTableField, "最大桌子数");
        config.repeatTimes = parsePositiveInt(repeatTimesField, "重复次数");
        config.topK = parsePositiveInt(topKField, "候选数量");
        config.verboseConsoleLog = false;
        config.runReplayAfterOptimization = false;
        config.validate();
        return config;
    }

    private int parsePositiveInt(JTextField field, String name) {
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (value <= 0) {
                throw new NumberFormatException(name + " 必须是正整数");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " 必须是正整数");
        }
    }

    private void setRunningState(boolean running, OptimizeConfig config) {
        startOptimizeButton.setEnabled(!running);
        cancelOptimizeButton.setEnabled(running);
        importBestButton.setEnabled(!running && currentBestResult != null && replayPresetConsumer != null);
        importSelectedTopKButton.setEnabled(!running && !displayedTopKResults.isEmpty() && replayPresetConsumer != null);
        progressBar.setIndeterminate(false);
        if (running) {
            progressBar.setMinimum(0);
            progressBar.setMaximum(config.totalCandidateCount());
            progressBar.setValue(0);
        }
        progressBar.setString(running ? "0/" + config.totalCandidateCount() : "就绪");
        if (running) {
            statusLabel.setText("正在后台运行：" + config.totalCandidateCount() + " 个候选方案 / "
                    + config.totalSimulationCount() + " 次仿真");
        }
    }

    private void cancelOptimization() {
        if (optimizeWorker != null && !optimizeWorker.isDone()) {
            optimizeWorker.cancel(true);
            cancelOptimizeButton.setEnabled(false);
            statusLabel.setText("正在取消，等待当前仿真片段结束...");
        }
    }

    private void clearResultView() {
        currentBestResult = null;
        displayedTopKResults = Collections.emptyList();
        bestWindowValue.setText("--");
        bestTableValue.setText("--");
        avgWaitValue.setText("--");
        lossValue.setText("--");
        seatUseValue.setText("--");
        windowUseValue.setText("--");
        topKTableModel.setRowCount(0);
        lossCurvePanel.setResults(Collections.emptyList());
        heatmapPanel.setResults(Collections.emptyList());
        importBestButton.setEnabled(false);
        importSelectedTopKButton.setEnabled(false);
    }

    private void updateResultView(OptimizeResult result) {
        if (result == null || result.bestResult == null) {
            statusLabel.setText("未产生有效结果");
            return;
        }

        SimRunResult best = result.bestResult;
        updateBestCards(best);
        fillTopKTable(result.topKResults);
        lossCurvePanel.setResults(result.allResults);
        heatmapPanel.setResults(result.allResults);
        currentBestResult = best.copyBasic();
        if (replayContextConsumer != null) {
            replayContextConsumer.accept(currentBestResult.copyBasic());
        }
        importBestButton.setEnabled(replayPresetConsumer != null);
        importSelectedTopKButton.setEnabled(replayPresetConsumer != null && !displayedTopKResults.isEmpty());
    }

    private void fillTopKTable(List<SimRunResult> topKResults) {
        topKTableModel.setRowCount(0);
        displayedTopKResults = snapshot(topKResults);
        for (int i = 0; i < displayedTopKResults.size(); i++) {
            SimRunResult r = displayedTopKResults.get(i);
            topKTableModel.addRow(new Object[]{
                    i + 1,
                    r.windowCount,
                    r.tableCount,
                    formatDouble(r.avgWaitTimeMinutes, 2),
                    formatDouble(r.loss, 4),
                    formatPercent(r.seatUtilization),
                    formatPercent(r.windowUtilization),
                    r.maxQueueLength,
                    formatPercent(r.abandonRate),
                    formatPercent(r.finishRate)
            });
        }
    }

    private void updateResultPreview(List<SimRunResult> allResults, SimRunResult best) {
        if (best != null) {
            currentBestResult = best.copyBasic();
            updateBestCards(best);
        }
        List<SimRunResult> topResults = snapshot(allResults);
        topResults.sort(Comparator.comparingDouble(r -> r.loss));
        if (topResults.size() > currentTopK) {
            topResults = new ArrayList<>(topResults.subList(0, currentTopK));
        }
        fillTopKTable(topResults);
        lossCurvePanel.setResults(allResults);
        heatmapPanel.setResults(allResults);
    }

    private void updateBestCards(SimRunResult best) {
        bestWindowValue.setText(String.valueOf(best.windowCount));
        bestTableValue.setText(String.valueOf(best.tableCount));
        avgWaitValue.setText(formatDouble(best.avgWaitTimeMinutes, 2) + " 分钟");
        lossValue.setText(formatDouble(best.loss, 4));
        seatUseValue.setText(formatPercent(best.seatUtilization));
        windowUseValue.setText(formatPercent(best.windowUtilization));
    }

    private void importBestToReplay() {
        if (currentBestResult == null || replayPresetConsumer == null) {
            return;
        }
        replayPresetConsumer.accept(currentBestResult.copyBasic());
    }

    private void importSelectedTopKToReplay() {
        int selectedRow = topKTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= displayedTopKResults.size() || replayPresetConsumer == null) {
            JOptionPane.showMessageDialog(this, "请先在候选方案表格中选择一行", "未选择方案", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        replayPresetConsumer.accept(displayedTopKResults.get(selectedRow).copyBasic());
    }

    private static List<SimRunResult> snapshot(List<SimRunResult> source) {
        List<SimRunResult> copies = new ArrayList<>();
        if (source == null) {
            return copies;
        }
        for (SimRunResult result : source) {
            copies.add(result.copyBasic());
        }
        return copies;
    }

    private String formatDouble(double value, int scale) {
        return String.format("%." + scale + "f", value);
    }

    private String formatPercent(double value) {
        return String.format("%.1f%%", value * 100.0);
    }

    private static class ProgressUpdate {
        private final int step;
        private final int total;
        private final List<SimRunResult> results;
        private final SimRunResult bestResult;

        private ProgressUpdate(int step, int total, List<SimRunResult> results, SimRunResult bestResult) {
            this.step = step;
            this.total = total;
            this.results = results;
            this.bestResult = bestResult;
        }
    }

    private static class LossCurvePanel extends JPanel {
        private List<SimRunResult> results = Collections.emptyList();

        private LossCurvePanel() {
            setOpaque(true);
            setBackground(ColorTheme.BG_CARD);
        }

        private void setResults(List<SimRunResult> results) {
            this.results = results == null ? Collections.emptyList() : results;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int left = 46;
            int right = 16;
            int top = 12;
            int bottom = 30;
            int plotWidth = Math.max(1, getWidth() - left - right);
            int plotHeight = Math.max(1, getHeight() - top - bottom);

            if (results.size() < 2) {
                drawEmptyMessage(g2, "运行寻优后显示损失趋势");
                g2.dispose();
                return;
            }

            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (SimRunResult r : results) {
                min = Math.min(min, Math.min(r.loss, r.currentBestLoss));
                max = Math.max(max, Math.max(r.loss, r.currentBestLoss));
            }
            if (Math.abs(max - min) < 0.000001) {
                max = min + 1.0;
            }

            drawGrid(g2, left, top, plotWidth, plotHeight, min, max);
            drawSeries(g2, left, top, plotWidth, plotHeight, min, max, false, ColorTheme.ACCENT_YELLOW);
            drawSeries(g2, left, top, plotWidth, plotHeight, min, max, true, ColorTheme.ACCENT_CYAN);
            drawLegend(g2, left, top);
            g2.dispose();
        }

        private void drawGrid(Graphics2D g2, int left, int top, int width, int height, double min, double max) {
            g2.setColor(ColorTheme.BG_ITEM);
            for (int i = 0; i <= 4; i++) {
                int y = top + i * height / 4;
                g2.drawLine(left, y, left + width, y);
            }
            g2.setColor(ColorTheme.TEXT_SECONDARY);
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g2.drawString(String.format("%.2f", max), 4, top + 10);
            g2.drawString(String.format("%.2f", min), 4, top + height);
            g2.drawString("步数", left + width - 24, top + height + 22);
        }

        private void drawSeries(Graphics2D g2, int left, int top, int width, int height,
                                double min, double max, boolean bestSeries, Color color) {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(bestSeries ? 2.4f : 1.6f));
            int lastX = -1;
            int lastY = -1;
            for (int i = 0; i < results.size(); i++) {
                SimRunResult r = results.get(i);
                double value = bestSeries ? r.currentBestLoss : r.loss;
                int x = left + (int) Math.round(i * width / (double) (results.size() - 1));
                int y = top + (int) Math.round((max - value) * height / (max - min));
                if (lastX >= 0) {
                    g2.drawLine(lastX, lastY, x, y);
                }
                lastX = x;
                lastY = y;
            }
        }

        private void drawLegend(Graphics2D g2, int left, int top) {
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g2.setColor(ColorTheme.ACCENT_YELLOW);
            g2.fillRect(left, top, 10, 4);
            g2.setColor(ColorTheme.TEXT_SECONDARY);
            g2.drawString("本轮损失", left + 16, top + 6);
            g2.setColor(ColorTheme.ACCENT_CYAN);
            g2.fillRect(left + 92, top, 10, 4);
            g2.setColor(ColorTheme.TEXT_SECONDARY);
            g2.drawString("当前最佳", left + 108, top + 6);
        }
    }

    private static class HeatmapPanel extends JPanel {
        private List<SimRunResult> results = Collections.emptyList();

        private HeatmapPanel() {
            setOpaque(true);
            setBackground(ColorTheme.BG_CARD);
        }

        private void setResults(List<SimRunResult> results) {
            this.results = results == null ? Collections.emptyList() : results;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (results.isEmpty()) {
                drawEmptyMessage(g2, "运行寻优后显示损失热力图");
                g2.dispose();
                return;
            }

            int minWindow = Integer.MAX_VALUE;
            int maxWindow = Integer.MIN_VALUE;
            int minTable = Integer.MAX_VALUE;
            int maxTable = Integer.MIN_VALUE;
            double minLoss = Double.MAX_VALUE;
            double maxLoss = -Double.MAX_VALUE;
            for (SimRunResult r : results) {
                minWindow = Math.min(minWindow, r.windowCount);
                maxWindow = Math.max(maxWindow, r.windowCount);
                minTable = Math.min(minTable, r.tableCount);
                maxTable = Math.max(maxTable, r.tableCount);
                minLoss = Math.min(minLoss, r.loss);
                maxLoss = Math.max(maxLoss, r.loss);
            }

            int rows = maxWindow - minWindow + 1;
            int cols = maxTable - minTable + 1;
            double[][] losses = new double[rows][cols];
            for (double[] row : losses) {
                Arrays.fill(row, Double.NaN);
            }
            for (SimRunResult r : results) {
                losses[r.windowCount - minWindow][r.tableCount - minTable] = r.loss;
            }

            int left = 42;
            int right = 12;
            int top = 12;
            int bottom = 28;
            int plotWidth = Math.max(1, getWidth() - left - right);
            int plotHeight = Math.max(1, getHeight() - top - bottom);
            int cellWidth = Math.max(1, plotWidth / cols);
            int cellHeight = Math.max(1, plotHeight / rows);

            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    double loss = losses[row][col];
                    int x = left + col * cellWidth;
                    int y = top + row * cellHeight;
                    g2.setColor(Double.isNaN(loss) ? ColorTheme.BG_ITEM : colorForLoss(loss, minLoss, maxLoss));
                    g2.fillRect(x, y, cellWidth - 1, cellHeight - 1);
                }
            }

            drawHeatmapLabels(g2, minWindow, maxWindow, minTable, maxTable, left, top, plotWidth, plotHeight);
            g2.dispose();
        }

        private void drawHeatmapLabels(Graphics2D g2, int minWindow, int maxWindow, int minTable, int maxTable,
                                       int left, int top, int width, int height) {
            g2.setColor(ColorTheme.TEXT_SECONDARY);
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g2.drawString("窗 " + minWindow, 6, top + 12);
            g2.drawString("窗 " + maxWindow, 6, top + height);
            g2.drawString("桌 " + minTable, left, top + height + 20);
            g2.drawString("桌 " + maxTable, left + width - 42, top + height + 20);
        }

        private Color colorForLoss(double loss, double minLoss, double maxLoss) {
            if (Math.abs(maxLoss - minLoss) < 0.000001) {
                return ColorTheme.ACCENT_CYAN;
            }
            double ratio = Math.max(0.0, Math.min(1.0, (loss - minLoss) / (maxLoss - minLoss)));
            if (ratio < 0.5) {
                return blend(ColorTheme.ACCENT_CYAN, ColorTheme.ACCENT_YELLOW, ratio * 2.0);
            }
            return blend(ColorTheme.ACCENT_YELLOW, ColorTheme.ACCENT_RED, (ratio - 0.5) * 2.0);
        }

        private Color blend(Color from, Color to, double ratio) {
            int red = (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * ratio);
            int green = (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * ratio);
            int blue = (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * ratio);
            return new Color(red, green, blue);
        }
    }

    private static void drawEmptyMessage(Graphics2D g2, String message) {
        g2.setColor(ColorTheme.TEXT_SECONDARY);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        int width = g2.getFontMetrics().stringWidth(message);
        g2.drawString(message, Math.max(8, (g2.getClipBounds().width - width) / 2),
                Math.max(20, g2.getClipBounds().height / 2));
    }
}
