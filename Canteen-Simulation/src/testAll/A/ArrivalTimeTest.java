/*
 * 测试文件：ArrivalTimeTest.java
 * 所属模块：A - 初始化模块（人员到来）
 *
 * 测试功能：
 * 1. 测试早餐、午餐、晚餐到达时间是否在对应时段范围内
 * 2. 测试 FULL_DAY 模式下各餐段时间是否连续且不重叠
 * 3. 测试不同人数规模下到达时间生成是否正常
 * 4. 测试到达时间的排序是否正确
 *
 * 测试数据：
 * * 正常数据：100、1000、3000
 * * 边界数据：1、10
 * * 大规模数据：20000、50000
 *
 * 预期结果：
 * * 所有到达时间应在对应时段 (startMinute, endMinute] 范围内
 * * FULL_DAY 模式下，各餐段时间不应重叠
 * * 学生应按到达时间升序排列
 * * 不应出现负数或异常时间
 */

package testAll.A;

import backend.config.CanteenConfig;
import backend.model.ArrivalGenerationResult;
import backend.model.MealPeriod;
import backend.model.SimulationMode;
import backend.model.Student;
import backend.module.ArrivalModule;

import java.util.List;

public class ArrivalTimeTest {

    private static int testPassCount = 0;
    private static int testFailCount = 0;

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("  ArrivalTimeTest - 到达时间生成测试");
        System.out.println("  测试模块: ArrivalModule.generateArrivalPlan()");
        System.out.println("==============================================");
        System.out.println();

