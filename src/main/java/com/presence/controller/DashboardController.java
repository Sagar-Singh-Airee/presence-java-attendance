/**
 * Purpose: Serves the dashboard page and camera-image endpoints.
 * Why it is used: It sends the live camera view and summary numbers to the browser.
 */
package com.presence.controller;

import com.presence.repository.PersonRepository;
import com.presence.service.AttendanceService;
import com.presence.service.CameraService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

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

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("todayCount", attendance.countToday());
        model.addAttribute("peopleCount", people.count());
        model.addAttribute("cameraStatus", camera.status());
        return "index";
    }

    @GetMapping(value = "/video-feed", produces = "multipart/x-mixed-replace;boundary=frame")
    @ResponseBody
    public StreamingResponseBody videoFeed(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        return output -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    byte[] jpeg = camera.nextJpegFrame();
                    if (jpeg != null) {
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
     * A single JPEG for browsers that do not keep a multipart MJPEG connection alive.
     * The dashboard polls this endpoint; it is more reliable on Windows browsers.
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
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(jpeg);
    }
}
