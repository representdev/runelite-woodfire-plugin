package com.woodfire;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;

public class WoodFireOverlay extends Overlay {

    private final Client client;
    private final WoodFirePlugin plugin;
    private final WoodFireConfig config;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public WoodFireOverlay(Client client, WoodFirePlugin plugin, WoodFireConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay() || !config.enabled()) {
            return null;
        }

        panelComponent.getChildren().clear();

        // Title
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Wood & Fire")
                .color(Color.GREEN)
                .build());

        // State
        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(plugin.getState().toString())
                .build());

        // Statistics
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Logs Chopped:")
                .right(String.valueOf(plugin.getLogsChopped()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Logs Burned:")
                .right(String.valueOf(plugin.getLogsBurned()))
                .build());

        // Runtime
        long runtime = plugin.getRuntime();
        Duration duration = Duration.ofMillis(runtime);
        String timeStr = String.format("%02d:%02d:%02d",
                duration.toHours(),
                duration.toMinutesPart(),
                duration.toSecondsPart());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Runtime:")
                .right(timeStr)
                .build());

        // Calculate rates if runtime > 0
        if (runtime > 60000) { // At least 1 minute
            int logsPerHour = (int) ((plugin.getLogsChopped() * 3600000L) / runtime);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Logs/Hour:")
                    .right(String.valueOf(logsPerHour))
                    .build());
        }

        // Anti-ban statistics (if enabled)
        if (plugin.getAntiBanHandler() != null) {
            AntiBanHandler.AntiBanStats stats = plugin.getAntiBanHandler().getStats();

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("")
                    .right("")
                    .build());

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Anti-Ban")
                    .color(Color.ORANGE)
                    .build());

            if (stats.currentlyAfk) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Status:")
                        .right("AFK")
                        .rightColor(Color.RED)
                        .build());
            }

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Miss-Clicks:")
                    .right(String.valueOf(stats.totalMissClicks))
                    .build());

            if (stats.fatigueMultiplier > 1.0) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Fatigue:")
                        .right(String.format("%.2fx", stats.fatigueMultiplier))
                        .rightColor(Color.YELLOW)
                        .build());
            }

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Session:")
                    .right(stats.minutesPlayed + " min")
                    .build());
        }

        return panelComponent.render(graphics);
    }
}