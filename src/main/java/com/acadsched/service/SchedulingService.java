package com.acadsched.service;

import com.acadsched.dto.SessionDTO;
import com.acadsched.model.*;
import com.acadsched.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core Scheduling Service — CSP-based timetable generation with Faculty Pool.
 *
 * Faculty Pool Model:
 *   Each subject has a pool of eligible faculty. During generation, the engine
 *   dynamically selects the best faculty for each session using:
 *   1. Availability Check: is the faculty free at this day/slot?
 *   2. Load Balancing: among free faculty, pick the one with the lowest workload.
 *
 * Faculty Leave:
 *   handleFacultyLeave() first tries to substitute with another pool member
 *   at the same slot, then falls back to moving the session to a different day.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulingService {

    private final TimetableRepository timetableRepository;
    private final SubjectRepository subjectRepository;
    private final FacultyRepository facultyRepository;
    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;
    private final ClassGroupRepository classGroupRepository;
    private final SubjectAllocationService subjectAllocationService;

    private static final String[] DAYS = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"};
    private static final String[] TIME_SLOTS = {
        "09:00-10:00", "10:00-11:00", "11:00-12:00", "12:00-13:00",
        "14:00-15:00", "15:00-16:00", "16:00-17:00"
    };

    // ─────────────────────────────────────────────────────────────────────────
    //  1. TIMETABLE GENERATION (Faculty Pool + Load Balancing)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public List<Timetable> generateTimetable(String semester, String section) {
        return generateTimetable(semester, section, 1, false);
    }

    @Transactional
    public List<Timetable> generateTimetable(String semester, String section, int studentGapHours) {
        return generateTimetable(semester, section, studentGapHours, false);
    }

    @Transactional
    public List<Timetable> generateTimetable(String semester, String section,
                                              int studentGapHours, boolean enforceLunchBreak) {
        log.info("Generating timetable for semester: {} section: {} gapHours: {} lunchBreak: {}",
                 semester, section, studentGapHours, enforceLunchBreak);

        studentGapHours = Math.max(0, Math.min(studentGapHours, 3));
        int maxClassesPerDay = TIME_SLOTS.length - studentGapHours;

        // ── Clear any previous schedule for this semester ────────────────
        List<Timetable> existing = timetableRepository.findBySemester(semester);
        timetableRepository.deleteAll(existing);

        // ── 1. ALLOCATE SESSIONS via SubjectAllocationService ───────────
        List<SessionDTO> sessions = subjectAllocationService
                .generateUnassignedSessions(semester, section);
        if (sessions.isEmpty()) {
            log.warn("No sessions allocated for semester={} section={}", semester, section);
            return new ArrayList<>();
        }

        ClassGroup classGroup = sessions.get(0).getClassGroup();

        // ── Load classrooms ─────────────────────────────────────────────
        List<Classroom> availableRooms = classroomRepository.findByAvailable(true);
        if (availableRooms.isEmpty()) {
            log.warn("No available classrooms");
            return new ArrayList<>();
        }

        // ── Pre-load all faculty for pool lookups ────────────────────────
        Map<Long, Faculty> facultyMap = facultyRepository.findAll().stream()
                .collect(Collectors.toMap(Faculty::getId, f -> f));

        // ── Conflict-tracking sets ──────────────────────────────────────
        Set<String> facultyOccupied = new HashSet<>();
        Set<String> roomOccupied    = new HashSet<>();
        Set<String> groupOccupied   = new HashSet<>();

        Map<String, Integer> classesPerDay = new HashMap<>();
        for (String day : DAYS) classesPerDay.put(day, 0);

        // ── Faculty workload tracker (for load balancing) ───────────────
        Map<Long, Integer> facultyWorkload = new HashMap<>();

        List<Timetable> schedule = new ArrayList<>();

        // Build available time-slots list
        List<String> availableTimeSlots = new ArrayList<>();
        for (String time : TIME_SLOTS) {
            if (enforceLunchBreak && "12:00-13:00".equals(time)) continue;
            availableTimeSlots.add(time);
        }

        // ── 2a. SCHEDULE LINKED LAB BLOCKS ──────────────────────────────
        Map<String, List<SessionDTO>> labBlocks = sessions.stream()
                .filter(SessionDTO::isLinked)
                .collect(Collectors.groupingBy(SessionDTO::getLinkedGroupId,
                         LinkedHashMap::new, Collectors.toList()));

        for (var entry : labBlocks.entrySet()) {
            List<SessionDTO> block = entry.getValue();
            block.sort(Comparator.comparingInt(SessionDTO::getBlockIndex));
            int blockLen = block.size();
            List<Long> eligibleIds = block.get(0).getEligibleFacultyIds();

            boolean placed = false;
            for (String day : DAYS) {
                if (classesPerDay.get(day) + blockLen > maxClassesPerDay) continue;

                for (int start = 0; start <= availableTimeSlots.size() - blockLen; start++) {
                    // Try to find a faculty free for ALL slots in the block
                    Long selectedFacultyId = selectFacultyForBlock(
                            eligibleIds, day, availableTimeSlots, start, blockLen,
                            facultyOccupied, facultyWorkload);
                    if (selectedFacultyId == null) continue;

                    Faculty selectedFaculty = facultyMap.get(selectedFacultyId);
                    if (selectedFaculty == null) continue;

                    // Check group availability for all slots
                    boolean groupFree = true;
                    for (int j = 0; j < blockLen; j++) {
                        String time = availableTimeSlots.get(start + j);
                        String gKey = "group:" + classGroup.getId() + "-" + day + "-" + time;
                        if (groupOccupied.contains(gKey)) { groupFree = false; break; }
                    }
                    if (!groupFree) continue;

                    // Find a lab room
                    Classroom labRoom = findLabRoom(availableRooms, day,
                            availableTimeSlots.get(start), roomOccupied);
                    if (labRoom == null) continue;

                    // Check room availability for all block slots
                    boolean roomFree = true;
                    for (int j = 1; j < blockLen; j++) {
                        String time = availableTimeSlots.get(start + j);
                        String rKey = "room:" + labRoom.getId() + "-" + day + "-" + time;
                        if (roomOccupied.contains(rKey)) { roomFree = false; break; }
                    }
                    if (!roomFree) continue;

                    // ── Place the entire block ──────────────────────────
                    for (int j = 0; j < blockLen; j++) {
                        SessionDTO s = block.get(j);
                        String time = availableTimeSlots.get(start + j);

                        s.setAssignedFacultyId(selectedFacultyId);

                        Timetable t = new Timetable();
                        t.setSubject(s.getSubject());
                        t.setFaculty(selectedFaculty);
                        t.setClassroom(labRoom);
                        t.setClassGroup(classGroup);
                        t.setDayOfWeek(day);
                        t.setTimeSlot(time);
                        t.setSemester(semester);
                        schedule.add(t);

                        facultyOccupied.add("faculty:" + selectedFacultyId + "-" + day + "-" + time);
                        roomOccupied.add("room:" + labRoom.getId() + "-" + day + "-" + time);
                        groupOccupied.add("group:" + classGroup.getId() + "-" + day + "-" + time);
                        classesPerDay.merge(day, 1, Integer::sum);
                        facultyWorkload.merge(selectedFacultyId, 1, Integer::sum);
                    }
                    placed = true;
                    break;
                }
                if (placed) break;
            }
            if (!placed) {
                log.warn("Could not place {}-hour lab block for {}",
                        blockLen, block.get(0).getSubject().getSubjectCode());
            }
        }

        // ── 2b. SCHEDULE INDEPENDENT SESSIONS (round-robin + pool) ──────
        List<SessionDTO> independent = sessions.stream()
                .filter(s -> !s.isLinked())
                .toList();

        List<String[]> slotOrder = buildRoundRobinSlots(enforceLunchBreak);

        Map<Long, List<SessionDTO>> bySubject = independent.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getSubject().getId(),
                        LinkedHashMap::new, Collectors.toList()));

        for (var subjectSessions : bySubject.values()) {
            Classroom room = pickClassroom(subjectSessions.get(0).getSubject(), availableRooms);
            int assigned = 0;

            for (String[] slot : slotOrder) {
                if (assigned >= subjectSessions.size()) break;

                String day  = slot[0];
                String time = slot[1];

                if (classesPerDay.get(day) >= maxClassesPerDay) continue;

                SessionDTO s = subjectSessions.get(assigned);

                // ── Dynamic faculty selection ───────────────────────────
                Long selectedFacultyId = selectFaculty(
                        s.getEligibleFacultyIds(), day, time,
                        facultyOccupied, facultyWorkload);
                if (selectedFacultyId == null) continue;

                Faculty selectedFaculty = facultyMap.get(selectedFacultyId);
                if (selectedFaculty == null) continue;

                // Room check
                String rKey = "room:" + room.getId() + "-" + day + "-" + time;
                if (roomOccupied.contains(rKey)) {
                    Classroom altRoom = findAlternateRoom(availableRooms, day, time, roomOccupied);
                    if (altRoom == null) continue;
                    room = altRoom;
                    rKey = "room:" + room.getId() + "-" + day + "-" + time;
                }

                // Group check
                String gKey = "group:" + classGroup.getId() + "-" + day + "-" + time;
                if (groupOccupied.contains(gKey)) continue;

                s.setAssignedFacultyId(selectedFacultyId);

                Timetable t = new Timetable();
                t.setSubject(s.getSubject());
                t.setFaculty(selectedFaculty);
                t.setClassroom(room);
                t.setClassGroup(classGroup);
                t.setDayOfWeek(day);
                t.setTimeSlot(time);
                t.setSemester(semester);

                schedule.add(t);
                facultyOccupied.add("faculty:" + selectedFacultyId + "-" + day + "-" + time);
                roomOccupied.add(rKey);
                groupOccupied.add(gKey);
                classesPerDay.merge(day, 1, Integer::sum);
                facultyWorkload.merge(selectedFacultyId, 1, Integer::sum);
                assigned++;
            }

            if (assigned < subjectSessions.size()) {
                log.warn("Could only assign {}/{} theory sessions for {}",
                        assigned, subjectSessions.size(),
                        subjectSessions.get(0).getSubject().getSubjectCode());
            }
        }

        List<Timetable> saved = timetableRepository.saveAll(schedule);
        log.info("Generated {} entries ({} lab + {} theory) across {} days",
                saved.size(),
                labBlocks.values().stream().mapToInt(List::size).sum(),
                independent.size(),
                saved.stream().map(Timetable::getDayOfWeek).distinct().count());
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FACULTY SELECTION ALGORITHM (availability + load balancing)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Select the best faculty from the eligible pool for a single slot.
     * 1. Filter to those FREE at (day, time)
     * 2. Among free, pick the one with the LOWEST current workload
     */
    private Long selectFaculty(List<Long> eligibleIds, String day, String time,
                                Set<String> facultyOccupied,
                                Map<Long, Integer> facultyWorkload) {
        return eligibleIds.stream()
                .filter(id -> !facultyOccupied.contains("faculty:" + id + "-" + day + "-" + time))
                .min(Comparator.comparingInt(id -> facultyWorkload.getOrDefault(id, 0)))
                .orElse(null);
    }

    /**
     * Select the best faculty from the eligible pool for a consecutive block.
     * The faculty must be free for ALL slots in the block.
     */
    private Long selectFacultyForBlock(List<Long> eligibleIds, String day,
                                        List<String> timeSlots, int startIdx, int blockLen,
                                        Set<String> facultyOccupied,
                                        Map<Long, Integer> facultyWorkload) {
        return eligibleIds.stream()
                .filter(id -> {
                    for (int j = 0; j < blockLen; j++) {
                        String time = timeSlots.get(startIdx + j);
                        if (facultyOccupied.contains("faculty:" + id + "-" + day + "-" + time)) {
                            return false;
                        }
                    }
                    return true;
                })
                .min(Comparator.comparingInt(id -> facultyWorkload.getOrDefault(id, 0)))
                .orElse(null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPER METHODS
    // ─────────────────────────────────────────────────────────────────────────

    private List<String[]> buildRoundRobinSlots(boolean enforceLunchBreak) {
        List<String[]> slots = new ArrayList<>();
        for (String time : TIME_SLOTS) {
            if (enforceLunchBreak && "12:00-13:00".equals(time)) continue;
            for (String day : DAYS) {
                slots.add(new String[]{day, time});
            }
        }
        return slots;
    }

    private Classroom pickClassroom(Subject subject, List<Classroom> rooms) {
        if (subject.getType() == Subject.SubjectType.PRACTICAL) {
            return rooms.stream()
                    .filter(r -> r.getType() == Classroom.RoomType.LABORATORY)
                    .findFirst()
                    .orElse(rooms.get(0));
        }
        return rooms.stream()
                .filter(r -> r.getType() == Classroom.RoomType.LECTURE_HALL)
                .findFirst()
                .orElse(rooms.get(0));
    }

    private Classroom findAlternateRoom(List<Classroom> rooms, String day,
                                         String time, Set<String> roomOccupied) {
        for (Classroom r : rooms) {
            String key = "room:" + r.getId() + "-" + day + "-" + time;
            if (!roomOccupied.contains(key)) return r;
        }
        return null;
    }

    private Classroom findLabRoom(List<Classroom> rooms, String day,
                                   String time, Set<String> roomOccupied) {
        for (Classroom r : rooms) {
            if (r.getType() == Classroom.RoomType.LABORATORY) {
                String key = "room:" + r.getId() + "-" + day + "-" + time;
                if (!roomOccupied.contains(key)) return r;
            }
        }
        return findAlternateRoom(rooms, day, time, roomOccupied);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  2. DYNAMIC FACULTY LEAVE RESCHEDULING (Pool Substitution + Move)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handle faculty leave with Pool Substitution:
     *   Step 1: Try to find a SUBSTITUTE from the subject's eligible pool
     *           who is free at the SAME day/slot → no disruption to schedule.
     *   Step 2: If no substitute, MOVE the session to a different day
     *           (keeping the same faculty) → existing move logic.
     */
    @Transactional
    public List<String> handleFacultyLeave(Long facultyId, String day, String timeSlot,
                                            int studentGapHours) {
        log.info("Handling faculty leave: facultyId={} day={} slot={} gapHours={}",
                 facultyId, day, timeSlot, studentGapHours);

        studentGapHours = Math.max(0, Math.min(studentGapHours, 3));
        int maxClassesPerDay = TIME_SLOTS.length - studentGapHours;

        List<String> actions = new ArrayList<>();

        Faculty leaveFaculty = facultyRepository.findById(facultyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Faculty not found with ID: " + facultyId));

        // ── Find affected timetable entries ─────────────────────────────
        List<Timetable> affectedEntries;
        if (timeSlot != null && !timeSlot.isEmpty()) {
            affectedEntries = timetableRepository
                    .findByFacultyAndDayAndTimeSlot(facultyId, day, timeSlot);
        } else {
            affectedEntries = timetableRepository.findByFacultyId(facultyId).stream()
                    .filter(t -> t.getDayOfWeek().equals(day))
                    .collect(Collectors.toList());
        }

        if (affectedEntries.isEmpty()) {
            actions.add("ℹ No classes found for " + leaveFaculty.getName() + " on " + day);
            return actions;
        }

        // ── Build occupation snapshot ───────────────────────────────────
        List<Timetable> allEntries = timetableRepository.findAll();
        Set<String> facultyOccupied = new HashSet<>();
        Set<String> roomOccupied    = new HashSet<>();
        Set<String> groupOccupied   = new HashSet<>();
        Map<String, Integer> groupDayCount = new HashMap<>();

        for (Timetable t : allEntries) {
            facultyOccupied.add("faculty:" + t.getFaculty().getId()
                    + "-" + t.getDayOfWeek() + "-" + t.getTimeSlot());
            roomOccupied.add("room:" + t.getClassroom().getId()
                    + "-" + t.getDayOfWeek() + "-" + t.getTimeSlot());
            groupOccupied.add("group:" + t.getClassGroup().getId()
                    + "-" + t.getDayOfWeek() + "-" + t.getTimeSlot());

            String gdKey = t.getClassGroup().getId() + "-" + t.getDayOfWeek();
            groupDayCount.merge(gdKey, 1, Integer::sum);
        }

        // ── Process each affected entry ─────────────────────────────────
        for (Timetable entry : affectedEntries) {
            String subjectName = entry.getSubject().getName();
            String originalSlot = entry.getDayOfWeek() + " " + entry.getTimeSlot();

            // ── STEP 1: Try SUBSTITUTE from the eligible pool ───────────
            boolean substituted = trySubstituteFromPool(entry, leaveFaculty,
                    facultyOccupied);

            if (substituted) {
                actions.add("✓ " + subjectName + " (" + originalSlot + "): "
                          + "Substituted with " + entry.getFaculty().getName()
                          + " (from faculty pool)");
                timetableRepository.save(entry);
                continue;
            }

            // ── STEP 2: Fall back to MOVE to alternate day ──────────────
            boolean moved = tryMoveToAlternateDay(entry, leaveFaculty, day,
                    facultyOccupied, roomOccupied, groupOccupied,
                    groupDayCount, maxClassesPerDay);

            if (moved) {
                actions.add("✓ " + subjectName + " (" + originalSlot + "): "
                          + "Moved to " + entry.getDayOfWeek() + " " + entry.getTimeSlot());
                timetableRepository.save(entry);
            } else {
                actions.add("✗ " + subjectName + " (" + originalSlot + "): "
                          + "No substitute or free slot — manual intervention needed.");
            }
        }

        return actions;
    }

    /** Backward-compatible overload (default 1 gap hour). */
    @Transactional
    public List<String> handleFacultyLeave(Long facultyId, String day, String timeSlot) {
        return handleFacultyLeave(facultyId, day, timeSlot, 1);
    }

    /**
     * Try to substitute the leave faculty with another member from the
     * subject's eligible pool who is free at the SAME day/slot.
     */
    private boolean trySubstituteFromPool(Timetable entry, Faculty leaveFaculty,
                                           Set<String> facultyOccupied) {
        Subject subject = entry.getSubject();
        Set<Faculty> pool = subject.getEligibleFaculty();
        if (pool == null || pool.size() <= 1) return false;

        String day = entry.getDayOfWeek();
        String time = entry.getTimeSlot();

        for (Faculty candidate : pool) {
            if (candidate.getId().equals(leaveFaculty.getId())) continue;
            if (!candidate.getAvailable()) continue;

            String fKey = "faculty:" + candidate.getId() + "-" + day + "-" + time;
            if (!facultyOccupied.contains(fKey)) {
                // Found a free substitute!
                // Update occupation tracking
                String oldFKey = "faculty:" + leaveFaculty.getId() + "-" + day + "-" + time;
                facultyOccupied.remove(oldFKey);
                facultyOccupied.add(fKey);

                entry.setFaculty(candidate);
                log.info("Substituted {} with {} for {} at {} {}",
                        leaveFaculty.getName(), candidate.getName(),
                        subject.getSubjectCode(), day, time);
                return true;
            }
        }
        return false;
    }

    /**
     * Move a class to a DIFFERENT day (never the leave day).
     */
    private boolean tryMoveToAlternateDay(Timetable entry, Faculty faculty, String leaveDay,
                                           Set<String> facultyOccupied,
                                           Set<String> roomOccupied,
                                           Set<String> groupOccupied,
                                           Map<String, Integer> groupDayCount,
                                           int maxClassesPerDay) {
        Long groupId = entry.getClassGroup().getId();

        for (String altDay : DAYS) {
            if (altDay.equals(leaveDay)) continue;

            String gdKey = groupId + "-" + altDay;
            int currentCount = groupDayCount.getOrDefault(gdKey, 0);
            if (currentCount >= maxClassesPerDay) continue;

            for (String altTime : TIME_SLOTS) {
                String fKey = "faculty:" + faculty.getId() + "-" + altDay + "-" + altTime;
                String rKey = "room:" + entry.getClassroom().getId() + "-" + altDay + "-" + altTime;
                String gKey = "group:" + groupId + "-" + altDay + "-" + altTime;

                if (!facultyOccupied.contains(fKey) &&
                    !roomOccupied.contains(rKey) &&
                    !groupOccupied.contains(gKey)) {

                    // Remove old keys
                    String oldFKey = "faculty:" + faculty.getId()
                            + "-" + entry.getDayOfWeek() + "-" + entry.getTimeSlot();
                    String oldRKey = "room:" + entry.getClassroom().getId()
                            + "-" + entry.getDayOfWeek() + "-" + entry.getTimeSlot();
                    String oldGKey = "group:" + groupId
                            + "-" + entry.getDayOfWeek() + "-" + entry.getTimeSlot();
                    String oldGdKey = groupId + "-" + entry.getDayOfWeek();

                    facultyOccupied.remove(oldFKey);
                    roomOccupied.remove(oldRKey);
                    groupOccupied.remove(oldGKey);
                    groupDayCount.merge(oldGdKey, -1, Integer::sum);

                    // Add new keys
                    facultyOccupied.add(fKey);
                    roomOccupied.add(rKey);
                    groupOccupied.add(gKey);
                    groupDayCount.merge(gdKey, 1, Integer::sum);

                    entry.setDayOfWeek(altDay);
                    entry.setTimeSlot(altTime);
                    return true;
                }
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  3. EXISTING QUERY METHODS
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void rescheduleSession(Long timetableId, String newDay, String newTimeSlot) {
        Timetable entry = timetableRepository.findById(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        entry.setDayOfWeek(newDay);
        entry.setTimeSlot(newTimeSlot);
        timetableRepository.save(entry);
    }

    public List<Timetable> getTimetableBySemesterAndSection(String semester, String section) {
        return timetableRepository.findBySemester(semester);
    }

    public List<Map<String, String>> getAvailableTimetables() {
        return timetableRepository.findDistinctSemesterSections().stream()
                .map(row -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("semester", (String) row[0]);
                    m.put("section",  (String) row[1]);
                    return m;
                })
                .toList();
    }

    public List<Timetable> getFacultyTimetable(Long facultyId) {
        return timetableRepository.findByFacultyId(facultyId);
    }

    public List<Timetable> getStudentTimetable(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        if (student.getClassGroup() == null) return new ArrayList<>();
        return timetableRepository.findByClassGroupId(student.getClassGroup().getId());
    }

    public List<Timetable> getTimetableBySemester(String semester) {
        return timetableRepository.findBySemester(semester);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  4. MANUAL ADMIN OVERRIDE (conflict-checked with force-save option)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> adminOverride(Long entryId, Long newFacultyId,
                                              Long newClassroomId, String newDay,
                                              String newTimeSlot, boolean force,
                                              int studentGapHours) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> conflicts = new ArrayList<>();

        Timetable entry = timetableRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable entry not found: " + entryId));

        Faculty newFaculty = facultyRepository.findById(newFacultyId)
                .orElseThrow(() -> new IllegalArgumentException("Faculty not found: " + newFacultyId));
        Classroom newClassroom = classroomRepository.findById(newClassroomId)
                .orElseThrow(() -> new IllegalArgumentException("Classroom not found: " + newClassroomId));

        // 1. Faculty double-booking
        List<Timetable> facultyConflicts = timetableRepository
                .findByFacultyAndDayAndTimeSlot(newFacultyId, newDay, newTimeSlot);
        facultyConflicts.removeIf(t -> t.getId().equals(entryId));
        if (!facultyConflicts.isEmpty()) {
            Timetable c = facultyConflicts.get(0);
            conflicts.add("Faculty conflict: " + newFaculty.getName()
                    + " is already teaching \"" + c.getSubject().getName()
                    + "\" at " + newDay + " " + newTimeSlot);
        }

        // 2. Room double-booking
        List<Timetable> roomConflicts = timetableRepository
                .findByClassroomAndDayAndTimeSlot(newClassroomId, newDay, newTimeSlot);
        roomConflicts.removeIf(t -> t.getId().equals(entryId));
        if (!roomConflicts.isEmpty()) {
            Timetable c = roomConflicts.get(0);
            conflicts.add("Room conflict: " + newClassroom.getRoomNumber()
                    + " is already booked for \"" + c.getSubject().getName()
                    + "\" at " + newDay + " " + newTimeSlot);
        }

        // 3. Class group double-booking
        Long groupId = entry.getClassGroup().getId();
        List<Timetable> groupConflicts = timetableRepository
                .findByClassGroupAndDayAndTimeSlot(groupId, newDay, newTimeSlot);
        groupConflicts.removeIf(t -> t.getId().equals(entryId));
        if (!groupConflicts.isEmpty()) {
            Timetable c = groupConflicts.get(0);
            conflicts.add("Group conflict: Section " + entry.getClassGroup().getSection()
                    + " already has \"" + c.getSubject().getName()
                    + "\" at " + newDay + " " + newTimeSlot);
        }

        // 4. Student gap hours
        studentGapHours = Math.max(0, Math.min(studentGapHours, 3));
        int maxClassesPerDay = TIME_SLOTS.length - studentGapHours;
        long currentCount = countGroupClassesOnDay(groupId, newDay, entryId);
        if (currentCount >= maxClassesPerDay) {
            conflicts.add("Gap hours violation: Section " + entry.getClassGroup().getSection()
                    + " already has " + currentCount + " classes on " + newDay
                    + " (max " + maxClassesPerDay + " with " + studentGapHours + " gap hour(s))");
        }

        result.put("conflicts", conflicts);

        if (conflicts.isEmpty() || force) {
            entry.setFaculty(newFaculty);
            entry.setClassroom(newClassroom);
            entry.setDayOfWeek(newDay);
            entry.setTimeSlot(newTimeSlot);
            timetableRepository.save(entry);

            result.put("saved", true);
            result.put("entry", Map.of(
                    "id", entry.getId(),
                    "day", entry.getDayOfWeek(),
                    "timeSlot", entry.getTimeSlot(),
                    "subject", entry.getSubject().getName(),
                    "faculty", entry.getFaculty().getName(),
                    "classroom", entry.getClassroom().getRoomNumber(),
                    "section", entry.getClassGroup().getSection()
            ));

            log.info("Admin override saved: entry={} → {}-{} faculty={} room={}{}",
                     entryId, newDay, newTimeSlot, newFaculty.getName(),
                     newClassroom.getRoomNumber(),
                     force && !conflicts.isEmpty() ? " (FORCED)" : "");
        } else {
            result.put("saved", false);
        }

        return result;
    }

    private long countGroupClassesOnDay(Long groupId, String day, Long excludeEntryId) {
        return timetableRepository.findByClassGroupId(groupId).stream()
                .filter(t -> t.getDayOfWeek().equals(day))
                .filter(t -> !t.getId().equals(excludeEntryId))
                .count();
    }
}
