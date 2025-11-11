package com.woodfire;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

import java.util.Random;

/**
 * Handles anti-ban behaviors to make the bot appear more human-like
 */
@Slf4j
public class AntiBanHandler {

    private final Client client;
    private final WoodFireConfig config;
    private final Random random;

    // AFK tracking
    private long nextAfkTime;
    private long afkEndTime;
    private boolean isAfk = false;

    // Camera tracking
    private int ticksSinceLastCameraMove = 0;
    private int nextCameraRotateTick;

    // Fatigue simulation
    private long sessionStartTime;
    private double fatigueMultiplier = 1.0;

    // Miss-click tracking
    private int missClickCounter = 0;
    private boolean justMissClicked = false;

    // Hesitation tracking
    private boolean isHesitating = false;
    private int hesitationTicksRemaining = 0;

    // Random checks
    private int ticksSinceLastCheck = 0;
    private int nextCheckTick;

    // Action timing
    private long lastActionTime = 0;
    private static final long MIN_ACTION_INTERVAL = 300; // ms

    public AntiBanHandler(Client client, WoodFireConfig config) {
        this.client = client;
        this.config = config;
        this.random = new Random();
        this.sessionStartTime = System.currentTimeMillis();

        initializeTimers();
    }

    private void initializeTimers() {
        scheduleNextAfk();
        scheduleNextCameraRotate();
        scheduleNextRandomCheck();
    }

    /**
     * Call this every game tick to handle anti-ban behaviors
     * @return true if bot should continue, false if in AFK/break
     */
    public boolean onTick() {
        // Handle AFK
        if (config.enableAfkBreaks()) {
            if (System.currentTimeMillis() >= nextAfkTime && !isAfk) {
                startAfk();
            }

            if (isAfk) {
                if (System.currentTimeMillis() >= afkEndTime) {
                    endAfk();
                } else {
                    return false; // Don't do anything while AFK
                }
            }
        }

        // Handle hesitation
        if (isHesitating) {
            hesitationTicksRemaining--;
            if (hesitationTicksRemaining <= 0) {
                isHesitating = false;
            }
            return false; // Don't act while hesitating
        }

        // Camera rotation
        if (config.enableCameraRotation()) {
            ticksSinceLastCameraMove++;
            if (ticksSinceLastCameraMove >= nextCameraRotateTick) {
                rotateCamera();
                ticksSinceLastCameraMove = 0;
                scheduleNextCameraRotate();
            }
        }

        // Random inventory/stats checks
        if (config.enableInventoryChecks()) {
            ticksSinceLastCheck++;
            if (ticksSinceLastCheck >= nextCheckTick) {
                performRandomCheck();
                ticksSinceLastCheck = 0;
                scheduleNextRandomCheck();
            }
        }

        // Update fatigue
        if (config.enableFatigue()) {
            updateFatigue();
        }

        return true;
    }

    /**
     * Get delay before next action (in ticks)
     */
    public int getActionDelay() {
        int baseDelay;

        if (config.useGaussianDelays()) {
            // Gaussian distribution: mean=2, stddev=1
            double gaussian = 2.0 + random.nextGaussian() * 1.0;
            baseDelay = Math.max(1, Math.min(config.maxDelay(), (int) gaussian));
        } else {
            // Uniform random
            baseDelay = 1 + random.nextInt(config.maxDelay());
        }

        // Apply fatigue multiplier
        if (config.enableFatigue()) {
            baseDelay = (int) (baseDelay * fatigueMultiplier);
        }

        // Add occasional longer pauses (10% chance)
        if (random.nextInt(100) < 10) {
            baseDelay += random.nextInt(3) + 1;
        }

        return baseDelay;
    }

    /**
     * Checks if we should miss-click this action
     */
    public boolean shouldMissClick() {
        if (!config.enableMissClicks()) {
            return false;
        }

        // Don't miss-click twice in a row
        if (justMissClicked) {
            justMissClicked = false;
            return false;
        }

        // 5% chance to miss-click
        if (random.nextInt(100) < 5) {
            justMissClicked = true;
            missClickCounter++;
            log.debug("Miss-clicking (total: {})", missClickCounter);
            return true;
        }

        return false;
    }

    /**
     * Get a nearby world point for miss-clicking
     */
    public WorldPoint getMissClickPoint(WorldPoint target) {
        int xOffset = random.nextInt(3) - 1; // -1, 0, or 1
        int yOffset = random.nextInt(3) - 1;

        return target.dx(xOffset).dy(yOffset);
    }

    /**
     * Checks if we should hesitate before this action
     */
    public boolean shouldHesitate() {
        if (!config.enableActionHesitation()) {
            return false;
        }

        // 8% chance to hesitate
        if (random.nextInt(100) < 8) {
            isHesitating = true;
            hesitationTicksRemaining = 1 + random.nextInt(3); // 1-3 ticks
            log.debug("Hesitating for {} ticks", hesitationTicksRemaining);
            return true;
        }

        return false;
    }

    /**
     * Check if enough time has passed since last action (anti-spam)
     */
    public boolean canPerformAction() {
        long now = System.currentTimeMillis();
        if (now - lastActionTime < MIN_ACTION_INTERVAL) {
            return false;
        }
        lastActionTime = now;
        return true;
    }

