/**
 * 文件说明：排队与入座逻辑的核心实现文件。
 *
 * 作用定位：
 * 1. 这是后端正式运行的事件驱动仿真引擎，负责把“学生到达 -> 选择窗口 -> 进入队列 -> 窗口服务 -> 等待座位 -> 入座就餐 -> 离开”这一整条流程串起来。
 * 2. 你负责的排队逻辑主要集中在 processArrivals、handleGroupArrival、estimateWindowWait、processServiceStarts、processServiceCompletions、updateQueueLength 等方法。
 * 3. 你负责的入座逻辑主要集中在 trySeatWaitingGroups、reserveSeatsForGroup、seatReadyMembersAtTable、findRandomFreeTableForGroup、findSharedTableForGroup、processDiningCompletions 等方法。
 * 4. 本文件同时负责把队列长度、桌位占用变化通过 SimulationEventListener 通知前端，让 QueueAreaPanel 和 DiningAreaPanel 能实时刷新。
 * 5. 文件后半部分的报表输出函数用于记录营业过程、生成 CSV/TXT/HTML 结果，服务于最终展示和统计，不是排队/入座决策本身。
 *
 * 核心规则：
 * - 每个学生按到达时间进入系统，同组学生作为一个 group 管理。
 * - 每个学生随机选择一个窗口排队，系统会估算等待时间；如果超过小组平均耐心值，则整组放弃排队。
 * - 窗口空闲时从对应队列取队首学生开始服务，服务结束后学生进入 WAITING_SEAT 状态。
 * - 同组中先打完饭的人可以先找座位；一旦找到桌子，后端会为整组锁定座位容量，后续成员打完饭后直接坐到同一桌。
 * - 入座优先选择能容纳整组的空桌；没有空桌时才允许拼桌，并且不能占用其他组已锁定的容量。
 * - 同组成员全部实际入座，并且都达到就餐结束时间后，整组一起离开并释放桌位。
 */
package backend.engine;

import backend.config.CanteenConfig;
import backend.model.ArrivalGenerationResult;
import backend.model.Student;
import backend.model.StudentStatus;
import backend.model.Table;
import backend.model.Window;
import backend.model.WindowState;
import frontend.SimulationEventListener;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * 正式事件驱动仿真引擎。
 *
 * 适配队友新版前端：桌位刷新使用 int[] seatGroupIds，前端按组颜色显示实际已入座座位。
 *
 * 当前后端规则：
 * 1. 后端仿真时间单位为秒。
 * 2. 学生按到达时间进入系统。
 * 3. 同组成员到达后，每个人随机选择任意窗口排队。
 * 4. 学生完成服务后进入等座区。
 * 5. 同组中只要有人先打完饭，就可以先占座；系统会为整组锁定容量。
 * 6. 锁定容量不直接传给前端，因此提前锁定的位置不会提前标红。
 * 7. 前端只显示 Table 中真实已经入座的 seatGroupIds。
 * 8. 入座优先找能容纳整组的空桌，没有空桌时才允许拼桌。
 * 9. 同组成员全部实际入座并全部达到就餐结束时间后，整组一起离开并释放座位。
 * 10. 仿真结束后输出完整营业信息：HTML 报告、时间线 CSV、事件 CSV、摘要 TXT。
 */
public class SimulationEngine implements Runnable {

    private static final DateTimeFormatter REPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

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

    /** groupId -> 整组成员。 */
    private final Map<Integer, List<Student>> groupMembers;

    /** groupId -> 已完成打饭、正在等待入座的成员。 */
    private final Map<Integer, List<Student>> waitingSeatByGroup;

    /** 已整组放弃排队的 groupId。 */
    private final Set<Integer> abandonedGroups;

    /** groupId -> 已经为该组锁定的桌号。 */
    private final Map<Integer, Integer> groupAssignedTable;

    /** groupId -> 该组锁定的座位数，一般等于该组总人数。 */
    private final Map<Integer, Integer> groupReservedSeats;

    /** groupId -> 已经实际入座的成员。 */
    private final Map<Integer, List<Student>> seatedMembersByGroup;

    /**
     * tableId -> 当前桌子被占用/锁定的座位数。
     *
     * 该值用于后端容量判断，包含实际已入座座位和提前锁定座位。
     * 前端显示不使用该值，而是使用 Table.getSeatGroupIds()，因此锁定但未入座的位置不会标红。
     */
    private final Map<Integer, Integer> tableReservedOrOccupiedSeats;

    private final List<ArrivalGenerationResult.PhaseBoundary> phaseBoundaries;
    private int currentPhaseIndex = -1;

    private volatile boolean running = true;

    private int nextArrivalIndex = 0;
    private long currentTime = 0;

    /** 对外保留一个“主报告路径”，现在指向 HTML 报告。 */
    private Path reportFilePath;
    private Path outputDir;
    private String reportBaseName;
    private Path timelineFilePath;
    private Path eventsFilePath;
    private Path summaryFilePath;
    private Path htmlReportFilePath;

    private PrintWriter timelineWriter;
    private PrintWriter eventWriter;
    private boolean timelineHeaderWritten = false;
    private boolean eventHeaderWritten = false;

    /** 用于最终生成 HTML 图表和汇总统计。 */
    private final List<Snapshot> snapshots = new ArrayList<>();

    private long[] cumulativeQueueLengthByWindow;
    private long[] busySampleCountByWindow;
    private long snapshotSampleCount = 0;

    private int emptyTableSeatCount = 0;
    private int sharedTableSeatCount = 0;
    private int seatReservationCount = 0;
    private int groupLeaveCount = 0;

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

        // 队友前端传入的 timeScale 原本以“分钟 tick”为参照。
        // 现在后端 tick 是秒，因此压缩 sleep，避免 GUI 慢 60 倍。
        this.timeScaleMillis = Math.max(1L, Math.round(timeScaleMillis / 60.0));
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
        this.waitingSeatByGroup = new HashMap<>();
        this.abandonedGroups = new HashSet<>();
        this.groupAssignedTable = new HashMap<>();
        this.groupReservedSeats = new HashMap<>();
        this.seatedMembersByGroup = new HashMap<>();
        this.tableReservedOrOccupiedSeats = new HashMap<>();
        this.phaseBoundaries = new ArrayList<>(phaseBoundaries == null ? Collections.emptyList() : phaseBoundaries);

        this.cumulativeQueueLengthByWindow = new long[windowStates.size()];
        this.busySampleCountByWindow = new long[windowStates.size()];

