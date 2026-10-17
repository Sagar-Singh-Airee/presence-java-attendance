/**
 * Purpose: Tests important application behavior automatically.
 * Why it is used: It checks that database, attendance, registration, export, and OpenCV features keep working after changes.
 */
package com.presence;

import com.presence.config.OpenCvConfiguration;
import com.presence.model.Person;
import com.presence.repository.AttendanceRepository;
import com.presence.repository.PersonRepository;
import com.presence.service.CsvExportService;
import com.presence.service.PeopleService;
import com.presence.service.RegistrationService;
import org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "presence.data-directory=target/test-presence-data",
        "presence.camera-index=-1"
})
class PresenceApplicationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PersonRepository people;
    @Autowired AttendanceRepository attendance;
    @Autowired PeopleService peopleService;
    @Autowired RegistrationService registration;
    @Autowired OpenCvConfiguration openCv;
    @Autowired CsvExportService csv;

    @BeforeEach
    void clearDatabase() {
        jdbc.execute("DELETE FROM attendance");
        jdbc.execute("DELETE FROM face_samples");
        jdbc.execute("DELETE FROM persons");
    }

    @Test
    void schemaInitializesAndPersonCanBeRegistered() {
        Person person = people.create("Amina Patel");
        assertTrue(person.id() > 0);
        assertEquals(1, people.count());
    }

    @Test
    void duplicatePersonNamesAreRejectedCaseInsensitively() {
        people.create("Amina Patel");
        assertThrows(PersonRepository.DuplicatePersonException.class, () -> people.create("amina patel"));
    }

    @Test
    void dailyAttendanceIsDatabaseProtectedAndLaterDateIsAllowed() {
        Person person = people.create("Daniel Roy");
        assertTrue(attendance.insertIfAbsent(person.id(), person.name(), LocalDate.of(2026, 10, 16), java.time.LocalTime.of(9, 0)));
        assertFalse(attendance.insertIfAbsent(person.id(), person.name(), LocalDate.of(2026, 10, 16), java.time.LocalTime.of(10, 0)));
        assertTrue(attendance.insertIfAbsent(person.id(), person.name(), LocalDate.of(2026, 10, 17), java.time.LocalTime.of(9, 0)));
        assertEquals(2, attendance.findAll().size());
    }

    @Test
    void nameAndDateFiltersUseStoredRecords() {
        Person amina = people.create("Amina Patel");
        Person daniel = people.create("Daniel Roy");
        attendance.insertIfAbsent(amina.id(), amina.name(), LocalDate.of(2026, 10, 16), java.time.LocalTime.NOON);
        attendance.insertIfAbsent(daniel.id(), daniel.name(), LocalDate.of(2026, 10, 17), java.time.LocalTime.NOON);
        assertEquals(1, attendance.find("ami", null).size());
        assertEquals("Daniel Roy", attendance.find(null, LocalDate.of(2026, 10, 17)).getFirst().personName());
    }

    @Test
    void csvExportContainsHeadersAndRealValues() {
        Person person = people.create("Riya, Singh");
        attendance.insertIfAbsent(person.id(), person.name(), LocalDate.of(2026, 10, 16), java.time.LocalTime.of(8, 30));
        String contents = new String(csv.export(attendance.findAll()));
        assertTrue(contents.startsWith("Name,Date,Time"));
        assertTrue(contents.contains("\"Riya, Singh\""));
        assertTrue(contents.contains("2026-10-16"));
    }

    @Test
    void deletingPersonPreservesAttendanceHistory() {
        Person person = people.create("Noah Das");
        attendance.insertIfAbsent(person.id(), person.name(), LocalDate.of(2026, 10, 16), java.time.LocalTime.of(8, 30));
        peopleService.delete(person.id());
        assertEquals(0, people.count());
        assertEquals("Noah Das", attendance.findAll().getFirst().personName());
        assertNull(attendance.findAll().getFirst().personId());
    }

    @Test
    void invalidImageAndEmptyRegistrationAreRejected() {
        MockMultipartFile invalid = new MockMultipartFile("files", "not-a-face.jpg", "image/jpeg", "not an image".getBytes());
        assertThrows(RegistrationService.RegistrationException.class, () -> registration.register("Jules", java.util.List.of(invalid)));
        assertThrows(RegistrationService.RegistrationException.class, () -> registration.register("Jules", java.util.List.of()));
        assertEquals(0, people.count());
    }

    @Test
    void nativeOpenCvAndContribLbphAreAvailable() {
        assertTrue(openCv.isAvailable(), openCv.failureMessage());
        try (LBPHFaceRecognizer recognizer = LBPHFaceRecognizer.create()) {
            assertNotNull(recognizer);
        }
    }
}
