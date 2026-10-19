/**
 * Purpose: Handles the face-registration web page.
 * Why it is used: It receives a name and photos from the user, then asks the registration service to save them.
 */
package com.presence.controller;

import com.presence.service.RegistrationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class RegistrationController {
    private final RegistrationService registration;
    public RegistrationController(RegistrationService registration) { this.registration = registration; }

    @GetMapping("/register")
    public String form() { return "register"; }

    @PostMapping("/register")
    public String register(@RequestParam("name") String name, @RequestParam(name = "files", required = false) List<MultipartFile> files,
                           Model model, RedirectAttributes flash) {
        try {
            RegistrationService.RegistrationResult result = registration.register(name, files);
            String message = result.addedToExistingPerson()
                    ? result.photographsAdded() + " photograph(s) added to " + result.person().name() + "."
                    : result.person().name() + " was registered with " + result.photographsAdded() + " photograph(s).";
            flash.addFlashAttribute("success", message);
            return "redirect:/faces";
        } catch (RegistrationService.RegistrationException | com.presence.service.FileStorageService.StorageException exception) {
            model.addAttribute("error", exception.getMessage());
            model.addAttribute("submittedName", name);
            return "register";
        }
    }
}
