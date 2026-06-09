package frontend;

import backend.dto.TrendPoint;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lightweight Swing line chart for queue, waiting-seat and seat-utilization trends.
 */
public class TrendGraphPanel extends JPanel {
    private List<TrendPoint> points = Collections.emptyList();

    public TrendGraphPanel() {
        setBackground(ColorTheme.BG_PANEL);
    }

    public void setTrendPoints(List<TrendPoint> points) {
        this.points = points == null ? Collections.emptyList() : new ArrayList<>(points);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int left = 36;
        int right = 12;
        int top = 18;
        int bottom = 28;
        int width = Math.max(1, getWidth() - left - right);
        int height = Math.max(1, getHeight() - top - bottom);

        g2.setColor(ColorTheme.BORDER_SOFT);
        g2.drawRect(left, top, width, height);
        g2.setFont(ColorTheme.font(Font.PLAIN, 11));
        g2.setColor(ColorTheme.TEXT_SECONDARY);
        g2.drawString("趋势", 8, 14);
        g2.setColor(ColorTheme.ACCENT_BLUE);
        g2.drawString("排队", left + 8, getHeight() - 8);
        g2.setColor(new Color(149, 117, 255));
        g2.drawString("等座", left + 52, getHeight() - 8);
        g2.setColor(ColorTheme.ACCENT_GREEN);
        g2.drawString("座位利用", left + 96, getHeight() - 8);

        if (points.size() < 2) {
            g2.setColor(ColorTheme.TEXT_SECONDARY);
            g2.drawString("等待仿真快照...", left + 30, top + height / 2);
            g2.dispose();
            return;
        }

        int max = 1;
        for (TrendPoint point : points) {
            max = Math.max(max, Math.max(point.queueingCount, point.waitingSeatCount));
        }
        drawLine(g2, left, top, width, height, max, true, ColorTheme.ACCENT_BLUE);
        drawLine(g2, left, top, width, height, max, false, new Color(149, 117, 255));
        drawUtilizationLine(g2, left, top, width, height, ColorTheme.ACCENT_GREEN);
        g2.dispose();
    }

    private void drawLine(Graphics2D g2, int left, int top, int width, int height, int max,
                          boolean queue, Color color) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(2f));
        int previousX = -1;
        int previousY = -1;
        for (int i = 0; i < points.size(); i++) {
            TrendPoint point = points.get(i);
            int value = queue ? point.queueingCount : point.waitingSeatCount;
            int x = left + (int) Math.round(i * width / (double) (points.size() - 1));
            int y = top + height - (int) Math.round(value * height / (double) max);
            if (previousX >= 0) {
                g2.drawLine(previousX, previousY, x, y);
            }
            previousX = x;
            previousY = y;
        }
    }

    private void drawUtilizationLine(Graphics2D g2, int left, int top, int width, int height, Color color) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                new float[]{5f, 4f}, 0));
        int previousX = -1;
        int previousY = -1;
        for (int i = 0; i < points.size(); i++) {
            TrendPoint point = points.get(i);
            double value = Math.max(0.0, Math.min(1.0, point.tableUtilizationRate));
            int x = left + (int) Math.round(i * width / (double) (points.size() - 1));
            int y = top + height - (int) Math.round(value * height);
            if (previousX >= 0) {
                g2.drawLine(previousX, previousY, x, y);
            }
            previousX = x;
            previousY = y;
        }
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(ColorTheme.TEXT_SECONDARY);
        g2.drawString("100%", left + width - 34, top + 12);
    }
}
