package br.com.bistrodesabores.financeiro.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseConfiguration {
    @Bean
    DataSource dataSource(
            @Value("${app.database-url:}") String databaseUrl,
            @Value("${app.database-username:}") String configuredUsername,
            @Value("${app.database-password:}") String configuredPassword) {
        if (databaseUrl.isBlank()) {
            throw new IllegalStateException("DATABASE_URL ausente. Configure a conexão PostgreSQL no arquivo .env.");
        }

        String uriValue = databaseUrl.startsWith("jdbc:") ? databaseUrl.substring("jdbc:".length()) : databaseUrl;
        URI uri = URI.create(uriValue);
        if (!"postgresql".equals(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalStateException("DATABASE_URL precisa ser uma URL PostgreSQL válida.");
        }

        String username = configuredUsername;
        String password = configuredPassword;
        if (uri.getRawUserInfo() != null) {
            String[] credentials = uri.getRawUserInfo().split(":", 2);
            if (username.isBlank()) {
                username = decode(credentials[0]);
            }
            if (password.isBlank() && credentials.length > 1) {
                password = decode(credentials[1]);
            }
        }
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Configure usuário e senha PostgreSQL em DATABASE_URL ou DATABASE_USERNAME/DATABASE_PASSWORD.");
        }

        String host = uri.getHost().contains(":") ? "[" + uri.getHost() + "]" : uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + path + query);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
