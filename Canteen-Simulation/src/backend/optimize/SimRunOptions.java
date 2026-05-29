package backend.optimize;

public class SimRunOptions {
    public boolean headless = false;
    public boolean csvEnabled = true;
    public boolean listenerEnabled = true;
    public boolean replayRecordEnabled = false;
    public int replaySnapshotIntervalSeconds = 60;
    public long randomSeed = -1L;
    public int totalPopulation = -1;

    public static SimRunOptions normal() {
        SimRunOptions opt = new SimRunOptions();
        opt.headless = false;
        opt.csvEnabled = true;
        opt.listenerEnabled = true;
        opt.replayRecordEnabled = false;
        return opt;
    }

    public static SimRunOptions optimize(long seed) {
        return optimize(seed, -1);
    }

    public static SimRunOptions optimize(long seed, int totalPopulation) {
        SimRunOptions opt = new SimRunOptions();
        opt.headless = true;
        opt.csvEnabled = false;
        opt.listenerEnabled = false;
        opt.replayRecordEnabled = false;
        opt.randomSeed = seed;
        opt.totalPopulation = totalPopulation;
        return opt;
    }

    public static SimRunOptions replay(long seed, int intervalSeconds) {
        return replay(seed, intervalSeconds, -1);
    }

    public static SimRunOptions replay(long seed, int intervalSeconds, int totalPopulation) {
        SimRunOptions opt = new SimRunOptions();
        opt.headless = true;
        opt.csvEnabled = false;
        opt.listenerEnabled = false;
        opt.replayRecordEnabled = true;
        opt.replaySnapshotIntervalSeconds = intervalSeconds;
        opt.randomSeed = seed;
        opt.totalPopulation = totalPopulation;
        return opt;
    }
}
