/**
 * Purpose: Opens the webcam, reads frames, and draws recognition labels.
 * Why it is used: It is the bridge between the physical camera and automatic attendance marking.
 */
package com.presence.service;

import com.presence.config.OpenCvConfiguration;
import com.presence.config.PresenceProperties;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imencode;
import static org.bytedeco.opencv.global.opencv_imgproc.FONT_HERSHEY_SIMPLEX;
import static org.bytedeco.opencv.global.opencv_imgproc.LINE_AA;
import static org.bytedeco.opencv.global.opencv_imgproc.putText;
import static org.bytedeco.opencv.global.opencv_imgproc.rectangle;
import static org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_HEIGHT;
import static org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_WIDTH;

/** Owns one physical camera. All MJPEG clients use this shared, locked capture. */
@Service
public class CameraService {
    private static final Logger log = LoggerFactory.getLogger(CameraService.class);
    private final Object captureLock = new Object();
    private final PresenceProperties properties;
    private final OpenCvConfiguration openCv;
    private final FaceRecognitionService recognition;
    private final AttendanceService attendance;
    private VideoCapture camera;
    private boolean stopping;
    private String problem;
    private long retryAfterMillis;

    public CameraService(PresenceProperties properties, OpenCvConfiguration openCv, FaceRecognitionService recognition,
                         AttendanceService attendance) {
        this.properties = properties;
        this.openCv = openCv;
        this.recognition = recognition;
        this.attendance = attendance;
    }

    public byte[] nextJpegFrame() {
        synchronized (captureLock) {
            if (!openCamera()) return null;
            try (Mat frame = new Mat(); BytePointer encoded = new BytePointer()) {
                if (!camera.read(frame) || frame.empty()) {
                    problem = "The webcam returned no frame.";
                    releaseCamera();
                    return null;
                }
                annotate(frame);
                if (!imencode(".jpg", frame, encoded)) {
                    problem = "The webcam frame could not be encoded.";
                    return null;
                }
                byte[] jpeg = new byte[(int) encoded.limit()];
                encoded.get(jpeg);
                return jpeg;
            } catch (RuntimeException exception) {
                problem = "Camera processing failed. Check the webcam and OpenCV installation.";
                log.warn("Camera frame processing failed", exception);
                releaseCamera();
                return null;
            }
        }
    }

    private boolean openCamera() {
        if (stopping || !openCv.isAvailable()) {
            problem = stopping ? "Camera service is stopping." : openCv.failureMessage();
            return false;
        }
        if (camera != null && camera.isOpened()) return true;
        if (System.currentTimeMillis() < retryAfterMillis) return false;
        retryAfterMillis = System.currentTimeMillis() + 5_000;
        releaseCamera();
        try {
            camera = new VideoCapture(properties.getCameraIndex());
            camera.set(CAP_PROP_FRAME_WIDTH, properties.getFrameWidth());
            camera.set(CAP_PROP_FRAME_HEIGHT, properties.getFrameHeight());
            if (!camera.isOpened()) {
                problem = "Camera " + properties.getCameraIndex() + " is unavailable.";
                releaseCamera();
                return false;
            }
            problem = null;
            log.info("Opened camera index {}", properties.getCameraIndex());
            return true;
        } catch (RuntimeException exception) {
            problem = "Camera " + properties.getCameraIndex() + " could not be opened.";
            log.warn("Unable to open camera", exception);
            releaseCamera();
            return false;
        }
    }

    private void annotate(Mat frame) {
        List<Rect> faces = recognition.detect(frame);
        for (Rect bounds : faces) {
            FaceRecognitionService.Prediction prediction = recognition.predict(frame, bounds);
            String label = prediction.known() ? prediction.person().name() : "Unknown";
            Scalar colour = prediction.known() ? new Scalar(182, 244, 81, 0) : new Scalar(90, 100, 255, 0);
            rectangle(frame, bounds, colour, 2, LINE_AA, 0);
            putText(frame, label, new Point(bounds.x(), Math.max(20, bounds.y() - 8)), FONT_HERSHEY_SIMPLEX, 0.65, colour, 2, LINE_AA, false);
            if (prediction.known()) attendance.markPresent(prediction.person().id(), prediction.person().name());
        }
    }

    public boolean isAvailable() {
        synchronized (captureLock) { return camera != null && camera.isOpened() && problem == null; }
    }

    public String status() {
        if (isAvailable()) return recognition.ready() ? "Camera connected. " + recognition.status() : "Camera connected. " + recognition.status();
        return problem == null ? "Camera will connect when the live feed starts." : "Camera unavailable: " + problem;
    }

    private void releaseCamera() {
        if (camera != null) {
            try { camera.release(); camera.close(); } catch (RuntimeException ignored) { }
            camera = null;
        }
    }

    @PreDestroy
    void stop() {
        synchronized (captureLock) { stopping = true; releaseCamera(); }
    }
}
