package frontend;

/**
 * 仿真参数数据传输对象 (Data Transfer Object)
 * 作用：将界面上零散的输入框数据，打包成一个整体。
 */
public class SimulationConfigDTO {
    public int totalTables = 30;       // 默认 30 张桌子
    public int openDuration = 120;     // 默认营业 120 分钟
    public int totalStudents = 1000;    // 默认就餐人数 1000 人
    public int windowCount = 5;        // 默认 5 个窗口
    public double probSolo = 0.7;      // 默认单人概率 0.7
    public long randomSeed = 20260407L; // 默认随机种子

    // 空构造函数（必须有，为了方便后续的 JSON 自动解析）
    public SimulationConfigDTO() {}
}
