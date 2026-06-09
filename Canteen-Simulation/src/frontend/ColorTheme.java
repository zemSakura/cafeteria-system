package frontend;

import java.awt.Color;
import java.awt.Font;

/**
 * Light blue-white dashboard palette shared by simulation and optimization views.
 */
public class ColorTheme {
    public static final String UI_FONT = "Microsoft YaHei UI";

    public static Font font(int style, int size) {
        return new Font(UI_FONT, style, size);
    }

    public static final Color BG_MAIN = new Color(243, 246, 251);
    public static final Color BG_CARD = new Color(255, 255, 255);
    public static final Color BG_PANEL = new Color(248, 250, 255);
    public static final Color BG_ITEM = new Color(238, 244, 255);
    public static final Color BG_CONTROL = new Color(230, 240, 255);

    public static final Color BG_EMPTY_SEAT = new Color(236, 253, 245);
    public static final Color QUEUE_TRACK = new Color(221, 235, 255);
    public static final Color LOG_BG = new Color(255, 255, 255);
    public static final Color BORDER_SOFT = new Color(216, 224, 234);

    public static final Color ACCENT_CYAN = new Color(22, 119, 255);
    public static final Color ACCENT_BLUE = new Color(0, 82, 184);
    public static final Color ACCENT_RED = new Color(239, 68, 68);
    public static final Color ACCENT_YELLOW = new Color(245, 158, 11);
    public static final Color ACCENT_GREEN = new Color(22, 163, 74);
    public static final Color ACCENT_PURPLE = new Color(124, 58, 237);

    public static final Color SEAT_OCCUPIED = new Color(37, 99, 235);

    public static final Color[] GROUP_COLORS = {
            new Color(37, 99, 235),
            new Color(14, 165, 233),
            new Color(16, 185, 129),
            new Color(245, 158, 11),
            new Color(124, 58, 237),
            new Color(249, 115, 22),
            new Color(5, 150, 105),
            new Color(219, 39, 119),
    };

    public static Color groupColor(int groupId) {
        return GROUP_COLORS[Math.abs(groupId) % GROUP_COLORS.length];
    }

    public static final Color TEXT_PRIMARY = new Color(15, 23, 42);
    public static final Color TEXT_SECONDARY = new Color(71, 85, 105);
    public static final Color TEXT_MUTED = new Color(148, 163, 184);
}
