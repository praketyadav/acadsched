package com.acadsched.repository;

import com.acadsched.model.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    Optional<Faculty> findByFacultyId(String facultyId);
    List<Faculty> findByDepartment(String department);
    List<Faculty> findByAvailable(Boolean available);
    boolean existsByFacultyId(String facultyId);
    boolean existsByEmail(String email);
    Optional<Faculty> findByEmail(String email);
}
