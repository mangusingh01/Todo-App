package org.learnandforget.todobackend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.learnandforget.todobackend.dto.AuthDto;
import org.learnandforget.todobackend.exception.DuplicateResourceException;
import org.learnandforget.todobackend.model.User;
import org.learnandforget.todobackend.repository.UserRepository;
import org.learnandforget.todobackend.security.JwtService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    // ── Register ──────────────────────────────────────────────────

    @Test
    @DisplayName("register — returns token on success")
    void register_returnsToken_onSuccess() {
        AuthDto.RegisterRequest request =
                new AuthDto.RegisterRequest("mangu", "mangu@test.com", "password123");

        when(userRepository.existsByUsername("mangu")).thenReturn(false);
        when(userRepository.existsByEmail("mangu@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any())).thenReturn("jwt-token");

        AuthDto.AuthResponse response = authService.register(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUsername()).isEqualTo("mangu");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register — throws DuplicateResourceException on duplicate username")
    void register_throwsDuplicate_onDuplicateUsername() {
        AuthDto.RegisterRequest request =
                new AuthDto.RegisterRequest("mangu", "other@test.com", "password123");

        when(userRepository.existsByUsername("mangu")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("mangu");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register — throws DuplicateResourceException on duplicate email")
    void register_throwsDuplicate_onDuplicateEmail() {
        AuthDto.RegisterRequest request =
                new AuthDto.RegisterRequest("newuser", "mangu@test.com", "password123");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("mangu@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("mangu@test.com");
    }

    // ── Login ─────────────────────────────────────────────────────

    @Test
    @DisplayName("login — returns token on valid credentials")
    void login_returnsToken_onValidCredentials() {
        AuthDto.LoginRequest request =
                new AuthDto.LoginRequest("mangu", "password123");

        User user = User.builder()
                .id(1L).username("mangu").email("mangu@test.com").password("encoded")
                .build();

        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByUsername("mangu")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthDto.AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUsername()).isEqualTo("mangu");
    }

    @Test
    @DisplayName("login — throws BadCredentialsException on wrong password")
    void login_throwsBadCredentials_onWrongPassword() {
        AuthDto.LoginRequest request =
                new AuthDto.LoginRequest("mangu", "wrongpassword");

        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByUsername(any());
    }
}