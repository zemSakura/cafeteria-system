package backend.model;

import java.util.Arrays;

/**
 * 桌位模型 — 支持多组拼桌
 */
public class Table {
    private final int id;
    private final int capacity;
    private final int[] seatGroupIds;   // seatGroupIds[seat] = groupId, -1 表示空
    private int occupiedSeatCount;
    private long firstOccupiedTime;
    private long lastReleasedTime;

    public Table(int id, int capacity) {
        this.id = id;
        this.capacity = capacity;
        this.seatGroupIds = new int[capacity];
        Arrays.fill(this.seatGroupIds, -1);
        this.occupiedSeatCount = 0;
        this.firstOccupiedTime = -1;
        this.lastReleasedTime = -1;
    }

    public int getId() { return id; }
    public int getCapacity() { return capacity; }
    public int getAvailableSeats() { return capacity - occupiedSeatCount; }
    public int getOccupiedSeatCount() { return occupiedSeatCount; }

    /** 返回座位归属数组副本 (-1=空，其他值=groupId) */
    public int[] getSeatGroupIds() {
        return seatGroupIds.clone();
    }

    public long getFirstOccupiedTime() { return firstOccupiedTime; }
    public long getLastReleasedTime() { return lastReleasedTime; }

    /** 该桌是否有任何人占用 */
    public boolean isOccupied() { return occupiedSeatCount > 0; }

    /**
     * 为一组人分配空座位，按自然就座习惯。
     *
     * 2人组：面对面 > 并排 > 任意
     * 座位布局：0 1
     *          2 3
     * 面对面 = {0,2} 或 {1,3}（同列）
     * 并排   = {0,1} 或 {2,3}（同行）
     *
     * @return 实际分配到的座位数
     */
    public int assignSeats(int groupId, int count, long time) {
        if (count > getAvailableSeats()) {
            return 0;
        }

        int[] chosen;
        if (count == 2) {
            chosen = pickBestTwoSeats();
        } else {
            chosen = pickFirstAvailable(count);
        }

        if (chosen == null) {
            return 0;
        }

        for (int seat : chosen) {
            seatGroupIds[seat] = groupId;
        }
        occupiedSeatCount += chosen.length;
        if (firstOccupiedTime < 0) {
            firstOccupiedTime = time;
        }
        return chosen.length;
    }

    /**
     * 2人组的最佳座位选择：面对面 > 并排 > 任意两个空位
     */
    private int[] pickBestTwoSeats() {
        // 面对面（同列）
        int[][] faceToFace = {{0, 2}, {1, 3}};
        // 并排（同行）
        int[][] sideBySide = {{0, 1}, {2, 3}};

        for (int[] pair : faceToFace) {
            if (seatGroupIds[pair[0]] == -1 && seatGroupIds[pair[1]] == -1) {
                return pair;
            }
        }
        for (int[] pair : sideBySide) {
            if (seatGroupIds[pair[0]] == -1 && seatGroupIds[pair[1]] == -1) {
                return pair;
            }
        }
        return pickFirstAvailable(2);
    }

    /**
     * 按索引顺序选取前 count 个空座
     */
    private int[] pickFirstAvailable(int count) {
        int[] result = new int[count];
        int found = 0;
        for (int i = 0; i < capacity && found < count; i++) {
            if (seatGroupIds[i] == -1) {
                result[found++] = i;
            }
        }
        if (found < count) {
            return null;
        }
        return result;
    }

    /**
     * 释放一组人的所有座位。
     * @return 实际释放的座位数
     */
    public int releaseSeats(int groupId, long time) {
        int released = 0;
        for (int i = 0; i < capacity; i++) {
            if (seatGroupIds[i] == groupId) {
                seatGroupIds[i] = -1;
                released++;
            }
        }
        occupiedSeatCount -= released;
        lastReleasedTime = time;
        if (occupiedSeatCount == 0) {
            firstOccupiedTime = -1;
        }
        return released;
    }

    @Override
    public String toString() {
        return "Table{" +
                "id=" + id +
                ", capacity=" + capacity +
                ", seats=" + occupiedSeatCount + "/" + capacity +
                ", seatGroupIds=" + Arrays.toString(seatGroupIds) +
                '}';
    }
}
