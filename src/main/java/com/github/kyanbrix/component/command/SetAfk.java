package com.github.kyanbrix.component.command;

import com.github.kyanbrix.Caffein;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SetAfk implements ICommand {
    private static final Logger log = LoggerFactory.getLogger(SetAfk.class);

    @Override
    public void accept(MessageReceivedEvent event) {

        long userId = event.getAuthor().getIdLong();
        Member member = event.getMember();
        Guild guild = event.getGuild();
        MessageChannelUnion channel = event.getChannel();


        try (Connection connection = Caffein.getInstance().getConnection()) {

            if (isUserAfk(userId,connection)) {

                channel.sendMessage("You are already set to afk!").queue();
                return;
            }

            String sql = "INSERT INTO afk_status (user_id, afk_message, afk_stamp) VALUES (?,?,?)";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1,userId);
                ps.setString(2,"");

            }





        }catch (SQLException e) {
            log.error(e.getMessage());
        }




    }

    @Override
    public String commandName() {
        return "afk";
    }

    @Override
    public String[] aliases() {
        return new String[]{"idle"};
    }

    private boolean isUserAfk(long userId, Connection connection) throws SQLException {

        String sql = "SELECT user_id FROM afk_status WHERE user_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1,userId);

            try (ResultSet set = ps.executeQuery()) {

                return set.next();

            }

        }
    }
}
