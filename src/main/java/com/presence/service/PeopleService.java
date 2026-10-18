/**
 * Purpose: Manages registered people and their removal.
 * Why it is used: It removes recognition data safely while preserving past attendance records.
 */
package com.presence.service;

// Person: Data model representing the person being deleted.
import com.presence.model.Person;
// AttendanceRepository: Clears the person_id foreign key reference on past attendance records so history is retained.
import com.presence.repository.AttendanceRepository;
// FaceSampleRepository: Deletes the database records linking training photos to this person.
import com.presence.repository.FaceSampleRepository;
// PersonRepository: Deletes the primary person record from the persons table in SQLite.
import com.presence.repository.PersonRepository;
// Logger + LoggerFactory: Provides structured logging so file-cleanup issues are recorded for debugging.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// Service: Marks this class as a Spring business service component.
import org.springframework.stereotype.Service;
// Transactional: Ensures all database deletion steps execute as a single atomic unit of work.
import org.springframework.transaction.annotation.Transactional;

@Service
public class PeopleService {

    private static final Logger log = LoggerFactory.getLogger(PeopleService.class);

    private final PersonRepository people;
    private final FaceSampleRepository samples;
    private final AttendanceRepository attendance;
    private final FileStorageService storage;
    private final FaceRecognitionService recognition;

    public PeopleService(PersonRepository people, FaceSampleRepository samples, AttendanceRepository attendance,
                         FileStorageService storage, FaceRecognitionService recognition) {
        this.people = people;
        this.samples = samples;
        this.attendance = attendance;
        this.storage = storage;
        this.recognition = recognition;
    }

    /**
     * Safely deletes a registered person from the system.
     *
     * The deletion happens in two phases:
     *   Phase 1 (transactional): All database rows are removed atomically — if any SQL step fails,
     *           the entire deletion rolls back and no data is lost.
     *   Phase 2 (post-commit): Physical photo files are deleted from disk and the LBPH recognition
     *           model is retrained. These are intentionally outside the transaction because file I/O
     *           failures (e.g., Windows file locks) should NOT prevent a successful database deletion
     *           from being reported to the user.
     *
     * Returns a DeleteResult containing the removed person and an optional warning if file cleanup failed.
     */
    public DeleteResult delete(long id) {
        // Phase 1: Remove all database records inside a transaction.
        Person person = deleteFromDatabase(id);

        // Phase 2: Clean up files and retrain the model outside the transaction.
        String warning = null;
        try {
            storage.deletePersonDirectory(id);
        } catch (FileStorageService.StorageException exception) {
            // The database rows are already deleted. Log the file-cleanup failure but don't treat it as a fatal error.
            log.warn("Database deletion of '{}' (id={}) succeeded, but file cleanup failed: {}",
                    person.name(), id, exception.getMessage());
            warning = "Files for " + person.name() + " could not be fully removed from disk.";
        }

        // Retrain the recognition model so the deleted person is no longer matched by the camera.
        try {
            recognition.rebuildModel();
        } catch (RuntimeException exception) {
            log.warn("Model rebuild after deleting '{}' failed: {}", person.name(), exception.getMessage());
            if (warning == null) {
                warning = "The recognition model could not be retrained immediately; it will recover on next restart.";
            }
        }

        return new DeleteResult(person, warning);
    }

    /**
     * Phase 1: Transactional database deletion.
     * All three SQL operations (clear attendance references, delete samples, delete person)
     * run inside a single transaction. If any fails, SQLite rolls back everything.
     */
    @Transactional
    protected Person deleteFromDatabase(long id) {
        // Step 1: Verify the person exists before proceeding.
        Person person = people.findById(id)
                .orElseThrow(() -> new PersonNotFoundException("That registered person no longer exists."));

        // Step 2: Unlink past attendance records so historical attendance dates and times remain preserved.
        attendance.clearPersonReference(id);

        // Step 3: Delete sample photo metadata from SQLite.
        samples.deleteByPersonId(id);

        // Step 4: Delete the person from the SQLite persons table.
        people.delete(id);

        return person;
    }

    /**
     * Holds the result of a person deletion: the removed person and an optional warning
     * when file cleanup or model retraining encountered a non-fatal issue.
     */
    public record DeleteResult(Person person, String warning) {
        public boolean hasWarning() { return warning != null; }
    }

    public static class PersonNotFoundException extends RuntimeException {
        public PersonNotFoundException(String message) { 
            super(message); 
        }
    }
}
