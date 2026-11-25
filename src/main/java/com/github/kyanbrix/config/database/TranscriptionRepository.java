package com.github.kyanbrix.config.database;

import com.github.kyanbrix.Caffein;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Logs every {@code /transcribe} request (not the transcript itself), so the spend can be checked.
 * The table is created on first use because the project has no migration tool.
 */
public final class TranscriptionRepository {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionRepository.class);

    private static volatile boolean tableReady;

    private TranscriptionRepository() {}

    private static void ensureTable(Connection connection) throws SQLException {
        if (tableReady) return;

        synchronized (TranscriptionRepository.class) {
            if (tableReady) return;

            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS transcriptions (
                            id            BIGSERIAL PRIMARY KEY,
                            user_id       BIGINT           NOT NULL,
                            guild_id      BIGINT           NOT NULL,
                            model         VARCHAR(64)      NOT NULL,
                            language      VARCHAR(8),
                            seconds       DOUBLE PRECISION NOT NULL DEFAULT 0,
                            input_tokens  BIGINT           NOT NULL DEFAULT 0,
                            output_tokens BIGINT           NOT NULL DEFAULT 0,
                            created_at    TIMESTAMPTZ      NOT NULL DEFAULT now()
                        )""");
            }
            tableReady = true;
        }
    }

    /** @param language the ISO-639-1 hint that was sent, or null when the model detected it */
    public static void save(long userId, long guildId, String model, String language, double seconds,
                            long inputTokens, long outputTokens) {

        try (Connection connection = Caffein.getInstance().getConnection()) {
            ensureTable(connection);

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO transcriptions (user_id, guild_id, model, language, seconds, input_tokens, output_tokens)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""")) {
                statement.setLong(1, userId);
                statement.setLong(2, guildId);
                statement.setString(3, model);
                statement.setString(4, language);
                statement.setDouble(5, seconds);
                statement.setLong(6, inputTokens);
                statement.setLong(7, outputTokens);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            log.error("Failed to log transcription for user {}", userId, e);
        }
    }
}
