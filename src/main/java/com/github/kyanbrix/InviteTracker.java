package com.github.kyanbrix;

import com.github.kyanbrix.utils.invite.InviteData;
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

import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InviteTracker extends ListenerAdapter {

    private final Map<String, InviteData> inviteCache = new ConcurrentHashMap<>();


    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {

        User user = event.getUser();
        Member member = event.getMember();
        Guild guild = event.getGuild();

        if (member.hasPermission(Permission.ADMINISTRATOR) || member.hasPermission(Permission.MANAGE_SERVER) || user.isBot()) return;


        TextChannel logChannel = guild.getTextChannelById(1470162738910203994L);

        guild.retrieveInvites().queue(invites -> {

            for (Invite invite: invites) {

                String code = invite.getCode();
                final InviteData cache = inviteCache.get(code);

                if (cache == null) continue;
                if (invite.getUses() == cache.getNumberOfUses()) continue;
                cache.incrementUses();

                if (logChannel!=null) {

                    MessageEmbed embed = new EmbedBuilder()
                            .setAuthor("Invite Logger",null,guild.getIconUrl())
                            .setDescription(String.format("User %s used invite url %s, created by `` %s ``",member.getAsMention(),invite.getUrl(),invite.getInviter().getName()))
                            .setColor(Color.decode("#FFEBCD"))
                            .setTimestamp(Instant.now().atZone(ZoneId.of("Asia/Manila")))
                            .build();

                    logChannel.sendMessageEmbeds(embed).queue();

                }else System.out.println("Cannot find the log channel");

            }




        });






    }

    @Override
    public void onGuildInviteCreate(@NotNull GuildInviteCreateEvent event) {


        String inviteCode = event.getCode();
        final InviteData inviteData = new InviteData(event.getInvite());

        inviteCache.put(inviteCode,inviteData);

    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {

        Guild guild = event.getGuild();

        if (guild.getIdLong() == 1469324454470353163L) {

            guild.retrieveInvites().queue(invites -> {

                for (Invite invite : invites) {

                    final InviteData inviteData = new InviteData(invite);

                    inviteCache.put(invite.getCode(),inviteData);

                }

                System.out.println("All invites are cached!");
            });


        }



    }

    @Override
    public void onGuildInviteDelete(@NotNull GuildInviteDeleteEvent event) {


        final String code = event.getCode();

        inviteCache.remove(code);

    }
}
