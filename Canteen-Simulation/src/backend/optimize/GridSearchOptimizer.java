package backend.optimize;

public class GridSearchOptimizer {
    private final SimulationAdapter adapter = new SimulationAdapter();
    private final LossEvaluator lossEvaluator = new LossEvaluator();

    public OptimizeResult run(OptimizeConfig optimizeConfig, LossConfig lossConfig) {
        optimizeConfig.validate();
        lossConfig.validate();

        OptimizeResult optimizeResult = new OptimizeResult();
        optimizeResult.totalCandidateCount = optimizeConfig.totalCandidateCount();
        optimizeResult.totalSimulationCount = optimizeConfig.totalSimulationCount();

        long totalStart = System.currentTimeMillis();
        int step = 0;

        for (int w = optimizeConfig.minWindowCount; w <= optimizeConfig.maxWindowCount; w++) {
            for (int t = optimizeConfig.minTableCount; t <= optimizeConfig.maxTableCount; t++) {
                step++;
                SimRunResult avgResult = runRepeated(w, t, step, optimizeConfig);
                double loss = lossEvaluator.evaluate(avgResult, lossConfig);
                avgResult.loss = loss;
                avgResult.step = step;

                if (loss < optimizeResult.bestLoss) {
                    optimizeResult.bestLoss = loss;
                    optimizeResult.bestResult = avgResult.copyBasic();
                }

                avgResult.currentBestLoss = optimizeResult.bestLoss;
                optimizeResult.allResults.add(avgResult);

                if (optimizeConfig.verboseConsoleLog) {
                    printStepLog(step, optimizeConfig.totalCandidateCount(), avgResult);
                }
            }
        }

        optimizeResult.totalRuntimeMs = System.currentTimeMillis() - totalStart;
        optimizeResult.buildTopK(optimizeConfig.topK);

        if (optimizeConfig.runReplayAfterOptimization && optimizeResult.bestResult != null) {
            optimizeResult.replayResult = runReplay(optimizeResult.bestResult, optimizeConfig);
        }

        return optimizeResult;
    }

    private SimRunResult runRepeated(int windowCount, int tableCount, int step, OptimizeConfig config) {
        ResultAverager averager = new ResultAverager();
        for (int i = 0; i < config.repeatTimes; i++) {
            long seed = config.baseRandomSeed + step * 1000L + i;
            SimRunResult r = adapter.runOnce(windowCount, tableCount, SimRunOptions.optimize(seed));
            averager.add(r);
        }
        return averager.average();
    }

    private ReplayResult runReplay(SimRunResult best, OptimizeConfig config) {
        long seed = config.baseRandomSeed + 999999L;
        SimRunOptions options = SimRunOptions.replay(seed, config.replaySnapshotIntervalSeconds);
        adapter.runOnce(best.windowCount, best.tableCount, options);
        return adapter.getLastReplayResult();
    }

    private void printStepLog(int step, int total, SimRunResult r) {
        System.out.println(
                "[Optimize] step " + step + "/" + total
                        + " | window=" + r.windowCount
                        + " | table=" + r.tableCount
                        + " | wait=" + String.format("%.2f", r.avgWaitTimeMinutes) + "min"
                        + " | maxQ=" + r.maxQueueLength
                        + " | seatUse=" + String.format("%.2f", r.seatUtilization)
                        + " | abandon=" + String.format("%.2f", r.abandonRate)
                        + " | loss=" + String.format("%.4f", r.loss)
                        + " | best=" + String.format("%.4f", r.currentBestLoss)
        );
    }
}
