package br.com.bistrodesabores.financeiro.api;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthSessionFilter extends OncePerRequestFilter {
    public static final String USER_ATTRIBUTE = AuthSessionFilter.class.getName() + ".user";
    private static final String SESSION_COOKIE = "bistro_session";
    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final String frontendOrigin;

    public AuthSessionFilter(
            AuthService authService,
            ObjectMapper objectMapper,
            @Value("${app.frontend-origin:http://localhost:5173}") String frontendOrigin) {
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.frontendOrigin = frontendOrigin;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getServletPath();
        return !("/api/data".equals(path)
                || "/api/auth/me".equals(path)
                || "/api/auth/logout".equals(path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null
                && !isSafeMethod(request.getMethod())
                && !frontendOrigin.equals(origin)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origem da solicitação não permitida.");
            return;
        }

        String token = sessionToken(request);
        AuthService.AuthenticatedUser user = authService.findUserForSession(token);
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-store");
            objectMapper.writeValue(response.getOutputStream(), Map.of("error", "Faça login para acessar os dados financeiros."));
            return;
        }
        request.setAttribute(USER_ATTRIBUTE, user);
        filterChain.doFilter(request, response);
    }

    private static boolean isSafeMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }

    static String sessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
