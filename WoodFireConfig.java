package com.woodfire;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("woodfire")
public interface WoodFireConfig extends Config {

    enum TreeType {
        NORMAL,
        OAK,
        WILLOW,
        MAPLE,
        YEW,
        MAGIC
    }

    @ConfigItem(
            keyName = "enabled",
            name = "Enable Plugin",
            description = "Toggle the plugin on/off",
            position = 1
    )
    default boolean enabled() {
        return false;
    }

    @ConfigItem(
            keyName = "treeType",
            name = "Tree Type",
            description = "Select which type of tree to chop",
            position = 2
    )
    default TreeType treeType() {
        return TreeType.NORMAL;
    }

    @ConfigItem(
            keyName = "burnFirst",
            name = "Burn Before Chopping",
            description = "Burn existing logs before chopping new ones",
            position = 3
    )
    default boolean burnFirst() {
        return false;
    }

    @ConfigItem(
            keyName = "maxDelay",
            name = "Max Delay (ticks)",
            description = "Maximum random delay between actions (1-5 ticks recommended)",
            position = 4
    )
    @Range(min = 1, max = 10)
    default int maxDelay() {
        return 3;
    }

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show Overlay",
            description = "Display statistics overlay",
            position = 5
    )
    default boolean showOverlay() {
        return true;
    }

    // ========== ANTI-BAN SETTINGS ==========

    @ConfigItem(
            keyName = "enableMissClicks",
            name = "Enable Miss-Clicks",
            description = "Occasionally miss-click for realism (5% chance)",
            position = 10
    )
    default boolean enableMissClicks() {
        return true;
    }

    @ConfigItem(
            keyName = "enableCameraRotation",
            name = "Rotate Camera",
            description = "Randomly rotate camera periodically",
            position = 11
    )
    default boolean enableCameraRotation() {
        return true;
    }

    @ConfigItem(
            keyName = "enableAfkBreaks",
            name = "Enable AFK Periods",
            description = "Randomly pause for 5-30 seconds",
            position = 12
    )
    default boolean enableAfkBreaks() {
        return true;
    }

    @ConfigItem(
            keyName = "enableFatigue",
            name = "Simulate Fatigue",
            description = "Gradually slow down over time",
            position = 13
    )
    default boolean enableFatigue() {
        return true;
    }

    @ConfigItem(
            keyName = "useGaussianDelays",
            name = "Human-Like Delays",
            description = "Use Gaussian distribution for more realistic timing",
            position = 14
    )
    default boolean useGaussianDelays() {
        return true;
    }

    @ConfigItem(
            keyName = "minAfkSeconds",
            name = "Min AFK (seconds)",
            description = "Minimum AFK duration",
            position = 15
    )
    @Range(min = 5, max = 60)
    default int minAfkSeconds() {
        return 5;
    }

    @ConfigItem(
            keyName = "maxAfkSeconds",
            name = "Max AFK (seconds)",
            description = "Maximum AFK duration",
            position = 16
    )
    @Range(min = 10, max = 120)
    default int maxAfkSeconds() {
        return 30;
    }

    @ConfigItem(
            keyName = "afkFrequency",
            name = "AFK Frequency (mins)",
            description = "How often to AFK (on average)",
            position = 17
    )
    @Range(min = 5, max = 60)
    default int afkFrequency() {
        return 15;
    }

    @ConfigItem(
            keyName = "cameraRotateFrequency",
            name = "Camera Rotate Frequency",
            description = "Rotate camera every N ticks (avg)",
            position = 18
    )
    @Range(min = 50, max = 300)
    default int cameraRotateFrequency() {
        return 100;
    }

    @ConfigItem(
            keyName = "enableActionHesitation",
            name = "Enable Hesitation",
            description = "Occasionally pause before actions",
            position = 19
    )
    default boolean enableActionHesitation() {
        return true;
    }

    @ConfigItem(
            keyName = "enableInventoryChecks",
            name = "Random Inventory Checks",
            description = "Occasionally open inventory/stats tab",
            position = 20
    )
    default boolean enableInventoryChecks() {
        return true;
    }
}