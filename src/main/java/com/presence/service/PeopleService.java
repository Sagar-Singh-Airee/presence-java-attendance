/**
 * Purpose: Manages registered people and their removal.
 * Why it is used: It removes recognition data safely while preserving past attendance records.
 */
package com.presence.service;

import com.presence.model.FaceSample;
import com.presence.model.Person;
import com.presence.repository.AttendanceRepository;
import com.presence.repository.FaceSampleRepository;
import com.presence.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Transactional
    public Person delete(long id) {
        Person person = people.findById(id).orElseThrow(() -> new PersonNotFoundException("That registered person no longer exists."));
        List<FaceSample> existingSamples = samples.findByPersonId(id);
        attendance.clearPersonReference(id); // historical name/date/time records stay intact
        samples.deleteByPersonId(id);
        people.delete(id);
        storage.deletePersonDirectory(id);
        recognition.rebuildModel();
        return person;
    }

    public static class PersonNotFoundException extends RuntimeException {
        public PersonNotFoundException(String message) { super(message); }
    }
}
