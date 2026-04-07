package backend;

import config.CanteenConfig;
import config.SimulationConfigRequest;
import engine.CanteenSimulationEngine;
import model.Student;
import model.WindowState;

import java.util.List;

/**
 * 测试主入口
 *
 * 说明：
 * 1. 这里现在不再直接 new ArrivalModule 来跑
 * 2. 而是通过总引擎 CanteenSimulationEngine 统一管理
 * 3. 更符合以后前后端对接方式
 */
public class Main {

    public static void main(String[] args) {
        // =====================================================
        // 1. 构造一份“前端传给后端”的配置请求
        // =====================================================
        SimulationConfigRequest request = new SimulationConfigRequest();

        // 基础配置
        request.setTableCount(180);
        request.setWindowCount(8);
        request.setOpenDuration(120);
        request.setSnapshotInterval(5);
        request.setRandomSeed(20260401L);

        // 就餐时长配置
        request.setDiningTimeMean(15.0);
        request.setDiningTimeStd(3.0);
        request.setMinDiningTime(5);

        // 忍耐度配置
        request.setPatienceMin(5);
        request.setPatienceMax(15);

        // 组队概率配置（总和必须为 1）
        request.setProbSolo(0.7);
        request.setProbDuo(0.2);
        request.setProbTeam(0.1);

        // =====================================================
        // 2. 创建总引擎
        // =====================================================
        CanteenSimulationEngine engine = new CanteenSimulationEngine();

        // =====================================================
        // 3. 注入配置并启动引擎
        // =====================================================
        engine.applyConfig(request);

        // 启动前也可以手动再校验一次
        CanteenConfig.validate();

        engine.startEngine();

        // =====================================================
        // 4. 读取引擎中的初始化结果
        // =====================================================
        List<WindowState> windowStates = engine.getWindowStates();
        List<Student> students = engine.getStudents();

        // =====================================================
        // 5. 打印调试信息
        // =====================================================
        printBasicInfo(engine, windowStates, students);

        // =====================================================
        // 6. 模拟“停止 -> 再开始”
        //    测试 resetEngine() 是否生效
        // =====================================================
        System.out.println();
        System.out.println("===== 测试停止并重启引擎 =====");

        engine.stopEngine();
        engine.resetEngine();
        engine.startEngine();

        System.out.println("重启后引擎概要: " + engine.getEngineSummary());
        System.out.println("重启后学生数: " + engine.getStudentCount());
        System.out.println("重启后窗口数: " + engine.getWindowCount());
        System.out.println("重启后桌位数: " + engine.getTableCount());
    }

    /**
     * 打印基础信息
     *
     * @param engine       总引擎
     * @param windowStates 窗口运行态
     * @param students     学生列表
     */
    private static void printBasicInfo(CanteenSimulationEngine engine,
                                       List<WindowState> windowStates,
                                       List<Student> students) {
        System.out.println("=".repeat(70));
        System.out.println("食堂仿真系统 - 后端引擎测试入口");
        System.out.println("=".repeat(70));

        System.out.println("时间单位说明: " + CanteenConfig.TIME_UNIT_DESCRIPTION);
        System.out.println("当前配置: ");
        System.out.println(CanteenConfig.dumpConfig());
        System.out.println();

        System.out.println("引擎概要: " + engine.getEngineSummary());
        System.out.println("窗口数量: " + windowStates.size());
        System.out.println("总到达人数: " + students.size());
        System.out.println();

        System.out.println("【窗口初始化结果】");
        for (WindowState state : windowStates) {
            System.out.println("  " + state.getWindow());
        }

        System.out.println();
        System.out.println("【前 20 个学生样例】");
        for (int i = 0; i < Math.min(20, students.size()); i++) {
            System.out.println("  " + students.get(i));
        }

        System.out.println("=".repeat(70));
    }
}