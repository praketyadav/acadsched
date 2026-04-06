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
     * Import subjects from CSV (header-based mapping).
     * Required columns: name, subjectCode, credits, department, semester
     * Optional columns: type (THEORY/PRACTICAL), facultyId, priority
     * Columns can be in any order — the header row determines mapping.
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

            // Build column index map from header (case-insensitive, trimmed)
            Map<String, Integer> colMap = new LinkedHashMap<>();
            for (int i = 0; i < header.length; i++) {
                colMap.put(header[i].trim().toLowerCase(), i);
            }

            // Validate required columns exist
            String[] requiredCols = {"name", "subjectcode", "credits", "department", "semester"};
            for (String col : requiredCols) {
                if (!colMap.containsKey(col)) {
                    result.put("success", false);
                    result.put("errors", List.of("Missing required column: '" + col + "'. Required: name, subjectCode, credits, department, semester"));
                    return result;
                }
            }

            String[] row;
            int rowNum = 1;
            while ((row = reader.readNext()) != null) {
                rowNum++;

                String name = getCol(row, colMap, "name");
                String code = getCol(row, colMap, "subjectcode");
                String creditsStr = getCol(row, colMap, "credits");
                String department = getCol(row, colMap, "department");
                String semester = getCol(row, colMap, "semester");
                String typeStr = getCol(row, colMap, "type").toUpperCase();
                String facultyIdStr = getCol(row, colMap, "facultyid");
                String priorityStr = getCol(row, colMap, "priority");

                // Validate required fields
                if (name.isEmpty() || code.isEmpty() || creditsStr.isEmpty()
                        || department.isEmpty() || semester.isEmpty()) {
                    errors.add("Row " + rowNum + ": Required fields (name, subjectCode, credits, department, semester) cannot be empty");
                    break;
                }

                // Check for duplicate subject code
                if (subjectRepository.existsBySubjectCode(code)) {
                    errors.add("Row " + rowNum + ": Subject code '" + code + "' already exists in the database");
                    break;
                }

                // Parse credits
                int credits;
                try {
                    credits = Integer.parseInt(creditsStr);
                } catch (NumberFormatException e) {
                    errors.add("Row " + rowNum + ": Invalid credit value '" + creditsStr + "' for subject '" + code + "'");
                    break;
                }

                // Parse priority (optional, defaults to 1)
                int priority = 1;
                if (!priorityStr.isEmpty()) {
                    try {
                        priority = Integer.parseInt(priorityStr);
                    } catch (NumberFormatException e) {
                        errors.add("Row " + rowNum + ": Invalid priority value '" + priorityStr + "' for subject '" + code + "'");
                        break;
                    }
                }

                Subject s = new Subject();
                s.setSubjectCode(code);
                s.setName(name);
                s.setCredits(credits);
                s.setDepartment(department);
                s.setSemester(semester);
                s.setPriority(priority);

                // Parse type (optional, defaults to THEORY)
                if (!typeStr.isEmpty()) {
                    try {
                        s.setType(Subject.SubjectType.valueOf(typeStr));
                    } catch (IllegalArgumentException e) {
                        errors.add("Row " + rowNum + ": Invalid subject type '" + typeStr + "' for subject '" + code + "'. Must be THEORY or PRACTICAL");
                        break;
                    }
                } else {
                    s.setType(Subject.SubjectType.THEORY);
                }

                // Link eligible faculty pool (pipe-separated: FAC001|FAC003|FAC006)
                if (!facultyIdStr.isEmpty()) {
                    String[] facultyIds = facultyIdStr.split("\\|");
                    for (String fid : facultyIds) {
                        String trimmedFid = fid.trim();
                        if (trimmedFid.isEmpty()) continue;
                        Optional<Faculty> fac = facultyRepository.findByFacultyId(trimmedFid);
                        if (fac.isPresent()) {
                            s.getEligibleFaculty().add(fac.get());
                        } else {
                            errors.add("Row " + rowNum + ": Faculty ID '" + trimmedFid + "' not found for subject '" + code + "'");
                            break;
                        }
                    }
                    if (!errors.isEmpty()) break;
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

    /**
     * Safely get a column value by header name. Returns "" if the column doesn't exist.
     */
    private String getCol(String[] row, Map<String, Integer> colMap, String colName) {
        Integer idx = colMap.get(colName);
        if (idx == null || idx >= row.length) return "";
        return row[idx].trim();
    }
}
