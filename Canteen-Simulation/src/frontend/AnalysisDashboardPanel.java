package frontend;

import backend.dto.SimulationSnapshot;
import backend.optimize.SimRunResult;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
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
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Bottom analytics band. It renders optimizer and simulation data already
 * produced by the backend; no optimization calculation is performed here.
 */
public class AnalysisDashboardPanel extends JPanel {
    private final DefaultTableModel comparisonModel = new DefaultTableModel(
            new Object[]{"指标", "当前方案", "推荐方案", "变化"},
            0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable comparisonTable = new JTable(comparisonModel);
    private final TrendGraphPanel trendPanel = new TrendGraphPanel();
    private final PlanHeatmapPanel heatmapPanel = new PlanHeatmapPanel();
    private final PlanComparePanel planComparePanel = new PlanComparePanel();
    private final JLabel sortingWarningLabel = new JLabel(" ");

    private SimulationSnapshot latestSnapshot;
    private SimRunResult currentResult;
    private SimRunResult bestResult;
    private List<SimRunResult> topKResults = Collections.emptyList();
    private List<SimRunResult> allResults = Collections.emptyList();
    private String noticeMessage;
    private boolean currentPlanPendingMetrics;
    private boolean recommendationReplayActive;

    public AnalysisDashboardPanel() {
        super(new BorderLayout(12, 12));
        setOpaque(false);
        setPreferredSize(new Dimension(0, 290));

        JPanel upper = new JPanel(new ResponsiveGridLayout(300, 12, 12));
        upper.setOpaque(false);
        upper.add(createTableCard("当前/推荐差异", comparisonTable));
        upper.add(createHeatmapCard());
        upper.add(createTrendCard());
        upper.add(createPlanCompareCard());
        add(upper, BorderLayout.CENTER);

        configureComparisonTable();
        refreshTables();
    }

    public void updateSnapshot(SimulationSnapshot snapshot) {
        latestSnapshot = snapshot;
        if (snapshot != null) {
            trendPanel.setTrendPoints(snapshot.trendPoints);
            if (recommendationReplayActive) {
                applySnapshotMetrics(snapshot, currentResult);
            } else {
                currentPlanPendingMetrics = false;
                applySnapshotMetrics(snapshot, currentResult);
            }
            if (noticeMessage != null && bestResult == null) {
                noticeMessage = "推荐方案已过期，请重新寻优";
            }
        }
        heatmapPanel.setData(currentResult, bestResult, allResults);
        planComparePanel.setData(currentResult, bestResult, topKResults, allResults);
        refreshTables();
    }

    public void updateOptimization(SimRunResult result) {
        updateOptimization(null, result, null, null);
    }

    public void updateOptimization(SimRunResult current,
                                   SimRunResult best,
                                   List<SimRunResult> topK,
                                   List<SimRunResult> all) {
        noticeMessage = null;
        recommendationReplayActive = false;
        currentResult = current == null ? null : current.copyBasic();
        currentPlanPendingMetrics = false;
        bestResult = best == null ? null : best.copyBasic();
        topKResults = topK == null ? Collections.emptyList() : snapshot(topK);
        allResults = all == null ? Collections.emptyList() : snapshot(all);
        heatmapPanel.setData(currentResult, bestResult, allResults);
        sortingWarningLabel.setText(" ");
        planComparePanel.setData(currentResult, bestResult, topKResults, allResults);
        refreshTables();
    }

    public void clear() {
        latestSnapshot = null;
        currentResult = null;
        bestResult = null;
        currentPlanPendingMetrics = false;
        recommendationReplayActive = false;
        topKResults = Collections.emptyList();
        allResults = Collections.emptyList();
        noticeMessage = null;
        trendPanel.setTrendPoints(null);
        heatmapPanel.setData(null, null, null);
        planComparePanel.setData(null, null, null, null);
        sortingWarningLabel.setText(" ");
        refreshTables();
    }

    public void markParametersChanged(String message) {
        latestSnapshot = null;
        currentResult = null;
        bestResult = null;
        currentPlanPendingMetrics = false;
        recommendationReplayActive = false;
        topKResults = Collections.emptyList();
        allResults = Collections.emptyList();
        noticeMessage = message == null || message.trim().isEmpty()
                ? "参数已变化，请重新仿真或启动寻优"
                : message;
        trendPanel.setTrendPoints(null);
        heatmapPanel.setData(null, null, null);
        planComparePanel.setData(null, null, null, null);
        sortingWarningLabel.setText(noticeMessage);
        refreshTables();
    }

    public void updateCurrentPlan(int windowCount, int tableCount) {
        SimRunResult plan = new SimRunResult();
        plan.windowCount = windowCount;
        plan.tableCount = tableCount;
        currentResult = plan;
        currentPlanPendingMetrics = true;
        recommendationReplayActive = false;
        refreshTables();
    }

    public void updateRecommendation(SimRunResult best,
                                     List<SimRunResult> topK,
                                     List<SimRunResult> all) {
        noticeMessage = null;
        recommendationReplayActive = false;
        bestResult = best == null ? null : best.copyBasic();
        topKResults = topK == null ? Collections.emptyList() : snapshot(topK);
        allResults = all == null ? Collections.emptyList() : snapshot(all);
        heatmapPanel.setData(currentResult, bestResult, allResults);
        sortingWarningLabel.setText(" ");
        planComparePanel.setData(currentResult, bestResult, topKResults, allResults);
        refreshTables();
    }

    public void beginCandidateReplay(SimRunResult current,
                                     SimRunResult recommendation,
                                     List<SimRunResult> topK,
                                     List<SimRunResult> all) {
        latestSnapshot = null;
        noticeMessage = null;
        currentResult = current == null ? null : current.copyBasic();
        bestResult = recommendation == null ? null : recommendation.copyBasic();
        topKResults = topK == null ? Collections.emptyList() : snapshot(topK);
        allResults = all == null ? Collections.emptyList() : snapshot(all);
        currentPlanPendingMetrics = false;
        recommendationReplayActive = true;
        sortingWarningLabel.setText(" ");
        heatmapPanel.setData(currentResult, bestResult, allResults);
        planComparePanel.setData(currentResult, bestResult, topKResults, allResults);
        refreshTables();
    }

    private void applySnapshotMetrics(SimulationSnapshot snapshot, SimRunResult target) {
        if (snapshot == null || target == null) {
            return;
        }
        target.requestedPopulation = snapshot.totalStudents;
        target.finishRate = snapshot.completionRate;
        target.abandonedStudents = snapshot.abandonedCount;
        target.netProfit = snapshot.netProfit;
        target.avgWaitTimeSeconds = snapshot.avgQueueWaitSeconds;
        target.avgWaitTimeMinutes = snapshot.avgQueueWaitSeconds / 60.0;
        target.avgSeatWaitTimeSeconds = snapshot.avgSeatWaitSeconds;
        target.avgSeatWaitTimeMinutes = snapshot.avgSeatWaitSeconds / 60.0;
        target.seatUtilization = snapshot.seatUtilizationRate;
        target.windowUtilization = snapshot.windowUtilizationRate;
    }

    private JPanel createTableCard(String title, JTable table) {
        JPanel card = createCard(title);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT));
        scrollPane.getViewport().setBackground(ColorTheme.BG_CARD);
        card.add(scrollPane, BorderLayout.CENTER);
        return card;
    }

    private JPanel createHeatmapCard() {
        JPanel card = createCard("候选方案搜索分布图");
        card.add(heatmapPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createTrendCard() {
        JPanel card = createCard("关键趋势");
        card.add(trendPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createPlanCompareCard() {
        JPanel card = createCard("方案对比");
        sortingWarningLabel.setForeground(ColorTheme.ACCENT_RED);
        sortingWarningLabel.setFont(ColorTheme.font(Font.BOLD, 12));
        card.add(planComparePanel, BorderLayout.CENTER);
        card.add(sortingWarningLabel, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createCard(String title) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(ColorTheme.BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        JLabel label = new JLabel(title);
        label.setForeground(ColorTheme.TEXT_PRIMARY);
        label.setFont(ColorTheme.font(Font.BOLD, 14));
        card.add(label, BorderLayout.NORTH);
        return card;
    }

    private void configureComparisonTable() {
        configureBaseTable(comparisonTable);
        comparisonTable.getColumnModel().getColumn(3).setCellRenderer(new DeltaRenderer());
    }

    private void configureBaseTable(JTable table) {
        table.setRowHeight(24);
        table.setFont(ColorTheme.font(Font.PLAIN, 12));
        table.getTableHeader().setFont(ColorTheme.font(Font.BOLD, 12));
        table.getTableHeader().setBackground(ColorTheme.BG_ITEM);
        table.getTableHeader().setForeground(ColorTheme.TEXT_PRIMARY);
        table.getTableHeader().setReorderingAllowed(false);
        table.setBackground(ColorTheme.BG_CARD);
        table.setForeground(ColorTheme.TEXT_PRIMARY);
        table.setGridColor(ColorTheme.BORDER_SOFT);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    }

    private void refreshTables() {
        refreshComparison();
    }

    private void refreshComparison() {
        comparisonModel.setRowCount(0);
        if (noticeMessage != null) {
            comparisonModel.addRow(new Object[]{"状态", noticeMessage, "等待更新", "-"});
            if (bestResult == null && currentResult == null && latestSnapshot == null) {
                return;
            }
        }
        if (bestResult == null && currentResult == null && latestSnapshot == null) {
            comparisonModel.addRow(new Object[]{"空状态", "等待仿真", "等待寻优", "-"});
            return;
        }

        MetricSource current = sourceFromCurrent();
        MetricSource best = bestResult == null ? null : MetricSource.from(bestResult);
        addComparisonRow("资源配置", currentPlanText(), bestPlanText(), planDeltaText());
        addComparisonRow("完成率", current == null ? null : percent(current.completionRate),
                best == null ? null : percent(best.completionRate),
                diff(current == null ? null : current.completionRate,
                        best == null ? null : best.completionRate,
                        true, "percent"));
        addComparisonRow("放弃人数", current == null ? null : current.abandonedCount + " 人",
                best == null ? null : best.abandonedCount + " 人",
                diffCount(current == null ? null : current.abandonedCount,
                        best == null ? null : best.abandonedCount,
                        false));
        addComparisonRow("净收益", current == null ? null : money(current.netProfit),
                best == null ? null : money(best.netProfit),
                diff(current == null ? null : current.netProfit,
                        best == null ? null : best.netProfit,
                        true, "money"));
        addComparisonRow("排队等待", current == null ? null : minutes(current.queueWaitSeconds),
                best == null ? null : minutes(best.queueWaitSeconds),
                diff(current == null ? null : current.queueWaitSeconds,
                        best == null ? null : best.queueWaitSeconds,
                        false, "seconds"));
        addComparisonRow("等座等待", current == null ? null : minutes(current.seatWaitSeconds),
                best == null ? null : minutes(best.seatWaitSeconds),
                diff(current == null ? null : current.seatWaitSeconds,
                        best == null ? null : best.seatWaitSeconds,
                        false, "seconds"));
    }

    private void addComparisonRow(String metric, String current, String best, String delta) {
        comparisonModel.addRow(new Object[]{
                metric,
                current == null ? "等待仿真" : current,
                best == null ? "等待寻优" : best,
                delta
        });
    }

    private MetricSource sourceFromCurrent() {
        if (recommendationReplayActive && currentResult != null) {
            return MetricSource.from(currentResult);
        }
        if (latestSnapshot != null) {
            return MetricSource.from(latestSnapshot);
        }
        if (currentPlanPendingMetrics) {
            return null;
        }
        if (currentResult != null) {
            return MetricSource.from(currentResult);
        }
        return null;
    }

    private String currentPlanText() {
        if (currentResult == null) {
            return latestSnapshot == null ? "等待仿真" : "实时仿真中";
        }
        return currentResult.windowCount + " 窗 / " + currentResult.tableCount + " 桌";
    }

    private String bestPlanText() {
        if (bestResult == null) {
            return "等待寻优";
        }
        return bestResult.windowCount + " 窗 / " + bestResult.tableCount + " 桌";
    }

    private String planDeltaText() {
        if (currentResult == null || bestResult == null) {
            return "-";
        }
        int windowDelta = bestResult.windowCount - currentResult.windowCount;
        int tableDelta = bestResult.tableCount - currentResult.tableCount;
        if (windowDelta == 0 && tableDelta == 0) {
            return "0";
        }
        return String.format(Locale.US, "窗 %+d / 桌 %+d", windowDelta, tableDelta);
    }

    private String diff(Double current, Double best, boolean higherIsBetter, String valueType) {
        if (current == null || best == null) {
            return "-";
        }
        double delta = best - current;
        double epsilon = "percent".equals(valueType) ? 0.0001 : 0.01;
        if (Math.abs(delta) < epsilon) {
            return "0";
        }
        boolean improved = higherIsBetter ? delta > 0 : delta < 0;
        String arrow = delta > 0 ? "↑ " : "↓ ";
        String value;
        if ("percent".equals(valueType)) {
            value = String.format(Locale.US, "%+.1f%%", delta * 100.0);
        } else if ("money".equals(valueType)) {
            value = String.format(Locale.US, "%+.0f 元", delta);
        } else {
            value = String.format(Locale.US, "%+.1f 分", delta / 60.0);
        }
        return (improved ? "改善 " : "变差 ") + arrow + value;
    }

    private String diffCount(Integer current, Integer best, boolean higherIsBetter) {
        if (current == null || best == null) {
            return "-";
        }
        int delta = best - current;
        if (delta == 0) {
            return "0";
        }
        boolean improved = higherIsBetter ? delta > 0 : delta < 0;
        String arrow = delta > 0 ? "↑ " : "↓ ";
        return (improved ? "改善 " : "变差 ") + arrow + String.format(Locale.US, "%+d 人", delta);
    }

    private List<SimRunResult> snapshot(List<SimRunResult> source) {
        if (source == null) {
            return Collections.emptyList();
        }
        List<SimRunResult> copies = new ArrayList<>();
        for (SimRunResult result : source) {
            copies.add(result.copyBasic());
        }
        return copies;
    }

    private String percent(double value) {
        return String.format(Locale.US, "%.1f%%", value * 100.0);
    }

    private String minutes(double seconds) {
        return String.format(Locale.US, "%.1f 分", seconds / 60.0);
    }

    private String money(double value) {
        return String.format(Locale.US, "%.0f 元", value);
    }

    private static class MetricSource {
        private final double completionRate;
        private final int abandonedCount;
        private final double netProfit;
        private final double queueWaitSeconds;
        private final double seatWaitSeconds;

        private MetricSource(double completionRate, int abandonedCount, double netProfit,
                             double queueWaitSeconds, double seatWaitSeconds) {
            this.completionRate = completionRate;
            this.abandonedCount = abandonedCount;
            this.netProfit = netProfit;
            this.queueWaitSeconds = queueWaitSeconds;
            this.seatWaitSeconds = seatWaitSeconds;
        }

        private static MetricSource from(SimulationSnapshot snapshot) {
            return new MetricSource(snapshot.completionRate, snapshot.abandonedCount, snapshot.netProfit,
                    snapshot.avgQueueWaitSeconds, snapshot.avgSeatWaitSeconds);
        }

        private static MetricSource from(SimRunResult result) {
            return new MetricSource(result.finishRate, result.abandonedStudents, result.netProfit,
                    result.avgWaitTimeSeconds > 0.0 ? result.avgWaitTimeSeconds : result.avgWaitTimeMinutes * 60.0,
                    result.avgSeatWaitTimeSeconds > 0.0
                            ? result.avgSeatWaitTimeSeconds
                            : result.avgSeatWaitTimeMinutes * 60.0);
        }
    }

    private class DeltaRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            String text = value == null ? "" : value.toString();
            component.setForeground(ColorTheme.TEXT_SECONDARY);
            if (text.startsWith("改善")) {
                component.setForeground(ColorTheme.ACCENT_GREEN);
            } else if (text.startsWith("变差")) {
                component.setForeground(ColorTheme.ACCENT_RED);
            }
            return component;
        }
    }

    private static class PlanComparePanel extends JPanel {
        private List<SimRunResult> plans = Collections.emptyList();

        private PlanComparePanel() {
            setBackground(ColorTheme.BG_PANEL);
        }

        private void setData(SimRunResult current,
                             SimRunResult best,
                             List<SimRunResult> candidates,
                             List<SimRunResult> allResults) {
            List<SimRunResult> next = new ArrayList<>();
            addPlan(next, current);
            addPlan(next, best);
            List<SimRunResult> source = candidates != null && !candidates.isEmpty()
                    ? candidates
                    : allResults;
            if (source != null) {
                for (SimRunResult result : source) {
                    addPlan(next, result);
                    if (next.size() >= 4) {
                        break;
                    }
                }
            }
            plans = next;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int left = 36;
            int right = 16;
            int top = 18;
            int bottom = 48;
            int width = Math.max(1, getWidth() - left - right);
            int height = Math.max(1, getHeight() - top - bottom);

            g2.setColor(ColorTheme.BORDER_SOFT);
            g2.drawLine(left, top, left, top + height);
            g2.drawLine(left, top + height, left + width, top + height);
            g2.setFont(ColorTheme.font(Font.PLAIN, 11));
            g2.setColor(ColorTheme.TEXT_SECONDARY);
            g2.drawString("完成率(%)", left, 12);
            g2.drawString("净收益(元)", Math.max(left + width - 58, left + 80), 12);

            if (plans.isEmpty()) {
                drawEmpty(g2, "等待寻优结果生成");
                g2.dispose();
                return;
            }

            double maxProfit = 1.0;
            for (SimRunResult plan : plans) {
                maxProfit = Math.max(maxProfit, Math.max(0.0, plan.netProfit));
            }
            int count = plans.size();
            int slot = Math.max(1, width / count);
            int barWidth = Math.max(18, Math.min(42, slot / 2));
            for (int i = 0; i < count; i++) {
                SimRunResult plan = plans.get(i);
                int centerX = left + slot * i + slot / 2;
                double completion = Math.max(0.0, Math.min(1.0, plan.finishRate));
                int barHeight = (int) Math.round(completion * height);
                int barX = centerX - barWidth / 2;
                int barY = top + height - barHeight;
                Color barColor = i == 1 ? ColorTheme.ACCENT_BLUE : new Color(191, 219, 254);
                g2.setColor(barColor);
                g2.fillRoundRect(barX, barY, barWidth, barHeight, 8, 8);
                g2.setColor(new Color(147, 197, 253));
                g2.drawRoundRect(barX, barY, barWidth, barHeight, 8, 8);

                int profitY = top + height - (int) Math.round(Math.max(0.0, plan.netProfit) / maxProfit * height);
                g2.setColor(ColorTheme.ACCENT_YELLOW);
                g2.fillOval(centerX + barWidth / 2 - 5, profitY - 5, 10, 10);

                g2.setColor(ColorTheme.TEXT_SECONDARY);
                String label = labelFor(i);
                int labelWidth = g2.getFontMetrics().stringWidth(label);
                g2.drawString(label, centerX - labelWidth / 2, top + height + 17);
                String config = plan.windowCount + "/" + plan.tableCount;
                int configWidth = g2.getFontMetrics().stringWidth(config);
                g2.drawString(config, centerX - configWidth / 2, top + height + 34);
            }

            g2.setColor(ColorTheme.ACCENT_BLUE);
            g2.fillRect(left + 4, getHeight() - 13, 10, 8);
            g2.setColor(ColorTheme.TEXT_SECONDARY);
            g2.drawString("完成率", left + 18, getHeight() - 5);
            g2.setColor(ColorTheme.ACCENT_YELLOW);
            g2.fillOval(left + 74, getHeight() - 13, 9, 9);
            g2.setColor(ColorTheme.TEXT_SECONDARY);
            g2.drawString("净收益", left + 88, getHeight() - 5);
            g2.dispose();
        }

        private void addPlan(List<SimRunResult> target, SimRunResult plan) {
            if (plan == null) {
                return;
            }
            for (SimRunResult existing : target) {
                if (existing.windowCount == plan.windowCount && existing.tableCount == plan.tableCount) {
                    return;
                }
            }
            target.add(plan.copyBasic());
        }

        private String labelFor(int index) {
            if (index == 0) {
                return "当前";
            }
            if (index == 1) {
                return "推荐";
            }
            return "方案" + (char) ('A' + index - 2);
        }

        private void drawEmpty(Graphics2D g2, String message) {
            g2.setColor(ColorTheme.TEXT_SECONDARY);
            g2.setFont(ColorTheme.font(Font.PLAIN, 13));
            int textWidth = g2.getFontMetrics().stringWidth(message);
            g2.drawString(message, Math.max(8, (getWidth() - textWidth) / 2),
                    Math.max(20, getHeight() / 2));
        }
    }

    private static class PlanHeatmapPanel extends JPanel {
        private SimRunResult current;
        private SimRunResult best;
        private List<SimRunResult> results = Collections.emptyList();

        private PlanHeatmapPanel() {
            setBackground(ColorTheme.BG_PANEL);
        }

        private void setData(SimRunResult current, SimRunResult best, List<SimRunResult> results) {
            this.current = current == null ? null : current.copyBasic();
            this.best = best == null ? null : best.copyBasic();
            this.results = results == null ? Collections.emptyList() : new ArrayList<>(results);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int left = 48;
            int right = 18;
            int top = 16;
            int bottom = 34;
            int width = Math.max(1, getWidth() - left - right);
            int height = Math.max(1, getHeight() - top - bottom);

            if (results.isEmpty()) {
                drawEmpty(g2, "候选方案待生成");
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
            int[] windowRange = centeredRange(minWindow, maxWindow,
                    best == null ? null : best.windowCount,
                    current == null ? null : current.windowCount,
                    6);
            int[] tableRange = centeredRange(minTable, maxTable,
                    best == null ? null : best.tableCount,
                    current == null ? null : current.tableCount,
                    40);
            minWindow = windowRange[0];
            maxWindow = windowRange[1];
            minTable = tableRange[0];
            maxTable = tableRange[1];

            int rows = Math.max(1, maxWindow - minWindow + 1);
            int cols = Math.max(1, maxTable - minTable + 1);
            double[][] metrics = new double[rows][cols];
            for (double[] row : metrics) {
                Arrays.fill(row, Double.NaN);
            }
            for (SimRunResult r : results) {
                int row = r.windowCount - minWindow;
                int col = r.tableCount - minTable;
                if (row < 0 || row >= rows || col < 0 || col >= cols) {
                    continue;
                }
                double old = metrics[row][col];
                double value = metric(r);
                metrics[row][col] = Double.isNaN(old) ? value : Math.max(old, value);
            }

            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    double value = metrics[row][col];
                    int x1 = left + (int) Math.floor(col * width / (double) cols);
                    int x2 = left + (int) Math.floor((col + 1) * width / (double) cols);
                    int y1 = top + (int) Math.floor(row * height / (double) rows);
                    int y2 = top + (int) Math.floor((row + 1) * height / (double) rows);
                    g2.setColor(Double.isNaN(value)
                            ? ColorTheme.BG_ITEM
                            : colorForMetric(value, minMetric, maxMetric));
                    if (Double.isNaN(value)) {
                        g2.fillRect(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1));
                    } else {
                        int markerSize = Math.max(6, Math.min(12, Math.min(x2 - x1, y2 - y1)));
                        int markerX = x1 + Math.max(0, (x2 - x1 - markerSize) / 2);
                        int markerY = y1 + Math.max(0, (y2 - y1 - markerSize) / 2);
                        g2.fillRoundRect(markerX, markerY, markerSize, markerSize, 6, 6);
                    }
                }
            }

            drawCandidatePoints(g2, minWindow, maxWindow, minTable, maxTable,
                    minMetric, maxMetric, left, top, width, height);
            drawAxes(g2, minWindow, maxWindow, minTable, maxTable, left, top, width, height);
            drawMarker(g2, current, minWindow, maxWindow, minTable, maxTable,
                    left, top, width, height, ColorTheme.ACCENT_YELLOW, "当前");
            drawMarker(g2, best, minWindow, maxWindow, minTable, maxTable,
                    left, top, width, height, ColorTheme.ACCENT_BLUE, "推荐");
            g2.dispose();
        }

        private int[] centeredRange(int rawMin,
                                    int rawMax,
                                    Integer centerValue,
                                    Integer secondaryValue,
                                    int minSpan) {
            if (centerValue == null) {
                return new int[]{rawMin, rawMax};
            }
            int radius = Math.max(1, minSpan / 2);
            radius = Math.max(radius, Math.abs(rawMin - centerValue));
            radius = Math.max(radius, Math.abs(rawMax - centerValue));
            if (secondaryValue != null) {
                radius = Math.max(radius, Math.abs(secondaryValue - centerValue));
            }
            return new int[]{centerValue - radius, centerValue + radius};
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

        private void drawAxes(Graphics2D g2, int minWindow, int maxWindow, int minTable, int maxTable,
                              int left, int top, int width, int height) {
            g2.setColor(ColorTheme.TEXT_SECONDARY);
            g2.setFont(ColorTheme.font(Font.PLAIN, 11));
            g2.drawString("窗口 " + minWindow, 6, top + 12);
            g2.drawString("窗口 " + maxWindow, 6, top + height);
            g2.drawString("餐桌 " + minTable, left, top + height + 22);
            g2.drawString("餐桌 " + maxTable, Math.max(left, left + width - 58), top + height + 22);
            g2.drawString("点色=候选得分，空白=未搜索", Math.max(left + 86, left + width / 2 - 76), top + height + 22);
            g2.setColor(ColorTheme.BORDER_SOFT);
            g2.drawRect(left, top, width, height);
        }

        private double metric(SimRunResult result) {
            return Math.abs(result.score) > 0.000001 ? result.score : result.finishRate;
        }

        private Color colorForMetric(double value, double min, double max) {
            if (Math.abs(max - min) < 0.000001) {
                return new Color(191, 219, 254);
            }
            double ratio = Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
            if (ratio < 0.33) {
                return blend(new Color(226, 232, 240), new Color(125, 211, 252), ratio / 0.33);
            }
            if (ratio < 0.66) {
                return blend(new Color(125, 211, 252), new Color(37, 99, 235), (ratio - 0.33) / 0.33);
            }
            return blend(new Color(37, 99, 235), new Color(15, 23, 42), (ratio - 0.66) / 0.34);
        }

        private Color blend(Color from, Color to, double ratio) {
            int red = (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * ratio);
            int green = (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * ratio);
            int blue = (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * ratio);
            return new Color(red, green, blue);
        }

        private void drawEmpty(Graphics2D g2, String message) {
            g2.setColor(ColorTheme.TEXT_SECONDARY);
            g2.setFont(ColorTheme.font(Font.PLAIN, 13));
            int textWidth = g2.getFontMetrics().stringWidth(message);
            g2.drawString(message, Math.max(8, (getWidth() - textWidth) / 2),
                    Math.max(20, getHeight() / 2));
        }
    }
}
