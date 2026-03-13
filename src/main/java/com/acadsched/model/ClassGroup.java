package com.acadsched.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "class_groups")
@Getter
@Setter
public class ClassGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(nullable = false)
    private String department;

    @Column(name = "\"year\"", nullable = false)
    private String year;

    private String section;

    @Column(nullable = false)
    private Integer studentStrength = 30;

    @Column(nullable = false)
    private Boolean active = true;
}
