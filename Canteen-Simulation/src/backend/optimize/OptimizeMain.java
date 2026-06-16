package backend.optimize;

import backend.dto.OptimizationMode;

public class OptimizeMain {
    public static void main(String[] args) {
        OptimizeConfig optimizeConfig = new OptimizeConfig();
        applyArgs(args, optimizeConfig);
        optimizeConfig.validate();

        LossConfig lossConfig = new LossConfig();
        GridSearchOptimizer optimizer = new GridSearchOptimizer();

        System.out.println("============================================");
        System.out.println("食堂仿真系统 - 后端自动寻优启动");
        System.out.println("候选参数数量：" + optimizeConfig.totalCandidateCount());
        System.out.println("总仿真次数：" + optimizeConfig.totalSimulationCount());
        System.out.println("============================================");

        OptimizeResult result = optimizer.run(optimizeConfig, lossConfig);
        printFinalResult(result);

        if (optimizeConfig.exportCsv) {
            new OptimizationCsvExporter().exportAll(result, "optimization-output");
            System.out.println("优化结果已导出到 optimization-output/ 目录");
        }
    }

    private static void applyArgs(String[] args, OptimizeConfig config) {
        if (args == null) {
            return;
        }
        boolean minWindowSet = false;
        boolean maxWindowSet = false;
        boolean minTableSet = false;
        boolean maxTableSet = false;
        for (String arg : args) {
            if (arg == null || !arg.startsWith("--") || !arg.contains("=")) {
                continue;
            }
            String[] parts = arg.substring(2).split("=", 2);
            String key = parts[0];
            String value = parts[1];
            if ("totalPopulation".equals(key) || "students".equals(key)) config.totalPopulation = Integer.parseInt(value);
            if ("minWindow".equals(key)) {
                config.minWindowCount = Integer.parseInt(value);
                minWindowSet = true;
            }
            if ("maxWindow".equals(key)) {
                config.maxWindowCount = Integer.parseInt(value);
                maxWindowSet = true;
            }
            if ("minTable".equals(key)) {
                config.minTableCount = Integer.parseInt(value);
                minTableSet = true;
            }
            if ("maxTable".equals(key)) {
                config.maxTableCount = Integer.parseInt(value);
                maxTableSet = true;
            }
            if ("currentWindow".equals(key)) config.currentWindowCount = Integer.parseInt(value);
            if ("currentTable".equals(key)) config.currentTableCount = Integer.parseInt(value);
            if ("repeat".equals(key)) config.repeatTimes = Integer.parseInt(value);
            if ("seed".equals(key)) config.baseRandomSeed = parseSeedOrDefault(value, config.baseRandomSeed);
            if ("topK".equals(key)) config.topK = Integer.parseInt(value);
            if ("onlyShowServiceLevelPassed".equals(key) || "onlyPassed".equals(key)) {
                config.onlyShowServiceLevelPassed = Boolean.parseBoolean(value);
            }
            if ("adaptive".equals(key)) config.useAdaptiveBoundarySearch = Boolean.parseBoolean(value);
            if ("convergent".equals(key)) config.useConvergentSearch = Boolean.parseBoolean(value);
            if ("maxIterations".equals(key) || "maxCandidates".equals(key)) config.maxCandidateEvaluations = Integer.parseInt(value);
            if ("patience".equals(key)) config.stagnationPatience = Integer.parseInt(value);
            if ("globalProbes".equals(key)) config.globalProbeCount = Integer.parseInt(value);
            if ("restarts".equals(key)) config.localRestartCount = Integer.parseInt(value);
            if ("exportCsv".equals(key)) config.exportCsv = Boolean.parseBoolean(value);
            if ("replay".equals(key)) config.runReplayAfterOptimization = Boolean.parseBoolean(value);
            if ("avgMealPrice".equals(key)) config.avgMealPrice = Double.parseDouble(value);
            if ("windowCostPerHour".equals(key)) config.windowCostPerHour = Double.parseDouble(value);
            if ("tableCost".equals(key)) config.tableCost = Double.parseDouble(value);
            if ("lostStudentPenalty".equals(key)) config.lostStudentPenalty = Double.parseDouble(value);
            if ("mode".equals(key)) config.optimizationMode = OptimizationMode.fromDisplayName(value);
        }
        applyAbesDefaults(config, minWindowSet, maxWindowSet, minTableSet, maxTableSet);
    }

