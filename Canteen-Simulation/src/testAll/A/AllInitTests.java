/*
 * 测试文件：AllInitTests.java
 * 所属模块：A - 初始化模块（人员到来）
 *
 * 测试功能：
 * 一键运行 A 模块所有单元测试，汇总各测试类的 PASS / FAIL 结果
 *
 * 包含的测试类：
 * 1. InitPopulationTest      - 人口初始化分配测试
 * 2. ArrivalTimeTest         - 到达时间生成测试
 * 3. PersonInitTest          - 人员对象生成测试
 * 4. BoundaryTest            - 边界与异常输入测试
 * 5. InitModuleIntegrationTest - 初始化模块集成测试
 *
 * 运行方式：
 * java AllInitTests
 *
 * 依赖：所有测试类必须可直接访问（同目录下）
 */

package testAll.A;

import backend.config.CanteenConfig;

public class AllInitTests {

    private static int totalPass = 0;
    private static int totalFail = 0;

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("============================================================");
        System.out.println("  ");
        System.out.println("  A 模块 - 初始化模块（人员到来）全部单元测试");
        System.out.println("  ");
        System.out.println("  包含测试类:");
        System.out.println("    1. InitPopulationTest      - 人口初始化分配测试");
        System.out.println("    2. ArrivalTimeTest         - 到达时间生成测试");
        System.out.println("    3. PersonInitTest          - 人员对象生成测试");
        System.out.println("    4. BoundaryTest            - 边界与异常输入测试");
        System.out.println("    5. InitModuleIntegrationTest - 初始化模块集成测试");
        System.out.println("  ");
        System.out.println("============================================================");
        System.out.println("============================================================");
        System.out.println();

        // 重置配置
        CanteenConfig.resetToDefaults();

        // =============================================
        // 1. InitPopulationTest
        // =============================================
        System.out.println("##############################################################");
        System.out.println("#  测试 1/5: InitPopulationTest");
        System.out.println("##############################################################");
        System.out.println();
        int fail1 = InitPopulationTest.runAll();
        accumulate("InitPopulationTest", fail1);

        CanteenConfig.resetToDefaults();

        // =============================================
        // 2. ArrivalTimeTest
        // =============================================
        System.out.println("##############################################################");
        System.out.println("#  测试 2/5: ArrivalTimeTest");
        System.out.println("##############################################################");
        System.out.println();
        int fail2 = ArrivalTimeTest.runAll();
        accumulate("ArrivalTimeTest", fail2);

        CanteenConfig.resetToDefaults();

        // =============================================
        // 3. PersonInitTest
        // =============================================
        System.out.println("##############################################################");
        System.out.println("#  测试 3/5: PersonInitTest");
        System.out.println("##############################################################");
        System.out.println();
        int fail3 = PersonInitTest.runAll();
        accumulate("PersonInitTest", fail3);

        CanteenConfig.resetToDefaults();

        // =============================================
        // 4. BoundaryTest
        // =============================================
        System.out.println("##############################################################");
        System.out.println("#  测试 4/5: BoundaryTest");
        System.out.println("##############################################################");
        System.out.println();
        int fail4 = BoundaryTest.runAll();
        accumulate("BoundaryTest", fail4);

        CanteenConfig.resetToDefaults();

        // =============================================
        // 5. InitModuleIntegrationTest
        // =============================================
        System.out.println("##############################################################");
        System.out.println("#  测试 5/5: InitModuleIntegrationTest");
        System.out.println("##############################################################");
        System.out.println();
        int fail5 = InitModuleIntegrationTest.runAll();
        accumulate("InitModuleIntegrationTest", fail5);

        // =============================================
        // 最终汇总
        // =============================================
        printFinalSummary();

        CanteenConfig.resetToDefaults();
    }

    private static void accumulate(String testName, int failCount) {
        // Each test class manages its own pass/fail internally
        // failCount represents the number of failures (from runAll return value)
        // We don't get pass count from the return, so just accumulate fails
        // The individual test classes already print their own summaries
        System.out.println();
        System.out.println("  >>> " + testName + " 完成 (failures=" + failCount + ")");
        System.out.println();
        totalFail += failCount;
    }

    private static void printFinalSummary() {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("============================================================");
        System.out.println("  ");
        System.out.println("  A 模块 - 全部单元测试 - 最终汇总");
        System.out.println("  ");
        System.out.println("  各测试类均已在上面输出各自的 PASS / FAIL 汇总");
        System.out.println("  ");
        System.out.println("  累计 FAIL 总数: " + totalFail);
        System.out.println("  ");
        if (totalFail == 0) {
            System.out.println("  结论: [全部测试通过]");
        } else {
            System.out.println("  结论: [存在 " + totalFail + " 个失败项，请检查上方输出]");
        }
        System.out.println("  ");
        System.out.println("============================================================");
        System.out.println("============================================================");
    }
}
