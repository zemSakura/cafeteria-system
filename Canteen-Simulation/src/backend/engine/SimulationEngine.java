package backend.engine;

import backend.config.CanteenConfig;
import backend.model.Student;
import backend.model.StudentStatus;
import backend.model.Table;
import backend.model.Window;
import backend.model.WindowState;
import frontend.SimulationEventListener;

import backend.model.ArrivalGenerationResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
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
 * 5. 同组成员尽量同桌入座，支持多组拼桌
 * 6. 桌子按空座数分配，不再要求完全空桌
 * 7. 同组成员统一用最长就餐时间，吃完一起走
 * 8. 前端按组颜色区分拼桌情况
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

    private final List<ArrivalGenerationResult.PhaseBoundary> phaseBoundaries;
    private int currentPhaseIndex = -1;

    private volatile boolean running = true;

    private int nextArrivalIndex = 0;
    private long currentTime = 0;

    public SimulationEngine(List<Student> students, SimulationEventListener listener) {
        this(students, listener, 50L, Collections.emptyList());
    }

    public SimulationEngine(List<Student> students,
                            SimulationEventListener listener,
                            long timeScaleMillis) {
        this(students, listener, timeScaleMillis, Collections.emptyList());
    }

    public SimulationEngine(List<Student> students,
                            SimulationEventListener listener,
                            long timeScaleMillis,
                            List<ArrivalGenerationResult.PhaseBoundary> phaseBoundaries) {
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
        this.phaseBoundaries = new ArrayList<>(phaseBoundaries);

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
            // 广播初始阶段
            checkAndBroadcastPhase();

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

                checkAndBroadcastPhase();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running = false;
            listener.onSimulationFinished();
        }
    }

    private void checkAndBroadcastPhase() {
        for (int i = currentPhaseIndex + 1; i < phaseBoundaries.size(); i++) {
            ArrivalGenerationResult.PhaseBoundary pb = phaseBoundaries.get(i);
            if (currentTime >= pb.startTick && (pb.endTick < 0 || currentTime < pb.endTick)) {
                currentPhaseIndex = i;
                listener.onPhaseChanged(pb.name, pb.label, currentTime);
                return;
            }
            // 如果已经超过这个阶段，继续检查下一个
            if (pb.endTick >= 0 && currentTime >= pb.endTick) {
                continue;
            }
            // 还没到这个阶段，不再检查后面的
            break;
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

        // 组耐心取均值，避免一人急性子拖累整组
        double groupPatience = members.stream()
                .mapToInt(Student::getPatience)
                .average()
                .orElse(0);

        // 偏好窗口忠诚度加成：选中自己最爱窗口时，耐心 +50%
        int preferredWindow = members.get(0).getPreferredWindow();
        if (windowId == preferredWindow) {
            groupPatience *= 1.5;
        }

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

            int groupSize = allMembers.size();
            int tableId = findTableWithEnoughSeats(groupSize);
            if (tableId < 0) {
                continue;
            }

            Table table = tables.get(tableId);
            table.assignSeats(groupId, groupSize, currentTime);

            // 同组成员统一使用最长就餐时间，吃完一起走
            long maxDiningTime = 0;
            for (Student student : readyMembers) {
                maxDiningTime = Math.max(maxDiningTime, student.getDiningTime());
            }
            long diningEndTime = currentTime + Math.max(1, maxDiningTime);

            for (Student student : readyMembers) {
                student.setTableId(tableId);
                student.setSeatAssignedTime(currentTime);
                student.setDiningStartTime(currentTime);
                student.setDiningEndTime(diningEndTime);
                student.setStatus(StudentStatus.DINING);
                diningStudents.add(student);
            }

            listener.onTableOccupancyChanged(tableId, table.getSeatGroupIds());
        }

        waitingSeatByGroup.entrySet().removeIf(entry -> {
            List<Student> members = entry.getValue();
            return !members.isEmpty() && members.get(0).getTableId() >= 0;
        });
    }

    /**
     * 找一张有足够空座位的桌子（支持拼桌）。
     * 优先选空桌；无空桌时选空座最少、已有人用的桌子（拼桌紧凑）。
     */
    private int findTableWithEnoughSeats(int groupSize) {
        List<Integer> emptyTables = new ArrayList<>();
        List<Integer> sharedTables = new ArrayList<>();

        for (Table table : tables) {
            if (table.getAvailableSeats() >= groupSize) {
                if (table.getOccupiedSeatCount() == 0) {
                    emptyTables.add(table.getId());
                } else {
                    sharedTables.add(table.getId());
                }
            }
        }

        if (!emptyTables.isEmpty()) {
            return emptyTables.get(random.nextInt(emptyTables.size()));
        }

        if (!sharedTables.isEmpty()) {
            // 拼桌时优先空座最少的，保持紧凑
            int bestId = -1;
            int bestAvail = Integer.MAX_VALUE;
            for (int id : sharedTables) {
                int avail = tables.get(id).getAvailableSeats();
                if (avail < bestAvail) {
                    bestAvail = avail;
                    bestId = id;
                } else if (avail == bestAvail && random.nextBoolean()) {
                    bestId = id;
                }
            }
            return bestId;
        }

        return -1;
    }

    /**
     * 吃完离开
     * 同组成员统一就餐时间，一桌多组各自释放自己的座位。
     * 每 tick 结束时批量通知前端，避免同一桌反复刷新。
     */
    private void processDiningCompletions() {
        Iterator<Student> iterator = diningStudents.iterator();
        Set<Integer> changedTables = new LinkedHashSet<>();

        while (iterator.hasNext()) {
            Student student = iterator.next();
            if (student.getDiningEndTime() > currentTime) {
                continue;
            }

            int tableId = student.getTableId();
            int groupId = student.getGroupId();

            student.setStatus(StudentStatus.LEFT_NORMAL);
            student.setLeaveReason("正常就餐结束");
            student.setLeaveTime(currentTime);

            if (tableId >= 0) {
                tables.get(tableId).releaseSeats(groupId, currentTime);
                changedTables.add(tableId);
            }

            iterator.remove();
        }

        for (int tableId : changedTables) {
            listener.onTableOccupancyChanged(tableId, tables.get(tableId).getSeatGroupIds());
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