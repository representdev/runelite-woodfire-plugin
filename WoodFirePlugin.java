package com.woodfire;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.Random;

@Slf4j
@PluginDescriptor(
        name = "Wood & Fire",
        description = "Automates woodcutting and firemaking",
        tags = {"woodcutting", "firemaking", "skilling"}
)
public class WoodFirePlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private WoodFireConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private WoodFireOverlay overlay;

    private AntiBanHandler antiBanHandler;
    private final Random random = new Random();
    private PluginState state = PluginState.IDLE;
    private int tickDelay = 0;
    private int logsChopped = 0;
    private int logsBurned = 0;
    private long startTime;

    private enum PluginState {
        IDLE,
        CHOPPING,
        WAITING_FOR_CHOP,
        FIREMAKING,
        WALKING_TO_TREE,
        WALKING_FOR_FIRE
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        antiBanHandler = new AntiBanHandler(client, config);
        startTime = System.currentTimeMillis();
        log.info("Wood & Fire plugin started");
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        state = PluginState.IDLE;
        log.info("Wood & Fire plugin stopped");
    }

    @Provides
    WoodFireConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(WoodFireConfig.class);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!config.enabled()) {
            state = PluginState.IDLE;
            return;
        }

        // Handle anti-ban behaviors first
        if (!antiBanHandler.onTick()) {
            // AFK or hesitating, don't do anything
            return;
        }

        // Handle tick delays for anti-pattern
        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        Player player = client.getLocalPlayer();
        if (player == null) {
            return;
        }

        // Main state machine
        switch (state) {
            case IDLE:
                handleIdleState(player);
                break;
            case CHOPPING:
                handleChoppingState(player);
                break;
            case WAITING_FOR_CHOP:
                handleWaitingState(player);
                break;
            case FIREMAKING:
                handleFiremakingState(player);
                break;
            case WALKING_TO_TREE:
                handleWalkingToTreeState(player);
                break;
            case WALKING_FOR_FIRE:
                handleWalkingForFireState(player);
                break;
        }
    }

    private void handleIdleState(Player player) {
        if (shouldFiremake()) {
            state = PluginState.FIREMAKING;
        } else if (shouldChop()) {
            GameObject tree = findNearestTree();
            if (tree != null) {
                state = PluginState.WALKING_TO_TREE;
            }
        }
    }

    private void handleChoppingState(Player player) {
        // Check if we're still animating (chopping)
        if (player.getAnimation() == -1 && !player.isMoving()) {
            // Animation stopped, check if inventory is full or we need to continue
            if (isInventoryFull()) {
                log.info("Inventory full, switching to firemaking");
                state = PluginState.FIREMAKING;
                tickDelay = getRandomDelayForScenario("inventory_full");
            } else {
                // Tree might have depleted, find another
                GameObject tree = findNearestTree();
                if (tree != null) {
                    clickTree(tree);
                    state = PluginState.WAITING_FOR_CHOP;
                    tickDelay = getRandomDelayForScenario("tree_depleted");
                }
            }
        }
    }

    private void handleWaitingState(Player player) {
        // Wait for animation to start
        if (player.getAnimation() != -1) {
            state = PluginState.CHOPPING;
        } else if (!player.isMoving()) {
            // Animation didn't start, might have clicked wrong spot
            state = PluginState.IDLE;
        }
    }

    private void handleFiremakingState(Player player) {
        if (!hasTinderbox()) {
            log.warn("No tinderbox found!");
            state = PluginState.IDLE;
            return;
        }

        if (getLogCount() == 0) {
            log.info("No logs left to burn");
            state = PluginState.IDLE;
            return;
        }

        // Check if we're already firemaking
        if (player.getAnimation() == 733) { // Firemaking animation
            return;
        }

        // If we're not moving and not animating, light a log
        if (!player.isMoving() && player.getAnimation() == -1) {
            lightLog();
            tickDelay = getRandomDelayForScenario("start_firemaking");
        }
    }

    private void handleWalkingToTreeState(Player player) {
        if (!player.isMoving()) {
            GameObject tree = findNearestTree();
            if (tree != null) {
                clickTree(tree);
                state = PluginState.WAITING_FOR_CHOP;
                tickDelay = getRandomDelay();
            } else {
                state = PluginState.IDLE;
            }
        }
    }

    private void handleWalkingForFireState(Player player) {
        if (!player.isMoving()) {
            state = PluginState.FIREMAKING;
        }
    }

    private boolean shouldFiremake() {
        return isInventoryFull() || (getLogCount() > 0 && config.burnFirst());
    }

    private boolean shouldChop() {
        return !isInventoryFull() && hasTinderbox();
    }

    private GameObject findNearestTree() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return null;
        }

        int[] treeIds = getTreeIdsForConfig();
        int searchRadius = 10;

        GameObject nearestTree = null;
        int nearestDistance = Integer.MAX_VALUE;

        for (GameObject gameObject : client.getGameObjects()) {
            if (gameObject == null) continue;

            for (int treeId : treeIds) {
                if (gameObject.getId() == treeId) {
                    LocalPoint localPoint = gameObject.getLocalLocation();
                    int distance = localPoint.distanceTo(player.getLocalLocation());

                    if (distance < nearestDistance && distance < searchRadius * 128) {
                        nearestTree = gameObject;
                        nearestDistance = distance;
                    }
                }
            }
        }

        return nearestTree;
    }

    private int[] getTreeIdsForConfig() {
        switch (config.treeType()) {
            case NORMAL:
                return new int[]{1276, 1277, 1278, 1279, 1280, 1282, 1283, 1284, 1285, 1286, 1289, 1290, 1291, 1315, 1316, 1318, 1319, 1330, 1331, 1332};
            case OAK:
                return new int[]{10820, 10821, 10822, 10823};
            case WILLOW:
                return new int[]{10829, 10831, 10833};
            case MAPLE:
                return new int[]{10832, 10833};
            case YEW:
                return new int[]{10822, 10823};
            case MAGIC:
                return new int[]{10834};
            default:
                return new int[]{1276}; // Default to normal tree
        }
    }

    private void clickTree(GameObject tree) {
        if (tree == null) return;

        // Check if we should hesitate before clicking
        if (antiBanHandler.shouldHesitate()) {
            log.debug("Hesitating before clicking tree");
            return;
        }

        // Check if we should miss-click
        if (antiBanHandler.shouldMissClick()) {
            WorldPoint missPoint = antiBanHandler.getMissClickPoint(tree.getWorldLocation());
            log.info("Miss-clicking near tree at: " + missPoint);

            client.invokeMenuAction(
                    "Walk here",
                    "",
                    0,
                    MenuAction.WALK.getId(),
                    missPoint.getX(),
                    missPoint.getY()
            );

            // Add delay before actual click
            tickDelay = antiBanHandler.getDelayForScenario("after_miss_click");
            return;
        }

        // Normal click with anti-ban delay check
        if (!antiBanHandler.canPerformAction()) {
            return;
        }

        log.info("Clicking tree at: " + tree.getLocalLocation());

        client.invokeMenuAction(
                "Chop down",
                tree.getName(),
                tree.getId(),
                MenuAction.GAME_OBJECT_FIRST_OPTION.getId(),
                tree.getSceneMinLocation().getX(),
                tree.getSceneMinLocation().getY()
        );

        logsChopped++;
    }

    private void lightLog() {
        Item tinderbox = getTinderbox();
        Item log = getLog();

        if (tinderbox == null || log == null) {
            return;
        }

        // Check if we should hesitate
        if (antiBanHandler.shouldHesitate()) {
            log.debug("Hesitating before lighting log");
            return;
        }

        // Check action timing
        if (!antiBanHandler.canPerformAction()) {
            return;
        }

        log.info("Lighting log");

        // Use tinderbox on log
        client.invokeMenuAction(
                "Use",
                "Tinderbox",
                tinderbox.getId(),
                MenuAction.WIDGET_TARGET.getId(),
                tinderbox.getSlot(),
                WidgetInfo.INVENTORY.getId()
        );

        // Then click log
        client.invokeMenuAction(
                "Use",
                log.getName(),
                log.getId(),
                MenuAction.WIDGET_TARGET_ON_WIDGET.getId(),
                log.getSlot(),
                WidgetInfo.INVENTORY.getId()
        );

        logsBurned++;
    }

    private boolean isInventoryFull() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) return false;

        int emptySlots = 0;
        for (Item item : inventory.getItems()) {
            if (item.getId() == -1 || item.getId() == 0) {
                emptySlots++;
            }
        }

        return emptySlots == 0;
    }

    private int getLogCount() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) return 0;

        int count = 0;
        int[] logIds = getLogIdsForConfig();

        for (Item item : inventory.getItems()) {
            for (int logId : logIds) {
                if (item.getId() == logId) {
                    count++;
                }
            }
        }

        return count;
    }

    private int[] getLogIdsForConfig() {
        switch (config.treeType()) {
            case NORMAL:
                return new int[]{1511}; // Logs
            case OAK:
                return new int[]{1521}; // Oak logs
            case WILLOW:
                return new int[]{1519}; // Willow logs
            case MAPLE:
                return new int[]{1517}; // Maple logs
            case YEW:
                return new int[]{1515}; // Yew logs
            case MAGIC:
                return new int[]{1513}; // Magic logs
            default:
                return new int[]{1511};
        }
    }

    private boolean hasTinderbox() {
        return getTinderbox() != null;
    }

    private Item getTinderbox() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) return null;

        for (Item item : inventory.getItems()) {
            if (item.getId() == 590) { // Tinderbox ID
                return item;
            }
        }

        return null;
    }

    private Item getLog() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) return null;

        int[] logIds = getLogIdsForConfig();

        for (Item item : inventory.getItems()) {
            for (int logId : logIds) {
                if (item.getId() == logId) {
                    return item;
                }
            }
        }

        return null;
    }

    private int getRandomDelay() {
        return antiBanHandler.getActionDelay();
    }

    private int getRandomDelayForScenario(String scenario) {
        return antiBanHandler.getDelayForScenario(scenario);
    }

    // Getters for overlay
    public PluginState getState() {
        return state;
    }

    public int getLogsChopped() {
        return logsChopped;
    }

    public int getLogsBurned() {
        return logsBurned;
    }

    public long getRuntime() {
        return System.currentTimeMillis() - startTime;
    }

    public AntiBanHandler getAntiBanHandler() {
        return antiBanHandler;
    }
}