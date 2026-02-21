package com.github.kyanbrix.component.command;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class MessageCommandMaker implements ICommand{
    @Override
    public void accept(MessageReceivedEvent event) {


        WebhookClientBuilder clientBuilder = new WebhookClientBuilder("https://discord.com/api/webhooks/1468371784654131358/SOHAVGoa2hQT9w__PcRjgS7b-EXOEnjtmymglXl3oFtNth7NmapZGEsUDXOZ2Gx4m-U6");
        clientBuilder.setThreadFactory((kian-> {

            Thread thread = new Thread(kian);

            thread.setDaemon(true);
            thread.setName("Test");
            return thread;
        }));


        clientBuilder.setWait(true);

        WebhookClient webhookClient = clientBuilder.build();

        webhookClient.send("TANGA").thenAccept(msg-> {

            System.out.println("Sent to dsicord "+ msg);
        });



    }

    @Override
    public String commandName() {
        return "create";
    }
}
