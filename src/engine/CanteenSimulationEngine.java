package engine;

import config.CanteenConfig;
import config.SimulationConfigRequest;
import model.SimulationEvent;
import model.StateSnapshot;
import model.StatisticsResult;
import model.Student;
import model.Table;
import model.WindowState;
import module.ArrivalModule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 食堂仿真总引擎
 *
 * 设计目的：
 * 1. 统一管理运行期状态
 * 2. 给前端提供“开始 / 停止 / 重置”入口
 * 3. 不再让前端直接碰底层模块
 *
 * 当前版本先负责：
 * - 应用配置
 * - resetEngine()
 * - 初始化窗口、学生、桌位、事件
 *
 * 后续你们可以继续往这里扩展：
 * - 排队逻辑
 * - 服务逻辑
 * - 分桌逻辑
 * - 快照逻辑
 * - 统计逻辑
 */
public class CanteenSimulationEngine {

    /**
     * 旧版兼容：到达模块可能往这个队列里塞学生
     */
    private final LinkedBlockingQueue<Student> arrivalQueue;

    /**
     * 到达模块
     */
    private ArrivalModule arrivalModule;

    /**
     * 当前仿真中的所有学生
     */
    private List<Student> students;

    /**
     * 当前窗口运行态
     */
    private List<WindowState> windowStates;

    /**
     * 当前桌位列表
     */
    private List<Table> tables;

    /**
     * 事件队列（目前先简单用 List 保存）
     * 以后如果你们进入完整事件驱动，可以换成 PriorityQueue
     */
    private List<SimulationEvent> eventQueue;

    /**
     * 快照列表
     */
    private List<StateSnapshot> snapshots;

    /**
     * 统计结果
     */
    private StatisticsResult statisticsResult;

    /**
     * 引擎运行标记
     */
    private boolean running;

    /**
     * 默认构造
     */
    public CanteenSimulationEngine() {
        this.arrivalQueue = new LinkedBlockingQueue<>();
        this.students = new ArrayList<>();
        this.windowStates = new ArrayList<>();
        this.tables = new ArrayList<>();
        this.eventQueue = new ArrayList<>();
        this.snapshots = new ArrayList<>();
        this.statisticsResult = new StatisticsResult();
        this.arrivalModule = new ArrivalModule(arrivalQueue, CanteenConfig.RANDOM_SEED);
        this.running = false;
    }

    /**
     * 统一应用配置
     *
     * 前端点击“开始”前，先把表单数据封装进 request，
     * 再调用这个方法
     *
     * @param request 配置请求
     */
    public synchronized void applyConfig(SimulationConfigRequest request) {
        CanteenConfig.updateAllConfigs(request);
    }

    /**
     * 启动引擎
     *
     * 当前版本逻辑：
     * 1. 先 reset
     * 2. 再根据最新配置创建到达模块
     * 3. 初始化窗口
     * 4. 生成学生
     * 5. 生成到达事件
     * 6. 初始化桌位
     */
    public synchronized void startEngine() {
        // 每次开始前先清空旧状态，防止数据叠加
        resetEngine();

        // 按最新随机种子重新创建模块
        arrivalModule = new ArrivalModule(arrivalQueue, CanteenConfig.RANDOM_SEED);

        // 初始化运行态
        windowStates = arrivalModule.initWindowStates();
        students = arrivalModule.generateStudents();
        eventQueue = arrivalModule.generateArrivalEvents();
        tables = initTables();

        running = true;
    }

    /**
     * 停止引擎
     *
     * 注意：
     * 这里只是把运行标记设为 false
     * 真正清空运行态请调用 resetEngine()
     */
    public synchronized void stopEngine() {
        running = false;
    }

    /**
     * 一键重置引擎
     *
     * 这是你前端同学要求的重点方法
     *
     * 作用：
     * 1. 停止运行
     * 2. 清空阻塞队列残留数据
     * 3. 重置到达模块内部随机状态
     * 4. 清空学生、窗口、桌位、事件、快照、统计数据
     *
     * 注意：
     * 这里只清理“运行态”
     * 不会自动恢复默认配置
     * 如果要恢复默认配置，请额外调用 CanteenConfig.resetToDefaults()
     */
    public synchronized void resetEngine() {
        running = false;

        // 清空旧到达队列
        arrivalQueue.clear();

        // 重置到达模块内部状态
        if (arrivalModule != null) {
            arrivalModule.resetForNextRun(CanteenConfig.RANDOM_SEED);
        }

        // 清空所有运行态数据
        students.clear();
        windowStates.clear();
        tables.clear();
        eventQueue.clear();
        snapshots.clear();

        // 统计结果重置
        statisticsResult = new StatisticsResult();
    }

    /**
     * 根据当前配置初始化桌位
     *
     * 这里先统一设为 4 人桌
     * 后续你们可以改成可配置桌型
     *
     * @return 桌位列表
     */
    private List<Table> initTables() {
        List<Table> result = new ArrayList<>();

        for (int i = 0; i < CanteenConfig.TOTAL_TABLES; i++) {
            result.add(new Table(i, 4));
        }

        return result;
    }

    /**
     * 获取当前是否运行中
     *
     * @return true / false
     */
    public synchronized boolean isRunning() {
        return running;
    }

    /**
     * 获取学生列表副本
     *
     * @return 学生列表副本
     */
    public synchronized List<Student> getStudents() {
        return new ArrayList<>(students);
    }

    /**
     * 获取窗口运行态副本
     *
     * @return 窗口运行态副本
     */
    public synchronized List<WindowState> getWindowStates() {
        return new ArrayList<>(windowStates);
    }

    /**
     * 获取桌位列表副本
     *
     * @return 桌位列表副本
     */
    public synchronized List<Table> getTables() {
        return new ArrayList<>(tables);
    }

    /**
     * 获取事件队列副本
     *
     * @return 事件列表副本
     */
    public synchronized List<SimulationEvent> getEventQueue() {
        return new ArrayList<>(eventQueue);
    }

    /**
     * 获取快照列表副本
     *
     * @return 快照列表副本
     */
    public synchronized List<StateSnapshot> getSnapshots() {
        return new ArrayList<>(snapshots);
    }

    /**
     * 获取统计结果
     *
     * @return 统计结果对象
     */
    public synchronized StatisticsResult getStatisticsResult() {
        return statisticsResult;
    }

    /**
     * 获取当前学生数量
     *
     * @return 学生数量
     */
    public synchronized int getStudentCount() {
        return students.size();
    }

    /**
     * 获取当前窗口数量
     *
     * @return 窗口数量
     */
    public synchronized int getWindowCount() {
        return windowStates.size();
    }

    /**
     * 获取当前桌位数量
     *
     * @return 桌位数量
     */
    public synchronized int getTableCount() {
        return tables.size();
    }

    /**
     * 获取引擎概要信息，便于调试
     *
     * @return 引擎概要字符串
     */
    public synchronized String getEngineSummary() {
        return "CanteenSimulationEngine{" +
                "running=" + running +
                ", students=" + students.size() +
                ", windows=" + windowStates.size() +
                ", tables=" + tables.size() +
                ", events=" + eventQueue.size() +
                ", snapshots=" + snapshots.size() +
                '}';
    }
}