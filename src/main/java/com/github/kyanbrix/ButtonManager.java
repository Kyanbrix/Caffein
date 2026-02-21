package com.github.kyanbrix;

import com.github.kyanbrix.component.button.FemaleButton;
import com.github.kyanbrix.component.button.GayButton;
import com.github.kyanbrix.component.button.IButton;
import com.github.kyanbrix.component.button.MaleButton;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ButtonManager extends ListenerAdapter {


    private final Map<String, IButton> buttons = new HashMap<>();


    public ButtonManager() {
        this.addButtons(new MaleButton());
        this.addButtons(new GayButton());
        this.addButtons(new FemaleButton());
    }


    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        final String id = event.getCustomId();

        IButton iButton = buttons.get(id);

        if (iButton == null) {
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
