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
// Service: Marks this class as a Spring business service component.
import org.springframework.stereotype.Service;
// Transactional: Ensures all deletion steps (database and file cleanups) execute as a single atomic unit of work.
import org.springframework.transaction.annotation.Transactional;

@Service
public class PeopleService {
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
     * Preserves their historical attendance records by setting person_id to NULL.
     * Deletes sample database rows and disk files, then immediately retrains the face model.
     */
    @Transactional
    public Person delete(long id) {
        // Step 1: Verify the person exists before proceeding.
        Person person = people.findById(id).orElseThrow(() -> new PersonNotFoundException("That registered person no longer exists."));

        // Step 2: Unlink past attendance records so historical attendance dates and times remain preserved.
        attendance.clearPersonReference(id);

        // Step 3: Delete sample photo metadata from SQLite.
        samples.deleteByPersonId(id);

        // Step 4: Delete the person from the SQLite persons table.
        people.delete(id);

        // Step 5: Delete the person's directory and physical photos from local disk storage.
        storage.deletePersonDirectory(id);

        // Step 6: Retrain the LBPH recognition model immediately so the deleted person is no longer recognized.
        recognition.rebuildModel();

        return person;
    }

    public static class PersonNotFoundException extends RuntimeException {
        public PersonNotFoundException(String message) { 
            super(message); 
        }
    }
}
