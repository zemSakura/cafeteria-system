/*
 * 测试文件：InitModuleIntegrationTest.java
 * 所属模块：A - 初始化模块（人员到来）
 *
 * 测试功能：
 * 1. 测试 SINGLE_PERIOD 模式下各餐段 (BREAKFAST/LUNCH/DINNER) 的完整初始化流程
 * 2. 测试 FULL_DAY 模式下三餐段连续生成的完整流程
 * 3. 测试 ArrivalGenerationResult 中的 MealArrivalStats 统计信息是否完整
 * 4. 测试到达事件 (SimulationEvent) 生成是否正确
 * 5. 测试窗口初始化 (initWindows/initWindowStates) 流程
 *
 * 测试数据：
 * * 正常数据：100、1000、3000
 * * 大规模数据：20000、50000
 *
 * 预期结果：
 * * 所有流程不抛出异常
 * * 学生人数合理
 * * 统计信息完整
 * * 事件队列有序
 * * 窗口配置正确
 */

package testAll.A;

import backend.config.CanteenConfig;
import backend.config.SimulationConfigRequest;
import backend.engine.CanteenSimulationEngine;
import backend.model.ArrivalDistributionPoint;
import backend.model.ArrivalGenerationResult;
import backend.model.ArrivalPeak;
import backend.model.MealArrivalStats;
import backend.model.MealPeriod;
import backend.model.SimulationEvent;
import backend.model.SimulationMode;
import backend.model.Student;
import backend.model.WindowState;
import backend.module.ArrivalModule;

import java.util.List;
import java.util.Map;

public class InitModuleIntegrationTest {

    private static int testPassCount = 0;
    private static int testFailCount = 0;

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("  InitModuleIntegrationTest - 初始化模块集成测试");
        System.out.println("  测试范围: ArrivalModule / CanteenSimulationEngine");
        System.out.println("==============================================");
        System.out.println();

