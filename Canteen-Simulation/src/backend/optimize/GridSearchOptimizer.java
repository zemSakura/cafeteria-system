package backend.optimize;

public class GridSearchOptimizer {
    private final SimulationAdapter adapter = new SimulationAdapter();
    private final LossEvaluator lossEvaluator = new LossEvaluator();

    public interface ProgressListener {
        void onStepFinished(int step, int total, SimRunResult stepResult, OptimizeResult currentResult);
    }

    public interface CancellationChecker {
        boolean isCancelled();
    }

    public OptimizeResult run(OptimizeConfig optimizeConfig, LossConfig lossConfig) {
        return run(optimizeConfig, lossConfig, null, null);
    }

    public OptimizeResult run(OptimizeConfig optimizeConfig,
                              LossConfig lossConfig,
                              ProgressListener progressListener,
                              CancellationChecker cancellationChecker) {
        optimizeConfig.validate();
        lossConfig.validate();

        OptimizeResult optimizeResult = new OptimizeResult();
        optimizeResult.totalCandidateCount = optimizeConfig.totalCandidateCount();
        optimizeResult.totalSimulationCount = optimizeConfig.totalSimulationCount();

        long totalStart = System.currentTimeMillis();
        int step = 0;

        for (int w = optimizeConfig.minWindowCount; w <= optimizeConfig.maxWindowCount; w++) {
            for (int t = optimizeConfig.minTableCount; t <= optimizeConfig.maxTableCount; t++) {
                if (isCancelled(cancellationChecker)) {
                    optimizeResult.totalRuntimeMs = System.currentTimeMillis() - totalStart;
                    optimizeResult.buildTopK(optimizeConfig.topK);
                    return optimizeResult;
                }

                step++;
                SimRunResult avgResult = runRepeated(w, t, step, optimizeConfig, cancellationChecker);
                if (avgResult == null) {
                    optimizeResult.totalRuntimeMs = System.currentTimeMillis() - totalStart;
                    optimizeResult.buildTopK(optimizeConfig.topK);
                    return optimizeResult;
                }

                double loss = lossEvaluator.evaluate(avgResult, lossConfig);
                avgResult.loss = loss;
                avgResult.step = step;

                if (loss < optimizeResult.bestLoss) {
                    optimizeResult.bestLoss = loss;
                    optimizeResult.bestResult = avgResult.copyBasic();
                }

                avgResult.currentBestLoss = optimizeResult.bestLoss;
                optimizeResult.allResults.add(avgResult);
                if (progressListener != null) {
                    progressListener.onStepFinished(step, optimizeResult.totalCandidateCount, avgResult, optimizeResult);
                }

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

    private SimRunResult runRepeated(int windowCount,
                                     int tableCount,
                                     int step,
                                     OptimizeConfig config,
                                     CancellationChecker cancellationChecker) {
        ResultAverager averager = new ResultAverager();
        for (int i = 0; i < config.repeatTimes; i++) {
            if (isCancelled(cancellationChecker)) {
                return null;
            }
            long seed = config.baseRandomSeed + step * 1000L + i;
            SimRunResult r = adapter.runOnce(windowCount, tableCount, SimRunOptions.optimize(seed));
            averager.add(r);
        }
        return averager.average();
    }

    private boolean isCancelled(CancellationChecker cancellationChecker) {
        return Thread.currentThread().isInterrupted()
                || (cancellationChecker != null && cancellationChecker.isCancelled());
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
