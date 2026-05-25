package backend.optimize;

public class OptimizeMain {
    public static void main(String[] args) {
        OptimizeConfig optimizeConfig = new OptimizeConfig();
        applyArgs(args, optimizeConfig);

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
        for (String arg : args) {
            if (arg == null || !arg.startsWith("--") || !arg.contains("=")) {
                continue;
            }
            String[] parts = arg.substring(2).split("=", 2);
            String key = parts[0];
            String value = parts[1];
            if ("minWindow".equals(key)) config.minWindowCount = Integer.parseInt(value);
            if ("maxWindow".equals(key)) config.maxWindowCount = Integer.parseInt(value);
            if ("minTable".equals(key)) config.minTableCount = Integer.parseInt(value);
            if ("maxTable".equals(key)) config.maxTableCount = Integer.parseInt(value);
            if ("repeat".equals(key)) config.repeatTimes = Integer.parseInt(value);
            if ("seed".equals(key)) config.baseRandomSeed = Long.parseLong(value);
            if ("topK".equals(key)) config.topK = Integer.parseInt(value);
            if ("exportCsv".equals(key)) config.exportCsv = Boolean.parseBoolean(value);
            if ("replay".equals(key)) config.runReplayAfterOptimization = Boolean.parseBoolean(value);
        }
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
        System.out.println("最大排队人数：" + best.maxQueueLength);
        System.out.println("平均排队长度：" + String.format("%.2f", best.avgQueueLength));
        System.out.println("座位利用率：" + String.format("%.2f", best.seatUtilization));
        System.out.println("窗口利用率：" + String.format("%.2f", best.windowUtilization));
        System.out.println("完成率：" + String.format("%.2f", best.finishRate));
        System.out.println("放弃率：" + String.format("%.2f", best.abandonRate));
        System.out.println("综合 Loss：" + String.format("%.4f", best.loss));

        System.out.println();
        System.out.println("Top 候选方案：");
        int rank = 1;
        for (SimRunResult r : result.topKResults) {
            System.out.println("#" + rank++
                    + " window=" + r.windowCount
                    + ", table=" + r.tableCount
                    + ", wait=" + String.format("%.2f", r.avgWaitTimeMinutes)
                    + ", maxQ=" + r.maxQueueLength
                    + ", seatUse=" + String.format("%.2f", r.seatUtilization)
                    + ", loss=" + String.format("%.4f", r.loss));
        }

        System.out.println();
        System.out.println("总候选配置数：" + result.totalCandidateCount);
        System.out.println("总仿真次数：" + result.totalSimulationCount);
        System.out.println("总运行耗时：" + result.totalRuntimeMs + " ms");
        if (result.replayResult != null) {
            System.out.println("最优方案回放快照数量：" + result.replayResult.snapshots.size());
        }
    }
}
