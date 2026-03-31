package backend.config;

public class CanteenConfig {

    private CanteenConfig() {
    }

    /**
     * 时间单位说明：
     * 当前统一约定：1 个仿真时间单位 = 1 分钟
     * 注意：这里只是“仿真单位”，不是系统真实秒。
     */
    public static final String TIME_UNIT_DESCRIPTION = "1 个仿真时间单位 = 1 分钟";

    // 1. 食堂基础硬件
    public static final int TOTAL_TABLES = 150;

    /**
     * 营业总时长：2 小时 = 120 个仿真时间单位
     */
    public static final int OPEN_DURATION = 120;

    /**
     * 快照间隔：每 5 分钟导出一次状态
     */
    public static final int SNAPSHOT_INTERVAL = 5;

    /**
     * 随机种子：便于复现实验
     */
    public static final long RANDOM_SEED = 20260324L;

    // 2. 窗口详细配置 (数组长度即为窗口总数)
    public static final int[] WINDOW_DISTANCES = {10, 15, 20, 25, 30};

    /**
     * 窗口平均服务时长（单位：仿真时间单位，即分钟）
     */
    public static final int[] WINDOW_AVG_SERVE_TIME = {1, 2, 1, 2, 2};

    // 3. 学生就餐时长配置（正态分布参数）
    public static final double DINING_TIME_MEAN = 15.0;   // 平均吃 15 分钟
    public static final double DINING_TIME_STD = 3.0;     // 标准差 3 分钟
    public static final int MIN_DINING_TIME = 5;          // 保底至少吃 5 分钟

    // 4. 学生忍耐度配置（均匀分布参数）
    public static final int PATIENCE_MIN = 5;   // 最短忍 5 分钟
    public static final int PATIENCE_MAX = 15;  // 最长忍 15 分钟

    // 5. 结队概率配置 (0.0 到 1.0)
    public static final double PROB_SOLO = 0.7; // 70% 一个人来
    public static final double PROB_DUO = 0.2;  // 20% 两人结伴
    public static final double PROB_TEAM = 0.1; // 10% 四人组队

    public static int getWindowCount() {
        return WINDOW_DISTANCES.length;
    }

    public static void validate() {
        if (WINDOW_DISTANCES.length != WINDOW_AVG_SERVE_TIME.length) {
            throw new IllegalArgumentException("窗口配置数组长度不一致");
        }

        if (WINDOW_DISTANCES.length == 0) {
            throw new IllegalArgumentException("窗口数不能为 0");
        }

        for (int distance : WINDOW_DISTANCES) {
            if (distance < 0) {
                throw new IllegalArgumentException("窗口距离不能为负数");
            }
        }

        for (int serveTime : WINDOW_AVG_SERVE_TIME) {
            if (serveTime <= 0) {
                throw new IllegalArgumentException("窗口服务时间必须大于 0");
            }
        }

        if (OPEN_DURATION <= 0) {
            throw new IllegalArgumentException("营业时长必须大于 0");
        }

        if (SNAPSHOT_INTERVAL <= 0) {
            throw new IllegalArgumentException("快照间隔必须大于 0");
        }

        if (PATIENCE_MIN > PATIENCE_MAX) {
            throw new IllegalArgumentException("忍耐度范围非法");
        }

        if (DINING_TIME_MEAN <= 0 || DINING_TIME_STD < 0 || MIN_DINING_TIME <= 0) {
            throw new IllegalArgumentException("就餐时长参数非法");
        }

        double sum = PROB_SOLO + PROB_DUO + PROB_TEAM;
        if (Math.abs(sum - 1.0) > 1e-9) {
            throw new IllegalArgumentException("组队概率之和必须为 1，当前为: " + sum);
        }
    }
}