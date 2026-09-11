package com.github.kyanbrix.features.chatfilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.kyanbrix.features.InviteCache;
import com.github.kyanbrix.features.data.InviteData;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DiscordInvitesChatFilter extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(DiscordInvitesChatFilter.class);

    Cache<Long,Integer> infraction = Caffeine.newBuilder()
            .expireAfterWrite(1,TimeUnit.HOURS)
            .maximumSize(100)
            .build();


    @Override
    public void onMessageReceived(@NonNull MessageReceivedEvent event) {


        if (event.getAuthor().isBot() || event.getMember().hasPermission(Permission.ADMINISTRATOR)) return;

        Guild guild = event.getGuild();
        long userId = event.getAuthor().getIdLong();

        Message message = event.getMessage();

        List<String> links = message.getInvites();

        if (links.isEmpty()) {
            return;
        }

        List<String> serverCodes = new ArrayList<>();

        if (InviteCache.getInstance().getInviteCache().isEmpty()) return;

        for (Map.Entry<String, InviteData> inviteDataEntry : InviteCache.getInstance().getInviteCache().entrySet()) {
            serverCodes.add(inviteDataEntry.getKey());
        }

        if (guild.getVanityCode() != null) serverCodes.add(guild.getVanityCode());

        boolean dt = links.stream().anyMatch(link -> !serverCodes.contains(link));

        if (dt) {

        }


        guild.retrieveInvites().queue(invites -> {

            // Get a list of valid codes for THIS server
            List<String> validServerCodes = invites.stream()
                    .map(Invite::getCode)
                    .collect(Collectors.toList());

            validServerCodes.addAll(invites.stream().map(Invite::getUrl).toList());


            if (guild.getVanityCode() != null) {
                validServerCodes.add(guild.getVanityCode());
                validServerCodes.add(guild.getVanityUrl());
            }


            validServerCodes.forEach(System.out::println);

            boolean isDetected = links.stream().anyMatch(link -> !validServerCodes.contains(link));


            if (isDetected) {

                int warnCount = infraction.get(userId,k -> 0) + 1;

                infraction.put(userId,warnCount);

                if (warnCount > 3) {

                    guild.timeoutFor(UserSnowflake.fromId(message.getAuthor().getIdLong()),Duration.ofDays(28)).queue();

                }

                message.delete().queue();

            }


        }, failure -> {
            // Always good practice to handle API failures!
            log.error("Failed to retrieve invites",failure);
        });


    }


}
