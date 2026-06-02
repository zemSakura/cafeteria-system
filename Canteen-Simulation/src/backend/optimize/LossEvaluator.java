package backend.optimize;

public class LossEvaluator {
    public double evaluate(SimRunResult r, LossConfig c) {
        c.validate();

        // 1. 纯线性硬件成本：每加一个窗口/桌子，稳步增加固定代价
        double resourceCost = (r.windowCount * c.windowCost) + (r.tableCount * c.tableCost);

        // 2. 等待超时二次惩罚
        double waitPenalty = 0.0;
        if (r.avgWaitTimeMinutes > c.hardWaitThresholdMinutes) {
            double excessWait = r.avgWaitTimeMinutes - c.hardWaitThresholdMinutes;
            waitPenalty = c.waitPenaltyWeight * Math.pow(excessWait, 2);
        }

        // 3. 放弃就餐二次惩罚
        double abandonPenalty = 0.0;
        if (r.abandonRate > c.hardAbandonRateThreshold) {
            double excessAbandon = r.abandonRate - c.hardAbandonRateThreshold;
            abandonPenalty = c.abandonPenaltyWeight * Math.pow(excessAbandon, 2);
        }

        // 4. 最终总损耗：平时只看成本，一旦越线，惩罚值会瞬间吞噬成本优势
        double loss = resourceCost + waitPenalty + abandonPenalty;

        if (Double.isNaN(loss) || Double.isInfinite(loss)) {
            return Double.MAX_VALUE;
        }
        return loss;
    }
}