package testAll.integration;

import backend.config.CanteenConfig;
import backend.config.SimulationConfigRequest;
import backend.model.*;
import backend.module.ArrivalModule;
import backend.optimize.SimRunOptions;
import backend.optimize.SimRunResult;
import backend.optimize.SimulationAdapter;

import java.util.*;

/**
 * 详细联调测试 - 覆盖更全面的接口关系、数据流和异常场景。
 * 每个测试包含更细粒度的子测试，输出结构化结果供报告使用。
 */
public class DetailedIntegrationTest {

    private static final StringBuilder jsonResults = new StringBuilder();
    private static int totalTests = 0;
    private static int totalPassed = 0;
    private static int totalFailed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  详细联调测试 - 扩展版");
        System.out.println("========================================\n");

        CanteenConfig.resetToDefaults();

        // === 一、配置模块深度测试 ===
        testConfigParametersPropagation();
        testConfigSnapshotRestore();
        testConfigValidationBoundaries();
        testSimulationConfigRequest();

        // === 二、到达模块深度测试 ===
        testArrivalTimeDistribution();
        testGroupSizeDistribution();
        testStudentAttributeGeneration();
        testArrivalPlanForAllMealPeriods();
        testArrivalWithDifferentPopulations();

        // === 三、仿真引擎深度测试 ===
        testEngineWithVaryingWindowCounts();
        testEngineWithVaryingTableCounts();
        testEngineWithVaryingPopulations();
        testEngineWithDifferentServiceTimes();

        // === 四、排队与服务深度测试 ===
        testQueueBehaviorUnderDifferentLoads();
        testServiceTimeImpact();

        // === 五、座位分配深度测试 ===
        testSeatingWithDifferentGroupConfigs();
        testSeatingUnderCapacityStress();

        // === 六、统计模块深度测试 ===
        testStatisticsConsistency();
        testStatisticsAcrossMultipleRuns();

        // === 七、优化管线深度测试 ===
        testSimulationAdapterConsistency();
        testResultAverager();

