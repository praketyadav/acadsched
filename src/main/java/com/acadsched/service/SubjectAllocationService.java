package com.acadsched.service;

import com.acadsched.dto.SessionDTO;
import com.acadsched.model.ClassGroup;
import com.acadsched.model.Faculty;
import com.acadsched.model.Subject;
import com.acadsched.model.Subject.SubjectType;
import com.acadsched.repository.ClassGroupRepository;
import com.acadsched.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Subject Allocation Service — the pre-processor for the scheduling engine.
 *
 * Takes a semester + section, fetches all matching Subjects, and "unpacks"
 * each into individual {@link SessionDTO} objects based on credits and type.
 *
 * Faculty Pool Model:
 * Instead of requiring a single fixed Faculty, each SessionDTO now carries
 * a {@code List<Long> eligibleFacultyIds} populated from the Subject's
 * ManyToMany faculty pool. The scheduler dynamically selects one per session.
 *
 * <ul>
 *   <li><b>THEORY / TUTORIAL</b>: 1 session per credit, each independent.</li>
 *   <li><b>PRACTICAL</b>: continuous block, linked by linkedGroupId.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubjectAllocationService {

    private final SubjectRepository subjectRepository;
    private final ClassGroupRepository classGroupRepository;

    /**
     * Generate unassigned sessions for the given semester + section.
     */
    @Transactional(readOnly = true)
    public List<SessionDTO> generateUnassignedSessions(String semester, String section) {
        log.info("═══ SESSION ALLOCATION START ═══");
        log.info("Allocating sessions for semester='{}' section='{}'", semester, section);

        // ── 1. Resolve ClassGroup ────────────────────────────────────────
        ClassGroup classGroup = classGroupRepository.findByName(section)
                .orElseGet(() -> {
                    ClassGroup g = new ClassGroup();
                    g.setName(section);
                    g.setDepartment("General");
                    g.setYear("Default");
                    g.setSection(section);
                    g.setStudentStrength(30);
                    g.setActive(true);
                    return classGroupRepository.save(g);
                });

        // ── 2. Fetch subjects WITH faculty pool pre-loaded (JOIN FETCH) ──
        List<Subject> subjects = subjectRepository.findBySemesterWithFaculty(semester);
        log.info("Found {} subjects for semester '{}'", subjects.size(), semester);

        if (subjects.isEmpty()) {
            log.warn("No subjects found for semester '{}' — returning empty session list", semester);
            return Collections.emptyList();
        }

        // ── 3. Unpack subjects into sessions ─────────────────────────────
        List<SessionDTO> sessions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int skippedNoPool = 0;
        int skippedNoCredits = 0;

        for (Subject subject : subjects) {
            // Edge case: empty eligible faculty pool
            Set<Faculty> pool = subject.getEligibleFaculty();
            log.info("  Subject '{}' [{}]: credits={}, type={}, poolSize={}",
                    subject.getName(), subject.getSubjectCode(),
                    subject.getCredits(), subject.getType(),
                    pool != null ? pool.size() : "NULL");

            if (pool == null || pool.isEmpty()) {
                warnings.add("⚠ Skipped " + subject.getSubjectCode()
                        + " — no eligible faculty in pool.");
                skippedNoPool++;
                continue;
            }

            // Edge case: zero or negative credits
            if (subject.getCredits() == null || subject.getCredits() <= 0) {
                warnings.add("⚠ Skipped " + subject.getSubjectCode()
                        + " — credits is 0 or missing.");
                skippedNoCredits++;
                continue;
            }

            // Extract eligible faculty IDs
            List<Long> eligibleIds = pool.stream()
                    .map(Faculty::getId)
                    .collect(Collectors.toList());

            log.info("    → Eligible faculty IDs: {}", eligibleIds);

            SubjectType type = subject.getType() != null
                    ? subject.getType()
                    : SubjectType.THEORY;

            int credits = subject.getCredits();

            switch (type) {
                case PRACTICAL -> sessions.addAll(
                        generateLabBlock(subject, classGroup, semester, credits, eligibleIds));
                case THEORY, TUTORIAL -> sessions.addAll(
                        generateTheorySessions(subject, classGroup, semester, credits, type, eligibleIds));
            }
        }

        // Log warnings
        warnings.forEach(log::warn);

        // ── 4. Sort: lab blocks first (harder to place), then by priority ─
        sessions.sort(Comparator
                .comparing((SessionDTO s) -> !s.isLinked())       // linked first
                .thenComparing(s -> -s.getPriority())              // high priority first
                .thenComparing(s -> -s.getBlockSize())             // larger blocks first
        );

        log.info("═══ SESSION ALLOCATION RESULT ═══");
        log.info("  Total subjects: {} | Skipped (no pool): {} | Skipped (no credits): {}",
                subjects.size(), skippedNoPool, skippedNoCredits);
        log.info("  Generated {} sessions ({} linked lab blocks)",
                sessions.size(),
                sessions.stream().filter(SessionDTO::isLinked).count());

        return sessions;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Generate N independent theory/tutorial sessions (1 per credit).
     */
    private List<SessionDTO> generateTheorySessions(Subject subject,
                                                     ClassGroup classGroup,
                                                     String semester,
                                                     int credits,
                                                     SubjectType type,
                                                     List<Long> eligibleFacultyIds) {
        List<SessionDTO> sessions = new ArrayList<>(credits);
        for (int i = 0; i < credits; i++) {
            sessions.add(SessionDTO.builder()
                    .subject(subject)
                    .classGroup(classGroup)
                    .semester(semester)
                    .sessionType(type)
                    .eligibleFacultyIds(new ArrayList<>(eligibleFacultyIds))
                    .blockSize(1)
                    .blockIndex(0)
                    .linkedGroupId(null)
                    .requiresLabRoom(false)
                    .priority(subject.getPriority())
                    .build());
        }
        return sessions;
    }

    /**
     * Generate a linked lab block: N sessions sharing a linkedGroupId.
     */
    private List<SessionDTO> generateLabBlock(Subject subject,
                                               ClassGroup classGroup,
                                               String semester,
                                               int credits,
                                               List<Long> eligibleFacultyIds) {
        String groupId = UUID.randomUUID().toString();
        List<SessionDTO> block = new ArrayList<>(credits);

        for (int i = 0; i < credits; i++) {
            block.add(SessionDTO.builder()
                    .subject(subject)
                    .classGroup(classGroup)
                    .semester(semester)
                    .sessionType(SubjectType.PRACTICAL)
                    .eligibleFacultyIds(new ArrayList<>(eligibleFacultyIds))
                    .blockSize(credits)
                    .blockIndex(i)
                    .linkedGroupId(groupId)
                    .requiresLabRoom(true)
                    .priority(subject.getPriority())
                    .build());
        }

        log.debug("Created {}-hour lab block for {} (group {}) with {} eligible faculty",
                credits, subject.getSubjectCode(), groupId.substring(0, 8), eligibleFacultyIds.size());

        return block;
    }
}
