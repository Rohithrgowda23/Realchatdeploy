package com.realchat.web;

import com.realchat.domain.User;
import com.realchat.dto.Dto.*;
import com.realchat.exception.ApiException;
import com.realchat.repository.UserRepository;
import com.realchat.security.JwtService;
import com.realchat.service.RateLimitService;
import com.realchat.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final SessionService sessions;
    private final RateLimitService limiter;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthRes register(@Valid @RequestBody RegisterReq req, HttpServletRequest http) {
        limiter.register(http.getRemoteAddr());
        String email = req.email().strip().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("An account with this email already exists");
        User u = new User();
        u.setName(req.name().strip());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u = users.save(u);
        return issue(u, http);
    }

    @PostMapping("/login")
    public AuthRes login(@Valid @RequestBody LoginReq req, HttpServletRequest http) {
        limiter.login(http.getRemoteAddr(), req.email().strip().toLowerCase());
        User u = users.findByEmailIgnoreCase(req.email().strip())
                .filter(x -> x.getPasswordHash() != null && encoder.matches(req.password(), x.getPasswordHash())) // OAuth-only accounts have no password
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return issue(u, http);
    }

    private AuthRes issue(User u, HttpServletRequest http) {
        // getRemoteAddr(), not X-Forwarded-For: client-supplied headers must not be trusted for identity
        String sid = sessions.create(u.getId(), http.getHeader("User-Agent"), http.getRemoteAddr(), jwt.getExpirationMinutes());
        return new AuthRes(jwt.generate(u.getId(), u.getName(), sid), UserDto.of(u, true));
    }
}