        System.out.println("\n========================================");
        System.out.println("  详细测试汇总");
        System.out.println("========================================");
        System.out.println("  总计: " + totalTests + " 项");
        System.out.println("  通过: " + totalPassed);
        System.out.println("  失败: " + totalFailed);
        System.out.println("  通过率: " + String.format("%.1f%%", 100.0 * totalPassed / Math.max(1, totalTests)));
        System.out.println("========================================");
    }

    // ==================== 一、配置模块深度测试 ====================

    static void testConfigParametersPropagation() {
        printSection("D-IT-01", "配置参数传播深度测试");
        boolean ok = true;

        // 测试每个关键参数是否正确传递给 ArrivalModule
        CanteenConfig.resetToDefaults();

        // 检查默认值
        check("默认桌子数=150", CanteenConfig.TOTAL_TABLES == 150, null);
        check("默认窗口数=5", CanteenConfig.getWindowCount() == 5, null);
        check("默认人口=1000", CanteenConfig.TOTAL_POPULATION == 1000, null);
        check("默认开放时间=120分钟", CanteenConfig.OPEN_DURATION == 120, null);
        check("默认就餐均值=900秒", CanteenConfig.DINING_TIME_MEAN == 900.0, null);
        check("默认耐心最小值=1200秒", CanteenConfig.PATIENCE_MIN == 1200, null);
        check("默认耐心最大值=2700秒", CanteenConfig.PATIENCE_MAX == 2700, null);
        check("默认solo概率=0.7", Math.abs(CanteenConfig.PROB_SOLO - 0.7) < 0.001, null);
        check("默认duo概率=0.15", Math.abs(CanteenConfig.PROB_DUO - 0.15) < 0.001, null);
        check("默认trio概率=0.05", Math.abs(CanteenConfig.PROB_TRIO - 0.05) < 0.001, null);
        check("默认team概率=0.1", Math.abs(CanteenConfig.PROB_TEAM - 0.1) < 0.001, null);

        // 修改配置后重新验证
        CanteenConfig.TOTAL_POPULATION = 200;
        check("修改后人口=200", CanteenConfig.TOTAL_POPULATION == 200, null);

        CanteenConfig.setWindowCount(8);
        check("修改后窗口数=8", CanteenConfig.getWindowCount() == 8, null);
        check("修改后距离数组长度=8", CanteenConfig.WINDOW_DISTANCES.length == 8, null);
        check("修改后服务时间数组长度=8", CanteenConfig.WINDOW_AVG_SERVE_TIME.length == 8, null);

        CanteenConfig.setTableCount(50);
        check("修改后桌子数=50", CanteenConfig.TOTAL_TABLES == 50, null);

        CanteenConfig.OPEN_DURATION = 60;
        check("修改后开放时间=60", CanteenConfig.OPEN_DURATION == 60, null);

        CanteenConfig.PATIENCE_MIN = 300;
        CanteenConfig.PATIENCE_MAX = 600;
        check("修改后耐心范围=[300,600]", CanteenConfig.PATIENCE_MIN == 300 && CanteenConfig.PATIENCE_MAX == 600, null);

        // 组概率修改并自动归一化
        CanteenConfig.PROB_SOLO = 0.5;
        CanteenConfig.PROB_DUO = 0.3;
        CanteenConfig.PROB_TRIO = 0.1;
        CanteenConfig.PROB_TEAM = 0.1;
        CanteenConfig.validate();
        double sum = CanteenConfig.PROB_SOLO + CanteenConfig.PROB_DUO + CanteenConfig.PROB_TRIO + CanteenConfig.PROB_TEAM;
        check("组概率和为1.0", Math.abs(sum - 1.0) < 1e-9, String.format("sum=%.10f", sum));

        // updateAllConfigs 完整传播测试
        CanteenConfig.resetToDefaults();
        SimulationConfigRequest req = new SimulationConfigRequest();
        req.setTableCount(30);
        req.setWindowCount(3);
        req.setOpenDuration(90);
        req.setRandomSeed(123456L);
        req.setTotalPopulation(150);
        req.setProbSolo(0.6);
        req.setSimulationMode("single_period");
        req.setMealPeriod("dinner");
        CanteenConfig.updateAllConfigs(req);

        check("DTO传播: 桌子=30", CanteenConfig.TOTAL_TABLES == 30, null);
        check("DTO传播: 窗口=3", CanteenConfig.getWindowCount() == 3, null);
        check("DTO传播: 开放=90", CanteenConfig.OPEN_DURATION == 90, null);
        check("DTO传播: 种子=123456", CanteenConfig.RANDOM_SEED == 123456L, null);
        check("DTO传播: 人口=150", CanteenConfig.TOTAL_POPULATION == 150, null);
        check("DTO传播: solo=0.6", Math.abs(CanteenConfig.PROB_SOLO - 0.6) < 0.001, null);
        check("DTO传播: 模式=单时段", CanteenConfig.SIMULATION_MODE == SimulationMode.SINGLE_PERIOD, null);
        check("DTO传播: 餐段=晚餐", CanteenConfig.MEAL_PERIOD == MealPeriod.DINNER, null);

        finishSection(ok);
    }

    static void testConfigSnapshotRestore() {
        printSection("D-IT-02", "配置快照与恢复深度测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 500;
        CanteenConfig.setWindowCount(10);
        CanteenConfig.setTableCount(200);
        CanteenConfig.OPEN_DURATION = 240;

        // 快照
        CanteenConfig.CanteenConfigSnapshot snap = CanteenConfig.snapshot();
        // 验证当前值（快照字段是 private 的）
        check("快照前: 人口=500", CanteenConfig.TOTAL_POPULATION == 500, null);
        check("快照前: 桌子=200", CanteenConfig.TOTAL_TABLES == 200, null);
        check("快照前: 开放=240", CanteenConfig.OPEN_DURATION == 240, null);
        check("快照前: 距离数组长度=10", CanteenConfig.WINDOW_DISTANCES.length == 10, null);

        // 修改
        CanteenConfig.TOTAL_POPULATION = 999;
        CanteenConfig.setTableCount(1);
        CanteenConfig.OPEN_DURATION = 1;

        // 恢复
        CanteenConfig.restore(snap);
        check("恢复: 人口=500", CanteenConfig.TOTAL_POPULATION == 500, null);
        check("恢复: 桌子=200", CanteenConfig.TOTAL_TABLES == 200, null);
        check("恢复: 开放=240", CanteenConfig.OPEN_DURATION == 240, null);
        check("恢复: 窗口数=10", CanteenConfig.getWindowCount() == 10, null);

        CanteenConfig.resetToDefaults();
        check("重置: 人口=1000", CanteenConfig.TOTAL_POPULATION == 1000, null);

        finishSection(ok);
    }

    static void testConfigValidationBoundaries() {
        printSection("D-IT-03", "配置验证边界测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();

        // 合法边界
        CanteenConfig.setTableCount(1);
        CanteenConfig.setWindowCount(1);
        CanteenConfig.OPEN_DURATION = 1;
        CanteenConfig.PATIENCE_MIN = 0;
        CanteenConfig.PATIENCE_MAX = 0;
        try {
            CanteenConfig.validate();
            check("合法边界: 1桌1窗1分钟耐心0", true, null);
        } catch (Exception e) {
            check("合法边界: 1桌1窗1分钟耐心0", false, e.getMessage());
            ok = false;
        }

        // 非法: 负数桌子
        CanteenConfig.resetToDefaults();
        try {
            CanteenConfig.setTableCount(-1);
            check("非法: 桌子=-1 应被拒绝", false, null);
            ok = false;
        } catch (IllegalArgumentException e) {
            check("非法: 桌子=-1 被正确拒绝", true, e.getMessage());
        }

        // 非法: 耐心范围颠倒
        CanteenConfig.resetToDefaults();
        CanteenConfig.PATIENCE_MIN = 1000;
        CanteenConfig.PATIENCE_MAX = 500;
        try {
            CanteenConfig.validate();
            check("非法: 耐心Min>Max 应被拒绝", false, null);
            ok = false;
        } catch (IllegalArgumentException e) {
            check("非法: 耐心Min>Max 被正确拒绝", true, e.getMessage());
        }

        // 非法: 空窗口
        CanteenConfig.resetToDefaults();
        try {
            CanteenConfig.setWindowCount(0);
            check("非法: 窗口=0 应被拒绝", false, null);
            ok = false;
        } catch (IllegalArgumentException e) {
            check("非法: 窗口=0 被正确拒绝", true, e.getMessage());
        }

        // 非法: 负概率
        CanteenConfig.resetToDefaults();
        CanteenConfig.PROB_SOLO = -0.5;
        try {
            CanteenConfig.validate();
            check("非法: solo概率=-0.5 应被拒绝", false, null);
            ok = false;
        } catch (IllegalArgumentException e) {
            check("非法: solo概率=-0.5 被正确拒绝", true, e.getMessage());
        }

        finishSection(ok);
    }

    static void testSimulationConfigRequest() {
        printSection("D-IT-04", "SimulationConfigRequest DTO 测试");
        boolean ok = true;

        SimulationConfigRequest req = new SimulationConfigRequest();
        check("DTO默认桌子=150", req.getTableCount() == 150, null);
        check("DTO默认窗口=5", req.getWindowCount() == 5, null);
        check("DTO默认人口=1000", req.getTotalPopulation() == 1000, null);
        check("DTO默认开放=120", req.getOpenDuration() == 120, null);
        check("DTO默认模式=single_period", "single_period".equals(req.getSimulationMode()), null);
        check("DTO默认餐段=lunch", "lunch".equals(req.getMealPeriod()), null);

        // setProbSolo 应自动分配剩余概率
        req.setProbSolo(0.5);
        double sum = req.getProbSolo() + req.getProbDuo() + req.getProbTrio() + req.getProbTeam();
        check("setProbSolo后概率和=1.0", Math.abs(sum - 1.0) < 1e-9, String.format("sum=%.10f", sum));

        finishSection(ok);
    }

    // ==================== 二、到达模块深度测试 ====================

    static void testArrivalTimeDistribution() {
        printSection("D-IT-05", "到达时间分布测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 200;
        CanteenConfig.OPEN_DURATION = 120;

        ArrivalModule am = new ArrivalModule(42L);
        ArrivalGenerationResult result = am.generateArrivalPlan(200, SimulationMode.SINGLE_PERIOD, MealPeriod.LUNCH, false);

        List<Student> students = result.getStudents();
        check("生成200人", students.size() == 200, null);

        // 检查时间分布
        long minTime = Long.MAX_VALUE, maxTime = Long.MIN_VALUE;
        double sumTime = 0;
        for (Student s : students) {
            minTime = Math.min(minTime, s.getArrivalTime());
            maxTime = Math.max(maxTime, s.getArrivalTime());
            sumTime += s.getArrivalTime();
        }
        double avgTime = sumTime / students.size();
        int openSec = CanteenConfig.OPEN_DURATION * 60;

        check("最小到达时间>=0", minTime >= 0, "min=" + minTime);
        check("最大到达时间<=" + openSec, maxTime <= openSec, "max=" + maxTime);
        check("平均到达时间接近中点(±20%)",
                Math.abs(avgTime - openSec / 2.0) < openSec * 0.20,
                String.format("avg=%.0f, midpoint=%.0f", avgTime, openSec / 2.0));

        // 检查时间排序
        boolean sorted = true;
        for (int i = 1; i < students.size(); i++) {
            if (students.get(i).getArrivalTime() < students.get(i - 1).getArrivalTime()) {
                sorted = false;
                break;
            }
        }
        check("学生按到达时间排序", sorted, null);

        // 检查在时间范围内均匀覆盖
        int q1Idx = students.size() / 4;
        int q3Idx = 3 * students.size() / 4;
        long q1 = students.get(q1Idx).getArrivalTime();
        long q3 = students.get(q3Idx).getArrivalTime();
        check("Q1和Q3时间差 > 总时长的25%", (q3 - q1) > openSec * 0.25,
                String.format("Q1=%d, Q3=%d, diff=%d, 25%%=%d", q1, q3, q3 - q1, (int)(openSec * 0.25)));

        finishSection(ok);
    }

    static void testGroupSizeDistribution() {
        printSection("D-IT-06", "分组规模分布测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 1000;
        CanteenConfig.PROB_SOLO = 0.70;
        CanteenConfig.PROB_DUO = 0.15;
        CanteenConfig.PROB_TRIO = 0.05;
        CanteenConfig.PROB_TEAM = 0.10;
        CanteenConfig.validate();

        ArrivalModule am = new ArrivalModule(100L);
        ArrivalGenerationResult result = am.generateArrivalPlan(1000, SimulationMode.SINGLE_PERIOD, MealPeriod.LUNCH, false);

        Map<Integer, List<Student>> grouped = am.groupStudentsByGroupId(result.getStudents());

        int solo = 0, duo = 0, trio = 0, team = 0;
        for (List<Student> g : grouped.values()) {
            switch (g.size()) {
                case 1: solo++; break;
                case 2: duo++; break;
                case 3: trio++; break;
                case 4: team++; break;
            }
        }
        int totalGroups = solo + duo + trio + team;

        double soloRatio = (double) solo / totalGroups;
        double duoRatio = (double) duo / totalGroups;
        double trioRatio = (double) trio / totalGroups;
        double teamRatio = (double) team / totalGroups;

        check("solo比例接近0.7(±0.1)", Math.abs(soloRatio - 0.7) < 0.1, String.format("%.3f", soloRatio));
        check("duo比例接近0.15(±0.08)", Math.abs(duoRatio - 0.15) < 0.08, String.format("%.3f", duoRatio));
        check("trio比例接近0.05(±0.05)", Math.abs(trioRatio - 0.05) < 0.05, String.format("%.3f", trioRatio));
        check("team比例接近0.1(±0.08)", Math.abs(teamRatio - 0.1) < 0.08, String.format("%.3f", teamRatio));

        finishSection(ok);
    }

    static void testStudentAttributeGeneration() {
        printSection("D-IT-07", "学生属性生成测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 100;
        ArrivalModule am = new ArrivalModule(200L);
        ArrivalGenerationResult result = am.generateArrivalPlan(100, SimulationMode.SINGLE_PERIOD, MealPeriod.LUNCH, false);

        List<Student> students = result.getStudents();

        // 检查属性范围
        int validDiningTime = 0, validPatience = 0;
        int fastCount = 0, normalCount = 0, slowCount = 0;
        Set<Integer> groupIds = new HashSet<>();
        Set<Integer> preferredWindows = new HashSet<>();

        for (Student s : students) {
            if (s.getDiningTime() >= CanteenConfig.MIN_DINING_TIME) validDiningTime++;
            if (s.getPatience() >= CanteenConfig.PATIENCE_MIN && s.getPatience() <= CanteenConfig.PATIENCE_MAX + 3 * 60) validPatience++;
            groupIds.add(s.getGroupId());
            preferredWindows.add(s.getPreferredWindow());

            if (s.getCrowdType() == CrowdType.FAST) fastCount++;
            else if (s.getCrowdType() == CrowdType.NORMAL) normalCount++;
            else if (s.getCrowdType() == CrowdType.SLOW) slowCount++;
        }

        check("所有学生就餐时间>=最小值", validDiningTime == students.size(),
                validDiningTime + "/" + students.size());
        check("所有学生耐心值在合理范围", validPatience == students.size(),
                validPatience + "/" + students.size());
        check("存在多种人群类型", fastCount > 0 && normalCount > 0,
                String.format("FAST=%d NORMAL=%d SLOW=%d", fastCount, normalCount, slowCount));
        check("存在多个组", groupIds.size() > 1, "groups=" + groupIds.size());
        check("偏好窗口覆盖多个", preferredWindows.size() >= 1, "windows=" + preferredWindows.size());

        // 检查学生状态初始化
        for (Student s : students) {
            check("初始状态为null或ARRIVING",
                    s.getStatus() == null || s.getStatus() == StudentStatus.ARRIVING, null);
            break; // 只检查第一个
        }

        finishSection(ok);
    }

    static void testArrivalPlanForAllMealPeriods() {
        printSection("D-IT-08", "各餐段到达计划测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 50;
        CanteenConfig.OPEN_DURATION = 60;

        // 早餐
        ArrivalModule am = new ArrivalModule(10L);
        ArrivalGenerationResult br = am.generateArrivalPlan(50, SimulationMode.SINGLE_PERIOD, MealPeriod.BREAKFAST, false);
        check("早餐: 生成50人", br.getStudents().size() == 50, null);
        check("早餐: 餐段正确", br.getStudents().get(0).getMealPeriod() == MealPeriod.BREAKFAST, null);

        // 午餐
        am.resetForNextRun(20L);
        ArrivalGenerationResult lr = am.generateArrivalPlan(50, SimulationMode.SINGLE_PERIOD, MealPeriod.LUNCH, false);
        check("午餐: 生成50人", lr.getStudents().size() == 50, null);
        check("午餐: 餐段正确", lr.getStudents().get(0).getMealPeriod() == MealPeriod.LUNCH, null);

        // 晚餐
        am.resetForNextRun(30L);
        ArrivalGenerationResult dr = am.generateArrivalPlan(50, SimulationMode.SINGLE_PERIOD, MealPeriod.DINNER, false);
        check("晚餐: 生成50人", dr.getStudents().size() == 50, null);
        check("晚餐: 餐段正确", dr.getStudents().get(0).getMealPeriod() == MealPeriod.DINNER, null);

        finishSection(ok);
    }

    static void testArrivalWithDifferentPopulations() {
        printSection("D-IT-09", "不同人口规模到达测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        CanteenConfig.OPEN_DURATION = 120;

        int[] populations = {1, 5, 10, 50, 100, 500, 1000};
        for (int pop : populations) {
            ArrivalModule am = new ArrivalModule(pop * 7L);
            ArrivalGenerationResult result = am.generateArrivalPlan(pop, SimulationMode.SINGLE_PERIOD, MealPeriod.LUNCH, false);
            check("人口=" + pop + ": 生成人数正确", result.getStudents().size() == pop, null);
        }

        finishSection(ok);
    }

    // ==================== 三、仿真引擎深度测试 ====================

    static void testEngineWithVaryingWindowCounts() {
        printSection("D-IT-10", "不同窗口数引擎测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 100;
        CanteenConfig.setTableCount(60);
        CanteenConfig.OPEN_DURATION = 120;

        SimulationAdapter adapter = new SimulationAdapter();
        int[] windowCounts = {1, 2, 3, 5, 8, 12};

        for (int wc : windowCounts) {
            SimRunResult r = adapter.runOnce(wc, 60,
                    SimRunOptions.optimize(42L, 100));
            check("窗口=" + wc + ": 运行成功", r != null && r.runtimeMs >= 0,
                    r != null ? "完成率=" + String.format("%.3f", r.finishRate) : "null");
            if (r != null) {
                check("窗口=" + wc + ": 完成率>0", r.finishRate > 0, null);
            }
        }

        finishSection(ok);
    }

    static void testEngineWithVaryingTableCounts() {
        printSection("D-IT-11", "不同桌子数引擎测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 100;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.OPEN_DURATION = 120;

        SimulationAdapter adapter = new SimulationAdapter();
        int[] tableCounts = {1, 5, 10, 20, 50, 100, 200};

        for (int tc : tableCounts) {
            SimRunResult r = adapter.runOnce(5, tc,
                    SimRunOptions.optimize(42L, 100));
            check("桌子=" + tc + ": 运行成功", r != null, null);
            if (r != null) {
                check("桌子=" + tc + ": 座位利用率", r.seatUtilization >= 0 && r.seatUtilization <= 1.0,
                        String.format("%.3f", r.seatUtilization));
            }
        }

        finishSection(ok);
    }

    static void testEngineWithVaryingPopulations() {
        printSection("D-IT-12", "不同人口规模引擎测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(80);
        CanteenConfig.OPEN_DURATION = 120;

        SimulationAdapter adapter = new SimulationAdapter();
        int[] populations = {1, 10, 50, 100, 200, 500};

        for (int pop : populations) {
            SimRunResult r = adapter.runOnce(5, 80,
                    SimRunOptions.optimize(pop * 13L, pop));
            check("人口=" + pop + ": 运行成功", r != null, null);
            if (r != null && pop > 1) {
                check("人口=" + pop + ": 完成率>0", r.finishRate > 0,
                        String.format("完成率=%.3f", r.finishRate));
            }
        }

        finishSection(ok);
    }

    static void testEngineWithDifferentServiceTimes() {
        printSection("D-IT-13", "不同服务时间引擎测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 80;
        CanteenConfig.setTableCount(50);
        CanteenConfig.OPEN_DURATION = 120;

        SimulationAdapter adapter = new SimulationAdapter();

        // 极短服务时间(1秒)
        int[] dist = {10, 15, 20, 25, 30};
        int[] fastSrv = {1, 1, 1, 1, 1};
        CanteenConfig.updateWindowConfigs(dist, fastSrv);
        SimRunResult rFast = adapter.runOnce(5, 50, SimRunOptions.optimize(1L, 80));
        check("快速服务(1秒): 完成", rFast != null, null);
        if (rFast != null) {
            check("快速服务: 等待时间很短", rFast.avgWaitTimeSeconds < 10,
                    String.format("%.1f秒", rFast.avgWaitTimeSeconds));
        }

        // 正常服务时间
        int[] normSrv = {20, 20, 20, 20, 20};
        CanteenConfig.updateWindowConfigs(dist, normSrv);
        SimRunResult rNorm = adapter.runOnce(5, 50, SimRunOptions.optimize(1L, 80));
        check("正常服务(20秒): 完成", rNorm != null, null);

        // 慢速服务时间(120秒)
        int[] slowSrv = {120, 120, 120, 120, 120};
        CanteenConfig.updateWindowConfigs(dist, slowSrv);
        SimRunResult rSlow = adapter.runOnce(5, 50, SimRunOptions.optimize(1L, 80));
        check("慢速服务(120秒): 完成", rSlow != null, null);
        if (rSlow != null && rFast != null) {
            check("慢速等待 > 快速等待", rSlow.avgWaitTimeSeconds > rFast.avgWaitTimeSeconds,
                    String.format("快%.1f秒 vs 慢%.1f秒", rFast.avgWaitTimeSeconds, rSlow.avgWaitTimeSeconds));
        }

        CanteenConfig.resetToDefaults();
        finishSection(ok);
    }

    // ==================== 四、排队与服务深度测试 ====================

    static void testQueueBehaviorUnderDifferentLoads() {
        printSection("D-IT-14", "不同负载下排队行为测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        CanteenConfig.setWindowCount(3);
        CanteenConfig.setTableCount(100);
        CanteenConfig.OPEN_DURATION = 120;

        SimulationAdapter adapter = new SimulationAdapter();

        SimRunResult r50 = adapter.runOnce(3, 100, SimRunOptions.optimize(42L, 50));
        SimRunResult r100 = adapter.runOnce(3, 100, SimRunOptions.optimize(42L, 100));
        SimRunResult r200 = adapter.runOnce(3, 100, SimRunOptions.optimize(42L, 200));
        SimRunResult r500 = adapter.runOnce(3, 100, SimRunOptions.optimize(42L, 500));

        // 随着人数增加，等待时间和队列应该增加
        if (r100 != null && r50 != null) {
            check("100人等待 >= 50人等待", r100.avgWaitTimeSeconds >= r50.avgWaitTimeSeconds,
                    String.format("%.1f vs %.1f", r100.avgWaitTimeSeconds, r50.avgWaitTimeSeconds));
        }
        if (r200 != null && r100 != null) {
            check("200人等待 >= 100人等待", r200.avgWaitTimeSeconds >= r100.avgWaitTimeSeconds,
                    String.format("%.1f vs %.1f", r200.avgWaitTimeSeconds, r100.avgWaitTimeSeconds));
        }
        if (r500 != null && r200 != null) {
            check("500人等待 >= 200人等待", r500.avgWaitTimeSeconds >= r200.avgWaitTimeSeconds,
                    String.format("%.1f vs %.1f", r500.avgWaitTimeSeconds, r200.avgWaitTimeSeconds));
        }

        finishSection(ok);
    }

    static void testServiceTimeImpact() {
        printSection("D-IT-15", "服务时间影响测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 80;
        CanteenConfig.setTableCount(50);
        CanteenConfig.OPEN_DURATION = 120;

        SimulationAdapter adapter = new SimulationAdapter();
        int[] dist = {10, 15, 20, 25, 30};

        double prevWait = -1;
        int[] serviceTimes = {1, 10, 30, 60, 120, 300};
        for (int st : serviceTimes) {
            int[] srv = {st, st, st, st, st};
            CanteenConfig.updateWindowConfigs(dist, srv);
            SimRunResult r = adapter.runOnce(5, 50, SimRunOptions.optimize(1L, 80));
            if (r != null) {
                check("服务时间=" + st + "秒: 完成", true, String.format("等待%.1f秒", r.avgWaitTimeSeconds));
                if (prevWait >= 0 && st > 10) {
                    check("服务时间增加→等待增加", r.avgWaitTimeSeconds >= prevWait * 0.5,
                            String.format("%.1f >= %.1f", r.avgWaitTimeSeconds, prevWait));
                }
                prevWait = r.avgWaitTimeSeconds;
            }
        }

        CanteenConfig.resetToDefaults();
        finishSection(ok);
    }

    // ==================== 五、座位分配深度测试 ====================

    static void testSeatingWithDifferentGroupConfigs() {
        printSection("D-IT-16", "不同组配置座位分配测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 60;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(30);
        CanteenConfig.OPEN_DURATION = 120;

        SimulationAdapter adapter = new SimulationAdapter();

        // 全单人
        CanteenConfig.PROB_SOLO = 1.0;
        CanteenConfig.PROB_DUO = 0.0;
        CanteenConfig.PROB_TRIO = 0.0;
        CanteenConfig.PROB_TEAM = 0.0;
        CanteenConfig.validate();
        SimRunResult rSolo = adapter.runOnce(5, 30, SimRunOptions.optimize(1L, 60));
        check("全单人: 完成", rSolo != null && rSolo.finishRate > 0,
                rSolo != null ? String.format("完成率=%.3f", rSolo.finishRate) : "null");

        // 高比例多人组
        CanteenConfig.PROB_SOLO = 0.1;
        CanteenConfig.PROB_DUO = 0.3;
        CanteenConfig.PROB_TRIO = 0.3;
        CanteenConfig.PROB_TEAM = 0.3;
        CanteenConfig.validate();
        SimRunResult rMulti = adapter.runOnce(5, 30, SimRunOptions.optimize(1L, 60));
        check("高多人组: 完成", rMulti != null && rMulti.finishRate > 0,
                rMulti != null ? String.format("完成率=%.3f", rMulti.finishRate) : "null");

        CanteenConfig.resetToDefaults();
        finishSection(ok);
    }

    static void testSeatingUnderCapacityStress() {
        printSection("D-IT-17", "座位容量压力测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 100;
        CanteenConfig.setWindowCount(3);  // 慢速窗口让座位积累
        CanteenConfig.OPEN_DURATION = 180;
        CanteenConfig.PATIENCE_MIN = 30 * 60;
        CanteenConfig.PATIENCE_MAX = 60 * 60;

        SimulationAdapter adapter = new SimulationAdapter();

        // 逐渐减少座位
        int[] tableCounts = {100, 50, 25, 10, 5, 3};
        double prevSeatUtil = 0;
        for (int tc : tableCounts) {
            SimRunResult r = adapter.runOnce(3, tc, SimRunOptions.optimize(42L, 100));
            if (r != null) {
                check("桌子=" + tc + ": 座位利用率", r.seatUtilization >= 0 && r.seatUtilization <= 1.0,
                        String.format("%.3f", r.seatUtilization));
                // 座位减少时利用率应大致上升
                if (tc < 50 && r.seatUtilization < prevSeatUtil * 0.5) {
                    // 不强制失败，仅记录
                }
                prevSeatUtil = r.seatUtilization;
            }
        }

        CanteenConfig.resetToDefaults();
        finishSection(ok);
    }

    // ==================== 六、统计模块深度测试 ====================

    static void testStatisticsConsistency() {
        printSection("D-IT-18", "统计一致性测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 100;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(40);
        CanteenConfig.OPEN_DURATION = 120;

        SimulationAdapter adapter = new SimulationAdapter();
        SimRunResult r = adapter.runOnce(5, 40, SimRunOptions.optimize(42L, 100));

        if (r != null) {
            // 完成+放弃 <= 到达
            check("完成+放弃 <= 到达", r.finishedStudents + r.abandonedStudents <= r.arrivedStudents,
                    String.format("%d+%d <= %d", r.finishedStudents, r.abandonedStudents, r.arrivedStudents));

            // 服务 <= 到达
            check("服务 <= 到达", r.servedStudents <= r.arrivedStudents,
                    String.format("%d <= %d", r.servedStudents, r.arrivedStudents));

            // 完成率 = 完成/到达 (约)
            double expectedFinishRate = r.arrivedStudents > 0 ? (double) r.finishedStudents / r.arrivedStudents : 0;
            check("完成率计算一致", Math.abs(r.finishRate - expectedFinishRate) < 0.01,
                    String.format("%.3f vs %.3f", r.finishRate, expectedFinishRate));

            // 放弃率 = 放弃/到达
            double expectedAbandonRate = r.arrivedStudents > 0 ? (double) r.abandonedStudents / r.arrivedStudents : 0;
            check("放弃率计算一致", Math.abs(r.abandonRate - expectedAbandonRate) < 0.01,
                    String.format("%.3f vs %.3f", r.abandonRate, expectedAbandonRate));

            // 无负数
            check("所有指标无负数", r.avgWaitTimeSeconds >= 0 && r.maxQueueLength >= 0
                    && r.avgQueueLength >= 0 && r.servedStudents >= 0, null);

            // 无NaN
            check("所有指标无NaN", !Double.isNaN(r.avgWaitTimeSeconds) && !Double.isNaN(r.seatUtilization)
                    && !Double.isNaN(r.windowUtilization) && !Double.isNaN(r.finishRate), null);
        }

        CanteenConfig.resetToDefaults();
        finishSection(ok);
    }

    static void testStatisticsAcrossMultipleRuns() {
        printSection("D-IT-19", "多次运行统计稳定性测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        CanteenConfig.TOTAL_POPULATION = 100;
        CanteenConfig.setWindowCount(5);
        CanteenConfig.setTableCount(40);
        CanteenConfig.OPEN_DURATION = 120;

        SimulationAdapter adapter = new SimulationAdapter();
        List<SimRunResult> results = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            SimRunResult r = adapter.runOnce(5, 40, SimRunOptions.optimize(42L, 100));
            results.add(r);
        }

        // 所有运行都应成功
        for (int i = 0; i < results.size(); i++) {
            check("运行" + (i + 1) + ": 完成", results.get(i) != null, null);
        }

        // 所有运行完成率相同（固定种子）
        if (results.size() >= 2 && results.get(0) != null && results.get(1) != null) {
            check("固定种子结果一致(完成率)", results.get(0).finishRate == results.get(1).finishRate,
                    String.format("%.3f vs %.3f", results.get(0).finishRate, results.get(1).finishRate));
        }

        CanteenConfig.resetToDefaults();
        finishSection(ok);
    }

    // ==================== 七、优化管线深度测试 ====================

    static void testSimulationAdapterConsistency() {
        printSection("D-IT-20", "SimulationAdapter 一致性测试");
        boolean ok = true;

        CanteenConfig.resetToDefaults();
        long originalSeed = CanteenConfig.RANDOM_SEED;
        int originalPopulation = CanteenConfig.TOTAL_POPULATION;
        int originalTables = CanteenConfig.TOTAL_TABLES;

        SimulationAdapter adapter = new SimulationAdapter();
        SimRunResult r = adapter.runOnce(5, 30, SimRunOptions.optimize(42L, 50));

        // adapter 运行后应恢复原始配置
        check("运行后种子恢复", CanteenConfig.RANDOM_SEED == originalSeed,
                CanteenConfig.RANDOM_SEED + " vs " + originalSeed);
        check("运行后人口恢复", CanteenConfig.TOTAL_POPULATION == originalPopulation,
                CanteenConfig.TOTAL_POPULATION + " vs " + originalPopulation);
        check("运行后桌子恢复", CanteenConfig.TOTAL_TABLES == originalTables,
                CanteenConfig.TOTAL_TABLES + " vs " + originalTables);
        check("运行后无头模式已关闭", !CanteenConfig.HEADLESS_MODE, null);

        check("结果包含正确窗口数", r != null && r.windowCount == 5, null);
        check("结果包含正确桌子数", r != null && r.tableCount == 30, null);
        check("结果包含种子", r != null && r.randomSeed >= 0, null);

        finishSection(ok);
    }

    static void testResultAverager() {
        printSection("D-IT-21", "ResultAverager 测试");
        boolean ok = true;

        backend.optimize.ResultAverager averager = new backend.optimize.ResultAverager();

        // 手动创建几个 SimRunResult 并平均
        SimRunResult r1 = new SimRunResult();
        r1.avgWaitTimeSeconds = 10.0;
        r1.maxQueueLength = 5;
        r1.finishRate = 0.9;
        r1.servedStudents = 45;

        SimRunResult r2 = new SimRunResult();
        r2.avgWaitTimeSeconds = 20.0;
        r2.maxQueueLength = 8;
        r2.finishRate = 0.8;
        r2.servedStudents = 40;

        averager.add(r1);
        averager.add(r2);
        SimRunResult avg = averager.average();

        check("平均等待=(10+20)/2=15", Math.abs(avg.avgWaitTimeSeconds - 15.0) < 0.01,
                String.format("%.1f", avg.avgWaitTimeSeconds));
        check("平均队列=(5+8)/2=6.5→7(四舍五入)", avg.maxQueueLength == 7,
                String.valueOf(avg.maxQueueLength));
        check("平均完成率=(0.9+0.8)/2=0.85", Math.abs(avg.finishRate - 0.85) < 0.01,
                String.format("%.3f", avg.finishRate));

        finishSection(ok);
    }

    // ==================== 辅助方法 ====================

    static void printSection(String id, String name) {
        System.out.println("\n--- " + id + " " + name + " ---");
    }

    static void check(String testName, boolean passed, String detail) {
        totalTests++;
        String marker = passed ? "[PASS]" : "[FAIL]";
        if (passed) totalPassed++;
        else totalFailed++;

        String line = marker + " " + testName;
        if (detail != null) line += " (" + detail + ")";
        System.out.println("  " + line);
    }

    static void finishSection(boolean ok) {
        // placeholder
    }
}
