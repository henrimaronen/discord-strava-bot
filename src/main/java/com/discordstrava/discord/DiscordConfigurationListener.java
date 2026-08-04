package com.discordstrava.discord;

import com.discordstrava.configuration.ChannelAccess;
import com.discordstrava.configuration.AnnouncementConfigurationRepository;
import com.discordstrava.configuration.ConfigureAnnouncementChannel;
import com.discordstrava.connection.ConnectStrava;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Thin JDA adapter; configuration rules live in {@link ConfigureAnnouncementChannel}. */
public final class DiscordConfigurationListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(DiscordConfigurationListener.class);
    private static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private static final String TIMEOUT_MESSAGE = "This request timed out. Please try again.";
    private static final String UNAVAILABLE_MESSAGE = "This request is temporarily unavailable. Please try again.";
    private final String discordGuildId;
    private final ConfigureAnnouncementChannel configuration;
    private final ConnectStrava connectStrava;
    private final AnnouncementConfigurationRepository announcementConfiguration;
    private final Executor commandExecutor;
    private final Duration commandTimeout;

    public DiscordConfigurationListener(String discordGuildId, ConfigureAnnouncementChannel configuration,
            ConnectStrava connectStrava, AnnouncementConfigurationRepository announcementConfiguration) {
        this(discordGuildId, configuration, connectStrava, announcementConfiguration, Runnable::run, DEFAULT_COMMAND_TIMEOUT);
    }

    public DiscordConfigurationListener(String discordGuildId, ConfigureAnnouncementChannel configuration,
            ConnectStrava connectStrava, AnnouncementConfigurationRepository announcementConfiguration,
            Executor commandExecutor) {
        this(discordGuildId, configuration, connectStrava, announcementConfiguration, commandExecutor,
                DEFAULT_COMMAND_TIMEOUT);
    }

    DiscordConfigurationListener(String discordGuildId, ConfigureAnnouncementChannel configuration,
            ConnectStrava connectStrava, AnnouncementConfigurationRepository announcementConfiguration,
            Executor commandExecutor, Duration commandTimeout) {
        this.discordGuildId = discordGuildId;
        this.configuration = configuration;
        this.connectStrava = connectStrava;
        this.announcementConfiguration = announcementConfiguration;
        this.commandExecutor = commandExecutor;
        this.commandTimeout = commandTimeout;
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
        guild.updateCommands().addCommands(
                Commands.slash("help", "Show available bot commands"),
                Commands.slash("connect", "Connect your Strava account"),
                Commands.slash("status", "Show your private Strava connection status"),
                Commands.slash("unlink", "Disconnect your Strava account"),
                Commands.slash("configure", "Configure Activity announcements")
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
        String command = event.getName();
        if (!"help".equals(command) && !"connect".equals(command) && !"status".equals(command)
                && !"unlink".equals(command) && !"configure".equals(command)) {
            return;
        }
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null || !discordGuildId.equals(guild.getId())) {
            event.reply("This command is only available in the configured Discord server.")
                    .setEphemeral(true).queue();
            return;
        }
        replyWhenComplete(event, command, () -> slashReply(event, guild, member));
    }

    private CommandReply slashReply(SlashCommandInteractionEvent event, Guild guild, Member member) {
        return switch (event.getName()) {
            case "help" -> CommandReply.message(DiscordHelp.message());
            case "connect" -> connect(guild, member);
            case "status" -> status(member);
            case "unlink" -> unlink(member);
            case "configure" -> configure(event, guild, member);
            default -> throw new IllegalArgumentException("Unknown command");
        };
    }

    private CommandReply configure(SlashCommandInteractionEvent event, Guild guild, Member member) {
        boolean canManageServer = member.hasPermission(Permission.MANAGE_SERVER);
        if ("disable".equals(event.getSubcommandName())) {
            return reply(configuration.disable(new ConfigureAnnouncementChannel.Request(guild.getId(), canManageServer, null)));
        }
        OptionMapping channelOption = event.getOption("channel");
        if (channelOption == null || !isAnnouncementChannel(channelOption)) {
            return CommandReply.message("Choose a channel in this Discord server.");
        }
        GuildMessageChannel channel = channelOption.getAsChannel().asGuildMessageChannel();
        ChannelAccess channelAccess = channelId -> guild.getSelfMember().hasPermission(
                channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS);
        return reply(configuration.configureChannel(new ConfigureAnnouncementChannel.Request(
                guild.getId(), canManageServer, channel.getId()), channelAccess));
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!"strava-unlink-confirm".equals(event.getComponentId())) return;
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null || !discordGuildId.equals(guild.getId())) {
            event.reply("This confirmation is no longer valid.").setEphemeral(true).queue();
            return;
        }
        replyWhenComplete(event, "unlink", () -> CommandReply.message(
                connectStrava.confirmUnlink(member.getId()) ? "Strava disconnected." : "This confirmation expired."));
    }

    private CommandReply connect(Guild guild, Member member) {
        ConnectStrava.StartResult result = connectStrava.start(new ConnectStrava.StartRequest(guild.getId(), member.getId()));
        if (result.result() != ConnectStrava.Result.LINK_READY) {
            return CommandReply.message("This command is only available in the configured Discord server.");
        }
        return CommandReply.withButton("Connect Strava privately.", Button.link(result.authorizationUrl(), "Connect Strava"));
    }

    private CommandReply status(Member member) {
        var status = connectStrava.status(member.getId());
        var channel = announcementConfiguration.get();
        String channelStatus = channel.enabled() ? "<#" + channel.channelId() + ">" : "disabled";
        String message = !status.connected() ? "Strava: not connected. Announcement channel: " + channelStatus
                : "Strava: " + status.athleteDisplayName() + ". Announcement channel: " + channelStatus
                        + ". " + (status.state() == com.discordstrava.connection.ConnectionState.RECONNECT_NEEDED
                                ? "Reconnect needed; use /connect." : "Connected.");
        return CommandReply.message(message);
    }

    private CommandReply unlink(Member member) {
        if (!connectStrava.requestUnlink(member.getId())) {
            return CommandReply.message("No Strava connection to unlink.");
        }
        return CommandReply.withButton("Confirm unlink within five minutes.", Button.danger("strava-unlink-confirm", "Unlink Strava"));
    }

    private boolean isAnnouncementChannel(OptionMapping channelOption) {
        return channelOption.getChannelType() == ChannelType.TEXT || channelOption.getChannelType() == ChannelType.NEWS;
    }

    private CommandReply reply(ConfigureAnnouncementChannel.Result result) {
        String message = switch (result) {
            case CONFIGURED -> "Announcement channel configured.";
            case DISABLED -> "Activity announcements disabled.";
            case NOT_AUTHORIZED -> "Manage Server permission is required.";
            case CHANNEL_UNAVAILABLE -> "I need View Channel, Send Messages, and Embed Links in that channel.";
            case WRONG_DISCORD_SERVER -> "This command is only available in the configured Discord server.";
        };
        return CommandReply.message(message);
    }

    private void replyWhenComplete(IReplyCallback event, String command, Supplier<CommandReply> operation) {
        event.deferReply(true).queue(hook -> run(command, operation, hook));
    }

    private void run(String command, Supplier<CommandReply> operation, InteractionHook hook) {
        FutureTask<CommandReply> task = new FutureTask<>(operation::get) {
            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    complete(command, hook, get(), null);
                } catch (Exception failure) {
                    complete(command, hook, null, failure);
                }
            }
        };
        try {
            commandExecutor.execute(task);
        } catch (RejectedExecutionException exception) {
            logger.warn("Discord command {} rejected because the command worker is busy", command);
            hook.editOriginal(UNAVAILABLE_MESSAGE).queue();
            return;
        }
        java.util.concurrent.CompletableFuture.delayedExecutor(commandTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> {
                    if (task.cancel(true)) complete(command, hook, null, new TimeoutException());
                });
    }

    private void complete(String command, InteractionHook hook, CommandReply reply, Throwable failure) {
        if (failure == null) {
            if (reply.button() == null) {
                hook.editOriginal(reply.message()).queue();
            } else {
                hook.editOriginal(reply.message()).setActionRow(reply.button()).queue();
            }
            return;
        }
        if (failure instanceof TimeoutException) {
            logger.warn("Discord command {} timed out after {} ms", command, commandTimeout.toMillis());
            hook.editOriginal(TIMEOUT_MESSAGE).queue();
            return;
        }
        logger.warn("Discord command {} failed", command, failure);
        hook.editOriginal(UNAVAILABLE_MESSAGE).queue();
    }

    private record CommandReply(String message, Button button) {
        static CommandReply message(String message) { return new CommandReply(message, null); }
        static CommandReply withButton(String message, Button button) { return new CommandReply(message, button); }
    }
}
