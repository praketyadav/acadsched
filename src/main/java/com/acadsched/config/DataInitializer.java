package com.acadsched.config;

import com.acadsched.model.*;
import com.acadsched.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final UserRepository userRepository;
    private final FacultyRepository facultyRepository;
    private final ClassroomRepository classroomRepository;
    private final SubjectRepository subjectRepository;
    private final ClassGroupRepository classGroupRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    @Bean
    CommandLineRunner initDatabase() {
        return args -> transactionTemplate.executeWithoutResult(status -> {
            try {
                // Only initialize if database is empty
                if (userRepository.count() > 0) {
                    log.info("Database already initialized");
                    return;
                }

                log.info("Initializing database with sample data...");

                // Create Admin User
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setEmail("admin@acadsched.com");
                admin.setFullName("System Administrator");
                admin.setRole(User.Role.ADMIN);
                admin.setEnabled(true);
                userRepository.save(admin);
                log.info("Created admin user");

                // Create Faculty User
                User facultyUser = new User();
                facultyUser.setUsername("faculty1");
                facultyUser.setPassword(passwordEncoder.encode("faculty123"));
                facultyUser.setEmail("faculty1@acadsched.com");
                facultyUser.setFullName("Dr. John Smith");
                facultyUser.setRole(User.Role.FACULTY);
                facultyUser.setEnabled(true);
                userRepository.save(facultyUser);

                // Student user created after class groups (needs classGroup reference)
                log.info("Created admin and faculty users");

                // Create Faculty Members
                Faculty faculty1 = new Faculty();
                faculty1.setName("Dr. John Smith");
                faculty1.setFacultyId("FAC001");
                faculty1.setDepartment("Computer Science");
                faculty1.setEmail("john.smith@acadsched.com");
                faculty1.setPhoneNumber("+1234567890");
                faculty1.setAvailable(true);
                Set<String> availability1 = new HashSet<>();
                availability1.add("MONDAY-09:00-10:00");
                availability1.add("TUESDAY-10:00-11:00");
                faculty1.setAvailableTimeSlots(availability1);
                faculty1 = facultyRepository.save(faculty1);

                Faculty faculty2 = new Faculty();
                faculty2.setName("Dr. Sarah Johnson");
                faculty2.setFacultyId("FAC002");
                faculty2.setDepartment("Computer Science");
                faculty2.setEmail("sarah.johnson@acadsched.com");
                faculty2.setPhoneNumber("+1234567891");
                faculty2.setAvailable(true);
                faculty2 = facultyRepository.save(faculty2);

                Faculty faculty3 = new Faculty();
                faculty3.setName("Prof. Michael Brown");
                faculty3.setFacultyId("FAC003");
                faculty3.setDepartment("Mathematics");
                faculty3.setEmail("michael.brown@acadsched.com");
                faculty3.setPhoneNumber("+1234567892");
                faculty3.setAvailable(true);
                faculty3 = facultyRepository.save(faculty3);
                log.info("Created sample faculty members");

                // Create Classrooms
                Classroom room1 = new Classroom();
                room1.setRoomNumber("CS-101");
                room1.setBuilding("Computer Science Block");
                room1.setCapacity(60);
                room1.setType(Classroom.RoomType.LECTURE_HALL);
                room1.setAvailable(true);
                room1.setEquipment("Projector, Smart Board");
                classroomRepository.save(room1);

                Classroom room2 = new Classroom();
                room2.setRoomNumber("CS-LAB1");
                room2.setBuilding("Computer Science Block");
                room2.setCapacity(40);
                room2.setType(Classroom.RoomType.LABORATORY);
                room2.setAvailable(true);
                room2.setEquipment("40 Computers, Projector");
                classroomRepository.save(room2);

                Classroom room3 = new Classroom();
                room3.setRoomNumber("MATH-201");
                room3.setBuilding("Mathematics Block");
                room3.setCapacity(50);
                room3.setType(Classroom.RoomType.LECTURE_HALL);
                room3.setAvailable(true);
                room3.setEquipment("Projector, Whiteboard");
                classroomRepository.save(room3);

                Classroom room4 = new Classroom();
                room4.setRoomNumber("SEM-301");
                room4.setBuilding("Main Building");
                room4.setCapacity(30);
                room4.setType(Classroom.RoomType.SEMINAR_ROOM);
                room4.setAvailable(true);
                room4.setEquipment("Projector, Audio System");
                classroomRepository.save(room4);
                log.info("Created sample classrooms");

                // Create Class Groups
                ClassGroup cse3rdYear = new ClassGroup();
                cse3rdYear.setName("CSE 3rd Year");
                cse3rdYear.setDepartment("Computer Science");
                cse3rdYear.setYear("3rd Year");
                cse3rdYear.setSection("A");
                cse3rdYear.setStudentStrength(60);
                cse3rdYear.setActive(true);
                cse3rdYear = classGroupRepository.save(cse3rdYear);

                ClassGroup ece4thYear = new ClassGroup();
                ece4thYear.setName("ECE 4th Year");
                ece4thYear.setDepartment("Electronics");
                ece4thYear.setYear("4th Year");
                ece4thYear.setSection("A");
                ece4thYear.setStudentStrength(50);
                ece4thYear.setActive(true);
                classGroupRepository.save(ece4thYear);

                ClassGroup mech2ndYear = new ClassGroup();
                mech2ndYear.setName("MECH 2nd Year");
                mech2ndYear.setDepartment("Mechanical");
                mech2ndYear.setYear("2nd Year");
                mech2ndYear.setSection("B");
                mech2ndYear.setStudentStrength(55);
                mech2ndYear.setActive(true);
                classGroupRepository.save(mech2ndYear);
                log.info("Created sample class groups");

                // ── Create Student User (assigned to CSE 3rd Year) ──────
                User student = new User();
                student.setUsername("student1");
                student.setPassword(passwordEncoder.encode("student123"));
                student.setEmail("student1@acadsched.com");
                student.setFullName("Jane Doe");
                student.setRole(User.Role.STUDENT);
                student.setClassGroup(cse3rdYear);
                student.setEnabled(true);
                userRepository.save(student);
                log.info("Created student user → assigned to ClassGroup '{}'", cse3rdYear.getName());

                // ── Link faculty user to Faculty entity ─────────────────
                facultyUser.setFaculty(faculty1);
                userRepository.save(facultyUser);
                log.info("Linked faculty user '{}' → Faculty '{}'",
                        facultyUser.getUsername(), faculty1.getName());

                // Create Subjects (with eligible faculty pools)
                // All entities are managed within this transaction,
                // so the ManyToMany cascade works correctly.
                Subject subject1 = new Subject();
                subject1.setSubjectCode("CS101");
                subject1.setName("Introduction to Programming");
                subject1.setCredits(4);
                subject1.setDepartment("Computer Science");
                subject1.setSemester("Fall 2024");
                subject1.getEligibleFaculty().add(faculty1);
                subject1.getEligibleFaculty().add(faculty2);
                subject1.setPriority(5);
                subject1.setType(Subject.SubjectType.THEORY);
                subjectRepository.save(subject1);

                Subject subject2 = new Subject();
                subject2.setSubjectCode("CS102");
                subject2.setName("Data Structures");
                subject2.setCredits(4);
                subject2.setDepartment("Computer Science");
                subject2.setSemester("Fall 2024");
                subject2.getEligibleFaculty().add(faculty1);
                subject2.setPriority(4);
                subject2.setType(Subject.SubjectType.THEORY);
                subjectRepository.save(subject2);

                Subject subject3 = new Subject();
                subject3.setSubjectCode("CS103");
                subject3.setName("Database Systems");
                subject3.setCredits(3);
                subject3.setDepartment("Computer Science");
                subject3.setSemester("Fall 2024");
                subject3.getEligibleFaculty().add(faculty2);
                subject3.getEligibleFaculty().add(faculty3);
                subject3.setPriority(3);
                subject3.setType(Subject.SubjectType.THEORY);
                subjectRepository.save(subject3);

                Subject subject4 = new Subject();
                subject4.setSubjectCode("MATH101");
                subject4.setName("Calculus I");
                subject4.setCredits(4);
                subject4.setDepartment("Mathematics");
                subject4.setSemester("Fall 2024");
                subject4.getEligibleFaculty().add(faculty3);
                subject4.setPriority(4);
                subject4.setType(Subject.SubjectType.THEORY);
                subjectRepository.save(subject4);

                Subject subject5 = new Subject();
                subject5.setSubjectCode("CS-LAB1");
                subject5.setName("Programming Lab");
                subject5.setCredits(2);
                subject5.setDepartment("Computer Science");
                subject5.setSemester("Fall 2024");
                subject5.getEligibleFaculty().add(faculty1);
                subject5.getEligibleFaculty().add(faculty2);
                subject5.setPriority(3);
                subject5.setType(Subject.SubjectType.PRACTICAL);
                subjectRepository.save(subject5);

                log.info("Created sample subjects");
                log.info("Database initialization complete!");
                log.info("Default credentials:");
                log.info("Admin - username: admin, password: admin123");
                log.info("Faculty - username: faculty1, password: faculty123");
                log.info("Student - username: student1, password: student123");
            } catch (Exception e) {
                log.error("Error during database initialization: {}", e.getMessage(), e);
                log.warn("Continuing without sample data initialization");
            }
        });
    }
}
