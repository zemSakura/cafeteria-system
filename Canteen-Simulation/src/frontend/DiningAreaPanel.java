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

        int rows = (int) Math.round(Math.sqrt(newTableCount));
        int cols = (int) Math.ceil((double) newTableCount / rows);
        this.setLayout(new GridLayout(rows, cols, 15, 15));

        for (int i = 0; i < newTableCount; i++) {
            tableSeatStates.put(i, new int[]{-1, -1, -1, -1});
            this.add(createSingleTable(i + 1));
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
                BorderFactory.createEmptyBorder(8, 0, 2, 0), // 用空气留白代替线条
                "桌 " + String.format("%02d", tableId)
        );
        titledBorder.setTitleColor(frontend.ColorTheme.TEXT_SECONDARY); // 让文字变成高级灰
        titledBorder.setTitleFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
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

        JPanel responsiveWrapper = new JPanel() {
            @Override
            public void doLayout() {
                super.doLayout();
                int w = getWidth();
                int h = getHeight();
                int size = Math.min(w, h);
                int x = (w - size) / 2;
                int y = (h - size) / 2;
                if (getComponentCount() > 0) {
                    getComponent(0).setBounds(x, y, size, size);
                }
            }
        };

        responsiveWrapper.setLayout(null);
        // 记得把包装器的背景色也设为透明或底层颜色，防止露馅
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
            if (groupId >= 0) {
                seat.setBackground(frontend.ColorTheme.groupColor(groupId));
            } else {
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