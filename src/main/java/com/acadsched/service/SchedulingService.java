package com.acadsched.service;

import com.acadsched.model.*;
import com.acadsched.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Core Scheduling Service
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

    @Transactional
    public List<Timetable> generateTimetable(String semester, String section) {
        log.info("Generating timetable for semester: {} section: {}", semester, section);
        
        ClassGroup classGroup = classGroupRepository.findByName(section)
                .orElseGet(() -> {
                    ClassGroup newGroup = new ClassGroup();
                    newGroup.setName(section);
                    newGroup.setDepartment("General");
                    newGroup.setYear("Default");
                    newGroup.setSection(section);
                    newGroup.setStudentStrength(30);
                    newGroup.setActive(true);
                    return classGroupRepository.save(newGroup);
                });
        
        ClassGroup finalClassGroup = classGroup;
        List<Timetable> existing = timetableRepository.findBySemester(semester);
        timetableRepository.deleteAll(existing);
        
        List<Subject> subjects = subjectRepository.findBySemester(semester);
        if (subjects.isEmpty()) {
            log.warn("No subjects found");
            return new ArrayList<>();
        }
        
        subjects.sort((s1, s2) -> {
            int priorityCompare = Integer.compare(s2.getPriority(), s1.getPriority());
            if (priorityCompare != 0) return priorityCompare;
            return Integer.compare(s2.getCredits(), s1.getCredits());
        });
        
        List<Timetable> schedule = new ArrayList<>();
        Set<String> occupied = new HashSet<>();
        
        for (Subject subject : subjects) {
            if (subject.getFaculty() == null) continue;
            
            int sessions = subject.getCredits();
            List<Classroom> rooms = classroomRepository.findByAvailable(true);
            if (rooms.isEmpty()) continue;
            
            Classroom room = rooms.get(0);
            
            for (int i = 0; i < sessions; i++) {
                boolean found = false;
                
                for (String day : DAYS) {
                    for (String time : TIME_SLOTS) {
                        String key = day + "-" + time;
                        
                        if (!occupied.contains(key)) {
                            Timetable entry = new Timetable();
                            entry.setSubject(subject);
                            entry.setFaculty(subject.getFaculty());
                            entry.setClassroom(room);
                            entry.setClassGroup(finalClassGroup);
                            entry.setDayOfWeek(day);
                            entry.setTimeSlot(time);
                            entry.setSemester(semester);
                            
                            schedule.add(entry);
                            occupied.add(key);
                            found = true;
                            break;
                        }
                    }
                    if (found) break;
                }
            }
        }
        
        List<Timetable> saved = timetableRepository.saveAll(schedule);
        log.info("Generated {} entries", saved.size());
        return saved;
    }

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

    public List<Timetable> getFacultyTimetable(Long facultyId) {
        return timetableRepository.findByFacultyId(facultyId);
    }

    public List<Timetable> getStudentTimetable(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        
        if (student.getClassGroup() == null) {
            return new ArrayList<>();
        }
        
        return timetableRepository.findByClassGroupId(student.getClassGroup().getId());
    }

    public List<Timetable> getTimetableBySemester(String semester) {
        return timetableRepository.findBySemester(semester);
    }
}
