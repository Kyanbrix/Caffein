package com.github.kyanbrix.component.command;

import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.w3c.dom.Text;

public class RoleSelectionCommand implements ICommand {
    @Override
    public void accept(MessageReceivedEvent event) {


        if (event.getAuthor().getIdLong() != Constant.KIAN_ID) return;
        Guild guild = event.getGuild();

        Container container = Container.of(

                TextDisplay.of("# **"+guild.getName()+"** Roles"),
                TextDisplay.of("Select your roles to show your interests, this helps keep the community organized and ensures you see content that matters to you."),

                Separator.createInvisible(Separator.Spacing.LARGE),
                Separator.createInvisible(Separator.Spacing.LARGE),


                Section.of(
                        Thumbnail.fromUrl("https://media.discordapp.net/attachments/1239853996663898184/1354168592630939718/equality.png?ex=699aaa07&is=69995887&hm=806e18758db68719231448e2628b8c97aad31ef1099e3281837d3c47683078e8&=&format=webp&quality=lossless"),
                        TextDisplay.of("## Gender"),
                        TextDisplay.of("Select your gender to help personalize your experience and roles in the community. This helps members connect and keeps interactions respectful and relevant. You can update your selection anytime.")
                ),

                Separator.createInvisible(Separator.Spacing.LARGE),

                ActionRow.of(Button.of(ButtonStyle.SUCCESS,"he","Male",Emoji.fromUnicode("U+2642")),
                        Button.of(ButtonStyle.SUCCESS,"she","Female",Emoji.fromUnicode("U+2640")),
                        Button.of(ButtonStyle.SUCCESS,"gay","LGBTQIA+",Emoji.fromUnicode("U+1F308"))),

                Separator.createDivider(Separator.Spacing.LARGE),

                Section.of(

                        Thumbnail.fromUrl("https://media.discordapp.net/attachments/1239853996663898184/1354168592396062863/birthday-cake.png?ex=699aaa07&is=69995887&hm=f51ffb8400a518f69f79aefadd99a435379d73193942d30f5cb4207c2b1570df&=&format=webp&quality=lossless"),
                        TextDisplay.of("## Age"),
                        TextDisplay.of("Select your age range to help us provide age-appropriate channels and content. This keeps the community safe and relevant for everyone. You can update your selection anytime.”")
                ),
                Separator.createInvisible(Separator.Spacing.LARGE),
                ActionRow.of(
                        StringSelectMenu.create("age")
                                .addOption("18 below","minor")
                                .addOption("18-21","teens")
                                .addOption("21+","tito")
                                .setPlaceholder("Select your age range")
                                .build()
                                .withUniqueId(1)),
                Separator.createDivider(Separator.Spacing.LARGE),

                Section.of(
                        Thumbnail.fromUrl("https://cdn3.emoji.gg/emojis/49086-console.png"),
                        TextDisplay.of("## Games"),
                        TextDisplay.of("Select your game roles to show the games you play and unlock related channels and activities. This helps you connect with other members who share the same interests and find teammates more easily.")
                ),
                Separator.createInvisible(Separator.Spacing.SMALL),

                ActionRow.of(
                        StringSelectMenu.create("games")

                                .addOption("Roblox","roblox",Emoji.fromFormatted("<:RobloxBiru:1475035117218173038>"))
                                .addOption("Mobile Legends","ml",Emoji.fromFormatted("<:mlbb:1475035227695878274>"))
                                .addOption("Dota 2","dota2",Emoji.fromFormatted("<:dota2:1475035303248134386>"))
                                .addOption("League Legends","lol",Emoji.fromFormatted("<:LOL_LOGO:1475035182414434335>"))
                                .addOption("Counter-Strike 2","cs2",Emoji.fromFormatted("<:emojigg_CS2:1475035360810635345>"))
                                .addOption("Minecraft","minecraft",Emoji.fromFormatted("<:minecraft:1475035431643906118>"))
                                .addOption("Valorant","val",Emoji.fromFormatted("<:Valorant:1475035499398824050>"))
                                .addOption("GTA 5","gta",Emoji.fromFormatted("<:gtav_icon:1475035570420846692>"))
                                .setPlaceholder("Select your games")
                                .setMaxValues(5)
                                .setUniqueId(3)
                                .build()),


                Separator.createDivider(Separator.Spacing.LARGE),
                Section.of(
                        Thumbnail.fromUrl("https://media2.giphy.com/media/v1.Y2lkPTc5MGI3NjExNGs3Z2Rkc2VrNjc5MDlyMHhiMG5qcTd5bjI5Mm5tdDVuNXAwM3RsYiZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/nuotlxgu78Ddzaq0HR/giphy.gif"),
                        TextDisplay.of("## Cafè Menu"),
                        TextDisplay.of("Choose your color role to customize your name color. This personalizes your appearance in the server. You can update it anytime.")
                ),

                Separator.createInvisible(Separator.Spacing.LARGE),
                ActionRow.of(StringSelectMenu.create("color")
                                .addOption("Espresso","esp",Emoji.fromFormatted("<:coffee:1474659875341598772>"))
                                .addOption("Caffè Latte","cl",Emoji.fromFormatted("<:coffee:1474659875341598772>"))
                                .addOption("Cappuccino","cap",Emoji.fromFormatted("<:coffee:1474659875341598772>"))
                                .addOption("Caramel Macchiato","cm",Emoji.fromFormatted("<:coffee:1474659875341598772>"))
                                .addOption("Vanilla Latte","vl",Emoji.fromFormatted("<:coffee:1474659875341598772>"))
                                .addOption("Mocha","mocha",Emoji.fromFormatted("<:coffee:1474659875341598772>"))
                                .addOption("Iced Coffee","ic",Emoji.fromFormatted("<:coffee:1474659875341598772>"))
                                .addOption("Matcha Latte","matchalatte",Emoji.fromFormatted("<:coffee:1474659875341598772>"))
                                .addOption("Strawberry Milk","sm",Emoji.fromFormatted("<:coffee:1474659875341598772>"))
                        .addOption("Chocolate Drink","cd",Emoji.fromFormatted("<:coffee:1474659875341598772>"))
                        .addOption("Blueberry Yogurt","by",Emoji.fromFormatted("<:coffee:1474659875341598772>"))
                        .addOption("Lemonade","lem",Emoji.fromFormatted("<:coffee:1474659875341598772>"))
                        .addOption("Milk Tea","mt",Emoji.fromFormatted("<:coffee:1474659875341598772>"))
                                .addOption("Peach Iced Tea","peach",Emoji.fromFormatted("<:coffee:1474659875341598772>"))
                                .addOption("Hazelnut Latte","hazel",Emoji.fromFormatted("<:coffee:1474659875341598772>"))
                                .setPlaceholder("Select a role")
                        .build()

                        .withUniqueId(2))

        );

        Container container1 = Container.of(

                Section.of(
                        Thumbnail.fromUrl("https://media2.giphy.com/media/v1.Y2lkPTc5MGI3NjExZGJoODB6cXg2eGN1ZXQzZGF0YmVnZm40ZWp4OGF1bnVneXp5a2d5MSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/Duzbk7OJGmNwwvB4i0/giphy.gif"),
                        TextDisplay.of("## Feedback"),
                        TextDisplay.of("Got ideas or suggestions to make the server even better? Drop your feedback here and let us know what you’d love to see added or improved. We’re always listening and appreciate your thoughts to help grow the community.")
                )

        );



        event.getChannel().sendMessageComponents(container).useComponentsV2().flatMap(message -> message.getChannel().sendMessageComponents(container1).useComponentsV2()).queue();



    }

    @Override
    public String commandName() {
        return "command";
    }


}
