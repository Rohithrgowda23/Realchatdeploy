package com.realchat.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final com.realchat.service.SessionService sessions;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            // A valid signature is not enough: the server-side session must still be active (not revoked/expired).
            jwt.parse(h.substring(7)).filter(t -> sessions.validate(t.sid(), t.userId())).ifPresent(t -> {
                UsernamePasswordAuthenticationToken a = new UsernamePasswordAuthenticationToken(t.userId(), null, List.of());
                a.setDetails(t.sid());
                SecurityContextHolder.getContext().setAuthentication(a);
            });
        }
        chain.doFilter(req, res);
    }
}
