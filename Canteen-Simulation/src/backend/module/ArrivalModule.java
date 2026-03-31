package backend.module;

import backend.config.CanteenConfig;
import backend.model.Student;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

public class ArrivalModule implements Runnable {
    private final BlockingQueue<Student> queue;

    // 用于统计的数组，我们将2小时分成24个时间段（每5分钟一段）
    private final int[] stats = new int[24];
    private final int interval = CanteenConfig.OPEN_DURATION / 24;

    public ArrivalModule(BlockingQueue<Student> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        int studentIdCounter = 1;
        int groupIdCounter = 1;
        int virtualTime = 0;

        System.out.println("=== 仿真开始：观察下方星号(*)的增长趋势 ===");

        while (virtualTime < CanteenConfig.OPEN_DURATION) {
            // 1. 泊松分布的核心逻辑：根据当前时间计算到达率 (Lambda)
            double progress = (double) virtualTime / CanteenConfig.OPEN_DURATION;
            // sin函数产生0.1到0.5的波动，峰值刚好在进度 0.5 (即中午12:00)
            double lambda = 0.05 + 0.45 * Math.sin(Math.PI * progress);

            double u = ThreadLocalRandom.current().nextDouble();
            int nextGap = (int) (-Math.log(1 - u) / lambda);
            if (nextGap < 1) nextGap = 1;

            virtualTime += nextGap;

            // 2. 结伴逻辑
            int groupSize = determineGroupSize();

            // 3. 统计并生成学生
            int statsIdx = virtualTime / interval;
            if (statsIdx >= 24) statsIdx = 23;
            stats[statsIdx] += groupSize; // 记录这个时间段来了多少人

            for (int i = 0; i < groupSize; i++) {
                try {
                    queue.put(new Student(studentIdCounter++, groupIdCounter, virtualTime));
                } catch (InterruptedException e) {
                    return;
                }
            }
            groupIdCounter++;

            // 4. 【实时可视化逻辑】
            // 每当模拟时间过去 300 秒（5分钟），就打印当前的“人流密度图”
            if (virtualTime % 300 < nextGap) {
                printDensityMap(virtualTime);
            }

            // 加速仿真：0.02秒代表模拟中的 nextGap 秒
            try { Thread.sleep(20); } catch (InterruptedException e) { break; }
        }
        System.out.println("\n=== 模拟结束，上方即为食堂完整的人流波动图 ===");
    }

    private void printDensityMap(int currentTime) {
        int currentBin = currentTime / interval;
        System.out.printf("[%04ds] 实时密度: ", currentTime);

        // 打印星号，星号的数量代表当前时间段的人数
        int count = stats[currentBin >= 24 ? 23 : currentBin];
        String bar = "*".repeat(Math.min(count, 50)); // 最多显示50个星号防止刷屏
        System.out.println(bar + " (" + count + "人)");
    }

    private int determineGroupSize() {
        double r = ThreadLocalRandom.current().nextDouble();
        if (r < CanteenConfig.PROB_SOLO) return 1;
        if (r < CanteenConfig.PROB_SOLO + CanteenConfig.PROB_DUO) return 2;
        return 4;
    }
}