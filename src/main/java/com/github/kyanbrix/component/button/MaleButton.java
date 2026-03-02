package com.github.kyanbrix.component.button;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MaleButton implements IButton{
    private static final Logger log = LoggerFactory.getLogger(MaleButton.class);

    @Override
    public void accept(ButtonInteractionEvent event) {

        Member member = event.getMember();
        Guild guild = event.getGuild();

        if (member != null && guild != null) roleHandler(event,member,guild);
    }


    private void roleHandler(ButtonInteractionEvent event,Member member, Guild guild) {

        List<Role> roleList = member.getRoles();

        Role femaleRole = guild.getRoleById(1474704395370369206L);
        Role maleRole = guild.getRoleById(1474704365733548134L);
        Role gayRole = guild.getRoleById(1474704414513303552L);

        if (femaleRole != null && maleRole != null && gayRole != null) {

            if (roleList.contains(maleRole)) {
                guild.removeRoleFromMember(member,maleRole).queue();
                sendReplyEphemeral(event,String.format("I remove your %s role from your profile",maleRole.getAsMention()));
            } else if (roleList.contains(femaleRole)) {
                guild.removeRoleFromMember(member,femaleRole).queue();
                guild.addRoleToMember(member,maleRole).queue();

                sendReplyEphemeral(event,String.format("I added %s and remove %s from your profile",maleRole.getAsMention(),femaleRole.getAsMention()));

            } else if (roleList.contains(gayRole)) {
                guild.removeRoleFromMember(member,gayRole).queue();
                guild.addRoleToMember(member,maleRole).queue();

                sendReplyEphemeral(event,String.format("I added %s and remove %s from your profile",maleRole.getAsMention(),gayRole.getAsMention()));

            }


        }else log.error("Error in role handler method");

    }

    @Override
    public String buttonId() {
        return "he";
    }
}
