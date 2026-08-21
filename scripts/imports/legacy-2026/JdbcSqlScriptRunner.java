import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Minimal PostgreSQL script runner for the generated legacy import.
 * It never prints SQL text or connection credentials.
 */
public final class JdbcSqlScriptRunner {
    private static final String BOUNDARY = "-- SIGEP_STATEMENT_BOUNDARY";

    private JdbcSqlScriptRunner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: JdbcSqlScriptRunner <generated-import.sql>");
        }

        String jdbcUrl = requireEnvironment("DATABASE_URL");
        String username = requireEnvironment("DATABASE_USERNAME");
        String password = requireEnvironment("DATABASE_PASSWORD");
        Path scriptPath = Path.of(args[0]).toAbsolutePath().normalize();
        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        List<String> statements = Arrays.stream(script.split("(?m)^" + BOUNDARY + "\\R", -1))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();

        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("ApplicationName", "sigep-legacy-import-2026");

        int completed = 0;
        try (Connection connection = DriverManager.getConnection(jdbcUrl, properties);
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
                completed++;
            }
        } catch (SQLException exception) {
            String sqlState = exception.getSQLState() == null ? "unknown" : exception.getSQLState();
            throw new IllegalStateException(
                "Import failed at statement " + (completed + 1) + " of " + statements.size()
                    + " (SQLSTATE " + sqlState + "). Database error details were suppressed to protect source data."
            );
        }

        System.out.printf("Import committed successfully: %d statements.%n", completed);
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
