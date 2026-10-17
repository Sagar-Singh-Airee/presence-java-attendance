/**
 * Purpose: Safely sends saved face photos to the browser.
 * Why it is used: It lets the registered-faces page display photos without exposing arbitrary files.
 */
package com.presence.controller;

import com.presence.repository.FaceSampleRepository;
import com.presence.service.FileStorageService;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@Controller
public class PhotoController {
    private final FaceSampleRepository samples;
    private final FileStorageService storage;
    public PhotoController(FaceSampleRepository samples, FileStorageService storage) { this.samples = samples; this.storage = storage; }

    @GetMapping("/photos/{personId}/{filename:.+}")
    public ResponseEntity<UrlResource> photo(@PathVariable long personId, @PathVariable String filename) throws Exception {
        String relative = personId + "/" + filename;
        if (!samples.belongsTo(personId, relative)) return ResponseEntity.notFound().build();
        Path path = storage.resolve(relative);
        if (!Files.isRegularFile(path)) return ResponseEntity.notFound().build();
        String contentType = Files.probeContentType(path);
        MediaType type = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok().contentType(Objects.requireNonNull(type)).body(new UrlResource(Objects.requireNonNull(path.toUri())));
    }
}
