package com.github.kyanbrix.component;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;


public class ConfessionModal extends ListenerAdapter {

    public static final String CONFESSION_MODAL_ID = "confession";
    private static final Logger log = LoggerFactory.getLogger(ConfessionModal.class);

    private static final String NODE_BIN = System.getenv().getOrDefault("NODE_BIN", "node");
    private static final String NODE_SCRIPT_PATH = "render-confession.js";
    private static final int TIMEOUT_SECONDS = 20;


    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        String customId = event.getCustomId();

        Message message = event.getMessage();
        if (customId.equals(CONFESSION_MODAL_ID)) {

           String confession =  event.getValue("confess").getAsString();



           event.deferEdit().queue(interactionHook -> {

               interactionHook.editOriginalComponents().queue();
               try {

                   byte[] png = renderToPng(confession);

                   event.getChannel().sendFiles(FileUpload.fromData(png, "confession.png"))
                           .setComponents(
                                   ActionRow.of(Button.of(ButtonStyle.SUCCESS, "confess", "Create Confession"))
                           )
                           .queue();

                   event.getHook().sendMessage("Your confession has been created!").setEphemeral(true).queue();


               }catch (Exception e){
                   log.error(e.getMessage());
                   event.getHook().sendMessage(e.getMessage()).setEphemeral(true).queue();
               }
           });

        }
    }


    public byte[] renderToPng(String message) throws IOException, InterruptedException {
        Path tempOutput = Files.createTempFile("confession-", ".png");
        try {
            ProcessBuilder pb = new ProcessBuilder(NODE_BIN, NODE_SCRIPT_PATH,tempOutput.toString());
            pb.redirectErrorStream(false);
            Process process = pb.start();

            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(message.getBytes(StandardCharsets.UTF_8));
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Renderer timed out after " + TIMEOUT_SECONDS + "s");
            }

            if (process.exitValue() != 0) {
                String stderr = readAll(process.getErrorStream());
                throw new IOException("Renderer failed (exit " + process.exitValue() + "): " + stderr);
            }

            return Files.readAllBytes(tempOutput);
        } finally {
            Files.deleteIfExists(tempOutput);
        }
    }


    private String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        in.transferTo(buffer);
        return buffer.toString(StandardCharsets.UTF_8);
    }

}
