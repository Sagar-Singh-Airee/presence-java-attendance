/**
 * Purpose: Safely sends saved face photos to the browser.
 * Why it is used: It lets the registered-faces page display photos without exposing arbitrary files.
 */
package com.presence.controller;

// FaceSampleRepository: Verifies that a photo path actually belongs to the given person before serving it.
import com.presence.repository.FaceSampleRepository;
// FileStorageService: Safely resolves relative image paths inside the local data/known_faces directory.
import com.presence.service.FileStorageService;
// UrlResource: Spring Resource implementation that streams a file directly from a file:// URI.
import org.springframework.core.io.UrlResource;
// MediaType: Represents MIME types like image/jpeg or image/png so the browser renders images inline.
import org.springframework.http.MediaType;
// ResponseEntity: Bundles the HTTP status, content-type header, and image stream.
import org.springframework.http.ResponseEntity;
// Controller: Marks this class as a Spring web controller handling photo requests.
import org.springframework.stereotype.Controller;
// GetMapping: Maps URL paths matching /photos/{personId}/{filename} to this photo serving method.
import org.springframework.web.bind.annotation.GetMapping;
// PathVariable: Extracts URL path segments (personId and filename) into Java method parameters.
import org.springframework.web.bind.annotation.PathVariable;

// Files: Standard Java NIO utility for checking file existence and probing MIME content types.
import java.nio.file.Files;
// Path: Represents a safe file-system path on the local computer.
import java.nio.file.Path;
// Objects: Provides null-safety checks to guarantee objects are non-null before passing to Spring constructors.
import java.util.Objects;

@Controller
public class PhotoController {
    private final FaceSampleRepository samples;
    private final FileStorageService storage;

    public PhotoController(FaceSampleRepository samples, FileStorageService storage) { 
        this.samples = samples; 
        this.storage = storage; 
    }

    /**
     * Securely serves a saved face photograph for preview on the registered people page.
     * Prevents path-traversal attacks by verifying the database record belongs to the person ID.
     */
    @GetMapping("/photos/{personId}/{filename:.+}")
    public ResponseEntity<UrlResource> photo(@PathVariable long personId, @PathVariable String filename) throws Exception {
        String relative = personId + "/" + filename;

        // Security check: Verify that this photo was registered to this person in SQLite.
        if (!samples.belongsTo(personId, relative)) {
            return ResponseEntity.notFound().build();
        }

        Path path = storage.resolve(relative);
        // Ensure the target is an actual existing file on disk.
        if (!Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }

        // Detects whether the file is a JPEG or PNG, defaulting to binary stream if unknown.
        String contentType = Files.probeContentType(path);
        MediaType type = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);

        // Objects.requireNonNull asserts that 'type' and 'path.toUri()' are non-null before building UrlResource.
        return ResponseEntity.ok()
                .contentType(Objects.requireNonNull(type))
                .body(new UrlResource(Objects.requireNonNull(path.toUri())));
    }
}
