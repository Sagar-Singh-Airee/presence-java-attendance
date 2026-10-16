/**
 * Purpose: Loads the OpenCV face-recognition library.
 * Why it is used: The application needs OpenCV before it can read camera frames or work with faces.
 */
package com.presence.config;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class OpenCvConfiguration {
    private static final Logger log = LoggerFactory.getLogger(OpenCvConfiguration.class);
    private volatile String failureMessage;

    @PostConstruct
    void loadNativeLibraries() {
        try {
            Loader.load(opencv_java.class);
            log.info("OpenCV native library loaded successfully.");
        } catch (Throwable exception) {
            failureMessage = "OpenCV could not load. Camera recognition is disabled; registration and records remain available.";
            log.error("OpenCV native library failed to load", exception);
        }
    }

    public boolean isAvailable() { return failureMessage == null; }
    public String failureMessage() { return failureMessage; }
}
