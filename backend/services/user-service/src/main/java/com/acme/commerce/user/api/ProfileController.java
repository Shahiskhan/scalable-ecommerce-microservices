package com.acme.commerce.user.api;

import com.acme.commerce.user.application.ProfileService;
import com.acme.commerce.user.domain.Profile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class ProfileController {
    private final ProfileService service;
    public ProfileController(ProfileService service) { this.service = service; }
    @GetMapping public Profile get(@RequestAttribute String userId) { return service.get(userId); }
    @PutMapping public Profile put(@RequestAttribute String userId, @Valid @RequestBody UpdateProfile body) {
        return service.put(userId, body.displayName(), body.expectedVersion());
    }
    public record UpdateProfile(@NotBlank @Size(max=100) String displayName, @Min(0) long expectedVersion) { }
}
