package frontend;

import java.awt.Color;

/**
 * 现代工业级总控台调色盘
 */
public class ColorTheme {
    // ===== 1. 全局背景层 =====

    // 外层总背景：略压深，让卡片浮出来
    public static final Color BG_MAIN = new Color(34, 36, 40);       // #222428

    // 一级卡片背景：寻优模式和仿真模式主卡片
    public static final Color BG_CARD = new Color(12, 13, 15);       // #0C0D0F

    // 二级面板背景：用于控制条、日志外壳、右侧区域等
    public static final Color BG_PANEL = new Color(18, 20, 23);      // #121417

    // 组件底色：按钮槽、输入框、表格头等
    public static final Color BG_ITEM = new Color(58, 61, 66);       // #3A3D42

    // 控制区按钮/禁用态底色
    public static final Color BG_CONTROL = new Color(68, 72, 78);    // #44484E

    // ===== 2. 仿真模式专用辅助色 =====

    // 空座位颜色：比旧 BG_ITEM 稍亮，保证红色座位之外的空位也清楚
    public static final Color BG_EMPTY_SEAT = new Color(62, 65, 70); // #3E4146

    // 队列进度条未填充轨道
    public static final Color QUEUE_TRACK = new Color(72, 76, 82);   // #484C52

    // 日志背景
    public static final Color LOG_BG = new Color(5, 6, 7);           // #050607

    // 柔和边框/分割线
    public static final Color BORDER_SOFT = new Color(48, 51, 56);   // #4C5057

    // ===== 3. 强调色 =====

    public static final Color ACCENT_CYAN = new Color(0, 242, 195);
    public static final Color ACCENT_BLUE = new Color(29, 140, 248);
    public static final Color ACCENT_RED = new Color(255, 82, 82);
    public static final Color ACCENT_YELLOW = new Color(253, 185, 39);

    // 红色状态的弱化版本，用于大面积座位，避免过刺眼
    public static final Color SEAT_OCCUPIED = new Color(255, 76, 82);

    // ===== 4. 拼桌分组色 =====

    public static final Color[] GROUP_COLORS = {
            new Color(255, 82, 82),
            new Color(29, 140, 248),
            new Color(0, 242, 195),
            new Color(253, 185, 39),
            new Color(149, 117, 255),
            new Color(255, 145, 48),
            new Color(0, 200, 117),
            new Color(255, 110, 180),
    };

    public static Color groupColor(int groupId) {
        return GROUP_COLORS[Math.abs(groupId) % GROUP_COLORS.length];
    }

    // ===== 5. 文本颜色 =====

    public static final Color TEXT_PRIMARY = new Color(235, 238, 242);
    public static final Color TEXT_SECONDARY = new Color(166, 173, 182);
    public static final Color TEXT_MUTED = new Color(118, 124, 132);
}