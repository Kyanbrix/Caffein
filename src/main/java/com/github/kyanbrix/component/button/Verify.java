package com.github.kyanbrix.component.button;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class Verify implements IButton{

    @Override
    public void accept(ButtonInteractionEvent event) {

        if (event.getMember().hasPermission(Permission.ADMINISTRATOR) || event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You are not allowed to use this!").setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        Member member = event.getMember();

        if ( guild == null ) return;
        Role role = guild.getRoleById(1472336089657114745L);

        if (role != null && member != null) {

            guild.addRoleToMember(member,role).queue();

            event.reply("You are now verified! Kindly read the server rules <1470147677546090728>.").setEphemeral(true).queue();

            try (WebhookClient client = WebhookClient.withUrl(System.getenv("WELCOME_WEBHOOK"))) {

                WebhookEmbed.EmbedAuthor embedAuthor = new WebhookEmbed.EmbedAuthor(member.getEffectiveName(),member.getUser().getEffectiveAvatarUrl(),null);

                WebhookEmbed webhookEmbed = new WebhookEmbedBuilder()
                        .setColor(0xF0E68C)
                        .setAuthor(embedAuthor)
                        .setDescription(String.format("%s has joined the server",member.getAsMention()))
                        .setTimestamp(Instant.now())
                        .build();

                WebhookMessage webhookMessage = new WebhookMessageBuilder()
                        .setUsername(guild.getName())
                        .setAvatarUrl(guild.getIconUrl())
                        .addEmbeds(webhookEmbed)
                        .build();

                client.send(webhookMessage);

            }


        }


    }

    @Override
    public String buttonId() {
        return "verify";
    }
}
