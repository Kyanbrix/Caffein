package com.github.kyanbrix.config.database;

import com.github.kyanbrix.Caffein;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores the chatbot's conversation turns per session (one session per Discord channel)
 * so the bot remembers a conversation across restarts.
 * The table is created on first use because the project has no migration tool.
 */
public final class ChatHistoryRepository {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryRepository.class);

    public record StoredMessage(String role, String content) {}

    private static volatile boolean tableReady;

    private ChatHistoryRepository() {}

    private static void ensureTable(Connection connection) throws SQLException {
        if (tableReady) return;

        synchronized (ChatHistoryRepository.class) {
            if (tableReady) return;

            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS chat_history (
                            id         BIGSERIAL PRIMARY KEY,
                            session_id VARCHAR(32) NOT NULL,
                            role       VARCHAR(16) NOT NULL,
                            content    TEXT        NOT NULL,
                            created_at TIMESTAMPTZ NOT NULL DEFAULT now()
                        )""");
                statement.execute("CREATE INDEX IF NOT EXISTS chat_history_session_idx ON chat_history (session_id, id DESC)");
            }
            tableReady = true;
        }
    }

    /** Returns the newest {@code limit} messages of a session, oldest first. */
    public static List<StoredMessage> loadRecent(String sessionId, int limit) throws SQLException {

        try (Connection connection = Caffein.getInstance().getConnection()) {
            ensureTable(connection);

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT role, content FROM chat_history WHERE session_id = ? ORDER BY id DESC LIMIT ?")) {
                statement.setString(1, sessionId);
                statement.setInt(2, limit);

                List<StoredMessage> messages = new ArrayList<>(limit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        messages.add(new StoredMessage(rs.getString("role"), rs.getString("content")));
                    }
                }
                Collections.reverse(messages);
                return messages;
            }
        }
    }

    /** Saves a user message and the assistant's reply together in one batch. */
    public static void saveTurn(String sessionId, String userContent, String assistantContent) {

        try (Connection connection = Caffein.getInstance().getConnection()) {
            ensureTable(connection);

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO chat_history (session_id, role, content) VALUES (?, ?, ?)")) {
                statement.setString(1, sessionId);
                statement.setString(2, "user");
                statement.setString(3, userContent);
                statement.addBatch();

                statement.setString(1, sessionId);
                statement.setString(2, "assistant");
                statement.setString(3, assistantContent);
                statement.addBatch();

                statement.executeBatch();
            }
        } catch (SQLException e) {
            log.error("Failed to save chat history for session {}", sessionId, e);
        }
    }

    /** Deletes a session's whole history; returns how many messages were removed. */
    public static int clear(String sessionId) throws SQLException {

        try (Connection connection = Caffein.getInstance().getConnection()) {
            ensureTable(connection);

            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM chat_history WHERE session_id = ?")) {
                statement.setString(1, sessionId);
                return statement.executeUpdate();
            }
        }
    }
}
