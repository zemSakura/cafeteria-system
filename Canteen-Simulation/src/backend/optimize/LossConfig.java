package backend.optimize;

public class LossConfig {
    // === 1. 线性成本系数 (Resource Units) ===
    public double windowCost = 20.0;  // 窗口极其昂贵：占地、人工、设备
    public double tableCost = 1.0;    // 桌子相对便宜：纯占地

    // === 2. 体验容忍红线 (Hard Thresholds) ===
    public double hardWaitThresholdMinutes = 5.0;  // 超过 5 分钟，学生开始焦躁
    public double hardAbandonRateThreshold = 0.05; // 超过 5% 的人因排队放弃就餐，属于严重运营事故

    // === 3. 二次惩罚系数 (Quadratic Penalty Weights) ===
    public double waitPenaltyWeight = 50.0;    // 等待超时的平方放大倍率
    public double abandonPenaltyWeight = 5000.0; // 放弃率的平方放大倍率 (极其严格)

    public void validate() {
        // 防止负数输入导致逻辑崩溃
        if (windowCost < 0) windowCost = 20.0;
        if (tableCost < 0) tableCost = 1.0;
        if (hardWaitThresholdMinutes <= 0) hardWaitThresholdMinutes = 5.0;
        if (hardAbandonRateThreshold <= 0) hardAbandonRateThreshold = 0.05;
        if (waitPenaltyWeight < 0) waitPenaltyWeight = 50.0;
        if (abandonPenaltyWeight < 0) abandonPenaltyWeight = 5000.0;
    }
}