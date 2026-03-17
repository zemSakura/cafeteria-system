package config;

public class CanteenConfig {
    // 1. 食堂基础硬件
    public static final int TOTAL_TABLES = 150;
    public static final int OPEN_DURATION = 1000; // 模拟总时长(秒)

    // 2. 窗口详细配置 (数组长度即为窗口总数)
    public static final int[] WINDOW_DISTANCES = {10, 15, 20, 25, 30};
    public static final int[] WINDOW_AVG_SERVE_TIME = {30, 60, 45, 40, 50};

    // 3. 学生就餐时长配置（正态分布参数）
    public static final double DINING_TIME_MEAN = 900.0;   // 平均吃15分钟
    public static final double DINING_TIME_STD = 180.0;    // 标准差3分钟

    // 4. 学生忍耐度配置（均匀分布参数）
    public static final int PATIENCE_MIN = 300; // 最短忍5分钟
    public static final int PATIENCE_MAX = 900; // 最长忍15分钟

    // 5. 结队概率配置 (0.0 到 1.0)
    // 规则：先判定是不是单人，不是的话再判定是不是双人...
    public static final double PROB_SOLO = 0.7; // 70% 一个人来
    public static final double PROB_DUO = 0.2;  // 20% 两人结伴
    public static final double PROB_TEAM = 0.1; // 10% 四人寝室出动
}