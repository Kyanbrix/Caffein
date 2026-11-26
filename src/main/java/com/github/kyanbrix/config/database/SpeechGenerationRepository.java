package com.github.kyanbrix.config.database;

import com.github.kyanbrix.Caffein;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Logs every {@code /speak} clip (not the text itself), so the spend can be checked. The speech endpoint
 * returns no token usage, so the input length is logged instead. The table is created on first use
 * because the project has no migration tool.
 */
public final class SpeechGenerationRepository {

    private static final Logger log = LoggerFactory.getLogger(SpeechGenerationRepository.class);

    private static volatile boolean tableReady;

    private SpeechGenerationRepository() {}

    private static void ensureTable(Connection connection) throws SQLException {
        if (tableReady) return;

        synchronized (SpeechGenerationRepository.class) {
            if (tableReady) return;

            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS speech_generations (
                            id          BIGSERIAL PRIMARY KEY,
                            user_id     BIGINT      NOT NULL,
                            guild_id    BIGINT      NOT NULL,
                            model       VARCHAR(64) NOT NULL,
                            voice       VARCHAR(32) NOT NULL,
                            language    VARCHAR(8),
                            characters  INT         NOT NULL,
                            audio_bytes INT         NOT NULL,
                            created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
                        )""");
            }
            tableReady = true;
        }
    }

    /** @param language the language the text was translated into first, or null if it was spoken as written */
    public static void save(long userId, long guildId, String model, String voice, String language,
                            int characters, int audioBytes) {

        try (Connection connection = Caffein.getInstance().getConnection()) {
            ensureTable(connection);

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO speech_generations (user_id, guild_id, model, voice, language, characters, audio_bytes)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""")) {
                statement.setLong(1, userId);
                statement.setLong(2, guildId);
                statement.setString(3, model);
                statement.setString(4, voice);
                statement.setString(5, language);
                statement.setInt(6, characters);
                statement.setInt(7, audioBytes);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            log.error("Failed to log speech generation for user {}", userId, e);
        }
    }
}
