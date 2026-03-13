package com.acadsched.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subjects")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Subject code is required")
    @Column(unique = true, nullable = false)
    private String subjectCode;

    @NotBlank(message = "Subject name is required")
    @Column(nullable = false)
    private String name;

    @Min(value = 1, message = "Credits must be at least 1")
    @Column(nullable = false)
    private Integer credits;

    @NotBlank(message = "Department is required")
    private String department;

    @NotBlank(message = "Semester is required")
    private String semester;

    @ManyToOne
    @JoinColumn(name = "faculty_id")
    private Faculty faculty;

    @Column(nullable = false)
    private Integer priority = 1; // Higher number = higher priority

    @Enumerated(EnumType.STRING)
    private SubjectType type;

    public enum SubjectType {
        THEORY,
        PRACTICAL,
        TUTORIAL
    }
}
