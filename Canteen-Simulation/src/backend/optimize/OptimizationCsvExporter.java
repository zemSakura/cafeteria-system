package backend.optimize;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

public class OptimizationCsvExporter {
    public void exportAll(OptimizeResult result, String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("创建优化输出目录失败: " + dirPath);
        }

        exportOptimizationResult(result, new File(dir, "optimization_result.csv").getPath());
        exportTopK(result, new File(dir, "top_k_result.csv").getPath());
        if (result.replayResult != null) {
            exportReplay(result.replayResult, new File(dir, "best_replay.csv").getPath());
        }
    }

    private void exportOptimizationResult(OptimizeResult result, String path) {
        try (PrintWriter out = new PrintWriter(new FileWriter(path))) {
            out.println("步骤,窗口数,桌子数,学生总数,平均排队等待分钟,平均等座等待分钟,最大排队人数,最大等座人数,平均排队长度,座位利用率,桌子利用率,窗口利用率,完成率,放弃率,净收益,综合评分,损失值,当前最佳损失,运行耗时毫秒");
            for (SimRunResult r : result.allResults) {
                out.println(r.step + ","
                        + r.windowCount + ","
                        + r.tableCount + ","
                        + r.totalStudents + ","
                        + r.avgWaitTimeMinutes + ","
                        + r.avgSeatWaitTimeMinutes + ","
                        + r.maxQueueLength + ","
                        + r.maxWaitingSeatCount + ","
                        + r.avgQueueLength + ","
                        + r.seatUtilization + ","
                        + r.tableUtilization + ","
                        + r.windowUtilization + ","
                        + r.finishRate + ","
                        + r.abandonRate + ","
                        + r.netProfit + ","
                        + r.score + ","
                        + r.loss + ","
                        + r.currentBestLoss + ","
                        + r.runtimeMs);
            }
        } catch (IOException e) {
            throw new RuntimeException("导出 optimization_result.csv 失败", e);
        }
    }

    private void exportTopK(OptimizeResult result, String path) {
        try (PrintWriter out = new PrintWriter(new FileWriter(path))) {
            out.println("排名,窗口数,桌子数,完成率,净收益,平均排队等待分钟,平均等座等待分钟,放弃人数,综合评分,推荐理由");
            int rank = 1;
            for (SimRunResult r : result.topKResults) {
                out.println(rank++ + ","
                        + r.windowCount + ","
                        + r.tableCount + ","
                        + r.finishRate + ","
                        + r.netProfit + ","
                        + r.avgWaitTimeMinutes + ","
                        + r.avgSeatWaitTimeMinutes + ","
                        + r.abandonedStudents + ","
                        + r.score + ","
                        + quote(r.reason));
            }
        } catch (IOException e) {
            throw new RuntimeException("导出 top_k_result.csv 失败", e);
        }
    }

    private void exportReplay(ReplayResult replay, String path) {
        try (PrintWriter out = new PrintWriter(new FileWriter(path))) {
            out.println("时间秒,时间分钟,总排队人数,等座人数,各窗口排队人数,忙碌窗口数,窗口总数,窗口利用率,已占座位,座位总数,空座位,桌子利用率,就餐人数,已到达人数,已服务人数,已完成人数,已放弃人数");
            for (ReplaySnapshot s : replay.snapshots) {
                out.println(s.timeSecond + ","
                        + s.timeMinute + ","
                        + s.totalQueueLength + ","
                        + s.waitingSeatStudents + ","
                        + quote(Arrays.toString(s.windowQueueLengths)) + ","
                        + s.busyWindowCount + ","
                        + s.totalWindowCount + ","
                        + s.windowUtilization + ","
                        + s.occupiedSeats + ","
                        + s.totalSeats + ","
                        + s.emptySeats + ","
                        + s.tableUtilization + ","
                        + s.diningStudents + ","
                        + s.arrivedStudents + ","
                        + s.servedStudents + ","
                        + s.finishedStudents + ","
                        + s.abandonedStudents);
            }
        } catch (IOException e) {
            throw new RuntimeException("导出 best_replay.csv 失败", e);
        }
    }

    private String quote(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }
}
