package com.saishekar.jobtracker.auth;

import com.saishekar.jobtracker.auth.AuthDtos.AuthResponse;
import com.saishekar.jobtracker.auth.AuthDtos.LoginRequest;
import com.saishekar.jobtracker.auth.AuthDtos.RegisterRequest;
import com.saishekar.jobtracker.common.ApiException;
import com.saishekar.jobtracker.model.User;
import com.saishekar.jobtracker.repository.UserRepository;
import com.saishekar.jobtracker.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwt) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = users.save(new User(email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim()));
        return respond(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));
        User user = users.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("User"));
        return respond(user);
    }

    private AuthResponse respond(User user) {
        return new AuthResponse(jwt.generateToken(user.getEmail()), user.getEmail(), user.getDisplayName());
    }
}
