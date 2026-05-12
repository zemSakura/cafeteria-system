package frontend;

/**
 * 仿真系统事件监听器
 */
public interface SimulationEventListener {

    void onStudentArrived(int studentId, long time);

    void onWindowQueueUpdated(int windowIndex, int queueLength);

    /**
     * 通知某张桌子的精确座位占用状态。
     * seatGroupIds 长度为 capacity，-1 表示空位，其他值表示占用该座的 groupId。
     */
    void onTableOccupancyChanged(int tableIndex, int[] seatGroupIds);

    /** 餐段切换通知（早餐/午餐/晚餐/关闭中） */
    void onPhaseChanged(String phaseName, String phaseLabel, long currentTime);

    void onSimulationFinished();
}