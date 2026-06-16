/*
 * 测试文件：InitPopulationTest.java
 * 所属模块：A - 初始化模块（人员到来）
 *
 * 测试功能：
 * 1. 测试 deriveMealPopulations 在不同总人数下的人数分配是否合理
 * 2. 测试早餐、午餐、晚餐人数是否满足 breakfast < dinner < lunch 的层级关系
 * 3. 测试各餐段人数是否均在 (0, totalPopulation] 范围内
 *
 * 测试数据：
 * * 正常数据：100、1000、3000
 * * 边界数据：1、10
 * * 大规模数据：20000、50000
 *
 * 预期结果：
 * * 所有餐段人数 > 0
 * * 早餐人数 < 晚餐人数 < 午餐人数
 * * 各餐段人数 <= 总人数
 */

package testAll.A;

import backend.config.CanteenConfig;
import backend.model.MealPeriod;
import backend.module.ArrivalModule;

import java.util.Map;

public class InitPopulationTest {

    private static int testPassCount = 0;
    private static int testFailCount = 0;

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("  InitPopulationTest - 人口初始化分配测试");
        System.out.println("  测试模块: ArrivalModule.deriveMealPopulations()");
        System.out.println("==============================================");
        System.out.println();

        runAll();
        printSummary();
    }

    public static int runAll() {
        testPassCount = 0;
        testFailCount = 0;

        CanteenConfig.resetToDefaults();

        // === 正常数据测试 ===
        testDeriveMealPopulations_Normal("正常数据-总人数100", 100);
        testDeriveMealPopulations_Normal("正常数据-总人数1000", 1000);
        testDeriveMealPopulations_Normal("正常数据-总人数3000", 3000);

        // === 边界数据测试 ===
        testDeriveMealPopulations_Normal("边界数据-总人数1", 1);
        testDeriveMealPopulations_Normal("边界数据-总人数10", 10);

        // === 大规模数据测试 ===
        testDeriveMealPopulations_Normal("大规模数据-总人数20000", 20000);
        testDeriveMealPopulations_Normal("大规模数据-总人数50000", 50000);

        // === 逻辑正确性测试 ===
        testHierarchyRule("逻辑测试-早餐<晚餐<午餐(1000人)", 1000);
        testHierarchyRule("逻辑测试-早餐<晚餐<午餐(3000人)", 3000);
        testHierarchyRule("逻辑测试-早餐<晚餐<午餐(50000人)", 50000);

        // === 比例合理性测试 ===
        testRatioReasonableness("比例测试-1000人比例是否合理", 1000);
        testRatioReasonableness("比例测试-20000人比例是否合理", 20000);

        return testFailCount;
    }

    // =====================================================
    // 测试1: 输入输出正确性测试 - deriveMealPopulations
    // =====================================================

    private static void testDeriveMealPopulations_Normal(String testName, int totalPopulation) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 总人数分配是否正确");

        // 输入数据
        System.out.println("输入数据：总人数 = " + totalPopulation);

        ArrivalModule module = new ArrivalModule(20260324L);
        Map<MealPeriod, Integer> result = module.deriveMealPopulations(totalPopulation);

        int breakfast = result.get(MealPeriod.BREAKFAST);
        int lunch = result.get(MealPeriod.LUNCH);
        int dinner = result.get(MealPeriod.DINNER);

        // 预期结果
        System.out.println("预期结果：各餐段人数 > 0 且 <= " + totalPopulation);

        // 实际结果
        System.out.println("实际结果：早餐=" + breakfast + ", 午餐=" + lunch + ", 晚餐=" + dinner);

        // 验证
        boolean allPositive = breakfast > 0 && lunch > 0 && dinner > 0;
        boolean allWithinRange = breakfast <= totalPopulation
                && lunch <= totalPopulation
                && dinner <= totalPopulation;

        if (allPositive) {
            System.out.println("[PASS] 各餐段人数均 > 0");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 存在餐段人数 <= 0");
            testFailCount++;
        }

        if (allWithinRange) {
            System.out.println("[PASS] 各餐段人数均 <= 总人数");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 存在餐段人数 > 总人数");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试2: 逻辑正确性测试 - 层级关系
    // =====================================================

    private static void testHierarchyRule(String testName, int totalPopulation) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 验证 breakfast < dinner < lunch 层级关系");

        // 输入数据
        System.out.println("输入数据：总人数 = " + totalPopulation);

        ArrivalModule module = new ArrivalModule(20260324L);
        Map<MealPeriod, Integer> result = module.deriveMealPopulations(totalPopulation);

        int breakfast = result.get(MealPeriod.BREAKFAST);
        int lunch = result.get(MealPeriod.LUNCH);
        int dinner = result.get(MealPeriod.DINNER);

        // 预期结果
        System.out.println("预期结果：早餐(" + breakfast + ") < 晚餐(" + dinner + ") < 午餐(" + lunch + ")");

        // 实际结果
        System.out.println("实际结果：早餐=" + breakfast + ", 午餐=" + lunch + ", 晚餐=" + dinner);

        // 验证
        if (breakfast < dinner && dinner < lunch) {
            System.out.println("[PASS] 满足 breakfast(" + breakfast + ") < dinner(" + dinner + ") < lunch(" + lunch + ")");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 不满足层级关系");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试3: 逻辑正确性测试 - 比例合理性
    // =====================================================

    private static void testRatioReasonableness(String testName, int totalPopulation) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 验证人数分配比例是否在合理范围内");

        // 输入数据
        System.out.println("输入数据：总人数 = " + totalPopulation);

        ArrivalModule module = new ArrivalModule(20260324L);
        Map<MealPeriod, Integer> result = module.deriveMealPopulations(totalPopulation);

        int breakfast = result.get(MealPeriod.BREAKFAST);
        int lunch = result.get(MealPeriod.LUNCH);
        int dinner = result.get(MealPeriod.DINNER);

        double breakfastRate = (double) breakfast / totalPopulation;
        double lunchRate = (double) lunch / totalPopulation;
        double dinnerRate = (double) dinner / totalPopulation;

        // 预期结果
        System.out.println("预期结果：早餐占比 ≈ 15%-30%, 午餐占比 ≈ 40%-65%, 晚餐占比 ≈ 25%-45%");

        // 实际结果
        System.out.printf("实际结果：早餐占比=%.2f%%, 午餐占比=%.2f%%, 晚餐占比=%.2f%%%n",
                breakfastRate * 100, lunchRate * 100, dinnerRate * 100);

        // 验证比例在合理范围
        boolean breakfastOk = breakfastRate >= 0.10 && breakfastRate <= 0.35;
        boolean lunchOk = lunchRate >= 0.35 && lunchRate <= 0.70;
        boolean dinnerOk = dinnerRate >= 0.20 && dinnerRate <= 0.50;

        if (breakfastOk) {
            System.out.println("[PASS] 早餐比例合理 (" + String.format("%.2f%%", breakfastRate * 100) + ")");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 早餐比例异常 (" + String.format("%.2f%%", breakfastRate * 100) + ")");
            testFailCount++;
        }

        if (lunchOk) {
            System.out.println("[PASS] 午餐比例合理 (" + String.format("%.2f%%", lunchRate * 100) + ")");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 午餐比例异常 (" + String.format("%.2f%%", lunchRate * 100) + ")");
            testFailCount++;
        }

        if (dinnerOk) {
            System.out.println("[PASS] 晚餐比例合理 (" + String.format("%.2f%%", dinnerRate * 100) + ")");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 晚餐比例异常 (" + String.format("%.2f%%", dinnerRate * 100) + ")");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 汇总
    // =====================================================

    private static void printSummary() {
        System.out.println("==============================================");
        System.out.println("  InitPopulationTest 测试汇总");
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
