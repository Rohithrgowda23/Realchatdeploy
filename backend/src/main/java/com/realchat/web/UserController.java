package com.realchat.web;

import com.realchat.domain.User;
import com.realchat.dto.Dto.*;
import com.realchat.exception.ApiException;
import com.realchat.repository.UserRepository;
import com.realchat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {
    private final UserRepository users;
    private final ChatService chat;

    @GetMapping("/users/me")
    public UserDto me(@AuthenticationPrincipal Long me) {
        return UserDto.of(users.findById(me).orElseThrow(() -> ApiException.notFound("User not found")), true);
    }

    @PutMapping("/users/me")
    public UserDto update(@AuthenticationPrincipal Long me, @Valid @RequestBody ProfileReq req) {
        User u = users.findById(me).orElseThrow(() -> ApiException.notFound("User not found"));
        if (req.name() != null) {
            if (req.name().isBlank()) throw ApiException.bad("Name cannot be blank");
            u.setName(req.name().strip());
        }
        if (req.about() != null) u.setAbout(req.about().isBlank() ? null : req.about().strip());
        if (req.avatarUrl() != null) u.setAvatarUrl(req.avatarUrl().isBlank() ? null : req.avatarUrl().strip());
        return UserDto.of(users.save(u), true);
    }

    @GetMapping("/users/search")
    public List<UserDto> search(@AuthenticationPrincipal Long me, @RequestParam String q) { return chat.searchUsers(me, q); }

    @GetMapping("/users/blocked")
    public List<UserDto> blocked(@AuthenticationPrincipal Long me) { return chat.blockedUsers(me); }

    @PostMapping("/users/{id}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@AuthenticationPrincipal Long me, @PathVariable Long id) { chat.block(me, id); }

    @DeleteMapping("/users/{id}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@AuthenticationPrincipal Long me, @PathVariable Long id) { chat.unblock(me, id); }

    @GetMapping("/presence")
    public List<Long> presence() { return chat.onlineIds(); }
}
