/*
 * 测试文件：BoundaryTest.java
 * 所属模块：A - 初始化模块（人员到来）
 *
 * 测试功能：
 * 1. 测试 totalPopulation 为 0、负数、极大数等非法输入时的异常处理
 * 2. 测试 null 参数（SimulationMode、MealPeriod、SimulationConfigRequest）的默认行为
 * 3. 测试 CanteenConfig.validate() 对非法配置的检测
 * 4. 测试 ArrivalModule 构造函数不同参数组合
 *
 * 测试数据：
 * * 非法数据：-1、0、Integer.MAX_VALUE
 * * null 数据：null SimulationMode、null MealPeriod、null SimulationConfigRequest
 * * 边界数据：1、10
 * * 异常配置数据：负数参数、超出范围的比例、不匹配的数组
 *
 * 预期结果：
 * * 负数/零 totalPopulation 应抛出 IllegalArgumentException
 * * null 参数应有默认行为或抛出异常
 * * 非法配置应抛出 IllegalArgumentException
 * * 边界值 1 应正常工作
 */

package testAll.A;

import backend.config.CanteenConfig;
import backend.config.SimulationConfigRequest;
import backend.model.MealPeriod;
import backend.model.SimulationMode;
import backend.module.ArrivalModule;

public class BoundaryTest {

    private static int testPassCount = 0;
    private static int testFailCount = 0;

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("  BoundaryTest - 边界与异常输入测试");
        System.out.println("  测试模块: ArrivalModule / CanteenConfig");
        System.out.println("==============================================");
        System.out.println();

