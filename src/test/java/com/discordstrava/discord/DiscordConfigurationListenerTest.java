package com.discordstrava.discord;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.discordstrava.configuration.AnnouncementConfiguration;
import com.discordstrava.configuration.AnnouncementConfigurationRepository;
import com.discordstrava.configuration.ConfigureAnnouncementChannel;
import com.discordstrava.connection.ConnectStrava;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DiscordConfigurationListenerTest {
    @Test
    @SuppressWarnings("unchecked")
    void replacesSlowStatusWithTimeoutAndCancelsTheWork() throws InterruptedException {
        String serverId = "123456789012345678";
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        ReplyCallbackAction initialReply = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        ConnectStrava connections = mock(ConnectStrava.class);
        AnnouncementConfigurationRepository announcements = mock(AnnouncementConfigurationRepository.class);
        CountDownLatch timedOut = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);

        when(event.getName()).thenReturn("status");
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(guild.getId()).thenReturn(serverId);
        when(member.getId()).thenReturn("234567890123456789");
        when(event.reply("Working…")).thenReturn(initialReply);
        when(initialReply.setEphemeral(true)).thenReturn(initialReply);
        doAnswer(invocation -> {
            ((java.util.function.Consumer<InteractionHook>) invocation.getArgument(0)).accept(hook);
            return null;
        }).when(initialReply).queue(any(java.util.function.Consumer.class));
        when(connections.status(anyString())).thenAnswer(invocation -> {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
            } catch (InterruptedException exception) {
                interrupted.countDown();
                throw exception;
            }
            return new ConnectStrava.Status(null, null);
        });
        when(hook.editOriginal("This request timed out. Please try again.")).thenAnswer(invocation -> {
            timedOut.countDown();
            return edit;
        });

        new DiscordConfigurationListener(serverId, mock(ConfigureAnnouncementChannel.class), connections, announcements,
                runnable -> new Thread(runnable).start(), Duration.ofMillis(10)).onSlashCommandInteraction(event);

        org.junit.jupiter.api.Assertions.assertTrue(timedOut.await(1, TimeUnit.SECONDS));
        org.junit.jupiter.api.Assertions.assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        verify(hook).editOriginal("This request timed out. Please try again.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void acknowledgesStatusBeforeReadingItsData() {
        String serverId = "123456789012345678";
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        ReplyCallbackAction initialReply = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        ConnectStrava connections = mock(ConnectStrava.class);
        AnnouncementConfigurationRepository announcements = mock(AnnouncementConfigurationRepository.class);

        when(event.getName()).thenReturn("status");
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(guild.getId()).thenReturn(serverId);
        when(member.getId()).thenReturn("234567890123456789");
        when(event.reply("Working…")).thenReturn(initialReply);
        when(initialReply.setEphemeral(true)).thenReturn(initialReply);
        doAnswer(invocation -> {
            ((java.util.function.Consumer<InteractionHook>) invocation.getArgument(0)).accept(hook);
            return null;
        }).when(initialReply).queue(any(java.util.function.Consumer.class));
        when(connections.status(anyString())).thenReturn(new ConnectStrava.Status(null, null));
        when(announcements.get()).thenReturn(AnnouncementConfiguration.disabled());
        when(hook.editOriginal(anyString())).thenReturn(edit);

        new DiscordConfigurationListener(serverId, mock(ConfigureAnnouncementChannel.class), connections, announcements,
                Runnable::run).onSlashCommandInteraction(event);

        var order = inOrder(initialReply, connections);
        order.verify(initialReply).queue(any(java.util.function.Consumer.class));
        order.verify(connections).status(member.getId());
        verify(hook).editOriginal("Strava: not connected. Announcement channel: disabled");
    }
}
