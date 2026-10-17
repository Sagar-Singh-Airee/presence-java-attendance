/**
 * Purpose: Serves the dashboard page and camera-image endpoints.
 * Why it is used: It sends the live camera view and summary numbers to the browser.
 */
package com.presence.controller;

// PersonRepository: Accesses SQLite to count how many people are registered.
import com.presence.repository.PersonRepository;
// AttendanceService: Accesses business logic to count today's attendance.
import com.presence.service.AttendanceService;
// CameraService: Controls the physical webcam and delivers captured frames.
import com.presence.service.CameraService;
// CacheControl: Adds HTTP headers instructing the browser not to cache live camera frames.
import org.springframework.http.CacheControl;
// HttpStatus: Provides standard HTTP response codes like 200 OK or 503 Service Unavailable.
import org.springframework.http.HttpStatus;
// MediaType: Specifies the data type of the response, such as image/jpeg.
import org.springframework.http.MediaType;
// ResponseEntity: Allows setting HTTP status code, headers, and body together.
import org.springframework.http.ResponseEntity;
// Controller: Marks this class as a Spring MVC controller handling web requests.
import org.springframework.stereotype.Controller;
// Model: Passes data from Java to the Thymeleaf HTML template.
import org.springframework.ui.Model;
// GetMapping: Routes incoming HTTP GET requests to specific Java methods.
import org.springframework.web.bind.annotation.GetMapping;
// ResponseBody: Indicates the return value should be sent directly as HTTP response body instead of a view name.
import org.springframework.web.bind.annotation.ResponseBody;
// StreamingResponseBody: Enables asynchronous streaming of continuous MJPEG frames to the browser.
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

// HttpServletResponse: Gives direct access to HTTP response headers for cache prevention.
import jakarta.servlet.http.HttpServletResponse;
// IOException: Handles potential input/output disconnects when a user closes the browser tab.
import java.io.IOException;
// Objects: Provides null-safety utility methods like requireNonNull.
import java.util.Objects;

@Controller
public class DashboardController {
    private final CameraService camera;
    private final AttendanceService attendance;
    private final PersonRepository people;

    public DashboardController(CameraService camera, AttendanceService attendance, PersonRepository people) {
        this.camera = camera;
        this.attendance = attendance;
        this.people = people;
    }

    /**
     * Loads the main dashboard page at http://127.0.0.1:8081/
     * Supplies today's attendance count, total registered people count, and camera status message.
     */
    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("todayCount", attendance.countToday());
        model.addAttribute("peopleCount", people.count());
        model.addAttribute("cameraStatus", camera.status());
        return "index";
    }

    /**
     * Continuously streams live video frames using the standard MJPEG format.
     * Each frame is delimited by an HTTP multipart boundary with Content-Type and Content-Length.
     * The CRLF (\r\n) characters ensure web browsers correctly detect where each image starts and ends.
     */
    @GetMapping(value = "/video-feed", produces = "multipart/x-mixed-replace;boundary=frame")
    @ResponseBody
    public StreamingResponseBody videoFeed(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        return output -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    byte[] jpeg = camera.nextJpegFrame();
                    if (jpeg != null) {
                        // Writes standard multipart MIME boundary headers separated by \r\n
                        output.write("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ".getBytes());
                        output.write(Integer.toString(jpeg.length).getBytes());
                        output.write("\r\n\r\n".getBytes());
                        output.write(jpeg);
                        output.write("\r\n".getBytes());
                        output.flush();
                    }
                    Thread.sleep(jpeg == null ? 500 : 100);
                }
            } catch (IOException ignored) {
                // Browsers routinely close an MJPEG request while navigating away.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };
    }

    /**
     * Returns a single JPEG frame for browsers that poll the camera feed instead of holding open an MJPEG stream.
     * Polling this endpoint is significantly more reliable across modern Windows browsers.
     * Objects.requireNonNull ensures MediaType.IMAGE_JPEG conforms to Spring's non-null parameter rules.
     */
    @GetMapping(value = "/video-frame", produces = MediaType.IMAGE_JPEG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> videoFrame() {
        byte[] jpeg = camera.nextJpegFrame();
        if (jpeg == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(Objects.requireNonNull(MediaType.IMAGE_JPEG))
                .cacheControl(CacheControl.noStore())
                .body(jpeg);
    }
}