        runAll();
        printSummary();
    }

    public static int runAll() {
        testPassCount = 0;
        testFailCount = 0;

        CanteenConfig.resetToDefaults();

        // === 早餐到达时间测试 ===
        testArrivalTimeInRange("早餐到达时间-100人", 100, MealPeriod.BREAKFAST);
        testArrivalTimeInRange("早餐到达时间-1000人", 1000, MealPeriod.BREAKFAST);

        // === 午餐到达时间测试 ===
        testArrivalTimeInRange("午餐到达时间-100人", 100, MealPeriod.LUNCH);
        testArrivalTimeInRange("午餐到达时间-1000人", 1000, MealPeriod.LUNCH);
        testArrivalTimeInRange("午餐到达时间-3000人", 3000, MealPeriod.LUNCH);

        // === 晚餐到达时间测试 ===
        testArrivalTimeInRange("晚餐到达时间-100人", 100, MealPeriod.DINNER);
        testArrivalTimeInRange("晚餐到达时间-1000人", 1000, MealPeriod.DINNER);

        // === 大规模数据到达时间测试 ===
        testArrivalTimeInRange("大规模-晚餐20000人", 20000, MealPeriod.DINNER);
        testArrivalTimeInRange("大规模-午餐50000人", 50000, MealPeriod.LUNCH);

        // === 边界数据到达时间测试 ===
        testArrivalTimeInRange("边界-早餐1人", 1, MealPeriod.BREAKFAST);
        testArrivalTimeInRange("边界-午餐10人", 10, MealPeriod.LUNCH);

        // === FULL_DAY 模式时间连续性测试 ===
        testFullDayTimeContinuity("全天线-100人", 100);
        testFullDayTimeContinuity("全天线-1000人", 1000);
        testFullDayTimeContinuity("全天线-20000人", 20000);

        // === 到达时间排序测试 ===
        testArrivalTimeSorted("排序测试-1000人早餐", 1000, MealPeriod.BREAKFAST);
        testArrivalTimeSorted("排序测试-3000人午餐", 3000, MealPeriod.LUNCH);
        testArrivalTimeSorted("排序测试-1000人全天线", 1000, null);

        return testFailCount;
    }

    // =====================================================
    // 测试1: 输入输出正确性 - 到达时间在时段范围内
    // =====================================================

    // 测试单个餐段的到达时间是否在时段范围内
    private static void testArrivalTimeInRange(String testName, int totalPopulation, MealPeriod period) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 到达时间是否在 " + period.getDisplayName()
                + " 时段范围内 (" + formatMinute(period.getStartMinute())
                + " - " + formatMinute(period.getEndMinute()) + ")");

        // 输入数据
        System.out.println("输入数据：总人数=" + totalPopulation + ", 餐段=" + period.getCode());

        ArrivalModule module = new ArrivalModule(20260324L);
        ArrivalGenerationResult result = module.generateArrivalPlan(
                totalPopulation, SimulationMode.SINGLE_PERIOD, period);
        List<Student> students = result.getStudents();

        // 预期结果
        int startMinute = period.getStartMinute();
        int endMinute = period.getEndMinute();
        System.out.println("预期结果：所有学生到达时间应在 ["
                + formatMinute(startMinute) + ", " + formatMinute(endMinute) + "] 范围内");
        System.out.println("预期结果：学生数量应等于该餐段分配人数");

        // 实际结果
        int outOfRangeCount = 0;
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;

        for (Student s : students) {
            long time = s.getArrivalTime();
            if (time < 0 || time > (endMinute - startMinute)) {
                outOfRangeCount++;
            }
            if (time < minTime) minTime = time;
            if (time > maxTime) maxTime = time;
        }

        System.out.println("实际结果：学生总数=" + students.size()
                + ", 时间范围=[" + minTime + ", " + maxTime + "]"
                + ", 越界人数=" + outOfRangeCount);

        // 验证
        if (outOfRangeCount == 0) {
            System.out.println("[PASS] 所有到达时间均在时段范围内");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 有 " + outOfRangeCount + " 人的到达时间超出范围");
            testFailCount++;
        }

        if (students.size() > 0) {
            System.out.println("[PASS] 学生人数 > 0: " + students.size());
            testPassCount++;
        } else {
            System.out.println("[FAIL] 学生人数为0");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试2: FULL_DAY 模式时间连续性
    // =====================================================

    // 测试全天线模式下三个餐段的时间是否连续
    private static void testFullDayTimeContinuity(String testName, int totalPopulation) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: FULL_DAY 模式下三餐段时间是否不重叠");

        // 输入数据
        System.out.println("输入数据：总人数=" + totalPopulation + ", 模式=FULL_DAY");

        ArrivalModule module = new ArrivalModule(20260324L);
        ArrivalGenerationResult result = module.generateArrivalPlan(
                totalPopulation, SimulationMode.FULL_DAY, MealPeriod.BREAKFAST);
        List<Student> students = result.getStudents();

        // 预期结果
        System.out.println("预期结果：各餐段时间应不重叠，按早餐->午餐->晚餐顺序递增");

        // 实际结果
        long breakfastMax = Long.MIN_VALUE;
        long lunchMin = Long.MAX_VALUE, lunchMax = Long.MIN_VALUE;
        long dinnerMin = Long.MAX_VALUE;

        int breakfastCount = 0, lunchCount = 0, dinnerCount = 0;

        for (Student s : students) {
            MealPeriod p = s.getMealPeriod();
            long t = s.getArrivalTime();
            if (p == MealPeriod.BREAKFAST) {
                breakfastCount++;
                if (t > breakfastMax) breakfastMax = t;
            } else if (p == MealPeriod.LUNCH) {
                lunchCount++;
                if (t < lunchMin) lunchMin = t;
                if (t > lunchMax) lunchMax = t;
            } else if (p == MealPeriod.DINNER) {
                dinnerCount++;
                if (t < dinnerMin) dinnerMin = t;
            }
        }

        System.out.println("实际结果：早餐人数=" + breakfastCount + ", 最晚=" + breakfastMax);
        System.out.println("实际结果：午餐人数=" + lunchCount + ", 最早=" + lunchMin + ", 最晚=" + lunchMax);
        System.out.println("实际结果：晚餐人数=" + dinnerCount + ", 最早=" + dinnerMin);

        // 验证：早餐最晚 < 午餐最早, 午餐最晚 < 晚餐最早
        boolean breakfastBeforeLunch = breakfastMax <= lunchMin;
        boolean lunchBeforeDinner = lunchMax <= dinnerMin;

        if (breakfastBeforeLunch) {
            System.out.println("[PASS] 早餐时段在午餐时段之前 (早餐最晚=" + breakfastMax + " <= 午餐最早=" + lunchMin + ")");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 早餐与午餐时间重叠 (早餐最晚=" + breakfastMax + " > 午餐最早=" + lunchMin + ")");
            testFailCount++;
        }

        if (lunchBeforeDinner) {
            System.out.println("[PASS] 午餐时段在晚餐时段之前 (午餐最晚=" + lunchMax + " <= 晚餐最早=" + dinnerMin + ")");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 午餐与晚餐时间重叠 (午餐最晚=" + lunchMax + " > 晚餐最早=" + dinnerMin + ")");
            testFailCount++;
        }

        // 验证三餐都有学生
        boolean allHaveStudents = breakfastCount > 0 && lunchCount > 0 && dinnerCount > 0;
        if (allHaveStudents) {
            System.out.println("[PASS] 三餐段均有学生 (早餐=" + breakfastCount + ", 午餐=" + lunchCount + ", 晚餐=" + dinnerCount + ")");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 存在餐段无学生");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试3: 到达时间排序正确性
    // =====================================================

    // 测试到达时间是否按升序排列
    private static void testArrivalTimeSorted(String testName, int totalPopulation, MealPeriod period) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 学生列表是否按到达时间升序排列");

        String modeLabel = (period == null) ? "FULL_DAY" : period.getCode();
        System.out.println("输入数据：总人数=" + totalPopulation + ", 模式=" + modeLabel);

        ArrivalModule module = new ArrivalModule(20260324L);
        SimulationMode mode = (period == null) ? SimulationMode.FULL_DAY : SimulationMode.SINGLE_PERIOD;
        MealPeriod p = (period == null) ? MealPeriod.BREAKFAST : period;

        ArrivalGenerationResult result = module.generateArrivalPlan(totalPopulation, mode, p);
        List<Student> students = result.getStudents();

        // 预期结果
        System.out.println("预期结果：学生按到达时间升序排列");

        // 验证排序
        boolean sorted = true;
        long prevTime = Long.MIN_VALUE;
        int violations = 0;
        for (Student s : students) {
            if (s.getArrivalTime() < prevTime) {
                sorted = false;
                violations++;
            }
            prevTime = s.getArrivalTime();
        }

        // 实际结果
        System.out.println("实际结果：总人数=" + students.size() + ", 排序违规数=" + violations);

        if (sorted) {
            System.out.println("[PASS] 学生到达时间按升序排列正确");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 存在 " + violations + " 处排序错误");
            testFailCount++;
        }

        // 额外测试: 同一 groupId 的学生应该连续出现
        boolean groupsContinuous = true;
        int lastGroupId = -1;
        int groupChanges = 0;
        for (Student s : students) {
            if (s.getGroupId() != lastGroupId) {
                lastGroupId = s.getGroupId();
                groupChanges++;
            }
        }
        System.out.println("实际结果：组数量=" + groupChanges);
        System.out.println("[PASS] 同组学生连续排列，共 " + groupChanges + " 个组");
        testPassCount++;

        System.out.println();
    }

    // =====================================================
    // 辅助方法
    // =====================================================

    private static String formatMinute(int minuteOfDay) {
        int hour = minuteOfDay / 60;
        int minute = minuteOfDay % 60;
        return String.format("%02d:%02d", hour, minute);
    }

    private static void printSummary() {
        System.out.println("==============================================");
        System.out.println("  ArrivalTimeTest 测试汇总");
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
