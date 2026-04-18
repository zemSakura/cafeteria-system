package frontend;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class DiningAreaPanel extends JPanel {

    /**
     * 每张桌子的 4 个座位占用状态
     * key = tableIndex
     * value = 长度为 4 的布尔数组，true 表示该座位有人
     */
    private final Map<Integer, boolean[]> tableSeatStates = new HashMap<>();

    private final Random random = new Random();

    /**
     * 2x2 座位下，允许的“两人自然坐法”
     * 这里只保留相邻组合，避免出现对角线分散坐
     *
     * 座位索引布局：
     * 0 1
     * 2 3
     *
     * 合法双人组合：
     * [0,1] 上排相邻
     * [2,3] 下排相邻
     * [0,2] 左列相邻
     * [1,3] 右列相邻
     */
    private static final int[][] NATURAL_TWO_SEAT_PATTERNS = {
            {0, 1},
            {2, 3},
            {0, 2},
            {1, 3}
    };

    public DiningAreaPanel(int initialTableCount) {
        this.setBorder(BorderFactory.createTitledBorder("Dining Area Snapshot (就餐区实时快照)"));
        updateTableCount(initialTableCount);
    }

    public void updateTableCount(int newTableCount) {
        this.removeAll();
        tableSeatStates.clear();

        int rows = (int) Math.round(Math.sqrt(newTableCount));
        int cols = (int) Math.ceil((double) newTableCount / rows);
        this.setLayout(new GridLayout(rows, cols, 15, 15));

        for (int i = 0; i < newTableCount; i++) {
            tableSeatStates.put(i, new boolean[4]);
            this.add(createSingleTable(i + 1));
        }

        this.revalidate();
        this.repaint();
    }

    private JPanel createSingleTable(int tableId) {
        JPanel tablePanel = new JPanel(new GridLayout(2, 2, 5, 5));
        tablePanel.setBorder(BorderFactory.createTitledBorder("桌 " + String.format("%02d", tableId)));

        for (int j = 0; j < 4; j++) {
            JPanel seat = new JPanel();
            seat.setBackground(Color.WHITE);
            seat.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            seat.putClientProperty("seat", Boolean.TRUE);

            // 仅用于本地测试点击效果，不影响正式仿真逻辑
            seat.addMouseListener(new MouseAdapter() {
                boolean isOccupied = false;

                @Override
                public void mouseClicked(MouseEvent e) {
                    isOccupied = !isOccupied;
                    seat.setBackground(isOccupied ? new Color(255, 59, 48) : Color.WHITE);
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
        responsiveWrapper.add(tablePanel);

        return responsiveWrapper;
    }

    /**
     * 按真实入座人数更新一张桌子的显示
     * occupiedSeats 可以是 0 / 1 / 2 / 3 / 4
     *
     * 显示策略：
     * - 1 人：随机选一个座位
     * - 2 人：优先选“自然双人组合”（相邻），避免对角线
     * - 3/4 人：在已有基础上随机补空座
     * - 人数减少：随机释放已占座位
     */
    public void updateTableOccupancy(int tableIndex, int occupiedSeats) {
        if (tableIndex < 0 || tableIndex >= this.getComponentCount()) {
            return;
        }

        occupiedSeats = Math.max(0, Math.min(4, occupiedSeats));

        boolean[] states = tableSeatStates.computeIfAbsent(tableIndex, k -> new boolean[4]);
        int currentOccupied = countOccupied(states);

        if (currentOccupied < occupiedSeats) {
            occupySeatsNaturally(states, currentOccupied, occupiedSeats);
        } else if (currentOccupied > occupiedSeats) {
            releaseRandomOccupiedSeats(states, currentOccupied - occupiedSeats);
        }

        applySeatStatesToUI(tableIndex, states);
    }

    private int countOccupied(boolean[] states) {
        int count = 0;
        for (boolean state : states) {
            if (state) {
                count++;
            }
        }
        return count;
    }

    /**
     * 更自然的入座逻辑
     */
    private void occupySeatsNaturally(boolean[] states, int currentOccupied, int targetOccupied) {
        // 空桌 -> 1人：随机坐
        if (currentOccupied == 0 && targetOccupied == 1) {
            occupyRandomEmptySeats(states, 1);
            return;
        }

        // 空桌 -> 2人：优先相邻坐，不允许对角线
        if (currentOccupied == 0 && targetOccupied == 2) {
            occupyNaturalTwoSeatPattern(states);
            return;
        }

        // 已有1人 -> 2人：第二个人优先坐在旁边
        if (currentOccupied == 1 && targetOccupied == 2) {
            occupySeatNearExisting(states);
            return;
        }

        // 其他情况：先按一般逻辑补齐
        occupyRandomEmptySeats(states, targetOccupied - currentOccupied);
    }

    /**
     * 两人组从空桌入座时，随机选择一种“自然双人组合”
     */
    private void occupyNaturalTwoSeatPattern(boolean[] states) {
        int[] pattern = NATURAL_TWO_SEAT_PATTERNS[random.nextInt(NATURAL_TWO_SEAT_PATTERNS.length)];
        states[pattern[0]] = true;
        states[pattern[1]] = true;
    }

    /**
     * 当桌上已有1人，又变成2人时，第二个人优先坐在相邻位置
     */
    private void occupySeatNearExisting(boolean[] states) {
        int occupiedIndex = -1;
        for (int i = 0; i < states.length; i++) {
            if (states[i]) {
                occupiedIndex = i;
                break;
            }
        }

        if (occupiedIndex == -1) {
            occupyNaturalTwoSeatPattern(states);
            return;
        }

        List<Integer> preferred = getAdjacentEmptySeats(states, occupiedIndex);
        if (!preferred.isEmpty()) {
            int seatIndex = preferred.get(random.nextInt(preferred.size()));
            states[seatIndex] = true;
            return;
        }

        // 极端兜底：如果相邻位都没有，就随便补一个空位
        occupyRandomEmptySeats(states, 1);
    }

    /**
     * 获取某个座位相邻的空位
     * 2x2 下相邻关系：
     * 0 -> 1,2
     * 1 -> 0,3
     * 2 -> 0,3
     * 3 -> 1,2
     */
    private List<Integer> getAdjacentEmptySeats(boolean[] states, int seatIndex) {
        List<Integer> result = new ArrayList<>();
        int[] neighbors;

        switch (seatIndex) {
            case 0:
                neighbors = new int[]{1, 2};
                break;
            case 1:
                neighbors = new int[]{0, 3};
                break;
            case 2:
                neighbors = new int[]{0, 3};
                break;
            case 3:
                neighbors = new int[]{1, 2};
                break;
            default:
                neighbors = new int[0];
        }

        for (int idx : neighbors) {
            if (!states[idx]) {
                result.add(idx);
            }
        }
        return result;
    }

    private void occupyRandomEmptySeats(boolean[] states, int count) {
        List<Integer> emptyIndices = new ArrayList<>();
        for (int i = 0; i < states.length; i++) {
            if (!states[i]) {
                emptyIndices.add(i);
            }
        }

        for (int k = 0; k < count && !emptyIndices.isEmpty(); k++) {
            int pick = random.nextInt(emptyIndices.size());
            int seatIndex = emptyIndices.remove(pick);
            states[seatIndex] = true;
        }
    }

    private void releaseRandomOccupiedSeats(boolean[] states, int count) {
        List<Integer> occupiedIndices = new ArrayList<>();
        for (int i = 0; i < states.length; i++) {
            if (states[i]) {
                occupiedIndices.add(i);
            }
        }

        for (int k = 0; k < count && !occupiedIndices.isEmpty(); k++) {
            int pick = random.nextInt(occupiedIndices.size());
            int seatIndex = occupiedIndices.remove(pick);
            states[seatIndex] = false;
        }
    }

    private void applySeatStatesToUI(int tableIndex, boolean[] states) {
        Component comp = this.getComponent(tableIndex);
        if (!(comp instanceof JPanel)) {
            return;
        }

        JPanel wrapper = (JPanel) comp;
        List<JPanel> seatPanels = new ArrayList<>();
        collectSeatPanels(wrapper, seatPanels);

        for (int i = 0; i < Math.min(4, seatPanels.size()); i++) {
            JPanel seat = seatPanels.get(i);
            seat.setBackground(states[i] ? new Color(255, 200, 200) : Color.WHITE);
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