package frontend;

import backend.dto.PressureLevel;
import backend.dto.SimulationSnapshot;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.Locale;

/**
 * Displays students who have finished service but are still waiting for seats.
 */
public class WaitingSeatPanel extends JPanel {
    private final JLabel countLabel = new JLabel("待仿真");
    private final JLabel waitLabel = new JLabel("等待后端快照");
    private final JProgressBar pressureBar = new JProgressBar(0, 100);

    public WaitingSeatPanel() {
        super(new BorderLayout(8, 8));
        setBackground(ColorTheme.BG_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));

        JLabel title = new JLabel("等座区");
        title.setForeground(ColorTheme.TEXT_PRIMARY);
        title.setFont(ColorTheme.font(Font.BOLD, 14));
        add(title, BorderLayout.NORTH);

        countLabel.setForeground(new Color(149, 117, 255));
        countLabel.setFont(ColorTheme.font(Font.BOLD, 24));
        waitLabel.setForeground(ColorTheme.TEXT_SECONDARY);

        JPanel center = new JPanel(new BorderLayout(0, 4));
        center.setOpaque(false);
        center.add(countLabel, BorderLayout.CENTER);
        center.add(waitLabel, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        pressureBar.setStringPainted(true);
        pressureBar.setBorderPainted(false);
        pressureBar.setBackground(ColorTheme.QUEUE_TRACK);
        add(pressureBar, BorderLayout.SOUTH);
    }

    public void updateSnapshot(SimulationSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        countLabel.setText(snapshot.waitingSeatCount + " 人");
        waitLabel.setText(String.format(Locale.US, "平均等座 %.1f 分，峰值 %d 人",
                snapshot.avgSeatWaitSeconds / 60.0,
                snapshot.maxWaitingSeatCount));
        int max = Math.max(20, Math.max(snapshot.maxWaitingSeatCount, snapshot.totalStudents / 10));
        pressureBar.setMaximum(max);
        pressureBar.setValue(Math.min(max, snapshot.waitingSeatCount));
        PressureLevel level = pressure(snapshot.waitingSeatCount, max);
        pressureBar.setForeground(colorFor(level));
        pressureBar.setString(levelText(level));
    }

    private PressureLevel pressure(int count, int max) {
        double ratio = max <= 0 ? 0.0 : count / (double) max;
        if (ratio < 0.25) return PressureLevel.LOW;
        if (ratio < 0.55) return PressureLevel.MEDIUM;
        if (ratio < 0.80) return PressureLevel.HIGH;
        return PressureLevel.OVERLOAD;
    }

    private Color colorFor(PressureLevel level) {
        switch (level) {
            case MEDIUM: return ColorTheme.ACCENT_YELLOW;
            case HIGH: return new Color(255, 145, 48);
            case OVERLOAD: return ColorTheme.ACCENT_RED;
            default: return ColorTheme.ACCENT_GREEN;
        }
    }

    private String levelText(PressureLevel level) {
        switch (level) {
            case MEDIUM: return "中压";
            case HIGH: return "高压";
            case OVERLOAD: return "拥堵";
            default: return "低压";
        }
    }
}
