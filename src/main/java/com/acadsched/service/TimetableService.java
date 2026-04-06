package com.acadsched.service;

import com.acadsched.model.ClassGroup;
import com.acadsched.model.Timetable;
import com.acadsched.model.User;
import com.acadsched.repository.TimetableRepository;
import com.acadsched.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimetableService {

    private final TimetableRepository timetableRepository;
    private final UserRepository userRepository;

    // ── Student timetable (by User ID) ─────────────────────────────────
    public List<Timetable> getStudentTimetable(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        if (student.getClassGroup() == null) {
            log.warn("Student {} has no class group", student.getUsername());
            return new ArrayList<>();
        }

        return timetableRepository.findByClassGroupId(student.getClassGroup().getId());
    }

    // ── Student timetable (by username — used by controller) ───────────
    public List<Timetable> getStudentTimetableByUsername(String username) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (student.getClassGroup() == null) {
            log.warn("Student '{}' has no class group assigned", username);
            return new ArrayList<>();
        }

        log.info("Loading timetable for student '{}' → ClassGroup '{}'",
                username, student.getClassGroup().getName());
        return timetableRepository.findByClassGroupId(student.getClassGroup().getId());
    }

    // ── Lookup the ClassGroup for a student ────────────────────────────
    public Optional<ClassGroup> findClassGroupForStudent(String username) {
        return userRepository.findByUsername(username)
                .map(User::getClassGroup);
    }

    // ── Check if student is assigned to a class group ──────────────────
    public boolean isStudentAssigned(String username) {
        return userRepository.findByUsername(username)
                .map(user -> user.getClassGroup() != null)
                .orElse(false);
    }

    // ── Faculty timetable ──────────────────────────────────────────────
    public List<Timetable> getFacultyTimetable(Long facultyId) {
        return timetableRepository.findByFacultyId(facultyId);
    }

    // ── Class group timetable ──────────────────────────────────────────
    public List<Timetable> getClassGroupTimetable(Long classGroupId) {
        return timetableRepository.findByClassGroupId(classGroupId);
    }

    // ── By semester ────────────────────────────────────────────────────
    public List<Timetable> getTimetableBySemester(String semester) {
        return timetableRepository.findBySemester(semester);
    }
}
