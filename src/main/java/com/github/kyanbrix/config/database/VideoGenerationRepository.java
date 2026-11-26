package com.github.kyanbrix.config.database;

import com.github.kyanbrix.Caffein;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Logs every {@code /video} job OpenAI accepted, whatever its outcome, so the spend can be checked and a
 * finished video can be found again by its OpenAI ID. The table is created on first use because the project
 * has no migration tool.
 */
public final class VideoGenerationRepository {

    private static final Logger log = LoggerFactory.getLogger(VideoGenerationRepository.class);

    private static volatile boolean tableReady;

    private VideoGenerationRepository() {}

    private static void ensureTable(Connection connection) throws SQLException {
        if (tableReady) return;

        synchronized (VideoGenerationRepository.class) {
            if (tableReady) return;

            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS video_generations (
                            id             BIGSERIAL PRIMARY KEY,
                            user_id        BIGINT       NOT NULL,
                            guild_id       BIGINT       NOT NULL,
                            video_id       VARCHAR(128) NOT NULL,
                            prompt         TEXT         NOT NULL,
                            model          VARCHAR(64)  NOT NULL,
                            size           VARCHAR(16)  NOT NULL,
                            seconds        INT          NOT NULL,
                            with_reference BOOLEAN      NOT NULL DEFAULT FALSE,
                            status         VARCHAR(32)  NOT NULL,
                            estimated_cost NUMERIC(8,2) NOT NULL DEFAULT 0,
                            created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
                        )""");
                statement.execute("CREATE INDEX IF NOT EXISTS video_generations_guild_idx ON video_generations (guild_id, created_at)");
            }
            tableReady = true;
        }
    }

    /**
     * @param status         {@code completed}, {@code failed} or {@code timed_out}
     * @param estimatedCost  dollars; 0 for a job that didn't complete
     */
    public static void save(long userId, long guildId, String videoId, String prompt, String model, String size,
                            int seconds, boolean withReference, String status, double estimatedCost) {

        try (Connection connection = Caffein.getInstance().getConnection()) {
            ensureTable(connection);

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO video_generations
                        (user_id, guild_id, video_id, prompt, model, size, seconds, with_reference, status, estimated_cost)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
                statement.setLong(1, userId);
                statement.setLong(2, guildId);
                statement.setString(3, videoId);
                statement.setString(4, prompt);
                statement.setString(5, model);
                statement.setString(6, size);
                statement.setInt(7, seconds);
                statement.setBoolean(8, withReference);
                statement.setString(9, status);
                statement.setBigDecimal(10, BigDecimal.valueOf(estimatedCost).setScale(2, RoundingMode.HALF_UP));
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            log.error("Failed to log video generation {} for user {}", videoId, userId, e);
        }
    }
}
