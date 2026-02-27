package com.github.kyanbrix.component.button;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.List;

public class FemaleButton implements IButton {
    private static final long FEMALE_ROLE_ID = 1474704395370369206L;

    @Override
    public String buttonId() {
        return "she";
    }

    @Override
    public void accept(ButtonInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();

        if (member == null || guild == null) {
            return;
        }

        Role femaleRole = guild.getRoleById(FEMALE_ROLE_ID);
        if (femaleRole == null) {
            sendReplyEphemeral(event, "Cannot update your profile because the role is missing in this server.");
            return;
        }

        List<Role> memberRoles = member.getRoles();
        if (memberRoles.contains(femaleRole)) {
            removeRole(member, femaleRole);
            sendReplyEphemeral(event, "I removed " + femaleRole.getAsMention() + " from your profile");
            return;
        }

        guild.addRoleToMember(member, femaleRole).queue();
        sendReplyEphemeral(event, "I added " + femaleRole.getAsMention() + " to your profile");
    }

    private void removeRole(Member member, Role role) {
        member.getGuild().removeRoleFromMember(member, role).queue();
    }
}
