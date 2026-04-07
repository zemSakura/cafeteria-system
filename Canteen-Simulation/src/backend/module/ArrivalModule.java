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

/**
 * 人员到来模块
 *
 * 主要职责：
 * 1. 根据当前配置初始化窗口
 * 2. 生成学生到达数据
 * 3. 生成到达事件
 * 4. 为后续引擎提供数据源
 *
 * 改造点：
 * 1. 支持动态窗口配置
 * 2. 支持 reset 后重复运行
 * 3. 支持随机种子重置，保证同参数下可复现实验
 */
public class ArrivalModule implements Runnable {

    /**
     * 兼容旧版演示入口保留的字段
     * 如果旧代码还在用 BlockingQueue，可以继续保留
     */
    private final BlockingQueue<Student> queue;

    /**
     * 当前模块所使用的随机种子
     * reset 时可以重新设置
     */
    private long seed;

    /**
     * 随机数对象不再 final
     * 因为 reset 时需要重新 new
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
     *
     * @param seed 随机种子
     */
    public ArrivalModule(long seed) {
        this(null, seed);
    }

    /**
     * 旧版兼容构造
     *
     * @param queue 阻塞队列
     */
    public ArrivalModule(BlockingQueue<Student> queue) {
        this(queue, CanteenConfig.RANDOM_SEED);
    }

    /**
     * 最完整构造
     *
     * @param queue 阻塞队列
     * @param seed  随机种子
     */
    public ArrivalModule(BlockingQueue<Student> queue, long seed) {
        this.queue = queue;
        this.seed = seed;
        this.random = new Random(seed);
    }

    /**
     * 重新开始前重置内部状态
     *
     * 作用：
     * 1. 重置随机数生成器
     * 2. 清空旧阻塞队列
     */
    public void resetForNextRun() {
        this.random = new Random(this.seed);

        if (this.queue != null) {
            this.queue.clear();
        }
    }

    /**
     * 使用新的随机种子重置
     *
     * @param newSeed 新随机种子
     */
    public void resetForNextRun(long newSeed) {
        this.seed = newSeed;
        this.random = new Random(newSeed);

        if (this.queue != null) {
            this.queue.clear();
        }
    }

    /**
     * 获取当前随机种子
     *
     * @return seed
     */
    public long getSeed() {
        return seed;
    }

    /**
     * 初始化窗口静态配置
     *
     * 这里直接读取当前 CanteenConfig 中的窗口数组
     * 因此一旦配置动态更新，这里会自动适配
     *
     * @return 窗口列表
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
     *
     * @return 窗口运行态列表
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
     * 说明：
     * 1. 这里只管“谁在什么时候来”
     * 2. 不负责排队/服务/入座/离开
     *
     * @return 学生列表
     */
    public List<Student> generateStudents() {
        List<Student> students = new ArrayList<>();

        // 注意：这里是局部计数器
        // 每次 generateStudents() 都会从 1 开始，不会脏数据叠加
        int studentIdCounter = 1;
        int groupIdCounter = 1;
        long virtualTime = 0;

        while (virtualTime < CanteenConfig.OPEN_DURATION) {
            // 进度比例，用于模拟中间高峰、两边低峰的人流分布
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

            // 同一个 groupId 下创建 groupSize 个学生
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
     *
     * @param students 学生列表
     * @return 分组后的 Map
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
     *
     * @return Group 列表
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
     * 直接生成到达事件
     *
     * 当前只生成 ARRIVAL 事件
     *
     * @return 事件列表
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
     *
     * 作用：
     * - 只把生成的学生塞进队列
     * - 不负责 sleep，不负责打印
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

    /**
     * 采样下一次到达间隔
     *
     * @param lambda 泊松过程参数
     * @return 下一个到达间隔
     */
    private int sampleArrivalGap(double lambda) {
        double u = random.nextDouble();
        double gap = -Math.log(1 - u) / lambda;
        return Math.max(1, (int) Math.ceil(gap));
    }

    /**
     * 随机决定小组人数
     *
     * @return 1 / 2 / 4
     */
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

    /**
     * 生成就餐时长
     *
     * @return 就餐时长
     */
    private int generateDiningTime() {
        double gaussian = random.nextGaussian();
        int result = (int) Math.round(
                CanteenConfig.DINING_TIME_MEAN + gaussian * CanteenConfig.DINING_TIME_STD
        );

        return Math.max(result, CanteenConfig.MIN_DINING_TIME);
    }

    /**
     * 随机生成倾向窗口
     *
     * @return 窗口编号
     */
    private int generatePreferredWindow() {
        return random.nextInt(CanteenConfig.getWindowCount());
    }

    /**
     * 生成学生忍耐度
     *
     * @return 忍耐度
     */
    private int generatePatience() {
        return CanteenConfig.PATIENCE_MIN
                + random.nextInt(CanteenConfig.PATIENCE_MAX - CanteenConfig.PATIENCE_MIN + 1);
    }
}