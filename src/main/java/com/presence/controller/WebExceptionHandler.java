/**
 * Purpose: Converts unexpected web errors into a friendly error page.
 * Why it is used: It prevents technical stack traces from being shown to normal users.
 */
package com.presence.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class WebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);
    @ExceptionHandler(Exception.class)
    public String friendlyError(Exception exception, Model model) {
        log.error("Unhandled web request error", exception);
        model.addAttribute("error", "Something went wrong, but your saved attendance data was not changed. Please try again.");
        return "error";
    }
}
