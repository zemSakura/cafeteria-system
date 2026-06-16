package frontend;

import backend.optimize.GridSearchOptimizer;
import backend.optimize.LossConfig;
import backend.optimize.OptimizeConfig;
import backend.optimize.OptimizeResult;
import backend.optimize.SimRunResult;
import backend.dto.OptimizationMode;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class OptimizationPanel extends JPanel {
    public interface OptimizationSummaryConsumer {
        void accept(SimRunResult currentResult,
                    SimRunResult bestResult,
                    List<SimRunResult> topKResults,
                    List<SimRunResult> allResults);
    }

    private final JTextField totalPopulationField = new JTextField("1000", 6);
    private final JButton startOptimizeButton = new JButton("开始寻优");
    private final JButton cancelOptimizeButton = new JButton("取消寻优");
    private final JButton importBestButton = new JButton("导入最佳方案到复盘");
    private final JButton importSelectedTopKButton = new JButton("导入选中方案");
    private final JLabel statusLabel = new JLabel("等待任务");
    private final JProgressBar progressBar = new JProgressBar();

    private final JLabel bestWindowValue = new JLabel("--");
    private final JLabel bestTableValue = new JLabel("--");
    private final JLabel avgWaitValue = new JLabel("--");
    private final JLabel seatWaitValue = new JLabel("--");
    private final JLabel netProfitValue = new JLabel("--");
    private final JLabel completionValue = new JLabel("--");
    private final JLabel scoreValue = new JLabel("--");
    private final JLabel seatUseValue = new JLabel("--");
    private final JLabel windowUseValue = new JLabel("--");
    private final JLabel comparisonValue = new JLabel("等待寻优结果");
    private final JLabel sortingWarningLabel = new JLabel(" ");
    private final LossCurvePanel lossCurvePanel = new LossCurvePanel();
    private final HeatmapPanel heatmapPanel = new HeatmapPanel();
    private final Consumer<SimRunResult> replayPresetConsumer;
    private final Consumer<SimRunResult> replayContextConsumer;
    private final OptimizationSummaryConsumer optimizationSummaryConsumer;
    private List<SimRunResult> displayedTopKResults = Collections.emptyList();
    private SimRunResult currentBestResult;
    private int currentTopK = 10;

    private final DefaultTableModel topKTableModel = new DefaultTableModel(
            new Object[]{"排名", "窗口数", "桌子数", "完成率", "全天净收益", "排队等待", "等座等待", "放弃人数", "综合评分", "推荐理由"},
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
        this(replayPresetConsumer, replayContextConsumer, null);
    }

    public OptimizationPanel(Consumer<SimRunResult> replayPresetConsumer,
                             Consumer<SimRunResult> replayContextConsumer,
                             OptimizationSummaryConsumer optimizationSummaryConsumer) {
        super(new BorderLayout(14, 14));
        this.replayPresetConsumer = replayPresetConsumer;
        this.replayContextConsumer = replayContextConsumer;
        this.optimizationSummaryConsumer = optimizationSummaryConsumer;
        setBackground(ColorTheme.BG_MAIN);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(createInputArea(), BorderLayout.WEST);
        add(createCenterArea(), BorderLayout.CENTER);
    }

    private JPanel createInputArea() {
        JPanel inputArea = createCardPanel(new BorderLayout(0, 14));
        inputArea.setPreferredSize(new Dimension(350, 0));
        inputArea.setMinimumSize(new Dimension(330, 0));

        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setOpaque(false);
        header.add(createTitleLabel("自动寻优控制台"), BorderLayout.NORTH);
        header.add(createNoteLabel("寻优按全天三餐统一计算，默认收益优先。未设置上下限时按 ABES 自适应扩展搜索范围。"),
                BorderLayout.CENTER);

        JPanel topContent = new JPanel(new BorderLayout(0, 14));
        topContent.setOpaque(false);
        topContent.add(header, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        GridBagConstraints formGbc = new GridBagConstraints();
        formGbc.gridx = 0;
        formGbc.gridy = 0;
        formGbc.weightx = 1;
        formGbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(createPopulationEditor(), formGbc);
        topContent.add(form, BorderLayout.CENTER);
        inputArea.add(topContent, BorderLayout.NORTH);

        JPanel actionPanel = new JPanel(new BorderLayout(0, 10));
        actionPanel.setOpaque(false);
        styleOptimizeButton(startOptimizeButton, ColorTheme.ACCENT_CYAN, ColorTheme.BG_CARD);
        startOptimizeButton.addActionListener(e -> startOptimization());

        styleOptimizeButton(cancelOptimizeButton, new Color(254, 226, 226), ColorTheme.ACCENT_RED);
        cancelOptimizeButton.setEnabled(false);
        cancelOptimizeButton.addActionListener(e -> cancelOptimization());

        styleOptimizeButton(importBestButton, ColorTheme.BG_CONTROL, ColorTheme.ACCENT_BLUE);
        importBestButton.setEnabled(false);
        importBestButton.addActionListener(e -> importBestToReplay());

        styleOptimizeButton(importSelectedTopKButton, ColorTheme.BG_CONTROL, ColorTheme.ACCENT_BLUE);
        importSelectedTopKButton.setEnabled(false);
        importSelectedTopKButton.addActionListener(e -> importSelectedTopKToReplay());

        statusLabel.setForeground(ColorTheme.TEXT_SECONDARY);
        statusLabel.setFont(ColorTheme.font(Font.PLAIN, 12));
        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(true);
        progressBar.setString("就绪");
        progressBar.setForeground(ColorTheme.ACCENT_CYAN);
        progressBar.setFont(ColorTheme.font(Font.BOLD, 12));
        progressBar.setPreferredSize(new Dimension(0, 24));

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
        JPanel kpiArea = new JPanel(new GridLayout(0, 3, 10, 10));
        kpiArea.setOpaque(false);
        kpiArea.add(createKpiCard("推荐窗口", bestWindowValue, ColorTheme.ACCENT_CYAN));
        kpiArea.add(createKpiCard("推荐桌子", bestTableValue, ColorTheme.ACCENT_CYAN));
        kpiArea.add(createKpiCard("完成率", completionValue, ColorTheme.ACCENT_CYAN));
        kpiArea.add(createKpiCard("全天净收益", netProfitValue, ColorTheme.ACCENT_YELLOW));
        kpiArea.add(createKpiCard("排队等待", avgWaitValue, ColorTheme.ACCENT_YELLOW));
        kpiArea.add(createKpiCard("等座等待", seatWaitValue, ColorTheme.ACCENT_YELLOW));
        kpiArea.add(createKpiCard("综合评分", scoreValue, ColorTheme.ACCENT_CYAN));
        kpiArea.add(createKpiCard("座位利用率", seatUseValue, ColorTheme.ACCENT_CYAN));
        kpiArea.add(createKpiCard("窗口利用率", windowUseValue, ColorTheme.ACCENT_CYAN));
        return kpiArea;
    }

    private JPanel createTableArea() {
        JPanel tableArea = createCardPanel(new BorderLayout(0, 10));
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.add(createTitleLabel("当前方案 vs 推荐方案 / TopK 候选方案"), BorderLayout.WEST);
        comparisonValue.setForeground(ColorTheme.ACCENT_YELLOW);
        comparisonValue.setFont(ColorTheme.font(Font.PLAIN, 12));
        header.add(comparisonValue, BorderLayout.CENTER);
        sortingWarningLabel.setForeground(ColorTheme.ACCENT_RED);
        sortingWarningLabel.setFont(ColorTheme.font(Font.BOLD, 12));
        header.add(sortingWarningLabel, BorderLayout.EAST);
        tableArea.add(header, BorderLayout.NORTH);

        topKTable.setRowHeight(28);
        topKTable.setFillsViewportHeight(true);
        topKTable.setBackground(ColorTheme.BG_CARD);
        topKTable.setForeground(ColorTheme.TEXT_PRIMARY);
        topKTable.setFont(ColorTheme.font(Font.PLAIN, 12));
        topKTable.setGridColor(ColorTheme.BG_ITEM);
        topKTable.getTableHeader().setBackground(ColorTheme.BG_ITEM);
        topKTable.getTableHeader().setForeground(ColorTheme.TEXT_PRIMARY);
        topKTable.getTableHeader().setFont(ColorTheme.font(Font.BOLD, 12));
        topKTable.getTableHeader().setReorderingAllowed(false);
        topKTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        TopKTableRenderer renderer = new TopKTableRenderer();
        for (int i = 0; i < topKTable.getColumnCount(); i++) {
            topKTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
        topKTable.getColumnModel().getColumn(0).setMaxWidth(58);
        topKTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        topKTable.getColumnModel().getColumn(2).setPreferredWidth(70);
        topKTable.getColumnModel().getColumn(3).setPreferredWidth(86);
        topKTable.getColumnModel().getColumn(4).setPreferredWidth(96);
        topKTable.getColumnModel().getColumn(5).setPreferredWidth(86);
        topKTable.getColumnModel().getColumn(6).setPreferredWidth(86);
        topKTable.getColumnModel().getColumn(8).setPreferredWidth(86);
        topKTable.getColumnModel().getColumn(9).setPreferredWidth(240);

        JScrollPane scrollPane = new JScrollPane(topKTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(ColorTheme.BG_CARD);
        tableArea.add(scrollPane, BorderLayout.CENTER);
        return tableArea;
    }

    private JPanel createChartArea() {
        JPanel chartArea = new JPanel(new GridLayout(1, 2, 14, 0));
        chartArea.setOpaque(false);
        chartArea.setPreferredSize(new Dimension(0, 220));
        chartArea.add(createChartCard("候选损失轨迹", lossCurvePanel));
        chartArea.add(createChartCard("候选方案搜索分布图", heatmapPanel));
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
        titleLabel.setFont(ColorTheme.font(Font.PLAIN, 13));

        valueLabel.setForeground(accentColor);
        valueLabel.setFont(ColorTheme.font(Font.BOLD, 24));

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createPopulationEditor() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);

        JLabel label = new JLabel("全天总人数(人)", SwingConstants.CENTER);
        label.setForeground(ColorTheme.TEXT_SECONDARY);
        label.setFont(ColorTheme.font(Font.PLAIN, 14));

        stylePopulationField(totalPopulationField);
        JPanel fieldWrap = new JPanel(new GridBagLayout());
        fieldWrap.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 14, 0, 14);
        fieldWrap.add(totalPopulationField, gbc);

        panel.add(label, BorderLayout.NORTH);
        panel.add(fieldWrap, BorderLayout.CENTER);
        return panel;
    }

    private void stylePopulationField(JTextField field) {
        field.setHorizontalAlignment(SwingConstants.CENTER);
        field.setBackground(ColorTheme.BG_ITEM);
        field.setForeground(ColorTheme.TEXT_PRIMARY);
        field.setCaretColor(ColorTheme.ACCENT_CYAN);
        field.setFont(ColorTheme.font(Font.BOLD, 18));
        field.setPreferredSize(new Dimension(0, 42));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)
        ));
        field.putClientProperty(
                "FlatLaf.style",
                "arc: 10; borderWidth: 0; focusWidth: 1; innerFocusWidth: 0"
        );
    }

    private JPanel createCardPanel(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(ColorTheme.BG_CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)
        ));
        return panel;
    }

    private JLabel createTitleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(ColorTheme.TEXT_PRIMARY);
        label.setFont(ColorTheme.font(Font.BOLD, 17));
        return label;
    }

    private JTextArea createNoteLabel(String text) {
        JTextArea label = new JTextArea(text);
        label.setOpaque(true);
        label.setBackground(ColorTheme.BG_ITEM);
        label.setForeground(ColorTheme.TEXT_SECONDARY);
        label.setFont(ColorTheme.font(Font.PLAIN, 13));
        label.setEditable(false);
        label.setFocusable(false);
        label.setLineWrap(true);
        label.setWrapStyleWord(false);
        label.setRows(4);
        label.setPreferredSize(new Dimension(0, 96));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        return label;
    }

    private void styleOptimizeButton(JButton button, Color bg, Color fg) {
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setBackground(bg);
        button.setForeground(fg);
        button.setFont(ColorTheme.font(Font.BOLD, 14));
        button.putClientProperty(
                "FlatLaf.style",
                "arc: 12; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; margin: 8,14,8,14"
        );
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
                LossConfig lossConfig = AdvancedOptimizationSettings.buildLossConfig();
                return new GridSearchOptimizer().run(
                        config,
                        lossConfig,
                        (step, total, stepResult, currentResult) -> {
                            if (shouldPublishProgress(step, total)) {
                                publish(new ProgressUpdate(step, total, snapshot(currentResult.allResults),
                                        currentResult.bestResult == null ? null : currentResult.bestResult.copyBasic()));
                            }
                        },
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
                        markProgressComplete(result);
                        String stopReason = result.stopReason == null || result.stopReason.isEmpty()
                                ? "寻优完成"
                                : result.stopReason;
                        setFinishedStatus(stopReason, result.totalCandidateCount, result.totalSimulationCount);
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

    private boolean shouldPublishProgress(int step, int total) {
        if (step <= 0 || step >= total) {
            return true;
        }
        int interval = Math.max(1, total / 200);
        return step % interval == 0;
    }

    private void markProgressComplete(OptimizeResult result) {
        int actual = result == null || result.allResults == null
                ? 0
                : result.allResults.size();
        if (actual <= 0 && result != null) {
            actual = Math.max(0, result.totalCandidateCount);
        }
        int max = Math.max(1, actual);
        progressBar.setMinimum(0);
        progressBar.setMaximum(max);
        progressBar.setValue(actual);
        progressBar.setString(actual + "/" + actual);
    }

    private void setFinishedStatus(String stopReason, int candidateCount, int simulationCount) {
        String reason = displayStopReason(stopReason);
        String detail = "实际评估 " + candidateCount + " 个候选方案 / " + simulationCount + " 次仿真";
        statusLabel.setText("<html>" + reason + "<br>" + detail + "</html>");
        statusLabel.setToolTipText(reason + "：" + detail);
    }

    private String displayStopReason(String stopReason) {
        if (stopReason == null || stopReason.trim().isEmpty()) {
            return "寻优完成";
        }
        if ("STABLE_INSIDE_RANGE".equals(stopReason)) {
            return "已稳定收敛";
        }
        if ("MAX_EVALUATION_BUDGET_REACHED".equals(stopReason)) {
            return "已达到候选评估上限";
        }
        if ("MAX_RESOURCE_LIMIT_REACHED".equals(stopReason)) {
            return "已达到资源搜索上限";
        }
        if ("MAX_ROUND_REACHED".equals(stopReason)) {
            return "已达到搜索轮次上限";
        }
        if ("NO_VALID_RESULT".equals(stopReason)) {
            return "未找到有效候选";
        }
        if ("CANCELLED".equals(stopReason)) {
            return "寻优已取消";
        }
        return stopReason;
    }

    private OptimizeConfig buildOptimizeConfigFromForm() {
        OptimizeConfig config = new OptimizeConfig();
        config.totalPopulation = parsePositiveInt(totalPopulationField, "全天总就餐人数");
        AdvancedOptimizationSettings.applyTo(config);
        config.verboseConsoleLog = false;
        config.runReplayAfterOptimization = false;
        AdvancedOptimizationSettings.applyRuntimeProfileForOptimization(config.totalPopulation);
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
        if (running) {
            progressBar.setString("0/" + config.totalCandidateCount());
        }
        if (running) {
            statusLabel.setText("全天自适应寻优中：" + config.totalCandidateCount() + " 个候选上限 / "
                    + config.totalSimulationCount() + " 次仿真上限");
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
        completionValue.setText("--");
        netProfitValue.setText("--");
        seatWaitValue.setText("--");
        scoreValue.setText("--");
        seatUseValue.setText("--");
        windowUseValue.setText("--");
        comparisonValue.setText("等待寻优结果");
        topKTableModel.setRowCount(0);
        topKTableModel.addRow(new Object[]{"等待寻优结果生成", "-", "-", "-", "-", "-", "-", "-", "-", "-"});
        sortingWarningLabel.setText(" ");
        lossCurvePanel.setResults(Collections.emptyList());
        heatmapPanel.setResults(Collections.emptyList(), null, null);
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
        updateComparison(result.currentResult, result.bestResult);
        lossCurvePanel.setResults(result.allResults);
        heatmapPanel.setResults(result.allResults, result.currentResult, result.bestResult);
        currentBestResult = best.copyBasic();
        if (replayContextConsumer != null) {
            replayContextConsumer.accept(currentBestResult.copyBasic());
        }
        notifyOptimizationSummary(result.currentResult, result.bestResult, result.topKResults, result.allResults);
        importBestButton.setEnabled(replayPresetConsumer != null);
        importSelectedTopKButton.setEnabled(replayPresetConsumer != null && !displayedTopKResults.isEmpty());
        String stopReason = result.stopReason == null || result.stopReason.isEmpty()
                ? "寻优完成"
                : result.stopReason;
        statusLabel.setText(stopReason + "，实际评估 " + result.allResults.size()
                + " 个候选，轮次 " + result.searchRounds);
    }

    private void fillTopKTable(List<SimRunResult> topKResults) {
        topKTableModel.setRowCount(0);
        displayedTopKResults = snapshot(topKResults);
        if (displayedTopKResults.isEmpty()) {
            topKTableModel.addRow(new Object[]{"等待寻优结果生成", "-", "-", "-", "-", "-", "-", "-", "-", "-"});
            sortingWarningLabel.setText(" ");
            return;
        }
        for (int i = 0; i < displayedTopKResults.size(); i++) {
            SimRunResult r = displayedTopKResults.get(i);
            topKTableModel.addRow(new Object[]{
                    i + 1,
                    r.windowCount,
                    r.tableCount,
                    formatPercent(r.finishRate),
                    formatMoney(r.netProfit),
                    formatDouble(r.avgWaitTimeMinutes, 2) + " 分钟",
                    formatDouble(r.avgSeatWaitTimeMinutes, 2) + " 分钟",
                    r.abandonedStudents,
                    formatDouble(r.score, 4),
                    safeReason(r)
            });
        }
        sortingWarningLabel.setText(" ");
    }

    private void updateResultPreview(List<SimRunResult> allResults, SimRunResult best) {
        if (best != null) {
            currentBestResult = best.copyBasic();
            updateBestCards(best);
        }
        List<SimRunResult> topResults = snapshot(allResults);
        topResults.sort(OptimizeResult.comparatorFor(currentOptimizationMode()));
        if (topResults.size() > currentTopK) {
            topResults = new ArrayList<>(topResults.subList(0, currentTopK));
        }
        fillTopKTable(topResults);
        lossCurvePanel.setResults(allResults);
        heatmapPanel.setResults(allResults, null, best);
        notifyOptimizationSummary(null, best, topResults, allResults);
    }

    private OptimizationMode currentOptimizationMode() {
        return OptimizationMode.fromDisplayName(AdvancedOptimizationSettings.optimizationModeDisplayName);
    }

    private void updateBestCards(SimRunResult best) {
        bestWindowValue.setText(String.valueOf(best.windowCount));
        bestTableValue.setText(String.valueOf(best.tableCount));
        avgWaitValue.setText(formatDouble(best.avgWaitTimeMinutes, 2) + " 分钟");
        seatWaitValue.setText(formatDouble(best.avgSeatWaitTimeMinutes, 2) + " 分钟");
        netProfitValue.setText(formatMoney(best.netProfit));
        completionValue.setText(formatPercent(best.finishRate));
        scoreValue.setText(formatDouble(best.score, 4));
        seatUseValue.setText(formatPercent(best.seatUtilization));
        windowUseValue.setText(formatPercent(best.windowUtilization));
    }

    private void updateComparison(SimRunResult current, SimRunResult best) {
        if (current == null || best == null) {
            comparisonValue.setText("当前方案对比数据不足");
            return;
        }
        comparisonValue.setText(String.format(
                "<html>当前 <b>%d窗/%d桌</b> -> 推荐 <b>%d窗/%d桌</b> | 完成率 <span style='color:%s'>%s</span>"
                        + " | 净收益 <span style='color:%s'>%s</span> | 放弃人数 <span style='color:%s'>%s</span>"
                        + " | 排队等待 <span style='color:%s'>%s</span> | 等座等待 <span style='color:%s'>%s</span></html>",
                current.windowCount,
                current.tableCount,
                best.windowCount,
                best.tableCount,
                deltaColor(best.finishRate - current.finishRate, true),
                deltaText(best.finishRate - current.finishRate, signedPercent(best.finishRate - current.finishRate)),
                deltaColor(best.netProfit - current.netProfit, true),
                deltaText(best.netProfit - current.netProfit, signedMoney(best.netProfit - current.netProfit)),
                deltaColor(best.abandonedStudents - current.abandonedStudents, false),
                deltaText(best.abandonedStudents - current.abandonedStudents, signedInteger(best.abandonedStudents - current.abandonedStudents)),
                deltaColor(best.avgWaitTimeMinutes - current.avgWaitTimeMinutes, false),
                deltaText(best.avgWaitTimeMinutes - current.avgWaitTimeMinutes, signedMinutes(best.avgWaitTimeMinutes - current.avgWaitTimeMinutes)),
                deltaColor(best.avgSeatWaitTimeMinutes - current.avgSeatWaitTimeMinutes, false),
                deltaText(best.avgSeatWaitTimeMinutes - current.avgSeatWaitTimeMinutes, signedMinutes(best.avgSeatWaitTimeMinutes - current.avgSeatWaitTimeMinutes))
        ));
    }

    private String deltaColor(double delta, boolean higherIsBetter) {
        if (Math.abs(delta) < 0.000001) {
            return "#64748b";
        }
        boolean improved = higherIsBetter ? delta > 0 : delta < 0;
        return improved ? "#16a34a" : "#dc2626";
    }

    private String signedPercent(double delta) {
        return String.format("%+.1f%%", delta * 100.0);
    }

    private String signedMoney(double delta) {
        return String.format("%+.0f 元", delta);
    }

    private String signedInteger(int delta) {
        return String.format("%+d 人", delta);
    }

    private String signedMinutes(double delta) {
        return String.format("%+.2f 分", delta);
    }

    private String deltaText(double delta, String formattedValue) {
        if (Math.abs(delta) < 0.000001) {
            return "0";
        }
        return (delta > 0 ? "↑ " : "↓ ") + formattedValue;
    }

    private void notifyOptimizationSummary(SimRunResult current,
                                           SimRunResult best,
                                           List<SimRunResult> topKResults,
                                           List<SimRunResult> allResults) {
        if (optimizationSummaryConsumer == null) {
            return;
        }
        optimizationSummaryConsumer.accept(
                current == null ? null : current.copyBasic(),
                best == null ? null : best.copyBasic(),
                snapshot(topKResults),
                snapshot(allResults)
        );
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

    private String formatMoney(double value) {
        return String.format("%.0f 元", value);
    }

    private String safeReason(SimRunResult result) {
        if (result.reason == null || result.reason.trim().isEmpty()) {
            return "综合评分最优候选";
        }
        return result.reason;
    }

    private static class TopKTableRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(column == 9 ? SwingConstants.LEFT : SwingConstants.CENTER);

            Object rank = table.getValueAt(row, 0);
            boolean emptyRow = rank != null && rank.toString().startsWith("等待");
            if (!isSelected) {
                component.setBackground(row == 0 && !emptyRow ? ColorTheme.BG_CONTROL : ColorTheme.BG_CARD);
            }
            component.setForeground(ColorTheme.TEXT_PRIMARY);
            if (emptyRow) {
                component.setForeground(ColorTheme.TEXT_MUTED);
            } else if (column == 3 || column == 8) {
                component.setForeground(ColorTheme.ACCENT_BLUE);
            } else if (column == 4) {
                component.setForeground(ColorTheme.ACCENT_YELLOW);
            } else if (column == 5 || column == 6) {
                component.setForeground(ColorTheme.TEXT_SECONDARY);
            }
            return component;
        }
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

            double rawMin = Double.MAX_VALUE;
            double rawMax = -Double.MAX_VALUE;
            double runningBestLoss = Double.MAX_VALUE;
            for (SimRunResult r : results) {
                runningBestLoss = Math.min(runningBestLoss, r.loss);
                rawMin = Math.min(rawMin, Math.min(r.loss, runningBestLoss));
                rawMax = Math.max(rawMax, Math.max(r.loss, runningBestLoss));
            }
            double min = toPlotValue(rawMin);
            double max = toPlotValue(rawMax);
            if (Math.abs(max - min) < 0.000001) {
                max = min + 1.0;
            }

            drawGrid(g2, left, top, plotWidth, plotHeight, rawMin, rawMax);
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
            g2.setFont(ColorTheme.font(Font.PLAIN, 11));
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
            double runningBestLoss = Double.MAX_VALUE;
            for (int i = 0; i < results.size(); i++) {
                SimRunResult r = results.get(i);
                runningBestLoss = Math.min(runningBestLoss, r.loss);
                double value = toPlotValue(bestSeries ? runningBestLoss : r.loss);
                int x = left + (int) Math.round(i * width / (double) (results.size() - 1));
                int y = top + (int) Math.round((max - value) * height / (max - min));
                if (bestSeries && lastX >= 0) {
                    g2.drawLine(lastX, lastY, x, y);
                }
                if (!bestSeries) {
                    g2.fillOval(x - 2, y - 2, 4, 4);
                }
                lastX = x;
                lastY = y;
            }
        }

        private double toPlotValue(double loss) {
            return Math.max(0.0, loss);
        }

        private void drawLegend(Graphics2D g2, int left, int top) {
            g2.setFont(ColorTheme.font(Font.PLAIN, 11));
            g2.setColor(ColorTheme.ACCENT_YELLOW);
            g2.fillOval(left, top - 2, 7, 7);
            g2.setColor(ColorTheme.TEXT_SECONDARY);
            g2.drawString("候选损失点", left + 16, top + 6);
            g2.setColor(ColorTheme.ACCENT_CYAN);
            g2.fillRect(left + 104, top, 10, 4);
            g2.setColor(ColorTheme.TEXT_SECONDARY);
            g2.drawString("历史最低损失", left + 120, top + 6);
        }
    }

    private static class HeatmapPanel extends JPanel {
        private List<SimRunResult> results = Collections.emptyList();
        private SimRunResult current;
        private SimRunResult best;

        private HeatmapPanel() {
            setOpaque(true);
            setBackground(ColorTheme.BG_CARD);
        }

        private void setResults(List<SimRunResult> results, SimRunResult current, SimRunResult best) {
            this.results = results == null ? Collections.emptyList() : new ArrayList<>(results);
            this.current = current == null ? null : current.copyBasic();
            this.best = best == null ? null : best.copyBasic();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (results.isEmpty()) {
                drawEmptyMessage(g2, "候选方案待生成：等待寻优结果");
                g2.dispose();
                return;
            }

            int minWindow = Integer.MAX_VALUE;
            int maxWindow = Integer.MIN_VALUE;
            int minTable = Integer.MAX_VALUE;
            int maxTable = Integer.MIN_VALUE;
            double minMetric = Double.MAX_VALUE;
            double maxMetric = -Double.MAX_VALUE;
            for (SimRunResult r : results) {
                minWindow = Math.min(minWindow, r.windowCount);
                maxWindow = Math.max(maxWindow, r.windowCount);
                minTable = Math.min(minTable, r.tableCount);
                maxTable = Math.max(maxTable, r.tableCount);
                double metric = metric(r);
                minMetric = Math.min(minMetric, metric);
                maxMetric = Math.max(maxMetric, metric);
            }

            int rows = maxWindow - minWindow + 1;
            int cols = maxTable - minTable + 1;
            double[][] metrics = new double[rows][cols];
            for (double[] row : metrics) {
                Arrays.fill(row, Double.NaN);
            }
            for (SimRunResult r : results) {
                int row = r.windowCount - minWindow;
                int col = r.tableCount - minTable;
                double old = metrics[row][col];
                double value = metric(r);
                metrics[row][col] = Double.isNaN(old) ? value : Math.max(old, value);
            }

            int left = 42;
            int right = 12;
            int top = 12;
            int bottom = 28;
            int plotWidth = Math.max(1, getWidth() - left - right);
            int plotHeight = Math.max(1, getHeight() - top - bottom);

            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    double value = metrics[row][col];
                    int x1 = left + (int) Math.floor(col * plotWidth / (double) cols);
                    int x2 = left + (int) Math.floor((col + 1) * plotWidth / (double) cols);
                    int y1 = top + (int) Math.floor(row * plotHeight / (double) rows);
                    int y2 = top + (int) Math.floor((row + 1) * plotHeight / (double) rows);
                    g2.setColor(Double.isNaN(value)
                            ? ColorTheme.BG_ITEM
                            : colorForMetric(value, minMetric, maxMetric));
                    g2.fillRect(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1));
                }
            }

            drawCandidatePoints(g2, minWindow, maxWindow, minTable, maxTable,
                    minMetric, maxMetric, left, top, plotWidth, plotHeight);
            drawHeatmapLabels(g2, minWindow, maxWindow, minTable, maxTable, left, top, plotWidth, plotHeight);
            drawMarker(g2, current, minWindow, maxWindow, minTable, maxTable,
                    left, top, plotWidth, plotHeight, ColorTheme.ACCENT_YELLOW, "当前");
            drawMarker(g2, best, minWindow, maxWindow, minTable, maxTable,
                    left, top, plotWidth, plotHeight, ColorTheme.ACCENT_BLUE, "推荐");
            drawLegend(g2, left + plotWidth - 132, top + 4);
            g2.dispose();
        }

        private void drawHeatmapLabels(Graphics2D g2, int minWindow, int maxWindow, int minTable, int maxTable,
                                       int left, int top, int width, int height) {
            g2.setColor(ColorTheme.TEXT_SECONDARY);
            g2.setFont(ColorTheme.font(Font.PLAIN, 11));
            g2.drawString("窗 " + minWindow, 6, top + 12);
            g2.drawString("窗 " + maxWindow, 6, top + height);
            g2.drawString("桌 " + minTable, left, top + height + 20);
            g2.drawString("桌 " + maxTable, left + width - 42, top + height + 20);
        }

        private void drawCandidatePoints(Graphics2D g2,
                                         int minWindow, int maxWindow,
                                         int minTable, int maxTable,
                                         double minMetric, double maxMetric,
                                         int left, int top, int width, int height) {
            for (SimRunResult result : results) {
                int x = scalePoint(result.tableCount, minTable, maxTable, left, width);
                int y = scalePoint(result.windowCount, minWindow, maxWindow, top, height);
                g2.setColor(colorForMetric(metric(result), minMetric, maxMetric));
                g2.fillOval(x - 4, y - 4, 8, 8);
                g2.setColor(Color.WHITE);
                g2.drawOval(x - 4, y - 4, 8, 8);
            }
        }

        private int scalePoint(int value, int min, int max, int start, int size) {
            if (max == min) {
                return start + size / 2;
            }
            return start + (int) Math.round((value - min) * size / (double) (max - min));
        }

        private void drawMarker(Graphics2D g2, SimRunResult result,
                                int minWindow, int maxWindow, int minTable, int maxTable,
                                int left, int top, int width, int height,
                                Color color, String label) {
            if (result == null
                    || result.windowCount < minWindow
                    || result.windowCount > maxWindow
                    || result.tableCount < minTable
                    || result.tableCount > maxTable) {
                return;
            }
            double col = result.tableCount - minTable + 0.5;
            double row = result.windowCount - minWindow + 0.5;
            double cols = maxTable - minTable + 1.0;
            double rows = maxWindow - minWindow + 1.0;
            int x = left + (int) Math.round(col * width / cols);
            int y = top + (int) Math.round(row * height / rows);
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(Color.WHITE);
            g2.fillOval(x - 7, y - 7, 14, 14);
            g2.setColor(color);
            g2.drawOval(x - 7, y - 7, 14, 14);
            g2.fillOval(x - 3, y - 3, 6, 6);
            g2.setFont(ColorTheme.font(Font.BOLD, 11));
            g2.drawString(label, Math.min(getWidth() - 34, x + 8), Math.max(12, y - 6));
        }

        private void drawLegend(Graphics2D g2, int x, int y) {
            g2.setFont(ColorTheme.font(Font.PLAIN, 11));
            g2.setColor(ColorTheme.TEXT_SECONDARY);
            g2.drawString("颜色=已评估候选得分，空白=未搜索", Math.max(8, x - 72), y + 10);
        }

        private double metric(SimRunResult result) {
            return Math.abs(result.score) > 0.000001 ? result.score : result.finishRate;
        }

        private Color colorForMetric(double value, double minMetric, double maxMetric) {
            if (Math.abs(maxMetric - minMetric) < 0.000001) {
                return ColorTheme.ACCENT_CYAN;
            }
            double ratio = Math.max(0.0, Math.min(1.0, (value - minMetric) / (maxMetric - minMetric)));
            if (ratio < 0.5) {
                return blend(new Color(219, 234, 254), ColorTheme.ACCENT_CYAN, ratio * 2.0);
            }
            return blend(ColorTheme.ACCENT_CYAN, ColorTheme.ACCENT_BLUE, (ratio - 0.5) * 2.0);
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
        g2.setFont(ColorTheme.font(Font.PLAIN, 13));
        int width = g2.getFontMetrics().stringWidth(message);
        g2.drawString(message, Math.max(8, (g2.getClipBounds().width - width) / 2),
                Math.max(20, g2.getClipBounds().height / 2));
    }
}
