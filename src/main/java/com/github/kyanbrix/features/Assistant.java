package com.github.kyanbrix.features;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.api.OpenAI.ChatSession;
import com.github.kyanbrix.api.OpenAI.SessionManager;
import com.github.kyanbrix.api.OpenAI.service.FileRenderService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

public class Assistant extends ListenerAdapter {


    private static final Logger log = LoggerFactory.getLogger(Assistant.class);
    private final SessionManager sessionManager = new  SessionManager();


    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {

        if (!event.isFromGuild() || event.getAuthor().isBot()) return;

        Message message = event.getMessage();

        String channel = event.getChannel().getId();

        List<User> mentions = message.getMentions().getUsers();


        if (!mentions.isEmpty()) {

            if (mentions.getFirst().getIdLong() == event.getJDA().getSelfUser().getIdLong()) {

                String removePing = message.getContentRaw().replace("<@1470149609073545377>","");

                ChatSession chatSession = sessionManager.getOrCreate(channel);

                EmbedBuilder builder = new EmbedBuilder();
                builder.setAuthor("AI ChatBot",null,"https://cdn3.emoji.gg/emojis/2660_Clyde_Bot.png");
                builder.setColor(0xCD853F);
                builder.setFooter("Requested by: "+ event.getAuthor().getEffectiveName(),event.getAuthor().getEffectiveAvatarUrl());
                builder.setTimestamp(Instant.now());


                StringBuilder fileTexts = new StringBuilder();

                if (!message.getAttachments().isEmpty()) {

                    for (Message.Attachment attachment : message.getAttachments()) {

                        if (attachment.isVideo()) continue;

                        if (attachment.isImage()) {

                            Caffein.getInstance().getExecutorService().submit(() -> {
                                event.getChannel().sendTyping().queue();


                                List<String> chunks = chatSession.chatWithMedia(removePing, List.of(message.getAttachments().stream().map(Message.Attachment::getUrl).toArray(String[]::new)));

                                if (chunks.size() == 1) {

                                    message.replyEmbeds(builder.setDescription(chunks.getFirst()).build()).queue();
                                    return;
                                }

                                int index = 0;

                                for (String chunk : chunks) {

                                    if (index == 0) message.replyEmbeds(builder.setDescription(chunk).build()).queue();
                                    else
                                        event.getChannel().sendMessageEmbeds(builder.setDescription(chunk).build()).queue();
                                    index++;
                                }
                            });


                        } else {

                            Caffein.getInstance().getExecutorService().submit(() -> {


                                String content = FileRenderService.readFile(attachment.getUrl(), attachment.getFileName());

                                fileTexts.append("\n\n-- File: ")
                                        .append(attachment.getFileName())
                                        .append("-- \n")
                                        .append(content);


                                String fullMessage = removePing;

                                if (!fileTexts.isEmpty()) {
                                    fullMessage += fileTexts.toString();
                                }

                                event.getChannel().sendTyping().queue();

                                List<String> chunks = chatSession.chatWithMedia(fullMessage, null);

                                for (String chunk : chunks) {
                                    message.replyEmbeds(builder.setDescription(chunk).build()).queue();
                                }
                            });

                        }

                    }





                }else {


                    Caffein.getInstance().getExecutorService().submit(() -> {
                        event.getChannel().sendTyping().queue();

                        List<String> chunks = chatSession.chatWithMedia(removePing, null);

                        if (chunks.size() == 1) {

                            message.replyEmbeds(builder.setDescription(chunks.getFirst()).build()).queue();
                            return;
                        }

                        int index = 0;

                        for (String chunk : chunks) {

                            if (index == 0) message.replyEmbeds(builder.setDescription(chunk).build()).queue();
                            else event.getChannel().sendMessageEmbeds(builder.setDescription(chunk).build()).queue();
                            index++;
                        }

                    });

                }











            }



        }










    }




}