    private static long parseSeedOrDefault(String value, long defaultSeed) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            System.err.println("随机种子必须为整数，已自动使用系统默认种子。");
            return defaultSeed;
        }
    }

    private static void applyAbesDefaults(OptimizeConfig config,
                                          boolean minWindowSet,
                                          boolean maxWindowSet,
                                          boolean minTableSet,
                                          boolean maxTableSet) {
        if (!minWindowSet) {
            config.minWindowCount = 1;
        }
        if (!maxWindowSet) {
            config.maxWindowCount = 4;
        }
        if (!minTableSet) {
            config.minTableCount = OptimizeConfig.defaultMinTableCount(config.totalPopulation);
        }
        if (!maxTableSet) {
            config.maxTableCount = OptimizeConfig.defaultMaxTableCount(config.totalPopulation);
        }
        config.maxWindowLimit = maxWindowSet ? config.maxWindowCount : Math.max(20, config.maxWindowCount);
        config.maxTableLimit = maxTableSet ? config.maxTableCount : OptimizeConfig.defaultTableLimit(config.totalPopulation);
    }

    private static void printFinalResult(OptimizeResult result) {
        System.out.println();
        System.out.println("============================================");
        System.out.println("搜索完成：最优推荐参数");
        System.out.println("============================================");

        SimRunResult best = result.bestResult;
        if (best == null) {
            System.out.println("未找到可用最优解");
            return;
        }

        System.out.println("推荐窗口数：" + best.windowCount);
        System.out.println("推荐桌子数：" + best.tableCount);
        System.out.println("平均等待时间：" + String.format("%.2f", best.avgWaitTimeMinutes) + " 分钟");
        System.out.println("平均等座时间：" + String.format("%.2f", best.avgSeatWaitTimeMinutes) + " 分钟");
        System.out.println("最大排队人数：" + best.maxQueueLength);
        System.out.println("平均排队长度：" + String.format("%.2f", best.avgQueueLength));
        System.out.println("座位利用率：" + String.format("%.2f", best.seatUtilization));
        System.out.println("窗口利用率：" + String.format("%.2f", best.windowUtilization));
        System.out.println("完成率：" + String.format("%.2f", best.finishRate));
        System.out.println("放弃率：" + String.format("%.2f", best.abandonRate));
        System.out.println("预计净收益：" + String.format("%.2f", best.netProfit) + " 元");
        System.out.println("综合评分：" + String.format("%.4f", best.score));
        System.out.println("综合损失：" + String.format("%.4f", best.loss));

        if (result.currentResult != null) {
            SimRunResult current = result.currentResult;
            System.out.println();
            System.out.println("当前方案对比："
                    + current.windowCount + " 窗/" + current.tableCount + " 桌"
                    + " -> " + best.windowCount + " 窗/" + best.tableCount + " 桌"
                    + " | 完成率 " + String.format("%+.2f%%", (best.finishRate - current.finishRate) * 100.0)
                    + " | 净收益 " + String.format("%+.2f", best.netProfit - current.netProfit) + " 元"
                    + " | 放弃人数 " + (best.abandonedStudents - current.abandonedStudents));
        }

        System.out.println();
        System.out.println("最优候选方案：");
        int rank = 1;
        for (SimRunResult r : result.topKResults) {
            System.out.println("第 " + rank++ + " 名"
                    + "，窗口=" + r.windowCount
                    + "，桌子=" + r.tableCount
                    + "，等待=" + String.format("%.2f", r.avgWaitTimeMinutes)
                    + "，等座=" + String.format("%.2f", r.avgSeatWaitTimeMinutes)
                    + "，完成率=" + String.format("%.2f", r.finishRate)
                    + "，净收益=" + String.format("%.2f", r.netProfit)
                    + "，评分=" + String.format("%.4f", r.score));
        }

        System.out.println();
        System.out.println("总候选配置数：" + result.totalCandidateCount);
        System.out.println("总仿真次数：" + result.totalSimulationCount);
        System.out.println("总运行耗时：" + result.totalRuntimeMs + " 毫秒");
        if (result.stopReason != null && !result.stopReason.isEmpty()) {
            System.out.println("停止原因：" + result.stopReason);
            System.out.println("搜索轮次：" + result.searchRounds);
        }
        if (result.rangeHistory != null && !result.rangeHistory.isEmpty()) {
            System.out.println("搜索范围历史：");
            for (String item : result.rangeHistory) {
                System.out.println(" - " + item);
            }
        }
        if (result.replayResult != null) {
            System.out.println("最优方案回放快照数量：" + result.replayResult.snapshots.size());
        }
    }
}
