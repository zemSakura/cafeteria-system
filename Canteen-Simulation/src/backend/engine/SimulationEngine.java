package backend.engine;

import backend.config.CanteenConfig;
import backend.model.Student;
import backend.model.StudentStatus;
import backend.model.Table;
import backend.model.Window;
import backend.model.WindowState;
import frontend.SimulationEventListener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * 正式事件驱动仿真引擎
 *
 * 当前版本功能：
 * 1. 学生按到达时间进入系统
 * 2. 同组成员统一选择窗口
 * 3. 若预计等待超过耐心值，则整组放弃排队
 * 4. 学生完成服务后进入等座区
 * 5. 同组成员尽量同桌入座
 * 6. 桌子随机分配，不再固定按编号顺序
 * 7. 只有同桌最后一个人离开时，桌子才释放
 * 8. 前端能够看到一张桌子真实坐了多少人
 */
public class SimulationEngine implements Runnable {

    private final List<Student> allStudents;
    private final SimulationEventListener listener;

    private final List<WindowState> windowStates;
    private final List<Deque<Student>> windowQueues;
    private final List<Table> tables;
    private final List<Student> diningStudents;

    private final Student[] servingStudents;
    private final long[] serviceEndTimes;

    private final long timeScaleMillis;
    private final Random random;

    /**
     * groupId -> 整组成员
     */
    private final Map<Integer, List<Student>> groupMembers;

    /**
     * groupId -> 该组已选择的窗口
     */
    private final Map<Integer, Integer> groupChosenWindow;

    /**
     * groupId -> 已完成服务、正在等待同桌入座的成员
     */
    private final Map<Integer, List<Student>> waitingSeatByGroup;

    /**
     * 已整组放弃排队的 groupId
     */
    private final Set<Integer> abandonedGroups;

    /**
     * tableId -> 该桌还剩多少人在吃
     */
    private final Map<Integer, Integer> tableRemainingDiners;

    /**
     * tableId -> 当前桌上坐了多少人（给前端显示用）
     */
    private final Map<Integer, Integer> tableOccupiedSeats;

    private volatile boolean running = true;

    private int nextArrivalIndex = 0;
    private long currentTime = 0;

    public SimulationEngine(List<Student> students, SimulationEventListener listener) {
        this(students, listener, 50L);
    }

    public SimulationEngine(List<Student> students,
                            SimulationEventListener listener,
                            long timeScaleMillis) {
        this.listener = Objects.requireNonNull(listener, "listener 不能为空");

        this.allStudents = new ArrayList<>();
        if (students != null) {
            this.allStudents.addAll(students);
        }

        this.allStudents.sort(
                Comparator.comparingLong(Student::getArrivalTime)
                        .thenComparingInt(Student::getGroupId)
                        .thenComparingInt(Student::getId)
        );

        this.timeScaleMillis = Math.max(1L, timeScaleMillis);
        this.random = new Random(CanteenConfig.RANDOM_SEED);

        this.windowStates = initWindowStates();
        this.windowQueues = new ArrayList<>();
        for (int i = 0; i < windowStates.size(); i++) {
            windowQueues.add(new ArrayDeque<>());
        }

        this.tables = initTables();
        this.diningStudents = new ArrayList<>();

        this.servingStudents = new Student[windowStates.size()];
        this.serviceEndTimes = new long[windowStates.size()];
        Arrays.fill(this.serviceEndTimes, -1L);

        this.groupMembers = new HashMap<>();
        this.groupChosenWindow = new HashMap<>();
        this.waitingSeatByGroup = new HashMap<>();
        this.abandonedGroups = new HashSet<>();
        this.tableRemainingDiners = new HashMap<>();
        this.tableOccupiedSeats = new HashMap<>();

        for (Student student : this.allStudents) {
            groupMembers.computeIfAbsent(student.getGroupId(), k -> new ArrayList<>()).add(student);
        }
    }

    public void requestStop() {
        running = false;
    }

