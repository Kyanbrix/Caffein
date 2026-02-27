package com.github.kyanbrix.component.button;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.List;

public class GayButton implements IButton {
    private static final long GAY_ROLE_ID = 1474704414513303552L;

    @Override
    public void accept(ButtonInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();

        if (member == null || guild == null) {
            return;
        }

        Role gayRole = guild.getRoleById(GAY_ROLE_ID);
        if (gayRole == null) {
            sendReplyEphemeral(event, "Cannot update your profile because the role is missing in this server.");
            return;
        }

        List<Role> memberRoles = member.getRoles();
        if (memberRoles.contains(gayRole)) {
            removeRole(member, gayRole);
            sendReplyEphemeral(event, "I removed " + gayRole.getAsMention() + " from your profile");
            return;
        }

        guild.addRoleToMember(member, gayRole).queue();
        sendReplyEphemeral(event, "I added " + gayRole.getAsMention() + " to your profile");
    }

    @Override
    public String buttonId() {
        return "gay";
    }

    private void removeRole(Member member, Role role) {
        member.getGuild().removeRoleFromMember(member, role).queue();
    }
}
