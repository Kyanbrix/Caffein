package com.github.kyanbrix.features;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.http.client.methods.RequestBuilder;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class AntiRaid extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(AntiRaid.class);
    private final Cache<Long,Boolean> recentJoin = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.SECONDS)
            .build();


    @Override
    public void onGuildMemberJoin(@NonNull GuildMemberJoinEvent event) {
        if (event.getUser().isBot()) return;

        Member member = event.getMember();

        Guild guild = event.getGuild();


        recentJoin.put(member.getIdLong(),true);

        int count = recentJoin.asMap().size();


        

        if (count > 10) {
            log.warn("JOIN FLOOD DETECTED! Pausing Invites.");

            guild.getManager().setInvitesDisabled(true).queue();

            recentJoin.invalidateAll();
        }

    }



}
