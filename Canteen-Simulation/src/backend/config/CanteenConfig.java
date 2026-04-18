package backend.config;

import java.util.Arrays;

/**
 * 食堂仿真系统全局配置类
 *
 * 设计说明：
 * 1. 以前很多配置是 public static final，前端无法动态修改
 * 2. 现在改成“默认值常量 + 运行时可变配置”两层结构
 * 3. 前端点击“开始”后，可以通过 updateAllConfigs(...) 一次性注入参数
 * 4. 支持动态窗口数量
 * 5. 支持恢复默认配置
 */
public class CanteenConfig {

    /**
     * 私有构造，禁止外部 new
     */
    private CanteenConfig() {
    }

    /**
     * 时间单位说明：CanteenConfig
     * 当前统一约定：1 个仿真时间单位 = 1 分钟
     * 注意：这里只是“仿真单位”，不是系统真实秒
     */
    public static final String TIME_UNIT_DESCRIPTION = "1 个仿真时间单位 = 1 分钟";

    // =========================================================
    // 一、默认值常量（永远不改，只作为“恢复默认配置”的依据）
    // =========================================================

    /** 默认桌位总数 */
    public static final int DEFAULT_TOTAL_TABLES = 150;

    /** 默认营业时长：120 个仿真时间单位 */
    public static final int DEFAULT_OPEN_DURATION = 120;

    /** 默认快照间隔：每 5 个仿真单位导出一次 */
    public static final int DEFAULT_SNAPSHOT_INTERVAL = 5;

    /** 默认随机种子：便于复现实验 */
    public static final long DEFAULT_RANDOM_SEED = 20260324L;

    /** 默认窗口距离数组 */
    public static int[] DEFAULT_WINDOW_DISTANCES = {10, 15, 20, 25, 30};

    /** 默认窗口平均服务时长数组 */
    public static final int[] DEFAULT_WINDOW_AVG_SERVE_TIME = {1, 2, 1, 2, 2};

    /** 默认就餐时长均值 */
    public static final double DEFAULT_DINING_TIME_MEAN = 15.0;

    /** 默认就餐时长标准差 */
    public static final double DEFAULT_DINING_TIME_STD = 3.0;

    /** 默认最短就餐时长 */
    public static final int DEFAULT_MIN_DINING_TIME = 5;

    /** 默认最小忍耐度 */
    public static final int DEFAULT_PATIENCE_MIN = 5;

    /** 默认最大忍耐度 */
    public static final int DEFAULT_PATIENCE_MAX = 15;

    /** 默认单人到达概率 */
    public static final double DEFAULT_PROB_SOLO = 0.7;

    /** 默认双人到达概率 */
    public static final double DEFAULT_PROB_DUO = 0.2;

    /** 默认四人组队到达概率 */
    public static final double DEFAULT_PROB_TEAM = 0.1;


    // =========================================================
    // 二、运行时配置（前端/引擎可以动态修改）
    // =========================================================

    /** 当前桌位总数 */
    public static int TOTAL_TABLES = DEFAULT_TOTAL_TABLES;

    /** 当前营业总时长 */
    public static int OPEN_DURATION = DEFAULT_OPEN_DURATION;

    /** 当前快照间隔 */
    public static int SNAPSHOT_INTERVAL = DEFAULT_SNAPSHOT_INTERVAL;

    /** 当前随机种子 */
    public static long RANDOM_SEED = DEFAULT_RANDOM_SEED;

    /** 当前窗口距离数组 */
    public static int[] WINDOW_DISTANCES = DEFAULT_WINDOW_DISTANCES.clone();

    /** 当前窗口平均服务时长数组 */
    public static int[] WINDOW_AVG_SERVE_TIME = DEFAULT_WINDOW_AVG_SERVE_TIME.clone();

    /** 当前就餐时长均值 */
    public static double DINING_TIME_MEAN = DEFAULT_DINING_TIME_MEAN;

