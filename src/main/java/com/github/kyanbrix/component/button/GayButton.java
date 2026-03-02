package com.github.kyanbrix.component.button;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class GayButton implements IButton{
    private static final Logger log = LoggerFactory.getLogger(GayButton.class);

    @Override
    public void accept(ButtonInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();

        if (member != null && guild != null) roleHandler(event,member,guild);

    }

    @Override
    public String buttonId() {
        return "gay";
    }

    private void roleHandler(ButtonInteractionEvent event,Member member, Guild guild) {

        List<Role> roleList = member.getRoles();

        Role femaleRole = guild.getRoleById(1474704395370369206L);
        Role maleRole = guild.getRoleById(1474704365733548134L);
        Role gayRole = guild.getRoleById(1474704414513303552L);

        if (femaleRole != null && maleRole != null && gayRole != null) {

            if (roleList.contains(gayRole)) {
                guild.removeRoleFromMember(member,gayRole).queue();
                sendReplyEphemeral(event,String.format("I remove your %s role from your profile",gayRole.getAsMention()));
            } else if (roleList.contains(maleRole)) {
                guild.removeRoleFromMember(member,maleRole).queue();
                guild.addRoleToMember(member,gayRole).queue();

                sendReplyEphemeral(event,String.format("I added %s and remove %s from your profile",gayRole.getAsMention(),maleRole.getAsMention()));

            } else if (roleList.contains(femaleRole)) {
                guild.removeRoleFromMember(member,femaleRole).queue();
                guild.addRoleToMember(member,gayRole).queue();

                sendReplyEphemeral(event,String.format("I added %s and remove %s from your profile",gayRole.getAsMention(),femaleRole.getAsMention()));

            }


        }else log.error("Role Handler error");

    }
}
