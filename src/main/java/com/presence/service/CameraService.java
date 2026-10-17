/**
 * Purpose: Opens the webcam, reads frames, and draws recognition labels.
 * Why it is used: It is the bridge between the physical camera and automatic attendance marking.
 */
package com.presence.service;

// OpenCvConfiguration: Checks whether OpenCV native C++ binaries loaded successfully on this OS.
import com.presence.config.OpenCvConfiguration;
// PresenceProperties: Provides configured camera index (e.g. 0) and target resolution width/height.
import com.presence.config.PresenceProperties;
// BytePointer: Byte buffer used by JavaCPP to hold compressed JPEG image data in native memory.
import org.bytedeco.javacpp.BytePointer;
// Mat: OpenCV matrix data structure representing raw camera video frames.
import org.bytedeco.opencv.opencv_core.Mat;
// Point: 2D coordinate point (x, y) used to position text labels above detected faces.
import org.bytedeco.opencv.opencv_core.Point;
// Rect: Represents the rectangular bounding box (x, y, width, height) surrounding a detected face.
import org.bytedeco.opencv.opencv_core.Rect;
// Scalar: 4-element color representation in OpenCV (BGR color space) for drawing bounding boxes and labels.
import org.bytedeco.opencv.opencv_core.Scalar;
// VideoCapture: OpenCV class for connecting to the hardware webcam and grabbing video frames.
import org.bytedeco.opencv.opencv_videoio.VideoCapture;
// Logger: Console logger for reporting camera connection successes or warnings.
import org.slf4j.Logger;
// LoggerFactory: Factory to initialize the logger for CameraService.
import org.slf4j.LoggerFactory;
// Service: Marks this class as a Spring business service managing webcam lifecycle.
import org.springframework.stereotype.Service;

// PreDestroy: Ensures camera hardware is cleanly released when the Spring application shuts down.
import jakarta.annotation.PreDestroy;
// List: Represents collections of detected face rectangles within a single frame.
import java.util.List;

// imencode: Compresses raw OpenCV Mat pixel frames into standard JPEG format for web streaming.
import static org.bytedeco.opencv.global.opencv_imgcodecs.imencode;
// FONT_HERSHEY_SIMPLEX: Standard legible font type for drawing names onto video frames.
import static org.bytedeco.opencv.global.opencv_imgproc.FONT_HERSHEY_SIMPLEX;
// LINE_AA: Enables anti-aliasing for smooth bounding box lines and crisp name text.
import static org.bytedeco.opencv.global.opencv_imgproc.LINE_AA;
// putText: Renders text strings (like person names or "Unknown") onto video frames.
import static org.bytedeco.opencv.global.opencv_imgproc.putText;
// rectangle: Draws the green or red bounding box around detected faces.
import static org.bytedeco.opencv.global.opencv_imgproc.rectangle;
// CAP_PROP_FRAME_HEIGHT: OpenCV property ID to request webcam frame height (e.g. 480).
import static org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_HEIGHT;
// CAP_PROP_FRAME_WIDTH: OpenCV property ID to request webcam frame width (e.g. 640).
import static org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_WIDTH;

/**
 * Owns the physical webcam hardware.
 * Grabs video frames, detects faces, draws bounding boxes and name labels,
 * and records attendance when a registered person is recognized.
 */
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
