package frontend;

import backend.dto.TableStat;
import backend.dto.TableStatus;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiningAreaPanel extends JPanel {

    private final Map<Integer, int[]> tableSeatStates = new HashMap<>();
    private final JPanel tablesGridPanel;

    public DiningAreaPanel(int initialTableCount) {
        this.setLayout(new BorderLayout(0, 5));
        this.setBackground(frontend.ColorTheme.BG_CARD);
        this.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JLabel titleLabel = new JLabel("就餐区");
        titleLabel.setFont(ColorTheme.font(Font.BOLD, 15));
        titleLabel.setForeground(frontend.ColorTheme.TEXT_PRIMARY);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 0));
        this.add(titleLabel, BorderLayout.NORTH);

        tablesGridPanel = new JPanel();
        tablesGridPanel.setOpaque(false); // 恢复透明，高级感拉满

        // =========================================
        // 【核心修复：向 QueueAreaPanel 借用的完美架构】
        // =========================================
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.setOpaque(false);
        // 放在 NORTH 释放高度，让滚动条能够精准计算真实高度！
        wrapperPanel.add(tablesGridPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(wrapperPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);

        // FlatLaf 滚动条美化
        scrollPane.getVerticalScrollBar().putClientProperty("ScrollBar.showButtons", false);
        scrollPane.getVerticalScrollBar().putClientProperty("ScrollBar.thumbArc", 999);
        scrollPane.getVerticalScrollBar().putClientProperty("ScrollBar.thumbInsets", new Insets(2, 4, 2, 4));

        this.add(scrollPane, BorderLayout.CENTER);

        updateTableCount(initialTableCount);
    }

    public void updateTableCount(int newTableCount) {
        tablesGridPanel.removeAll();
        tableSeatStates.clear();

        int MAX_COLS = 10;
        int MAX_VISIBLE_ROWS = 5;
        int COMPACT_THRESHOLD = MAX_COLS * MAX_VISIBLE_ROWS; // 50
        double TARGET_RATIO = 1.8;

        int bestCols;
        int bestRows;

        if (newTableCount > COMPACT_THRESHOLD) {
            // 超过 50 张桌子后，不再强行免滚动。
            // 固定每行最多 10 张，保持单桌比例稳定，允许纵向滚动。
            bestCols = MAX_COLS;
            bestRows = (int) Math.ceil((double) newTableCount / bestCols);
        } else {
            // 50 张以内，继续使用原来的智能自适应逻辑。
            bestCols = 1;
            bestRows = newTableCount;

            int minScore = Integer.MAX_VALUE;
            double bestRatioDiff = Double.MAX_VALUE;

            for (int c = 1; c <= MAX_COLS; c++) {
                int r = (int) Math.ceil((double) newTableCount / c);
                int emptySlots = (r * c) - newTableCount;
                int scrollPenalty = 0;

                // 50 张以内优先避免超过 5 行，尽量一屏展示。
                if (r > MAX_VISIBLE_ROWS) {
                    scrollPenalty = 1000;
                }

                int totalScore = scrollPenalty + emptySlots;
                double currentRatio = (double) c / r;
                double ratioDiff = Math.abs(currentRatio - TARGET_RATIO);

                if (totalScore < minScore) {
                    minScore = totalScore;
                    bestCols = c;
                    bestRows = r;
                    bestRatioDiff = ratioDiff;
                } else if (totalScore == minScore) {
                    if (ratioDiff < bestRatioDiff) {
                        bestCols = c;
                        bestRows = r;
                        bestRatioDiff = ratioDiff;
                    }
                }
            }
        }

        tablesGridPanel.setLayout(new GridLayout(bestRows, bestCols, 8, 6));

        for (int i = 0; i < newTableCount; i++) {
            tableSeatStates.put(i, new int[]{-1, -1, -1, -1});
            JPanel tableWrapper = createSingleTable(i + 1);
            tablesGridPanel.add(tableWrapper);
        }

        tablesGridPanel.revalidate();
        tablesGridPanel.repaint();
    }

    private JPanel createSingleTable(int tableId) {
        JPanel tableContainer = new JPanel(new BorderLayout(0, 2));
        tableContainer.setOpaque(false);

        JLabel tableLabel = new JLabel("桌 " + String.format("%02d", tableId));
        tableLabel.setFont(ColorTheme.font(Font.BOLD, 10));
        tableLabel.setForeground(frontend.ColorTheme.TEXT_SECONDARY);
        tableLabel.setHorizontalAlignment(SwingConstants.CENTER);
        tableContainer.add(tableLabel, BorderLayout.NORTH);

        JPanel seatsGrid = new JPanel(new GridLayout(2, 2, 5, 5));
        seatsGrid.setOpaque(false);

        for (int j = 0; j < 4; j++) {
            JPanel seat = new JPanel();
            seat.setBackground(frontend.ColorTheme.BG_EMPTY_SEAT);
            seat.setBorder(BorderFactory.createLineBorder(frontend.ColorTheme.BORDER_SOFT, 1));
            seat.putClientProperty("FlatLaf.style", "arc: 6");
            seat.putClientProperty("seat", Boolean.TRUE);
            seatsGrid.add(seat);
        }

        JPanel MarginPaddingPanel = new JPanel(new BorderLayout());
        MarginPaddingPanel.setOpaque(false);
        MarginPaddingPanel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        MarginPaddingPanel.add(seatsGrid, BorderLayout.CENTER);

        tableContainer.add(MarginPaddingPanel, BorderLayout.CENTER);

        JPanel responsiveWrapper = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                // 物理底线，确保多桌时撑开高度触发滚动条
                return new Dimension(60, 64);
            }

            @Override
            public void doLayout() {
                super.doLayout();
                int w = getWidth();
                int h = getHeight();

                int minW = 50;
                int minH = 54;

                int finalW = Math.max(minW, w);
                int finalH = Math.max(minH, Math.min(h, (int)(w * 0.95)));

                int x = (w - finalW) / 2;
                int y = (h - finalH) / 2;

                if (getComponentCount() > 0) {
                    getComponent(0).setBounds(x, y, finalW, finalH);
                }
            }
        };
        responsiveWrapper.setLayout(null);
        responsiveWrapper.setOpaque(false);
        responsiveWrapper.add(tableContainer);

        return responsiveWrapper;
    }

    public void updateTableOccupancy(int tableIndex, int[] seatGroupIds) {
        if (tableIndex < 0 || tableIndex >= tablesGridPanel.getComponentCount()) {
            return;
        }
        int[] states = new int[4];
        for (int i = 0; i < Math.min(4, seatGroupIds.length); i++) {
            states[i] = seatGroupIds[i];
        }
        tableSeatStates.put(tableIndex, states);
        applySeatStatesToUI(tableIndex, states);
    }

    public void updateTableStats(List<TableStat> stats) {
        if (stats == null) {
            return;
        }
        for (TableStat stat : stats) {
            applyTableStatToUI(stat);
        }
    }

    private void applyTableStatToUI(TableStat stat) {
        if (stat.tableId < 0 || stat.tableId >= tablesGridPanel.getComponentCount()) {
            return;
        }
        Component comp = tablesGridPanel.getComponent(stat.tableId);
        if (!(comp instanceof JPanel)) {
            return;
        }
        JPanel wrapper = (JPanel) comp;
        List<JPanel> seatPanels = new ArrayList<>();
        collectSeatPanels(wrapper, seatPanels);

        Color fill = colorFor(stat.status);
        for (int i = 0; i < seatPanels.size(); i++) {
            JPanel seat = seatPanels.get(i);
            boolean occupied = i < stat.occupiedSeats;
            seat.setBackground(occupied ? fill : ColorTheme.BG_EMPTY_SEAT);
            Color border = stat.status == TableStatus.RELEASING_SOON
                    ? ColorTheme.ACCENT_BLUE
                    : ColorTheme.BORDER_SOFT;
            seat.setBorder(BorderFactory.createLineBorder(border, stat.status == TableStatus.RELEASING_SOON ? 2 : 1));
        }
        String release = stat.expectedReleaseTime < 0
                ? "-"
                : String.format("%d:%02d", stat.expectedReleaseTime / 60, stat.expectedReleaseTime % 60);
        wrapper.setToolTipText("桌 " + (stat.tableId + 1)
                + " | 容量 " + stat.capacity
                + " | 占用 " + stat.occupiedSeats
                + " | 预计释放 " + release);
        wrapper.repaint();
    }

    private Color colorFor(TableStatus status) {
        if (status == TableStatus.PARTIAL) {
            return ColorTheme.ACCENT_YELLOW;
        }
        if (status == TableStatus.NEAR_FULL) {
            return new Color(255, 145, 48);
        }
        if (status == TableStatus.FULL) {
            return ColorTheme.ACCENT_RED;
        }
        if (status == TableStatus.RELEASING_SOON) {
            return ColorTheme.ACCENT_BLUE;
        }
        return ColorTheme.ACCENT_GREEN;
    }

    private void applySeatStatesToUI(int tableIndex, int[] states) {
        Component comp = tablesGridPanel.getComponent(tableIndex);
        if (!(comp instanceof JPanel)) {
            return;
        }

        JPanel wrapper = (JPanel) comp;
        List<JPanel> seatPanels = new ArrayList<>();
        collectSeatPanels(wrapper, seatPanels);

        for (int i = 0; i < Math.min(4, seatPanels.size()); i++) {
            JPanel seat = seatPanels.get(i);
            int groupId = states[i];

            if (groupId >= 0) {
                seat.setBackground(frontend.ColorTheme.SEAT_OCCUPIED);
                seat.setBorder(BorderFactory.createLineBorder(frontend.ColorTheme.SEAT_OCCUPIED.brighter(), 1));
            } else {
                seat.setBackground(frontend.ColorTheme.BG_EMPTY_SEAT);
                seat.setBorder(BorderFactory.createLineBorder(frontend.ColorTheme.BORDER_SOFT, 1));
            }
        }

        wrapper.repaint();
    }

    private void collectSeatPanels(Container container, List<JPanel> result) {
        for (Component c : container.getComponents()) {
            if (c instanceof JPanel) {
                JPanel panel = (JPanel) c;
                Object isSeat = panel.getClientProperty("seat");
                if (Boolean.TRUE.equals(isSeat)) {
                    result.add(panel);
                } else {
                    collectSeatPanels(panel, result);
                }
            }
        }
    }

    public void clearOccupancy() {
        for (int i = 0; i < tablesGridPanel.getComponentCount(); i++) {
            int[] emptySeats = new int[]{-1, -1, -1, -1};
            tableSeatStates.put(i, emptySeats);
            applySeatStatesToUI(i, emptySeats);
        }
    }
}
