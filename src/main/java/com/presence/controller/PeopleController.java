/**
 * Purpose: Shows registered people and handles deletion requests.
 * Why it is used: It gives the user a simple page for managing the people available for recognition.
 */
package com.presence.controller;

import com.presence.repository.PersonRepository;
import com.presence.service.PeopleService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PeopleController {
    private final PersonRepository people;
    private final PeopleService peopleService;
    public PeopleController(PersonRepository people, PeopleService peopleService) {
        this.people = people; this.peopleService = peopleService;
    }

    @GetMapping("/faces")
    public String faces(Model model) {
        model.addAttribute("people", people.findAllWithPreview());
        return "faces";
    }

    @PostMapping("/faces/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes flash) {
        try {
            String name = peopleService.delete(id).name();
            flash.addFlashAttribute("success", name + " was removed. Historic attendance was retained.");
        } catch (PeopleService.PersonNotFoundException | com.presence.service.FileStorageService.StorageException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/faces";
    }
}
