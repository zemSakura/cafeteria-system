package frontend;

import backend.dto.SimulationSnapshot;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * Compact process strip for the simulation lifecycle.
 */
public class StatusFlowPanel extends JPanel {
    private final JLabel arrivedValue = createValueLabel();
    private final JLabel queueValue = createValueLabel();
    private final JLabel servingValue = createValueLabel();
    private final JLabel waitingSeatValue = createValueLabel();
    private final JLabel diningValue = createValueLabel();
    private final JLabel completedValue = createValueLabel();
    private final JLabel abandonedValue = createValueLabel();

    public StatusFlowPanel() {
        super(new GridLayout(1, 7, 8, 0));
        setOpaque(false);
        add(createNode("已到达", arrivedValue, ColorTheme.ACCENT_CYAN));
        add(createNode("排队", queueValue, new Color(149, 117, 255)));
        add(createNode("打饭", servingValue, ColorTheme.ACCENT_BLUE));
        add(createNode("等座", waitingSeatValue, ColorTheme.ACCENT_YELLOW));
        add(createNode("就餐", diningValue, ColorTheme.ACCENT_GREEN));
        add(createNode("完成", completedValue, ColorTheme.ACCENT_CYAN));
        add(createNode("放弃", abandonedValue, ColorTheme.ACCENT_RED));
    }

    public void updateSnapshot(SimulationSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        arrivedValue.setText(snapshot.arrivedCount + " 人");
        queueValue.setText(snapshot.queueingCount + " 人");
        servingValue.setText(snapshot.servingCount + " 人");
        waitingSeatValue.setText(snapshot.waitingSeatCount + " 人");
        diningValue.setText(snapshot.diningCount + " 人");
        completedValue.setText(snapshot.completedCount + " 人");
        abandonedValue.setText(snapshot.abandonedCount + " 人");
    }

    private JPanel createNode(String title, JLabel value, Color accent) {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 2));
        panel.setBackground(ColorTheme.BG_PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(ColorTheme.TEXT_SECONDARY);
        titleLabel.setFont(ColorTheme.font(Font.PLAIN, 11));
        value.setForeground(accent);
        panel.add(titleLabel);
        panel.add(value);
        return panel;
    }

    private JLabel createValueLabel() {
        JLabel label = new JLabel("待仿真");
        label.setFont(ColorTheme.font(Font.BOLD, 13));
        return label;
    }
}
