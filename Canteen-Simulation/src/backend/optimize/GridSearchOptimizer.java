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

        // 核心：纯粹的双重 for 循环全量遍历，不加任何早停逻辑，保证拿到全局最优和完整热力图
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

                // 调用我们重构后的、绝对不会指数爆炸的 LossEvaluator
                double loss = lossEvaluator.evaluate(avgResult, lossConfig);
                avgResult.loss = loss;
                avgResult.step = step;

                // 记录全局最优解
                if (loss < optimizeResult.bestLoss) {
                    optimizeResult.bestLoss = loss;
                    optimizeResult.bestResult = avgResult.copyBasic();
                }

                avgResult.currentBestLoss = optimizeResult.bestLoss;
                optimizeResult.allResults.add(avgResult);

                // 将数据吐给前端更新 UI
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

        // 寻优结束后，用最佳参数跑一次回放，供前端大屏完美复盘
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
            long seed = deriveRunSeed(config.baseRandomSeed, windowCount, tableCount, i);
            SimRunResult r = adapter.runOnce(windowCount, tableCount, SimRunOptions.optimize(seed, config.totalPopulation));
            averager.add(r);
        }
        SimRunResult average = averager.average();
        average.randomSeed = deriveRunSeed(config.baseRandomSeed, windowCount, tableCount, config.repeatTimes);
        average.requestedPopulation = config.totalPopulation;
        applySearchRange(average, config);
        average.baseRandomSeed = config.baseRandomSeed;
        average.repeatTimes = config.repeatTimes;
        return average;
    }

    private boolean isCancelled(CancellationChecker cancellationChecker) {
        return Thread.currentThread().isInterrupted()
                || (cancellationChecker != null && cancellationChecker.isCancelled());
    }

    private ReplayResult runReplay(SimRunResult best, OptimizeConfig config) {
        long seed = deriveRunSeed(config.baseRandomSeed, best.windowCount, best.tableCount, config.repeatTimes);
        SimRunOptions options = SimRunOptions.replay(seed, config.replaySnapshotIntervalSeconds, config.totalPopulation);
        adapter.runOnce(best.windowCount, best.tableCount, options);
        return adapter.getLastReplayResult();
    }

    private long deriveRunSeed(long baseSeed, int windowCount, int tableCount, int repeatIndex) {
        long seed = baseSeed;
        seed ^= 0x9E3779B97F4A7C15L + windowCount * 1000003L;
        seed ^= Long.rotateLeft(tableCount * 10007L, 21);
        seed ^= Long.rotateLeft(repeatIndex * 1009L, 42);
        return seed;
    }

    private void applySearchRange(SimRunResult result, OptimizeConfig config) {
        result.minWindowCount = config.minWindowCount;
        result.maxWindowCount = config.maxWindowCount;
        result.minTableCount = config.minTableCount;
        result.maxTableCount = config.maxTableCount;
    }

    private void printStepLog(int step, int total, SimRunResult r) {
        System.out.println(
                "[寻优] 步骤 " + step + "/" + total
                        + " | 窗口=" + r.windowCount
                        + " | 桌子=" + r.tableCount
                        + " | 等待=" + String.format("%.2f", r.avgWaitTimeMinutes) + "分钟"
                        + " | 最大排队=" + r.maxQueueLength
                        + " | 损失值=" + String.format("%.4f", r.loss)
                        + " | 当前最佳=" + String.format("%.4f", r.currentBestLoss)
        );
    }
}