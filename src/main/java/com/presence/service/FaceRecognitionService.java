/**
 * Purpose: Detects faces and compares them with registered people.
 * Why it is used: It contains the local OpenCV and LBPH recognition work that identifies a person.
 */
package com.presence.service;

import com.presence.config.DataPaths;
import com.presence.config.OpenCvConfiguration;
import com.presence.config.PresenceProperties;
import com.presence.model.FaceSample;
import com.presence.model.Person;
import com.presence.repository.FaceSampleRepository;
import com.presence.repository.PersonRepository;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.bytedeco.opencv.global.opencv_core.CV_32SC1;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;
import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_COLOR;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imdecode;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imread;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_AREA;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.equalizeHist;
import static org.bytedeco.opencv.global.opencv_imgproc.resize;

/** Implements one real OpenCV Haar + LBPH pipeline for both uploads and camera frames. */
@Service
public class FaceRecognitionService {
    public static final int FACE_SIZE = 200;

    // ── Haar detector tuning ──────────────────────────────────────────────────
    /** How much the image is scaled down each pass (1.05 = finer, slower; 1.3 = coarser, faster). */
    private static final double SCALE_FACTOR   = 1.15;
    /** Minimum number of overlapping neighbour rectangles a candidate must have to be kept.
     *  Higher = fewer false positives. 5-7 is a safe sweet-spot for webcam distances. */
    private static final int    MIN_NEIGHBOURS  = 6;
    /** Minimum face size in pixels. Ignores tiny blobs far from camera (< 10 % of frame width). */
    private static final int    MIN_FACE_PX     = 60;
    /** Maximum face size in pixels. Avoids matching extremely close-up objects (0 = unlimited). */
    private static final int    MAX_FACE_PX     = 0;

    // ── Temporal vote buffer ──────────────────────────────────────────────────
    /** How many recent frame predictions to consider for the majority-vote smoothing. */
    private static final int VOTE_WINDOW = 7;
    /** The prediction must appear in at least this fraction of the window to be confirmed. */
    private static final double VOTE_QUORUM = 0.6;

    // ── NMS (non-maximum suppression) ─────────────────────────────────────────
    /** Two detection boxes are considered the SAME face when their IoU exceeds this. */
    private static final double NMS_IOU_THRESHOLD = 0.35;

    private final Object modelLock = new Object();
    private final DataPaths paths;
    private final OpenCvConfiguration openCv;
    private final PresenceProperties properties;
    private final FaceSampleRepository samples;
    private final PersonRepository people;
    private CascadeClassifier detector;
    private LBPHFaceRecognizer recognizer;
    private Map<Integer, Person> labels = Map.of();
    private volatile String status = "Face recognition is waiting for registrations.";

    /**
     * Per-region temporal vote buffer.
     * Key: a coarse grid cell string derived from the face centre (e.g. "2x1"),
     * Value: a sliding window of the last VOTE_WINDOW raw label IDs (−1 = unknown).
     */
    private final ConcurrentHashMap<String, Deque<Integer>> voteBufs = new ConcurrentHashMap<>();
    /** Tracks when each grid cell was last seen (ms) so stale entries can be evicted. */
    private final ConcurrentHashMap<String, Long> voteTimestamps = new ConcurrentHashMap<>();
    private static final long VOTE_TTL_MS = 2_000;

