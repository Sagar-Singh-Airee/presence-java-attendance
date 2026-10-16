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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public FaceRecognitionService(DataPaths paths, OpenCvConfiguration openCv, PresenceProperties properties,
                                  FaceSampleRepository samples, PersonRepository people) {
        this.paths = paths;
        this.openCv = openCv;
        this.properties = properties;
        this.samples = samples;
        this.people = people;
    }

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
                recognizer = LBPHFaceRecognizer.create(1, 8, 8, 8, properties.getRecognitionThreshold());
                recognizer.train(trainingImages, trainingLabels);
                recognizer.write(paths.models().resolve("lbph_model.yml").toString());
                labels = Map.copyOf(loadedLabels);
                status = "Face recognition is ready (" + faces.size() + " training photo" + (faces.size() == 1 ? "" : "s") + ").";
            } finally {
                for (Mat face : faces) face.close();
            }
        }
    }

    /** Predicts a registered person when the LBPH distance is at or below the configured threshold. */
    public Prediction predict(Mat bgrFrame, Rect faceBounds) {
        synchronized (modelLock) {
            if (recognizer == null) return Prediction.unknown();
            try (Mat gray = new Mat(); Mat face = new Mat()) {
                cvtColor(bgrFrame, gray, COLOR_BGR2GRAY);
                preprocess(new Mat(gray, faceBounds), face);
                try (IntPointer label = new IntPointer(1); DoublePointer distance = new DoublePointer(1)) {
                    recognizer.predict(face, label, distance);
                    Person person = labels.get(label.get(0));
                    if (person == null || label.get(0) < 0 || distance.get(0) > properties.getRecognitionThreshold()) {
                        return Prediction.unknown();
                    }
                    return new Prediction(person, distance.get(0));
                }
            } catch (RuntimeException exception) {
                return Prediction.unknown();
            }
        }
    }

    public List<Rect> detect(Mat bgrFrame) {
        synchronized (modelLock) {
            initializeDetector();
            if (detector == null) return List.of();
            try (Mat gray = new Mat(); RectVector result = new RectVector()) {
                cvtColor(bgrFrame, gray, COLOR_BGR2GRAY);
                detector.detectMultiScale(gray, result);
                List<Rect> faces = new ArrayList<>((int) result.size());
                for (long index = 0; index < result.size(); index++) faces.add(new Rect(result.get(index)));
                return faces;
            }
        }
    }

    private Mat extractLargestFace(Mat colourImage) {
        try (Mat gray = new Mat(); RectVector found = new RectVector()) {
            cvtColor(colourImage, gray, COLOR_BGR2GRAY);
            detector.detectMultiScale(gray, found);
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
