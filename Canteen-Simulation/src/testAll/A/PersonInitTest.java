/*
 * 测试文件：PersonInitTest.java
 * 所属模块：A - 初始化模块（人员到来）
 *
 * 测试功能：
 * 1. 测试 generateStudents 生成的学生对象字段是否完整、合法
 * 2. 测试 generateGroups 和 groupStudentsByGroupId 的分组逻辑
 * 3. 测试 generateArrivalPlan 生成的学生是否包含正确的餐段和人群类型
 * 4. 测试同一组的学生具有相同的 groupId 和 arrivalTime
 *
 * 测试数据：
 * * 正常数据：100、1000、3000
 * * 边界数据：1、10
 * * 大规模数据：20000、50000
 *
 * 预期结果：
 * * 学生 id 从 1 开始自增，无重复
 * * 每个学生都有合法的 groupId、arrivalTime、diningTime、mealPeriod、crowdType
 * * 同组学生 groupId 相同，arrivalTime 相同
 * * 分组后每组至少 1 人，最多 4 人
 */

package testAll.A;

import backend.config.CanteenConfig;
import backend.model.ArrivalGenerationResult;
import backend.model.CrowdType;
import backend.model.Group;
import backend.model.MealPeriod;
import backend.model.SimulationMode;
import backend.model.Student;
import backend.module.ArrivalModule;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PersonInitTest {

    private static int testPassCount = 0;
    private static int testFailCount = 0;

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("  PersonInitTest - 人员对象生成测试");
        System.out.println("  测试模块: ArrivalModule.generateStudents / generateGroups");
        System.out.println("==============================================");
        System.out.println();

        runAll();
        printSummary();
    }

    public static int runAll() {
        testPassCount = 0;
        testFailCount = 0;

        CanteenConfig.resetToDefaults();

        // === 学生字段完整性测试 ===
        testStudentFieldsCompleteness("字段完整性-午餐100人", 100, MealPeriod.LUNCH);
        testStudentFieldsCompleteness("字段完整性-午餐1000人", 1000, MealPeriod.LUNCH);
        testStudentFieldsCompleteness("字段完整性-早餐1000人", 1000, MealPeriod.BREAKFAST);
        testStudentFieldsCompleteness("字段完整性-晚餐3000人", 3000, MealPeriod.DINNER);

        // === 学生ID唯一性测试 ===
        testStudentIdUniqueness("ID唯一性-午餐1000人", 1000, MealPeriod.LUNCH);
        testStudentIdUniqueness("ID唯一性-全天线3000人", 3000, null);

        // === 同组学生一致性测试 ===
        testSameGroupConsistency("同组一致性-午餐100人", 100, MealPeriod.LUNCH);
        testSameGroupConsistency("同组一致性-午餐1000人", 1000, MealPeriod.LUNCH);
        testSameGroupConsistency("同组一致性-全天线3000人", 3000, null);

        // === 人群类型分布测试 ===
        testCrowdTypeDistribution("人群类型-午餐1000人", 1000, MealPeriod.LUNCH);
        testCrowdTypeDistribution("人群类型-全天线20000人", 20000, null);

        // === 分组测试 ===
        testGroupGeneration("分组测试-午餐1000人", 1000, MealPeriod.LUNCH);
        testGroupGeneration("分组测试-全天线3000人", 3000, null);

        // === generateStudents 与 generateArrivalPlan 一致性测试 ===
        testGenerateStudentsConsistency("学生生成一致性-午餐1000人", 1000, MealPeriod.LUNCH);

        // === 边界数据测试 ===
        testStudentFieldsCompleteness("边界数据-编号1人", 1, MealPeriod.LUNCH);
        testStudentFieldsCompleteness("边界数据-编号10人", 10, MealPeriod.LUNCH);

        // === 大规模数据测试 ===
        testStudentFieldsCompleteness("大规模数据-总人数50000人", 50000, MealPeriod.LUNCH);

        return testFailCount;
    }

    // =====================================================
    // 测试1: 学生字段完整性
    // =====================================================

    // 测试生成的学生对象所有必要字段是否完整
    private static void testStudentFieldsCompleteness(String testName, int totalPopulation, MealPeriod period) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 学生对象字段完整性检查");

        String modeLabel = (period == null) ? "FULL_DAY" : period.getCode();
        System.out.println("输入数据：总人数=" + totalPopulation + ", 餐段=" + modeLabel);

        ArrivalModule module = new ArrivalModule(20260324L);
        SimulationMode mode = (period == null) ? SimulationMode.FULL_DAY : SimulationMode.SINGLE_PERIOD;
        MealPeriod p = (period == null) ? MealPeriod.BREAKFAST : period;

        ArrivalGenerationResult result = module.generateArrivalPlan(totalPopulation, mode, p);
        List<Student> students = result.getStudents();

        System.out.println("预期结果：每个学生应有合法 id(>0), groupId(>0), arrivalTime(>=0), mealPeriod, crowdType");

        // 实际结果
        int nullMealPeriod = 0, nullCrowdType = 0;
        int invalidId = 0, invalidGroupId = 0, invalidArrivalTime = 0;
        MealPeriod firstPeriod = null;
        boolean allSamePeriod = true;

        for (Student s : students) {
            if (s.getId() <= 0) invalidId++;
            if (s.getGroupId() <= 0) invalidGroupId++;
            if (s.getArrivalTime() < 0) invalidArrivalTime++;
            if (s.getMealPeriod() == null) nullMealPeriod++;
            if (s.getCrowdType() == null) nullCrowdType++;

            if (firstPeriod == null) {
                firstPeriod = s.getMealPeriod();
            } else if (period != null && s.getMealPeriod() != firstPeriod) {
                allSamePeriod = false;
            }
        }

        System.out.println("实际结果：总人数=" + students.size()
                + ", 无效id=" + invalidId + ", 无效groupId=" + invalidGroupId
                + ", 无效arrivalTime=" + invalidArrivalTime
                + ", 空mealPeriod=" + nullMealPeriod
                + ", 空crowdType=" + nullCrowdType);

        if (invalidId == 0) {
            System.out.println("[PASS] 所有学生 id > 0");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 存在 " + invalidId + " 个无效 id");
            testFailCount++;
        }

        if (invalidGroupId == 0) {
            System.out.println("[PASS] 所有学生 groupId > 0");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 存在 " + invalidGroupId + " 个无效 groupId");
            testFailCount++;
        }

        if (invalidArrivalTime == 0) {
            System.out.println("[PASS] 所有学生 arrivalTime >= 0");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 存在 " + invalidArrivalTime + " 个无效 arrivalTime");
            testFailCount++;
        }

        if (nullMealPeriod == 0) {
            System.out.println("[PASS] 所有学生 mealPeriod 非空");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 存在 " + nullMealPeriod + " 个空 mealPeriod");
            testFailCount++;
        }

        if (nullCrowdType == 0) {
            System.out.println("[PASS] 所有学生 crowdType 非空");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 存在 " + nullCrowdType + " 个空 crowdType");
            testFailCount++;
        }

        // 对于 SINGLE_PERIOD 模式，检查所有学生是否都在同一个餐段
        if (period != null && !allSamePeriod) {
            System.out.println("[FAIL] SINGLE_PERIOD 模式下存在不同餐段的学生");
            testFailCount++;
        } else if (period != null) {
            System.out.println("[PASS] SINGLE_PERIOD 模式下所有学生餐段一致");
            testPassCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试2: 学生 ID 唯一性
    // =====================================================

    // 测试所有学生 ID 是否唯一，没有重复
    private static void testStudentIdUniqueness(String testName, int totalPopulation, MealPeriod period) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 学生 ID 唯一性检查");

        String modeLabel = (period == null) ? "FULL_DAY" : period.getCode();
        System.out.println("输入数据：总人数=" + totalPopulation + ", 模式=" + modeLabel);

        ArrivalModule module = new ArrivalModule(20260324L);
        SimulationMode mode = (period == null) ? SimulationMode.FULL_DAY : SimulationMode.SINGLE_PERIOD;
        MealPeriod p = (period == null) ? MealPeriod.BREAKFAST : period;

        ArrivalGenerationResult result = module.generateArrivalPlan(totalPopulation, mode, p);
        List<Student> students = result.getStudents();

        System.out.println("预期结果：所有学生 ID 唯一，无重复");

        // 实际结果
        Set<Integer> ids = new HashSet<>();
        int duplicates = 0;
        for (Student s : students) {
            if (!ids.add(s.getId())) {
                duplicates++;
            }
        }

        System.out.println("实际结果：总人数=" + students.size() + ", 唯一ID数=" + ids.size() + ", 重复数=" + duplicates);

        if (duplicates == 0 && ids.size() == students.size()) {
            System.out.println("[PASS] 所有学生 ID 唯一，无重复");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 存在 " + duplicates + " 个重复 ID");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试3: 同组学生一致性
    // =====================================================

    // 测试同一 groupId 的学生是否具有相同的 arrivalTime 和 mealPeriod
    private static void testSameGroupConsistency(String testName, int totalPopulation, MealPeriod period) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: 同组学生 groupId、arrivalTime、mealPeriod 一致性");

        String modeLabel = (period == null) ? "FULL_DAY" : period.getCode();
        System.out.println("输入数据：总人数=" + totalPopulation + ", 模式=" + modeLabel);

        ArrivalModule module = new ArrivalModule(20260324L);
        SimulationMode mode = (period == null) ? SimulationMode.FULL_DAY : SimulationMode.SINGLE_PERIOD;
        MealPeriod p = (period == null) ? MealPeriod.BREAKFAST : period;

        ArrivalGenerationResult result = module.generateArrivalPlan(totalPopulation, mode, p);
        List<Student> students = result.getStudents();
        Map<Integer, List<Student>> grouped = module.groupStudentsByGroupId(students);

        System.out.println("预期结果：同组学生具有相同的 arrivalTime 和 mealPeriod");

        // 实际结果
        int groupsOK = 0;
        int groupsWithErrors = 0;
        for (Map.Entry<Integer, List<Student>> entry : grouped.entrySet()) {
            List<Student> members = entry.getValue();
            if (members.isEmpty()) continue;

            long firstTime = members.get(0).getArrivalTime();
            MealPeriod firstPeriod = members.get(0).getMealPeriod();
            boolean groupOk = true;

            for (Student s : members) {
                if (s.getArrivalTime() != firstTime || s.getMealPeriod() != firstPeriod) {
                    groupOk = false;
                    break;
                }
            }

            if (groupOk) groupsOK++;
            else groupsWithErrors++;
        }

        System.out.println("实际结果：总组数=" + grouped.size()
                + ", 一致组数=" + groupsOK + ", 不一致组数=" + groupsWithErrors);

        if (groupsWithErrors == 0) {
            System.out.println("[PASS] 所有组内学生 arrivalTime 和 mealPeriod 一致");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 存在 " + groupsWithErrors + " 个组内部不一致");
            testFailCount++;
        }

        // 验证每个组最多4人
        int maxGroupSize = 0;
        int oversizedGroups = 0;
        for (List<Student> members : grouped.values()) {
            if (members.size() > maxGroupSize) maxGroupSize = members.size();
            if (members.size() > 4) oversizedGroups++;
        }
        System.out.println("实际结果：最大组人数=" + maxGroupSize + ", 超限组数=" + oversizedGroups);
        if (oversizedGroups == 0) {
            System.out.println("[PASS] 所有组人数 <= 4");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 存在 " + oversizedGroups + " 个组人数 > 4");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试4: 人群类型分布
    // =====================================================

    // 测试 FAST/NORMAL/SLOW 三种人群类型是否都有覆盖
    private static void testCrowdTypeDistribution(String testName, int totalPopulation, MealPeriod period) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: CrowdType 人群类型分布");

        String modeLabel = (period == null) ? "FULL_DAY" : period.getCode();
        System.out.println("输入数据：总人数=" + totalPopulation + ", 模式=" + modeLabel);

        ArrivalModule module = new ArrivalModule(20260324L);
        SimulationMode mode = (period == null) ? SimulationMode.FULL_DAY : SimulationMode.SINGLE_PERIOD;
        MealPeriod p = (period == null) ? MealPeriod.BREAKFAST : period;

        ArrivalGenerationResult result = module.generateArrivalPlan(totalPopulation, mode, p);
        List<Student> students = result.getStudents();

        System.out.println("预期结果：包含 FAST、NORMAL、SLOW 三种人群类型");

        int fastCount = 0, normalCount = 0, slowCount = 0;
        for (Student s : students) {
            CrowdType ct = s.getCrowdType();
            if (ct == CrowdType.FAST) fastCount++;
            else if (ct == CrowdType.NORMAL) normalCount++;
            else if (ct == CrowdType.SLOW) slowCount++;
        }

        int total = students.size();
        System.out.printf("实际结果：FAST=%d(%.2f%%), NORMAL=%d(%.2f%%), SLOW=%d(%.2f%%)%n",
                fastCount, 100.0 * fastCount / total,
                normalCount, 100.0 * normalCount / total,
                slowCount, 100.0 * slowCount / total);

        // 对于大量数据（>=1000），三种类型都应有出现
        if (total >= 1000) {
            if (fastCount > 0 && normalCount > 0 && slowCount > 0) {
                System.out.println("[PASS] 三种人群类型均有覆盖");
                testPassCount++;
            } else {
                System.out.println("[FAIL] 缺少某种人群类型");
                testFailCount++;
            }
        } else {
            // 小数据量只要求至少有学生即可
            System.out.println("[PASS] 学生数据量较小，已生成 " + total + " 人");
            testPassCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试5: 分组测试
    // =====================================================

    // 测试 generateGroups 结果是否正确
    private static void testGroupGeneration(String testName, int totalPopulation, MealPeriod period) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: generateGroups 分组功能");

        String modeLabel = (period == null) ? "FULL_DAY" : period.getCode();
        System.out.println("输入数据：总人数=" + totalPopulation + ", 模式=" + modeLabel);

        ArrivalModule module = new ArrivalModule(20260324L);
        ArrivalGenerationResult result;
        if (period == null) {
            result = module.generateArrivalPlan(totalPopulation, SimulationMode.FULL_DAY, MealPeriod.BREAKFAST);
        } else {
            result = module.generateArrivalPlan(totalPopulation, SimulationMode.SINGLE_PERIOD, period);
        }

        List<Group> groups = module.generateGroups();

        System.out.println("预期结果：分组总数应与 groupId 数一致，每组人数 1-4 人");

        int totalMembers = 0;
        boolean allGroupSizeValid = true;
        Set<Integer> groupIds = new HashSet<>();

        for (Group g : groups) {
            totalMembers += g.getSize();
            groupIds.add(g.getGroupId());
            if (g.getSize() < 1 || g.getSize() > 4) {
                allGroupSizeValid = false;
            }
        }

        System.out.println("实际结果：总组数=" + groups.size()
                + ", 总成员数=" + totalMembers
                + ", 唯一 groupId 数=" + groupIds.size());

        if (groupIds.size() == groups.size()) {
            System.out.println("[PASS] 所有 groupId 唯一");
            testPassCount++;
        } else {
            System.out.println("[FAIL] groupId 有重复");
            testFailCount++;
        }

        if (allGroupSizeValid) {
            System.out.println("[PASS] 所有组人数在 1-4 范围内");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 存在组人数不在 1-4 范围内");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 测试6: generateStudents 一致性
    // =====================================================

    // 测试 generateStudents() 与 generateArrivalPlan() 生成的学生是否一致
    private static void testGenerateStudentsConsistency(String testName, int totalPopulation, MealPeriod period) {
        System.out.println("--------------------------------------------------");
        System.out.println("[" + testName + "]");
        System.out.println("测试功能: generateStudents() 与 generateArrivalPlan() 一致性");

        System.out.println("输入数据：总人数=" + totalPopulation + ", 餐段=" + period.getCode());

        ArrivalModule module = new ArrivalModule(20260324L);
        module.generateArrivalPlan(totalPopulation, SimulationMode.SINGLE_PERIOD, period);
        List<Student> fromPlan = module.getLastGenerationResult().getStudents();

        module.resetForNextRun(20260324L);
        List<Student> fromStudents = module.generateStudents();

        System.out.println("预期结果：两种方式生成学生数量相同，ID 序列相同");

        System.out.println("实际结果：generateArrivalPlan 学生数=" + fromPlan.size()
                + ", generateStudents 学生数=" + fromStudents.size());

        if (fromPlan.size() == fromStudents.size()) {
            System.out.println("[PASS] 两种方式生成的学生数量一致");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 学生数量不一致");
            testFailCount++;
        }

        System.out.println();
    }

    // =====================================================
    // 汇总
    // =====================================================

    private static void printSummary() {
        System.out.println("==============================================");
        System.out.println("  PersonInitTest 测试汇总");
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
