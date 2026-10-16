/**
 * Purpose: Rebuilds the face-recognition model when the app starts.
 * Why it is used: It makes registered faces ready for recognition after a restart.
 */
package com.presence.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Rebuild from locally saved original photographs every time the application starts. */
@Component
@Order(1)
public class RecognitionModelInitializer implements ApplicationRunner {
    private final FaceRecognitionService recognition;
    public RecognitionModelInitializer(FaceRecognitionService recognition) { this.recognition = recognition; }
    @Override public void run(ApplicationArguments args) { recognition.rebuildModel(); }
}
