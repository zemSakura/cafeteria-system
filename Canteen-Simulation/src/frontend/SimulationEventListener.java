package frontend;

/**
 * 仿真系统事件监听器
 */
public interface SimulationEventListener {

    void onStudentArrived(int studentId, long time);

    void onWindowQueueUpdated(int windowIndex, int queueLength);

    /**
     * 通知某张桌子当前坐了多少人
     * occupiedSeats 可取 0 / 1 / 2 / 4
     */
    void onTableOccupancyChanged(int tableIndex, int occupiedSeats);

    void onSimulationFinished();
}