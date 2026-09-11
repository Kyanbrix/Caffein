package com.github.kyanbrix.features;

import com.github.kyanbrix.utils.Constant;
import com.github.kyanbrix.features.data.InviteData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteDeleteEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InviteTracker extends ListenerAdapter {

    private static final long LOG_ID = 1477919584181813404L;
    private static final Logger log = LoggerFactory.getLogger(InviteTracker.class);

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {

        User user = event.getUser();
        Member member = event.getMember();
        Guild guild = event.getGuild();

        if (member.hasPermission(Permission.ADMINISTRATOR) || member.hasPermission(Permission.MANAGE_SERVER) || user.isBot()) return;



        TextChannel logChannel = guild.getTextChannelById(LOG_ID);

        guild.retrieveInvites().queue(invites -> {

            for (Invite invite: invites) {

                String code = invite.getCode();
                final InviteData cache = InviteCache.getInstance().getInviteData(code);

                if (cache == null) continue;

                if (invite.getUses() == cache.getNumberOfUses()) continue;

                if (invite.getUses() > cache.getNumberOfUses()) {
                    cache.incrementUses();

                    sendMessageLogs(member, guild, logChannel, String.format("User %s used invite url ``%s`` created by %s",user.getAsMention(),invite.getUrl(),invite.getInviter().getAsMention()));
                    return;
                }

            }

            sendMessageLogs(member,guild,logChannel,String.format("User %s used vanity url ``%s`` to join.",user.getAsMention(),guild.getVanityUrl()));

        });

    }

    private void sendMessageLogs(Member member, Guild guild, TextChannel logChannel,String description) {
        if (logChannel!=null) {

            MessageEmbed embed = new EmbedBuilder()
                    .setAuthor("Invite Logger",null,guild.getIconUrl())
                    .setDescription(description)
                    .setColor(Color.decode("#FFEBCD"))
                    .setThumbnail(member.getAvatarUrl())
                    .setTimestamp(Instant.now())
                    .build();

            logChannel.sendMessageEmbeds(embed).queue();

        }else System.out.println("Cannot find the log channel");
    }

    @Override
    public void onGuildInviteCreate(@NotNull GuildInviteCreateEvent event) {


        String inviteCode = event.getCode();
        final InviteData inviteData = new InviteData(event.getInvite());
        InviteCache.getInstance().setInviteCache(inviteCode,inviteData);

    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {

        Guild guild = event.getGuild();

        if (guild.getIdLong() == Constant.SERVER_CAFE_ID) {

            guild.retrieveInvites().queue(invites -> {

                for (Invite invite : invites) {


                    final InviteData inviteData = new InviteData(invite);

                    InviteCache.getInstance().setInviteCache(invite.getCode(),inviteData);

                }
            });


        }



    }

    @Override
    public void onGuildInviteDelete(@NotNull GuildInviteDeleteEvent event) {


        final String code = event.getCode();

        InviteCache.getInstance().removeInviteCache(code);
    }
}
