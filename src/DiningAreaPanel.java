import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

// 这是一个纯粹的“组件类”，供别人调用
public class DiningAreaPanel extends JPanel {

    // 构造函数：告诉这个面板需要生成多少张桌子
    public DiningAreaPanel(int n) {
        // 动态计算最优行列比例
        int rows = (int) Math.round(Math.sqrt(n));
        int cols = (int) Math.ceil((double) n / rows);

        this.setLayout(new GridLayout(rows, cols, 15, 15));
        this.setBorder(BorderFactory.createTitledBorder("Dining Area Snapshot (就餐区实时快照)"));

        for (int i = 1; i <= n; i++) {
            JPanel tablePanel = new JPanel(new GridLayout(2, 2, 5, 5));
            tablePanel.setBorder(BorderFactory.createTitledBorder("桌 " + String.format("%02d", i)));

            for (int j = 0; j < 4; j++) {
                JPanel seat = new JPanel();
                seat.setBackground(Color.WHITE);
                seat.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));

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
                    getComponent(0).setBounds(x, y, size, size);
                }
            };
            responsiveWrapper.setLayout(null);
            responsiveWrapper.add(tablePanel);

            this.add(responsiveWrapper); // 把组装好的桌子加到这个大面板里
        }
    }
}