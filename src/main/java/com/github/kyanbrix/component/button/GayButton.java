package com.github.kyanbrix.component.button;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.List;

public class GayButton implements IButton{
    @Override
    public void accept(ButtonInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();


        if (member != null && guild != null) {
            List<Role> roleList = member.getRoles();

            Role role = guild.getRoleById(1474704414513303552L);

            if (role != null) {

                if (roleList.contains(role)) {

                    removeRole(member,guild);

                    sendReplyEphemeral(event,"I remove "+role.getAsMention()+" from your profile");


                } else {
                    guild.addRoleToMember(member,role).queue();
                    sendReplyEphemeral(event,"I added "+role.getAsMention()+" into your profile");
                }

            }

        }
    }

    @Override
    public String buttonId() {
        return "gay";
    }

    private void removeRole(Member member, Guild guild) {

        if (member != null && guild != null) {
            Role role = guild.getRoleById(1474704414513303552L); // edit potang ina

            if (role!=null) {
                guild.removeRoleFromMember(member,role).queue();

            }else System.out.println("Cannot remove a role because it is not present in the server!");


        }
    }
}
