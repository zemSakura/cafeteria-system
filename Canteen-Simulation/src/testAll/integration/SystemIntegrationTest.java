package testAll.integration;

import backend.config.CanteenConfig;
import backend.model.MealPeriod;
import backend.model.SimulationMode;
import backend.optimize.SimRunOptions;
import backend.optimize.SimRunResult;
import backend.optimize.SimulationAdapter;

import java.util.List;

/**
 * 系统联调测试：以具体任务用例形式测试系统整体能力。
 * 覆盖正常、低负载、高峰、座位不足、窗口不足、边界、全天等场景。
 */
public class SystemIntegrationTest {

    private static int passed = 0;
    private static int failed = 0;
    private static final StringBuilder resultLog = new StringBuilder();

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  食堂仿真系统 - 系统联调测试");
        System.out.println("========================================\n");

        CanteenConfig.resetToDefaults();

        testNormalLunchPeak();       // ST-01
        testLowLoadIdle();           // ST-02
        testPeakCongestion();        // ST-03
        testSeatShortage();          // ST-04
        testWindowShortage();        // ST-05
        testBoundaryConditions();    // ST-06
        testFullDaySimulation();     // ST-07

        System.out.println("\n========================================");
        System.out.println("  系统联调测试汇总");
        System.out.println("========================================");
        System.out.println("  通过: " + passed + " / 失败: " + failed);
        System.out.println("  总计: " + (passed + failed));
        System.out.println("========================================");

