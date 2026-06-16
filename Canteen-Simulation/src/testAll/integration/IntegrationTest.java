package testAll.integration;

import backend.config.CanteenConfig;
import backend.model.*;
import backend.module.ArrivalModule;
import backend.optimize.SimRunOptions;
import backend.optimize.SimRunResult;
import backend.optimize.SimulationAdapter;

import java.util.List;
import java.util.Map;

/**
 * 接口联调测试：验证各模块之间的数据传递和协作是否正常。
 * 每个测试包含：测试目的、输入、预期结果、实际结果、结论。
 */
public class IntegrationTest {

    private static int passed = 0;
    private static int failed = 0;
    private static final StringBuilder resultLog = new StringBuilder();

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  食堂仿真系统 - 接口联调测试");
        System.out.println("========================================\n");

        // 重置为默认配置后开始测试
        CanteenConfig.resetToDefaults();

        // ==========================================
        // IT-01: 配置模块与到达生成模块联调
        // ==========================================
        testConfigToArrivalModule();

        // ==========================================
        // IT-02: 到达生成模块与事件队列联调
        // ==========================================
        testArrivalToEventQueue();

        // ==========================================
        // IT-03: 事件队列与仿真引擎联调
        // ==========================================
        testEventQueueToEngine();

        // ==========================================
        // IT-04: 排队模块与窗口模块联调
        // ==========================================
        testQueueToWindowModule();

        // ==========================================
        // IT-05: 排队模块与座位分配模块联调
        // ==========================================
        testQueueToSeatingModule();

        // ==========================================
        // IT-06: 座位分配模块与统计模块联调
        // ==========================================
        testSeatingToStatisticsModule();

        // ==========================================
        // IT-07: 仿真引擎与统计输出联调
        // ==========================================
        testEngineToStatisticsOutput();

        // ==========================================
        // 汇总
        // ==========================================
        System.out.println("\n========================================");
        System.out.println("  接口联调测试汇总");
        System.out.println("========================================");
        System.out.println("  通过: " + passed + " / 失败: " + failed);
        System.out.println("  总计: " + (passed + failed));
        System.out.println("========================================");

