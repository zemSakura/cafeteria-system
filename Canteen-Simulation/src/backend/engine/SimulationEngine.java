package backend.engine;

import backend.config.CanteenConfig;
import backend.model.ArrivalGenerationResult;
import backend.model.Student;
import backend.model.StudentStatus;
import backend.model.Table;
import backend.model.Window;
import backend.model.WindowState;
import frontend.SimulationEventListener;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
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
 * 文件说明：排队、入座与营业信息输出的核心实现文件。
 *
 * 作用定位：
 * 1. 这是后端正式运行的事件驱动仿真引擎，负责串联“学生到达 -> 选择窗口 -> 排队 -> 打饭 -> 等座 -> 入座 -> 就餐 -> 离开”的完整流程。
 * 2. 排队逻辑主要集中在 processArrivals、handleGroupArrival、estimateWindowWait、processServiceStarts、processServiceCompletions、updateQueueLength 等方法。
 * 3. 入座逻辑主要集中在 trySeatWaitingGroups、reserveSeatsForGroup、seatReadyMembersAtTable、findRandomFreeTableForGroup、findSharedTableForGroup、processDiningCompletions 等方法。
 * 4. 本文件保留队友新增的前端事件通知：学生到达、进入窗口队列、入座、离开、餐段切换都会实时通知 MainDashboard。
 * 5. 本文件同时负责输出营业信息文件：时间线 CSV、事件 CSV、摘要 TXT、HTML 报告。运行前会清理旧的 simulation-output 报告文件，避免无用文件越积越多。
 *
 * 核心规则：
 * - 后端仿真时间单位为秒。
 * - 学生按到达时间进入系统，同组学生统一判断是否放弃排队。
 * - 同组成员到达后，每个学生独立选择窗口排队。
 * - 学生完成服务后进入等座区；同组中先打完饭的人可以先占座。
 * - 一旦找到桌子，后端会为整组锁定座位容量，防止后续同组成员没有座位。
 * - 前端只显示 Table 中真实已经入座的 seatGroupIds，锁定但未入座的位置不会提前标红。
 * - 同组成员全部实际入座并全部达到就餐结束时间后，整组一起离开并释放座位。
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

    /** 用于最终生成摘要和 HTML 报告。 */
    private final List<Snapshot> snapshots = new ArrayList<>();

    private long[] cumulativeQueueLengthByWindow;
    private long[] busySampleCountByWindow;
    private long snapshotSampleCount = 0L;

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
                    listener.onStudentArrived(member.getId(), member.getGroupId(), currentTime);
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
            listener.onStudentQueuedAtWindow(student.getId(), student.getGroupId(), windowId,
                    windowQueues.get(windowId).size(), currentTime);
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
            listener.onStudentLeft(student.getId(), student.getGroupId(), -1, reason, currentTime);
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
            listener.onStudentSeatedAtTable(student.getId(), student.getGroupId(), tableId, currentTime);

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
                listener.onStudentLeft(student.getId(), student.getGroupId(), tableId, student.getLeaveReason(), groupLeaveTime);
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
            cleanOldGeneratedReports(outputDir);

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

    private void cleanOldGeneratedReports(Path dir) {
        String[] patterns = {
                "simulation_result_*.csv",
                "simulation_timeline_*.csv",
                "simulation_events_*.csv",
                "simulation_summary_*.txt",
                "simulation_report_*.html"
        };

        for (String pattern : patterns) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, pattern)) {
                for (Path file : stream) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                        // 删除失败不影响仿真运行；新报告仍会按时间戳生成。
                    }
                }
            } catch (IOException ignored) {
                // 目录不存在或遍历失败时直接跳过。
            }
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

        int interval = Math.max(1, CanteenConfig.SNAPSHOT_INTERVAL);
        boolean shouldWrite = snapshot.timeSecond == 0 || snapshot.timeSecond % interval == 0 || isSimulationFinished();
        if (timelineWriter == null || !shouldWrite) {
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
        int notArrivedStudents = totalStudents - arrivedStudents;
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
        int totalSeats = tables.size() * 4;
        double tableUtilization = totalSeats <= 0 ? 0.0 : actualSeatedSeatCount * 100.0 / totalSeats;
        double reservedCapacityUtilization = totalSeats <= 0 ? 0.0 : reservedOrOccupiedSeatCount * 100.0 / totalSeats;

        return new Snapshot(
                currentTime,
                getCurrentPhaseName(),
                totalStudents,
                notArrivedStudents,
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
                tableUtilization,
                reservedCapacityUtilization,
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

    private void writeEvent(long timeSecond,
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
                timeSecond + "," +
                        formatDouble(timeSecond / 60.0) + "," +
                        escapeCsv(eventType) + "," +
                        emptyIfNegative(studentId) + "," +
                        emptyIfNegative(groupId) + "," +
                        emptyIfNegative(windowId < 0 ? -1 : windowId + 1) + "," +
                        emptyIfNegative(tableId < 0 ? -1 : tableId + 1) + "," +
                        emptyIfNegative(queueWaitSecond) + "," +
                        emptyIfNegative(seatWaitSecond) + "," +
                        emptyIfNegative(durationSecond) + "," +
                        escapeCsv(description)
        );
        eventWriter.flush();
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
        if (summaryFilePath == null || htmlReportFilePath == null) {
            return;
        }
        SummaryStats stats = calculateSummaryStats();
        writeSummaryReport(stats);
        writeHtmlReport(stats);
    }

    private SummaryStats calculateSummaryStats() {
        SummaryStats stats = new SummaryStats();
        stats.totalStudents = allStudents.size();
        stats.arrivedStudents = countStudentsNotInStatus(StudentStatus.ARRIVING);
        stats.leftNormally = countStudentsInStatus(StudentStatus.LEFT_NORMAL);
        stats.balkedStudents = countStudentsInStatus(StudentStatus.BALKED);
        stats.leftNoSeatStudents = countStudentsInStatus(StudentStatus.LEFT_NO_SEAT);
        stats.inSystemAtEnd = stats.totalStudents - stats.leftNormally - stats.balkedStudents - stats.leftNoSeatStudents - countStudentsInStatus(StudentStatus.ARRIVING);
        stats.completionRate = stats.totalStudents == 0 ? 0.0 : stats.leftNormally * 100.0 / stats.totalStudents;
        stats.balkRate = stats.totalStudents == 0 ? 0.0 : stats.balkedStudents * 100.0 / stats.totalStudents;

        long queueWaitSum = 0L;
        int queueWaitCount = 0;
        long seatWaitSum = 0L;
        int seatWaitCount = 0;
        long stayTimeSum = 0L;
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
            if (student.getLeaveTime() >= 0) {
                long stayTime = Math.max(0L, student.getLeaveTime() - student.getArrivalTime());
                stayTimeSum += stayTime;
                stayTimeCount++;
            }
        }

        stats.avgQueueWait = queueWaitCount == 0 ? 0.0 : queueWaitSum / (double) queueWaitCount;
        stats.avgSeatWait = seatWaitCount == 0 ? 0.0 : seatWaitSum / (double) seatWaitCount;
        stats.avgStayTime = stayTimeCount == 0 ? 0.0 : stayTimeSum / (double) stayTimeCount;

        for (Snapshot snapshot : snapshots) {
            stats.maxTotalQueuing = Math.max(stats.maxTotalQueuing, snapshot.totalQueuing);
            stats.maxServingStudents = Math.max(stats.maxServingStudents, snapshot.servingStudents);
            stats.maxWaitingSeatStudents = Math.max(stats.maxWaitingSeatStudents, snapshot.waitingForSeatStudents);
            stats.maxActualSeatedSeats = Math.max(stats.maxActualSeatedSeats, snapshot.actualSeatedSeats);
            stats.maxLockedOnlySeats = Math.max(stats.maxLockedOnlySeats, snapshot.lockedOnlySeats);
            stats.avgTableUtilization += snapshot.tableUtilization;
            stats.avgReservedCapacityUtilization += snapshot.reservedCapacityUtilization;
        }
        if (!snapshots.isEmpty()) {
            stats.avgTableUtilization /= snapshots.size();
            stats.avgReservedCapacityUtilization /= snapshots.size();
        }

        return stats;
    }

    private void writeSummaryReport(SummaryStats stats) {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(summaryFilePath, StandardCharsets.UTF_8))) {
            writer.println("食堂就餐仿真系统营业摘要");
            writer.println("========================");
            writer.println("报告时间戳: " + reportBaseName);
            writer.println("主报告文件: " + htmlReportFilePath);
            writer.println();
            writer.println("一、基础配置");
            writer.println("总学生数: " + stats.totalStudents);
            writer.println("桌子数量: " + tables.size());
            writer.println("总座位数: " + (tables.size() * 4));
            writer.println("窗口数量: " + windowStates.size());
            writer.println("窗口平均服务时间: " + Arrays.toString(CanteenConfig.WINDOW_AVG_SERVE_TIME) + " 秒");
            writer.println("学生耐心范围: " + formatSeconds(CanteenConfig.PATIENCE_MIN) + " - " + formatSeconds(CanteenConfig.PATIENCE_MAX));
            writer.println("快照输出间隔: " + CanteenConfig.SNAPSHOT_INTERVAL + " 秒");
            writer.println();
            writer.println("二、就餐结果");
            writer.println("已到达学生数: " + stats.arrivedStudents);
            writer.println("正常完成就餐人数: " + stats.leftNormally);
            writer.println("放弃排队人数: " + stats.balkedStudents);
            writer.println("因无座离开人数: " + stats.leftNoSeatStudents);
            writer.println("结束时仍在系统内人数: " + stats.inSystemAtEnd);
            writer.println("完成率: " + formatPercent(stats.completionRate));
            writer.println("放弃率: " + formatPercent(stats.balkRate));
            writer.println();
            writer.println("三、等待与拥堵");
            writer.println("平均排队等待: " + formatSeconds(stats.avgQueueWait));
            writer.println("最大排队等待: " + formatSeconds(stats.maxQueueWait));
            writer.println("平均等座等待: " + formatSeconds(stats.avgSeatWait));
            writer.println("最大等座等待: " + formatSeconds(stats.maxSeatWait));
            writer.println("平均系统停留时间: " + formatSeconds(stats.avgStayTime));
            writer.println("最大总排队人数: " + stats.maxTotalQueuing);
            writer.println("最大等座人数: " + stats.maxWaitingSeatStudents);
            writer.println("最大实际入座人数: " + stats.maxActualSeatedSeats);
            writer.println("最大锁定但未实际入座座位数: " + stats.maxLockedOnlySeats);
            writer.println();
            writer.println("四、座位与窗口");
            writer.println("平均桌位利用率: " + formatPercent(stats.avgTableUtilization));
            writer.println("平均预留容量利用率: " + formatPercent(stats.avgReservedCapacityUtilization));
            writer.println("空桌入座次数: " + emptyTableSeatCount);
            writer.println("拼桌入座次数: " + sharedTableSeatCount);
            writer.println("座位锁定次数: " + seatReservationCount);
            writer.println("整组离开次数: " + groupLeaveCount);
            writer.println();
            writer.println("五、输出文件");
            writer.println("时间线 CSV: " + timelineFilePath);
            writer.println("事件 CSV: " + eventsFilePath);
            writer.println("摘要 TXT: " + summaryFilePath);
            writer.println("HTML 报告: " + htmlReportFilePath);
        } catch (IOException e) {
            System.err.println("Failed to write summary report: " + e.getMessage());
        }
    }

    private void writeHtmlReport(SummaryStats stats) {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(htmlReportFilePath, StandardCharsets.UTF_8))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html lang=\"zh-CN\">");
            writer.println("<head>");
            writer.println("<meta charset=\"UTF-8\">");
            writer.println("<title>食堂就餐仿真营业报告</title>");
            writer.println("<style>");
            writer.println("body{font-family:Arial,'Microsoft YaHei',sans-serif;margin:24px;background:#f6f7fb;color:#111827;}");
            writer.println("h1,h2{margin:0 0 14px;} .section{background:white;border-radius:12px;padding:18px;margin:16px 0;box-shadow:0 1px 5px rgba(0,0,0,.08);}");
            writer.println(".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px;} .card{background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;padding:12px;}");
            writer.println(".value{font-size:24px;font-weight:700;margin-top:6px;} table{border-collapse:collapse;width:100%;} th,td{border:1px solid #e5e7eb;padding:8px;text-align:left;} th{background:#f3f4f6;}");
            writer.println(".bar-wrap{background:#e5e7eb;border-radius:999px;height:14px;overflow:hidden;} .bar{height:14px;background:#4f46e5;} .muted{color:#6b7280;font-size:13px;}");
            writer.println("</style>");
            writer.println("</head><body>");
            writer.println("<h1>食堂就餐仿真营业报告</h1>");
            writer.println("<p class=\"muted\">生成时间戳：" + escapeHtml(reportBaseName) + "</p>");

            writer.println("<div class=\"section\"><h2>结果概览</h2><div class=\"grid\">");
            writeMetricCard(writer, "总学生数", String.valueOf(stats.totalStudents));
            writeMetricCard(writer, "完成就餐人数", String.valueOf(stats.leftNormally));
            writeMetricCard(writer, "完成率", formatPercent(stats.completionRate));
            writeMetricCard(writer, "放弃排队人数", String.valueOf(stats.balkedStudents));
            writeMetricCard(writer, "放弃率", formatPercent(stats.balkRate));
            writeMetricCard(writer, "平均排队等待", formatSeconds(stats.avgQueueWait));
            writeMetricCard(writer, "平均等座等待", formatSeconds(stats.avgSeatWait));
            writeMetricCard(writer, "平均停留时间", formatSeconds(stats.avgStayTime));
            writer.println("</div></div>");

            writer.println("<div class=\"section\"><h2>关键拥堵指标</h2>");
            writer.println("<table><tr><th>指标</th><th>数值</th></tr>");
            writeTableRow(writer, "最大总排队人数", String.valueOf(stats.maxTotalQueuing));
            writeTableRow(writer, "最大窗口服务中人数", String.valueOf(stats.maxServingStudents));
            writeTableRow(writer, "最大等座人数", String.valueOf(stats.maxWaitingSeatStudents));
            writeTableRow(writer, "最大实际入座人数", String.valueOf(stats.maxActualSeatedSeats));
            writeTableRow(writer, "最大锁定但未入座座位数", String.valueOf(stats.maxLockedOnlySeats));
            writeTableRow(writer, "平均桌位利用率", formatPercent(stats.avgTableUtilization));
            writeTableRow(writer, "平均预留容量利用率", formatPercent(stats.avgReservedCapacityUtilization));
            writer.println("</table></div>");

            writer.println("<div class=\"section\"><h2>窗口统计</h2>");
            writer.println("<table><tr><th>窗口</th><th>累计服务人数</th><th>最大队列长度</th><th>平均队列长度</th><th>忙碌采样占比</th></tr>");
            for (int i = 0; i < windowStates.size(); i++) {
                WindowState state = windowStates.get(i);
                double avgQueue = snapshotSampleCount == 0 ? 0.0 : cumulativeQueueLengthByWindow[i] / (double) snapshotSampleCount;
                double busyRate = snapshotSampleCount == 0 ? 0.0 : busySampleCountByWindow[i] * 100.0 / snapshotSampleCount;
                writer.println("<tr><td>窗口 " + (i + 1) + "</td><td>" + state.getServedCount() + "</td><td>" + state.getMaxQueueLength() + "</td><td>" + formatDouble(avgQueue) + "</td><td>" + formatPercent(busyRate) + "</td></tr>");
            }
            writer.println("</table></div>");

            writer.println("<div class=\"section\"><h2>完成率条形图</h2>");
            writer.println("<p>完成率</p><div class=\"bar-wrap\"><div class=\"bar\" style=\"width:" + Math.min(100.0, stats.completionRate) + "%\"></div></div>");
            writer.println("<p>放弃率</p><div class=\"bar-wrap\"><div class=\"bar\" style=\"width:" + Math.min(100.0, stats.balkRate) + "%;background:#dc2626\"></div></div>");
            writer.println("</div>");

            writer.println("<div class=\"section\"><h2>输出文件</h2><table><tr><th>文件</th><th>路径</th></tr>");
            writeTableRow(writer, "时间线 CSV", String.valueOf(timelineFilePath));
            writeTableRow(writer, "事件 CSV", String.valueOf(eventsFilePath));
            writeTableRow(writer, "摘要 TXT", String.valueOf(summaryFilePath));
            writeTableRow(writer, "HTML 报告", String.valueOf(htmlReportFilePath));
            writer.println("</table></div>");

            writer.println("</body></html>");
        } catch (IOException e) {
            System.err.println("Failed to write HTML report: " + e.getMessage());
        }
    }

    private void writeMetricCard(PrintWriter writer, String label, String value) {
        writer.println("<div class=\"card\"><div>" + escapeHtml(label) + "</div><div class=\"value\">" + escapeHtml(value) + "</div></div>");
    }

    private void writeTableRow(PrintWriter writer, String key, String value) {
        writer.println("<tr><td>" + escapeHtml(key) + "</td><td>" + escapeHtml(value) + "</td></tr>");
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
        int totalStudents;
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
}
