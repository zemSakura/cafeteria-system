package frontend;

import backend.dto.SimulationSnapshot;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridLayout;
import java.awt.Font;

/**
 * Separate completed and abandoned leaving areas.
 */
public class LeavingAreaPanel extends JPanel {
    private final JLabel completedLabel = new JLabel("待仿真");
    private final JLabel abandonedLabel = new JLabel("待仿真");

    public LeavingAreaPanel() {
        super(new GridLayout(1, 2, 8, 0));
        setOpaque(false);
        add(createBox("完成离开区", completedLabel, ColorTheme.ACCENT_CYAN));
        add(createBox("放弃离开区", abandonedLabel, ColorTheme.ACCENT_RED));
    }

    public void updateSnapshot(SimulationSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        completedLabel.setText(snapshot.completedCount + " 人");
        abandonedLabel.setText(snapshot.abandonedCount + " 人");
    }

    private JPanel createBox(String title, JLabel value, java.awt.Color color) {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 4));
        panel.setBackground(ColorTheme.BG_PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(ColorTheme.TEXT_SECONDARY);
        value.setForeground(color);
        value.setFont(ColorTheme.font(Font.BOLD, 20));
        panel.add(titleLabel);
        panel.add(value);
        return panel;
    }
}
