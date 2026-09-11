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




    public void saveUserSession(long userid,String username, String sessionKey) {

        try (Connection connection = Caffein.getInstance().getConnection()) {

            PreparedStatement statement = connection.prepareStatement("INSERT INTO lastfm (userid, username, sessionkey) VALUES (?, ?, ?) ");
            statement.setLong(1, userid);
            statement.setString(2, username);
            statement.setString(3, sessionKey);

            int row = statement.executeUpdate();

            if (row > 0) {
                log.info("User {} is successfully saved", userid);
            }

        }catch (SQLException e) {
            log.error(e.getMessage());
        }

    }

    public String getUsernameFromLastFm(long userid) {

        try (Connection connection = Caffein.getInstance().getConnection()) {

            PreparedStatement statement = connection.prepareStatement("SELECT username FROM lastfm WHERE userid = ? ");
            statement.setLong(1, userid);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {

                    return resultSet.getString("username");
                }else return null;

            }


        }catch (SQLException e) {
            log.error(e.getMessage());
        }


        return null;
    }







}