        for (Student student : this.allStudents) {
            groupMembers.computeIfAbsent(student.getGroupId(), k -> new ArrayList<>()).add(student);
        }
    }

    public void requestStop() {
        running = false;
    }

    public Path getReportFilePath() {
        return reportFilePath;
    }

    @Override
    public void run() {
        try {
            openReportWriters();
            checkAndBroadcastPhase();

            while (running) {
                processDiningCompletions();
                processServiceCompletions();
                trySeatWaitingGroups();
                processArrivals();
                processServiceStarts();
                writeCurrentSnapshot();

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
            closeReportWriters();
            generateFinalReports();
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
                writeEvent(currentTime, "PHASE_CHANGE", -1, -1, -1, -1, -1, -1, -1,
                        "切换到餐段：" + pb.label);
                return;
            }
            if (pb.endTick >= 0 && currentTime >= pb.endTick) {
                continue;
            }
            break;
        }
    }

    /** 按组处理到达，但排队窗口按每个学生独立随机选择。 */
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
                    writeEvent(currentTime, "ARRIVE", member.getId(), member.getGroupId(), -1, -1, -1, -1, -1,
                            "学生到达餐厅");
                }
            }

            handleGroupArrival(groupId);

            while (nextArrivalIndex < allStudents.size()
                    && allStudents.get(nextArrivalIndex).getGroupId() == groupId) {
                nextArrivalIndex++;
            }
        }
    }

    private void handleGroupArrival(int groupId) {
        if (abandonedGroups.contains(groupId)) {
            return;
        }

        List<Student> members = groupMembers.get(groupId);
        if (members == null || members.isEmpty()) {
            return;
        }

        Map<Student, Integer> selectedWindows = new HashMap<>();
        Map<Integer, Integer> addedCountByWindow = new HashMap<>();
        long maxEstimatedWait = 0L;

        for (Student student : members) {
            int windowId = chooseRandomWindowForStudent();
            selectedWindows.put(student, windowId);

            int alreadyAssignedToThisWindow = addedCountByWindow.getOrDefault(windowId, 0);
            long estimatedWait = estimateWindowWait(windowId)
                    + (long) alreadyAssignedToThisWindow * windowStates.get(windowId).getWindow().getAvgServeTime();
            maxEstimatedWait = Math.max(maxEstimatedWait, estimatedWait);

            addedCountByWindow.put(windowId, alreadyAssignedToThisWindow + 1);
        }

        double groupPatience = members.stream()
                .mapToInt(Student::getPatience)
                .average()
                .orElse(0);

        if (maxEstimatedWait > groupPatience) {
            abandonGroup(members, "预计等待超过耐心值");
            return;
        }

        Set<Integer> updatedWindows = new HashSet<>();
        for (Student student : members) {
            int windowId = selectedWindows.get(student);
            student.setFinalWindowId(windowId);
            student.setQueueEnterTime(currentTime);
            student.setStatus(StudentStatus.QUEUING);
            windowQueues.get(windowId).offerLast(student);
            updatedWindows.add(windowId);

            writeEvent(currentTime, "QUEUE_ENTER", student.getId(), student.getGroupId(), windowId, -1, -1, -1, -1,
                    "学生进入窗口 " + (windowId + 1) + " 排队");
        }

        for (Integer windowId : updatedWindows) {
            updateQueueLength(windowId);
        }
    }

    private int chooseRandomWindowForStudent() {
        return random.nextInt(windowQueues.size());
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
            writeEvent(currentTime, "BALK", student.getId(), student.getGroupId(), student.getFinalWindowId(), -1, -1, -1, -1,
                    "学生放弃排队：" + reason);
        }
    }

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

            long queueWait = Math.max(0L, currentTime - student.getQueueEnterTime());
            writeEvent(currentTime, "SERVE_START", student.getId(), student.getGroupId(), i, -1, queueWait, -1, -1,
                    "学生开始在窗口 " + (i + 1) + " 打饭");

            updateQueueLength(i);
        }
    }

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

            long serviceDuration = Math.max(0L, currentTime - student.getServiceStartTime());
            writeEvent(currentTime, "SERVE_END", student.getId(), student.getGroupId(), i, -1,
                    Math.max(0L, student.getServiceStartTime() - student.getQueueEnterTime()), -1, serviceDuration,
                    "学生打饭结束，进入等待座位状态");
        }
    }

    /**
     * 尝试让已打完饭成员入座。
     * 同组第一批成员找到桌位后，后端立即为整组锁定容量；后续成员打完饭后直接坐到原桌。
     */
    private void trySeatWaitingGroups() {
        Iterator<Map.Entry<Integer, List<Student>>> iterator = waitingSeatByGroup.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, List<Student>> entry = iterator.next();
            int groupId = entry.getKey();
            List<Student> readyMembers = entry.getValue();
            if (readyMembers == null || readyMembers.isEmpty()) {
                iterator.remove();
                continue;
            }

            List<Student> allMembers = groupMembers.get(groupId);
            if (allMembers == null || allMembers.isEmpty()) {
                iterator.remove();
                continue;
            }

            int tableId = groupAssignedTable.getOrDefault(groupId, -1);
            if (tableId < 0) {
                int groupSize = allMembers.size();
                tableId = findRandomFreeTableForGroup(groupSize);
                boolean assignedToEmptyTable = tableId >= 0;
                if (tableId < 0) {
                    tableId = findSharedTableForGroup(groupSize);
                }
                if (tableId < 0) {
                    continue;
                }
                reserveSeatsForGroup(groupId, tableId, groupSize);
                if (assignedToEmptyTable) {
                    emptyTableSeatCount++;
                } else {
                    sharedTableSeatCount++;
                }
            }

            seatReadyMembersAtTable(groupId, tableId, readyMembers);
            iterator.remove();
        }
    }

    private void reserveSeatsForGroup(int groupId, int tableId, int groupSize) {
        Table table = tables.get(tableId);
        int oldReservedOrOccupied = tableReservedOrOccupiedSeats.getOrDefault(tableId, 0);
        int newReservedOrOccupied = oldReservedOrOccupied + groupSize;

        if (newReservedOrOccupied > table.getCapacity()) {
            throw new IllegalStateException("table capacity exceeded while reserving seats");
        }

        groupAssignedTable.put(groupId, tableId);
        groupReservedSeats.put(groupId, groupSize);
        tableReservedOrOccupiedSeats.put(tableId, newReservedOrOccupied);
        seatReservationCount++;

        writeEvent(currentTime, "SEAT_RESERVE", -1, groupId, -1, tableId, -1, -1, -1,
                "为小组 " + groupId + " 锁定桌 " + (tableId + 1) + " 的 " + groupSize + " 个座位容量");
    }

    private void seatReadyMembersAtTable(int groupId, int tableId, List<Student> readyMembers) {
        List<Student> seatedMembers = seatedMembersByGroup.computeIfAbsent(groupId, k -> new ArrayList<>());
        List<Student> newlySeated = new ArrayList<>();

        for (Student student : readyMembers) {
            if (student.getStatus() == StudentStatus.DINING) {
                continue;
            }
            newlySeated.add(student);
        }

        if (newlySeated.isEmpty()) {
            return;
        }

        Table table = tables.get(tableId);
        int assigned = table.assignSeats(groupId, newlySeated.size(), currentTime);
        if (assigned != newlySeated.size()) {
            throw new IllegalStateException("actual table seats are insufficient for ready group members");
        }

        for (Student student : newlySeated) {
            student.setTableId(tableId);
            student.setSeatAssignedTime(currentTime);
            student.setDiningStartTime(currentTime);
            student.setDiningEndTime(currentTime + Math.max(1, student.getDiningTime()));
            student.setStatus(StudentStatus.DINING);

            seatedMembers.add(student);
            diningStudents.add(student);

            long seatWait = Math.max(0L, currentTime - student.getServiceEndTime());
            writeEvent(currentTime, "SEAT", student.getId(), student.getGroupId(), student.getFinalWindowId(), tableId,
                    Math.max(0L, student.getServiceStartTime() - student.getQueueEnterTime()), seatWait, student.getDiningTime(),
                    "学生入座桌 " + (tableId + 1));
        }

        listener.onTableOccupancyChanged(tableId, table.getSeatGroupIds());
    }

    /** 优先找能容纳整组人数的空桌。 */
    private int findRandomFreeTableForGroup(int groupSize) {
        List<Integer> candidates = new ArrayList<>();

        for (Table table : tables) {
            int reservedOrOccupied = tableReservedOrOccupiedSeats.getOrDefault(table.getId(), 0);
            if (reservedOrOccupied == 0 && table.getCapacity() >= groupSize) {
                candidates.add(table.getId());
            }
        }

        if (candidates.isEmpty()) {
            return -1;
        }

        return candidates.get(random.nextInt(candidates.size()));
    }

    /** 无空桌时才允许拼桌，且不能占用其他组已经锁定的容量。 */
    private int findSharedTableForGroup(int groupSize) {
        List<Integer> candidates = new ArrayList<>();

        for (Table table : tables) {
            int reservedOrOccupied = tableReservedOrOccupiedSeats.getOrDefault(table.getId(), 0);
            if (reservedOrOccupied <= 0) {
                continue;
            }

            int emptySeats = table.getCapacity() - reservedOrOccupied;
            if (emptySeats >= groupSize) {
                candidates.add(table.getId());
            }
        }

        if (candidates.isEmpty()) {
            return -1;
        }

        int bestId = -1;
        int bestEmptySeats = Integer.MAX_VALUE;
        for (int tableId : candidates) {
            Table table = tables.get(tableId);
            int emptySeats = table.getCapacity() - tableReservedOrOccupiedSeats.getOrDefault(tableId, 0);
            if (emptySeats < bestEmptySeats) {
                bestEmptySeats = emptySeats;
                bestId = tableId;
            } else if (emptySeats == bestEmptySeats && random.nextBoolean()) {
                bestId = tableId;
            }
        }
        return bestId;
    }

    /** 同组必须全部实际入座，并且全部达到各自就餐结束时间后，整组一起离开。 */
    private void processDiningCompletions() {
        Iterator<Map.Entry<Integer, List<Student>>> iterator = seatedMembersByGroup.entrySet().iterator();
        Set<Integer> changedTables = new LinkedHashSet<>();

        while (iterator.hasNext()) {
            Map.Entry<Integer, List<Student>> entry = iterator.next();
            int groupId = entry.getKey();
            List<Student> seatedMembers = entry.getValue();
            List<Student> allMembers = groupMembers.get(groupId);

            if (allMembers == null || seatedMembers == null || seatedMembers.isEmpty()) {
                iterator.remove();
                continue;
            }

            if (seatedMembers.size() < allMembers.size()) {
                continue;
            }

            boolean allFinished = true;
            long groupLeaveTime = currentTime;
            for (Student student : seatedMembers) {
                if (student.getDiningEndTime() > currentTime) {
                    allFinished = false;
                    break;
                }
                groupLeaveTime = Math.max(groupLeaveTime, student.getDiningEndTime());
            }

            if (!allFinished) {
                continue;
            }

            int tableId = groupAssignedTable.getOrDefault(groupId, -1);
            if (tableId < 0) {
                tableId = seatedMembers.get(0).getTableId();
            }

            for (Student student : seatedMembers) {
                student.setStatus(StudentStatus.LEFT_NORMAL);
                student.setLeaveReason("正常就餐结束");
                student.setLeaveTime(groupLeaveTime);
                diningStudents.remove(student);

                writeEvent(groupLeaveTime, "LEAVE", student.getId(), student.getGroupId(), student.getFinalWindowId(), tableId,
                        Math.max(0L, student.getServiceStartTime() - student.getQueueEnterTime()),
                        Math.max(0L, student.getSeatAssignedTime() - student.getServiceEndTime()),
                        Math.max(0L, student.getDiningEndTime() - student.getDiningStartTime()),
                        "学生随小组一起离开");
            }

            if (tableId >= 0) {
                Table table = tables.get(tableId);
                table.releaseSeats(groupId, currentTime);

                int reservedSeats = groupReservedSeats.getOrDefault(groupId, seatedMembers.size());
                int remainingReservedOrOccupied = tableReservedOrOccupiedSeats.getOrDefault(tableId, 0) - reservedSeats;
                if (remainingReservedOrOccupied <= 0) {
                    tableReservedOrOccupiedSeats.remove(tableId);
                } else {
                    tableReservedOrOccupiedSeats.put(tableId, remainingReservedOrOccupied);
                }

                changedTables.add(tableId);
            }

            groupAssignedTable.remove(groupId);
            groupReservedSeats.remove(groupId);
            groupLeaveCount++;
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

    private void openReportWriters() {
        try {
            outputDir = Paths.get("simulation-output").toAbsolutePath();
            Files.createDirectories(outputDir);

            reportBaseName = LocalDateTime.now().format(REPORT_TIME_FORMATTER);
            timelineFilePath = outputDir.resolve("simulation_timeline_" + reportBaseName + ".csv");
            eventsFilePath = outputDir.resolve("simulation_events_" + reportBaseName + ".csv");
            summaryFilePath = outputDir.resolve("simulation_summary_" + reportBaseName + ".txt");
            htmlReportFilePath = outputDir.resolve("simulation_report_" + reportBaseName + ".html");
            reportFilePath = htmlReportFilePath;

            timelineWriter = new PrintWriter(Files.newBufferedWriter(timelineFilePath, StandardCharsets.UTF_8));
            eventWriter = new PrintWriter(Files.newBufferedWriter(eventsFilePath, StandardCharsets.UTF_8));

            writeTimelineHeader();
            writeEventHeader();

            System.out.println("Simulation timeline file: " + timelineFilePath);
            System.out.println("Simulation events file: " + eventsFilePath);
            System.out.println("Simulation summary file: " + summaryFilePath);
            System.out.println("Simulation HTML report: " + htmlReportFilePath);
        } catch (IOException e) {
            timelineWriter = null;
            eventWriter = null;
            reportFilePath = null;
            System.err.println("Failed to create simulation report files: " + e.getMessage());
        }
    }

    private void writeTimelineHeader() {
        if (timelineWriter == null || timelineHeaderWritten) {
            return;
        }

        timelineWriter.println(
                "timeSecond," +
                        "timeMinute," +
                        "phase," +
                        "totalStudents," +
                        "notArrivedStudents," +
                        "arrivedStudents," +
                        "windowQueues," +
                        "totalQueuing," +
                        "servingStudents," +
                        "waitingForSeatStudents," +
                        "diningStudents," +
                        "actualSeatedTables," +
                        "actualSeatedSeats," +
                        "reservedOrOccupiedTables," +
                        "reservedOrOccupiedSeats," +
                        "lockedOnlySeats," +
                        "leftNormally," +
                        "balkedStudents," +
                        "leftNoSeatStudents," +
                        "tableUtilization," +
                        "reservedCapacityUtilization," +
                        "actualTableSeatGroups," +
                        "tableReservationCounts"
        );
        timelineHeaderWritten = true;
    }

    private void writeEventHeader() {
        if (eventWriter == null || eventHeaderWritten) {
            return;
        }
        eventWriter.println(
                "timeSecond," +
                        "timeMinute," +
                        "eventType," +
                        "studentId," +
                        "groupId," +
                        "windowId," +
                        "tableId," +
                        "queueWaitSecond," +
                        "seatWaitSecond," +
                        "durationSecond," +
                        "description"
        );
        eventHeaderWritten = true;
    }

    private void writeCurrentSnapshot() {
        Snapshot snapshot = buildCurrentSnapshot();
        snapshots.add(snapshot);
        updateSampleAccumulators(snapshot);

        if (timelineWriter == null) {
            return;
        }

        timelineWriter.println(
                snapshot.timeSecond + "," +
                        formatDouble(snapshot.timeSecond / 60.0) + "," +
                        escapeCsv(snapshot.phase) + "," +
                        snapshot.totalStudents + "," +
                        snapshot.notArrivedStudents + "," +
                        snapshot.arrivedStudents + "," +
                        escapeCsv(snapshot.windowQueues) + "," +
                        snapshot.totalQueuing + "," +
                        snapshot.servingStudents + "," +
                        snapshot.waitingForSeatStudents + "," +
                        snapshot.diningStudents + "," +
                        snapshot.actualSeatedTables + "," +
                        snapshot.actualSeatedSeats + "," +
                        snapshot.reservedOrOccupiedTables + "," +
                        snapshot.reservedOrOccupiedSeats + "," +
                        snapshot.lockedOnlySeats + "," +
                        snapshot.leftNormally + "," +
                        snapshot.balkedStudents + "," +
                        snapshot.leftNoSeatStudents + "," +
                        formatDouble(snapshot.tableUtilization) + "," +
                        formatDouble(snapshot.reservedCapacityUtilization) + "," +
                        escapeCsv(snapshot.actualTableSeatGroups) + "," +
                        escapeCsv(snapshot.tableReservationCounts)
        );
        timelineWriter.flush();
    }

    private Snapshot buildCurrentSnapshot() {
        int totalStudents = allStudents.size();
        int arrivedStudents = countStudentsNotInStatus(StudentStatus.ARRIVING);
        int totalQueuing = getTotalQueueLength();
        int servingCount = getServingCount();
        int waitingForSeatCount = getWaitingSeatCount();
        int diningCount = diningStudents.size();
        int actualSeatedTableCount = getActualSeatedTableCount();
        int actualSeatedSeatCount = diningStudents.size();
        int reservedOrOccupiedTableCount = getReservedOrOccupiedTableCount();
        int reservedOrOccupiedSeatCount = getReservedOrOccupiedSeatCount();
        int lockedOnlySeatCount = Math.max(0, reservedOrOccupiedSeatCount - actualSeatedSeatCount);
        int leftNormally = countStudentsInStatus(StudentStatus.LEFT_NORMAL);
        int balkedStudents = countStudentsInStatus(StudentStatus.BALKED);
        int leftNoSeatStudents = countStudentsInStatus(StudentStatus.LEFT_NO_SEAT);
        int totalSeats = getTotalSeatCount();

        return new Snapshot(
                currentTime,
                getCurrentPhaseName(),
                totalStudents,
                Math.max(0, totalStudents - arrivedStudents),
                arrivedStudents,
                formatWindowQueues(),
                totalQueuing,
                servingCount,
                waitingForSeatCount,
                diningCount,
                actualSeatedTableCount,
                actualSeatedSeatCount,
                reservedOrOccupiedTableCount,
                reservedOrOccupiedSeatCount,
                lockedOnlySeatCount,
                leftNormally,
                balkedStudents,
                leftNoSeatStudents,
                totalSeats <= 0 ? 0.0 : actualSeatedSeatCount * 100.0 / totalSeats,
                totalSeats <= 0 ? 0.0 : reservedOrOccupiedSeatCount * 100.0 / totalSeats,
                formatActualTableSeatGroups(),
                formatTableReservationCounts()
        );
    }

    private void updateSampleAccumulators(Snapshot snapshot) {
        snapshotSampleCount++;
        for (int i = 0; i < windowQueues.size(); i++) {
            cumulativeQueueLengthByWindow[i] += windowQueues.get(i).size();
            if (servingStudents[i] != null) {
                busySampleCountByWindow[i]++;
            }
        }
    }

    private void writeEvent(long time,
                            String eventType,
                            int studentId,
                            int groupId,
                            int windowId,
                            int tableId,
                            long queueWaitSecond,
                            long seatWaitSecond,
                            long durationSecond,
                            String description) {
        if (eventWriter == null) {
            return;
        }
        eventWriter.println(
                time + "," +
                        formatDouble(time / 60.0) + "," +
                        escapeCsv(eventType) + "," +
                        emptyIfNegative(studentId) + "," +
                        emptyIfNegative(groupId) + "," +
                        emptyIfNegative(windowId >= 0 ? windowId + 1 : -1) + "," +
                        emptyIfNegative(tableId >= 0 ? tableId + 1 : -1) + "," +
                        emptyIfNegative(queueWaitSecond) + "," +
                        emptyIfNegative(seatWaitSecond) + "," +
                        emptyIfNegative(durationSecond) + "," +
                        escapeCsv(description)
        );
    }

    private void closeReportWriters() {
        if (timelineWriter != null) {
            timelineWriter.flush();
            timelineWriter.close();
            timelineWriter = null;
        }
        if (eventWriter != null) {
            eventWriter.flush();
            eventWriter.close();
            eventWriter = null;
        }
    }

    private void generateFinalReports() {
        if (outputDir == null || summaryFilePath == null || htmlReportFilePath == null) {
            return;
        }
        try {
            writeSummaryReport();
            writeHtmlReport();
            System.out.println("Simulation report generated: " + htmlReportFilePath);
        } catch (IOException e) {
            System.err.println("Failed to generate final report: " + e.getMessage());
        }
    }

    private void writeSummaryReport() throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(summaryFilePath, StandardCharsets.UTF_8))) {
            SummaryStats stats = calculateSummaryStats();
            writer.println("食堂营业仿真摘要报告");
            writer.println("====================");
            writer.println();
            writer.println("一、基本配置");
            writer.println("仿真时间单位：" + CanteenConfig.TIME_UNIT_DESCRIPTION);
            writer.println("仿真模式：" + CanteenConfig.SIMULATION_MODE);
            writer.println("餐段：" + CanteenConfig.MEAL_PERIOD);
            writer.println("输入人数：" + CanteenConfig.TOTAL_POPULATION);
            writer.println("实际生成学生数：" + allStudents.size());
            writer.println("营业时长：" + CanteenConfig.OPEN_DURATION + " 分钟");
            writer.println("桌子数量：" + CanteenConfig.TOTAL_TABLES);
            writer.println("总座位数：" + getTotalSeatCount());
            writer.println("窗口数量：" + CanteenConfig.getWindowCount());
            writer.println("随机种子：" + CanteenConfig.RANDOM_SEED);
            writer.println("学生耐心范围：" + formatSeconds(CanteenConfig.PATIENCE_MIN) + " - " + formatSeconds(CanteenConfig.PATIENCE_MAX));
            writer.println("窗口打饭时间：" + formatWindowServeTimes());
            writer.println("平均就餐时间：" + formatSeconds(Math.round(CanteenConfig.DINING_TIME_MEAN)));
            writer.println();

            writer.println("二、总体结果");
            writer.println("总到达人数：" + stats.arrivedStudents);
            writer.println("正常完成就餐人数：" + stats.leftNormally);
            writer.println("放弃排队人数：" + stats.balkedStudents);
            writer.println("仿真结束仍在系统内人数：" + stats.inSystemAtEnd);
            writer.println("完成率：" + formatPercent(stats.completionRate));
            writer.println("放弃率：" + formatPercent(stats.balkRate));
            writer.println("平均排队等待时间：" + formatSeconds(stats.avgQueueWait));
            writer.println("最大排队等待时间：" + formatSeconds(stats.maxQueueWait));
            writer.println("平均等待座位时间：" + formatSeconds(stats.avgSeatWait));
            writer.println("最大等待座位时间：" + formatSeconds(stats.maxSeatWait));
            writer.println("平均在餐厅停留时间：" + formatSeconds(stats.avgStayTime));
            writer.println();

            writer.println("三、窗口统计");
            for (int i = 0; i < windowStates.size(); i++) {
                WindowState ws = windowStates.get(i);
                writer.println("窗口 " + (i + 1)
                        + " | 服务时间=" + ws.getWindow().getAvgServeTime() + " 秒"
                        + " | 服务人数=" + ws.getServedCount()
                        + " | 最大队长=" + ws.getMaxQueueLength()
                        + " | 平均队长=" + formatDouble(getAverageWindowQueueLength(i))
                        + " | 利用率=" + formatPercent(getWindowUtilization(i)));
            }
            writer.println();

            writer.println("四、入座统计");
            writer.println("最大真实入座人数：" + stats.maxActualSeatedSeats);
            writer.println("最大锁定未入座座位数：" + stats.maxLockedOnlySeats);
            writer.println("最大等待座位人数：" + stats.maxWaitingSeatStudents);
            writer.println("最大总排队人数：" + stats.maxTotalQueuing);
            writer.println("最大正在打饭人数：" + stats.maxServingStudents);
            writer.println("平均桌位利用率：" + formatPercent(stats.avgTableUtilization));
            writer.println("平均后端占用容量利用率：" + formatPercent(stats.avgReservedCapacityUtilization));
            writer.println("空桌入座次数：" + emptyTableSeatCount);
            writer.println("拼桌入座次数：" + sharedTableSeatCount);
            writer.println("锁座次数：" + seatReservationCount);
            writer.println("整组离开次数：" + groupLeaveCount);
            writer.println();

            writer.println("五、输出文件");
            writer.println("HTML 报告：" + htmlReportFilePath);
            writer.println("时间线 CSV：" + timelineFilePath);
            writer.println("事件日志 CSV：" + eventsFilePath);
            writer.println("摘要 TXT：" + summaryFilePath);
        }
    }

    private void writeHtmlReport() throws IOException {
        SummaryStats stats = calculateSummaryStats();
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(htmlReportFilePath, StandardCharsets.UTF_8))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">");
            writer.println("<title>食堂营业仿真报告</title>");
            writer.println("<style>");
            writer.println("body{font-family:Arial,'Microsoft YaHei',sans-serif;margin:24px;background:#f6f7f9;color:#1f2937;}");
            writer.println("h1,h2{margin:18px 0 10px;} .card{background:#fff;border:1px solid #e5e7eb;border-radius:12px;padding:16px;margin:14px 0;box-shadow:0 1px 4px rgba(0,0,0,.04);}");
            writer.println("table{border-collapse:collapse;width:100%;margin:10px 0;} th,td{border:1px solid #e5e7eb;padding:8px;text-align:left;font-size:14px;} th{background:#f3f4f6;}");
            writer.println(".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px;} .metric{background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:12px;} .metric b{display:block;font-size:22px;margin-top:6px;}");
            writer.println("svg{width:100%;max-width:980px;height:280px;background:#fff;border:1px solid #e5e7eb;border-radius:8px;} .small{color:#6b7280;font-size:13px;} code{background:#f3f4f6;padding:2px 4px;border-radius:4px;}");
            writer.println("</style></head><body>");

            writer.println("<h1>食堂营业仿真报告</h1>");
            writer.println("<p class=\"small\">生成时间：" + escapeHtml(LocalDateTime.now().toString()) + "</p>");
            writer.println("<p class=\"small\">主报告文件：<code>" + escapeHtml(htmlReportFilePath.getFileName().toString()) + "</code>；原始时间线和事件日志见同目录 CSV 文件。</p>");

            writer.println("<div class=\"grid\">");
            writeMetric(writer, "实际生成学生数", String.valueOf(allStudents.size()));
            writeMetric(writer, "正常完成就餐", String.valueOf(stats.leftNormally));
            writeMetric(writer, "放弃排队", String.valueOf(stats.balkedStudents));
            writeMetric(writer, "完成率", formatPercent(stats.completionRate));
            writeMetric(writer, "最大总排队", String.valueOf(stats.maxTotalQueuing));
            writeMetric(writer, "最大等待座位", String.valueOf(stats.maxWaitingSeatStudents));
            writeMetric(writer, "最大真实入座", String.valueOf(stats.maxActualSeatedSeats));
            writeMetric(writer, "最大锁定未入座", String.valueOf(stats.maxLockedOnlySeats));
            writer.println("</div>");

            writer.println("<div class=\"card\"><h2>一、基本配置</h2>");
            writer.println("<table><tr><th>配置项</th><th>值</th></tr>");
            writeRow(writer, "时间单位", CanteenConfig.TIME_UNIT_DESCRIPTION);
            writeRow(writer, "仿真模式", String.valueOf(CanteenConfig.SIMULATION_MODE));
            writeRow(writer, "餐段", String.valueOf(CanteenConfig.MEAL_PERIOD));
            writeRow(writer, "输入人数", String.valueOf(CanteenConfig.TOTAL_POPULATION));
            writeRow(writer, "实际生成学生数", String.valueOf(allStudents.size()));
            writeRow(writer, "营业时长", CanteenConfig.OPEN_DURATION + " 分钟");
            writeRow(writer, "桌子数量", String.valueOf(CanteenConfig.TOTAL_TABLES));
            writeRow(writer, "总座位数", String.valueOf(getTotalSeatCount()));
            writeRow(writer, "窗口数量", String.valueOf(CanteenConfig.getWindowCount()));
            writeRow(writer, "随机种子", String.valueOf(CanteenConfig.RANDOM_SEED));
            writeRow(writer, "耐心范围", formatSeconds(CanteenConfig.PATIENCE_MIN) + " - " + formatSeconds(CanteenConfig.PATIENCE_MAX));
            writeRow(writer, "窗口打饭时间", formatWindowServeTimes());
            writeRow(writer, "平均就餐时间", formatSeconds(Math.round(CanteenConfig.DINING_TIME_MEAN)));
            writer.println("</table></div>");

            writer.println("<div class=\"card\"><h2>二、总体营业结果</h2>");
            writer.println("<table><tr><th>指标</th><th>值</th></tr>");
            writeRow(writer, "总到达人数", String.valueOf(stats.arrivedStudents));
            writeRow(writer, "正常完成就餐人数", String.valueOf(stats.leftNormally));
            writeRow(writer, "放弃排队人数", String.valueOf(stats.balkedStudents));
            writeRow(writer, "仿真结束仍在系统内人数", String.valueOf(stats.inSystemAtEnd));
            writeRow(writer, "完成率", formatPercent(stats.completionRate));
            writeRow(writer, "放弃率", formatPercent(stats.balkRate));
            writeRow(writer, "平均排队等待时间", formatSeconds(stats.avgQueueWait));
            writeRow(writer, "最大排队等待时间", formatSeconds(stats.maxQueueWait));
            writeRow(writer, "平均等待座位时间", formatSeconds(stats.avgSeatWait));
            writeRow(writer, "最大等待座位时间", formatSeconds(stats.maxSeatWait));
            writeRow(writer, "平均在餐厅停留时间", formatSeconds(stats.avgStayTime));
            writer.println("</table></div>");

            writer.println("<div class=\"card\"><h2>三、学生到达与状态变化</h2>");
            writer.println("<h3>每分钟到达人数</h3>");
            writer.println(buildSingleLineChart("每分钟到达人数", buildArrivalPerMinuteSeries(), "到达人数", "#2563eb"));
            writer.println("<h3>学生状态随时间变化</h3>");
            writer.println(buildMultiLineChart("状态人数", Arrays.asList(
                    new Series("排队", collectSnapshots("totalQueuing"), "#f97316"),
                    new Series("打饭", collectSnapshots("servingStudents"), "#7c3aed"),
                    new Series("等座", collectSnapshots("waitingForSeatStudents"), "#dc2626"),
                    new Series("就餐", collectSnapshots("diningStudents"), "#16a34a"),
                    new Series("正常离开", collectSnapshots("leftNormally"), "#0f766e"),
                    new Series("放弃", collectSnapshots("balkedStudents"), "#6b7280")
            )));
            writer.println("</div>");

            writer.println("<div class=\"card\"><h2>四、窗口排队与打饭</h2>");
            writer.println("<h3>总排队人数随时间变化</h3>");
            writer.println(buildSingleLineChart("总排队人数", collectSnapshots("totalQueuing"), "排队人数", "#f97316"));
            writer.println("<h3>各窗口统计</h3>");
            writer.println("<table><tr><th>窗口</th><th>服务时间</th><th>服务人数</th><th>最大队长</th><th>平均队长</th><th>利用率</th></tr>");
            for (int i = 0; i < windowStates.size(); i++) {
                WindowState ws = windowStates.get(i);
                writer.println("<tr><td>窗口 " + (i + 1) + "</td><td>" + ws.getWindow().getAvgServeTime() + " 秒</td><td>" + ws.getServedCount() + "</td><td>" + ws.getMaxQueueLength() + "</td><td>" + formatDouble(getAverageWindowQueueLength(i)) + "</td><td>" + formatPercent(getWindowUtilization(i)) + "</td></tr>");
            }
            writer.println("</table>");
            writer.println(buildBarChart("窗口服务人数", buildWindowServedSeries(), "服务人数", "#2563eb"));
            writer.println("</div>");

            writer.println("<div class=\"card\"><h2>五、桌位、拼桌与锁座</h2>");
            writer.println("<h3>真实入座与锁定座位</h3>");
            writer.println(buildMultiLineChart("座位状态", Arrays.asList(
                    new Series("真实入座", collectSnapshots("actualSeatedSeats"), "#16a34a"),
                    new Series("锁定未入座", collectSnapshots("lockedOnlySeats"), "#dc2626"),
                    new Series("后端总占用容量", collectSnapshots("reservedOrOccupiedSeats"), "#7c3aed")
            )));
            writer.println("<h3>入座统计</h3>");
            writer.println("<table><tr><th>指标</th><th>值</th></tr>");
            writeRow(writer, "最大真实入座人数", String.valueOf(stats.maxActualSeatedSeats));
            writeRow(writer, "最大锁定未入座座位数", String.valueOf(stats.maxLockedOnlySeats));
            writeRow(writer, "最大等待座位人数", String.valueOf(stats.maxWaitingSeatStudents));
            writeRow(writer, "平均桌位利用率", formatPercent(stats.avgTableUtilization));
            writeRow(writer, "平均后端占用容量利用率", formatPercent(stats.avgReservedCapacityUtilization));
            writeRow(writer, "空桌入座次数", String.valueOf(emptyTableSeatCount));
            writeRow(writer, "拼桌入座次数", String.valueOf(sharedTableSeatCount));
            writeRow(writer, "锁座次数", String.valueOf(seatReservationCount));
            writeRow(writer, "整组离开次数", String.valueOf(groupLeaveCount));
            writer.println("</table></div>");

            writer.println("<div class=\"card\"><h2>六、学生小组分布</h2>");
            writer.println(buildGroupDistributionTable());
            writer.println("</div>");

            writer.println("<div class=\"card\"><h2>七、原始输出文件</h2>");
            writer.println("<ul>");
            writer.println("<li>时间线 CSV：<code>" + escapeHtml(timelineFilePath.getFileName().toString()) + "</code></li>");
            writer.println("<li>事件日志 CSV：<code>" + escapeHtml(eventsFilePath.getFileName().toString()) + "</code></li>");
            writer.println("<li>摘要 TXT：<code>" + escapeHtml(summaryFilePath.getFileName().toString()) + "</code></li>");
            writer.println("</ul>");
            writer.println("<p class=\"small\">CSV 可以直接用 Excel 打开，用于二次画图或检查每个时刻的餐厅状态。</p>");
            writer.println("</div>");

            writer.println("</body></html>");
        }
    }

    private void writeMetric(PrintWriter writer, String label, String value) {
        writer.println("<div class=\"metric\"><span>" + escapeHtml(label) + "</span><b>" + escapeHtml(value) + "</b></div>");
    }

    private void writeRow(PrintWriter writer, String key, String value) {
        writer.println("<tr><td>" + escapeHtml(key) + "</td><td>" + escapeHtml(value) + "</td></tr>");
    }

    private SummaryStats calculateSummaryStats() {
        SummaryStats stats = new SummaryStats();
        stats.arrivedStudents = countStudentsNotInStatus(StudentStatus.ARRIVING);
        stats.leftNormally = countStudentsInStatus(StudentStatus.LEFT_NORMAL);
        stats.balkedStudents = countStudentsInStatus(StudentStatus.BALKED);
        stats.leftNoSeatStudents = countStudentsInStatus(StudentStatus.LEFT_NO_SEAT);
        stats.inSystemAtEnd = allStudents.size() - countStudentsInStatus(StudentStatus.ARRIVING)
                - stats.leftNormally - stats.balkedStudents - stats.leftNoSeatStudents;

        if (!allStudents.isEmpty()) {
            stats.completionRate = stats.leftNormally * 100.0 / allStudents.size();
            stats.balkRate = stats.balkedStudents * 100.0 / allStudents.size();
        }

        long queueWaitSum = 0;
        long seatWaitSum = 0;
        long stayTimeSum = 0;
        int queueWaitCount = 0;
        int seatWaitCount = 0;
        int stayTimeCount = 0;

        for (Student student : allStudents) {
            if (student.getServiceStartTime() >= 0 && student.getQueueEnterTime() >= 0) {
                long queueWait = Math.max(0L, student.getServiceStartTime() - student.getQueueEnterTime());
                queueWaitSum += queueWait;
                queueWaitCount++;
                stats.maxQueueWait = Math.max(stats.maxQueueWait, queueWait);
            }
            if (student.getSeatAssignedTime() >= 0 && student.getServiceEndTime() >= 0) {
                long seatWait = Math.max(0L, student.getSeatAssignedTime() - student.getServiceEndTime());
                seatWaitSum += seatWait;
                seatWaitCount++;
                stats.maxSeatWait = Math.max(stats.maxSeatWait, seatWait);
            }
            if (student.getLeaveTime() >= 0 && student.getArrivalTime() >= 0) {
                long stay = Math.max(0L, student.getLeaveTime() - student.getArrivalTime());
                stayTimeSum += stay;
                stayTimeCount++;
            }
        }

        stats.avgQueueWait = queueWaitCount == 0 ? 0 : queueWaitSum / (double) queueWaitCount;
        stats.avgSeatWait = seatWaitCount == 0 ? 0 : seatWaitSum / (double) seatWaitCount;
        stats.avgStayTime = stayTimeCount == 0 ? 0 : stayTimeSum / (double) stayTimeCount;

        double tableUtilizationSum = 0.0;
        double reservedUtilizationSum = 0.0;
        for (Snapshot s : snapshots) {
            stats.maxTotalQueuing = Math.max(stats.maxTotalQueuing, s.totalQueuing);
            stats.maxServingStudents = Math.max(stats.maxServingStudents, s.servingStudents);
            stats.maxWaitingSeatStudents = Math.max(stats.maxWaitingSeatStudents, s.waitingForSeatStudents);
            stats.maxActualSeatedSeats = Math.max(stats.maxActualSeatedSeats, s.actualSeatedSeats);
            stats.maxLockedOnlySeats = Math.max(stats.maxLockedOnlySeats, s.lockedOnlySeats);
            tableUtilizationSum += s.tableUtilization;
            reservedUtilizationSum += s.reservedCapacityUtilization;
        }
        if (!snapshots.isEmpty()) {
            stats.avgTableUtilization = tableUtilizationSum / snapshots.size();
            stats.avgReservedCapacityUtilization = reservedUtilizationSum / snapshots.size();
        }

        return stats;
    }

    private int countStudentsInStatus(StudentStatus status) {
        int count = 0;
        for (Student student : allStudents) {
            if (student.getStatus() == status) {
                count++;
            }
        }
        return count;
    }

    private int countStudentsNotInStatus(StudentStatus status) {
        int count = 0;
        for (Student student : allStudents) {
            if (student.getStatus() != status) {
                count++;
            }
        }
        return count;
    }

    private int getTotalQueueLength() {
        int total = 0;
        for (Deque<Student> queue : windowQueues) {
            total += queue.size();
        }
        return total;
    }

    private int getServingCount() {
        int count = 0;
        for (Student student : servingStudents) {
            if (student != null) {
                count++;
            }
        }
        return count;
    }

    private int getWaitingSeatCount() {
        int count = 0;
        for (List<Student> members : waitingSeatByGroup.values()) {
            count += members.size();
        }
        return count;
    }

    private int getActualSeatedTableCount() {
        int count = 0;
        for (Table table : tables) {
            if (table.getOccupiedSeatCount() > 0) {
                count++;
            }
        }
        return count;
    }

    private int getReservedOrOccupiedTableCount() {
        int count = 0;
        for (Integer occupiedSeats : tableReservedOrOccupiedSeats.values()) {
            if (occupiedSeats != null && occupiedSeats > 0) {
                count++;
            }
        }
        return count;
    }

    private int getReservedOrOccupiedSeatCount() {
        int count = 0;
        for (Integer occupiedSeats : tableReservedOrOccupiedSeats.values()) {
            if (occupiedSeats != null) {
                count += occupiedSeats;
            }
        }
        return count;
    }

    private int getTotalSeatCount() {
        int total = 0;
        for (Table table : tables) {
            total += table.getCapacity();
        }
        return total;
    }

    private String getCurrentPhaseName() {
        if (currentPhaseIndex >= 0 && currentPhaseIndex < phaseBoundaries.size()) {
            return phaseBoundaries.get(currentPhaseIndex).name;
        }
        return "未分段";
    }

    private String formatWindowQueues() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < windowQueues.size(); i++) {
            if (i > 0) {
                builder.append('|');
            }
            builder.append('W').append(i + 1).append('=').append(windowQueues.get(i).size());
        }
        return builder.toString();
    }

    private String formatActualTableSeatGroups() {
        List<String> parts = new ArrayList<>();
        for (Table table : tables) {
            if (table.getOccupiedSeatCount() <= 0) {
                continue;
            }
            parts.add("T" + (table.getId() + 1) + "=" + Arrays.toString(table.getSeatGroupIds()));
        }
        if (parts.isEmpty()) {
            return "none";
        }
        return String.join("|", parts);
    }

    private String formatTableReservationCounts() {
        if (tableReservedOrOccupiedSeats.isEmpty()) {
            return "none";
        }

        List<Integer> tableIds = new ArrayList<>(tableReservedOrOccupiedSeats.keySet());
        tableIds.sort(Integer::compareTo);

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tableIds.size(); i++) {
            int tableId = tableIds.get(i);
            if (i > 0) {
                builder.append('|');
            }
            builder.append('T').append(tableId + 1).append('=').append(tableReservedOrOccupiedSeats.get(tableId));
        }
        return builder.toString();
    }

    private String formatWindowServeTimes() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < CanteenConfig.WINDOW_AVG_SERVE_TIME.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("W").append(i + 1).append("=").append(CanteenConfig.WINDOW_AVG_SERVE_TIME[i]).append("s");
        }
        return builder.toString();
    }

    private double getAverageWindowQueueLength(int windowIndex) {
        if (snapshotSampleCount <= 0 || windowIndex < 0 || windowIndex >= cumulativeQueueLengthByWindow.length) {
            return 0.0;
        }
        return cumulativeQueueLengthByWindow[windowIndex] / (double) snapshotSampleCount;
    }

    private double getWindowUtilization(int windowIndex) {
        if (snapshotSampleCount <= 0 || windowIndex < 0 || windowIndex >= busySampleCountByWindow.length) {
            return 0.0;
        }
        return busySampleCountByWindow[windowIndex] * 100.0 / snapshotSampleCount;
    }

    private List<Integer> buildArrivalPerMinuteSeries() {
        int maxMinute = 0;
        for (Student student : allStudents) {
            maxMinute = Math.max(maxMinute, (int) (student.getArrivalTime() / 60));
        }
        List<Integer> result = new ArrayList<>(Collections.nCopies(maxMinute + 1, 0));
        for (Student student : allStudents) {
            int minute = (int) (student.getArrivalTime() / 60);
            result.set(minute, result.get(minute) + 1);
        }
        return result;
    }

    private List<Integer> buildWindowServedSeries() {
        List<Integer> result = new ArrayList<>();
        for (WindowState ws : windowStates) {
            result.add(ws.getServedCount());
        }
        return result;
    }

    private List<Integer> collectSnapshots(String field) {
        List<Integer> result = new ArrayList<>();
        int step = Math.max(1, snapshots.size() / 500);
        for (int i = 0; i < snapshots.size(); i += step) {
            Snapshot s = snapshots.get(i);
            switch (field) {
                case "totalQueuing": result.add(s.totalQueuing); break;
                case "servingStudents": result.add(s.servingStudents); break;
                case "waitingForSeatStudents": result.add(s.waitingForSeatStudents); break;
                case "diningStudents": result.add(s.diningStudents); break;
                case "leftNormally": result.add(s.leftNormally); break;
                case "balkedStudents": result.add(s.balkedStudents); break;
                case "actualSeatedSeats": result.add(s.actualSeatedSeats); break;
                case "lockedOnlySeats": result.add(s.lockedOnlySeats); break;
                case "reservedOrOccupiedSeats": result.add(s.reservedOrOccupiedSeats); break;
                default: result.add(0);
            }
        }
        return result;
    }

    private String buildGroupDistributionTable() {
        Map<Integer, Integer> groupsBySize = new HashMap<>();
        int totalMembers = 0;
        for (List<Student> members : groupMembers.values()) {
            int size = members == null ? 0 : members.size();
            groupsBySize.put(size, groupsBySize.getOrDefault(size, 0) + 1);
            totalMembers += size;
        }
        StringBuilder html = new StringBuilder();
        html.append("<table><tr><th>小组人数</th><th>小组数量</th><th>学生人数</th></tr>");
        List<Integer> sizes = new ArrayList<>(groupsBySize.keySet());
        sizes.sort(Integer::compareTo);
        for (Integer size : sizes) {
            int groupCount = groupsBySize.get(size);
            html.append("<tr><td>").append(size).append(" 人组</td><td>").append(groupCount).append("</td><td>").append(groupCount * size).append("</td></tr>");
        }
        html.append("<tr><th>合计</th><th>").append(groupMembers.size()).append("</th><th>").append(totalMembers).append("</th></tr>");
        html.append("</table>");
        return html.toString();
    }

    private String buildSingleLineChart(String title, List<Integer> values, String label, String color) {
        return buildMultiLineChart(title, Collections.singletonList(new Series(label, values, color)));
    }

    private String buildMultiLineChart(String title, List<Series> seriesList) {
        int width = 980;
        int height = 280;
        int left = 46;
        int right = 20;
        int top = 24;
        int bottom = 38;
        int plotWidth = width - left - right;
        int plotHeight = height - top - bottom;

        int maxY = 1;
        int maxN = 1;
        for (Series series : seriesList) {
            maxN = Math.max(maxN, series.values.size());
            for (Integer value : series.values) {
                maxY = Math.max(maxY, value == null ? 0 : value);
            }
        }

        StringBuilder svg = new StringBuilder();
        svg.append("<svg viewBox=\"0 0 ").append(width).append(' ').append(height).append("\" role=\"img\" aria-label=\"").append(escapeHtml(title)).append("\">");
        svg.append("<text x=\"16\" y=\"18\" font-size=\"14\" fill=\"#111827\">").append(escapeHtml(title)).append("</text>");
        svg.append("<line x1=\"").append(left).append("\" y1=\"").append(top + plotHeight).append("\" x2=\"").append(left + plotWidth).append("\" y2=\"").append(top + plotHeight).append("\" stroke=\"#9ca3af\"/>");
        svg.append("<line x1=\"").append(left).append("\" y1=\"").append(top).append("\" x2=\"").append(left).append("\" y2=\"").append(top + plotHeight).append("\" stroke=\"#9ca3af\"/>");
        svg.append("<text x=\"8\" y=\"").append(top + 6).append("\" font-size=\"11\" fill=\"#6b7280\">").append(maxY).append("</text>");
        svg.append("<text x=\"8\" y=\"").append(top + plotHeight).append("\" font-size=\"11\" fill=\"#6b7280\">0</text>");

        int legendX = left + 8;
        int legendY = height - 12;
        for (Series series : seriesList) {
            svg.append("<circle cx=\"").append(legendX).append("\" cy=\"").append(legendY - 4).append("\" r=\"4\" fill=\"").append(series.color).append("\"/>");
            svg.append("<text x=\"").append(legendX + 8).append("\" y=\"").append(legendY).append("\" font-size=\"11\" fill=\"#374151\">").append(escapeHtml(series.name)).append("</text>");
            legendX += 80 + series.name.length() * 5;
        }

        for (Series series : seriesList) {
            svg.append("<polyline fill=\"none\" stroke=\"").append(series.color).append("\" stroke-width=\"2\" points=\"");
            for (int i = 0; i < series.values.size(); i++) {
                int value = series.values.get(i) == null ? 0 : series.values.get(i);
                double x = left + (series.values.size() == 1 ? 0 : i * plotWidth / (double) (series.values.size() - 1));
                double y = top + plotHeight - value * plotHeight / (double) maxY;
                svg.append(formatDouble(x)).append(',').append(formatDouble(y)).append(' ');
            }
            svg.append("\"/>");
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private String buildBarChart(String title, List<Integer> values, String label, String color) {
        int width = 980;
        int height = 280;
        int left = 46;
        int right = 20;
        int top = 24;
        int bottom = 42;
        int plotWidth = width - left - right;
        int plotHeight = height - top - bottom;
        int maxY = 1;
        for (Integer value : values) {
            maxY = Math.max(maxY, value == null ? 0 : value);
        }
        int count = Math.max(1, values.size());
        double barSpace = plotWidth / (double) count;
        double barWidth = Math.max(8, barSpace * 0.62);

        StringBuilder svg = new StringBuilder();
        svg.append("<svg viewBox=\"0 0 ").append(width).append(' ').append(height).append("\" role=\"img\" aria-label=\"").append(escapeHtml(title)).append("\">");
        svg.append("<text x=\"16\" y=\"18\" font-size=\"14\" fill=\"#111827\">").append(escapeHtml(title)).append("</text>");
        svg.append("<line x1=\"").append(left).append("\" y1=\"").append(top + plotHeight).append("\" x2=\"").append(left + plotWidth).append("\" y2=\"").append(top + plotHeight).append("\" stroke=\"#9ca3af\"/>");
        svg.append("<line x1=\"").append(left).append("\" y1=\"").append(top).append("\" x2=\"").append(left).append("\" y2=\"").append(top + plotHeight).append("\" stroke=\"#9ca3af\"/>");
        svg.append("<text x=\"8\" y=\"").append(top + 6).append("\" font-size=\"11\" fill=\"#6b7280\">").append(maxY).append("</text>");
        for (int i = 0; i < values.size(); i++) {
            int value = values.get(i) == null ? 0 : values.get(i);
            double x = left + i * barSpace + (barSpace - barWidth) / 2.0;
            double barHeight = value * plotHeight / (double) maxY;
            double y = top + plotHeight - barHeight;
            svg.append("<rect x=\"").append(formatDouble(x)).append("\" y=\"").append(formatDouble(y)).append("\" width=\"").append(formatDouble(barWidth)).append("\" height=\"").append(formatDouble(barHeight)).append("\" fill=\"").append(color).append("\"/>");
            svg.append("<text x=\"").append(formatDouble(x + barWidth / 2)).append("\" y=\"").append(top + plotHeight + 16).append("\" font-size=\"11\" text-anchor=\"middle\" fill=\"#374151\">W").append(i + 1).append("</text>");
            svg.append("<text x=\"").append(formatDouble(x + barWidth / 2)).append("\" y=\"").append(formatDouble(Math.max(top + 12, y - 4))).append("\" font-size=\"10\" text-anchor=\"middle\" fill=\"#374151\">" ).append(value).append("</text>");
        }
        svg.append("<text x=\"").append(left + 8).append("\" y=\"").append(height - 8).append("\" font-size=\"11\" fill=\"#374151\">").append(escapeHtml(label)).append("</text>");
        svg.append("</svg>");
        return svg.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "\"\"";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String emptyIfNegative(long value) {
        return value < 0 ? "" : String.valueOf(value);
    }

    private String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String formatPercent(double value) {
        return formatDouble(value) + "%";
    }

    private String formatSeconds(double seconds) {
        return formatSeconds(Math.round(seconds));
    }

    private String formatSeconds(long seconds) {
        if (seconds < 0) {
            return "-";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (minutes <= 0) {
            return remainingSeconds + " 秒";
        }
        return minutes + " 分 " + remainingSeconds + " 秒";
    }

    private static class Snapshot {
        final long timeSecond;
        final String phase;
        final int totalStudents;
        final int notArrivedStudents;
        final int arrivedStudents;
        final String windowQueues;
        final int totalQueuing;
        final int servingStudents;
        final int waitingForSeatStudents;
        final int diningStudents;
        final int actualSeatedTables;
        final int actualSeatedSeats;
        final int reservedOrOccupiedTables;
        final int reservedOrOccupiedSeats;
        final int lockedOnlySeats;
        final int leftNormally;
        final int balkedStudents;
        final int leftNoSeatStudents;
        final double tableUtilization;
        final double reservedCapacityUtilization;
        final String actualTableSeatGroups;
        final String tableReservationCounts;

        Snapshot(long timeSecond,
                 String phase,
                 int totalStudents,
                 int notArrivedStudents,
                 int arrivedStudents,
                 String windowQueues,
                 int totalQueuing,
                 int servingStudents,
                 int waitingForSeatStudents,
                 int diningStudents,
                 int actualSeatedTables,
                 int actualSeatedSeats,
                 int reservedOrOccupiedTables,
                 int reservedOrOccupiedSeats,
                 int lockedOnlySeats,
                 int leftNormally,
                 int balkedStudents,
                 int leftNoSeatStudents,
                 double tableUtilization,
                 double reservedCapacityUtilization,
                 String actualTableSeatGroups,
                 String tableReservationCounts) {
            this.timeSecond = timeSecond;
            this.phase = phase;
            this.totalStudents = totalStudents;
            this.notArrivedStudents = notArrivedStudents;
            this.arrivedStudents = arrivedStudents;
            this.windowQueues = windowQueues;
            this.totalQueuing = totalQueuing;
            this.servingStudents = servingStudents;
            this.waitingForSeatStudents = waitingForSeatStudents;
            this.diningStudents = diningStudents;
            this.actualSeatedTables = actualSeatedTables;
            this.actualSeatedSeats = actualSeatedSeats;
            this.reservedOrOccupiedTables = reservedOrOccupiedTables;
            this.reservedOrOccupiedSeats = reservedOrOccupiedSeats;
            this.lockedOnlySeats = lockedOnlySeats;
            this.leftNormally = leftNormally;
            this.balkedStudents = balkedStudents;
            this.leftNoSeatStudents = leftNoSeatStudents;
            this.tableUtilization = tableUtilization;
            this.reservedCapacityUtilization = reservedCapacityUtilization;
            this.actualTableSeatGroups = actualTableSeatGroups;
            this.tableReservationCounts = tableReservationCounts;
        }
    }

    private static class SummaryStats {
        int arrivedStudents;
        int leftNormally;
        int balkedStudents;
        int leftNoSeatStudents;
        int inSystemAtEnd;
        double completionRate;
        double balkRate;
        double avgQueueWait;
        long maxQueueWait;
        double avgSeatWait;
        long maxSeatWait;
        double avgStayTime;
        int maxTotalQueuing;
        int maxServingStudents;
        int maxWaitingSeatStudents;
        int maxActualSeatedSeats;
        int maxLockedOnlySeats;
        double avgTableUtilization;
        double avgReservedCapacityUtilization;
    }

    private static class Series {
        final String name;
        final List<Integer> values;
        final String color;

        Series(String name, List<Integer> values, String color) {
            this.name = name;
            this.values = values == null ? Collections.emptyList() : values;
            this.color = color;
        }
    }
}