    public FaceRecognitionService(DataPaths paths, OpenCvConfiguration openCv, PresenceProperties properties,
                                  FaceSampleRepository samples, PersonRepository people) {
        this.paths = paths;
        this.openCv = openCv;
        this.properties = properties;
        this.samples = samples;
        this.people = people;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Initialisation
    // ─────────────────────────────────────────────────────────────────────────

    /** Checks whether the bundled Haar Cascade is usable, loading it outside the JAR when necessary. */
    public void initializeDetector() {
        synchronized (modelLock) {
            if (!openCv.isAvailable() || detector != null) return;
            try {
                Path cascade = paths.models().resolve("haarcascade_frontalface_default.xml");
                if (Files.notExists(cascade)) {
                    try (InputStream source = new ClassPathResource("models/haarcascade_frontalface_default.xml").getInputStream()) {
                        Files.copy(source, cascade, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                detector = new CascadeClassifier(cascade.toString());
                if (detector.empty()) {
                    detector.close();
                    detector = null;
                    status = "The bundled Haar Cascade could not be loaded.";
                }
            } catch (IOException | RuntimeException exception) {
                status = "The Haar Cascade model is unavailable; face recognition is disabled.";
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public Mat validateAndExtractLargestFace(byte[] imageBytes) {
        if (!openCv.isAvailable()) throw new FaceProcessingException(openCv.failureMessage());
        initializeDetector();
        if (detector == null) throw new FaceProcessingException(status);
        if (imageBytes == null || imageBytes.length == 0) throw new FaceProcessingException("The uploaded image is empty.");
        try (BytePointer input = new BytePointer(imageBytes);
             Mat encoded = new Mat(1, imageBytes.length, CV_8UC1, input);
             Mat image = imdecode(encoded, IMREAD_COLOR)) {
            if (image.empty()) throw new FaceProcessingException("The file is not a readable image.");
            return extractLargestFace(image);
        } catch (FaceProcessingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new FaceProcessingException("The file could not be processed as a face photograph.");
        }
    }

    public void rebuildModel() {
        synchronized (modelLock) {
            if (!openCv.isAvailable()) {
                status = openCv.failureMessage();
                return;
            }
            initializeDetector();
            if (detector == null) return;
            List<Mat> faces = new ArrayList<>();
            List<Integer> faceLabels = new ArrayList<>();
            Map<Integer, Person> loadedLabels = new HashMap<>();
            for (FaceSample sample : samples.findAll()) {
                Person person = people.findById(sample.personId()).orElse(null);
                Path source = resolveSample(sample.imagePath());
                if (person == null || source == null || Files.notExists(source)) continue;
                try (Mat image = imread(source.toString(), IMREAD_COLOR)) {
                    if (!image.empty()) {
                        faces.add(extractLargestFace(image));
                        faceLabels.add(Math.toIntExact(person.id()));
                        loadedLabels.put(Math.toIntExact(person.id()), person);
                    }
                } catch (RuntimeException ignored) {
                    // A damaged historic image is skipped; a later valid sample keeps the person trainable.
                }
            }
            if (recognizer != null) {
                recognizer.close();
                recognizer = null;
            }
            labels = Map.of();
            if (faces.isEmpty()) {
                status = "Face recognition is waiting for registrations.";
                return;
            }
            try (MatVector trainingImages = new MatVector(faces.size());
                 Mat trainingLabels = new Mat(faces.size(), 1, CV_32SC1)) {
                IntIndexer labelIndexer = trainingLabels.createIndexer();
                for (int index = 0; index < faces.size(); index++) {
                    trainingImages.put(index, faces.get(index));
                    labelIndexer.put(index, 0, faceLabels.get(index));
                }
                labelIndexer.release();
                // Tighter threshold: LBPH distance must be ≤ configured value AND ≤ 80.
                // Lower radius/neighbours = more detailed histogram (better for high-res crops).
                double threshold = Math.min(properties.getRecognitionThreshold(), 80.0);
                recognizer = LBPHFaceRecognizer.create(1, 8, 8, 8, threshold);
                recognizer.train(trainingImages, trainingLabels);
                recognizer.write(paths.models().resolve("lbph_model.yml").toString());
                labels = Map.copyOf(loadedLabels);
                voteBufs.clear();
                voteTimestamps.clear();
                status = "Face recognition is ready (" + faces.size() + " training photo" + (faces.size() == 1 ? "" : "s") + ").";
            } finally {
                for (Mat face : faces) face.close();
            }
        }
    }

    /**
     * Detects faces in a BGR frame and returns deduplicated bounding boxes.
     * Applies tuned Haar parameters + soft NMS to eliminate overlapping detections.
     */
    public List<Rect> detect(Mat bgrFrame) {
        synchronized (modelLock) {
            initializeDetector();
            if (detector == null) return List.of();
            try (Mat gray = new Mat(); RectVector raw = new RectVector()) {
                cvtColor(bgrFrame, gray, COLOR_BGR2GRAY);
                detector.detectMultiScale(
                        gray,
                        raw,
                        SCALE_FACTOR,
                        MIN_NEIGHBOURS,
                        0,                               // flags – must be 0 for LBP/Haar cascade
                        new Size(MIN_FACE_PX, MIN_FACE_PX),
                        new Size(MAX_FACE_PX, MAX_FACE_PX)
                );
                List<Rect> boxes = new ArrayList<>((int) raw.size());
                for (long i = 0; i < raw.size(); i++) boxes.add(new Rect(raw.get(i)));
                return nms(boxes);
            }
        }
    }

    /**
     * Predicts a registered person for the given face bounding box.
     * Uses a per-region sliding-window majority vote to stabilise flickering labels.
     */
    public Prediction predict(Mat bgrFrame, Rect faceBounds) {
        synchronized (modelLock) {
            if (recognizer == null) return Prediction.unknown();
            try (Mat gray = new Mat(); Mat face = new Mat()) {
                cvtColor(bgrFrame, gray, COLOR_BGR2GRAY);
                preprocess(new Mat(gray, faceBounds), face);
                try (IntPointer labelPtr = new IntPointer(1); DoublePointer distPtr = new DoublePointer(1)) {
                    recognizer.predict(face, labelPtr, distPtr);
                    int rawLabel = labelPtr.get(0);
                    double dist  = distPtr.get(0);
                    double threshold = Math.min(properties.getRecognitionThreshold(), 80.0);

                    // Raw prediction: only accept if distance is within threshold
                    int vote = (rawLabel >= 0 && dist <= threshold && labels.containsKey(rawLabel))
                            ? rawLabel : -1;

                    // Apply temporal vote smoothing
                    String cell = gridCell(faceBounds, bgrFrame.cols(), bgrFrame.rows());
                    int smoothedLabel = updateVote(cell, vote);

                    if (smoothedLabel < 0) return Prediction.unknown();
                    Person person = labels.get(smoothedLabel);
                    return person != null ? new Prediction(person, dist) : Prediction.unknown();
                }
            } catch (RuntimeException exception) {
                return Prediction.unknown();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Non-maximum suppression: removes boxes that overlap heavily with a larger box.
     * Keeps the largest box of each overlapping cluster.
     */
    private List<Rect> nms(List<Rect> boxes) {
        if (boxes.size() <= 1) return boxes;
        // Sort descending by area so we always keep the largest of a cluster.
        boxes.sort((a, b) -> Long.compare(area(b), area(a)));
        boolean[] suppressed = new boolean[boxes.size()];
        List<Rect> kept = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            if (suppressed[i]) continue;
            kept.add(boxes.get(i));
            for (int j = i + 1; j < boxes.size(); j++) {
                if (!suppressed[j] && iou(boxes.get(i), boxes.get(j)) > NMS_IOU_THRESHOLD) {
                    suppressed[j] = true;
                }
            }
        }
        return kept;
    }

    private static long area(Rect r) { return (long) r.width() * r.height(); }

    private static double iou(Rect a, Rect b) {
        int ix1 = Math.max(a.x(), b.x());
        int iy1 = Math.max(a.y(), b.y());
        int ix2 = Math.min(a.x() + a.width(),  b.x() + b.width());
        int iy2 = Math.min(a.y() + a.height(), b.y() + b.height());
        if (ix2 <= ix1 || iy2 <= iy1) return 0.0;
        double inter = (double)(ix2 - ix1) * (iy2 - iy1);
        double union = area(a) + area(b) - inter;
        return union <= 0 ? 0.0 : inter / union;
    }

    /**
     * Maps a face bounding box to a coarse grid cell string for vote-buffer keying.
     * Grid resolution: 8 cells wide × 6 cells tall (granular enough to separate faces,
     * coarse enough to tolerate minor jitter across frames).
     */
    private static String gridCell(Rect r, int frameW, int frameH) {
        int cx = r.x() + r.width()  / 2;
        int cy = r.y() + r.height() / 2;
        int gx = (int)((double) cx / Math.max(frameW, 1)  * 8);
        int gy = (int)((double) cy / Math.max(frameH, 1) * 6);
        return gx + "x" + gy;
    }

    /**
     * Slides a vote into the window for the given grid cell and returns the
     * majority label if it meets VOTE_QUORUM, otherwise −1 (unknown).
     * Evicts stale cells that haven't been seen for VOTE_TTL_MS.
     */
    private int updateVote(String cell, int rawLabel) {
        long now = System.currentTimeMillis();
        // Evict cells that haven't been active recently
        voteTimestamps.forEach((k, ts) -> {
            if (now - ts > VOTE_TTL_MS) {
                voteBufs.remove(k);
                voteTimestamps.remove(k);
            }
        });
        Deque<Integer> window = voteBufs.computeIfAbsent(cell, k -> new ArrayDeque<>(VOTE_WINDOW));
        voteTimestamps.put(cell, now);
        window.addLast(rawLabel);
        if (window.size() > VOTE_WINDOW) window.pollFirst();

        // Tally votes
        Map<Integer, Integer> tally = new HashMap<>();
        for (int v : window) tally.merge(v, 1, Integer::sum);

        int bestLabel = -1;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> e : tally.entrySet()) {
            if (e.getValue() > bestCount) { bestCount = e.getValue(); bestLabel = e.getKey(); }
        }
        double quorum = (double) bestCount / window.size();
        return (quorum >= VOTE_QUORUM && bestLabel >= 0) ? bestLabel : -1;
    }

    private Mat extractLargestFace(Mat colourImage) {
        try (Mat gray = new Mat(); RectVector found = new RectVector()) {
            cvtColor(colourImage, gray, COLOR_BGR2GRAY);
            detector.detectMultiScale(
                    gray,
                    found,
                    SCALE_FACTOR,
                    MIN_NEIGHBOURS,
                    0,
                    new Size(MIN_FACE_PX, MIN_FACE_PX),
                    new Size(MAX_FACE_PX, MAX_FACE_PX)
            );
            if (found.size() == 0) throw new FaceProcessingException("No front-facing face was found in this image.");
            Rect largest = found.get(0);
            long largestArea = (long) largest.width() * largest.height();
            for (long index = 1; index < found.size(); index++) {
                Rect candidate = found.get(index);
                long candidateArea = (long) candidate.width() * candidate.height();
                if (candidateArea > largestArea) { largest = candidate; largestArea = candidateArea; }
            }
            Mat processed = new Mat();
            preprocess(new Mat(gray, largest), processed);
            return processed;
        }
    }

    private void preprocess(Mat grayFace, Mat result) {
        try (Mat equalized = new Mat()) {
            equalizeHist(grayFace, equalized);
            resize(equalized, result, new Size(FACE_SIZE, FACE_SIZE), 0, 0, INTER_AREA);
        }
    }

    private Path resolveSample(String databasePath) {
        Path path = paths.knownFaces().resolve(databasePath).normalize();
        return path.startsWith(paths.knownFaces()) ? path : null;
    }

    public boolean ready() { return recognizer != null; }
    public String status() { return status; }

    @PreDestroy
    void release() {
        synchronized (modelLock) {
            if (recognizer != null) recognizer.close();
            if (detector != null) detector.close();
        }
    }

    public record Prediction(Person person, double distance) {
        static Prediction unknown() { return new Prediction(null, Double.POSITIVE_INFINITY); }
        public boolean known() { return person != null; }
    }

    public static class FaceProcessingException extends RuntimeException {
        public FaceProcessingException(String message) { super(message); }
    }
}
