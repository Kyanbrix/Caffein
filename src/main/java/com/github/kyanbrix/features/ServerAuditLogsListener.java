package com.github.kyanbrix.features;

import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class ServerAuditLogsListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ServerAuditLogsListener.class);

    @Override
    public void onGuildAuditLogEntryCreate(@NonNull GuildAuditLogEntryCreateEvent event) {

        JDA jda = event.getJDA();

        TextChannel channel = jda.getTextChannelById(1485998964733120822L);

        if (channel == null) return;

        String eventType = event.getEntry().getType().name();
        long userId = event.getEntry().getUserIdLong();

        EmbedBuilder builder = new EmbedBuilder();
        builder.setColor(0xDC143C);
        builder.setAuthor(jda.getGuildById(Constant.SERVER_CAFE_ID).getName()+" audit logs",null,jda.getGuildById(Constant.SERVER_CAFE_ID).getIconUrl());
        builder.setTimestamp(Instant.now());
        builder.setFooter("User ID: "+userId);
        switch (eventType) {

            case "MESSAGE_DELETE" -> {

                long targetId = event.getEntry().getTargetIdLong();
                builder.setDescription(String.format("<@%d> message was deleted by <@%d>",targetId,userId));
                channel.sendMessageEmbeds(builder.build()).queue();

            }

            case "CHANNEL_CREATE" -> {
                long channelId = event.getEntry().getTargetIdLong();
                builder.setDescription(String.format("<@%d> created a channel <#%d>",userId,channelId));
                channel.sendMessageEmbeds(builder.build()).queue();
            }

            case "ROLE_DELETE" -> {

                builder.setDescription(String.format("<@%d> deleted a role",userId));
                channel.sendMessageEmbeds(builder.build()).queue();
            }

            case "BAN" -> {

                long banUserId = event.getEntry().getTargetIdLong();
                builder.setDescription(String.format("<@%d> banned user <@%d> ",userId,banUserId));
                channel.sendMessageEmbeds(builder.build()).queue();
            }

            case "KICK" -> {
                long kickUser = event.getEntry().getTargetIdLong();
                builder.setDescription(String.format("<@%d> kicked user <@%d> ",userId,kickUser));
                channel.sendMessageEmbeds(builder.build()).queue();
            }

            case "ROLE_CREATE" -> {

                long roleId = event.getEntry().getTargetIdLong();

                builder.setDescription(String.format("<@%d> created a role <@%d>",userId,roleId));
                channel.sendMessageEmbeds(builder.build()).queue();
            }

            case "ROLE_UPDATE" -> {

                long roleId = event.getEntry().getTargetIdLong();

                Role role = jda.getRoleById(roleId);

                if (role == null) {
                    log.warn("Role is null cannot add to the audit logs");
                    return;
                }

                builder.setDescription(String.format("<@%d> updated the ``%s`` role",userId,role.getName()));
                channel.sendMessageEmbeds(builder.build()).queue();
            }

            case "CHANNEL_DELETE" -> {
                builder.setDescription(String.format("<@%d> deleted a channel",userId));
                channel.sendMessageEmbeds(builder.build()).queue();
            }

        }

    }
}
