package com.github.kyanbrix.component.command;

import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class AddRole implements ICommand{
    private static final Logger log = LoggerFactory.getLogger(AddRole.class);

    @Override
    public void accept(MessageReceivedEvent event) {

    if (event.getAuthor().getIdLong() != Constant.KIAN_ID) return;

    Message message = event.getMessage();

    String command = removePrefixCommand(message.getContentRaw().toLowerCase());

    String[] splitCommand = command.split(" ");

    if (splitCommand.length == 1) return;

    Guild guild = event.getGuild();

    Member member = guild.getMemberById(splitCommand[0]);

    if (member == null) {
        log.error("Member is null, i cannot add a role");
        event.getChannel().sendMessage("That user is not found! Try to make sure the ID is correct").queue();
        return;
    }

    Role role = guild.getRoleById(splitCommand[1]);

    if (role == null) {
        event.getChannel().sendMessage("Role ID ``"+splitCommand[1]+"`` is not found!").queue();
        return;
    }

    message.delete().queue();

    guild.addRoleToMember(member,role).queue();

    MessageEmbed embed = new EmbedBuilder()
            .setColor(0x90EE90)
            .setDescription(String.format("Successfully added a %s role to %s",role.getAsMention(),member.getAsMention()))
            .build();

    event.getChannel().sendMessageEmbeds(embed).queue();



    }

    @Override
    public String commandName() {
        return "addrole";
    }

}
