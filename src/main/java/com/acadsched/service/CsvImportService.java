package com.acadsched.service;

import com.acadsched.model.*;
import com.acadsched.repository.*;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImportService {

    private final FacultyRepository facultyRepository;
    private final ClassroomRepository classroomRepository;
    private final SubjectRepository subjectRepository;

    /**
     * Import faculty from CSV.
     * Columns: name, facultyId, department, email, phoneNumber
     */
    @Transactional
    public Map<String, Object> importFaculty(MultipartFile file) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<Faculty> toSave = new ArrayList<>();

        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(file.getInputStream())).build()) {

            String[] header = reader.readNext();
            if (header == null) {
                result.put("success", false);
                result.put("errors", List.of("Empty CSV file"));
                return result;
            }

            String[] row;
            int rowNum = 1;
            while ((row = reader.readNext()) != null) {
                rowNum++;
                if (row.length < 4) {
                    errors.add("Row " + rowNum + ": Missing required columns (need at least name, facultyId, department, email)");
                    break;
                }

                String name = row[0].trim();
                String facultyId = row[1].trim();
                String department = row[2].trim();
                String email = row[3].trim();
                String phone = row.length > 4 ? row[4].trim() : "";

                if (name.isEmpty() || facultyId.isEmpty() || department.isEmpty() || email.isEmpty()) {
                    errors.add("Row " + rowNum + ": Required fields cannot be empty");
                    break;
                }

                if (facultyRepository.existsByFacultyId(facultyId)) {
                    errors.add("Row " + rowNum + ": Faculty ID '" + facultyId + "' already exists");
                    break;
                }
                if (facultyRepository.existsByEmail(email)) {
                    errors.add("Row " + rowNum + ": Email '" + email + "' already exists");
                    break;
                }

                Faculty f = new Faculty();
                f.setName(name);
                f.setFacultyId(facultyId);
                f.setDepartment(department);
                f.setEmail(email);
                f.setPhoneNumber(phone);
                f.setAvailable(true);
                toSave.add(f);
            }
        } catch (Exception e) {
            errors.add("CSV parsing error: " + e.getMessage());
        }

        if (!errors.isEmpty()) {
            result.put("success", false);
            result.put("errors", errors);
            return result;
        }

        facultyRepository.saveAll(toSave);
        result.put("success", true);
        result.put("count", toSave.size());
        return result;
    }

    /**
     * Import classrooms from CSV.
     * Columns: roomNumber, building, capacity, type, equipment
     */
    @Transactional
    public Map<String, Object> importClassrooms(MultipartFile file) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<Classroom> toSave = new ArrayList<>();

        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(file.getInputStream())).build()) {

            String[] header = reader.readNext();
            if (header == null) {
                result.put("success", false);
                result.put("errors", List.of("Empty CSV file"));
                return result;
            }

            String[] row;
            int rowNum = 1;
            while ((row = reader.readNext()) != null) {
                rowNum++;
                if (row.length < 3) {
                    errors.add("Row " + rowNum + ": Missing required columns (need at least roomNumber, building, capacity)");
                    break;
                }

                String roomNumber = row[0].trim();
                String building = row[1].trim();
                String capacityStr = row[2].trim();
                String typeStr = row.length > 3 ? row[3].trim().toUpperCase() : "";
                String equipment = row.length > 4 ? row[4].trim() : "";

                if (roomNumber.isEmpty() || building.isEmpty() || capacityStr.isEmpty()) {
                    errors.add("Row " + rowNum + ": Required fields cannot be empty");
                    break;
                }

                if (classroomRepository.existsByRoomNumber(roomNumber)) {
                    errors.add("Row " + rowNum + ": Room '" + roomNumber + "' already exists");
                    break;
                }

                int capacity;
                try {
                    capacity = Integer.parseInt(capacityStr);
                } catch (NumberFormatException e) {
                    errors.add("Row " + rowNum + ": Invalid capacity '" + capacityStr + "'");
                    break;
                }

                Classroom c = new Classroom();
                c.setRoomNumber(roomNumber);
                c.setBuilding(building);
                c.setCapacity(capacity);
                c.setAvailable(true);
                c.setEquipment(equipment);

                if (!typeStr.isEmpty()) {
                    try {
                        c.setType(Classroom.RoomType.valueOf(typeStr));
                    } catch (IllegalArgumentException e) {
                        c.setType(Classroom.RoomType.LECTURE_HALL);
                    }
                }

                toSave.add(c);
            }
        } catch (Exception e) {
            errors.add("CSV parsing error: " + e.getMessage());
        }

        if (!errors.isEmpty()) {
            result.put("success", false);
            result.put("errors", errors);
            return result;
        }

        classroomRepository.saveAll(toSave);
        result.put("success", true);
        result.put("count", toSave.size());
        return result;
    }

    /**
     * Import subjects from CSV.
     * Columns: subjectCode, name, credits, department, semester, facultyId, priority, type
     */
    @Transactional
    public Map<String, Object> importSubjects(MultipartFile file) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<Subject> toSave = new ArrayList<>();

        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(file.getInputStream())).build()) {

            String[] header = reader.readNext();
            if (header == null) {
                result.put("success", false);
                result.put("errors", List.of("Empty CSV file"));
                return result;
            }

            String[] row;
            int rowNum = 1;
            while ((row = reader.readNext()) != null) {
                rowNum++;
                if (row.length < 5) {
                    errors.add("Row " + rowNum + ": Missing required columns (need at least subjectCode, name, credits, department, semester)");
                    break;
                }

                String code = row[0].trim();
                String name = row[1].trim();
                String creditsStr = row[2].trim();
                String department = row[3].trim();
                String semester = row[4].trim();
                String facultyIdStr = row.length > 5 ? row[5].trim() : "";
                String priorityStr = row.length > 6 ? row[6].trim() : "1";
                String typeStr = row.length > 7 ? row[7].trim().toUpperCase() : "";

                if (code.isEmpty() || name.isEmpty() || creditsStr.isEmpty()
                        || department.isEmpty() || semester.isEmpty()) {
                    errors.add("Row " + rowNum + ": Required fields cannot be empty");
                    break;
                }

                if (subjectRepository.existsBySubjectCode(code)) {
                    errors.add("Row " + rowNum + ": Subject code '" + code + "' already exists");
                    break;
                }

                int credits;
                try {
                    credits = Integer.parseInt(creditsStr);
                } catch (NumberFormatException e) {
                    errors.add("Row " + rowNum + ": Invalid credits '" + creditsStr + "'");
                    break;
                }

                int priority = 1;
                try {
                    if (!priorityStr.isEmpty()) priority = Integer.parseInt(priorityStr);
                } catch (NumberFormatException ignored) {}

                Subject s = new Subject();
                s.setSubjectCode(code);
                s.setName(name);
                s.setCredits(credits);
                s.setDepartment(department);
                s.setSemester(semester);
                s.setPriority(priority);

                if (!typeStr.isEmpty()) {
                    try {
                        s.setType(Subject.SubjectType.valueOf(typeStr));
                    } catch (IllegalArgumentException e) {
                        s.setType(Subject.SubjectType.THEORY);
                    }
                }

                // Link faculty if provided
                if (!facultyIdStr.isEmpty()) {
                    Optional<Faculty> fac = facultyRepository.findByFacultyId(facultyIdStr);
                    if (fac.isPresent()) {
                        s.setFaculty(fac.get());
                    } else {
                        errors.add("Row " + rowNum + ": Faculty ID '" + facultyIdStr + "' not found");
                        break;
                    }
                }

                toSave.add(s);
            }
        } catch (Exception e) {
            errors.add("CSV parsing error: " + e.getMessage());
        }

        if (!errors.isEmpty()) {
            result.put("success", false);
            result.put("errors", errors);
            return result;
        }

        subjectRepository.saveAll(toSave);
        result.put("success", true);
        result.put("count", toSave.size());
        return result;
    }
}
