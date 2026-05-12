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
 * 10. 仿真期间每个时刻输出餐厅状态快照到 CSV 文件。
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

    private PrintWriter reportWriter;
    private Path reportFilePath;
    private boolean reportHeaderWritten = false;

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
            openReportWriter();
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
            closeReportWriter();
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
                if (tableId < 0) {
                    tableId = findSharedTableForGroup(groupSize);
                }
                if (tableId < 0) {
                    continue;
                }
                reserveSeatsForGroup(groupId, tableId, groupSize);
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

    private void openReportWriter() {
        try {
            Path outputDir = Paths.get("simulation-output");
            Files.createDirectories(outputDir);

            String fileName = "simulation_result_" + LocalDateTime.now().format(REPORT_TIME_FORMATTER) + ".csv";
            reportFilePath = outputDir.resolve(fileName).toAbsolutePath();

            BufferedWriter writer = Files.newBufferedWriter(reportFilePath, StandardCharsets.UTF_8);
            reportWriter = new PrintWriter(writer);
            writeReportHeader();
            System.out.println("Simulation report file: " + reportFilePath);
        } catch (IOException e) {
            reportWriter = null;
            reportFilePath = null;
            System.err.println("Failed to create simulation report file: " + e.getMessage());
        }
    }

    private void writeReportHeader() {
        if (reportWriter == null || reportHeaderWritten) {
            return;
        }

        reportWriter.println(
                "timeSecond," +
                        "phase," +
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
                        "actualTableSeatGroups," +
                        "tableReservationCounts"
        );
        reportHeaderWritten = true;
    }

    private void writeCurrentSnapshot() {
        if (reportWriter == null) {
            return;
        }

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

        reportWriter.println(
                currentTime + "," +
                        escapeCsv(getCurrentPhaseName()) + "," +
                        arrivedStudents + "," +
                        escapeCsv(formatWindowQueues()) + "," +
                        totalQueuing + "," +
                        servingCount + "," +
                        waitingForSeatCount + "," +
                        diningCount + "," +
                        actualSeatedTableCount + "," +
                        actualSeatedSeatCount + "," +
                        reservedOrOccupiedTableCount + "," +
                        reservedOrOccupiedSeatCount + "," +
                        lockedOnlySeatCount + "," +
                        leftNormally + "," +
                        balkedStudents + "," +
                        escapeCsv(formatActualTableSeatGroups()) + "," +
                        escapeCsv(formatTableReservationCounts())
        );
        reportWriter.flush();
    }

    private void closeReportWriter() {
        if (reportWriter != null) {
            reportWriter.flush();
            reportWriter.close();
            reportWriter = null;
        }
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
}
