package backend.optimize;

import backend.config.CanteenConfig;
import backend.engine.SimulationEngine;
import backend.model.ArrivalGenerationResult;
import backend.model.StatisticsResult;
import backend.module.ArrivalModule;

public class SimulationAdapter {
    private ReplayResult lastReplayResult;

    public SimRunResult runOnce(int windowCount, int tableCount, SimRunOptions options) {
        if (options == null) {
            options = SimRunOptions.optimize(CanteenConfig.RANDOM_SEED);
        }
        CanteenConfig.CanteenConfigSnapshot snapshot = CanteenConfig.snapshot();
        long start = System.currentTimeMillis();

        try {
            CanteenConfig.RANDOM_SEED = options.randomSeed >= 0 ? options.randomSeed : CanteenConfig.RANDOM_SEED;
            CanteenConfig.setWindowCount(windowCount);
            CanteenConfig.setTableCount(tableCount);
            CanteenConfig.HEADLESS_MODE = options.headless;
            CanteenConfig.CSV_ENABLED = options.csvEnabled;
            CanteenConfig.LISTENER_ENABLED = options.listenerEnabled;
            CanteenConfig.REPLAY_RECORD_ENABLED = options.replayRecordEnabled;
            CanteenConfig.REPLAY_SNAPSHOT_INTERVAL_SECONDS = options.replaySnapshotIntervalSeconds;

            ArrivalModule arrivalModule = new ArrivalModule(CanteenConfig.RANDOM_SEED);
            ArrivalGenerationResult arrivalResult = arrivalModule.generateArrivalPlan(
                    CanteenConfig.TOTAL_POPULATION,
                    CanteenConfig.SIMULATION_MODE,
                    CanteenConfig.MEAL_PERIOD
            );

            SimulationEngine engine = new SimulationEngine(
                    arrivalResult.getStudents(),
                    null,
                    1L,
                    arrivalResult.getPhaseBoundaries()
            );

            ReplayRecorder recorder = null;
            if (options.replayRecordEnabled) {
                recorder = new ReplayRecorder(windowCount, tableCount, options.replaySnapshotIntervalSeconds);
                engine.setReplayRecorder(recorder);
            }

            engine.run();
            StatisticsResult statistics = engine.getStatisticsResult();
            statistics.setRuntimeMs(System.currentTimeMillis() - start);

            if (recorder != null) {
                lastReplayResult = recorder.getReplayResult();
            } else {
                lastReplayResult = null;
            }

            SimRunResult result = convert(statistics);
            result.windowCount = windowCount;
            result.tableCount = tableCount;
            result.runtimeMs = System.currentTimeMillis() - start;
            result.randomSeed = CanteenConfig.RANDOM_SEED;
            result.mealMode = CanteenConfig.SIMULATION_MODE + ":" + CanteenConfig.MEAL_PERIOD;
            return result;
        } finally {
            CanteenConfig.restore(snapshot);
        }
    }

    public ReplayResult getLastReplayResult() {
        return lastReplayResult;
    }

    private SimRunResult convert(StatisticsResult s) {
        SimRunResult r = new SimRunResult();
        r.totalStudents = s.getTotalStudents();
        r.arrivedStudents = s.getArrivedStudents();
        r.servedStudents = s.getServedStudents();
        r.abandonedStudents = s.getAbandonedStudents();
        r.finishedStudents = s.getFinishedStudents();
        r.avgWaitTimeSeconds = s.getAvgWaitTimeSeconds();
        r.avgWaitTimeMinutes = s.getAvgWaitTimeMinutes();
        r.maxQueueLength = s.getMaxQueueLength();
        r.avgQueueLength = s.getAvgQueueLength();
        r.seatUtilization = s.getSeatUtilization();
        r.windowUtilization = s.getWindowUtilization();
        r.finishRate = s.getFinishRate();
        r.abandonRate = s.getAbandonRate();
        r.runtimeMs = s.getRuntimeMs();
        return r;
    }
}
