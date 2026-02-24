package com.github.kyanbrix;

import com.github.kyanbrix.database.SQLBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class GuildMessagesHandler extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(GuildMessagesHandler.class);

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {

        //Only for non-admins
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.getMember().hasPermission(Permission.ADMINISTRATOR)) return;

        final long userId = event.getAuthor().getIdLong();

        Guild guild = event.getGuild();

        insertData(userId);

        checkIfUserIsGoingToBeRegular(userId,guild,event.getMember());



    }


    private void insertData(long userId) {

        String sql = "INSERT INTO user_messages (userid,total_messages,last_message) VALUES(?,?,?) ON CONFLICT (userid)" +
                " DO UPDATE " +
                "SET total_messages = user_messages.total_messages + 1 , " +
                "last_message = EXCLUDED.last_message";


        try (Connection connection = Caffein.getInstance().getConnection()) {

            try (PreparedStatement statement = connection.prepareStatement(sql)){
                statement.setObject(1,userId);
                statement.setObject(2,1);
                statement.setObject(3,Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Manila"))));

                int rows = statement.executeUpdate();

                System.out.println("Rows: "+rows);
            }



        }catch (SQLException e) {
            log.error(e.getMessage());
        }


    }

    private void checkIfUserIsGoingToBeRegular(final long user_id, Guild guild, Member member) {

        List<Role> memberRoles = member.getRoles();

        Role role = guild.getRoleById(1475742792092487851L);

        try (Connection connection = Caffein.getInstance().getConnection()){

            String sql = "SELECT * FROM user_messages WHERE userid = ?";


            try(PreparedStatement query = connection.prepareStatement(sql)) {
                query.setObject(1,user_id);

                try (ResultSet set = query.executeQuery()) {

                    if (set.next()) {
                        int total_message = set.getInt("total_messages");

                        if (total_message >= 200) {

                            if (role !=null && !memberRoles.contains(role)) guild.addRoleToMember(member,role).queue();

                            String sqlInsert = "INSERT INTO regulars (user_id,last_message) VALUES(?,?) ON CONFLICT (user_id) DO UPDATE SET last_message = EXCLUDED.last_message";

                            try (PreparedStatement insertData = connection.prepareStatement(sqlInsert)) {
                                insertData.setObject(1,user_id);
                                insertData.setTimestamp(2,Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Manila"))));

                                insertData.executeUpdate();

                            }

                        }
                    }

                }
            }




        }catch (SQLException e) {
            log.error(e.getMessage());
        }


    }


}
