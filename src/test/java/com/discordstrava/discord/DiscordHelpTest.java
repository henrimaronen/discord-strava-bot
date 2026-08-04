package com.discordstrava.discord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscordHelpTest {
    @Test
    void listsEverySupportedSlashCommand() {
        assertThat(DiscordHelp.message())
                .contains("/connect")
                .contains("/status")
                .contains("/unlink")
                .contains("/configure channel")
                .contains("/configure disable");
    }
}
