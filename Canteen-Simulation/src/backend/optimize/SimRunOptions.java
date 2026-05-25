package backend.optimize;

public class SimRunOptions {
    public boolean headless = false;
    public boolean csvEnabled = true;
    public boolean listenerEnabled = true;
    public boolean replayRecordEnabled = false;
    public int replaySnapshotIntervalSeconds = 60;
    public long randomSeed = -1L;

    public static SimRunOptions normal() {
        SimRunOptions opt = new SimRunOptions();
        opt.headless = false;
        opt.csvEnabled = true;
        opt.listenerEnabled = true;
        opt.replayRecordEnabled = false;
        return opt;
    }

    public static SimRunOptions optimize(long seed) {
        SimRunOptions opt = new SimRunOptions();
        opt.headless = true;
        opt.csvEnabled = false;
        opt.listenerEnabled = false;
        opt.replayRecordEnabled = false;
        opt.randomSeed = seed;
        return opt;
    }

    public static SimRunOptions replay(long seed, int intervalSeconds) {
        SimRunOptions opt = new SimRunOptions();
        opt.headless = true;
        opt.csvEnabled = false;
        opt.listenerEnabled = false;
        opt.replayRecordEnabled = true;
        opt.replaySnapshotIntervalSeconds = intervalSeconds;
        opt.randomSeed = seed;
        return opt;
    }
}
