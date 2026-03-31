package module;

import config.CanteenConfig;
import model.EventType;
import model.Group;
import model.SimulationEvent;
import model.Student;
import model.Window;
import model.WindowState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class ArrivalModule implements Runnable {

    /**
     * 兼容旧版演示入口保留的字段
     * 正式集成时不建议依赖 BlockingQueue
     */
    private final BlockingQueue<Student> queue;

    /**
     * 使用固定种子，保证实验可复现
     */
    private final Random random;

    public ArrivalModule() {
        this(null, CanteenConfig.RANDOM_SEED);
    }

    public ArrivalModule(long seed) {
        this(null, seed);
    }

    public ArrivalModule(BlockingQueue<Student> queue) {
        this(queue, CanteenConfig.RANDOM_SEED);
    }

    public ArrivalModule(BlockingQueue<Student> queue, long seed) {
        this.queue = queue;
        this.random = new Random(seed);
    }

    /**
     * 正式对接接口 1：初始化窗口静态配置
     */
    public List<Window> initWindows() {
        List<Window> windows = new ArrayList<>();
        for (int i = 0; i < CanteenConfig.getWindowCount(); i++) {
            windows.add(new Window(
                    i,
                    CanteenConfig.WINDOW_DISTANCES[i],
                    CanteenConfig.WINDOW_AVG_SERVE_TIME[i]
            ));
        }
        return windows;
    }

    /**
     * 正式对接接口 2：初始化窗口运行态
     */
    public List<WindowState> initWindowStates() {
        List<Window> windows = initWindows();
        List<WindowState> states = new ArrayList<>();
        for (Window window : windows) {
            states.add(new WindowState(window));
        }
        return states;
    }

    /**
     * 正式对接接口 3：生成学生到达数据
     * 这里只负责“谁在什么时间到达”
     */
    public List<Student> generateStudents() {
        List<Student> students = new ArrayList<>();

        int studentIdCounter = 1;
        int groupIdCounter = 1;
        long virtualTime = 0;

        while (virtualTime < CanteenConfig.OPEN_DURATION) {
            double progress = (double) virtualTime / CanteenConfig.OPEN_DURATION;

            // 中间高、两边低：午餐高峰更明显
            double lambda = 0.05 + 0.45 * Math.sin(Math.PI * progress);
            if (lambda <= 0) {
                lambda = 0.01;
            }

            int nextGap = sampleArrivalGap(lambda);
            virtualTime += nextGap;

            if (virtualTime > CanteenConfig.OPEN_DURATION) {
                break;
            }

            int groupSize = determineGroupSize();

            for (int i = 0; i < groupSize; i++) {
                int diningTime = generateDiningTime();
                int preferredWindow = generatePreferredWindow();
                int patience = generatePatience();

                Student student = new Student(
                        studentIdCounter++,
                        groupIdCounter,
                        virtualTime,
                        diningTime,
                        preferredWindow,
                        patience
                );

                students.add(student);
            }

            groupIdCounter++;
        }

        return students;
    }

    /**
     * 正式对接接口 4：按 groupId 整理学生
     */
    public Map<Integer, List<Student>> groupStudentsByGroupId(List<Student> students) {
        Map<Integer, List<Student>> grouped = new LinkedHashMap<>();
        for (Student student : students) {
            grouped.computeIfAbsent(student.getGroupId(), k -> new ArrayList<>()).add(student);
        }
        return grouped;
    }

    /**
     * 可选对接接口：直接生成 Group 列表
     */
    public List<Group> generateGroups() {
        List<Student> students = generateStudents();
        Map<Integer, List<Student>> grouped = groupStudentsByGroupId(students);

        List<Group> groups = new ArrayList<>();
        for (Map.Entry<Integer, List<Student>> entry : grouped.entrySet()) {
            List<Student> members = entry.getValue();
            long arrivalTime = members.get(0).getArrivalTime();
            groups.add(new Group(entry.getKey(), arrivalTime, members));
        }
        return groups;
    }

    /**
     * 可选对接接口：直接生成到达事件
     * 当前只生成 ARRIVAL 事件
     */
    public List<SimulationEvent> generateArrivalEvents() {
        List<Student> students = generateStudents();
        List<SimulationEvent> events = new ArrayList<>();

        for (Student student : students) {
            events.add(new SimulationEvent(
                    student.getArrivalTime(),
                    EventType.ARRIVAL,
                    student.getGroupId(),
                    student.getId(),
                    -1,
                    -1,
                    0
            ));
        }

        return events;
    }

    /**
     * 保留 Runnable：兼容旧版测试写法
     * 但这里不再 sleep、不再打印，只是把结果塞进队列
     */
    @Override
    public void run() {
        if (queue == null) {
            return;
        }

        List<Student> students = generateStudents();
        for (Student student : students) {
            try {
                queue.put(student);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private int sampleArrivalGap(double lambda) {
        double u = random.nextDouble();
        double gap = -Math.log(1 - u) / lambda;
        return Math.max(1, (int) Math.ceil(gap));
    }

    private int determineGroupSize() {
        double r = random.nextDouble();
        if (r < CanteenConfig.PROB_SOLO) {
            return 1;
        }
        if (r < CanteenConfig.PROB_SOLO + CanteenConfig.PROB_DUO) {
            return 2;
        }
        return 4;
    }

    private int generateDiningTime() {
        double gaussian = random.nextGaussian();
        int result = (int) Math.round(
                CanteenConfig.DINING_TIME_MEAN + gaussian * CanteenConfig.DINING_TIME_STD
        );
        return Math.max(result, CanteenConfig.MIN_DINING_TIME);
    }

    private int generatePreferredWindow() {
        return random.nextInt(CanteenConfig.getWindowCount());
    }

    private int generatePatience() {
        return CanteenConfig.PATIENCE_MIN
                + random.nextInt(CanteenConfig.PATIENCE_MAX - CanteenConfig.PATIENCE_MIN + 1);
    }
}