        runAll();
        printSummary();
    }

    public static int runAll() {
        testPassCount = 0;
        testFailCount = 0;

        // === SINGLE_PERIOD 集成测试 ===
        testSinglePeriodIntegration("集成-SINGLE_PERIOD-早餐100人", 100, MealPeriod.BREAKFAST);
        testSinglePeriodIntegration("集成-SINGLE_PERIOD-午餐1000人", 1000, MealPeriod.LUNCH);
        testSinglePeriodIntegration("集成-SINGLE_PERIOD-晚餐3000人", 3000, MealPeriod.DINNER);
        testSinglePeriodIntegration("集成-SINGLE_PERIOD-午餐20000人", 20000, MealPeriod.LUNCH);
        testSinglePeriodIntegration("集成-SINGLE_PERIOD-晚餐50000人", 50000, MealPeriod.DINNER);

        // === FULL_DAY 集成测试 ===
        testFullDayIntegration("集成-FULL_DAY-100人", 100);
        testFullDayIntegration("集成-FULL_DAY-1000人", 1000);
        testFullDayIntegration("集成-FULL_DAY-20000人", 20000);

        // === MealArrivalStats 完整性测试 ===
        testMealArrivalStats("统计信息完整性-午餐1000人", 1000, MealPeriod.LUNCH);
        testMealArrivalStats("统计信息完整性-全天线3000人", 3000, null);

        // === 到达事件测试 ===
        testArrivalEvents("到达事件-午餐1000人", 1000, MealPeriod.LUNCH);
        testArrivalEvents("到达事件-全天线3000人", 3000, null);

        // === 窗口初始化测试 ===
        testWindowInit("窗口初始化-默认配置");

        // === CanteenSimulationEngine 集成测试 ===
        testSimulationEngine("引擎集成-默认配置启动");
        testSimulationEngineConfig("引擎集成-自定义配置启动");

        return testFailCount;
    }

    // =====================================================
    // 测试1: SINGLE_PERIOD 集成测试
    // =====================================================

    // 测试单餐段模式的完整初始化流程
    private static void testSinglePeriodIntegration(String testName, int totalPopulation, MealPeriod period) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: SINGLE_PERIOD 模式下 " + period.getDisplayName() + " 完整初始化流程");

        System.out.println("输入数据：总人数=" + totalPopulation + ", 模式=SINGLE_PERIOD, 餐段=" + period.getCode());
        System.out.println("预期结果：完整生成学生列表、到达事件、统计数据，无异常");

        CanteenConfig.resetToDefaults();

        try {
            ArrivalModule module = new ArrivalModule(20260324L);
            ArrivalGenerationResult result = module.generateArrivalPlan(
                    totalPopulation, SimulationMode.SINGLE_PERIOD, period);

            // 验证结果结构
            System.out.println("实际结果：");

            // 模式
            System.out.println("  模拟模式: " + result.getSimulationMode());
            if (result.getSimulationMode() == SimulationMode.SINGLE_PERIOD) {
                System.out.println("  [PASS] 模式正确");
                testPassCount++;
            } else {
                System.out.println("  [FAIL] 模式错误");
                testFailCount++;
            }

            // 总人数参数
            System.out.println("  总人数参数: " + result.getTotalPopulation());
            if (result.getTotalPopulation() == totalPopulation) {
                System.out.println("  [PASS] 总人数参数正确");
                testPassCount++;
            } else {
                System.out.println("  [FAIL] 总人数参数不正确");
                testFailCount++;
            }

            // 学生列表
            List<Student> students = result.getStudents();
            System.out.println("  生成学生数: " + students.size());
            if (!students.isEmpty()) {
                System.out.println("  [PASS] 成功生成学生");
                testPassCount++;
            } else {
                System.out.println("  [FAIL] 未生成任何学生");
                testFailCount++;
            }

            // 到达事件
            List<SimulationEvent> events = result.getArrivalEvents();
            System.out.println("  到达事件数: " + events.size());
            if (events.size() == students.size()) {
                System.out.println("  [PASS] 到达事件数与生成学生数一致");
                testPassCount++;
            } else {
                System.out.println("  [FAIL] 事件数(" + events.size() + ") != 学生数(" + students.size() + ")");
                testFailCount++;
            }

            // 餐段统计
            Map<MealPeriod, MealArrivalStats> mealStats = result.getMealStats();
            System.out.println("  餐段统计数: " + mealStats.size());
            if (mealStats.containsKey(period)) {
                MealArrivalStats stats = mealStats.get(period);
                System.out.println("  该餐段人数: " + stats.getPopulation()
                        + " (背景=" + stats.getBackgroundPopulation()
                        + ", 高峰=" + stats.getPeakPopulation()
                        + ", 峰数=" + stats.getPeaks().size()
                        + ", 分布点数=" + stats.getDistributionPoints().size() + ")");
                System.out.println("  [PASS] 餐段统计完整");
                testPassCount++;
            } else {
                System.out.println("  [FAIL] 缺少 " + period.getCode() + " 统计信息");
                testFailCount++;
            }

        } catch (Exception e) {
            System.out.println("实际结果：抛出异常 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("[FAIL] 集成测试不应抛出异常");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试2: FULL_DAY 集成测试
    // =====================================================

    // 测试全天模式的完整初始化流程
    private static void testFullDayIntegration(String testName, int totalPopulation) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: FULL_DAY 模式下三餐段连续初始化流程");

        System.out.println("输入数据：总人数=" + totalPopulation + ", 模式=FULL_DAY");
        System.out.println("预期结果：生成包含早餐、午餐、晚餐三个餐段的学生和统计信息");

        CanteenConfig.resetToDefaults();

        try {
            ArrivalModule module = new ArrivalModule(20260324L);
            ArrivalGenerationResult result = module.generateArrivalPlan(
                    totalPopulation, SimulationMode.FULL_DAY, MealPeriod.BREAKFAST);

            List<Student> students = result.getStudents();
            Map<MealPeriod, MealArrivalStats> mealStats = result.getMealStats();

            // 实际结果
            int breakfastCount = 0, lunchCount = 0, dinnerCount = 0;
            for (Student s : students) {
                MealPeriod p = s.getMealPeriod();
                if (p == MealPeriod.BREAKFAST) breakfastCount++;
                else if (p == MealPeriod.LUNCH) lunchCount++;
                else if (p == MealPeriod.DINNER) dinnerCount++;
            }

            System.out.println("实际结果：总学生数=" + students.size());
            System.out.println("  早餐人数=" + breakfastCount
                    + ", 午餐人数=" + lunchCount
                    + ", 晚餐人数=" + dinnerCount);

            // 验证三餐都有学生
            boolean allPeriodsHaveStudents = breakfastCount > 0 && lunchCount > 0 && dinnerCount > 0;
            if (allPeriodsHaveStudents) {
                System.out.println("[PASS] 三餐段均生成了学生");
                testPassCount++;
            } else {
                System.out.println("[FAIL] 存在餐段没有学生");
                testFailCount++;
            }

            // 验证统计包含三餐
            if (mealStats.size() == 3) {
                System.out.println("[PASS] 统计信息包含全部三个餐段");
                testPassCount++;
            } else {
                System.out.println("[FAIL] 统计信息餐段数=" + mealStats.size() + " (预期3)");
                testFailCount++;
            }

            // 验证 time 连续性
            long maxBreakfastTime = -1, minLunchTime = Long.MAX_VALUE, maxLunchTime = -1, minDinnerTime = Long.MAX_VALUE;
            for (Student s : students) {
                long t = s.getArrivalTime();
                if (s.getMealPeriod() == MealPeriod.BREAKFAST) {
                    if (t > maxBreakfastTime) maxBreakfastTime = t;
                } else if (s.getMealPeriod() == MealPeriod.LUNCH) {
                    if (t < minLunchTime) minLunchTime = t;
                    if (t > maxLunchTime) maxLunchTime = t;
                } else if (s.getMealPeriod() == MealPeriod.DINNER) {
                    if (t < minDinnerTime) minDinnerTime = t;
                }
            }
            System.out.println("实际结果：早餐最晚=" + maxBreakfastTime
                    + ", 午餐最早=" + minLunchTime + ", 午餐最晚=" + maxLunchTime
                    + ", 晚餐最早=" + minDinnerTime);

            if (maxBreakfastTime <= minLunchTime && maxLunchTime <= minDinnerTime) {
                System.out.println("[PASS] 餐段时间连续无重叠");
                testPassCount++;
            } else {
                System.out.println("[FAIL] 餐段时间存在重叠");
                testFailCount++;
            }

        } catch (Exception e) {
            System.out.println("实际结果：抛出异常 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("[FAIL] FULL_DAY 集成测试不应抛出异常");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试3: MealArrivalStats 完整性
    // =====================================================

    // 测试统计信息是否完整，包含必要的分布数据
    private static void testMealArrivalStats(String testName, int totalPopulation, MealPeriod period) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: MealArrivalStats 统计信息完整性");

        String modeLabel = (period == null) ? "FULL_DAY" : period.getCode();
        System.out.println("输入数据：总人数=" + totalPopulation + ", 模式=" + modeLabel);
        System.out.println("预期结果：统计应包含 population, backgroundPopulation, peakPopulation, peaks, distributionPoints");

        CanteenConfig.resetToDefaults();

        ArrivalModule module = new ArrivalModule(20260324L);
        SimulationMode mode = (period == null) ? SimulationMode.FULL_DAY : SimulationMode.SINGLE_PERIOD;
        MealPeriod p = (period == null) ? MealPeriod.BREAKFAST : period;

        ArrivalGenerationResult result = module.generateArrivalPlan(totalPopulation, mode, p);
        Map<MealPeriod, MealArrivalStats> mealStats = result.getMealStats();

        for (Map.Entry<MealPeriod, MealArrivalStats> entry : mealStats.entrySet()) {
            MealArrivalStats stats = entry.getValue();
            System.out.println();
            System.out.println("  " + entry.getKey().getDisplayName() + ":");
            System.out.println("    总人数: " + stats.getPopulation());
            System.out.println("    背景人数: " + stats.getBackgroundPopulation());
            System.out.println("    高峰人数: " + stats.getPeakPopulation());

            // 验证背景+高峰=总人数
            if (stats.getBackgroundPopulation() + stats.getPeakPopulation() == stats.getPopulation()) {
                System.out.println("    [PASS] 背景(" + stats.getBackgroundPopulation()
                        + ") + 高峰(" + stats.getPeakPopulation()
                        + ") = 总人数(" + stats.getPopulation() + ")");
                testPassCount++;
            } else {
                System.out.println("    [FAIL] 背景+高峰 != 总人数");
                testFailCount++;
            }

            // 验证峰值信息
            List<ArrivalPeak> peaks = stats.getPeaks();
            if (!peaks.isEmpty()) {
                System.out.println("    [PASS] 包含 " + peaks.size() + " 个峰值: "
                        + peaks.stream().map(ArrivalPeak::getName).reduce((a, b) -> a + ", " + b).orElse(""));
                testPassCount++;
            } else {
                System.out.println("    [FAIL] 无峰值信息");
                testFailCount++;
            }

            // 验证分布点
            List<ArrivalDistributionPoint> points = stats.getDistributionPoints();
            if (!points.isEmpty()) {
                System.out.println("    [PASS] 包含 " + points.size() + " 个分布点 (分钟级)");
                testPassCount++;
            } else {
                System.out.println("    [FAIL] 无分布点信息");
                testFailCount++;
            }
        }

        System.out.println();
    }

    // =====================================================
    // 测试4: 到达事件
    // =====================================================

    // 测试到达事件生成是否正确
    private static void testArrivalEvents(String testName, int totalPopulation, MealPeriod period) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 到达事件生成正确性");

        String modeLabel = (period == null) ? "FULL_DAY" : period.getCode();
        System.out.println("输入数据：总人数=" + totalPopulation + ", 模式=" + modeLabel);
        System.out.println("预期结果：事件数量=学生数量, 事件有序, 每个事件对应唯一学生");

        CanteenConfig.resetToDefaults();

        ArrivalModule module = new ArrivalModule(20260324L);
        SimulationMode mode = (period == null) ? SimulationMode.FULL_DAY : SimulationMode.SINGLE_PERIOD;
        MealPeriod p = (period == null) ? MealPeriod.BREAKFAST : period;

        ArrivalGenerationResult result = module.generateArrivalPlan(totalPopulation, mode, p);
        List<Student> students = result.getStudents();
        List<SimulationEvent> events = result.getArrivalEvents();

        // 验证事件数量
        System.out.println("实际结果：学生数=" + students.size() + ", 事件数=" + events.size());
        if (events.size() == students.size()) {
            System.out.println("[PASS] 事件数量与生成学生数一致");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 事件数量不一致");
            testFailCount++;
        }

        // 验证事件有序
        boolean eventsSorted = true;
        for (int i = 1; i < events.size(); i++) {
            if (events.get(i).getEventTime() < events.get(i - 1).getEventTime()) {
                eventsSorted = false;
                break;
            }
        }
        if (eventsSorted) {
            System.out.println("[PASS] 事件按时间升序排列");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 事件未按时间升序排列");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试5: 窗口初始化
    // =====================================================

    // 测试窗口初始化
    private static void testWindowInit(String testName) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 窗口初始化 (initWindows / initWindowStates)");

        CanteenConfig.resetToDefaults();

        System.out.println("输入数据：默认配置, 窗口数=" + CanteenConfig.getWindowCount());
        System.out.println("预期结果：生成正确数量的窗口和窗口状态");

        ArrivalModule module = new ArrivalModule(20260324L);

        // initWindows
        var windows = module.initWindows();
        System.out.println("实际结果：initWindows 生成 " + windows.size() + " 个窗口");
        if (windows.size() == CanteenConfig.getWindowCount()) {
            System.out.println("[PASS] initWindows 窗口数量正确");
            testPassCount++;
        } else {
            System.out.println("[FAIL] initWindows 窗口数量不正确");
            testFailCount++;
        }

        // initWindowStates
        List<WindowState> states = module.initWindowStates();
        System.out.println("实际结果：initWindowStates 生成 " + states.size() + " 个窗口状态");
        if (states.size() == CanteenConfig.getWindowCount()) {
            System.out.println("[PASS] initWindowStates 窗口状态数量正确");
            testPassCount++;
        } else {
            System.out.println("[FAIL] initWindowStates 窗口状态数量不正确");
            testFailCount++;
        }

        // 验证窗口 ID 和距离
        boolean allValid = true;
        for (int i = 0; i < windows.size(); i++) {
            var w = windows.get(i);
            if (w.getId() != i) allValid = false;
            if (w.getDistanceFromDoor() < 0) allValid = false;
            if (w.getAvgServeTime() <= 0) allValid = false;
        }
        if (allValid) {
            System.out.println("[PASS] 所有窗口属性合法");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 存在窗口属性非法");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试6: CanteenSimulationEngine 集成
    // =====================================================

    // 测试 CanteenSimulationEngine 默认配置启动
    private static void testSimulationEngine(String testName) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: CanteenSimulationEngine 默认配置启动");

        CanteenConfig.resetToDefaults();

        System.out.println("输入数据：默认配置 (总人数=" + CanteenConfig.DEFAULT_TOTAL_POPULATION + ")");
        System.out.println("预期结果：引擎正常启动，生成学生、窗口、桌位、事件");

        try {
            CanteenSimulationEngine engine = new CanteenSimulationEngine();
            engine.startEngine();

            System.out.println("实际结果：");
            System.out.println("  学生数: " + engine.getStudentCount());
            System.out.println("  窗口数: " + engine.getWindowCount());
            System.out.println("  桌位数: " + engine.getTableCount());
            System.out.println("  事件数: " + engine.getEventQueue().size());
            System.out.println("  运行中: " + engine.isRunning());

            if (engine.getStudentCount() > 0) {
                System.out.println("[PASS] 引擎启动后生成了学生");
                testPassCount++;
            } else {
                System.out.println("[FAIL] 未生成任何学生");
                testFailCount++;
            }

            if (engine.getWindowCount() > 0) {
                System.out.println("[PASS] 窗口初始化正确");
                testPassCount++;
            } else {
                System.out.println("[FAIL] 窗口未初始化");
                testFailCount++;
            }

            if (engine.isRunning()) {
                System.out.println("[PASS] 引擎运行状态正确");
                testPassCount++;
            } else {
                System.out.println("[FAIL] 引擎未处于运行状态");
                testFailCount++;
            }

            // 清理
            engine.stopEngine();
            engine.resetEngine();

        } catch (Exception e) {
            System.out.println("实际结果：抛出异常 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("[FAIL] 引擎启动不应抛出异常");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试7: CanteenSimulationEngine 自定义配置
    // =====================================================

    // 测试 CanteenSimulationEngine 自定义配置启动
    private static void testSimulationEngineConfig(String testName) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: CanteenSimulationEngine 自定义配置启动");

        CanteenConfig.resetToDefaults();

        System.out.println("输入数据：自定义配置 (总人数=500, 桌位=100)");
        System.out.println("预期结果：引擎按自定义配置正常启动");

        try {
            CanteenSimulationEngine engine = new CanteenSimulationEngine();

            SimulationConfigRequest request = new SimulationConfigRequest();
            request.setTotalPopulation(500);
            request.setTableCount(100);

            engine.applyConfig(request);
            engine.startEngine();

            System.out.println("实际结果：");
            System.out.println("  学生数: " + engine.getStudentCount());
            System.out.println("  桌位数: " + engine.getTableCount());

            if (engine.getTableCount() == 100) {
                System.out.println("[PASS] 桌位数量按自定义配置正确");
                testPassCount++;
            } else {
                System.out.println("[FAIL] 桌位数量不正确: " + engine.getTableCount() + " (预期100)");
                testFailCount++;
            }

            if (engine.getStudentCount() > 0) {
                System.out.println("[PASS] 自定义配置下生成了学生");
                testPassCount++;
            } else {
                System.out.println("[FAIL] 未生成学生");
                testFailCount++;
            }

            // 清理
            engine.stopEngine();
            engine.resetEngine();
            CanteenConfig.resetToDefaults();

        } catch (Exception e) {
            System.out.println("实际结果：抛出异常 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("[FAIL] 自定义配置启动不应抛出异常");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 汇总
    // =====================================================

    private static void printSummary() {
        System.out.println("==============================================");
        System.out.println("  InitModuleIntegrationTest 测试汇总");
        System.out.println("  PASS: " + testPassCount + "  FAIL: " + testFailCount);
        System.out.println("  总计: " + (testPassCount + testFailCount));
        if (testFailCount == 0) {
            System.out.println("  结果: [全部通过]");
        } else {
            System.out.println("  结果: [存在失败]");
        }
        System.out.println("==============================================");
    }
}
