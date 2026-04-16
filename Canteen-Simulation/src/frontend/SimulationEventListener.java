package frontend;

/**
 * 仿真系统事件监听器 (前端提供给后端的通信协议)
 * * 作用：后端引擎完全不需要知道 UI 长什么样，它只需要在特定的时机，调用这里的对应方法即可。
 */
public interface SimulationEventListener {

    /**
     * 当有学生抵达食堂时触发
     * @param studentId 学生编号
     * @param time      当前仿真时间戳
     */
    void onStudentArrived(int studentId, long time);

    /**
     * 当某个打饭窗口的排队人数发生变化时触发
     * @param windowIndex 窗口编号 (0, 1, 2...)
     * @param queueLength 当前排队的总人数
     */
    void onWindowQueueUpdated(int windowIndex, int queueLength);

    /**
     * 当某张桌子的状态发生改变时触发
     * @param tableIndex 桌子编号
     * @param isOccupied 是否被占用 (true: 变红, false: 变空)
     */
    void onTableStatusChanged(int tableIndex, boolean isOccupied);

    /**
     * 当整个仿真结束时触发
     */
    void onSimulationFinished();
}