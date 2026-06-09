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
            if (options.totalPopulation > 0) {
                CanteenConfig.TOTAL_POPULATION = options.totalPopulation;
            }
            CanteenConfig.applyOptimizationDecision(snapshot, windowCount, tableCount);
            CanteenConfig.HEADLESS_MODE = options.headless;
            CanteenConfig.CSV_ENABLED = options.csvEnabled;
            CanteenConfig.LISTENER_ENABLED = options.listenerEnabled;
            CanteenConfig.REPLAY_RECORD_ENABLED = options.replayRecordEnabled;
            CanteenConfig.REPLAY_SNAPSHOT_INTERVAL_SECONDS = options.replaySnapshotIntervalSeconds;

            ArrivalModule arrivalModule = new ArrivalModule(CanteenConfig.RANDOM_SEED);
            ArrivalGenerationResult arrivalResult = arrivalModule.generateArrivalPlan(
                    CanteenConfig.TOTAL_POPULATION,
                    CanteenConfig.SIMULATION_MODE,
                    CanteenConfig.MEAL_PERIOD,
                    !options.headless
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
            result.requestedPopulation = CanteenConfig.TOTAL_POPULATION;
            result.openDuration = CanteenConfig.OPEN_DURATION;
            result.probSolo = CanteenConfig.PROB_SOLO;
            result.avgMealPrice = CanteenConfig.AVG_MEAL_PRICE;
            result.windowCostPerHour = CanteenConfig.WINDOW_COST_PER_HOUR;
            result.tableCost = CanteenConfig.TABLE_COST;
            result.lostStudentPenalty = CanteenConfig.LOST_STUDENT_PENALTY;
            result.simulationModeCode = CanteenConfig.SIMULATION_MODE.getCode();
            result.mealPeriodCode = CanteenConfig.MEAL_PERIOD.getCode();
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
        r.p95WaitTimeSeconds = s.getP95WaitTimeSeconds();
        r.p95WaitTimeMinutes = s.getP95WaitTimeMinutes();
        r.avgSeatWaitTimeSeconds = s.getAvgSeatWaitTimeSeconds();
        r.avgSeatWaitTimeMinutes = s.getAvgSeatWaitTimeMinutes();
        r.p95SeatWaitTimeSeconds = s.getP95SeatWaitTimeSeconds();
        r.p95SeatWaitTimeMinutes = s.getP95SeatWaitTimeMinutes();
        r.avgTotalStayTimeSeconds = s.getAvgTotalStayTimeSeconds();
        r.avgTotalStayTimeMinutes = s.getAvgTotalStayTimeMinutes();
        r.p95TotalStayTimeSeconds = s.getP95TotalStayTimeSeconds();
        r.p95TotalStayTimeMinutes = s.getP95TotalStayTimeMinutes();
        r.maxQueueLength = s.getMaxQueueLength();
        r.maxWaitingSeatCount = s.getMaxWaitingSeatCount();
        r.avgQueueLength = s.getAvgQueueLength();
        r.seatUtilization = s.getSeatUtilization();
        r.tableUtilization = s.getTableUtilization();
        r.windowUtilization = s.getWindowUtilization();
        r.finishRate = s.getFinishRate();
        r.abandonRate = s.getAbandonRate();
        r.grossRevenue = s.getGrossRevenue();
        r.windowOperatingCost = s.getWindowCost();
        r.tableOperatingCost = s.getTableCost();
        r.lostOpportunityCost = s.getLostOpportunityCost();
        r.netProfit = s.getNetProfit();
        r.runtimeMs = s.getRuntimeMs();
        return r;
    }
}
