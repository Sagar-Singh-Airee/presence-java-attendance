/**
 * Purpose: Converts unexpected web errors into a friendly error page.
 * Why it is used: It prevents technical stack traces from being shown to normal users.
 */
package com.presence.controller;

// Logger: Used for recording log messages to the console and terminal.
import org.slf4j.Logger;
// LoggerFactory: Factory that initializes and configures the logger instance for this class.
import org.slf4j.LoggerFactory;
// HttpStatus: Provides standard HTTP status codes, such as 404 NOT_FOUND.
import org.springframework.http.HttpStatus;
// Model: Transmits error messages to the Thymeleaf error.html template.
import org.springframework.ui.Model;
// ControllerAdvice: Tells Spring Boot to apply this exception interceptor across all controllers globally.
import org.springframework.web.bind.annotation.ControllerAdvice;
// ExceptionHandler: Flags a method to handle specific Java exception types when thrown during requests.
import org.springframework.web.servlet.resource.NoResourceFoundException;
// NoResourceFoundException: Specific Spring Boot 3 exception thrown when a requested static file (like favicon.ico) does not exist.
import org.springframework.web.bind.annotation.ExceptionHandler;

// HttpServletResponse: Allows setting low-level HTTP status codes and headers directly.
import jakarta.servlet.http.HttpServletResponse;
// IOException: Required because HttpServletResponse.sendError may throw an I/O exception.
import java.io.IOException;

@ControllerAdvice
public class WebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    /**
     * Handles missing static files requested automatically by browsers (such as favicon.ico or sourcemaps).
     * Instead of logging an alarming ERROR stack trace in the terminal or redirecting to an error page,
     * this cleanly returns a silent HTTP 404 status code, matching standard web server behavior.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public void missingStaticResource(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }

    /**
     * Catches any unexpected internal application exceptions.
     * Logs the full exception details to the terminal so developers can diagnose the issue,
     * while showing a calm, friendly message to the user on error.html without leaking sensitive system data.
     */
    @ExceptionHandler(Exception.class)
    public String friendlyError(Exception exception, Model model) {
        log.error("Unhandled web request error", exception);
        model.addAttribute("error", "Something went wrong, but your saved attendance data was not changed. Please try again.");
        return "error";
    }
}
