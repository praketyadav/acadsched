package com.acadsched.service;

import com.acadsched.model.Timetable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Wrapper service for backward compatibility
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimetableSchedulingService {

    private final SchedulingService schedulingService;

    @Transactional
    public List<Timetable> generateTimetable(String semester, String section) {
        return schedulingService.generateTimetable(semester, section);
    }

    @Transactional
    public List<Timetable> generateTimetable(String semester, String section, int studentGapHours) {
        return schedulingService.generateTimetable(semester, section, studentGapHours);
    }

    public List<Timetable> getTimetableBySemesterAndSection(String semester, String section) {
        return schedulingService.getTimetableBySemesterAndSection(semester, section);
    }

    public List<Timetable> getFacultyTimetable(Long facultyId) {
        return schedulingService.getFacultyTimetable(facultyId);
    }

    @Transactional
    public void rescheduleSession(Long timetableId, String newDay, String newTimeSlot) {
        schedulingService.rescheduleSession(timetableId, newDay, newTimeSlot);
    }

    @Transactional
    public List<String> handleFacultyLeave(Long facultyId, String day, String timeSlot) {
        return schedulingService.handleFacultyLeave(facultyId, day, timeSlot);
    }
}
