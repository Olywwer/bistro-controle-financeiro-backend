package br.com.bistrodesabores.financeiro.api;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api")
public class FinancialDataController {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FinancialDataController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("status", "ok"));
    }

    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> getData() {
        Map<String, Object> response = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT data::text, updated_at FROM bistro_financial_data WHERE id = 1",
                resultSet -> {
                    if (resultSet.next()) {
                        try {
                            response.put("data", objectMapper.readTree(resultSet.getString(1)));
                        } catch (Exception exception) {
                            throw new IllegalStateException("Os dados financeiros salvos no PostgreSQL não são JSON válido.", exception);
                        }
                        Timestamp updatedAt = resultSet.getTimestamp(2);
                        response.put("updatedAt", updatedAt == null ? null : updatedAt.toInstant().toString());
                    }
                    return null;
                });
        if (!response.containsKey("data")) {
            response.put("data", null);
            response.put("updatedAt", null);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PutMapping("/data")
    @Transactional
    public ResponseEntity<Map<String, Object>> saveData(
            @RequestBody JsonNode data,
            HttpServletRequest request) {
        validateData(data);
        AuthService.AuthenticatedUser user =
                (AuthService.AuthenticatedUser) request.getAttribute(AuthSessionFilter.USER_ATTRIBUTE);
        List<String> previousRows = jdbcTemplate.queryForList(
                "SELECT data::text FROM bistro_financial_data WHERE id = 1",
                String.class);
        JsonNode previousData = null;
        if (!previousRows.isEmpty()) {
            try {
                previousData = objectMapper.readTree(previousRows.get(0));
            } catch (Exception exception) {
                throw new IllegalStateException("Os dados financeiros salvos no PostgreSQL não são JSON válido.", exception);
            }
        }
        JsonNode dataToSave = addEntryCreators(data, previousData, user);
        final String json;
        try {
            json = objectMapper.writeValueAsString(dataToSave);
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível serializar os dados financeiros.", exception);
        }

        Timestamp updatedAt = jdbcTemplate.queryForObject(
                """
                INSERT INTO bistro_financial_data (id, data, updated_at)
                VALUES (1, ?::jsonb, now())
                ON CONFLICT (id) DO UPDATE
                SET data = EXCLUDED.data, updated_at = now()
                RETURNING updated_at
                """,
                Timestamp.class,
                json);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("saved", true, "updatedAt", updatedAt.toInstant().toString()));
    }

    private JsonNode addEntryCreators(
            JsonNode data,
            JsonNode previousData,
            AuthService.AuthenticatedUser user) {
        ObjectNode dataToSave = data.deepCopy();
        Map<String, JsonNode> previousEntries = new HashMap<>();
        if (previousData != null && previousData.path("entries").isArray()) {
            for (JsonNode previousEntry : previousData.path("entries")) {
                String id = previousEntry.path("id").asText("");
                if (!id.isBlank()) previousEntries.put(id, previousEntry);
            }
        }

        for (JsonNode node : dataToSave.path("entries")) {
            if (!(node instanceof ObjectNode entry)) continue;
            JsonNode previousEntry = previousEntries.get(entry.path("id").asText(""));
            if (previousEntry != null) {
                copyCreatorField(previousEntry, entry, "createdByName");
                copyCreatorField(previousEntry, entry, "createdByUsername");
                copyCreatorField(previousEntry, entry, "createdAt");
            } else if (previousData != null) {
                entry.put("createdByName", user.name());
                entry.put("createdByUsername", user.username());
                entry.put("createdAt", java.time.Instant.now().toString());
            } else {
                entry.remove("createdByName");
                entry.remove("createdByUsername");
                entry.remove("createdAt");
            }
        }
        return dataToSave;
    }

    private static void copyCreatorField(JsonNode source, ObjectNode target, String field) {
        JsonNode value = source.get(field);
        if (value == null) target.remove(field);
        else target.set(field, value.deepCopy());
    }

    private void validateData(JsonNode data) {
        if (data == null || !data.isObject()
                || !data.path("entries").isArray()
                || !data.path("suppliers").isArray()
                || !data.path("banks").isArray()
                || !data.path("bankUpdates").isArray()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Dados financeiros inválidos. Atualize a página e tente novamente.");
        }
    }
}
