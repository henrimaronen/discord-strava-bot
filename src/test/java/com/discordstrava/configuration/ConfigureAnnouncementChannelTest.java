package com.discordstrava.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigureAnnouncementChannelTest {
    private final FakeRepository repository = new FakeRepository();
    private final FakeChannelAccess channelAccess = new FakeChannelAccess();
    private final ConfigureAnnouncementChannel service = new ConfigureAnnouncementChannel(
            "123456789012345678", repository, channelAccess);

    @Test
    void rejectsConfigurationFromAnotherDiscordServer() {
        var result = service.configureChannel(new ConfigureAnnouncementChannel.Request(
                "987654321098765432", true, "222222222222222222"));

        assertThat(result).isEqualTo(ConfigureAnnouncementChannel.Result.WRONG_DISCORD_SERVER);
        assertThat(repository.configuration).isEqualTo(AnnouncementConfiguration.disabled());
    }

    @Test
    void requiresManageServerPermission() {
        var result = service.configureChannel(new ConfigureAnnouncementChannel.Request(
                "123456789012345678", false, "222222222222222222"));

        assertThat(result).isEqualTo(ConfigureAnnouncementChannel.Result.NOT_AUTHORIZED);
        assertThat(repository.configuration).isEqualTo(AnnouncementConfiguration.disabled());
    }

    @Test
    void replacesChannelOnlyWhenBotCanUseIt() {
        repository.configuration = AnnouncementConfiguration.enabled("111111111111111111");
        channelAccess.usableChannels.put("222222222222222222", true);

        var result = service.configureChannel(new ConfigureAnnouncementChannel.Request(
                "123456789012345678", true, "222222222222222222"));

        assertThat(result).isEqualTo(ConfigureAnnouncementChannel.Result.CONFIGURED);
        assertThat(repository.configuration).isEqualTo(AnnouncementConfiguration.enabled("222222222222222222"));
    }

    @Test
    void rejectsUnusableChannelWithoutReplacingExistingConfiguration() {
        repository.configuration = AnnouncementConfiguration.enabled("111111111111111111");

        var result = service.configureChannel(new ConfigureAnnouncementChannel.Request(
                "123456789012345678", true, "222222222222222222"));

        assertThat(result).isEqualTo(ConfigureAnnouncementChannel.Result.CHANNEL_UNAVAILABLE);
        assertThat(repository.configuration).isEqualTo(AnnouncementConfiguration.enabled("111111111111111111"));
    }

    @Test
    void disableStopsAnnouncementsWithoutChangingConnections() {
        repository.configuration = AnnouncementConfiguration.enabled("111111111111111111");

        var result = service.disable(new ConfigureAnnouncementChannel.Request(
                "123456789012345678", true, null));

        assertThat(result).isEqualTo(ConfigureAnnouncementChannel.Result.DISABLED);
        assertThat(repository.configuration).isEqualTo(AnnouncementConfiguration.disabled());
        assertThat(repository.connectionWrites).isZero();
    }

    private static final class FakeRepository implements AnnouncementConfigurationRepository {
        private AnnouncementConfiguration configuration = AnnouncementConfiguration.disabled();
        private int connectionWrites;

        @Override
        public AnnouncementConfiguration get() {
            return configuration;
        }

        @Override
        public void save(AnnouncementConfiguration configuration) {
            this.configuration = configuration;
        }
    }

    private static final class FakeChannelAccess implements ChannelAccess {
        private final Map<String, Boolean> usableChannels = new HashMap<>();

        @Override
        public boolean canPublishEmbeds(String channelId) {
            return usableChannels.getOrDefault(channelId, false);
        }
    }
}
