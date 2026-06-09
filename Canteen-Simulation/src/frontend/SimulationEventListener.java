package frontend;

import backend.dto.SimulationSnapshot;

/**
 * 仿真系统事件监听器
 */
public interface SimulationEventListener {

    /**
     * 学生抵达食堂。
     * @param studentId 学生ID
     * @param groupId 所属组ID
     * @param time 当前仿真时间
     */
    void onStudentArrived(int studentId, int groupId, long time);

    /**
     * 学生选择窗口排队打饭。
     * @param studentId 学生ID
     * @param groupId 所属组ID
     * @param windowIndex 窗口编号
     * @param queueLength 该窗口当前排队人数（含该生）
     * @param time 当前仿真时间
     */
    void onStudentQueuedAtWindow(int studentId, int groupId, int windowIndex, int queueLength, long time);

    void onWindowQueueUpdated(int windowIndex, int queueLength);

    /**
     * 通知某张桌子的精确座位占用状态。
     * seatGroupIds 长度为 capacity，-1 表示空位，其他值表示占用该座的 groupId。
     */
    void onTableOccupancyChanged(int tableIndex, int[] seatGroupIds);

    /**
     * 学生入座就餐。
     * @param studentId 学生ID
     * @param groupId 所属组ID
     * @param tableIndex 桌子编号
     * @param time 当前仿真时间
     */
    void onStudentSeatedAtTable(int studentId, int groupId, int tableIndex, long time);

    /**
     * 学生离开食堂。
     * @param studentId 学生ID
     * @param groupId 所属组ID
     * @param tableIndex 离开前所在的桌子编号，-1 表示未入座
     * @param reason 离开原因
     * @param time 当前仿真时间
     */
    void onStudentLeft(int studentId, int groupId, int tableIndex, String reason, long time);

    /** 餐段切换通知（早餐/午餐/晚餐/关闭中） */
    void onPhaseChanged(String phaseName, String phaseLabel, long currentTime);

    /**
     * Aggregated backend snapshot for KPI cards, pressure colors and trends.
     * Default implementation keeps existing listeners source-compatible.
     */
    default void onSnapshot(SimulationSnapshot snapshot) {
    }

    void onSimulationFinished();
}
