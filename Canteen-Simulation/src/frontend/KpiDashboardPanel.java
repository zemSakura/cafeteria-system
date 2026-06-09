package frontend;

import backend.dto.SimulationSnapshot;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Right-side KPI cards driven only by backend snapshots.
 */
public class KpiDashboardPanel extends JPanel {
    private final Map<String, JLabel> values = new LinkedHashMap<>();

    public KpiDashboardPanel() {
        super(new GridLayout(0, 2, 10, 10));
        setOpaque(false);
        addCard("当前接待人数", "reception", ColorTheme.ACCENT_CYAN);
        addCard("完成率", "completion", ColorTheme.ACCENT_GREEN);
        addCard("当前排队人数", "queueing", new Color(149, 117, 255));
        addCard("当前等座人数", "waitingSeat", ColorTheme.ACCENT_YELLOW);
        addCard("平均等待时间", "avgWait", ColorTheme.ACCENT_CYAN);
        addCard("预计净收益", "netProfit", ColorTheme.ACCENT_YELLOW);
    }

    public void updateSnapshot(SimulationSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        setValue("reception", snapshot.arrivedCount + " / " + snapshot.totalStudents);
        setValue("completion", percent(snapshot.completionRate));
        setValue("queueing", snapshot.queueingCount + " 人");
        setValue("waitingSeat", snapshot.waitingSeatCount + " 人");
        setValue("avgWait", minutes(snapshot.avgQueueWaitSeconds + snapshot.avgSeatWaitSeconds));
        setValue("netProfit", money(snapshot.netProfit));
    }

    private void addCard(String title, String key, Color accent) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(ColorTheme.BG_PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(14, 12, 14, 12)
        ));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(ColorTheme.TEXT_SECONDARY);
        titleLabel.setFont(ColorTheme.font(Font.PLAIN, 12));
        JLabel valueLabel = new JLabel("待仿真");
        valueLabel.setForeground(accent);
        valueLabel.setFont(ColorTheme.font(Font.BOLD, 20));
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        values.put(key, valueLabel);
        add(card);
    }

    private void setValue(String key, String value) {
        JLabel label = values.get(key);
        if (label != null) {
            label.setText(value);
            label.setToolTipText(value);
            label.setFont(ColorTheme.font(Font.BOLD, fontSizeFor(value)));
        }
    }

    private int fontSizeFor(String value) {
        if (value == null) {
            return 20;
        }
        if (value.length() >= 12) {
            return 16;
        }
        if (value.length() >= 9) {
            return 18;
        }
        return 20;
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
}
