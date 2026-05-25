package backend.optimize;

public class ReplayRecorder {
    private final ReplayResult replayResult = new ReplayResult();
    private final int intervalSeconds;
    private int lastRecordTime = -1;

    public ReplayRecorder(int windowCount, int tableCount, int intervalSeconds) {
        replayResult.windowCount = windowCount;
        replayResult.tableCount = tableCount;
        this.intervalSeconds = intervalSeconds <= 0 ? 60 : intervalSeconds;
    }

    public void tryRecord(int currentTimeSecond, ReplaySnapshot snapshot) {
        if (lastRecordTime < 0 || currentTimeSecond - lastRecordTime >= intervalSeconds) {
            replayResult.addSnapshot(snapshot);
            lastRecordTime = currentTimeSecond;
        }
    }

    public ReplayResult getReplayResult() {
        return replayResult;
    }
}
