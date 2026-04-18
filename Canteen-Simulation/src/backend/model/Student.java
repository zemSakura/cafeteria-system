package backend.model;

import backend.config.CanteenConfig;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 学生实体类 - 整个仿真的核心数据对象
 * 负责记录学生从“到达”到“离开”的全过程数据
 */
public class Student {

    // --- 1. [人员到来模块] 负责初始化的数据 ---
    private int id;                 // 学生个人唯一 ID
    private int groupId;            // 小组 ID (结伴到来的同学共享此 ID)
    private long arrivalTime;       // 到达门口时间（单位：仿真时间单位）
    private int diningTime;         // 计划就餐时长（单位：仿真时间单位）
    private int preferredWindow;    // 倾向窗口编号（注意：不是最终窗口）
    private int patience;           // 忍耐度（排队最大可等待时长）
    private StudentStatus status;   // 当前实时状态

    // --- 2. [排队与离开模块] 负责填写的记录数据 ---
    private int finalWindowId = -1;     // 实际排队窗口
    private long serviceStartTime = -1; // 开始服务时间
    private long serviceEndTime = -1;   // 服务结束时间
    private long seatAssignedTime = -1; // 分配桌位时间
    private long diningStartTime = -1;  // 开始就餐时间
    private long diningEndTime = -1;    // 就餐结束时间
    private long leaveTime = -1;        // 离开系统时间
    private int tableId = -1;           // 分配到的桌位 ID
    private String leaveReason = "";    // 离开原因
    private long queueEnterTime = -1;   // 入队时间
    private long queueLeaveTime = -1;   // 出队时间

    /**
     * 保留原构造器：兼容旧代码
     * 这里仍然自动生成 diningTime / preferredWindow / patience
     */
    public Student(int id, int groupId, long arrivalTime) {
        this.id = id;
        this.groupId = groupId;
        this.arrivalTime = arrivalTime;
        this.status = StudentStatus.ARRIVING;

        ThreadLocalRandom rand = ThreadLocalRandom.current();

        double gaussian = rand.nextGaussian();
        this.diningTime = (int) Math.round(
                CanteenConfig.DINING_TIME_MEAN + gaussian * CanteenConfig.DINING_TIME_STD
        );
        if (this.diningTime < CanteenConfig.MIN_DINING_TIME) {
            this.diningTime = CanteenConfig.MIN_DINING_TIME;
        }

        this.preferredWindow = rand.nextInt(CanteenConfig.getWindowCount());
        this.patience = rand.nextInt(CanteenConfig.PATIENCE_MIN, CanteenConfig.PATIENCE_MAX + 1);
    }

    /**
     * 新增重载构造器：便于 ArrivalModule 用固定随机种子生成可复现实验数据
     */
    public Student(int id, int groupId, long arrivalTime, int diningTime, int preferredWindow, int patience) {
        this.id = id;
        this.groupId = groupId;
        this.arrivalTime = arrivalTime;
        this.diningTime = Math.max(diningTime, CanteenConfig.MIN_DINING_TIME);
        this.preferredWindow = preferredWindow;
        this.patience = patience;
        this.status = StudentStatus.ARRIVING;
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
    public long getServiceEndTime() { return serviceEndTime; }
    public long getSeatAssignedTime() { return seatAssignedTime; }
    public long getDiningStartTime() { return diningStartTime; }
    public long getDiningEndTime() { return diningEndTime; }
    public long getLeaveTime() { return leaveTime; }
    public int getTableId() { return tableId; }
    public String getLeaveReason() { return leaveReason; }
    public long getQueueEnterTime() { return queueEnterTime; }
    public long getQueueLeaveTime() { return queueLeaveTime; }

    // --- Setter 方法 (供后续排队、就餐模块修改状态) ---
    public void setStatus(StudentStatus status) { this.status = status; }
    public void setFinalWindowId(int finalWindowId) { this.finalWindowId = finalWindowId; }
    public void setServiceStartTime(long serviceStartTime) { this.serviceStartTime = serviceStartTime; }
    public void setServiceEndTime(long serviceEndTime) { this.serviceEndTime = serviceEndTime; }
    public void setSeatAssignedTime(long seatAssignedTime) { this.seatAssignedTime = seatAssignedTime; }
    public void setDiningStartTime(long diningStartTime) { this.diningStartTime = diningStartTime; }
    public void setDiningEndTime(long diningEndTime) { this.diningEndTime = diningEndTime; }
    public void setLeaveTime(long leaveTime) { this.leaveTime = leaveTime; }
    public void setTableId(int tableId) { this.tableId = tableId; }
    public void setLeaveReason(String leaveReason) { this.leaveReason = leaveReason; }
    public void setQueueEnterTime(long queueEnterTime) { this.queueEnterTime = queueEnterTime; }
    public void setQueueLeaveTime(long queueLeaveTime) { this.queueLeaveTime = queueLeaveTime; }

    /**
     * 等待时长：
     * 没开始服务时返回 -1，避免和“零等待”混淆
     */
    public long getWaitTime() {
        if (serviceStartTime < 0) {
            return -1;
        }
        return serviceStartTime - arrivalTime;
    }

    /**
     * 整个系统停留时长：
     * 从到达到离开
     */
    public long getSystemStayTime() {
        if (leaveTime < 0) {
            return -1;
        }
        return leaveTime - arrivalTime;
    }

    /**
     * 实际就餐时长：
     * 从 diningStartTime 到 diningEndTime
     */
    public long getDiningDuration() {
        if (diningStartTime < 0 || diningEndTime < 0) {
            return -1;
        }
        return diningEndTime - diningStartTime;
    }

    @Override
    public String toString() {
        String statusDesc;
        switch (this.status) {
            case ARRIVING:
                statusDesc = "刚到达";
                break;
            case QUEUING:
                statusDesc = "排队中(窗口" + finalWindowId + ")";
                break;
            case DINING:
                statusDesc = "就餐中";
                break;
            case LEAVING:
                statusDesc = "离开流程中";
                break;
            case BALKED:
                statusDesc = "放弃排队";
                break;
            case SELECTING_WINDOW:
                statusDesc = "选窗口";
                break;
            case SERVING:
                statusDesc = "服务中";
                break;
            case WAITING_SEAT:
                statusDesc = "等座中";
                break;
            case LEFT_NORMAL:
                statusDesc = "正常离开";
                break;
            case LEFT_NO_SEAT:
                statusDesc = "无座离开";
                break;
            default:
                statusDesc = "未知状态";
        }

        return String.format(
                "学生[ID:%-4d | 组:%-4d | 状态:%-8s | 到达:%-3d | 计划吃:%-3d | 倾向窗口:%d | 最终窗口:%d | 桌位:%d]",
                id, groupId, statusDesc, arrivalTime, diningTime, preferredWindow, finalWindowId, tableId
        );
    }
}