/**
 * Purpose: Saves and removes uploaded face-photo files.
 * Why it is used: It keeps original training photos organized safely on this computer.
 */
package com.presence.service;

import com.presence.config.DataPaths;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png");
    private final DataPaths paths;

    public FileStorageService(DataPaths paths) { this.paths = paths; }

    public String store(long personId, MultipartFile file) {
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED.contains(extension)) throw new StorageException("Only JPG, JPEG, and PNG photographs are allowed.");
        String filename = UUID.randomUUID() + "." + extension;
        String relative = personId + "/" + filename;
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            try (var source = file.getInputStream()) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return relative;
        } catch (IOException exception) {
            throw new StorageException("The photograph could not be saved locally.");
        }
    }

    public Path resolve(String relativePath) {
        Path target = paths.knownFaces().resolve(relativePath).normalize();
        if (!target.startsWith(paths.knownFaces())) throw new StorageException("Unsafe photograph path was rejected.");
        return target;
    }

    public void deletePersonDirectory(long personId) {
        Path directory = resolve(Long.toString(personId));
        if (!Files.exists(directory)) return;
        try (var files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException exception) { throw new StorageException("A saved photograph could not be removed."); }
            });
        } catch (IOException exception) {
            throw new StorageException("The saved photographs could not be removed.");
        }
    }

    public static String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    public static class StorageException extends RuntimeException {
        public StorageException(String message) { super(message); }
    }
}
