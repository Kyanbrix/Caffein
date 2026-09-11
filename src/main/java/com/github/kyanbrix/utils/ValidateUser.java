package com.github.kyanbrix.utils;

import com.github.kyanbrix.Caffein;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ValidateUser {


    private static final Logger log = LoggerFactory.getLogger(ValidateUser.class);

    public static boolean isUserAuthenticated(long userid) {

        try (Connection connection = Caffein.getInstance().getConnection()) {

            PreparedStatement ps = connection.prepareStatement("SELECT userid FROM lastfm WHERE userid = ?");
            ps.setLong(1, userid);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        }catch(Exception e) {
            log.error("Error connecting to database.", e);
        }

        return false;
    }

}
