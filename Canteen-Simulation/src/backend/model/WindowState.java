package backend.model;

/**
 * 窗口运行态数据
 * 负责维护忙闲状态、排队长度、累计服务人数等
 */
public class WindowState {

    private final Window window;
    private boolean busy;
    private int currentQueueLength;
    private int servedCount;
    private long totalBusyTime;
    private int maxQueueLength;

    public WindowState(Window window) {
        this.window = window;
        this.busy = false;
        this.currentQueueLength = 0;
        this.servedCount = 0;
        this.totalBusyTime = 0;
        this.maxQueueLength = 0;
    }

    public Window getWindow() { return window; }
    public boolean isBusy() { return busy; }
    public int getCurrentQueueLength() { return currentQueueLength; }
    public int getServedCount() { return servedCount; }
    public long getTotalBusyTime() { return totalBusyTime; }
    public int getMaxQueueLength() { return maxQueueLength; }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public void setCurrentQueueLength(int currentQueueLength) {
        this.currentQueueLength = currentQueueLength;
        if (currentQueueLength > this.maxQueueLength) {
            this.maxQueueLength = currentQueueLength;
        }
    }

    public void incrementQueueLength() {
        this.currentQueueLength++;
        if (this.currentQueueLength > this.maxQueueLength) {
            this.maxQueueLength = this.currentQueueLength;
        }
    }

    public void decrementQueueLength() {
        if (this.currentQueueLength > 0) {
            this.currentQueueLength--;
        }
    }

    public void incrementServedCount() {
        this.servedCount++;
    }

    public void addBusyTime(long busyTime) {
        if (busyTime > 0) {
            this.totalBusyTime += busyTime;
        }
    }

    @Override
    public String toString() {
        return "WindowState{" +
                "window=" + window +
                ", busy=" + busy +
                ", currentQueueLength=" + currentQueueLength +
                ", servedCount=" + servedCount +
                ", totalBusyTime=" + totalBusyTime +
                ", maxQueueLength=" + maxQueueLength +
                '}';
    }
}