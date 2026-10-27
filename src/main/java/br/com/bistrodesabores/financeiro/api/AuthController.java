package br.com.bistrodesabores.financeiro.api;

import java.time.Duration;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String SESSION_COOKIE = "bistro_session";
    private static final Duration SESSION_DURATION = Duration.ofHours(8);

    private final AuthService authService;
    private final boolean secureCookie;
    private final String cookieSameSite;

    public AuthController(
            AuthService authService,
            @org.springframework.beans.factory.annotation.Value("${app.auth-cookie-secure:false}") boolean secureCookie,
            @org.springframework.beans.factory.annotation.Value("${app.auth-cookie-same-site:Strict}") String cookieSameSite) {
        this.authService = authService;
        this.secureCookie = secureCookie;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody RegistrationRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        AuthService.AuthenticatedUser user = authService.register(request.name(), request.username(), request.password());
        String token = authService.createSession(user.id());
        response.addHeader("Set-Cookie", sessionCookie(token, SESSION_DURATION).toString());
        return noStore(Map.of("user", user.profile()));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        AuthService.AuthenticatedUser user = authService.login(request.username(), request.password());
        String token = authService.createSession(user.id());
        response.addHeader("Set-Cookie", sessionCookie(token, SESSION_DURATION).toString());
        return noStore(Map.of("user", user.profile()));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> currentUser(HttpServletRequest request) {
        AuthService.AuthenticatedUser user = (AuthService.AuthenticatedUser) request.getAttribute(AuthSessionFilter.USER_ATTRIBUTE);
        return noStore(Map.of("user", user.profile()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Boolean>> logout(
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        String token = AuthSessionFilter.sessionToken(request);
        if (token != null) {
            authService.deleteSession(token);
        }
        response.addHeader("Set-Cookie", sessionCookie("", Duration.ZERO).toString());
        return noStore(Map.of("loggedOut", true));
    }

    private ResponseCookie sessionCookie(String token, Duration maxAge) {
        return ResponseCookie.from(SESSION_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(cookieSameSite)
                .path("/api")
                .maxAge(maxAge)
                .build();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    public record RegistrationRequest(String name, String username, String password) {}

    public record LoginRequest(String username, String password) {}
}
