package com.github.kyanbrix.config.database;

import com.github.kyanbrix.Caffein;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserRepository {


    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);


    /**
     * Links a Discord user to a Last.fm account, replacing any earlier link.
     * Updates first so relinking works whether or not {@code lastfm.userid} has a unique constraint.
     *
     * @throws SQLException so the callback page can tell the user the link didn't save
     */
    public void saveLastFmSession(long userid, String username, String sessionKey) throws SQLException {

        try (Connection connection = Caffein.getInstance().getConnection()) {

            try (PreparedStatement update = connection.prepareStatement("UPDATE lastfm SET username = ?, sessionkey = ? WHERE userid = ?")) {
                update.setString(1, username);
                update.setString(2, sessionKey);
                update.setLong(3, userid);

                if (update.executeUpdate() > 0) {
                    log.info("User {} relinked Last.fm as {}", userid, username);
                    return;
                }
            }

            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO lastfm (userid, username, sessionkey) VALUES (?, ?, ?)")) {
                insert.setLong(1, userid);
                insert.setString(2, username);
                insert.setString(3, sessionKey);
                insert.executeUpdate();
                log.info("User {} linked Last.fm as {}", userid, username);
            }
        }

    }

    /** Removes a user's Last.fm link; returns whether they had one. */
    public boolean deleteLastFmLink(long userid) throws SQLException {

        try (Connection connection = Caffein.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM lastfm WHERE userid = ?")) {
            statement.setLong(1, userid);
            return statement.executeUpdate() > 0;
        }

    }

    /** The user's linked Last.fm name, or null if they haven't linked one (or the lookup failed). */
    public String getUsernameFromLastFm(long userid) {

        try (Connection connection = Caffein.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT username FROM lastfm WHERE userid = ?")) {
            statement.setLong(1, userid);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("username") : null;
            }

        } catch (SQLException e) {
            log.error("Failed to look up the Last.fm link of {}", userid, e);
        }

        return null;
    }

}
