package me.cioco.inventorycleaner.config;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class InventoryCleaner implements ClientModInitializer {

    private static final String DEFAULT_CONFIG_NAME = "default";
    public static boolean toggled = false;
    private final Set<Item> itemsToThrow = new HashSet<>();
    private final Set<Integer> lockedSlots = new HashSet<>();
    private int throwDelayTicks = 20;
    private boolean autoOpen = false;
    private boolean inventoryOpenOnly = false;
    private CleaningMode mode = CleaningMode.BLACKLIST;

    private int tickCounter = 0;

    private boolean weOpenedInventory = false;

    @Override
    public void onInitializeClient() {
        loadConfiguration(DEFAULT_CONFIG_NAME);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggled && client.player != null) {
                cleanInventory(client);
            }
        });
    }

    public CleaningMode getMode() {
        return mode;
    }

    public void setMode(CleaningMode mode) {
        this.mode = mode;
    }

    public Set<Item> getItemsToThrow() {
        return itemsToThrow;
    }

    public Set<Integer> getLockedSlots() {
        return lockedSlots;
    }

    public boolean isSlotLocked(int slotId) {
        return lockedSlots.contains(slotId);
    }

    public int getThrowDelayTicks() {
        return throwDelayTicks;
    }

    public void setThrowDelayTicks(int ticks) {
        this.throwDelayTicks = Math.max(1, ticks);
    }

    public boolean isInventoryOpenOnly() {
        return inventoryOpenOnly;
    }

    public void setInventoryOpenOnly(boolean flag) {
        this.inventoryOpenOnly = flag;
    }

    public void saveConfiguration() {
        saveConfiguration(DEFAULT_CONFIG_NAME);
    }

    public void saveConfiguration(String name) {
        try {
            Path configDir = getConfigDir();
            Files.createDirectories(configDir);
            Path configPath = configDir.resolve(name + ".properties");

            Properties properties = new Properties();
            properties.setProperty("toggled", Boolean.toString(toggled));
            properties.setProperty("autoopen", String.valueOf(autoOpen));
            properties.setProperty("inventoryOpenOnly", String.valueOf(inventoryOpenOnly));
            properties.setProperty("delay", String.valueOf(throwDelayTicks));
            properties.setProperty("mode", mode.name());

            for (Item item : itemsToThrow)
                properties.setProperty(Registries.ITEM.getId(item).toString(), "true");

            for (Integer slot : lockedSlots)
                properties.setProperty("lock_" + slot, "true");

            try (OutputStream output = Files.newOutputStream(configPath)) {
                properties.store(output, "InventoryCleaner configuration: " + name);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean loadConfiguration(String name) {
        Path configPath = getConfigDir().resolve(name + ".properties");
        if (!Files.exists(configPath)) return false;

        itemsToThrow.clear();
        lockedSlots.clear();

        try (InputStream input = Files.newInputStream(configPath)) {
            Properties properties = new Properties();
            properties.load(input);

            if (properties.containsKey("toggled"))
                toggled = Boolean.parseBoolean(properties.getProperty("toggled"));

            if (properties.containsKey("autoopen"))
                this.autoOpen = Boolean.parseBoolean(properties.getProperty("autoopen"));

            if (properties.containsKey("inventoryOpenOnly"))
                this.inventoryOpenOnly = Boolean.parseBoolean(properties.getProperty("inventoryOpenOnly"));

            if (properties.containsKey("delay")) {
                try {
                    throwDelayTicks = Integer.parseInt(properties.getProperty("delay"));
                } catch (NumberFormatException ignored) {
                }
            }

            if (properties.containsKey("mode")) {
                try {
                    this.mode = CleaningMode.valueOf(properties.getProperty("mode").toUpperCase());
                } catch (IllegalArgumentException e) {
                    this.mode = CleaningMode.BLACKLIST;
                }
            }

            for (String key : properties.stringPropertyNames()) {
                if (key.equals("toggled") || key.equals("delay") || key.equals("mode")
                        || key.equals("autoopen") || key.equals("inventoryOpenOnly")) continue;

                if (key.startsWith("lock_")) {
                    try {
                        lockedSlots.add(Integer.parseInt(key.substring(5)));
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    Identifier id = Identifier.of(key);
                    if (Registries.ITEM.containsId(id))
                        itemsToThrow.add(Registries.ITEM.get(id));
                }
            }
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("inventory-cleaner");
    }

    private void cleanInventory(MinecraftClient client) {
        if (client.player == null) return;
        if (++tickCounter % throwDelayTicks != 0) return;

        PlayerScreenHandler handler = client.player.playerScreenHandler;
        Slot targetSlot = findThrowableSlot(handler);

        if (targetSlot == null) {
            maybeCloseInventory(client);
            return;
        }

        boolean inventoryWasOpen = client.currentScreen instanceof InventoryScreen;
        if (!inventoryWasOpen) {
            client.setScreen(new InventoryScreen(client.player));
            weOpenedInventory = true;
        }

        client.interactionManager.clickSlot(
                handler.syncId, targetSlot.id, 1, SlotActionType.THROW, client.player
        );

        if (weOpenedInventory) {
            Slot next = findThrowableSlot(handler);
            if (next == null) {
                maybeCloseInventory(client);
            }
        }
    }

    private Slot findThrowableSlot(PlayerScreenHandler handler) {
        for (int i = 9; i <= 44; i++) {
            Slot slot = handler.getSlot(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            int vanillaSlot = (slot.id >= 36) ? slot.id - 36 : slot.id;
            if (isSlotLocked(vanillaSlot)) continue;

            boolean isInList = itemsToThrow.contains(stack.getItem());
            boolean shouldThrow = (mode == CleaningMode.BLACKLIST) == isInList;

            if (shouldThrow) return slot;
        }
        return null;
    }

    private void maybeCloseInventory(MinecraftClient client) {
        if (weOpenedInventory && client.currentScreen instanceof InventoryScreen) {
            client.setScreen(null);
        }
        weOpenedInventory = false;
    }

    public enum CleaningMode {BLACKLIST, WHITELIST}
}