package com.discordstrava.discord;

import com.discordstrava.configuration.ChannelAccess;
import com.discordstrava.configuration.ConfigureAnnouncementChannel;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/** Thin JDA adapter; configuration rules live in {@link ConfigureAnnouncementChannel}. */
public final class DiscordConfigurationListener extends ListenerAdapter {
    private final String discordGuildId;
    private final ConfigureAnnouncementChannel configuration;

    public DiscordConfigurationListener(String discordGuildId, ConfigureAnnouncementChannel configuration) {
        this.discordGuildId = discordGuildId;
        this.configuration = configuration;
    }

    @Override
    public void onReady(ReadyEvent event) {
        registerCommands(event.getJDA());
    }

    public void registerCommands(JDA jda) {
        Guild guild = jda.getGuildById(discordGuildId);
        if (guild == null) {
            return;
        }
        guild.upsertCommand(Commands.slash("configure", "Configure Activity announcements")
                        .addSubcommands(
                                        new net.dv8tion.jda.api.interactions.commands.build.SubcommandData(
                                                "channel", "Set the Announcement channel")
                                        .addOptions(new net.dv8tion.jda.api.interactions.commands.build.OptionData(
                                                OptionType.CHANNEL, "channel", "Announcement channel", true)
                                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)),
                                new net.dv8tion.jda.api.interactions.commands.build.SubcommandData(
                                        "disable", "Disable Activity announcements")))
                .queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"configure".equals(event.getName())) {
            return;
        }
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null || !discordGuildId.equals(guild.getId())) {
            event.reply("This command is only available in the configured Discord server.")
                    .setEphemeral(true).queue();
            return;
        }
        boolean canManageServer = member.hasPermission(Permission.MANAGE_SERVER);
        if ("disable".equals(event.getSubcommandName())) {
            reply(event, configuration.disable(new ConfigureAnnouncementChannel.Request(
                    guild.getId(), canManageServer, null)));
            return;
        }
        OptionMapping channelOption = event.getOption("channel");
        if (channelOption == null || !isAnnouncementChannel(channelOption)) {
            event.reply("Choose a channel in this Discord server.").setEphemeral(true).queue();
            return;
        }
        GuildMessageChannel channel = channelOption.getAsChannel().asGuildMessageChannel();
        ChannelAccess channelAccess = channelId -> guild.getSelfMember().hasPermission(
                channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS);
        reply(event, configuration.configureChannel(new ConfigureAnnouncementChannel.Request(
                guild.getId(), canManageServer, channel.getId()), channelAccess));
    }

    private boolean isAnnouncementChannel(OptionMapping channelOption) {
        return channelOption.getChannelType() == ChannelType.TEXT || channelOption.getChannelType() == ChannelType.NEWS;
    }

    private void reply(SlashCommandInteractionEvent event, ConfigureAnnouncementChannel.Result result) {
        String message = switch (result) {
            case CONFIGURED -> "Announcement channel configured.";
            case DISABLED -> "Activity announcements disabled.";
            case NOT_AUTHORIZED -> "Manage Server permission is required.";
            case CHANNEL_UNAVAILABLE -> "I need View Channel, Send Messages, and Embed Links in that channel.";
            case WRONG_DISCORD_SERVER -> "This command is only available in the configured Discord server.";
        };
        event.reply(message).setEphemeral(true).queue();
    }
}
