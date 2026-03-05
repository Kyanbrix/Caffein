package com.github.kyanbrix.component;

import com.github.kyanbrix.Caffein;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class RoleCreationModal extends ListenerAdapter {

    private static final String MODAL_ID = "createRole";
    private static final String UPDATE_ROLE_ID = "updateRole";
    private static final Logger log = LoggerFactory.getLogger(RoleCreationModal.class);

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {



        switch (event.getCustomId()) {

            case MODAL_ID -> {
                Guild guild = event.getGuild();
                User user = event.getUser();

                String role_name = event.getValue("role_name").getAsString();

                String colorHex = event.getValue("color_hex").getAsString();

                List<Message.Attachment> icon = event.getValue("role_icon").getAsAttachmentList();

                Message.Attachment roleImage = icon.getFirst();


                if (!icon.isEmpty()) {

                    roleImage.getProxy().download().thenAccept(inputStream -> {

                        try {

                            Icon roleIcon = Icon.from(inputStream);

                            guild.createRole().setName(role_name).setIcon(roleIcon).setColor(Color.decode(colorHex)).queue(role -> {

                                guild.modifyRolePositions().selectPosition(role).moveTo(6).queue();

                                try (Connection connection = Caffein.getInstance().getConnection()) {

                                    try (PreparedStatement insert = connection.prepareStatement("INSERT INTO premium_role (user_id, role_id) VALUES (?,?)")) {
                                        insert.setLong(1,user.getIdLong());
                                        insert.setLong(2,role.getIdLong());
                                        insert.executeUpdate();
                                    }

                                    event.reply("Role has been created! Kindly check your profile if it is being added.").setEphemeral(true).queue();

                                }catch (SQLException e) {
                                    log.error("SQL error when inserting data",e);
                                }



                            },throwable -> event.reply("Your color hex code format is wrong!").setEphemeral(true).queue());

                        }catch (IOException e) {
                            log.error("Failed to read the download image");
                        }


                    }).exceptionally(throwable -> {
                        log.error("Failed to download");
                        return null;
                    });



                }else {

                    guild.createRole().setName(role_name).setColor(Color.decode(colorHex)).queue(role -> {

                        guild.modifyRolePositions().selectPosition(role).moveTo(6).queue();

                        try (Connection connection = Caffein.getInstance().getConnection()) {

                            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO premium_role (user_id, role_id) VALUES (?,?)")) {
                                insert.setLong(1,user.getIdLong());
                                insert.setLong(2,role.getIdLong());
                                insert.executeUpdate();

                            }

                            event.reply("Role has been created! Kindly check your profile if it is being added.").setEphemeral(true).queue();

                        }catch (SQLException e) {
                            log.error("SQL error when inserting data",e);
                        }



                    },throwable -> event.reply("Your color hex code format is wrong!").setEphemeral(true).queue());
                }
            }


            case UPDATE_ROLE_ID -> {
                Guild guild = event.getGuild();
                User user = event.getUser();

                String role_name = event.getValue("new_role_name").getAsString();

                String colorHex = event.getValue("new_color_hex").getAsString();

                List<Message.Attachment> attachments = event.getValue("new_role_icon").getAsAttachmentList();


                if (!attachments.isEmpty()) {

                    Message.Attachment iconToDownload = attachments.getFirst();

                    iconToDownload.getProxy().download().thenAccept(inputStream -> {

                        try {
                            Icon roleIcon = Icon.from(inputStream);

                            try (Connection connection = Caffein.getInstance().getConnection()) {

                                try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM premium_role WHERE user_id = ?")) {
                                    ps.setLong(1,user.getIdLong());

                                    try (ResultSet set = ps.executeQuery()) {

                                        if (set.next()) {
                                            long roleId = set.getLong("role_id");

                                            guild.getRoleById(roleId).getManager().setName(role_name).setColor(Color.decode(colorHex)).setIcon(roleIcon).queue();

                                            log.info("Successfully updated a role id {}",roleId);
                                        }


                                    }

                                }


                            }catch (SQLException e) {
                                log.error("SQL Error",e);
                            }



                        }catch (IOException e) {

                            log.error("Error",e);

                        }


                    });



                }else {

                    try (Connection connection = Caffein.getInstance().getConnection()) {

                        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM premium_role WHERE user_id = ?")) {
                            ps.setLong(1,user.getIdLong());

                            try (ResultSet set = ps.executeQuery()) {

                                if (set.next()) {

                                    long roleId = set.getLong("role_id");

                                    guild.getRoleById(roleId).getManager().setName(role_name).setColor(Color.decode(colorHex)).queue();

                                    log.info("Successfully updated a role id {}",roleId);
                                }


                            }

                        }


                    }catch (SQLException e) {
                        log.error("SQL Error",e);
                    }
                }


            }


        }








    }
}
