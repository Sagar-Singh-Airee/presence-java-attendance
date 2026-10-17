/**
 * Purpose: Validates a registration and adds its face photos.
 * Why it is used: It connects the form, database, photo storage, and recognition-model training in one controlled process.
 */
package com.presence.service;

// Person: Data model representing a registered individual in the system.
import com.presence.model.Person;
// FaceSampleRepository: Database repository for saving records of face sample images.
import com.presence.repository.FaceSampleRepository;
// PersonRepository: Database repository for creating and looking up persons.
import com.presence.repository.PersonRepository;
// Mat: OpenCV matrix data structure representing raw uncompressed image pixel data.
import org.bytedeco.opencv.opencv_core.Mat;
// Service: Marks this class as a Spring business service component.
import org.springframework.stereotype.Service;
// MultipartFile: Represents an uploaded file received over HTTP multipart/form-data.
import org.springframework.web.multipart.MultipartFile;

// ArrayList: Resizable array list used to collect validated photos before writing to disk.
import java.util.ArrayList;
// List: Represents ordered lists of uploaded and validated photo files.
import java.util.List;

@Service
public class RegistrationService {
    private static final long MAX_TOTAL_BYTES = 16L * 1024 * 1024;
    private final PersonRepository people;
    private final FaceSampleRepository samples;
    private final FileStorageService storage;
    private final FaceRecognitionService recognition;

    public RegistrationService(PersonRepository people, FaceSampleRepository samples, FileStorageService storage,
                               FaceRecognitionService recognition) {
        this.people = people;
        this.samples = samples;
        this.storage = storage;
        this.recognition = recognition;
    }

    public RegistrationResult register(String submittedName, List<MultipartFile> uploadedFiles) {
        String name = submittedName == null ? "" : submittedName.trim().replaceAll("\\s+", " ");
        if (name.isBlank()) throw new RegistrationException("Enter a full name.");
        if (name.length() > 100) throw new RegistrationException("A name must be 100 characters or fewer.");
        List<MultipartFile> files = uploadedFiles == null ? List.of() : uploadedFiles.stream()
                .filter(file -> !file.isEmpty()).toList();
        if (files.isEmpty()) throw new RegistrationException("Choose at least one face photograph.");

        // Calculates total uploaded size across all photos using an explicit loop.
        // This avoids functional interface method-reference null-safety warnings on MultipartFile::getSize.
        long total = 0L;
        for (MultipartFile file : files) {
            total += file.getSize();
        }
        if (total > MAX_TOTAL_BYTES) throw new RegistrationException("All photographs together must be 16 MB or smaller.");

        // Validate every file before writing any database or photograph record.
        List<MultipartFile> validated = new ArrayList<>();
        for (MultipartFile file : files) {
            String extension = FileStorageService.extensionOf(file.getOriginalFilename());
            if (!List.of("jpg", "jpeg", "png").contains(extension)) {
                throw new RegistrationException(file.getOriginalFilename() + ": only JPG, JPEG, and PNG files are accepted.");
            }
            try (Mat ignored = recognition.validateAndExtractLargestFace(file.getBytes())) {
                validated.add(file);
            } catch (FaceRecognitionService.FaceProcessingException exception) {
                throw new RegistrationException(file.getOriginalFilename() + ": " + exception.getMessage());
            } catch (Exception exception) {
                throw new RegistrationException(file.getOriginalFilename() + ": this file could not be read.");
            }
        }

        Person existing = people.findByNormalizedName(name).orElse(null);
        Person person = existing == null ? people.create(name) : existing;
        List<String> saved = new ArrayList<>();
        try {
            for (MultipartFile file : validated) {
                String relativePath = storage.store(person.id(), file);
                samples.add(person.id(), relativePath);
                saved.add(relativePath);
            }
            recognition.rebuildModel();
            return new RegistrationResult(person, existing != null, saved.size());
        } catch (RuntimeException exception) {
            // A newly-created row without any usable samples is not a registration.
            if (existing == null && saved.isEmpty()) people.delete(person.id());
            throw exception;
        }
    }

    public record RegistrationResult(Person person, boolean addedToExistingPerson, int photographsAdded) { }
    public static class RegistrationException extends RuntimeException {
        public RegistrationException(String message) { super(message); }
    }
}
