package com.acadsched.repository;

import com.acadsched.model.ClassGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ClassGroup entity
 * Handles database operations for class groups
 */
@Repository
public interface ClassGroupRepository extends JpaRepository<ClassGroup, Long> {
    
    Optional<ClassGroup> findByName(String name);
    
    List<ClassGroup> findByDepartment(String department);
    
    List<ClassGroup> findByYear(String year);
    
    List<ClassGroup> findByActive(Boolean active);
    
    List<ClassGroup> findByDepartmentAndYear(String department, String year);
    
    boolean existsByName(String name);
}
