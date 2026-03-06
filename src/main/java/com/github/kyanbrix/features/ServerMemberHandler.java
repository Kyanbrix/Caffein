package com.github.kyanbrix.features;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.github.kyanbrix.Caffein;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdatePrimaryGuildEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

public class ServerMemberHandler extends ListenerAdapter {


    private static final Logger log = LoggerFactory.getLogger(ServerMemberHandler.class);
    private static final long GUILD_ID = 1469324454470353163L;
    private static final long LOG_CHANNEL_ID = 1477920159443456194L;
    private static final long SERVER_TAG_LOG_ID = 1477920635748352123L;

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        if (event.getMember().getUser().isBot()) return;

        Member member = event.getMember();
        Guild guild = event.getGuild();

        if (guild.getIdLong() != GUILD_ID) return;

        TextChannel logChannel = guild.getTextChannelById(LOG_CHANNEL_ID);

        WebhookClient client = WebhookClient.withUrl(System.getenv("WELCOME_WEBHOOK"));

        WebhookEmbed.EmbedAuthor author = new WebhookEmbed.EmbedAuthor("New Member", guild.getIconUrl(), null);

        WebhookEmbed webhookEmbed = new WebhookEmbedBuilder()
                .setAuthor(author)
                .setDescription(String.format("%s has joined the server", member.getAsMention()))
                .setColor(15316501)
                .setThumbnailUrl(member.getUser().getAvatarUrl())
                .build();

        WebhookMessage webhookMessage = new WebhookMessageBuilder()
                .setUsername(guild.getName())
                .setAvatarUrl(guild.getIconUrl())
                .addEmbeds(webhookEmbed)
                .build();

        client.send(webhookMessage);

        String accountAge = TimeFormat.RELATIVE.format(member.getUser().getTimeCreated());

        MessageEmbed embed = new EmbedBuilder()
                .setAuthor("Member Joined", null, member.getAvatarUrl())
                .addField("User", String.format("%s (%s)", member.getAsMention(), member.getUser().getName()), false)
                .addField("Account Created", accountAge, false)
                .setColor(Color.ORANGE)
                .setThumbnail(member.getUser().getAvatarUrl())
                .setFooter("User ID: " + member.getId())
                .setTimestamp(Instant.now())
                .build();

        if (logChannel != null) logChannel.sendMessageEmbeds(embed).queue();
        else log.error("Member joined log channel is null");

    }


    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {

        if (event.getUser().isBot()) return;

        long memberIdLong = event.getUser().getIdLong();

        Guild guild = event.getGuild();
        TextChannel logChannel = guild.getTextChannelById(LOG_CHANNEL_ID);
        User user = event.getUser();

        if (logChannel != null) {

            MessageEmbed embed = new EmbedBuilder()
                    .setAuthor("User Left", null, user.getAvatarUrl())
                    .setDescription(String.format("%s (%s) has left the server", user.getAsMention(), user.getName()))
                    .setColor(Color.decode("#FF0000"))
                    .setThumbnail(user.getAvatarUrl())
                    .setFooter("ID: " + user.getIdLong())
                    .build();

            logChannel.sendMessageEmbeds(embed).queue();
        }


        try (Connection connection = Caffein.getInstance().getConnection()) {

            String deleteUserMessages = "DELETE FROM user_messages WHERE userid = ?";
            String deleteFromRegular = "DELETE FROM regulars WHERE user_id = ?";

            try (PreparedStatement ps = connection.prepareStatement(deleteUserMessages)) {

                ps.setObject(1, memberIdLong);
                ps.executeUpdate();

                try (PreparedStatement ps1 = connection.prepareStatement(deleteFromRegular)) {

                    ps1.setObject(1, memberIdLong);
                    ps1.executeUpdate();
                }

            }

        } catch (SQLException e) {
            log.error(e.getMessage(), e.fillInStackTrace());
        }


    }


    @Override
    public void onUserUpdatePrimaryGuild(@NotNull UserUpdatePrimaryGuildEvent event) {
        Guild guild = event.getJDA().getGuildById(GUILD_ID);
        if (guild == null) return;

        TextChannel logChannel = guild.getTextChannelById(SERVER_TAG_LOG_ID);
        if (logChannel == null) {
            log.error("Primary guild log channel is null for guild {}", GUILD_ID);
            return;
        }

        User user = event.getUser();
        User.PrimaryGuild newGuild = event.getNewPrimaryGuild();
        User.PrimaryGuild oldGuild = event.getOldPrimaryGuild();

        EmbedBuilder builder = new EmbedBuilder()
                .setAuthor(user.getName(), null, user.getAvatarUrl())
                .setTimestamp(Instant.now());

        boolean hadTag = oldGuild != null && oldGuild.getIdLong() == GUILD_ID;
        boolean hasTag = newGuild != null && newGuild.getIdLong() == GUILD_ID;

        if (hasTag && !hadTag) {
            builder.setDescription(String.format("%s used our server tag", user.getAsMention()))
                    .setColor(Color.decode("#FF4500"));
        } else if (!hasTag && hadTag) {
            builder.setDescription(String.format("%s removed our server tag from their profile", user.getAsMention()))
                    .setColor(Color.decode("#FF4500"));
        } else {
            return;
        }

        logChannel.sendMessageEmbeds(builder.build()).queue();
    }

    @Override
    public void onGuildMemberRoleRemove(@NotNull GuildMemberRoleRemoveEvent event) {

        Member member = event.getMember();

        Guild guild = event.getGuild();

        if (event.getRoles().contains(guild.getBoostRole())) {

            try (Connection connection = Caffein.getInstance().getConnection()) {

                try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM premium_role WHERE user_id = ?")) {
                    ps.setLong(1, member.getIdLong());

                    try (ResultSet set = ps.executeQuery()) {

                        if (set.next()) {

                            long roleId = set.getLong("role_id");

                            Role role = guild.getRoleById(roleId);

                            if (role != null) guild.removeRoleFromMember(member, role).queue();

                            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM premium_role WHERE user_id = ?")) {
                                delete.setLong(1, member.getIdLong());
                                delete.executeUpdate();

                                log.info("Successfully remove a user from premium_role");
                            }


                        }


                    }

                }

            } catch (SQLException e) {
                log.error("Error on retrieving data from premium_role", e);
            }


        }


    }
}
