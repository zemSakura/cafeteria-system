package frontend;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class DiningAreaPanel extends JPanel {

    // 构造函数：初始化时，直接让 updateTableCount 来干活
    public DiningAreaPanel(int initialTableCount) {
        this.setBorder(BorderFactory.createTitledBorder("Dining Area Snapshot (就餐区实时快照)"));
        // 刚启动时，直接调用更新方法画出初始数量的桌子
        updateTableCount(initialTableCount);
    }

    // 核心更新方法：负责擦除、重算布局、重画、刷新
    public void updateTableCount(int newTableCount) {
        // 1. 擦除旧组件
        this.removeAll();

        // 2. 重新计算栅格比例（非常重要！保证 50 张桌子也能排得方方正正）
        int rows = (int) Math.round(Math.sqrt(newTableCount));
        int cols = (int) Math.ceil((double) newTableCount / rows);
        this.setLayout(new GridLayout(rows, cols, 15, 15));

        // 3. 循环添加新桌子（调用下面抽取的造桌子方法）
        for (int i = 1; i <= newTableCount; i++) {
            this.add(createSingleTable(i));
        }

        // 4. 通知底层重新排版并涂色
        this.revalidate();
        this.repaint();
    }

    // --- 辅助方法：流水线上的“造桌机器” ---
    // 把那几十行复杂的造桌子逻辑单独剥离出来，让主代码极其干净
    private JPanel createSingleTable(int tableId) {
        JPanel tablePanel = new JPanel(new GridLayout(2, 2, 5, 5));
        tablePanel.setBorder(BorderFactory.createTitledBorder("桌 " + String.format("%02d", tableId)));

        for (int j = 0; j < 4; j++) {
            JPanel seat = new JPanel();
            seat.setBackground(Color.WHITE);
            seat.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));

            // 座位点击变红的测试逻辑
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

        // 响应式居中包装盒
        JPanel responsiveWrapper = new JPanel() {
            @Override
            public void doLayout() {
                super.doLayout();
                int w = getWidth(), h = getHeight();
                int size = Math.min(w, h);
                int x = (w - size) / 2, y = (h - size) / 2;
                if (getComponentCount() > 0) {
                    getComponent(0).setBounds(x, y, size, size);
                }
            }
        };
        responsiveWrapper.setLayout(null);
        responsiveWrapper.add(tablePanel);

        return responsiveWrapper;
    }
}