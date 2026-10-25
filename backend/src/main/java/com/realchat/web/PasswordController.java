package com.realchat.web;

import com.realchat.domain.User;
import com.realchat.exception.ApiException;
import com.realchat.repository.UserRepository;
import com.realchat.service.PasswordResetService;
import com.realchat.service.RateLimitService;
import com.realchat.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PasswordController {
    public record ForgotReq(@NotBlank @Email @Size(max = 190) String email) {}
    public record TokenReq(@NotBlank @Size(max = 200) String token) {}
    public record ResetReq(@NotBlank @Size(max = 200) String token,
                           @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String newPassword) {}
    public record ChangeReq(@Size(max = 72) String currentPassword,
                            @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String newPassword) {}

    private final PasswordResetService reset;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SessionService sessions;
    private final RateLimitService limiter;

    /** Always the same answer, whether or not the email has an account. */
    @PostMapping("/api/auth/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> forgot(@Valid @RequestBody ForgotReq r, HttpServletRequest http) {
        reset.request(r.email(), http.getRemoteAddr());
        return Map.of("message", "If an account exists for that email, a reset link is on its way.");
    }

    @PostMapping("/api/auth/reset-password/validate")
    public Map<String, Boolean> validate(@Valid @RequestBody TokenReq r, HttpServletRequest http) {
        return Map.of("valid", reset.isValid(r.token(), http.getRemoteAddr()));
    }

    @PostMapping("/api/auth/reset-password")
    public Map<String, String> doReset(@Valid @RequestBody ResetReq r, HttpServletRequest http) {
        reset.reset(r.token(), r.newPassword(), http.getRemoteAddr());
        return Map.of("message", "Password updated. Please sign in.");
    }

    /** Change the password, or set one for a Google-only account. Other devices are signed out afterwards. */
    @PutMapping("/api/users/me/password")
    public Map<String, String> change(Authentication a, @Valid @RequestBody ChangeReq r) {
        Long me = (Long) a.getPrincipal();
        limiter.passwordChange(me);
        User u = users.findById(me).orElseThrow(() -> ApiException.notFound("User not found"));
        if (u.getPasswordHash() != null && (r.currentPassword() == null || !encoder.matches(r.currentPassword(), u.getPasswordHash())))
            throw ApiException.forbidden("Current password is incorrect");
        u.setPasswordHash(encoder.encode(r.newPassword()));
        users.save(u);
        sessions.revokeOthers(me, a.getDetails() instanceof String s ? s : "");
        return Map.of("message", "Password updated. Other devices were signed out.");
    }
}