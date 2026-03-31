package backend;

import backend.config.CanteenConfig;
import backend.model.Student;
import backend.model.WindowState;
import backend.module.ArrivalModule;
import java.util.Map;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        // 1. 校验配置
        CanteenConfig.validate();

        // 2. 初始化模块
        ArrivalModule arrivalModule = new ArrivalModule();

        // 3. 初始化窗口（静态 + 运行态）
        List<WindowState> windowStates = arrivalModule.initWindowStates();

        // 4. 生成到达学生数据
        List<Student> students = arrivalModule.generateStudents();

        // 5. 按组整理
        Map<Integer, List<Student>> grouped = arrivalModule.groupStudentsByGroupId(students);

        // 6. 测试输出
        printBasicInfo(windowStates, students, grouped.size());
        printTrafficFlowReport(students, grouped.size());
    }

    private static void printBasicInfo(List<WindowState> windowStates,
                                       List<Student> students,
                                       int totalGroups) {
        System.out.println("=".repeat(70));
        System.out.println("食堂仿真系统 - 人员到来模块测试入口");
        System.out.println("=".repeat(70));
        System.out.println("时间单位说明: " + CanteenConfig.TIME_UNIT_DESCRIPTION);
        System.out.println("营业总时长: " + CanteenConfig.OPEN_DURATION);
        System.out.println("快照间隔: " + CanteenConfig.SNAPSHOT_INTERVAL);
        System.out.println("窗口数量: " + windowStates.size());
        System.out.println("总到达人数: " + students.size());
        System.out.println("总到达组数: " + totalGroups);
        System.out.println();

        System.out.println("【窗口初始化结果】");
        for (WindowState state : windowStates) {
            System.out.println("  " + state.getWindow());
        }
        System.out.println();
    }

    /**
     * 打印到达流量分布
     * 这里只是测试用途，正式系统的快照输出以后交给统一模块
     */
    private static void printTrafficFlowReport(List<Student> students, int totalGroups) {
        System.out.println("【人员到达分布报告】");

        if (students.isEmpty()) {
            System.out.println("未生成任何学生数据。");
            return;
        }

        int totalPeople = students.size();
        double avgGroupSize = totalGroups == 0 ? 0.0 : (double) totalPeople / totalGroups;

        System.out.printf("总人数: %d | 总组数: %d | 平均组规模: %.2f%n",
                totalPeople, totalGroups, avgGroupSize);

        int bucketSize = CanteenConfig.SNAPSHOT_INTERVAL;
        int bucketCount = (CanteenConfig.OPEN_DURATION + bucketSize - 1) / bucketSize;
        int[] distribution = new int[bucketCount];

        for (Student s : students) {
            int index = (int) (s.getArrivalTime() / bucketSize);
            if (index >= bucketCount) {
                index = bucketCount - 1;
            }
            distribution[index]++;
        }

        System.out.println();
        System.out.println("时间区间(仿真单位)    人数    直方图");
        System.out.println("-".repeat(70));

        for (int i = 0; i < bucketCount; i++) {
            int start = i * bucketSize;
            int end = Math.min((i + 1) * bucketSize, CanteenConfig.OPEN_DURATION);
            int count = distribution[i];

            String bar = count == 0 ? "" : "*".repeat(Math.max(1, count / 2));
            System.out.printf("[%3d, %3d)        %-5d   %s%n", start, end, count, bar);
        }

        System.out.println("-".repeat(70));
        System.out.println("说明：当前只是“到达模块测试入口”，不是最终总仿真入口。");
        System.out.println("后续排队、服务、入座、离开应由统一事件驱动引擎接管。");
        System.out.println("=".repeat(70));
    }
}