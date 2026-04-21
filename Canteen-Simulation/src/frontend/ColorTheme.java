package frontend;

import java.awt.Color;

/**
 * 现代工业级总控台调色盘 (中性灰度高对比模式)
 */
public class ColorTheme {
    // 1. 硬件外壳底色 (Backgrounds)
    // 最外层窗体背景：高级的工程深灰色，作为大屏的物理外壳
    public static final Color BG_MAIN = new Color(43, 45, 48);       // #2B2D30 (类似专业 IDE 的默认底色)

    // 2. 液晶屏幕底色 (Screens)
    // 面板和日志的底色：极暗的黑灰色，提供极高对比度，但不使用纯黑以保护视力
    public static final Color BG_CARD = new Color(15, 15, 15);       // #0F0F0F (深邃液晶黑)

    // 3. 元素底色 (Items)
    // 座位空闲颜色：明显的深灰色，确保在黑色面板上轮廓极其清晰
    public static final Color BG_ITEM = new Color(55, 55, 55);       // #373737 (中性深灰)

    // 4. 霓虹强调色 (Accents)
    // 保持不变。在黑白灰的极简底色下，这些颜色亮起时会产生极其惊艳的视觉爆发力
    public static final Color ACCENT_CYAN = new Color(0, 242, 195);
    public static final Color ACCENT_BLUE = new Color(29, 140, 248);
    public static final Color ACCENT_RED = new Color(255, 82, 82);
    public static final Color ACCENT_YELLOW = new Color(253, 185, 39);

    // 5. 文字颜色 (Text)
    // 提升亮度以对抗深色背景，但使用亮灰白而非纯白，消除光晕感
    public static final Color TEXT_PRIMARY = new Color(230, 230, 230); // 亮灰白 (用于主标题、重要数据)
    public static final Color TEXT_SECONDARY = new Color(150, 150, 150); // 清晰的中灰 (用于副标题、日志次要信息)
}