    /**
     * Get a random delay with more variance for specific scenarios
     */
    public int getDelayForScenario(String scenario) {
        int baseDelay = getActionDelay();

        switch (scenario) {
            case "tree_depleted":
                // Longer delay when tree depletes (confusion simulation)
                return baseDelay + random.nextInt(3) + 1;

            case "inventory_full":
                // Slight delay when inventory fills up
                return baseDelay + random.nextInt(2);

            case "start_firemaking":
                // Delay before starting to burn logs
                return baseDelay + random.nextInt(2);

            case "after_miss_click":
                // Shorter delay after miss-click (realization)
                return Math.max(1, baseDelay - 1);

            default:
                return baseDelay;
        }
    }

    private void startAfk() {
        isAfk = true;
        int afkDuration = config.minAfkSeconds() +
                random.nextInt(config.maxAfkSeconds() - config.minAfkSeconds());
        afkEndTime = System.currentTimeMillis() + (afkDuration * 1000L);

        log.info("Starting AFK for {} seconds", afkDuration);
    }

    private void endAfk() {
        isAfk = false;
        log.info("AFK ended, resuming");
        scheduleNextAfk();
    }

    private void scheduleNextAfk() {
        // Schedule next AFK based on frequency with ±30% variance
        int baseMinutes = config.afkFrequency();
        int variance = (int) (baseMinutes * 0.3);
        int minutesUntilAfk = baseMinutes - variance + random.nextInt(variance * 2);

        nextAfkTime = System.currentTimeMillis() + (minutesUntilAfk * 60 * 1000L);
        log.debug("Next AFK scheduled in {} minutes", minutesUntilAfk);
    }

    private void rotateCamera() {
        int currentYaw = client.getCameraYaw();
        int currentPitch = client.getCameraPitch();

        // Rotate yaw by ±200-500 units
        int yawChange = (random.nextBoolean() ? 1 : -1) * (200 + random.nextInt(300));
        int newYaw = currentYaw + yawChange;

        // Occasionally adjust pitch too (20% chance)
        int newPitch = currentPitch;
        if (random.nextInt(100) < 20) {
            int pitchChange = (random.nextBoolean() ? 1 : -1) * (50 + random.nextInt(100));
            newPitch = Math.max(128, Math.min(383, currentPitch + pitchChange));
        }

        // Set camera (this would need to be done gradually over multiple ticks for realism)
        // For now, we just log it
        log.debug("Rotating camera: yaw {} -> {}, pitch {} -> {}",
                currentYaw, newYaw, currentPitch, newPitch);

        // In practice, you'd use:
        // client.setCameraYawTarget(newYaw);
        // client.setCameraPitchTarget(newPitch);
    }

    private void scheduleNextCameraRotate() {
        int baseTicks = config.cameraRotateFrequency();
        int variance = (int) (baseTicks * 0.3);
        nextCameraRotateTick = baseTicks - variance + random.nextInt(variance * 2);
    }

    private void performRandomCheck() {
        int checkType = random.nextInt(3);

        switch (checkType) {
            case 0:
                log.debug("Checking inventory");
                // Could open inventory tab here
                break;
            case 1:
                log.debug("Checking stats");
                // Could open stats tab here
                break;
            case 2:
                log.debug("Checking equipment");
                // Could open equipment tab here
                break;
        }
    }

    private void scheduleNextRandomCheck() {
        // Check every 30-120 seconds
        nextCheckTick = (30 + random.nextInt(90)) * 100 / 60; // Convert seconds to ticks
    }

    private void updateFatigue() {
        long sessionDuration = System.currentTimeMillis() - sessionStartTime;
        long hoursPlayed = sessionDuration / (1000 * 60 * 60);

        // After 1 hour, start getting slower
        // Max slowdown of 1.5x at 3+ hours
        if (hoursPlayed >= 1) {
            fatigueMultiplier = Math.min(1.5, 1.0 + (hoursPlayed * 0.15));

            // Add occasional "attention lapses" - very long delays
            if (hoursPlayed >= 2 && random.nextInt(1000) < 5) {
                log.debug("Attention lapse! (fatigue)");
                isHesitating = true;
                hesitationTicksRemaining = 5 + random.nextInt(10);
            }
        }
    }

    /**
     * Gets statistics for display
     */
    public AntiBanStats getStats() {
        return new AntiBanStats(
                missClickCounter,
                isAfk,
                fatigueMultiplier,
                (int) ((System.currentTimeMillis() - sessionStartTime) / 1000 / 60)
        );
    }

    public boolean isAfk() {
        return isAfk;
    }

    public boolean isHesitating() {
        return isHesitating;
    }

    /**
     * Stats class for anti-ban information
     */
    public static class AntiBanStats {
        public final int totalMissClicks;
        public final boolean currentlyAfk;
        public final double fatigueMultiplier;
        public final int minutesPlayed;

        public AntiBanStats(int totalMissClicks, boolean currentlyAfk,
                            double fatigueMultiplier, int minutesPlayed) {
            this.totalMissClicks = totalMissClicks;
            this.currentlyAfk = currentlyAfk;
            this.fatigueMultiplier = fatigueMultiplier;
            this.minutesPlayed = minutesPlayed;
        }
    }
}