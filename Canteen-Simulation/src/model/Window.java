package model;

/**
 * 窗口静态配置模型
 * 只负责描述窗口本身的固定属性
 */
public class Window {
    private int id;                // 窗口编号
    private int distanceFromDoor;  // 距离门口距离（米）
    private int avgServeTime;      // 平均服务时长（仿真时间单位）

    public Window(int id, int distanceFromDoor, int avgServeTime) {
        this.id = id;
        this.distanceFromDoor = distanceFromDoor;
        this.avgServeTime = avgServeTime;
    }

    public int getId() { return id; }
    public int getDistanceFromDoor() { return distanceFromDoor; }
    public int getAvgServeTime() { return avgServeTime; }

    @Override
    public String toString() {
        return String.format(
                "窗口[%d] | 距离门:%dm | 平均服务时长:%d",
                id, distanceFromDoor, avgServeTime
        );
    }
}