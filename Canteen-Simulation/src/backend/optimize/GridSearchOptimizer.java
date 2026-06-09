package backend.optimize;

import backend.config.CanteenConfig;
import backend.dto.OptimizationMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        lossConfig.apply(optimizeConfig);
        lossConfig.validate();

        if (optimizeConfig.useAdaptiveBoundarySearch) {
            return runAdaptiveBoundarySearch(optimizeConfig, lossConfig, progressListener, cancellationChecker);
        }
        if (optimizeConfig.useConvergentSearch) {
            return runConvergentSearch(optimizeConfig, lossConfig, progressListener, cancellationChecker);
        }
        return runGridSearch(optimizeConfig, lossConfig, progressListener, cancellationChecker);
    }

    private OptimizeResult runAdaptiveBoundarySearch(OptimizeConfig config,
                                                     LossConfig lossConfig,
                                                     ProgressListener progressListener,
                                                     CancellationChecker cancellationChecker) {
        OptimizeResult optimizeResult = new OptimizeResult();
        int budget = config.totalCandidateCount();
        optimizeResult.totalCandidateCount = budget;
        optimizeResult.totalSimulationCount = budget * config.repeatTimes;

        long totalStart = System.currentTimeMillis();
        Map<String, SimRunResult> evaluated = new LinkedHashMap<>();
        int[] stepRef = new int[]{0};

        int windowMin = Math.max(1, config.minWindowCount);
        int windowMax = Math.max(windowMin, config.maxWindowCount);
        int tableMin = Math.max(1, config.minTableCount);
        int tableMax = Math.max(tableMin, config.maxTableCount);
        int windowLimit = Math.max(windowMax, config.maxWindowLimit);
        int tableLowerLimit = 1;
        int tableLimit = Math.max(tableMax, config.maxTableLimit);

        SimRunResult globalBest = null;
        int stableRounds = 0;
        String stopReason = "MAX_ROUND_REACHED";

        for (int round = 1; round <= config.maxAutoRounds && stepRef[0] < budget; round++) {
            if (isCancelled(cancellationChecker)) {
                stopReason = "CANCELLED";
                break;
            }
            optimizeResult.searchRounds = round;
            optimizeResult.rangeHistory.add("第 " + round + " 轮：窗口 "
                    + windowMin + "-" + windowMax + "，桌子 " + tableMin + "-" + tableMax);

            List<int[]> candidates = buildAdaptiveRoundCandidates(
                    windowMin, windowMax, tableMin, tableMax, config);
            evaluateAdaptiveCandidateList(
                    candidates,
                    stepRef,
                    budget,
                    config,
                    lossConfig,
                    optimizeResult,
                    progressListener,
                    cancellationChecker,
                    evaluated,
                    windowMin,
                    windowMax,
                    tableMin,
                    tableMax
            );

            if (isCancelled(cancellationChecker)) {
                stopReason = "CANCELLED";
                break;
            }

            SimRunResult roundBest = bestKnownCandidate(candidates, evaluated, config.optimizationMode);
            if (roundBest == null) {
                stopReason = "NO_VALID_RESULT";
                break;
            }

            boolean improved = isMeaningfullyBetter(globalBest, roundBest, config);
            if (globalBest == null || improved) {
                globalBest = roundBest.copyBasic();
                optimizeResult.bestResult = globalBest.copyBasic();
                optimizeResult.bestLoss = globalBest.loss;
                stableRounds = 0;
            }

            boolean requestedExpansion = false;
            boolean expanded = false;
            if (roundBest.windowCount >= windowMax - config.windowBoundaryMargin) {
                requestedExpansion = true;
                if (windowMax < windowLimit) {
                    windowMax = Math.min(windowLimit, windowMax + config.windowExpandStep);
                    expanded = true;
                }
            }
            if (roundBest.tableCount >= tableMax - config.tableBoundaryMargin) {
                requestedExpansion = true;
                if (tableMax < tableLimit) {
                    tableMax = Math.min(tableLimit, tableMax + config.tableExpandStep);
                    expanded = true;
                }
            }
            if (roundBest.tableCount <= tableMin + config.tableBoundaryMargin) {
                requestedExpansion = true;
                if (tableMin > tableLowerLimit) {
                    tableMin = Math.max(tableLowerLimit, tableMin - config.tableExpandStep);
                    expanded = true;
                }
            }
            if (globalBest != null && !globalBest.serviceLevelPassed) {
                if (needsMoreWindows(globalBest, lossConfig) && windowMax < windowLimit) {
                    requestedExpansion = true;
                    windowMax = Math.min(windowLimit, windowMax + config.windowExpandStep);
                    expanded = true;
                }
                if (needsMoreTables(globalBest, lossConfig) && tableMax < tableLimit) {
                    requestedExpansion = true;
                    tableMax = Math.min(tableLimit, tableMax + config.tableExpandStep);
                    expanded = true;
                }
            }

            if (requestedExpansion && !expanded) {
                stopReason = "MAX_RESOURCE_LIMIT_REACHED";
                break;
            }
            if (expanded) {
                stableRounds = 0;
                continue;
            }

            boolean safelyInside = roundBest.windowCount > windowMin + config.windowBoundaryMargin
                    && roundBest.windowCount < windowMax - config.windowBoundaryMargin
                    && roundBest.tableCount > tableMin + config.tableBoundaryMargin
                    && roundBest.tableCount < tableMax - config.tableBoundaryMargin;
            if (safelyInside) {
                if (!improved) {
                    stableRounds++;
                }
                if (stableRounds >= config.stableRoundLimit && globalBest != null) {
                    SimRunResult verified = validateAdaptiveNeighborhood(
                            globalBest,
                            stepRef,
                            budget,
                            config,
                            lossConfig,
                            optimizeResult,
                            progressListener,
                            cancellationChecker,
                            evaluated,
                            windowMin,
                            windowMax,
                            tableMin,
                            tableMax
                    );
                    if (isMeaningfullyBetter(globalBest, verified, config)) {
                        globalBest = verified.copyBasic();
                        stableRounds = 0;
                    } else {
                        stopReason = "STABLE_INSIDE_RANGE";
                        break;
                    }
                }
            } else {
                if (windowMax < windowLimit) {
                    windowMax = Math.min(windowLimit, windowMax + config.windowExpandStep);
                }
                if (tableMax < tableLimit) {
                    tableMax = Math.min(tableLimit, tableMax + config.tableExpandStep);
                }
                stableRounds = 0;
            }
        }

        if (stepRef[0] >= budget && !"STABLE_INSIDE_RANGE".equals(stopReason)) {
            stopReason = "MAX_EVALUATION_BUDGET_REACHED";
        }
        if (globalBest != null) {
            optimizeResult.bestResult = globalBest.copyBasic();
            optimizeResult.bestLoss = globalBest.loss;
        }
        optimizeResult.totalRuntimeMs = System.currentTimeMillis() - totalStart;
        optimizeResult.totalCandidateCount = optimizeResult.allResults.size();
        optimizeResult.totalSimulationCount = optimizeResult.allResults.size() * config.repeatTimes;
        optimizeResult.stopReason = stopReason;

        populateCurrentPlan(optimizeResult, config, lossConfig, cancellationChecker);
        optimizeResult.buildTopK(config.topK, config.optimizationMode, config.onlyShowServiceLevelPassed);

        if (config.runReplayAfterOptimization && optimizeResult.bestResult != null) {
            optimizeResult.replayResult = runReplay(optimizeResult.bestResult, config);
        }

        return optimizeResult;
    }

    private OptimizeResult runGridSearch(OptimizeConfig optimizeConfig,
                                         LossConfig lossConfig,
                                         ProgressListener progressListener,
                                         CancellationChecker cancellationChecker) {
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
                    optimizeResult.buildTopK(optimizeConfig.topK, optimizeConfig.optimizationMode,
                            optimizeConfig.onlyShowServiceLevelPassed);
                    return optimizeResult;
                }

                step++;
                SimRunResult avgResult = evaluateCandidate(
                        w,
                        t,
                        step,
                        optimizeConfig,
                        lossConfig,
                        optimizeResult,
                        progressListener,
                        cancellationChecker
                );
                if (avgResult == null) {
                    optimizeResult.totalRuntimeMs = System.currentTimeMillis() - totalStart;
                    optimizeResult.buildTopK(optimizeConfig.topK, optimizeConfig.optimizationMode,
                            optimizeConfig.onlyShowServiceLevelPassed);
                    return optimizeResult;
                }

                if (optimizeConfig.verboseConsoleLog) {
                    printStepLog(step, optimizeConfig.totalCandidateCount(), avgResult);
                }
            }
        }

        optimizeResult.totalRuntimeMs = System.currentTimeMillis() - totalStart;
        populateCurrentPlan(optimizeResult, optimizeConfig, lossConfig, cancellationChecker);
        optimizeResult.buildTopK(optimizeConfig.topK, optimizeConfig.optimizationMode,
                optimizeConfig.onlyShowServiceLevelPassed);

        // 寻优结束后，用最佳参数跑一次回放，供前端大屏完美复盘
        if (optimizeConfig.runReplayAfterOptimization && optimizeResult.bestResult != null) {
            optimizeResult.replayResult = runReplay(optimizeResult.bestResult, optimizeConfig);
        }

        return optimizeResult;
    }

    private OptimizeResult runConvergentSearch(OptimizeConfig config,
                                               LossConfig lossConfig,
                                               ProgressListener progressListener,
                                               CancellationChecker cancellationChecker) {
        OptimizeResult optimizeResult = new OptimizeResult();
        int budget = config.totalCandidateCount();
        optimizeResult.totalCandidateCount = budget;
        optimizeResult.totalSimulationCount = budget * config.repeatTimes;

        long totalStart = System.currentTimeMillis();
        Map<String, SimRunResult> evaluated = new LinkedHashMap<>();
        int[] stepRef = new int[]{0};

        evaluateCandidateList(
                buildGlobalProbeCandidates(config),
                stepRef,
                budget,
                config,
                lossConfig,
                optimizeResult,
                progressListener,
                cancellationChecker,
                evaluated
        );

        int restartIndex = 0;
        while (stepRef[0] < budget && !isCancelled(cancellationChecker)) {
            List<SimRunResult> seeds = sortedResultsByLoss(optimizeResult);
            if (seeds.isEmpty()) {
                break;
            }

            int restartCount = Math.min(config.localRestartCount, seeds.size());
            SimRunResult seed = seeds.get(restartIndex % restartCount);
            boolean madeProgress = runLocalDescentFromSeed(
                    seed,
                    stepRef,
                    budget,
                    config,
                    lossConfig,
                    optimizeResult,
                    progressListener,
                    cancellationChecker,
                    evaluated
            );
            restartIndex++;

            if (!madeProgress) {
                evaluateCandidateIfNeeded(
                        explorationWindow(stepRef[0] + restartIndex, config),
                        explorationTable(stepRef[0] + restartIndex, config),
                        stepRef,
                        budget,
                        config,
                        lossConfig,
                        optimizeResult,
                        progressListener,
                        cancellationChecker,
                        evaluated
                );
            }

            if (restartIndex >= config.localRestartCount
                    && stepRef[0] >= Math.min(budget, config.globalProbeCount + config.localRestartCount)) {
                boolean hasRoomForExploration = stepRef[0] < budget
                        && evaluated.size() < config.fullGridCandidateCount();
                if (!hasRoomForExploration) {
                    break;
                }
            }
        }

        optimizeResult.totalRuntimeMs = System.currentTimeMillis() - totalStart;
        optimizeResult.totalCandidateCount = optimizeResult.allResults.size();
        optimizeResult.totalSimulationCount = optimizeResult.allResults.size() * config.repeatTimes;
        populateCurrentPlan(optimizeResult, config, lossConfig, cancellationChecker);
        optimizeResult.buildTopK(config.topK, config.optimizationMode, config.onlyShowServiceLevelPassed);

        if (config.runReplayAfterOptimization && optimizeResult.bestResult != null) {
            optimizeResult.replayResult = runReplay(optimizeResult.bestResult, config);
        }

        return optimizeResult;
    }

    private void populateCurrentPlan(OptimizeResult optimizeResult,
                                     OptimizeConfig config,
                                     LossConfig lossConfig,
                                     CancellationChecker cancellationChecker) {
        if (optimizeResult == null || isCancelled(cancellationChecker)) {
            return;
        }

        SimRunResult current = null;
        for (SimRunResult result : optimizeResult.allResults) {
            if (result.windowCount == config.currentWindowCount
                    && result.tableCount == config.currentTableCount) {
                current = result.copyBasic();
                break;
            }
        }

        if (current == null) {
            current = runRepeated(config.currentWindowCount, config.currentTableCount, 0, config, cancellationChecker);
            if (current == null) {
                return;
            }
            current.step = 0;
            current.loss = lossEvaluator.evaluate(current, lossConfig);
        }
        optimizeResult.currentResult = current.copyBasic();
    }

    private boolean runLocalDescentFromSeed(SimRunResult seed,
                                            int[] stepRef,
                                            int budget,
                                            OptimizeConfig config,
                                            LossConfig lossConfig,
                                            OptimizeResult optimizeResult,
                                            ProgressListener progressListener,
                                            CancellationChecker cancellationChecker,
                                            Map<String, SimRunResult> evaluated) {
        int currentWindow = seed.windowCount;
        int currentTable = seed.tableCount;
        int windowStep = initialStep(config.minWindowCount, config.maxWindowCount);
        int tableStep = initialStep(config.minTableCount, config.maxTableCount);
        int stagnantRounds = 0;
        boolean madeProgress = false;

        while (stepRef[0] < budget && !isCancelled(cancellationChecker)) {
            String centerKey = key(currentWindow, currentTable);
            SimRunResult center = evaluated.get(centerKey);
            if (center == null) {
                center = seed;
            }

            List<int[]> neighborhood = buildNeighborhood(currentWindow, currentTable, windowStep, tableStep, config);
            evaluateCandidateList(
                    neighborhood,
                    stepRef,
                    budget,
                    config,
                    lossConfig,
                    optimizeResult,
                    progressListener,
                    cancellationChecker,
                    evaluated
            );

            SimRunResult bestNeighbor = bestKnownCandidate(neighborhood, evaluated);
            if (bestNeighbor != null && center.loss - bestNeighbor.loss > config.minLossImprovement) {
                currentWindow = bestNeighbor.windowCount;
                currentTable = bestNeighbor.tableCount;
                stagnantRounds = 0;
                madeProgress = true;
                continue;
            }

            stagnantRounds++;
            windowStep = Math.max(1, windowStep / 2);
            tableStep = Math.max(1, tableStep / 2);
            if (windowStep == 1 && tableStep == 1 && stagnantRounds >= config.stagnationPatience) {
                break;
            }
        }

        return madeProgress;
    }

    private List<int[]> buildAdaptiveRoundCandidates(int windowMin,
                                                     int windowMax,
                                                     int tableMin,
                                                     int tableMax,
                                                     OptimizeConfig config) {
        Map<String, int[]> unique = new LinkedHashMap<>();
        List<Integer> tableProbes = buildTableProbeValues(tableMin, tableMax, config);
        List<Integer> windowProbes = buildWindowProbeValues(windowMin, windowMax, config, tableProbes.size());

        // Cover the full window range first. A large table range must not consume
        // the whole evaluation budget before practical window counts are tested.
        for (int table : tableProbes) {
            for (int window : windowProbes) {
                addAdaptiveCandidate(unique, window, table, windowMin, windowMax, tableMin, tableMax);
            }
        }

        int midWindow = midpoint(windowMin, windowMax);
        int midTable = midpoint(tableMin, tableMax);
        addAdaptiveCandidate(unique, midWindow, midTable, windowMin, windowMax, tableMin, tableMax);
        addAdaptiveCandidate(unique, config.currentWindowCount, config.currentTableCount,
                windowMin, windowMax, tableMin, tableMax);
        addAdaptiveCandidate(unique, windowMin, tableMin, windowMin, windowMax, tableMin, tableMax);
        addAdaptiveCandidate(unique, windowMin, tableMax, windowMin, windowMax, tableMin, tableMax);
        addAdaptiveCandidate(unique, windowMax, tableMin, windowMin, windowMax, tableMin, tableMax);
        addAdaptiveCandidate(unique, windowMax, tableMax, windowMin, windowMax, tableMin, tableMax);

        int tableStep = Math.max(1, config.adaptiveTableScanStep);
        for (int window = windowMin; window <= windowMax; window++) {
            for (int table = tableMin; table <= tableMax; table += tableStep) {
                addAdaptiveCandidate(unique, window, table, windowMin, windowMax, tableMin, tableMax);
            }
            addAdaptiveCandidate(unique, window, tableMax, windowMin, windowMax, tableMin, tableMax);
        }
        return new ArrayList<>(unique.values());
    }

    private List<Integer> buildWindowProbeValues(int windowMin,
                                                 int windowMax,
                                                 OptimizeConfig config,
                                                 int tableProbeCount) {
        int windowCells = Math.max(1, windowMax - windowMin + 1);
        int budgetPerTableProbe = Math.max(1, config.maxCandidateEvaluations / Math.max(1, tableProbeCount));
        int probeCount = Math.min(windowCells, Math.max(8, budgetPerTableProbe));
        Map<Integer, Integer> unique = new LinkedHashMap<>();
        addIntProbe(unique, config.currentWindowCount, windowMin, windowMax);
        addIntProbe(unique, estimatePracticalWindowCount(config), windowMin, windowMax);
        addIntProbe(unique, midpoint(windowMin, windowMax), windowMin, windowMax);
        addEvenlySpacedProbes(unique, windowMin, windowMax, probeCount);
        return new ArrayList<>(unique.keySet());
    }

    private List<Integer> buildTableProbeValues(int tableMin,
                                                int tableMax,
                                                OptimizeConfig config) {
        int estimated = estimatePracticalTableCount(config);
        Map<Integer, Integer> unique = new LinkedHashMap<>();
        addIntProbe(unique, estimated, tableMin, tableMax);
        addIntProbe(unique, (int) Math.round(estimated * 0.75), tableMin, tableMax);
        addIntProbe(unique, (int) Math.round(estimated * 1.25), tableMin, tableMax);
        addIntProbe(unique, (int) Math.round(estimated * 1.50), tableMin, tableMax);
        addIntProbe(unique, (int) Math.round(estimated * 2.00), tableMin, tableMax);
        addIntProbe(unique, config.currentTableCount, tableMin, tableMax);
        addIntProbe(unique, midpoint(tableMin, tableMax), tableMin, tableMax);
        if (unique.size() < 6) {
            addEvenlySpacedProbes(unique, tableMin, tableMax, 6);
        }
        return new ArrayList<>(unique.keySet());
    }

    private int estimatePracticalWindowCount(OptimizeConfig config) {
        double peakPopulation = estimatePeakMealPopulation(config);
        double openSeconds = Math.max(60.0, CanteenConfig.OPEN_DURATION * 60.0);
        double capacityPerWindow = Math.max(1.0, openSeconds / averageServeSeconds());
        return Math.max(1, (int) Math.ceil(peakPopulation / capacityPerWindow / 0.85));
    }

    private int estimatePracticalTableCount(OptimizeConfig config) {
        if (!"fullDay".equalsIgnoreCase(CanteenConfig.SIMULATION_MODE.getCode())) {
            return OptimizeConfig.estimatePracticalTableCount(config.totalPopulation, 0.0, 1.0, 0.0);
        }
        return OptimizeConfig.estimatePracticalTableCount(config.totalPopulation);
    }

    private double estimatePeakMealPopulation(OptimizeConfig config) {
        if (!"fullDay".equalsIgnoreCase(CanteenConfig.SIMULATION_MODE.getCode())) {
            return config.totalPopulation;
        }
        double breakfast = Math.max(0.0, CanteenConfig.BREAKFAST_POPULATION_RATIO);
        double lunch = Math.max(0.0, CanteenConfig.LUNCH_POPULATION_RATIO);
        double dinner = Math.max(0.0, CanteenConfig.DINNER_POPULATION_RATIO);
        double sum = breakfast + lunch + dinner;
        if (sum <= 0.0) {
            breakfast = CanteenConfig.DEFAULT_BREAKFAST_POPULATION_RATIO;
            lunch = CanteenConfig.DEFAULT_LUNCH_POPULATION_RATIO;
            dinner = CanteenConfig.DEFAULT_DINNER_POPULATION_RATIO;
            sum = breakfast + lunch + dinner;
        }
        double peakRatio = Math.max(breakfast, Math.max(lunch, dinner)) / sum;
        return config.totalPopulation * peakRatio;
    }

    private double averageServeSeconds() {
        int[] serveTimes = CanteenConfig.WINDOW_AVG_SERVE_TIME;
        if (serveTimes == null || serveTimes.length == 0) {
            serveTimes = CanteenConfig.DEFAULT_WINDOW_AVG_SERVE_TIME;
        }
        double sum = 0.0;
        for (int serveTime : serveTimes) {
            sum += Math.max(1, serveTime);
        }
        return Math.max(1.0, sum / serveTimes.length);
    }

    private void addEvenlySpacedProbes(Map<Integer, Integer> unique,
                                       int min,
                                       int max,
                                       int count) {
        if (count <= 1 || min == max) {
            addIntProbe(unique, min, min, max);
            return;
        }
        for (int i = 0; i < count; i++) {
            int value = min + (int) Math.round((max - min) * (i / (double) (count - 1)));
            addIntProbe(unique, value, min, max);
        }
    }

    private void addIntProbe(Map<Integer, Integer> unique, int value, int min, int max) {
        int clamped = clamp(value, min, max);
        unique.put(clamped, clamped);
    }

    private void evaluateAdaptiveCandidateList(List<int[]> candidates,
                                               int[] stepRef,
                                               int budget,
                                               OptimizeConfig config,
                                               LossConfig lossConfig,
                                               OptimizeResult optimizeResult,
                                               ProgressListener progressListener,
                                               CancellationChecker cancellationChecker,
                                               Map<String, SimRunResult> evaluated,
                                               int windowMin,
                                               int windowMax,
                                               int tableMin,
                                               int tableMax) {
        for (int[] candidate : candidates) {
            if (stepRef[0] >= budget || isCancelled(cancellationChecker)) {
                return;
            }
            evaluateAdaptiveCandidateIfNeeded(
                    candidate[0],
                    candidate[1],
                    stepRef,
                    budget,
                    config,
                    lossConfig,
                    optimizeResult,
                    progressListener,
                    cancellationChecker,
                    evaluated,
                    windowMin,
                    windowMax,
                    tableMin,
                    tableMax
            );
        }
    }

    private boolean needsMoreWindows(SimRunResult result, LossConfig lossConfig) {
        if (result == null || lossConfig == null) {
            return false;
        }
        return result.p95WaitTimeMinutes > lossConfig.hardWaitThresholdMinutes
                || result.maxQueueLength > lossConfig.hardQueueThresholdLength
                || result.abandonRate > lossConfig.hardAbandonThresholdRate;
    }

    private boolean needsMoreTables(SimRunResult result, LossConfig lossConfig) {
        if (result == null || lossConfig == null) {
            return false;
        }
        return result.p95SeatWaitTimeMinutes > lossConfig.hardSeatWaitThresholdMinutes
                || result.seatUtilization > lossConfig.hardSeatUtilizationThreshold
                || result.finishRate < lossConfig.minAcceptFinishRate;
    }

    private SimRunResult evaluateAdaptiveCandidateIfNeeded(int windowCount,
                                                           int tableCount,
                                                           int[] stepRef,
                                                           int budget,
                                                           OptimizeConfig config,
                                                           LossConfig lossConfig,
                                                           OptimizeResult optimizeResult,
                                                           ProgressListener progressListener,
                                                           CancellationChecker cancellationChecker,
                                                           Map<String, SimRunResult> evaluated,
                                                           int windowMin,
                                                           int windowMax,
                                                           int tableMin,
                                                           int tableMax) {
        int window = clamp(windowCount, windowMin, windowMax);
        int table = clamp(tableCount, tableMin, tableMax);
        String key = key(window, table);
        SimRunResult existing = evaluated.get(key);
        if (existing != null) {
            return existing;
        }
        if (stepRef[0] >= budget || isCancelled(cancellationChecker)) {
            return null;
        }

        stepRef[0]++;
        SimRunResult result = evaluateCandidate(
                window,
                table,
                stepRef[0],
                config,
                lossConfig,
                optimizeResult,
                progressListener,
                cancellationChecker
        );
        if (result == null) {
            return null;
        }
        applySearchRange(result, windowMin, windowMax, tableMin, tableMax);
        evaluated.put(key, result);
        if (config.verboseConsoleLog) {
            printStepLog(stepRef[0], budget, result);
        }
        return result;
    }

    private SimRunResult validateAdaptiveNeighborhood(SimRunResult center,
                                                      int[] stepRef,
                                                      int budget,
                                                      OptimizeConfig config,
                                                      LossConfig lossConfig,
                                                      OptimizeResult optimizeResult,
                                                      ProgressListener progressListener,
                                                      CancellationChecker cancellationChecker,
                                                      Map<String, SimRunResult> evaluated,
                                                      int windowMin,
                                                      int windowMax,
                                                      int tableMin,
                                                      int tableMax) {
        if (center == null) {
            return null;
        }
        int tableStep = Math.max(1, config.finalValidationTableStep);
        Map<String, int[]> unique = new LinkedHashMap<>();
        int[][] offsets = {
                {0, 0},
                {-1, 0},
                {1, 0},
                {0, -1},
                {0, 1},
                {-1, -1},
                {-1, 1},
                {1, -1},
                {1, 1}
        };
        for (int[] offset : offsets) {
            addAdaptiveCandidate(
                    unique,
                    center.windowCount + offset[0],
                    center.tableCount + offset[1] * tableStep,
                    windowMin,
                    windowMax,
                    tableMin,
                    tableMax
            );
        }
        List<int[]> candidates = new ArrayList<>(unique.values());
        evaluateAdaptiveCandidateList(candidates, stepRef, budget, config, lossConfig,
                optimizeResult, progressListener, cancellationChecker, evaluated,
                windowMin, windowMax, tableMin, tableMax);
        SimRunResult localBest = bestKnownCandidate(candidates, evaluated, config.optimizationMode);
        return localBest == null ? center : localBest;
    }

    private void addAdaptiveCandidate(Map<String, int[]> candidates,
                                      int window,
                                      int table,
                                      int windowMin,
                                      int windowMax,
                                      int tableMin,
                                      int tableMax) {
        int clampedWindow = clamp(window, windowMin, windowMax);
        int clampedTable = clamp(table, tableMin, tableMax);
        candidates.put(key(clampedWindow, clampedTable), new int[]{clampedWindow, clampedTable});
    }

    private void evaluateCandidateList(List<int[]> candidates,
                                       int[] stepRef,
                                       int budget,
                                       OptimizeConfig config,
                                       LossConfig lossConfig,
                                       OptimizeResult optimizeResult,
                                       ProgressListener progressListener,
                                       CancellationChecker cancellationChecker,
                                       Map<String, SimRunResult> evaluated) {
        for (int[] candidate : candidates) {
            if (stepRef[0] >= budget || isCancelled(cancellationChecker)) {
                return;
            }
            evaluateCandidateIfNeeded(
                    candidate[0],
                    candidate[1],
                    stepRef,
                    budget,
                    config,
                    lossConfig,
                    optimizeResult,
                    progressListener,
                    cancellationChecker,
                    evaluated
            );
        }
    }

    private SimRunResult evaluateCandidateIfNeeded(int windowCount,
                                                   int tableCount,
                                                   int[] stepRef,
                                                   int budget,
                                                   OptimizeConfig config,
                                                   LossConfig lossConfig,
                                                   OptimizeResult optimizeResult,
                                                   ProgressListener progressListener,
                                                   CancellationChecker cancellationChecker,
                                                   Map<String, SimRunResult> evaluated) {
        int window = clamp(windowCount, config.minWindowCount, config.maxWindowCount);
        int table = clamp(tableCount, config.minTableCount, config.maxTableCount);
        String key = key(window, table);
        SimRunResult existing = evaluated.get(key);
        if (existing != null) {
            return existing;
        }
        if (stepRef[0] >= budget || isCancelled(cancellationChecker)) {
            return null;
        }

        stepRef[0]++;
        SimRunResult result = evaluateCandidate(
                window,
                table,
                stepRef[0],
                config,
                lossConfig,
                optimizeResult,
                progressListener,
                cancellationChecker
        );
        if (result == null) {
            return null;
        }
        evaluated.put(key, result);
        if (config.verboseConsoleLog) {
            printStepLog(stepRef[0], budget, result);
        }
        return result;
    }

    private List<SimRunResult> sortedResultsByLoss(OptimizeResult optimizeResult) {
        List<SimRunResult> results = new ArrayList<>(optimizeResult.allResults);
        results.sort(Comparator.comparingDouble(r -> r.loss));
        return results;
    }

    private SimRunResult bestKnownCandidate(List<int[]> candidates, Map<String, SimRunResult> evaluated) {
        SimRunResult best = null;
        for (int[] candidate : candidates) {
            SimRunResult result = evaluated.get(key(candidate[0], candidate[1]));
            if (result != null && (best == null || result.loss < best.loss)) {
                best = result;
            }
        }
        return best;
    }

    private SimRunResult bestKnownCandidate(List<int[]> candidates,
                                            Map<String, SimRunResult> evaluated,
                                            OptimizationMode mode) {
        SimRunResult best = null;
        Comparator<SimRunResult> comparator = OptimizeResult.comparatorFor(mode);
        for (int[] candidate : candidates) {
            SimRunResult result = evaluated.get(key(candidate[0], candidate[1]));
            if (result != null && (best == null || comparator.compare(result, best) < 0)) {
                best = result;
            }
        }
        return best;
    }

    private boolean isMeaningfullyBetter(SimRunResult currentBest,
                                         SimRunResult candidate,
                                         OptimizeConfig config) {
        if (candidate == null) {
            return false;
        }
        if (currentBest == null) {
            return true;
        }
        double deltaLoss = currentBest.loss - candidate.loss;
        double ratio = deltaLoss / Math.max(1.0, Math.abs(currentBest.loss));
        return ratio > config.minImprovementRatio
                || (OptimizeResult.comparatorFor(config.optimizationMode).compare(candidate, currentBest) < 0
                && deltaLoss > config.minLossImprovement);
    }

    private List<int[]> buildGlobalProbeCandidates(OptimizeConfig config) {
        Map<String, int[]> candidates = new LinkedHashMap<>();
        int midWindow = midpoint(config.minWindowCount, config.maxWindowCount);
        int midTable = midpoint(config.minTableCount, config.maxTableCount);
        addCandidate(candidates, midWindow, midTable, config);
        addCandidate(candidates, config.minWindowCount, config.minTableCount, config);
        addCandidate(candidates, config.minWindowCount, config.maxTableCount, config);
        addCandidate(candidates, config.maxWindowCount, config.minTableCount, config);
        addCandidate(candidates, config.maxWindowCount, config.maxTableCount, config);
        addCandidate(candidates, midWindow, config.minTableCount, config);
        addCandidate(candidates, midWindow, config.maxTableCount, config);
        addCandidate(candidates, config.minWindowCount, midTable, config);
        addCandidate(candidates, config.maxWindowCount, midTable, config);

        int explorationIndex = 1;
        while (candidates.size() < config.globalProbeCount
                && candidates.size() < config.fullGridCandidateCount()) {
            addCandidate(
                    candidates,
                    explorationWindow(explorationIndex, config),
                    explorationTable(explorationIndex, config),
                    config
            );
            explorationIndex++;
        }
        return new ArrayList<>(candidates.values());
    }

    private void addCandidate(Map<String, int[]> candidates, int window, int table, OptimizeConfig config) {
        int clampedWindow = clamp(window, config.minWindowCount, config.maxWindowCount);
        int clampedTable = clamp(table, config.minTableCount, config.maxTableCount);
        candidates.put(key(clampedWindow, clampedTable), new int[]{clampedWindow, clampedTable});
    }

    private int explorationWindow(int index, OptimizeConfig config) {
        return spaceFillingValue(index, config.minWindowCount, config.maxWindowCount, 0.6180339887498949);
    }

    private int explorationTable(int index, OptimizeConfig config) {
        return spaceFillingValue(index, config.minTableCount, config.maxTableCount, 0.4142135623730950);
    }

    private int spaceFillingValue(int index, int min, int max, double stepRatio) {
        if (min == max) {
            return min;
        }
        double position = (index * stepRatio) % 1.0;
        return clamp(min + (int) Math.round(position * (max - min)), min, max);
    }

    private SimRunResult evaluateCandidate(int windowCount,
                                           int tableCount,
                                           int step,
                                           OptimizeConfig config,
                                           LossConfig lossConfig,
                                           OptimizeResult optimizeResult,
                                           ProgressListener progressListener,
                                           CancellationChecker cancellationChecker) {
        SimRunResult avgResult = runRepeated(windowCount, tableCount, step, config, cancellationChecker);
        if (avgResult == null) {
            return null;
        }

        double loss = lossEvaluator.evaluate(avgResult, lossConfig);
        avgResult.loss = loss;
        avgResult.step = step;

        if (optimizeResult.isBetter(avgResult, config.optimizationMode)) {
            optimizeResult.bestLoss = loss;
            optimizeResult.bestResult = avgResult.copyBasic();
        }

        avgResult.currentBestLoss = optimizeResult.bestLoss;
        optimizeResult.allResults.add(avgResult);
        if (progressListener != null) {
            progressListener.onStepFinished(step, optimizeResult.totalCandidateCount, avgResult, optimizeResult);
        }
        return avgResult;
    }

    private int midpoint(int min, int max) {
        return min + (max - min) / 2;
    }

    private int initialStep(int min, int max) {
        return Math.max(1, (int) Math.ceil((max - min) / 3.0));
    }

    private List<int[]> buildNeighborhood(int centerWindow, int centerTable,
                                          int windowStep, int tableStep,
                                          OptimizeConfig config) {
        int[][] offsets = {
                {0, 0},
                {-1, 0},
                {1, 0},
                {0, -1},
                {0, 1},
                {-1, -1},
                {-1, 1},
                {1, -1},
                {1, 1}
        };
        Map<String, int[]> unique = new LinkedHashMap<>();
        for (int[] offset : offsets) {
            int window = clamp(centerWindow + offset[0] * windowStep,
                    config.minWindowCount, config.maxWindowCount);
            int table = clamp(centerTable + offset[1] * tableStep,
                    config.minTableCount, config.maxTableCount);
            unique.put(window + ":" + table, new int[]{window, table});
        }
        return new ArrayList<>(unique.values());
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String key(int window, int table) {
        return window + ":" + table;
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
            long seed = deriveScenarioSeed(config.baseRandomSeed, i);
            SimRunResult r = adapter.runOnce(windowCount, tableCount, SimRunOptions.optimize(seed, config.totalPopulation));
            averager.add(r);
        }
        SimRunResult average = averager.average();
        average.randomSeed = deriveScenarioSeed(config.baseRandomSeed, config.repeatTimes);
        average.requestedPopulation = config.totalPopulation;
        average.avgMealPrice = config.avgMealPrice;
        average.windowCostPerHour = config.windowCostPerHour;
        average.tableCost = config.tableCost;
        average.lostStudentPenalty = config.lostStudentPenalty;
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
        long seed = deriveScenarioSeed(config.baseRandomSeed, config.repeatTimes);
        SimRunOptions options = SimRunOptions.replay(seed, config.replaySnapshotIntervalSeconds, config.totalPopulation);
        adapter.runOnce(best.windowCount, best.tableCount, options);
        return adapter.getLastReplayResult();
    }

    private long deriveScenarioSeed(long baseSeed, int repeatIndex) {
        long seed = baseSeed;
        seed ^= 0x9E3779B97F4A7C15L + repeatIndex * 1009L;
        seed ^= Long.rotateLeft(seed, 21);
        return seed;
    }

    private void applySearchRange(SimRunResult result, OptimizeConfig config) {
        result.minWindowCount = config.minWindowCount;
        result.maxWindowCount = config.maxWindowCount;
        result.minTableCount = config.minTableCount;
        result.maxTableCount = config.maxTableCount;
    }

    private void applySearchRange(SimRunResult result,
                                  int minWindowCount,
                                  int maxWindowCount,
                                  int minTableCount,
                                  int maxTableCount) {
        result.minWindowCount = minWindowCount;
        result.maxWindowCount = maxWindowCount;
        result.minTableCount = minTableCount;
        result.maxTableCount = maxTableCount;
    }

    private void printStepLog(int step, int total, SimRunResult r) {
        System.out.println(
                "[寻优] 步骤 " + step + "/" + total
                        + " | 窗口=" + r.windowCount
                        + " | 桌子=" + r.tableCount
                        + " | 等待=" + String.format("%.2f", r.avgWaitTimeMinutes) + "分钟"
                        + " | P95等待=" + String.format("%.2f", r.p95WaitTimeMinutes) + "分钟"
                        + " | 最大排队=" + r.maxQueueLength
                        + " | 损失值=" + String.format("%.4f", r.loss)
                        + " | SLA=" + (r.serviceLevelPassed ? "达标" : "未达标")
                        + " | 当前最佳=" + String.format("%.4f", r.currentBestLoss)
        );
    }
}
