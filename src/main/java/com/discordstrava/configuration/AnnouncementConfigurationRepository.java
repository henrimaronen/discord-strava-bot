package com.discordstrava.configuration;

public interface AnnouncementConfigurationRepository {
    AnnouncementConfiguration get();

    void save(AnnouncementConfiguration configuration);
}