    /** 当前就餐时长标准差 */
    public static double DINING_TIME_STD = DEFAULT_DINING_TIME_STD;

    /** 当前最短就餐时长 */
    public static int MIN_DINING_TIME = DEFAULT_MIN_DINING_TIME;

    /** 当前最小忍耐度 */
    public static int PATIENCE_MIN = DEFAULT_PATIENCE_MIN;

    /** 当前最大忍耐度 */
    public static int PATIENCE_MAX = DEFAULT_PATIENCE_MAX;

    /** 当前单人到达概率 */
    public static double PROB_SOLO = DEFAULT_PROB_SOLO;

    /** 当前双人到达概率 */
    public static double PROB_DUO = DEFAULT_PROB_DUO;

    /** 当前四人组队到达概率 */
    public static double PROB_TEAM = DEFAULT_PROB_TEAM;


    /**
     * 获取当前窗口数量
     * 窗口数量由窗口数组长度决定
     *
     * @return 当前窗口数
     */
    public static int getWindowCount() {
        return WINDOW_DISTANCES.length;
    }

    /**
     * 动态初始化窗口配置
     *
     * 使用场景：
     * - 前端只传一个 windowCount
     * - 后端根据这个数量自动生成默认的窗口距离和服务时长数组
     *
     * 默认规则：
     * - 距离：10, 15, 20, 25...
     * - 平均服务时间：统一先给 2
     *
     * @param windowCount 窗口数量
     */
    public static synchronized void initWindowsConfig(int windowCount) {
        if (windowCount <= 0) {
            throw new IllegalArgumentException("窗口数量必须大于 0");
        }

        WINDOW_DISTANCES = new int[windowCount];
        WINDOW_AVG_SERVE_TIME = new int[windowCount];

        for (int i = 0; i < windowCount; i++) {
            WINDOW_DISTANCES[i] = 10 + i * 5;
            WINDOW_AVG_SERVE_TIME[i] = 2;
        }
    }

    /**
     * 允许前端直接传完整窗口数组
     *
     * 使用场景：
     * - 前端不只想控制窗口数
     * - 还想自定义每个窗口的距离和服务时长
     *
     * @param distances  窗口距离数组
     * @param serveTimes 窗口服务时长数组
     */
    public static synchronized void updateWindowConfigs(int[] distances, int[] serveTimes) {
        if (distances == null || serveTimes == null) {
            throw new IllegalArgumentException("窗口配置数组不能为 null");
        }

        if (distances.length == 0 || serveTimes.length == 0) {
            throw new IllegalArgumentException("窗口配置数组长度不能为 0");
        }

        if (distances.length != serveTimes.length) {
            throw new IllegalArgumentException("窗口距离数组和服务时长数组长度必须一致");
        }

        // clone 防止外部数组引用直接污染内部配置
        WINDOW_DISTANCES = distances.clone();
        WINDOW_AVG_SERVE_TIME = serveTimes.clone();

        validate();
    }

    /**
     * 统一参数注入入口
     *
     * 这是前端最推荐调用的方法：
     * - 把所有输入框里的值装进 SimulationConfigRequest
     * - 然后一次性传给这里
     *
     * @param request 前端传来的完整配置请求
     */
    public static synchronized void updateAllConfigs(SimulationConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("配置请求不能为空");
        }

        // 1. 更新非窗口配置
        TOTAL_TABLES = request.getTableCount();
        OPEN_DURATION = request.getOpenDuration();
        SNAPSHOT_INTERVAL = request.getSnapshotInterval();
        RANDOM_SEED = request.getRandomSeed();

        DINING_TIME_MEAN = request.getDiningTimeMean();
        DINING_TIME_STD = request.getDiningTimeStd();
        MIN_DINING_TIME = request.getMinDiningTime();

        PATIENCE_MIN = request.getPatienceMin();
        PATIENCE_MAX = request.getPatienceMax();

