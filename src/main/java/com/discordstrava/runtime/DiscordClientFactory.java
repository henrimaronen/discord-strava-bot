package com.discordstrava.runtime;

import net.dv8tion.jda.api.JDA;

@FunctionalInterface
interface DiscordClientFactory {
    JDA start(String token);
}
