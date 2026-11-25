package com.github.kyanbrix.config.database;

import com.github.kyanbrix.Caffein;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Logs every successful {@code /imagine} image, so the spend can be checked. The table is created on first use because the project has no migration tool.
 */
public final class ImageGenerationRepository {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerationRepository.class);

    private static volatile boolean tableReady;

    private ImageGenerationRepository() {}

    private static void ensureTable(Connection connection) throws SQLException {
        if (tableReady) return;

        synchronized (ImageGenerationRepository.class) {
            if (tableReady) return;

            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS image_generations (
                            id            BIGSERIAL PRIMARY KEY,
                            user_id       BIGINT      NOT NULL,
                            guild_id      BIGINT      NOT NULL,
                            prompt        TEXT        NOT NULL,
                            model         VARCHAR(64) NOT NULL,
                            quality       VARCHAR(16) NOT NULL,
                            input_tokens  BIGINT      NOT NULL DEFAULT 0,
                            output_tokens BIGINT      NOT NULL DEFAULT 0,
                            created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
                        )""");
                statement.execute("CREATE INDEX IF NOT EXISTS image_generations_guild_idx ON image_generations (guild_id, created_at)");
            }
            tableReady = true;
        }
    }

    public static void save(long userId, long guildId, String prompt, String model, String quality,
                            long inputTokens, long outputTokens) {

        try (Connection connection = Caffein.getInstance().getConnection()) {
            ensureTable(connection);

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO image_generations (user_id, guild_id, prompt, model, quality, input_tokens, output_tokens)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""")) {
                statement.setLong(1, userId);
                statement.setLong(2, guildId);
                statement.setString(3, prompt);
                statement.setString(4, model);
                statement.setString(5, quality);
                statement.setLong(6, inputTokens);
                statement.setLong(7, outputTokens);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            log.error("Failed to log image generation for user {}", userId, e);
        }
    }
}