        System.out.println("\n" + resultLog.toString());
    }

    // ==================== ST-01 ====================

    private static void testNormalLunchPeak() {
        String testId = "ST-01";
        String testName = "正常午餐高峰仿真测试";

        logTestHeader(testId, testName);
        log("测试场景: 模拟中午就餐高峰，中等人数、多窗口、座位正常");
        log("输入配置:");
        log("  学生人数: 200");
        log("  窗口数: 5");
        log("  桌子数: 80 (320座)");
        log("  开放时间: 120分钟");
        log("  餐段: 午餐");
        log("  模式: 单时段");

        boolean allPassed = true;
        StringBuilder details = new StringBuilder();

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 200;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(80);
        CanteenConfig.OPEN_DURATION = 120;
        CanteenConfig.SIMULATION_MODE = SimulationMode.SINGLE_PERIOD;
        CanteenConfig.MEAL_PERIOD = MealPeriod.LUNCH;

        SimulationAdapter adapter = new SimulationAdapter();
        SimRunResult r = adapter.runOnce(5, 80,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED, 200));

        if (r == null) {
            log("  错误: 仿真返回 null");
            logResult(testId, testName, false);
            return;
        }

        details.append(String.format("  实际输出:\n"));
        details.append(String.format("    到达人数: %d\n", r.arrivedStudents));
        details.append(String.format("    服务人数: %d\n", r.servedStudents));
        details.append(String.format("    完成人数: %d\n", r.finishedStudents));
        details.append(String.format("    放弃人数: %d\n", r.abandonedStudents));
        details.append(String.format("    平均等待时间: %.1f 秒 (%.1f 分钟)\n", r.avgWaitTimeSeconds, r.avgWaitTimeMinutes));
        details.append(String.format("    最大队列长度: %d\n", r.maxQueueLength));
        details.append(String.format("    平均队列长度: %.2f\n", r.avgQueueLength));
        details.append(String.format("    座位利用率: %.3f\n", r.seatUtilization));
        details.append(String.format("    窗口利用率: %.3f\n", r.windowUtilization));
        details.append(String.format("    完成率: %.3f\n", r.finishRate));
        details.append(String.format("    放弃率: %.3f\n", r.abandonRate));
        details.append(String.format("    运行耗时: %d ms\n", r.runtimeMs));

        // 验证
        boolean c1 = r.arrivedStudents > 0;
        details.append("  检查1 (有学生到达): ").append(c1 ? "通过" : "失败").append("\n");
        if (!c1) allPassed = false;

        boolean c2 = r.finishRate > 0;
        details.append("  检查2 (完成率 > 0): ").append(c2 ? "通过" : "失败")
                .append(" - ").append(String.format("%.3f", r.finishRate)).append("\n");
        if (!c2) allPassed = false;

        boolean c3 = r.avgWaitTimeSeconds >= 0;
        details.append("  检查3 (等待时间非负): ").append(c3 ? "通过" : "失败").append("\n");
        if (!c3) allPassed = false;

        boolean c4 = r.maxQueueLength >= 0;
        details.append("  检查4 (队列长度非负): ").append(c4 ? "通过" : "失败").append("\n");
        if (!c4) allPassed = false;

        boolean c5 = r.seatUtilization >= 0 && r.seatUtilization <= 1.0;
        details.append("  检查5 (座位利用率在[0,1]): ").append(c5 ? "通过" : "失败")
                .append(" - ").append(String.format("%.3f", r.seatUtilization)).append("\n");
        if (!c5) allPassed = false;

        boolean c6 = r.runtimeMs > 0;
        details.append("  检查6 (运行耗时 > 0): ").append(c6 ? "通过" : "失败").append("\n");
        if (!c6) allPassed = false;

        log(details.toString());
        logResult(testId, testName, allPassed);
        CanteenConfig.resetToDefaults();
    }

    // ==================== ST-02 ====================

    private static void testLowLoadIdle() {
        String testId = "ST-02";
        String testName = "低人数空闲场景测试";

        logTestHeader(testId, testName);
        log("测试场景: 很少学生，座位和窗口充足");
        log("输入配置:");
        log("  学生人数: 10");
        log("  窗口数: 5");
        log("  桌子数: 50");
        log("  开放时间: 120分钟");

        boolean allPassed = true;
        StringBuilder details = new StringBuilder();

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 10;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(50);
        CanteenConfig.OPEN_DURATION = 120;

        SimulationAdapter adapter = new SimulationAdapter();
        SimRunResult r = adapter.runOnce(5, 50,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED, 10));

        if (r == null) { logResult(testId, testName, false); return; }

        details.append(String.format("  实际输出:\n"));
        details.append(String.format("    平均等待: %.1f 秒\n", r.avgWaitTimeSeconds));
        details.append(String.format("    最大队列: %d\n", r.maxQueueLength));
        details.append(String.format("    放弃人数: %d\n", r.abandonedStudents));
        details.append(String.format("    完成率: %.3f\n", r.finishRate));
        details.append(String.format("    座位利用率: %.3f\n", r.seatUtilization));

        boolean c1 = r.abandonedStudents == 0 || r.abandonRate < 0.1;
        details.append("  检查1 (放弃率低): ").append(c1 ? "通过" : "失败")
                .append(" - 放弃 ").append(r.abandonedStudents).append(" 人\n");
        if (!c1) allPassed = false;

        boolean c2 = r.finishRate > 0.5;
        details.append("  检查2 (完成率高): ").append(c2 ? "通过" : "失败")
                .append(" - ").append(String.format("%.3f", r.finishRate)).append("\n");
        if (!c2) allPassed = false;

        boolean c3 = r.seatUtilization <= 0.5;
        details.append("  检查3 (座位利用率低): ").append(c3 ? "通过" : "失败")
                .append(" - ").append(String.format("%.3f", r.seatUtilization)).append("\n");
        if (!c3) allPassed = false;

        // 低负载下平均等待时间应该较短
        boolean c4 = r.avgWaitTimeMinutes < 5;
        details.append("  检查4 (等待时间短): ").append(c4 ? "通过" : "失败")
                .append(" - ").append(String.format("%.1f 分钟", r.avgWaitTimeMinutes)).append("\n");
        if (!c4) allPassed = false;

        log(details.toString());
        logResult(testId, testName, allPassed);
        CanteenConfig.resetToDefaults();
    }

    // ==================== ST-03 ====================

    private static void testPeakCongestion() {
        String testId = "ST-03";
        String testName = "高峰拥堵场景测试";

        logTestHeader(testId, testName);
        log("测试场景: 大量学生，窗口较少，预期出现明显排队");
        log("输入配置:");
        log("  学生人数: 500");
        log("  窗口数: 3");
        log("  桌子数: 80");
        log("  开放时间: 120分钟");

        boolean allPassed = true;
        StringBuilder details = new StringBuilder();

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 500;
        CanteenConfig.setWindowCount(3);
        CanteenConfig.setTableCount(80);
        CanteenConfig.OPEN_DURATION = 120;

        SimulationAdapter adapter = new SimulationAdapter();
        SimRunResult r = adapter.runOnce(3, 80,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED, 500));

        if (r == null) { logResult(testId, testName, false); return; }

        details.append(String.format("  实际输出:\n"));
        details.append(String.format("    到达: %d, 服务: %d, 完成: %d, 放弃: %d\n",
                r.arrivedStudents, r.servedStudents, r.finishedStudents, r.abandonedStudents));
        details.append(String.format("    平均等待: %.1f 秒 (%.1f 分钟)\n", r.avgWaitTimeSeconds, r.avgWaitTimeMinutes));
        details.append(String.format("    最大队列长度: %d\n", r.maxQueueLength));
        details.append(String.format("    平均队列长度: %.2f\n", r.avgQueueLength));
        details.append(String.format("    窗口利用率: %.3f\n", r.windowUtilization));
        details.append(String.format("    完成率: %.3f\n", r.finishRate));
        details.append(String.format("    放弃率: %.3f\n", r.abandonRate));

        // 高峰场景应有明显排队
        boolean c1 = r.maxQueueLength > 0;
        details.append("  检查1 (有排队产生): ").append(c1 ? "通过" : "失败")
                .append(" - 最大队列 ").append(r.maxQueueLength).append("\n");
        if (!c1) allPassed = false;

        // 窗口利用率应较高
        boolean c2 = r.windowUtilization > 0.3;
        details.append("  检查2 (窗口利用率较高): ").append(c2 ? "通过" : "失败")
                .append(" - ").append(String.format("%.3f", r.windowUtilization)).append("\n");
        if (!c2) allPassed = false;

        // 系统不崩溃
        boolean c3 = r.runtimeMs > 0;
        details.append("  检查3 (系统不崩溃): ").append(c3 ? "通过" : "失败").append("\n");
        if (!c3) allPassed = false;

        log(details.toString());
        logResult(testId, testName, allPassed);
        CanteenConfig.resetToDefaults();
    }

    // ==================== ST-04 ====================

    private static void testSeatShortage() {
        String testId = "ST-04";
        String testName = "座位不足场景测试";

        logTestHeader(testId, testName);
        log("测试场景: 学生较多但座位极少");
        log("输入配置:");
        log("  学生人数: 100");
        log("  窗口数: 5");
        log("  桌子数: 5 (仅20座)");
        log("  开放时间: 180分钟");
        log("  耐心: 10-20分钟");

        boolean allPassed = true;
        StringBuilder details = new StringBuilder();

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 100;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(5);
        CanteenConfig.OPEN_DURATION = 180;
        CanteenConfig.PATIENCE_MIN = 10 * 60;
        CanteenConfig.PATIENCE_MAX = 20 * 60;

        SimulationAdapter adapter = new SimulationAdapter();
        SimRunResult r = adapter.runOnce(5, 5,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED, 100));

        if (r == null) { logResult(testId, testName, false); return; }

        details.append(String.format("  实际输出:\n"));
        details.append(String.format("    完成: %d, 放弃: %d\n", r.finishedStudents, r.abandonedStudents));
        details.append(String.format("    平均等待: %.1f 分钟\n", r.avgWaitTimeMinutes));
        details.append(String.format("    最大队列: %d\n", r.maxQueueLength));
        details.append(String.format("    座位利用率: %.3f\n", r.seatUtilization));
        details.append(String.format("    完成率: %.3f\n", r.finishRate));
        details.append(String.format("    放弃率: %.3f\n", r.abandonRate));

        // 座位不足时利用率应很高
        boolean c1 = r.seatUtilization > 0.7;
        details.append("  检查1 (座位利用率高): ").append(c1 ? "通过" : "失败")
                .append(" - ").append(String.format("%.3f", r.seatUtilization)).append("\n");
        if (!c1) allPassed = false;

        // 可能有学生放弃
        boolean c2 = r.abandonRate >= 0 && r.abandonRate <= 1.0;
        details.append("  检查2 (放弃率合理): ").append(c2 ? "通过" : "失败")
                .append(" - ").append(String.format("%.3f", r.abandonRate)).append("\n");
        if (!c2) allPassed = false;

        // 系统应正常完成
        boolean c3 = r.runtimeMs > 0;
        details.append("  检查3 (系统正常完成): ").append(c3 ? "通过" : "失败").append("\n");
        if (!c3) allPassed = false;

        log(details.toString());
        logResult(testId, testName, allPassed);
        CanteenConfig.resetToDefaults();
    }

    // ==================== ST-05 ====================

    private static void testWindowShortage() {
        String testId = "ST-05";
        String testName = "窗口不足场景测试";

        logTestHeader(testId, testName);
        log("测试场景: 仅有1个窗口，正常人数");
        log("输入配置:");
        log("  学生人数: 60");
        log("  窗口数: 1");
        log("  桌子数: 40");
        log("  开放时间: 120分钟");

        boolean allPassed = true;
        StringBuilder details = new StringBuilder();

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 60;
        CanteenConfig.setWindowCount(1);
        CanteenConfig.setTableCount(40);
        CanteenConfig.OPEN_DURATION = 120;

        SimulationAdapter adapter = new SimulationAdapter();
        SimRunResult r = adapter.runOnce(1, 40,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED, 60));

        if (r == null) { logResult(testId, testName, false); return; }

        details.append(String.format("  实际输出:\n"));
        details.append(String.format("    到达: %d, 服务: %d, 完成: %d, 放弃: %d\n",
                r.arrivedStudents, r.servedStudents, r.finishedStudents, r.abandonedStudents));
        details.append(String.format("    平均等待: %.1f 分钟\n", r.avgWaitTimeMinutes));
        details.append(String.format("    最大队列: %d\n", r.maxQueueLength));
        details.append(String.format("    窗口利用率: %.3f\n", r.windowUtilization));
        details.append(String.format("    完成率: %.3f\n", r.finishRate));

        // 单窗口利用率应较高
        boolean c1 = r.windowUtilization > 0.5;
        details.append("  检查1 (窗口利用率高): ").append(c1 ? "通过" : "失败")
                .append(" - ").append(String.format("%.3f", r.windowUtilization)).append("\n");
        if (!c1) allPassed = false;

        // 有排队产生
        boolean c2 = r.maxQueueLength > 0;
        details.append("  检查2 (产生排队): ").append(c2 ? "通过" : "失败")
                .append(" - 最大队列 ").append(r.maxQueueLength).append("\n");
        if (!c2) allPassed = false;

        boolean c3 = r.runtimeMs > 0;
        details.append("  检查3 (正常运行): ").append(c3 ? "通过" : "失败").append("\n");
        if (!c3) allPassed = false;

        log(details.toString());
        logResult(testId, testName, allPassed);
        CanteenConfig.resetToDefaults();
    }

    // ==================== ST-06 ====================

    private static void testBoundaryConditions() {
        String testId = "ST-06";
        String testName = "极端边界场景测试";

        logTestHeader(testId, testName);
        log("测试场景: 最小人数、最小窗口/桌子、极短开放时间、极端服务时间");
        log("输入配置 (多个子测试):");

        boolean allPassed = true;
        StringBuilder details = new StringBuilder();
        SimulationAdapter adapter = new SimulationAdapter();

        // 子测试1: 人数为1
        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 1;
        CanteenConfig.setWindowCount(1);
        CanteenConfig.setTableCount(1);
        CanteenConfig.OPEN_DURATION = 10;
        try {
            SimRunResult r = adapter.runOnce(1, 1,
                    SimRunOptions.optimize(CanteenConfig.RANDOM_SEED, 1));
            boolean sub1 = r != null && r.totalStudents >= 1;
            details.append("  子测试1 (1人1窗1桌): ").append(sub1 ? "通过" : "失败")
                    .append(r != null ? " - 完成率 " + String.format("%.3f", r.finishRate) : "").append("\n");
            if (!sub1) allPassed = false;
        } catch (Exception e) {
            details.append("  子测试1 (1人1窗1桌): 失败 - 异常: ").append(e.getMessage()).append("\n");
            allPassed = false;
        }

        // 子测试2: 极短开放时间
        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 30;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(20);
        CanteenConfig.OPEN_DURATION = 1; // 仅1分钟
        try {
            SimRunResult r = adapter.runOnce(5, 20,
                    SimRunOptions.optimize(CanteenConfig.RANDOM_SEED, 30));
            boolean sub2 = r != null;
            details.append("  子测试2 (开放时间=1分钟): ").append(sub2 ? "通过" : "失败")
                    .append(r != null ? " - 完成率 " + String.format("%.3f", r.finishRate) : "").append("\n");
            if (!sub2) allPassed = false;
        } catch (Exception e) {
            details.append("  子测试2 (开放时间=1分钟): 失败 - 异常: ").append(e.getMessage()).append("\n");
            allPassed = false;
        }

        // 子测试3: 极短服务时间
        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 50;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(30);
        CanteenConfig.OPEN_DURATION = 120;
        int[] fastDist = {10, 15, 20, 25, 30};
        int[] fastSrv = {1, 1, 1, 1, 1};
        CanteenConfig.updateWindowConfigs(fastDist, fastSrv);
        try {
            SimRunResult r = adapter.runOnce(5, 30,
                    SimRunOptions.optimize(CanteenConfig.RANDOM_SEED, 50));
            boolean sub3 = r != null && r.avgWaitTimeSeconds >= 0;
            details.append("  子测试3 (极短服务时间=1秒): ").append(sub3 ? "通过" : "失败")
                    .append(r != null ? " - 平均等待 " + String.format("%.1f", r.avgWaitTimeSeconds) + "秒" : "").append("\n");
            if (!sub3) allPassed = false;
        } catch (Exception e) {
            details.append("  子测试3 (极短服务时间=1秒): 失败 - 异常: ").append(e.getMessage()).append("\n");
            allPassed = false;
        }

        // 子测试4: 极长服务时间
        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 30;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(30);
        CanteenConfig.OPEN_DURATION = 300; // 5小时
        int[] slowSrv = {300, 300, 300, 300, 300};
        CanteenConfig.updateWindowConfigs(fastDist, slowSrv);
        try {
            SimRunResult r = adapter.runOnce(5, 30,
                    SimRunOptions.optimize(CanteenConfig.RANDOM_SEED, 30));
            boolean sub4 = r != null;
            details.append("  子测试4 (极长服务时间=300秒): ").append(sub4 ? "通过" : "失败")
                    .append(r != null ? " - 完成率 " + String.format("%.3f", r.finishRate) : "").append("\n");
            if (!sub4) allPassed = false;
        } catch (Exception e) {
            details.append("  子测试4 (极长服务时间=300秒): 失败 - 异常: ").append(e.getMessage()).append("\n");
            allPassed = false;
        }

        // 子测试5: 窗口数为0应被拒绝
        CanteenConfig.resetToDefaults();
        try {
            CanteenConfig.setWindowCount(0);
            details.append("  子测试5 (窗口数=0应被拒绝): 失败 - 未抛出异常\n");
            allPassed = false;
        } catch (IllegalArgumentException e) {
            details.append("  子测试5 (窗口数=0应被拒绝): 通过 - 正确抛出异常\n");
        }

        // 子测试6: 桌子数为0应被拒绝
        CanteenConfig.resetToDefaults();
        try {
            CanteenConfig.setTableCount(0);
            details.append("  子测试6 (桌子数=0应被拒绝): 失败 - 未抛出异常\n");
            allPassed = false;
        } catch (IllegalArgumentException e) {
            details.append("  子测试6 (桌子数=0应被拒绝): 通过 - 正确抛出异常\n");
        }

        log(details.toString());
        logResult(testId, testName, allPassed);
        CanteenConfig.resetToDefaults();
    }

    // ==================== ST-07 ====================

    private static void testFullDaySimulation() {
        String testId = "ST-07";
        String testName = "全天仿真场景测试";

        logTestHeader(testId, testName);
        log("测试场景: 全天模式，包含早中晚三餐");
        log("输入配置:");
        log("  学生人数: 100 (每餐段)");
        log("  窗口数: 5");
        log("  桌子数: 60");
        log("  开放时间: 90分钟 (每餐段)");
        log("  模式: 全天");

        boolean allPassed = true;
        StringBuilder details = new StringBuilder();

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 100;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(60);
        CanteenConfig.OPEN_DURATION = 90;
        CanteenConfig.SIMULATION_MODE = SimulationMode.FULL_DAY;
        CanteenConfig.MEAL_PERIOD = MealPeriod.LUNCH;

        // 使用 ArrivalModule 直接测试全天生成
        backend.module.ArrivalModule arrivalModule = new backend.module.ArrivalModule(CanteenConfig.RANDOM_SEED);
        backend.model.ArrivalGenerationResult fullDayResult = arrivalModule.generateArrivalPlan(
                100, SimulationMode.FULL_DAY, MealPeriod.LUNCH, true);

        // 检查三餐统计数据
        java.util.Map<MealPeriod, backend.model.MealArrivalStats> mealStats = fullDayResult.getMealStats();
        boolean c1 = mealStats.size() == 3;
        details.append("  检查1 (三餐统计): ").append(c1 ? "通过" : "失败")
                .append(" - 包含 ").append(mealStats.size()).append(" 个餐段: ").append(mealStats.keySet()).append("\n");
        if (!c1) allPassed = false;

        // 检查阶段边界
        java.util.List<backend.model.ArrivalGenerationResult.PhaseBoundary> boundaries = fullDayResult.getPhaseBoundaries();
        boolean c2 = boundaries.size() == 5; // 早餐+关闭+午餐+关闭+晚餐
        details.append("  检查2 (阶段边界=5): ").append(c2 ? "通过" : "失败")
                .append(" - 实际 ").append(boundaries.size()).append(" 个\n");
        for (backend.model.ArrivalGenerationResult.PhaseBoundary b : boundaries) {
            details.append("    ").append(b.name).append(": tick[").append(b.startTick)
                    .append(", ").append(b.endTick).append("] ").append(b.label).append("\n");
        }
        if (!c2) allPassed = false;

        // 检查总学生数 (全天模式三餐各100人 = 300人)
        int totalStudents = fullDayResult.getStudents().size();
        boolean c3 = totalStudents == 300;
        details.append("  检查3 (总学生数=300): ").append(c3 ? "通过" : "失败")
                .append(" - 实际 ").append(totalStudents).append(" 人\n");
        if (!c3) allPassed = false;

        // 运行全天仿真
        SimulationAdapter adapter = new SimulationAdapter();
        SimRunResult r = adapter.runOnce(5, 60,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED, 100));

        boolean c4 = r != null && r.runtimeMs > 0;
        details.append("  检查4 (全天仿真运行成功): ").append(c4 ? "通过" : "失败");
        if (r != null) {
            details.append(" - 到达 ").append(r.arrivedStudents)
                    .append(", 完成 ").append(r.finishedStudents)
                    .append(", 耗时 ").append(r.runtimeMs).append("ms");
        }
        details.append("\n");
        if (!c4) allPassed = false;

        // 检查事件有序
        List<backend.model.SimulationEvent> events = fullDayResult.getArrivalEvents();
        boolean c5 = true;
        for (int i = 1; i < events.size(); i++) {
            if (events.get(i).getEventTime() < events.get(i - 1).getEventTime()) {
                c5 = false;
                break;
            }
        }
        details.append("  检查5 (全天事件有序): ").append(c5 ? "通过" : "失败").append("\n");
        if (!c5) allPassed = false;

        log(details.toString());
        logResult(testId, testName, allPassed);
        CanteenConfig.resetToDefaults();
    }

    // ==================== 辅助方法 ====================

    private static void logTestHeader(String id, String name) {
        String header = "\n--- " + id + " " + name + " ---";
        System.out.println(header);
        resultLog.append(header).append("\n");
    }

    private static void log(String msg) {
        System.out.println(msg);
        resultLog.append(msg).append("\n");
    }

    private static void logResult(String id, String name, boolean passedFlag) {
        String status = passedFlag ? "通过" : "不通过";
        String line = ">>> " + id + " " + name + ": " + status;
        System.out.println(line);
        resultLog.append(line).append("\n");
        if (passedFlag) {
            passed++;
        } else {
            failed++;
        }
    }
}