        PROB_SOLO = request.getProbSolo();
        PROB_DUO = request.getProbDuo();
        PROB_TEAM = request.getProbTeam();

        // 2. 更新窗口配置
        // 如果前端传了完整数组，就按数组来
        // 否则只按 windowCount 动态初始化
        if (request.getWindowDistances() != null && request.getWindowAvgServeTime() != null) {
            updateWindowConfigs(request.getWindowDistances(), request.getWindowAvgServeTime());
        } else {
            initWindowsConfig(request.getWindowCount());
        }

        // 3. 最终统一校验
        validate();
    }

    /**
     * 恢复到默认配置
     *
     * 使用场景：
     * - 前端点“恢复默认设置”
     * - 或者测试时想把配置重置回最初状态
     */
    public static synchronized void resetToDefaults() {
        TOTAL_TABLES = DEFAULT_TOTAL_TABLES;
        OPEN_DURATION = DEFAULT_OPEN_DURATION;
        SNAPSHOT_INTERVAL = DEFAULT_SNAPSHOT_INTERVAL;
        RANDOM_SEED = DEFAULT_RANDOM_SEED;

        WINDOW_DISTANCES = DEFAULT_WINDOW_DISTANCES.clone();
        WINDOW_AVG_SERVE_TIME = DEFAULT_WINDOW_AVG_SERVE_TIME.clone();

        DINING_TIME_MEAN = DEFAULT_DINING_TIME_MEAN;
        DINING_TIME_STD = DEFAULT_DINING_TIME_STD;
        MIN_DINING_TIME = DEFAULT_MIN_DINING_TIME;

        PATIENCE_MIN = DEFAULT_PATIENCE_MIN;
        PATIENCE_MAX = DEFAULT_PATIENCE_MAX;

        PROB_SOLO = DEFAULT_PROB_SOLO;
        PROB_DUO = DEFAULT_PROB_DUO;
        PROB_TEAM = DEFAULT_PROB_TEAM;

        validate();
    }

    /**
     * 配置合法性校验
     *
     * 任何开始仿真前，都建议先调用一次
     */
    public static synchronized void validate() {
        if (TOTAL_TABLES <= 0) {
            throw new IllegalArgumentException("桌位数量必须大于 0");
        }

        if (WINDOW_DISTANCES == null || WINDOW_AVG_SERVE_TIME == null) {
            throw new IllegalArgumentException("窗口配置数组不能为空");
        }

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

        if (PATIENCE_MIN < 0 || PATIENCE_MAX < 0 || PATIENCE_MIN > PATIENCE_MAX) {
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

    /**
     * 打印当前配置，便于调试
     *
     * @return 当前配置字符串
     */
    public static synchronized String dumpConfig() {
        return "CanteenConfig{" +
                "TOTAL_TABLES=" + TOTAL_TABLES +
                ", OPEN_DURATION=" + OPEN_DURATION +
                ", SNAPSHOT_INTERVAL=" + SNAPSHOT_INTERVAL +
                ", RANDOM_SEED=" + RANDOM_SEED +
                ", WINDOW_DISTANCES=" + Arrays.toString(WINDOW_DISTANCES) +
                ", WINDOW_AVG_SERVE_TIME=" + Arrays.toString(WINDOW_AVG_SERVE_TIME) +
                ", DINING_TIME_MEAN=" + DINING_TIME_MEAN +
                ", DINING_TIME_STD=" + DINING_TIME_STD +
                ", MIN_DINING_TIME=" + MIN_DINING_TIME +
                ", PATIENCE_MIN=" + PATIENCE_MIN +
                ", PATIENCE_MAX=" + PATIENCE_MAX +
                ", PROB_SOLO=" + PROB_SOLO +
                ", PROB_DUO=" + PROB_DUO +
                ", PROB_TEAM=" + PROB_TEAM +
                '}';
    }
}