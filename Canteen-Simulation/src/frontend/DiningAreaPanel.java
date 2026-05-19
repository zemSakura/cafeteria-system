package frontend;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiningAreaPanel extends JPanel {

    /**
     * 每张桌子的精确座位归属
     * key = tableIndex
     * value = 长度为 4 的 int 数组，-1 表示空位，其他值表示占用该座的 groupId
     */
    private final Map<Integer, int[]> tableSeatStates = new HashMap<>();

    public DiningAreaPanel(int initialTableCount) {
        // 【修改点 1】：用透明的空气墙 (EmptyBorder) 作为基础，再在上面加标题
        javax.swing.border.TitledBorder titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10), // 隐形的 10 像素护城河
                "Dining Area Snapshot (就餐区实时快照)"
        );

        // 【修改点 2】：把标题的颜色改成咱们调色盘里的“高级灰”
        titledBorder.setTitleColor(frontend.ColorTheme.TEXT_SECONDARY);

        this.setBorder(titledBorder);

        // 【修改点 3】：确保整个大面板的底色完美融入环境
        // 这里用最深的 BG_MAIN (金属外壳色)，这样里面的桌子 (BG_CARD) 就能浮现出来
        this.setBackground(frontend.ColorTheme.BG_MAIN);

        updateTableCount(initialTableCount);
    }

    public void updateTableCount(int newTableCount) {
        this.removeAll();
        tableSeatStates.clear();

        // =========================================
        // 【终极排版引擎：多目标权重评分算法】
        // 目标 1：不滑动 (最多 5 行)
        // 目标 2：完美矩形 (最少空位)
        // 目标 3：桌子最大化 (比例接近屏幕 1.8)
        // =========================================
        int MAX_COLS = 10;         // 极限列数限制
        int MAX_VISIBLE_ROWS = 5;  // 超过 5 行必然触发滑动窗口
        double TARGET_RATIO = 1.8; // 最佳屏幕长宽比

        int bestCols = 1;
        int bestRows = newTableCount;
        int minScore = Integer.MAX_VALUE;
        double bestRatioDiff = Double.MAX_VALUE;

        // 从接近正方形开始穷举，一直试到极限列数
        int startCol = (int) Math.ceil(Math.sqrt(newTableCount));
        for (int c = startCol; c <= MAX_COLS; c++) {
            int r = (int) Math.ceil((double) newTableCount / c);

            // 计算瑕疵 1：空位数量 (违背“尽量满足矩形”的原则)
            int emptySlots = (r * c) - newTableCount;

            // 计算瑕疵 2：滑动惩罚 (违背“尽可能不滑动”的原则)
            int scrollPenalty = 0;
            // 只有当总数在理论上能一屏装下时，才进行严厉惩罚
            if (r > MAX_VISIBLE_ROWS && newTableCount <= MAX_COLS * MAX_VISIBLE_ROWS) {
                scrollPenalty = 1000; // 只要能不滑动，就拥有一票否决权！
            }

            // 综合瑕疵得分（越低越好）
            int totalScore = scrollPenalty + emptySlots;

            // 比例差异计算（用于判断桌子大小，越接近 1.8 桌子越大）
            double currentRatio = (double) c / r;
            double ratioDiff = Math.abs(currentRatio - TARGET_RATIO);

            // 按照你的优先级进行“王者选拔”
            if (totalScore < minScore) {
                // 发现更优解（更少滑动、更少空位）
                minScore = totalScore;
                bestCols = c;
                bestRows = r;
                bestRatioDiff = ratioDiff;
            } else if (totalScore == minScore) {
                // 如果空位一样多（比如 4x4 和 8x2 都是 0 空位），就比谁的桌子更大！
                if (ratioDiff < bestRatioDiff) {
                    bestCols = c;
                    bestRows = r;
                    bestRatioDiff = ratioDiff;
                }
            }
        }

        // 应用选拔出的最强王者配置
        this.setLayout(new GridLayout(bestRows, bestCols, 15, 15));

        for (int i = 0; i < newTableCount; i++) {
            tableSeatStates.put(i, new int[]{-1, -1, -1, -1});
            JPanel tableWrapper = createSingleTable(i + 1);

            // 赋予 85x85 保底体积，桌子少时自动拉伸变大，桌子多时死守底线并滚动
            tableWrapper.setPreferredSize(new Dimension(85, 85));
            this.add(tableWrapper);
        }

        this.revalidate();
        this.repaint();
    }


    private JPanel createSingleTable(int tableId) {
        // 优化 1：把座位间隙从 5 拉大到 8，增加呼吸感
        JPanel tablePanel = new JPanel(new GridLayout(2, 2, 8, 8));

        // 给桌子底层铺上“悬浮卡片色”
        tablePanel.setBackground(frontend.ColorTheme.BG_CARD);

        // 优化 2：改造默认的文字边框，去掉那圈死板的实线，换成隐形留白！
        javax.swing.border.TitledBorder titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(12, 4, 4, 4), // 增加顶部空间放文字，减少左右留白
                "桌 " + String.format("%02d", tableId)
        );
        titledBorder.setTitleColor(frontend.ColorTheme.TEXT_SECONDARY); // 让文字变成高级灰
        //titledBorder.setTitleFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
        tablePanel.setBorder(titledBorder);

        for (int j = 0; j < 4; j++) {
            JPanel seat = new JPanel();
            seat.setBackground(frontend.ColorTheme.BG_ITEM); // 正确：一出生就是深色
            seat.putClientProperty("FlatLaf.style", "arc: 15"); // 正确：开启现代圆角
            seat.putClientProperty("seat", Boolean.TRUE);

            // 优化 3：把测试代码里的老颜色，也同步成你的赛博调色盘！
            seat.addMouseListener(new MouseAdapter() {
                boolean isOccupied = false;

                @Override
                public void mouseClicked(MouseEvent e) {
                    isOccupied = !isOccupied;
                    // 彻底告别纯白和老红色！
                    seat.setBackground(isOccupied ?
                            frontend.ColorTheme.ACCENT_RED : frontend.ColorTheme.BG_ITEM);
                }
            });

            tablePanel.add(seat);
        }

        // 包装器代码
        JPanel responsiveWrapper = new JPanel() {
            @Override
            public void doLayout() {
                super.doLayout();
                int w = getWidth();
                int h = getHeight();
                // 确保桌子在格子内既不被拉长，也不被缩得太小
                int size = Math.min(w, h);
                int x = (w - size) / 2;
                int y = (h - size) / 2;
                if (getComponentCount() > 0) {
                    // 如果桌子数极多，这里保证它至少有 120px 的渲染大小
                    getComponent(0).setBounds(x, y, size, size);
                }
            }
        };
        responsiveWrapper.setLayout(null);
        responsiveWrapper.setOpaque(false);
        responsiveWrapper.add(tablePanel);

        return responsiveWrapper;
    }

    /**
     * 按后端传来的精确座位状态更新桌子显示。
     * seatGroupIds[seat] = groupId（-1 表示空位）
     */
    public void updateTableOccupancy(int tableIndex, int[] seatGroupIds) {
        if (tableIndex < 0 || tableIndex >= this.getComponentCount()) {
            return;
        }

        int[] states = new int[4];
        for (int i = 0; i < Math.min(4, seatGroupIds.length); i++) {
            states[i] = seatGroupIds[i];
        }
        tableSeatStates.put(tableIndex, states);

        applySeatStatesToUI(tableIndex, states);
    }

    private void applySeatStatesToUI(int tableIndex, int[] states) {
        Component comp = this.getComponent(tableIndex);
        if (!(comp instanceof JPanel)) {
            return;
        }

        JPanel wrapper = (JPanel) comp;
        List<JPanel> seatPanels = new ArrayList<>();
        collectSeatPanels(wrapper, seatPanels);

        for (int i = 0; i < Math.min(4, seatPanels.size()); i++) {
            JPanel seat = seatPanels.get(i);
            int groupId = states[i];

            // =========================================
            // 【UI 审美重构：回归工业大屏本质】
            // =========================================
            if (groupId >= 0) {
                // 只要有人坐（不管是几个人结伴），统一显示为醒目的“占用红”
                // （如果你觉得红色太刺眼，也可以改成 ColorTheme.ACCENT_BLUE 等其他主题色）
                seat.setBackground(frontend.ColorTheme.ACCENT_RED);
            } else {
                // 空位保持原样
                seat.setBackground(frontend.ColorTheme.BG_ITEM);
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
}