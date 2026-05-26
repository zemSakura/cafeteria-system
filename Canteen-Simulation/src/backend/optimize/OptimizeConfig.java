package backend.optimize;

public class OptimizeConfig {
    public int totalPopulation = 1000;
    public int minWindowCount = 3;
    public int maxWindowCount = 6;
    public int minTableCount = 80;
    public int maxTableCount = 100;
    public int repeatTimes = 2;
    public long baseRandomSeed = 20260525L;
    public boolean exportCsv = true;
    public int topK = 10;
    public boolean runReplayAfterOptimization = true;
    public int replaySnapshotIntervalSeconds = 60;
    public boolean verboseConsoleLog = true;

    public void validate() {
        if (totalPopulation <= 0) {
            throw new IllegalArgumentException("就餐人数必须大于 0");
        }
        if (minWindowCount <= 0 || maxWindowCount < minWindowCount) {
            throw new IllegalArgumentException("窗口数量搜索范围不合法");
        }
        if (minTableCount <= 0 || maxTableCount < minTableCount) {
            throw new IllegalArgumentException("桌子数量搜索范围不合法");
        }
        if (repeatTimes <= 0) {
            throw new IllegalArgumentException("重复次数必须大于 0");
        }
        if (topK <= 0) {
            topK = 10;
        }
        if (replaySnapshotIntervalSeconds <= 0) {
            replaySnapshotIntervalSeconds = 60;
        }
    }

    public int totalCandidateCount() {
        return (maxWindowCount - minWindowCount + 1) * (maxTableCount - minTableCount + 1);
    }

    public int totalSimulationCount() {
        return totalCandidateCount() * repeatTimes;
    }
}
