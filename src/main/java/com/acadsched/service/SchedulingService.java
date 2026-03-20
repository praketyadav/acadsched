package com.acadsched.service;

import com.acadsched.model.*;
import com.acadsched.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core Scheduling Service — CSP-based timetable generation.
 *
 * Bug fix:  Replaced single flat "occupied" set with per-faculty, per-room,
 *           and per-class-group conflict tracking.  Sessions are now distributed
 *           across the week using round-robin day selection instead of greedy
 *           sequential fill from Monday.
 *
 * Feature 1 — Student Gap Hours:
 *   The generator accepts a mandatory studentGapHours parameter.  During slot
 *   selection, consecutive-class limits are enforced so that each day contains
 *   at least N free slots for every class group.
 *
 * Feature 2 — Dynamic Faculty Leave Rescheduling:
 *   handleFacultyLeave() allows marking a faculty member as absent for a
 *   specific day/slot *after* the timetable is generated.  Only the affected
 *   entries are rescheduled (substitute faculty or alternate slot); the rest
 *   of the timetable is untouched.
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

    private static final String[] DAYS = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"};
    private static final String[] TIME_SLOTS = {
        "09:00-10:00", "10:00-11:00", "11:00-12:00", "12:00-13:00",
        "14:00-15:00", "15:00-16:00", "16:00-17:00"
    };

    // ─────────────────────────────────────────────────────────────────────────
    //  1. TIMETABLE GENERATION  (bug-fixed + studentGapHours constraint)
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
    public List<Timetable> generateTimetable(String semester, String section, int studentGapHours, boolean enforceLunchBreak) {
        log.info("Generating timetable for semester: {} section: {} gapHours: {} lunchBreak: {}",
                 semester, section, studentGapHours, enforceLunchBreak);

        // Clamp gap hours to a sane range (0–3)
        studentGapHours = Math.max(0, Math.min(studentGapHours, 3));

        // Maximum classes allowed per day = total slots minus gap requirement
        int maxClassesPerDay = TIME_SLOTS.length - studentGapHours;

        // ── Resolve / create ClassGroup ──────────────────────────────────
        ClassGroup classGroup = classGroupRepository.findByName(section)
                .orElseGet(() -> {
                    ClassGroup g = new ClassGroup();
                    g.setName(section);
                    g.setDepartment("General");
                    g.setYear("Default");
                    g.setSection(section);
                    g.setStudentStrength(30);
                    g.setActive(true);
                    return classGroupRepository.save(g);
                });

        // ── Clear any previous schedule for this semester ────────────────
        List<Timetable> existing = timetableRepository.findBySemester(semester);
        timetableRepository.deleteAll(existing);

        // ── Load resources ──────────────────────────────────────────────
        List<Subject> subjects = subjectRepository.findBySemester(semester);
        if (subjects.isEmpty()) {
            log.warn("No subjects found for semester {}", semester);
            return new ArrayList<>();
        }

        // Sort by priority (desc), then credits (desc)
        subjects.sort((s1, s2) -> {
            int cmp = Integer.compare(s2.getPriority(), s1.getPriority());
            return cmp != 0 ? cmp : Integer.compare(s2.getCredits(), s1.getCredits());
        });

        List<Classroom> availableRooms = classroomRepository.findByAvailable(true);
        if (availableRooms.isEmpty()) {
            log.warn("No available classrooms");
            return new ArrayList<>();
        }

        // ── Conflict-tracking sets ──────────────────────────────────────
        // Each key is  "resource:id-DAY-TIME"  e.g. "faculty:3-MONDAY-09:00-10:00"
        Set<String> facultyOccupied  = new HashSet<>();
        Set<String> roomOccupied     = new HashSet<>();
        Set<String> groupOccupied    = new HashSet<>();

        // Track how many classes are assigned per day for the class group
        // (used to enforce studentGapHours)
        Map<String, Integer> classesPerDay = new HashMap<>();
        for (String day : DAYS) {
            classesPerDay.put(day, 0);
        }

        List<Timetable> schedule = new ArrayList<>();

        // ── Assign sessions for each subject ────────────────────────────
        for (Subject subject : subjects) {
            if (subject.getFaculty() == null) continue;

            Faculty faculty = subject.getFaculty();
            int sessionsNeeded = subject.getCredits();

            // Pick the best classroom for this subject (match type if possible)
            Classroom room = pickClassroom(subject, availableRooms);

            // Build a round-robin ordered list of (day, time) pairs so that
            // sessions spread evenly across the week instead of piling up
            // on Monday/Tuesday.
            List<String[]> slotOrder = buildRoundRobinSlots(enforceLunchBreak);

            int assigned = 0;
            for (String[] slot : slotOrder) {
                if (assigned >= sessionsNeeded) break;

                String day  = slot[0];
                String time = slot[1];

                // ── Gap constraint: skip this day if already at max classes ──
                if (classesPerDay.get(day) >= maxClassesPerDay) continue;

                // ── Conflict checks ─────────────────────────────────────
                String fKey = "faculty:" + faculty.getId() + "-" + day + "-" + time;
                String rKey = "room:" + room.getId() + "-" + day + "-" + time;
                String gKey = "group:" + classGroup.getId() + "-" + day + "-" + time;

                if (facultyOccupied.contains(fKey)) continue;  // faculty busy
                if (roomOccupied.contains(rKey)) {
                    // Try another room
                    Classroom altRoom = findAlternateRoom(availableRooms, day, time, roomOccupied);
                    if (altRoom == null) continue;
                    room = altRoom;
                    rKey = "room:" + room.getId() + "-" + day + "-" + time;
                }
                if (groupOccupied.contains(gKey)) continue;    // group busy

                // ── Assign ──────────────────────────────────────────────
                Timetable entry = new Timetable();
                entry.setSubject(subject);
                entry.setFaculty(faculty);
                entry.setClassroom(room);
                entry.setClassGroup(classGroup);
                entry.setDayOfWeek(day);
                entry.setTimeSlot(time);
                entry.setSemester(semester);

                schedule.add(entry);
                facultyOccupied.add(fKey);
                roomOccupied.add(rKey);
                groupOccupied.add(gKey);
                classesPerDay.merge(day, 1, Integer::sum);
                assigned++;
            }

            if (assigned < sessionsNeeded) {
                log.warn("Could only assign {}/{} sessions for {} ({})",
                         assigned, sessionsNeeded, subject.getName(), subject.getSubjectCode());
            }
        }

        List<Timetable> saved = timetableRepository.saveAll(schedule);
        log.info("Generated {} entries across {} days", saved.size(),
                 saved.stream().map(Timetable::getDayOfWeek).distinct().count());
        return saved;
    }

    /**
     * Build a list of (day, time) pairs ordered to spread sessions evenly.
     * Pattern:  Mon-slot1, Tue-slot1, Wed-slot1 … Fri-slot1, Mon-slot2, …
     * This prevents the greedy "fill Monday first" problem.
     */
    private List<String[]> buildRoundRobinSlots(boolean enforceLunchBreak) {
        List<String[]> slots = new ArrayList<>();
        for (String time : TIME_SLOTS) {
            // Skip the lunch break slot if enforced
            if (enforceLunchBreak && "12:00-13:00".equals(time)) continue;
            for (String day : DAYS) {
                slots.add(new String[]{day, time});
            }
        }
        return slots;
    }

    /**
     * Pick the best classroom for a subject.
     * Labs get LABORATORY rooms first; theory gets LECTURE_HALL first.
     */
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

    /**
     * Find an alternate room that is free at the given day/time.
     */
    private Classroom findAlternateRoom(List<Classroom> rooms, String day,
                                         String time, Set<String> roomOccupied) {
        for (Classroom r : rooms) {
            String key = "room:" + r.getId() + "-" + day + "-" + time;
            if (!roomOccupied.contains(key)) return r;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  2. DYNAMIC FACULTY LEAVE RESCHEDULING  (move-only, gap-aware)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handle a faculty member going on leave for a specific day (or day+slot).
     *
     * Strategy (move-only):
     *   1. Find all timetable entries for this faculty on the given day.
     *   2. For each affected entry, REMOVE it from the leave day and find a
     *      valid empty slot on a DIFFERENT day within the week.
     *   3. The new placement must respect the studentGapHours constraint –
     *      no day may exceed (TIME_SLOTS.length - studentGapHours) classes
     *      for the same class group.
     *   4. If no valid slot exists on any other day, return a user-friendly
     *      error for that specific class.
     *
     * IMPORTANT: Only affected entries are modified.  The rest of the timetable
     * remains completely untouched.
     *
     * @param studentGapHours Gap hours constraint (default 1 if ≤ 0)
     */
    @Transactional
    public List<String> handleFacultyLeave(Long facultyId, String day, String timeSlot,
                                            int studentGapHours) {
        log.info("Handling faculty leave: facultyId={} day={} slot={} gapHours={}",
                 facultyId, day, timeSlot, studentGapHours);

        // Clamp
        studentGapHours = Math.max(0, Math.min(studentGapHours, 3));
        int maxClassesPerDay = TIME_SLOTS.length - studentGapHours;

        List<String> actions = new ArrayList<>();

        // Find the faculty entity
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

        // Count classes-per-day-per-group for gap enforcement
        // Key: "groupId-DAY" → count
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

        // ── Process each affected entry (MOVE to another day) ───────────
        for (Timetable entry : affectedEntries) {
            String subjectName = entry.getSubject().getName();
            String originalSlot = entry.getDayOfWeek() + " " + entry.getTimeSlot();
            Long groupId = entry.getClassGroup().getId();

            boolean moved = tryMoveToAlternateDay(entry, leaveFaculty, day,
                    facultyOccupied, roomOccupied, groupOccupied,
                    groupDayCount, maxClassesPerDay);

            if (moved) {
                actions.add("✓ " + subjectName + " (" + originalSlot + "): "
                          + "Moved to " + entry.getDayOfWeek() + " " + entry.getTimeSlot());
                timetableRepository.save(entry);
            } else {
                actions.add("✗ " + subjectName + " (" + originalSlot + "): "
                          + "No free slot available on any other day — manual intervention needed.");
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
     * Move a class to a DIFFERENT day (never the leave day).
     * Enforces:
     *   – faculty / room / group conflict-free
     *   – studentGapHours via maxClassesPerDay cap per class-group-day
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

            // ── Gap constraint: would this day exceed the limit? ────────
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

                    // ── Remove old occupation keys ──────────────────────
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

                    // ── Add new occupation keys ─────────────────────────
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
    //  3. EXISTING QUERY METHODS  (unchanged)
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

    /**
     * Returns all distinct (semester, section) pairs from the database
     * as a list of maps with keys "semester" and "section".
     */
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
    //  4. MANUAL ADMIN OVERRIDE  (conflict-checked with force-save option)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Attempt to manually override a timetable entry.
     * Returns a map with:
     *   - "conflicts": List<String> of conflict descriptions (empty if none)
     *   - "saved": boolean — true if the entry was saved
     *   - "entry": summary of the saved entry (if saved)
     *
     * @param force  if true, save even when conflicts exist
     * @param studentGapHours  gap constraint (default 1)
     */
    @Transactional
    public Map<String, Object> adminOverride(Long entryId, Long newFacultyId,
                                              Long newClassroomId, String newDay,
                                              String newTimeSlot, boolean force,
                                              int studentGapHours) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> conflicts = new ArrayList<>();

        Timetable entry = timetableRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable entry not found: " + entryId));

        // Resolve new faculty and classroom
        Faculty newFaculty = facultyRepository.findById(newFacultyId)
                .orElseThrow(() -> new IllegalArgumentException("Faculty not found: " + newFacultyId));
        Classroom newClassroom = classroomRepository.findById(newClassroomId)
                .orElseThrow(() -> new IllegalArgumentException("Classroom not found: " + newClassroomId));

        // ── Conflict detection ──────────────────────────────────────────

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

        // 4. Student gap hours constraint
        studentGapHours = Math.max(0, Math.min(studentGapHours, 3));
        int maxClassesPerDay = TIME_SLOTS.length - studentGapHours;
        long currentCount = countGroupClassesOnDay(groupId, newDay, entryId);
        if (currentCount >= maxClassesPerDay) {
            conflicts.add("Gap hours violation: Section " + entry.getClassGroup().getSection()
                    + " already has " + currentCount + " classes on " + newDay
                    + " (max " + maxClassesPerDay + " with " + studentGapHours + " gap hour(s))");
        }

        result.put("conflicts", conflicts);

        // ── Save decision ───────────────────────────────────────────────
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

    /** Count how many classes a class-group has on a given day, excluding a specific entry. */
    private long countGroupClassesOnDay(Long groupId, String day, Long excludeEntryId) {
        return timetableRepository.findByClassGroupId(groupId).stream()
                .filter(t -> t.getDayOfWeek().equals(day))
                .filter(t -> !t.getId().equals(excludeEntryId))
                .count();
    }
}
