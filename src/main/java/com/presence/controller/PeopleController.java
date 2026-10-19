/**
 * Purpose: Shows registered people and handles deletion requests.
 * Why it is used: It gives the user a simple page for managing the people available for recognition.
 */
package com.presence.controller;

// PersonRepository: Provides the list of all registered people with their preview photos.
import com.presence.repository.PersonRepository;
// PeopleService: Contains the business logic for deleting a person and cleaning up their data.
import com.presence.service.PeopleService;
// Controller: Marks this class as a Spring MVC web controller handling browser requests.
import org.springframework.stereotype.Controller;
// Model: Passes data from this controller to the Thymeleaf HTML template for rendering.
import org.springframework.ui.Model;
// GetMapping: Maps HTTP GET requests to a specific handler method.
import org.springframework.web.bind.annotation.GetMapping;
// PathVariable: Extracts the {id} segment from the URL and passes it as a method parameter.
import org.springframework.web.bind.annotation.PathVariable;
// PostMapping: Maps HTTP POST requests (like form submissions) to a specific handler method.
import org.springframework.web.bind.annotation.PostMapping;
// RedirectAttributes: Stores temporary "flash" messages that survive a redirect and appear once on the next page.
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PeopleController {
    private final PersonRepository people;
    private final PeopleService peopleService;

    public PeopleController(PersonRepository people, PeopleService peopleService) {
        this.people = people;
        this.peopleService = peopleService;
    }

    /**
     * Displays the "Registered people" page with everyone available for face recognition.
     */
    @GetMapping("/faces")
    public String faces(Model model) {
        model.addAttribute("people", people.findAllWithPreview());
        return "faces";
    }

    /**
     * Handles the "Remove person" button click.
     * Deletes the person's database records and disk files, then redirects back to the faces page.
     * Shows a success message with an optional warning if file cleanup encountered a non-fatal issue.
     */
    @PostMapping("/faces/{id}/delete")
    public String delete(@PathVariable("id") long id, RedirectAttributes flash) {
        try {
            PeopleService.DeleteResult result = peopleService.delete(id);
            String name = result.person().name();

            if (result.hasWarning()) {
                // Database deletion succeeded but file cleanup or model retraining had a non-fatal issue.
                flash.addFlashAttribute("success", name + " was removed. Historic attendance was retained.");
                flash.addFlashAttribute("warning", result.warning());
            } else {
                flash.addFlashAttribute("success", name + " was removed. Historic attendance was retained.");
            }
        } catch (PeopleService.PersonNotFoundException exception) {
            // The person was already deleted or never existed — show a clean error message.
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/faces";
    }
}
