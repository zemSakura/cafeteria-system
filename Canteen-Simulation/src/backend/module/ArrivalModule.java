package backend.module;

import backend.config.CanteenConfig;
import backend.model.EventType;
import backend.model.Group;
import backend.model.SimulationEvent;
import backend.model.Student;
import backend.model.Window;
import backend.model.WindowState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

/**
 * 人员到来模块
 *
 * 当前职责：
 * 1. 根据当前配置初始化窗口
 * 2. 生成学生到达数据
 * 3. 生成到达事件
 * 4. 为后续引擎提供数据源
 *
 * 注意：
 * - 本类现在是“纯后端数据模块”
 * - 不再直接操作任何 Swing UI 组件
 * - 不再承担仿真播放职责
 */
public class ArrivalModule {

    /**
     * 为了兼容旧代码保留这个字段
     * 当前正式版引擎并不依赖它
     */
    private final BlockingQueue<Student> queue;

    /**
     * 当前模块使用的随机种子
     */
    private long seed;

    /**
     * 随机数对象
     */
    private Random random;

    /**
     * 默认构造：使用系统当前随机种子
     */
    public ArrivalModule() {
        this(null, CanteenConfig.RANDOM_SEED);
    }

    /**
     * 指定种子构造
     */
    public ArrivalModule(long seed) {
        this(null, seed);
    }

    /**
     * 旧版兼容构造
     */
    public ArrivalModule(BlockingQueue<Student> queue) {
        this(queue, CanteenConfig.RANDOM_SEED);
    }

    /**
     * 最完整构造
     */
    public ArrivalModule(BlockingQueue<Student> queue, long seed) {
        this.queue = queue;
        this.seed = seed;
        this.random = new Random(seed);
    }

    /**
     * 重新开始前重置内部状态
     */
    public void resetForNextRun() {
        this.random = new Random(this.seed);
        if (this.queue != null) {
            this.queue.clear();
        }
    }

    /**
     * 使用新的随机种子重置
     */
    public void resetForNextRun(long newSeed) {
        this.seed = newSeed;
        this.random = new Random(newSeed);
        if (this.queue != null) {
            this.queue.clear();
        }
    }

    public long getSeed() {
        return seed;
    }

    /**
     * 初始化窗口静态配置
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
     * 初始化窗口运行态
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
     * 生成学生到达数据
     *
     * 这里只负责：
     * - 谁来
     * - 什么时候来
     * - 带什么属性来
     *
     * 不负责：
     * - 排队
     * - 服务
     * - 入座
     * - 离开
     */
    public List<Student> generateStudents() {
        List<Student> students = new ArrayList<>();

        int studentIdCounter = 1;
        int groupIdCounter = 1;
        long virtualTime = 0;

        while (virtualTime < CanteenConfig.OPEN_DURATION) {
            double progress = (double) virtualTime / CanteenConfig.OPEN_DURATION;

            double lambda = 0.28 + 1.35 * Math.sin(Math.PI * progress);
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
     * 按 groupId 整理学生
     */
    public Map<Integer, List<Student>> groupStudentsByGroupId(List<Student> students) {
        Map<Integer, List<Student>> grouped = new LinkedHashMap<>();

        for (Student student : students) {
            grouped.computeIfAbsent(student.getGroupId(), k -> new ArrayList<>()).add(student);
        }

        return grouped;
    }

    /**
     * 直接生成 Group 列表
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
     * 根据给定学生列表生成到达事件
     * 推荐正式引擎优先使用这个版本，避免重复随机生成造成不一致
     */
    public List<SimulationEvent> generateArrivalEvents(List<Student> students) {
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
     * 保留旧版无参方法，兼容原先代码
     */
    public List<SimulationEvent> generateArrivalEvents() {
        return generateArrivalEvents(generateStudents());
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