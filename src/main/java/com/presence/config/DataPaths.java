/**
 * Purpose: Creates and provides the folders used for local application data.
 * Why it is used: It gives every service one safe, consistent place for the database, photos, and recognition model.
 */
package com.presence.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class DataPaths {
    private final PresenceProperties properties;
    private Path root;

    public DataPaths(PresenceProperties properties) { this.properties = properties; }

    @PostConstruct
    void createDirectories() throws IOException {
        root = Path.of(properties.getDataDirectory()).toAbsolutePath().normalize();
        Files.createDirectories(root.resolve("known_faces"));
        Files.createDirectories(root.resolve("models"));
    }

    public Path root() { return root; }
    public Path database() { return root.resolve("presence.db"); }
    public Path knownFaces() { return root.resolve("known_faces"); }
    public Path models() { return root.resolve("models"); }
}