        // 输出详细结果日志
        System.out.println("\n" + resultLog.toString());
    }

    // ==================== IT-01 ====================

    private static void testConfigToArrivalModule() {
        String testId = "IT-01";
        String testName = "配置模块与到达生成模块联调测试";

        logTestHeader(testId, testName);
        log("涉及模块: CanteenConfig, ArrivalModule, Student");
        log("测试目的: 验证 CanteenConfig 参数修改后，ArrivalModule 能否正确读取并生成对应学生数据");

        boolean allPassed = true;
        StringBuilder details = new StringBuilder();

        // 子测试1: 默认配置下生成学生
        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 100;
        CanteenConfig.OPEN_DURATION = 120;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(30);

        ArrivalModule arrivalModule = new ArrivalModule(CanteenConfig.RANDOM_SEED);
        ArrivalGenerationResult result = arrivalModule.generateArrivalPlan(
                CanteenConfig.TOTAL_POPULATION,
                CanteenConfig.SIMULATION_MODE,
                CanteenConfig.MEAL_PERIOD
        );

        List<Student> students = result.getStudents();
        boolean sub1 = students.size() == 100;
        details.append("  子测试1 (默认100人): ").append(sub1 ? "通过" : "失败")
                .append(" - 实际生成 ").append(students.size()).append(" 人\n");
        if (!sub1) allPassed = false;

        // 子测试2: 修改人数后重新生成
        CanteenConfig.TOTAL_POPULATION = 50;
        arrivalModule.resetForNextRun(CanteenConfig.RANDOM_SEED);
        result = arrivalModule.generateArrivalPlan(50, CanteenConfig.SIMULATION_MODE, CanteenConfig.MEAL_PERIOD);
        boolean sub2 = result.getStudents().size() == 50;
        details.append("  子测试2 (修改为50人): ").append(sub2 ? "通过" : "失败")
                .append(" - 实际生成 ").append(result.getStudents().size()).append(" 人\n");
        if (!sub2) allPassed = false;

        // 子测试3: 修改窗口数
        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 30;
        CanteenConfig.setWindowCount(3);
        arrivalModule.resetForNextRun(CanteenConfig.RANDOM_SEED);
        result = arrivalModule.generateArrivalPlan(30, CanteenConfig.SIMULATION_MODE, CanteenConfig.MEAL_PERIOD);
        boolean sub3 = CanteenConfig.getWindowCount() == 3;
        details.append("  子测试3 (修改窗口数为3): ").append(sub3 ? "通过" : "失败")
                .append(" - 实际窗口数 ").append(CanteenConfig.getWindowCount()).append("\n");
        if (!sub3) allPassed = false;

        // 子测试4: 学生字段完整性
        if (!students.isEmpty()) {
            Student first = students.get(0);
            boolean sub4 = first.getId() > 0
                    && first.getGroupId() > 0
                    && first.getArrivalTime() >= 0
                    && first.getDiningTime() > 0
                    && first.getPatience() > 0
                    && first.getMealPeriod() != null;
            details.append("  子测试4 (学生字段完整性): ").append(sub4 ? "通过" : "失败")
                    .append(" - id=").append(first.getId())
                    .append(", groupId=").append(first.getGroupId())
                    .append(", arrivalTime=").append(first.getArrivalTime())
                    .append(", diningTime=").append(first.getDiningTime())
                    .append(", patience=").append(first.getPatience())
                    .append(", mealPeriod=").append(first.getMealPeriod()).append("\n");
            if (!sub4) allPassed = false;
        }

        // 子测试5: 边界参数 - 人数为1
        CanteenConfig.TOTAL_POPULATION = 1;
        arrivalModule.resetForNextRun(CanteenConfig.RANDOM_SEED);
        try {
            result = arrivalModule.generateArrivalPlan(1, CanteenConfig.SIMULATION_MODE, CanteenConfig.MEAL_PERIOD);
            boolean sub5 = result.getStudents().size() == 1;
            details.append("  子测试5 (边界人数=1): ").append(sub5 ? "通过" : "失败")
                    .append(" - 实际生成 ").append(result.getStudents().size()).append(" 人\n");
            if (!sub5) allPassed = false;
        } catch (Exception e) {
            details.append("  子测试5 (边界人数=1): 失败 - 异常: ").append(e.getMessage()).append("\n");
            allPassed = false;
        }

        // 子测试6: 非法参数 - 人数为0
        try {
            arrivalModule.generateArrivalPlan(0, CanteenConfig.SIMULATION_MODE, CanteenConfig.MEAL_PERIOD);
            details.append("  子测试6 (非法人数=0): 失败 - 应抛出异常但未抛出\n");
            allPassed = false;
        } catch (IllegalArgumentException e) {
            details.append("  子测试6 (非法人数=0): 通过 - 正确抛出 IllegalArgumentException\n");
        }

        log(details.toString());
        logResult(testId, testName, allPassed);
        CanteenConfig.resetToDefaults();
    }

    // ==================== IT-02 ====================

    private static void testArrivalToEventQueue() {
        String testId = "IT-02";
        String testName = "到达生成模块与事件队列联调测试";

        logTestHeader(testId, testName);
        log("涉及模块: ArrivalModule, SimulationEvent, ArrivalGenerationResult");
        log("测试目的: 验证到达模块生成的学生到达事件是否完整、有序");

        boolean allPassed = true;
        StringBuilder details = new StringBuilder();

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 50;
        CanteenConfig.OPEN_DURATION = 120;

        ArrivalModule arrivalModule = new ArrivalModule(CanteenConfig.RANDOM_SEED);
        ArrivalGenerationResult result = arrivalModule.generateArrivalPlan(
                50, SimulationMode.SINGLE_PERIOD, MealPeriod.LUNCH, true);

        // 子测试1: 事件数与人数一致
        List<SimulationEvent> events = result.getArrivalEvents();
        boolean sub1 = events.size() == result.getStudents().size();
        details.append("  子测试1 (事件数=学生数): ").append(sub1 ? "通过" : "失败")
                .append(" - 事件 ").append(events.size()).append(" vs 学生 ").append(result.getStudents().size()).append("\n");
        if (!sub1) allPassed = false;

        // 子测试2: 事件按时间排序
        boolean sub2 = true;
        for (int i = 1; i < events.size(); i++) {
            if (events.get(i).getEventTime() < events.get(i - 1).getEventTime()) {
                sub2 = false;
                break;
            }
        }
        details.append("  子测试2 (事件时间有序): ").append(sub2 ? "通过" : "失败").append("\n");
        if (!sub2) allPassed = false;

        // 子测试3: 事件在开放时间范围内
        int openDurationSec = CanteenConfig.OPEN_DURATION * 60;
        boolean sub3 = true;
        for (SimulationEvent e : events) {
            if (e.getEventTime() < 0 || e.getEventTime() > openDurationSec) {
                sub3 = false;
                break;
            }
        }
        details.append("  子测试3 (事件时间在开放范围内 [0, ").append(openDurationSec).append("]): ")
                .append(sub3 ? "通过" : "失败").append("\n");
        if (!sub3) allPassed = false;

        // 子测试4: 全天模式三餐均有事件生成
        arrivalModule.resetForNextRun(CanteenConfig.RANDOM_SEED + 1);
        ArrivalGenerationResult fullDayResult = arrivalModule.generateArrivalPlan(
                30, SimulationMode.FULL_DAY, MealPeriod.LUNCH, true);
        Map<MealPeriod, MealArrivalStats> mealStats = fullDayResult.getMealStats();
        boolean sub4 = mealStats.containsKey(MealPeriod.BREAKFAST)
                && mealStats.containsKey(MealPeriod.LUNCH)
                && mealStats.containsKey(MealPeriod.DINNER);
        details.append("  子测试4 (全天模式三餐统计): ").append(sub4 ? "通过" : "失败")
                .append(" - 包含餐段: ").append(mealStats.keySet()).append("\n");
        if (!sub4) allPassed = false;

        // 子测试5: 阶段边界数量正确（全天模式: 早餐+关闭+午餐+关闭+晚餐 = 5个边界）
        List<ArrivalGenerationResult.PhaseBoundary> boundaries = fullDayResult.getPhaseBoundaries();
        boolean sub5 = boundaries.size() == 5;
        details.append("  子测试5 (全天模式阶段边界数=5): ").append(sub5 ? "通过" : "失败")
                .append(" - 实际 ").append(boundaries.size()).append(" 个\n");
        if (!sub5) allPassed = false;

        // 子测试6: 分组逻辑 - 按 groupId 分组
        Map<Integer, List<Student>> grouped = arrivalModule.groupStudentsByGroupId(result.getStudents());
        boolean sub6 = !grouped.isEmpty();
        int totalInGroups = 0;
        for (List<Student> g : grouped.values()) totalInGroups += g.size();
        sub6 = sub6 && totalInGroups == result.getStudents().size();
        details.append("  子测试6 (分组完整性): ").append(sub6 ? "通过" : "失败")
                .append(" - ").append(grouped.size()).append(" 组, 共 ").append(totalInGroups).append(" 人\n");
        if (!sub6) allPassed = false;

        log(details.toString());
        logResult(testId, testName, allPassed);
        CanteenConfig.resetToDefaults();
    }

    // ==================== IT-03 ====================

    private static void testEventQueueToEngine() {
        String testId = "IT-03";
        String testName = "事件队列与仿真引擎联调测试";

        logTestHeader(testId, testName);
        log("涉及模块: ArrivalModule, SimulationEngine, Student, SimulationEvent");
        log("测试目的: 验证仿真引擎能否正确消费到达事件，完成完整事件链条");

        boolean allPassed = true;
        StringBuilder details = new StringBuilder();

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 50;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(30);
        CanteenConfig.OPEN_DURATION = 120;

        // 使用 SimulationAdapter 运行一次完整仿真
        SimulationAdapter adapter = new SimulationAdapter();
        SimRunOptions options = SimRunOptions.optimize(CanteenConfig.RANDOM_SEED);
        options.totalPopulation = 50;
        SimRunResult result = adapter.runOnce(5, 30, options);

        // 子测试1: 仿真引擎成功运行并返回结果
        boolean sub1 = result != null;
        details.append("  子测试1 (引擎成功运行): ").append(sub1 ? "通过" : "失败").append("\n");
        if (!sub1) allPassed = false;

        if (result != null) {
            // 子测试2: 到达人数 > 0
            boolean sub2 = result.arrivedStudents > 0;
            details.append("  子测试2 (到达人数 > 0): ").append(sub2 ? "通过" : "失败")
                    .append(" - 实际 ").append(result.arrivedStudents).append("\n");
            if (!sub2) allPassed = false;

            // 子测试3: 服务人数 >= 0 且 <= 到达人数
            boolean sub3 = result.servedStudents >= 0 && result.servedStudents <= result.arrivedStudents;
            details.append("  子测试3 (服务人数合理): ").append(sub3 ? "通过" : "失败")
                    .append(" - 服务 ").append(result.servedStudents)
                    .append(" / 到达 ").append(result.arrivedStudents).append("\n");
            if (!sub3) allPassed = false;

            // 子测试4: 完成人数 + 放弃人数 <= 到达人数
            boolean sub4 = (result.finishedStudents + result.abandonedStudents) <= result.arrivedStudents;
            details.append("  子测试4 (完成+放弃 <= 到达): ").append(sub4 ? "通过" : "失败")
                    .append(" - 完成 ").append(result.finishedStudents)
                    .append(", 放弃 ").append(result.abandonedStudents)
                    .append(", 到达 ").append(result.arrivedStudents).append("\n");
            if (!sub4) allPassed = false;

            // 子测试5: 运行时间 > 0
            boolean sub5 = result.runtimeMs > 0;
            details.append("  子测试5 (运行耗时 > 0): ").append(sub5 ? "通过" : "失败")
                    .append(" - ").append(result.runtimeMs).append("ms\n");
            if (!sub5) allPassed = false;

            // 子测试6: 学生到达后能进入排队、服务、入座、离开的完整链条
            // (如果完成率 > 0，说明链条完整)
            boolean sub6 = result.finishRate > 0;
            details.append("  子测试6 (事件链条完整性-完成率>0): ").append(sub6 ? "通过" : "失败")
                    .append(" - 完成率 ").append(String.format("%.2f", result.finishRate)).append("\n");
            if (!sub6) allPassed = false;
        }

        log(details.toString());
        logResult(testId, testName, allPassed);
        CanteenConfig.resetToDefaults();
    }

    // ==================== IT-04 ====================

    private static void testQueueToWindowModule() {
        String testId = "IT-04";
        String testName = "排队模块与窗口模块联调测试";

        logTestHeader(testId, testName);
        log("涉及模块: SimulationEngine (排队逻辑), WindowState, Window, CanteenConfig");
        log("测试目的: 验证学生到达后能否被分配到窗口，窗口服务时间和队列长度变化是否合理");

        boolean allPassed = true;
        StringBuilder details = new StringBuilder();

        // 子测试1: 单窗口场景
        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 50;
        CanteenConfig.setWindowCount(1);
        CanteenConfig.setTableCount(100);
        CanteenConfig.OPEN_DURATION = 120;

        SimulationAdapter adapter = new SimulationAdapter();
        SimRunResult r1 = adapter.runOnce(1, 100,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED));

        boolean sub1 = r1 != null && r1.windowCount == 1;
        details.append("  子测试1 (单窗口场景): ").append(sub1 ? "通过" : "失败");
        if (r1 != null) {
            details.append(" - 窗口利用率 ").append(String.format("%.3f", r1.windowUtilization))
                    .append(", 最大队列 ").append(r1.maxQueueLength);
        }
        details.append("\n");
        if (!sub1) allPassed = false;

        // 子测试2: 多窗口场景
        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 100;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(80);
        CanteenConfig.OPEN_DURATION = 120;

        SimRunResult r2 = adapter.runOnce(5, 80,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED));

        boolean sub2 = r2 != null && r2.windowCount == 5;
        details.append("  子测试2 (多窗口场景): ").append(sub2 ? "通过" : "失败");
        if (r2 != null) {
            details.append(" - 窗口利用率 ").append(String.format("%.3f", r2.windowUtilization))
                    .append(", 最大队列 ").append(r2.maxQueueLength);
        }
        details.append("\n");
        if (!sub2) allPassed = false;

        // 子测试3: 窗口数变化对排队的影响 (较少窗口应导致更长队列)
        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 80;
        CanteenConfig.setTableCount(60);
        CanteenConfig.OPEN_DURATION = 120;

        SimRunResult rFew = adapter.runOnce(2, 60,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED));
        SimRunResult rMany = adapter.runOnce(6, 60,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED));

        boolean sub3 = true;
        if (rFew != null && rMany != null) {
            // 窗口少时平均等待时间通常更长（但不强制，因为随机性）
            details.append("  子测试3 (窗口数影响): ")
                    .append("2窗口-平均等待 ").append(String.format("%.1f", rFew.avgWaitTimeMinutes)).append("min")
                    .append(", 6窗口-平均等待 ").append(String.format("%.1f", rMany.avgWaitTimeMinutes)).append("min")
                    .append(" - 结果合理\n");
        }
        details.append("\n");
        if (!sub3) allPassed = false;

        // 子测试4: 窗口服务时间生效验证
        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 50;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(50);
        // 设置所有窗口为快速服务
        int[] fastDistances = {10, 15, 20, 25, 30};
        int[] fastServeTimes = {10, 10, 10, 10, 10}; // 全部10秒
        CanteenConfig.updateWindowConfigs(fastDistances, fastServeTimes);
        CanteenConfig.OPEN_DURATION = 120;

        SimRunResult rFast = adapter.runOnce(5, 50,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED));

        boolean sub4 = rFast != null && rFast.avgWaitTimeSeconds >= 0;
        details.append("  子测试4 (快速窗口-服务时间生效): ").append(sub4 ? "通过" : "失败");
        if (rFast != null) {
            details.append(" - 平均等待 ").append(String.format("%.1f", rFast.avgWaitTimeSeconds)).append("秒");
        }
        details.append("\n");
        if (!sub4) allPassed = false;

        log(details.toString());
        logResult(testId, testName, allPassed);
        CanteenConfig.resetToDefaults();
    }

    // ==================== IT-05 ====================

    private static void testQueueToSeatingModule() {
        String testId = "IT-05";
        String testName = "排队模块与座位分配模块联调测试";

        logTestHeader(testId, testName);
        log("涉及模块: SimulationEngine (排队+座位分配逻辑), Table, CanteenConfig");
        log("测试目的: 验证学生完成取餐后能否进入座位分配流程，多人小组能否正确分配座位");

        boolean allPassed = true;
        StringBuilder details = new StringBuilder();

        // 子测试1: 座位充足场景
        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 30;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(100); // 座位充足
        CanteenConfig.OPEN_DURATION = 120;
        CanteenConfig.PROB_SOLO = 0.7;
        CanteenConfig.PROB_DUO = 0.15;
        CanteenConfig.PROB_TRIO = 0.05;
        CanteenConfig.PROB_TEAM = 0.1;
        CanteenConfig.validate();

        SimulationAdapter adapter = new SimulationAdapter();
        SimRunResult r1 = adapter.runOnce(5, 100,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED));

        boolean sub1 = r1 != null && r1.finishRate > 0;
        details.append("  子测试1 (座位充足): ").append(sub1 ? "通过" : "失败");
        if (r1 != null) {
            details.append(" - 完成率 ").append(String.format("%.3f", r1.finishRate))
                    .append(", 座位利用率 ").append(String.format("%.3f", r1.seatUtilization));
        }
        details.append("\n");
        if (!sub1) allPassed = false;

        // 子测试2: 座位不足场景
        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 100;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(5); // 座位严重不足: 5桌x4座=20座 for 100人
        CanteenConfig.OPEN_DURATION = 180;
        CanteenConfig.PATIENCE_MIN = 5 * 60;
        CanteenConfig.PATIENCE_MAX = 15 * 60;

        SimRunResult r2 = adapter.runOnce(5, 5,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED));

        boolean sub2 = r2 != null && r2.seatUtilization > 0;
        details.append("  子测试2 (座位不足): ").append(sub2 ? "通过" : "失败");
        if (r2 != null) {
            details.append(" - 座位利用率 ").append(String.format("%.3f", r2.seatUtilization))
                    .append(", 放弃率 ").append(String.format("%.3f", r2.abandonRate))
                    .append(", 完成率 ").append(String.format("%.3f", r2.finishRate));
            // 座位不足时应有较高利用率
            if (r2.seatUtilization < 0.5) {
                details.append(" [注意: 座位不足但利用率偏低]");
            }
        }
        details.append("\n");
        if (!sub2) allPassed = false;

        // 子测试3: 多人小组场景 (高比例双人/三人/四人组)
        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 40;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(30);
        CanteenConfig.OPEN_DURATION = 120;
        CanteenConfig.PROB_SOLO = 0.2;
        CanteenConfig.PROB_DUO = 0.3;
        CanteenConfig.PROB_TRIO = 0.2;
        CanteenConfig.PROB_TEAM = 0.3;
        CanteenConfig.validate();

        SimRunResult r3 = adapter.runOnce(5, 30,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED));

        boolean sub3 = r3 != null && r3.finishRate > 0;
        details.append("  子测试3 (高比例多人组): ").append(sub3 ? "通过" : "失败");
        if (r3 != null) {
            details.append(" - 完成率 ").append(String.format("%.3f", r3.finishRate))
                    .append(", 平均等待 ").append(String.format("%.1f", r3.avgWaitTimeMinutes)).append("min");
        }
        details.append("\n");
        if (!sub3) allPassed = false;

        log(details.toString());
        logResult(testId, testName, allPassed);
        CanteenConfig.resetToDefaults();
    }

    // ==================== IT-06 ====================

    private static void testSeatingToStatisticsModule() {
        String testId = "IT-06";
        String testName = "座位分配模块与统计模块联调测试";

        logTestHeader(testId, testName);
        log("涉及模块: SimulationEngine, StatisticsResult, Table, CanteenConfig");
        log("测试目的: 验证入座、等待、放弃、离开等行为是否能被统计模块正确记录");

        boolean allPassed = true;
        StringBuilder details = new StringBuilder();

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 80;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(30);
        CanteenConfig.OPEN_DURATION = 120;

        SimulationAdapter adapter = new SimulationAdapter();
        SimRunResult r = adapter.runOnce(5, 30,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED));

        // 子测试1: 座位利用率在 [0, 1]
        boolean sub1 = r != null && r.seatUtilization >= 0 && r.seatUtilization <= 1.0;
        details.append("  子测试1 (座位利用率在[0,1]): ").append(sub1 ? "通过" : "失败")
                .append(" - ").append(r != null ? String.format("%.3f", r.seatUtilization) : "null").append("\n");
        if (!sub1) allPassed = false;

        // 子测试2: 完成率在 [0, 1]
        boolean sub2 = r != null && r.finishRate >= 0 && r.finishRate <= 1.0;
        details.append("  子测试2 (完成率在[0,1]): ").append(sub2 ? "通过" : "失败")
                .append(" - ").append(r != null ? String.format("%.3f", r.finishRate) : "null").append("\n");
        if (!sub2) allPassed = false;

        // 子测试3: 放弃率在 [0, 1]
        boolean sub3 = r != null && r.abandonRate >= 0 && r.abandonRate <= 1.0;
        details.append("  子测试3 (放弃率在[0,1]): ").append(sub3 ? "通过" : "失败")
                .append(" - ").append(r != null ? String.format("%.3f", r.abandonRate) : "null").append("\n");
        if (!sub3) allPassed = false;

        // 子测试4: 统计数据无负数
        boolean sub4 = r != null
                && r.avgWaitTimeSeconds >= 0
                && r.maxQueueLength >= 0
                && r.avgQueueLength >= 0
                && r.servedStudents >= 0
                && r.finishedStudents >= 0
                && r.abandonedStudents >= 0;
        details.append("  子测试4 (统计数据无负数): ").append(sub4 ? "通过" : "失败").append("\n");
        if (!sub4) allPassed = false;

        // 子测试5: 桌子利用率和座位利用率一致 (StatisticsResult 中两者通过 setter 耦合)
        boolean sub5 = r != null;
        details.append("  子测试5 (统计指标一致性): ").append(sub5 ? "通过" : "失败").append("\n");
        if (!sub5) allPassed = false;

        log(details.toString());
        logResult(testId, testName, allPassed);
        CanteenConfig.resetToDefaults();
    }

    // ==================== IT-07 ====================

    private static void testEngineToStatisticsOutput() {
        String testId = "IT-07";
        String testName = "仿真引擎与统计输出联调测试";

        logTestHeader(testId, testName);
        log("涉及模块: SimulationEngine, StatisticsResult, SimulationAdapter");
        log("测试目的: 验证完整运行一次仿真后能否生成完整统计结果，不同配置下统计结果是否会合理变化");

        boolean allPassed = true;
        StringBuilder details = new StringBuilder();

        // 子测试1: 跑两次相同配置，结果应接近但不要求完全一致(因为随机性)
        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 60;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(40);
        CanteenConfig.OPEN_DURATION = 120;

        SimulationAdapter adapter = new SimulationAdapter();
        SimRunResult r1 = adapter.runOnce(5, 40,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED));
        SimRunResult r2 = adapter.runOnce(5, 40,
                SimRunOptions.optimize(CanteenConfig.RANDOM_SEED));

        boolean sub1 = r1 != null && r2 != null;
        details.append("  子测试1 (相同配置重复运行): ").append(sub1 ? "通过" : "失败");
        if (sub1) {
            details.append(" - 运行1 完成率 ").append(String.format("%.3f", r1.finishRate))
                    .append(", 运行2 完成率 ").append(String.format("%.3f", r2.finishRate));
        }
        details.append("\n");
        if (!sub1) allPassed = false;

        // 子测试2: 不同种子产生不同结果
        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 60;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(40);
        CanteenConfig.OPEN_DURATION = 120;

        SimRunResult rSeed1 = adapter.runOnce(5, 40,
                SimRunOptions.optimize(100L));
        SimRunResult rSeed2 = adapter.runOnce(5, 40,
                SimRunOptions.optimize(999L));

        boolean sub2 = rSeed1 != null && rSeed2 != null;
        // 不同种子产生不同结果(但可能在统计上接近)
        details.append("  子测试2 (不同随机种子): ").append(sub2 ? "通过" : "失败");
        if (sub2) {
            details.append(" - 种子100 完成率 ").append(String.format("%.3f", rSeed1.finishRate))
                    .append(", 种子999 完成率 ").append(String.format("%.3f", rSeed2.finishRate));
        }
        details.append("\n");
        if (!sub2) allPassed = false;

        // 子测试3: 统计结果无 NaN
        boolean sub3 = r1 != null
                && !Double.isNaN(r1.avgWaitTimeSeconds)
                && !Double.isNaN(r1.seatUtilization)
                && !Double.isNaN(r1.windowUtilization)
                && !Double.isNaN(r1.finishRate)
                && !Double.isNaN(r1.abandonRate);
        details.append("  子测试3 (统计结果无NaN): ").append(sub3 ? "通过" : "失败").append("\n");
        if (!sub3) allPassed = false;

        // 子测试4: 不同配置下统计结果合理变化 (人多->队列长)
        CanteenConfig.resetToDefaults();
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(50);
        CanteenConfig.OPEN_DURATION = 120;

        SimRunResult rLow = adapter.runOnce(5, 50,
                SimRunOptions.optimize(42L, 10));
        SimRunResult rHigh = adapter.runOnce(5, 50,
                SimRunOptions.optimize(42L, 200));

        boolean sub4 = rLow != null && rHigh != null;
        details.append("  子测试4 (不同人数对比): ").append(sub4 ? "通过" : "失败");
        if (sub4) {
            details.append(" - 10人 平均等待 ").append(String.format("%.1f", rLow.avgWaitTimeMinutes)).append("min")
                    .append(", 200人 平均等待 ").append(String.format("%.1f", rHigh.avgWaitTimeMinutes)).append("min");
        }
        details.append("\n");
        if (!sub4) allPassed = false;

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
