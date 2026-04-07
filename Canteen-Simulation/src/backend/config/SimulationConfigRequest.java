package config;

/**
 * 前端 -> 后端 的统一配置请求对象
 *
 * 设计目的：
 * 1. 前端不要再零散地修改 CanteenConfig 里的静态变量
 * 2. 所有参数统一封装成一个对象传进来
 * 3. 后端只需要调用 CanteenConfig.updateAllConfigs(request)
 */
public class SimulationConfigRequest {

    /** 桌位数量 */
    private int tableCount;

    /** 窗口数量 */
    private int windowCount;

    /** 营业时长 */
    private int openDuration;

    /** 快照间隔 */
    private int snapshotInterval;

    /** 随机种子 */
    private long randomSeed;

    /** 就餐时长均值 */
    private double diningTimeMean;

    /** 就餐时长标准差 */
    private double diningTimeStd;

    /** 最短就餐时长 */
    private int minDiningTime;

    /** 最小忍耐度 */
    private int patienceMin;

    /** 最大忍耐度 */
    private int patienceMax;

    /** 单人到达概率 */
    private double probSolo;

    /** 双人到达概率 */
    private double probDuo;

    /** 四人组到达概率 */
    private double probTeam;

    /**
     * 可选字段：
     * 如果前端不仅要传窗口数量，还要精细控制每个窗口的参数，
     * 可以直接传这两个数组
     */
    private int[] windowDistances;
    private int[] windowAvgServeTime;

    /**
     * 无参构造：
     * 默认先把所有值初始化为系统默认配置
     */
    public SimulationConfigRequest() {
        this.tableCount = CanteenConfig.DEFAULT_TOTAL_TABLES;
        this.windowCount = CanteenConfig.DEFAULT_WINDOW_DISTANCES.length;
        this.openDuration = CanteenConfig.DEFAULT_OPEN_DURATION;
        this.snapshotInterval = CanteenConfig.DEFAULT_SNAPSHOT_INTERVAL;
        this.randomSeed = CanteenConfig.DEFAULT_RANDOM_SEED;

        this.diningTimeMean = CanteenConfig.DEFAULT_DINING_TIME_MEAN;
        this.diningTimeStd = CanteenConfig.DEFAULT_DINING_TIME_STD;
        this.minDiningTime = CanteenConfig.DEFAULT_MIN_DINING_TIME;

        this.patienceMin = CanteenConfig.DEFAULT_PATIENCE_MIN;
        this.patienceMax = CanteenConfig.DEFAULT_PATIENCE_MAX;

        this.probSolo = CanteenConfig.DEFAULT_PROB_SOLO;
        this.probDuo = CanteenConfig.DEFAULT_PROB_DUO;
        this.probTeam = CanteenConfig.DEFAULT_PROB_TEAM;
    }

    public int getTableCount() {
        return tableCount;
    }

    public void setTableCount(int tableCount) {
        this.tableCount = tableCount;
    }

    public int getWindowCount() {
        return windowCount;
    }

    public void setWindowCount(int windowCount) {
        this.windowCount = windowCount;
    }

    public int getOpenDuration() {
        return openDuration;
    }

    public void setOpenDuration(int openDuration) {
        this.openDuration = openDuration;
    }

    public int getSnapshotInterval() {
        return snapshotInterval;
    }

    public void setSnapshotInterval(int snapshotInterval) {
        this.snapshotInterval = snapshotInterval;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public void setRandomSeed(long randomSeed) {
        this.randomSeed = randomSeed;
    }

    public double getDiningTimeMean() {
        return diningTimeMean;
    }

    public void setDiningTimeMean(double diningTimeMean) {
        this.diningTimeMean = diningTimeMean;
    }

    public double getDiningTimeStd() {
        return diningTimeStd;
    }

    public void setDiningTimeStd(double diningTimeStd) {
        this.diningTimeStd = diningTimeStd;
    }

    public int getMinDiningTime() {
        return minDiningTime;
    }

    public void setMinDiningTime(int minDiningTime) {
        this.minDiningTime = minDiningTime;
    }

    public int getPatienceMin() {
        return patienceMin;
    }

    public void setPatienceMin(int patienceMin) {
        this.patienceMin = patienceMin;
    }

    public int getPatienceMax() {
        return patienceMax;
    }

    public void setPatienceMax(int patienceMax) {
        this.patienceMax = patienceMax;
    }

    public double getProbSolo() {
        return probSolo;
    }

    public void setProbSolo(double probSolo) {
        this.probSolo = probSolo;
    }

    public double getProbDuo() {
        return probDuo;
    }

    public void setProbDuo(double probDuo) {
        this.probDuo = probDuo;
    }

    public double getProbTeam() {
        return probTeam;
    }

    public void setProbTeam(double probTeam) {
        this.probTeam = probTeam;
    }

    public int[] getWindowDistances() {
        return windowDistances;
    }

    public void setWindowDistances(int[] windowDistances) {
        this.windowDistances = windowDistances;
    }

    public int[] getWindowAvgServeTime() {
        return windowAvgServeTime;
    }

    public void setWindowAvgServeTime(int[] windowAvgServeTime) {
        this.windowAvgServeTime = windowAvgServeTime;
    }
}