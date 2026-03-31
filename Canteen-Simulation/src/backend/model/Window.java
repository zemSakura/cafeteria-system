package backend.model;

/**
 * 窗口模型
 * 由负责人员到来模块的同学（你）进行初始化
 */
public class Window {
    private int id;                // 窗口编号
    private int distanceFromDoor;  // 距离门口的距离（你要求的特殊属性）
    private int avgServeTime;     // 窗口平均服务时间

    public Window(int id, int distanceFromDoor, int avgServeTime) {
        this.id = id;
        this.distanceFromDoor = distanceFromDoor;
        this.avgServeTime = avgServeTime;
    }

    // Getter 方法
    public int getId() { return id; }
    public int getDistanceFromDoor() { return distanceFromDoor; }
    public int getAvgServeTime() { return avgServeTime; }

    @Override
    public String toString() {
        return String.format("窗口[%d] | 距离门:%dm | 平均服务时间:%ds",
                id, distanceFromDoor, avgServeTime);
    }
}