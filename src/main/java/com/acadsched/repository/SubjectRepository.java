package com.acadsched.repository;

import com.acadsched.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    Optional<Subject> findBySubjectCode(String subjectCode);
    List<Subject> findByDepartment(String department);
    List<Subject> findBySemester(String semester);
    List<Subject> findByDepartmentAndSemester(String department, String semester);
    List<Subject> findByOrderByPriorityDesc();
    boolean existsBySubjectCode(String subjectCode);

    /** Fetch subjects with their eligible faculty pool pre-loaded (avoids LazyInitializationException). */
    @Query("SELECT DISTINCT s FROM Subject s LEFT JOIN FETCH s.eligibleFaculty WHERE s.semester = :semester")
    List<Subject> findBySemesterWithFaculty(@Param("semester") String semester);

    /** Find subjects where a specific faculty is in the eligible pool. */
    @Query("SELECT s FROM Subject s JOIN s.eligibleFaculty f WHERE f.id = :facultyId")
    List<Subject> findByEligibleFacultyId(@Param("facultyId") Long facultyId);
}
