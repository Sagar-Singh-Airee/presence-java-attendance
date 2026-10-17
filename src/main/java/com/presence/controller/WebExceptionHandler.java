/**
 * Purpose: Converts unexpected web errors into a friendly error page.
 * Why it is used: It prevents technical stack traces from being shown to normal users.
 */
package com.presence.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@ControllerAdvice
public class WebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    /**
     * Static resources that browsers request automatically (favicon.ico, robots.txt, etc.)
     * must return a plain 404 — not a full error page and not an ERROR log.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public void missingStaticResource(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }

    /**
     * Everything else that reaches here is a genuine application fault — log it and show
     * the user a friendly message instead of a raw stack trace.
     */
    @ExceptionHandler(Exception.class)
    public String friendlyError(Exception exception, Model model) {
        log.error("Unhandled web request error", exception);
        model.addAttribute("error", "Something went wrong, but your saved attendance data was not changed. Please try again.");
        return "error";
    }
}
