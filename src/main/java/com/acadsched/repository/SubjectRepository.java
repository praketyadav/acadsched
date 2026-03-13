package com.acadsched.repository;

import com.acadsched.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    Optional<Subject> findBySubjectCode(String subjectCode);
    List<Subject> findByDepartment(String department);
    List<Subject> findBySemester(String semester);
    List<Subject> findByDepartmentAndSemester(String department, String semester);
    List<Subject> findByFacultyId(Long facultyId);
    List<Subject> findByOrderByPriorityDesc();
}
