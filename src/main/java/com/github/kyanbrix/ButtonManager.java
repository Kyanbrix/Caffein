package com.github.kyanbrix;

import com.github.kyanbrix.component.button.*;
import com.github.kyanbrix.component.slashcommand.ClassSkills;
import com.github.kyanbrix.component.slashcommand.CollectionChests;
import com.github.kyanbrix.component.slashcommand.LastFm;
import com.github.kyanbrix.component.slashcommand.QuestView;
import com.github.kyanbrix.component.slashcommand.Shop;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ButtonManager extends ListenerAdapter {


    private static final Logger log = LoggerFactory.getLogger(ButtonManager.class);
    private final Map<String, IButton> buttons = new HashMap<>();


    public ButtonManager() {
        this.addButtons(new MaleButton());
        this.addButtons(new GayButton());
        this.addButtons(new FemaleButton());
        this.addButtons(new Verify());
        this.addButtons(new CreateConfession());
        this.addButtons(new Ticket());
        this.addButtons(new DeleteAvatarButton());
    }


    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        final String id = event.getCustomId();
        IButton iButton = buttons.get(id);

        if (iButton == null) {

            String[] split = id.split(":");

           if (split[0].equals(LastFm.BUTTON_PREFIX)) {
               LastFm.handleButton(event);
               return;
           }

           // Pager from the old /top tracks command, which /fm top tracks replaced
           if (split[0].equals("toptracks")) {
               event.reply("This button has expired. Use `/fm top tracks` instead.").setEphemeral(true).queue();
               return;
           }

           if (split[0].equals(Shop.BUTTON_PREFIX)) {
               Shop.handleButton(event);
               return;
           }

           if (split[0].equals(ClassSkills.BUTTON_PREFIX)) {
               ClassSkills.handleButton(event);
               return;
           }

           if (split[0].equals(CollectionChests.BUTTON_PREFIX)) {
               CollectionChests.handleButton(event);
               return;
           }

           if (split[0].equals(QuestView.BUTTON_PREFIX)) {
               QuestView.handleButton(event);
               return;
           }

            event.reply("Error!").setEphemeral(true).queue();
            return;
        }

        iButton.accept(event);
    }


    private void addButtons(IButton iButton) {

        if (buttons.containsKey(iButton.buttonId())) throw new IllegalArgumentException("Duplicate ID");

        buttons.put(iButton.buttonId(),iButton);
    }

}