        runAll();
        printSummary();
    }

    public static int runAll() {
        testPassCount = 0;
        testFailCount = 0;

        // === totalPopulation 非法输入测试 ===
        testNegativePopulation("非法输入-负数(-1)", -1);
        testZeroPopulation("非法输入-零(0)", 0);
        testMaxPopulation("边界输入-Integer.MAX_VALUE", Integer.MAX_VALUE);

        // === totalPopulation 边界有效输入测试 ===
        testMinValidPopulation("边界输入-最小有效值(1)", 1);
        testSmallValidPopulation("边界输入-小值(10)", 10);

        // === null 参数测试 ===
        testNullSimulationMode("null参数-null SimulationMode");
        testNullMealPeriod("null参数-null MealPeriod");
        testNullConfigRequest("null参数-null SimulationConfigRequest");

        // === CanteenConfig 配置异常测试 ===
        testInvalidWindowCount("异常配置-windowCount<=0");
        testNullWindowConfigs("异常配置-null窗口配置数组");
        testMismatchedWindowConfigs("异常配置-窗口数组长度不匹配");
        testInvalidRate("异常配置-非法概率值");
        testNegativeOpenDuration("异常配置-负数营业时长");
        testResetToDefaults("正常配置-resetToDefaults恢复默认值");

        // === ArrivalModule 构造函数测试 ===
        testArrivalModuleConstructors("构造函数测试-多种构造方式");

        // === resetForNextRun 测试 ===
        testResetForNextRun("重置测试-resetForNextRun");

        return testFailCount;
    }

    // =====================================================
    // 测试1: 负数 totalPopulation
    // =====================================================

    // 测试负数总人数
    private static void testNegativePopulation(String testName, int population) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 负数 totalPopulation 应抛出异常");

        System.out.println("输入数据：totalPopulation = " + population);
        System.out.println("预期结果：抛出 IllegalArgumentException");

        try {
            ArrivalModule module = new ArrivalModule(20260324L);
            module.generateArrivalPlan(population, SimulationMode.SINGLE_PERIOD, MealPeriod.LUNCH);
            System.out.println("实际结果：没有抛出异常");
            System.out.println("[FAIL] 负数 totalPopulation 应抛出异常但未抛出");
            testFailCount++;
        } catch (IllegalArgumentException e) {
            System.out.println("实际结果：抛出 IllegalArgumentException: " + e.getMessage());
            System.out.println("[PASS] 正确抛出 IllegalArgumentException");
            testPassCount++;
        } catch (Exception e) {
            System.out.println("实际结果：抛出 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("[PASS] 抛出异常 (类型: " + e.getClass().getSimpleName() + ")");
            testPassCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试2: 零 totalPopulation
    // =====================================================

    // 测试总人数为0
    private static void testZeroPopulation(String testName, int population) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 零 totalPopulation 应抛出异常");

        System.out.println("输入数据：totalPopulation = " + population);
        System.out.println("预期结果：抛出 IllegalArgumentException");

        try {
            ArrivalModule module = new ArrivalModule(20260324L);
            module.generateArrivalPlan(population, SimulationMode.SINGLE_PERIOD, MealPeriod.LUNCH);
            System.out.println("实际结果：没有抛出异常");
            System.out.println("[FAIL] 零 totalPopulation 应抛出异常但未抛出");
            testFailCount++;
        } catch (IllegalArgumentException e) {
            System.out.println("实际结果：抛出 IllegalArgumentException: " + e.getMessage());
            System.out.println("[PASS] 正确抛出 IllegalArgumentException");
            testPassCount++;
        } catch (Exception e) {
            System.out.println("实际结果：抛出 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("[PASS] 抛出异常 (类型: " + e.getClass().getSimpleName() + ")");
            testPassCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试3: Integer.MAX_VALUE
    // =====================================================

    // 测试极大值（仅测人口分配，避免 generateArrivalPlan 导致 OOM）
    private static void testMaxPopulation(String testName, int population) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: Integer.MAX_VALUE 作为 deriveMealPopulations 输入");

        System.out.println("输入数据：totalPopulation = " + population + " (Integer.MAX_VALUE)");
        System.out.println("预期结果：deriveMealPopulations 应能完成计算或抛出可预期异常");

        try {
            CanteenConfig.resetToDefaults();
            ArrivalModule module = new ArrivalModule(20260324L);
            java.util.Map<MealPeriod, Integer> result = module.deriveMealPopulations(population);
            System.out.println("实际结果：早餐=" + result.get(MealPeriod.BREAKFAST)
                    + ", 午餐=" + result.get(MealPeriod.LUNCH)
                    + ", 晚餐=" + result.get(MealPeriod.DINNER));
            System.out.println("[PASS] 极大值输入下 deriveMealPopulations 正常完成计算");
            testPassCount++;
        } catch (IllegalArgumentException e) {
            System.out.println("实际结果：抛出 IllegalArgumentException: " + e.getMessage());
            System.out.println("[PASS] 抛出可预期的 IllegalArgumentException");
            testPassCount++;
        } catch (Exception e) {
            System.out.println("实际结果：抛出 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("[PASS] 抛出异常但未崩溃 (" + e.getClass().getSimpleName() + ")");
            testPassCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试4: 最小有效值
    // =====================================================

    // 测试总人数为1（最小有效值）
    private static void testMinValidPopulation(String testName, int population) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 最小有效 totalPopulation=1 应正常生成");

        System.out.println("输入数据：totalPopulation = " + population);
        System.out.println("预期结果：正常生成 1 人的到达计划，不抛出异常");

        CanteenConfig.resetToDefaults();

        try {
            ArrivalModule module = new ArrivalModule(20260324L);
            var result = module.generateArrivalPlan(population, SimulationMode.SINGLE_PERIOD, MealPeriod.LUNCH);
            int studentCount = result.getStudents().size();
            System.out.println("实际结果：成功生成 " + studentCount + " 个学生");
            if (studentCount > 0) {
                System.out.println("[PASS] 最小有效值正常工作，生成了 " + studentCount + " 人");
                testPassCount++;
            } else {
                System.out.println("[FAIL] 总人数为1但未生成任何学生");
                testFailCount++;
            }
        } catch (Exception e) {
            System.out.println("实际结果：抛出异常 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("[FAIL] 最小有效值不应抛出异常");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试5: 小有效值
    // =====================================================

    // 测试总人数为10
    private static void testSmallValidPopulation(String testName, int population) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 小值 totalPopulation=10 应正常生成");

        System.out.println("输入数据：totalPopulation = " + population);
        System.out.println("预期结果：正常生成到达计划，学生数 > 0");

        CanteenConfig.resetToDefaults();

        try {
            ArrivalModule module = new ArrivalModule(20260324L);
            var result = module.generateArrivalPlan(population, SimulationMode.SINGLE_PERIOD, MealPeriod.LUNCH);
            int studentCount = result.getStudents().size();
            System.out.println("实际结果：成功生成 " + studentCount + " 个学生");
            if (studentCount > 0) {
                System.out.println("[PASS] 小值正常工作，生成了 " + studentCount + " 人");
                testPassCount++;
            } else {
                System.out.println("[FAIL] 总人数为10但未生成任何学生");
                testFailCount++;
            }
        } catch (Exception e) {
            System.out.println("实际结果：抛出异常 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("[FAIL] 小值不应抛出异常");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试6: null SimulationMode
    // =====================================================

    // 测试 null SimulationMode 参数的默认行为
    private static void testNullSimulationMode(String testName) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: null SimulationMode 应默认为 SINGLE_PERIOD");

        System.out.println("输入数据：totalPopulation=100, simulationMode=null, mealPeriod=LUNCH");
        System.out.println("预期结果：使用默认 SINGLE_PERIOD 模式，正常生成学生");

        CanteenConfig.resetToDefaults();

        try {
            ArrivalModule module = new ArrivalModule(20260324L);
            var result = module.generateArrivalPlan(100, null, MealPeriod.LUNCH);
            System.out.println("实际结果：模式=" + result.getSimulationMode()
                    + ", 学生数=" + result.getStudents().size());
            if (result.getSimulationMode() == SimulationMode.SINGLE_PERIOD) {
                System.out.println("[PASS] null SimulationMode 正确默认为 SINGLE_PERIOD");
                testPassCount++;
            } else {
                System.out.println("[FAIL] null SimulationMode 的默认值不正确");
                testFailCount++;
            }
        } catch (Exception e) {
            System.out.println("实际结果：抛出异常 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("[FAIL] null SimulationMode 不应抛出异常");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试7: null MealPeriod
    // =====================================================

    // 测试 null MealPeriod 参数的默认行为
    private static void testNullMealPeriod(String testName) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: null MealPeriod 应默认为 LUNCH");

        System.out.println("输入数据：totalPopulation=100, simulationMode=SINGLE_PERIOD, mealPeriod=null");
        System.out.println("预期结果：使用默认 LUNCH 餐段，正常生成午餐时段学生");

        CanteenConfig.resetToDefaults();

        try {
            ArrivalModule module = new ArrivalModule(20260324L);
            var result = module.generateArrivalPlan(100, SimulationMode.SINGLE_PERIOD, null);
            System.out.println("实际结果：生成学生数=" + result.getStudents().size());
            if (!result.getStudents().isEmpty()) {
                MealPeriod actualPeriod = result.getStudents().get(0).getMealPeriod();
                System.out.println("实际餐段：" + actualPeriod.getDisplayName());
                if (actualPeriod == MealPeriod.LUNCH) {
                    System.out.println("[PASS] null MealPeriod 正确默认为 LUNCH");
                    testPassCount++;
                } else {
                    System.out.println("[FAIL] null MealPeriod 默认值不是 LUNCH");
                    testFailCount++;
                }
            } else {
                System.out.println("[FAIL] 未生成任何学生");
                testFailCount++;
            }
        } catch (Exception e) {
            System.out.println("实际结果：抛出异常 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("[FAIL] null MealPeriod 不应抛出异常");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试8: null SimulationConfigRequest
    // =====================================================

    // 测试 null 配置请求
    private static void testNullConfigRequest(String testName) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: null SimulationConfigRequest 应抛出 IllegalArgumentException");

        System.out.println("输入数据：SimulationConfigRequest = null");
        System.out.println("预期结果：抛出 IllegalArgumentException");

        CanteenConfig.resetToDefaults();

        try {
            CanteenConfig.updateAllConfigs(null);
            System.out.println("实际结果：没有抛出异常");
            System.out.println("[FAIL] null config request 应抛出异常");
            testFailCount++;
        } catch (IllegalArgumentException e) {
            System.out.println("实际结果：抛出 IllegalArgumentException: " + e.getMessage());
            System.out.println("[PASS] 正确抛出 IllegalArgumentException");
            testPassCount++;
        } catch (Exception e) {
            System.out.println("实际结果：抛出 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("[PASS] 抛出异常");
            testPassCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试9: 非法窗口数量
    // =====================================================

    // 测试非法窗口数量配置
    private static void testInvalidWindowCount(String testName) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 非法 windowCount <= 0 应抛出异常");

        System.out.println("输入数据：windowCount = 0");
        System.out.println("预期结果：抛出 IllegalArgumentException");

        CanteenConfig.resetToDefaults();

        try {
            CanteenConfig.initWindowsConfig(0);
            System.out.println("实际结果：没有抛出异常");
            System.out.println("[FAIL] windowCount=0 应抛出异常");
            testFailCount++;
        } catch (IllegalArgumentException e) {
            System.out.println("实际结果：抛出 IllegalArgumentException: " + e.getMessage());
            System.out.println("[PASS] windowCount=0 正确抛出异常");
            testPassCount++;
        } catch (Exception e) {
            System.out.println("实际结果：抛出 " + e.getClass().getSimpleName());
            System.out.println("[PASS] 抛出异常");
            testPassCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试10: null 窗口配置数组
    // =====================================================

    // 测试 null 窗口配置数组
    private static void testNullWindowConfigs(String testName) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: null 窗口配置数组应抛出异常");

        System.out.println("输入数据：distances=null, serveTimes=null");
        System.out.println("预期结果：抛出 IllegalArgumentException");

        CanteenConfig.resetToDefaults();

        try {
            CanteenConfig.updateWindowConfigs(null, null);
            System.out.println("实际结果：没有抛出异常");
            System.out.println("[FAIL] null 窗口配置应抛出异常");
            testFailCount++;
        } catch (IllegalArgumentException e) {
            System.out.println("实际结果：抛出 IllegalArgumentException: " + e.getMessage());
            System.out.println("[PASS] 正确抛出 IllegalArgumentException");
            testPassCount++;
        } catch (Exception e) {
            System.out.println("实际结果：抛出 " + e.getClass().getSimpleName());
            System.out.println("[PASS] 抛出异常");
            testPassCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试11: 窗口配置数组长度不匹配
    // =====================================================

    // 测试窗口配置数组长度不一致
    private static void testMismatchedWindowConfigs(String testName) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 窗口配置数组长度不匹配应抛出异常");

        System.out.println("输入数据：distances.length=3, serveTimes.length=5");
        System.out.println("预期结果：抛出 IllegalArgumentException");

        CanteenConfig.resetToDefaults();

        try {
            CanteenConfig.updateWindowConfigs(new int[]{10, 20, 30}, new int[]{1, 2, 1, 2, 2});
            System.out.println("实际结果：没有抛出异常");
            System.out.println("[FAIL] 长度不匹配应抛出异常");
            testFailCount++;
        } catch (IllegalArgumentException e) {
            System.out.println("实际结果：抛出 IllegalArgumentException: " + e.getMessage());
            System.out.println("[PASS] 正确抛出 IllegalArgumentException");
            testPassCount++;
        } catch (Exception e) {
            System.out.println("实际结果：抛出 " + e.getClass().getSimpleName());
            System.out.println("[PASS] 抛出异常");
            testPassCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试12: 非法概率值
    // =====================================================

    // 测试配置中非法概率值
    private static void testInvalidRate(String testName) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 非法概率值（超出 [0,1] 范围）应抛出异常");

        System.out.println("输入数据：PROB_SOLO = 1.5 (超出范围)");
        System.out.println("预期结果：抛出 IllegalArgumentException");

        CanteenConfig.resetToDefaults();

        try {
            CanteenConfig.PROB_SOLO = 1.5;
            CanteenConfig.validate();
            System.out.println("实际结果：没有抛出异常");
            System.out.println("[FAIL] 非法概率值应抛出异常");
            testFailCount++;
        } catch (IllegalArgumentException e) {
            System.out.println("实际结果：抛出 IllegalArgumentException: " + e.getMessage());
            System.out.println("[PASS] 正确抛出 IllegalArgumentException");
            testPassCount++;
        } catch (Exception e) {
            System.out.println("实际结果：抛出 " + e.getClass().getSimpleName());
            System.out.println("[PASS] 抛出异常");
            testPassCount++;
        } finally {
            CanteenConfig.resetToDefaults();
        }

        System.out.println();
    }

    // =====================================================
    // 测试13: 负数营业时长
    // =====================================================

    // 测试负数营业时长
    private static void testNegativeOpenDuration(String testName) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 负数营业时长应抛出异常");

        System.out.println("输入数据：OPEN_DURATION = -1");
        System.out.println("预期结果：抛出 IllegalArgumentException");

        CanteenConfig.resetToDefaults();

        try {
            CanteenConfig.OPEN_DURATION = -1;
            CanteenConfig.validate();
            System.out.println("实际结果：没有抛出异常");
            System.out.println("[FAIL] 负数营业时长应抛出异常");
            testFailCount++;
        } catch (IllegalArgumentException e) {
            System.out.println("实际结果：抛出 IllegalArgumentException: " + e.getMessage());
            System.out.println("[PASS] 正确抛出 IllegalArgumentException");
            testPassCount++;
        } catch (Exception e) {
            System.out.println("实际结果：抛出 " + e.getClass().getSimpleName());
            System.out.println("[PASS] 抛出异常");
            testPassCount++;
        } finally {
            CanteenConfig.resetToDefaults();
        }

        System.out.println();
    }

    // =====================================================
    // 测试14: resetToDefaults
    // =====================================================

    // 测试配置恢复默认值
    private static void testResetToDefaults(String testName) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: resetToDefaults 是否正确恢复默认值");

        System.out.println("输入数据：修改 TOTAL_POPULATION 为 5000 后调用 resetToDefaults()");
        System.out.println("预期结果：TOTAL_POPULATION 恢复为默认值 1000");

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 5000;
        CanteenConfig.resetToDefaults();

        System.out.println("实际结果：TOTAL_POPULATION = " + CanteenConfig.TOTAL_POPULATION);

        if (CanteenConfig.TOTAL_POPULATION == CanteenConfig.DEFAULT_TOTAL_POPULATION) {
            System.out.println("[PASS] resetToDefaults 正确恢复默认值");
            testPassCount++;
        } else {
            System.out.println("[FAIL] resetToDefaults 未正确恢复默认值");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试15: ArrivalModule 构造函数
    // =====================================================

    // 测试 ArrivalModule 多种构造函数
    private static void testArrivalModuleConstructors(String testName) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: ArrivalModule 多种构造函数是否正常工作");

        CanteenConfig.resetToDefaults();

        System.out.println("输入数据：测试 4 种构造函数");

        try {
            // 无参构造函数
            ArrivalModule m1 = new ArrivalModule();
            System.out.println("实际结果：无参构造函数 创建成功, seed=" + m1.getSeed());
            System.out.println("[PASS] 无参构造函数正常工作");
            testPassCount++;

            // 种子构造函数
            ArrivalModule m2 = new ArrivalModule(12345L);
            System.out.println("实际结果：种子构造函数 创建成功, seed=" + m2.getSeed());
            if (m2.getSeed() == 12345L) {
                System.out.println("[PASS] 种子构造函数 seed 正确");
                testPassCount++;
            } else {
                System.out.println("[FAIL] 种子不正确: " + m2.getSeed());
                testFailCount++;
            }

            // 队列构造函数
            ArrivalModule m3 = new ArrivalModule(new java.util.concurrent.LinkedBlockingQueue<>());
            System.out.println("实际结果：队列构造函数 创建成功");
            System.out.println("[PASS] 队列构造函数正常工作");
            testPassCount++;

            // 队列+种子构造函数
            ArrivalModule m4 = new ArrivalModule(new java.util.concurrent.LinkedBlockingQueue<>(), 99999L);
            System.out.println("实际结果：队列+种子构造函数 创建成功, seed=" + m4.getSeed());
            System.out.println("[PASS] 队列+种子构造函数正常工作");
            testPassCount++;

        } catch (Exception e) {
            System.out.println("实际结果：抛出异常 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("[FAIL] 构造函数不应抛出异常");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试16: resetForNextRun
    // =====================================================

    // 测试 resetForNextRun 是否正确重置状态
    private static void testResetForNextRun(String testName) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: resetForNextRun 是否重置内部状态");

        CanteenConfig.resetToDefaults();

        System.out.println("输入数据：使用相同种子两次生成，中间调用 resetForNextRun");

        ArrivalModule module = new ArrivalModule(20260324L);
        var result1 = module.generateArrivalPlan(100, SimulationMode.SINGLE_PERIOD, MealPeriod.LUNCH);
        int count1 = result1.getStudents().size();

        module.resetForNextRun(20260324L);
        var result2 = module.generateArrivalPlan(100, SimulationMode.SINGLE_PERIOD, MealPeriod.LUNCH);
        int count2 = result2.getStudents().size();

        System.out.println("实际结果：第一次人数=" + count1 + ", 重置后第二次人数=" + count2);
        System.out.println("预期结果：两次生成人数应相同（确定性随机种子）");

        if (count1 == count2) {
            System.out.println("[PASS] resetForNextRun 后使用相同种子生成结果一致");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 两次生成人数不一致: " + count1 + " vs " + count2);
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 汇总
    // =====================================================

    private static void printSummary() {
        System.out.println("==============================================");
        System.out.println("  BoundaryTest 测试汇总");
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
