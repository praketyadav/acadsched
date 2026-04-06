package com.acadsched.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "faculty")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"eligibleSubjects", "availableTimeSlots"})
public class Faculty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Faculty name is required")
    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String facultyId;

    @NotBlank(message = "Department is required")
    private String department;

    @NotBlank(message = "Email is required")
    @Column(unique = true)
    private String email;

    private String phoneNumber;

    @Column(nullable = false)
    private Boolean available = true;

    @ElementCollection
    @CollectionTable(name = "faculty_availability", joinColumns = @JoinColumn(name = "faculty_id"))
    @Column(name = "time_slot")
    private Set<String> availableTimeSlots = new HashSet<>();

    /** Subjects this faculty is eligible to teach (inverse side). */
    @ManyToMany(mappedBy = "eligibleFaculty")
    private Set<Subject> eligibleSubjects = new HashSet<>();
}

