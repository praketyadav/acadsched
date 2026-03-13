package com.acadsched.service;

import com.acadsched.model.Timetable;
import com.acadsched.model.User;
import com.acadsched.repository.TimetableRepository;
import com.acadsched.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimetableService {

    private final TimetableRepository timetableRepository;
    private final UserRepository userRepository;

    public List<Timetable> getStudentTimetable(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        if (student.getClassGroup() == null) {
            log.warn("Student {} has no class group", student.getUsername());
            return new ArrayList<>();
        }

        return timetableRepository.findByClassGroupId(student.getClassGroup().getId());
    }

    public List<Timetable> getFacultyTimetable(Long facultyId) {
        return timetableRepository.findByFacultyId(facultyId);
    }

    public List<Timetable> getClassGroupTimetable(Long classGroupId) {
        return timetableRepository.findByClassGroupId(classGroupId);
    }

    public List<Timetable> getTimetableBySemester(String semester) {
        return timetableRepository.findBySemester(semester);
    }
}
