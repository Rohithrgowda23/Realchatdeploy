package com.realchat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Operational endpoints. /actuator/health shows only UP/DOWN to everyone (for load balancers); everything else
 * (details, metrics) needs HTTP Basic credentials from OPS_USER / OPS_PASSWORD. If those are not set, nobody can log in.
 */
@Configuration
public class OpsSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain actuatorChain(HttpSecurity http, PasswordEncoder encoder,
                                      @Value("${app.ops.user:}") String user, @Value("${app.ops.password:}") String password) throws Exception {
        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager();
        if (!user.isBlank() && !password.isBlank()) {
            UserDetails ops = org.springframework.security.core.userdetails.User.withUsername(user).password(encoder.encode(password)).roles("OPS").build();
            users.createUser(ops);
        }
        http.securityMatcher("/actuator/**")
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .userDetailsService(users)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().hasRole("OPS"))
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}