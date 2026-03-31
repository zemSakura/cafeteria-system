package backend.model;

import backend.config.CanteenConfig;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 学生实体类 - 整个仿真的核心数据对象
 * 负责记录学生从“到达”到“离开”的全过程数据
 */
public class Student {

    // --- 1. [人员到来模块] 负责初始化的数据 ---
    private int id;                 // 学生个人唯一ID
    private int groupId;            // 小组ID (结伴到来的同学共享此ID)
    private long arrivalTime;       // 到达门口的时间 (单位: 秒)
    private int diningTime;         // 随机生成的计划就餐时长 (单位: 秒)
    private int preferredWindow;    // 初始倾向去往的窗口编号
    private int patience;           // 忍耐度 (在队列中等待超过此时长会离开)
    private StudentStatus status;   // 当前实时状态 (枚举类型)

    // --- 2. [排队与离开模块] 负责填写的记录数据 ---
    private int finalWindowId = -1;    // 实际排队的窗口 (-1表示尚未分配)
    private long serviceStartTime = 0; // 开始打到饭/接受服务的时间点
    private long leaveTime = 0;        // 离开食堂的时间点

    /**
     * 构造函数：由“人员到来模块”调用
     * 所有的随机数值均根据 CanteenConfig 中的配置自动生成
     */
    public Student(int id, int groupId, long arrivalTime) {
        this.id = id;
        this.groupId = groupId;
        this.arrivalTime = arrivalTime;

        // 初始状态设为：刚到达
        this.status = StudentStatus.ARRIVING;

        // 获取随机工具类
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        // 1. 根据配置的正态分布参数生成【就餐时长】
        double gaussian = rand.nextGaussian();
        this.diningTime = (int) (CanteenConfig.DINING_TIME_MEAN + gaussian * CanteenConfig.DINING_TIME_STD);
        // 保底：就餐时间不少于 5 分钟 (300秒)
        if (this.diningTime < 300) this.diningTime = 300;

        // 2. 根据配置的窗口总数生成【倾向窗口】
        int windowCount = CanteenConfig.WINDOW_DISTANCES.length;
        this.preferredWindow = rand.nextInt(windowCount);

        // 3. 根据配置的范围生成【忍耐度】
        this.patience = rand.nextInt(CanteenConfig.PATIENCE_MIN, CanteenConfig.PATIENCE_MAX + 1);
    }

    // --- Getter 方法 (全模块通用) ---
    public int getId() { return id; }
    public int getGroupId() { return groupId; }
    public long getArrivalTime() { return arrivalTime; }
    public int getDiningTime() { return diningTime; }
    public int getPreferredWindow() { return preferredWindow; }
    public int getPatience() { return patience; }
    public StudentStatus getStatus() { return status; }
    public int getFinalWindowId() { return finalWindowId; }
    public long getServiceStartTime() { return serviceStartTime; }
    public long getLeaveTime() { return leaveTime; }

    // --- Setter 方法 (供后续排队、就餐模块修改状态) ---
    public void setStatus(StudentStatus status) { this.status = status; }
    public void setFinalWindowId(int finalWindowId) { this.finalWindowId = finalWindowId; }
    public void setServiceStartTime(long serviceStartTime) { this.serviceStartTime = serviceStartTime; }
    public void setLeaveTime(long leaveTime) { this.leaveTime = leaveTime; }

    /**
     * 计算等待时长 (逻辑字段)
     * 公式: $WaitTime = ServiceStartTime - ArrivalTime$
     */
    public long getWaitTime() {
        if (serviceStartTime > 0) {
            return serviceStartTime - arrivalTime;
        }
        return 0;
    }

    /**
     * 重写 toString 方法，方便在控制台打印调试信息
     */
    @Override
    public String toString() {
        String statusDesc = "";
        switch (this.status) {
            case ARRIVING: statusDesc = "刚到达"; break;
            case QUEUING:  statusDesc = "排队中(窗口" + finalWindowId + ")"; break;
            case DINING:   statusDesc = "就餐中"; break;
            case LEAVING:  statusDesc = "已离开"; break;
            case BALKED:   statusDesc = "嫌人多离开"; break;
        }

        return String.format("学生[ID:%-4d | 组:%-4d | 状态:%-6s | 计划吃:%-4ds | 倾向窗口:%d]",
                id, groupId, statusDesc, diningTime, preferredWindow);
    }
}