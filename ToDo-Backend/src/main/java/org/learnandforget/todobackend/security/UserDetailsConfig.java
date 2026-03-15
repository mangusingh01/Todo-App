package org.learnandforget.todobackend.security;

import lombok.RequiredArgsConstructor;
import org.learnandforget.todobackend.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Separated from SecurityConfig deliberately to break the circular dependency:
 *
 * SecurityConfig -> JwtAuthFilter -> UserDetailsService -> SecurityConfig (cycle!)
 *
 * By moving UserDetailsService into its own @Configuration class,
 * Spring can resolve it independently before wiring SecurityConfig.
 */
@Configuration
@RequiredArgsConstructor
public class UserDetailsConfig {

    private final UserRepository userRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + username));
    }
}