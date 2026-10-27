package br.com.bistrodesabores.financeiro.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]{3,32}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);

    private final JdbcTemplate jdbcTemplate;

    public AuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public AuthenticatedUser register(String nameInput, String usernameInput, String password) {
        String name = nameInput == null ? "" : nameInput.trim();
        String username = normalizeUsername(usernameInput);
        validateCredentials(name, password);

        try {
            long userId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO bistro_users (name, username, password_hash)
                    VALUES (?, ?, ?)
                    RETURNING id
                    """,
                    Long.class,
                    name,
                    username,
                    PASSWORD_ENCODER.encode(password));
            return new AuthenticatedUser(userId, name, username);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esse usuário já está cadastrado. Escolha outro nome de usuário.");
        }
    }

    public AuthenticatedUser login(String usernameInput, String password) {
        String username = normalizeUsername(usernameInput);
        if (password == null || password.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a senha.");
        }

        LoginAccount account;
        try {
            account = jdbcTemplate.queryForObject(
                    """
                    SELECT id, name, username, password_hash
                    FROM bistro_users
                    WHERE username = ?
                    """,
                    (resultSet, rowNum) -> new LoginAccount(
                            new AuthenticatedUser(
                                    resultSet.getLong("id"),
                                    resultSet.getString("name"),
                                    resultSet.getString("username")),
                            resultSet.getString("password_hash")),
                    username);
        } catch (EmptyResultDataAccessException exception) {
            throw invalidLogin();
        }

        if (!PASSWORD_ENCODER.matches(password, account.passwordHash())) {
            throw invalidLogin();
        }
        return account.user();
    }

    @Transactional
    public String createSession(long userId) {
        jdbcTemplate.update("DELETE FROM bistro_auth_sessions WHERE expires_at <= now()");
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        jdbcTemplate.update(
                "INSERT INTO bistro_auth_sessions (token_hash, user_id, expires_at) VALUES (?, ?, now() + interval '8 hours')",
                hashToken(token),
                userId);
        return token;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser findUserForSession(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT u.id, u.name, u.username
                    FROM bistro_auth_sessions s
                    JOIN bistro_users u ON u.id = s.user_id
                    WHERE s.token_hash = ? AND s.expires_at > now()
                    """,
                    (resultSet, rowNum) -> new AuthenticatedUser(
                            resultSet.getLong("id"),
                            resultSet.getString("name"),
                            resultSet.getString("username")),
                    hashToken(token));
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    @Transactional
    public void deleteSession(String token) {
        jdbcTemplate.update("DELETE FROM bistro_auth_sessions WHERE token_hash = ?", hashToken(token));
    }

    private static void validateCredentials(String name, String password) {
        if (name.length() < 2 || name.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um nome com 2 a 120 caracteres.");
        }
        if (password == null || !password.matches("\\d{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha deve conter exatamente 6 números.");
        }
    }

    private static String normalizeUsername(String input) {
        String username = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O usuário deve ter de 3 a 32 caracteres: letras sem acento, números, ponto, hífen ou sublinhado.");
        }
        return username;
    }

    private static ResponseStatusException invalidLogin() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário ou senha incorretos.");
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível nesta instalação Java.", exception);
        }
    }

    private record LoginAccount(AuthenticatedUser user, String passwordHash) {}

    public record AuthenticatedUser(long id, String name, String username) {
        public Map<String, Object> profile() {
            return Map.of("name", name, "username", username);
        }
    }
}