    @Override
    public void run() {
        try {
            while (running) {
                processDiningCompletions();
                processServiceCompletions();
                trySeatWaitingGroups();
                processArrivals();
                processServiceStarts();

                if (isSimulationFinished()) {
                    break;
                }

                Thread.sleep(timeScaleMillis);
                currentTime++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running = false;
            listener.onSimulationFinished();
        }
    }

    /**
     * 按组处理到达
     */
    private void processArrivals() {
        while (nextArrivalIndex < allStudents.size()
                && allStudents.get(nextArrivalIndex).getArrivalTime() <= currentTime) {

            Student first = allStudents.get(nextArrivalIndex);
            int groupId = first.getGroupId();
            List<Student> members = groupMembers.get(groupId);

            if (members == null || members.isEmpty()) {
                nextArrivalIndex++;
                continue;
            }

            for (Student member : members) {
                if (member.getArrivalTime() <= currentTime
                        && member.getStatus() == StudentStatus.ARRIVING) {
                    listener.onStudentArrived(member.getId(), currentTime);
                }
            }

            handleGroupArrival(groupId);

            while (nextArrivalIndex < allStudents.size()
                    && allStudents.get(nextArrivalIndex).getGroupId() == groupId) {
                nextArrivalIndex++;
            }
        }
    }

    /**
     * 整组到达后的统一处理
     */
    private void handleGroupArrival(int groupId) {
        if (abandonedGroups.contains(groupId)) {
            return;
        }

        List<Student> members = groupMembers.get(groupId);
        if (members == null || members.isEmpty()) {
            return;
        }

        int windowId = chooseWindowForGroup(members);
        long estimatedWait = estimateWindowWait(windowId);

        int groupPatience = members.stream()
                .mapToInt(Student::getPatience)
                .min()
                .orElse(0);

        if (estimatedWait > groupPatience) {
            abandonGroup(members, "预计等待超过耐心值");
            return;
        }

        groupChosenWindow.put(groupId, windowId);

        for (Student student : members) {
            student.setFinalWindowId(windowId);
            student.setQueueEnterTime(currentTime);
            student.setStatus(StudentStatus.QUEUING);
            windowQueues.get(windowId).offerLast(student);
        }

        updateQueueLength(windowId);
    }

    /**
     * 整组选择同一个窗口
     */
    private int chooseWindowForGroup(List<Student> members) {
        int groupId = members.get(0).getGroupId();

        if (groupChosenWindow.containsKey(groupId)) {
            return groupChosenWindow.get(groupId);
        }

        Student representative = members.get(0);

        List<Integer> bestCandidates = new ArrayList<>();
        double bestScore = Double.MAX_VALUE;

        for (int i = 0; i < windowQueues.size(); i++) {
            WindowState state = windowStates.get(i);
            Window window = state.getWindow();

            int queueLen = windowQueues.get(i).size();
            double estimatedWait = queueLen * window.getAvgServeTime();

            if (servingStudents[i] != null) {
                estimatedWait += Math.max(1L, serviceEndTimes[i] - currentTime);
            }

            double distancePenalty = window.getDistanceFromDoor() * 0.08;
            double preferenceBonus = (i == representative.getPreferredWindow()) ? -1.2 : 0.0;

            double score = estimatedWait + distancePenalty + preferenceBonus;

            if (score < bestScore - 1e-9) {
                bestScore = score;
                bestCandidates.clear();
                bestCandidates.add(i);
            } else if (Math.abs(score - bestScore) <= 1e-9) {
                bestCandidates.add(i);
            }
        }

        int chosen = bestCandidates.get(random.nextInt(bestCandidates.size()));
        groupChosenWindow.put(groupId, chosen);
        return chosen;
    }

    private long estimateWindowWait(int windowId) {
        WindowState state = windowStates.get(windowId);
        long wait = (long) windowQueues.get(windowId).size() * state.getWindow().getAvgServeTime();

        if (servingStudents[windowId] != null) {
            wait += Math.max(1L, serviceEndTimes[windowId] - currentTime);
        }

        return wait;
    }

    private void abandonGroup(List<Student> members, String reason) {
        int groupId = members.get(0).getGroupId();
        abandonedGroups.add(groupId);

        for (Student student : members) {
            student.setStatus(StudentStatus.BALKED);
            student.setLeaveReason(reason);
            student.setLeaveTime(currentTime);
        }
    }

    /**
     * 窗口开始服务
     */
    private void processServiceStarts() {
        for (int i = 0; i < windowStates.size(); i++) {
            if (servingStudents[i] != null) {
                continue;
            }

            Deque<Student> queue = windowQueues.get(i);
            if (queue.isEmpty()) {
                continue;
            }

            Student student = queue.pollFirst();
            WindowState state = windowStates.get(i);

            state.setBusy(true);
            state.incrementServedCount();

            student.setQueueLeaveTime(currentTime);
            student.setServiceStartTime(currentTime);
            student.setStatus(StudentStatus.SERVING);

            servingStudents[i] = student;
            serviceEndTimes[i] = currentTime + Math.max(1, state.getWindow().getAvgServeTime());

            updateQueueLength(i);
        }
    }

    /**
     * 服务完成后先进入等座区
     */
    private void processServiceCompletions() {
        for (int i = 0; i < windowStates.size(); i++) {
            Student student = servingStudents[i];
            if (student == null) {
                continue;
            }

            if (serviceEndTimes[i] > currentTime) {
                continue;
            }

            WindowState state = windowStates.get(i);

            student.setServiceEndTime(currentTime);
            state.setBusy(false);
            state.addBusyTime(Math.max(1, currentTime - student.getServiceStartTime()));

            servingStudents[i] = null;
            serviceEndTimes[i] = -1L;

            student.setStatus(StudentStatus.WAITING_SEAT);
            waitingSeatByGroup
                    .computeIfAbsent(student.getGroupId(), k -> new ArrayList<>())
                    .add(student);
        }
    }

    /**
     * 尝试让等座区中的整组成员同桌入座
     */
    private void trySeatWaitingGroups() {
        for (Map.Entry<Integer, List<Student>> entry : waitingSeatByGroup.entrySet()) {
            int groupId = entry.getKey();
            List<Student> readyMembers = entry.getValue();
            List<Student> allMembers = groupMembers.get(groupId);

            if (allMembers == null || allMembers.isEmpty()) {
                continue;
            }

            if (readyMembers.size() < allMembers.size()) {
                continue;
            }

            int tableId = findRandomFreeTableForGroup(allMembers.size());
            if (tableId < 0) {
                continue;
            }

            Table table = tables.get(tableId);
            table.occupy(groupId, currentTime);
            tableRemainingDiners.put(tableId, allMembers.size());
            tableOccupiedSeats.put(tableId, allMembers.size());

            for (Student student : readyMembers) {
                student.setTableId(tableId);
                student.setSeatAssignedTime(currentTime);
                student.setDiningStartTime(currentTime);
                student.setDiningEndTime(currentTime + Math.max(1, student.getDiningTime()));
                student.setStatus(StudentStatus.DINING);
                diningStudents.add(student);
            }

            listener.onTableOccupancyChanged(tableId, allMembers.size());
        }

        waitingSeatByGroup.entrySet().removeIf(entry -> {
            List<Student> members = entry.getValue();
            return !members.isEmpty() && members.get(0).getTableId() >= 0;
        });
    }

    /**
     * 随机找一张能容纳整组人数的空桌
     */
    private int findRandomFreeTableForGroup(int groupSize) {
        List<Integer> candidates = new ArrayList<>();

        for (Table table : tables) {
            if (!table.isOccupied() && table.getCapacity() >= groupSize) {
                candidates.add(table.getId());
            }
        }

        if (candidates.isEmpty()) {
            return -1;
        }

        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * 吃完离开
     * 只有最后一个人离开时才释放桌子
     * 同时把桌上的实时人数更新给前端
     */
    private void processDiningCompletions() {
        Iterator<Student> iterator = diningStudents.iterator();
        while (iterator.hasNext()) {
            Student student = iterator.next();
            if (student.getDiningEndTime() > currentTime) {
                continue;
            }

            int tableId = student.getTableId();

            student.setStatus(StudentStatus.LEFT_NORMAL);
            student.setLeaveReason("正常就餐结束");
            student.setLeaveTime(currentTime);

            if (tableId >= 0 && tableRemainingDiners.containsKey(tableId)) {
                int remain = tableRemainingDiners.get(tableId) - 1;
                if (remain <= 0) {
                    tables.get(tableId).release(currentTime);
                    tableRemainingDiners.remove(tableId);
                    tableOccupiedSeats.remove(tableId);
                    listener.onTableOccupancyChanged(tableId, 0);
                } else {
                    tableRemainingDiners.put(tableId, remain);
                    tableOccupiedSeats.put(tableId, remain);
                    listener.onTableOccupancyChanged(tableId, remain);
                }
            }

            iterator.remove();
        }
    }

    private boolean isSimulationFinished() {
        if (nextArrivalIndex < allStudents.size()) {
            return false;
        }

        for (Deque<Student> queue : windowQueues) {
            if (!queue.isEmpty()) {
                return false;
            }
        }

        for (Student student : servingStudents) {
            if (student != null) {
                return false;
            }
        }

        if (!waitingSeatByGroup.isEmpty()) {
            return false;
        }

        return diningStudents.isEmpty();
    }

    private void updateQueueLength(int windowId) {
        int len = windowQueues.get(windowId).size();
        windowStates.get(windowId).setCurrentQueueLength(len);
        listener.onWindowQueueUpdated(windowId, len);
    }

    private List<WindowState> initWindowStates() {
        List<WindowState> result = new ArrayList<>();
        for (int i = 0; i < CanteenConfig.getWindowCount(); i++) {
            Window window = new Window(
                    i,
                    CanteenConfig.WINDOW_DISTANCES[i],
                    CanteenConfig.WINDOW_AVG_SERVE_TIME[i]
            );
            result.add(new WindowState(window));
        }
        return result;
    }

    private List<Table> initTables() {
        List<Table> result = new ArrayList<>();
        for (int i = 0; i < CanteenConfig.TOTAL_TABLES; i++) {
            result.add(new Table(i, 4));
        }
        return result;
    }
}