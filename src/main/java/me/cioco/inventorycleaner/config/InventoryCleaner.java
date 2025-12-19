package me.cioco.inventorycleaner.config;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
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

    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        loadConfiguration(DEFAULT_CONFIG_NAME);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggled && client.player != null) {
                cleanInventory(client);
            }
        });
    }

    public Set<Item> getItemsToThrow() {
        return itemsToThrow;
    }

    public Set<Integer> getLockedSlots() {
        return lockedSlots;
    }

    public void addItemToThrow(Item item) {
        itemsToThrow.add(item);
    }

    public void removeItemToThrow(Item item) {
        itemsToThrow.remove(item);
    }

    public boolean isItemInThrowList(Item item) {
        return itemsToThrow.contains(item);
    }

    public void lockSlot(int slotId) {
        lockedSlots.add(slotId);
    }

    public void unlockSlot(int slotId) {
        lockedSlots.remove(slotId);
    }

    public boolean isSlotLocked(int slotId) {
        return lockedSlots.contains(slotId);
    }

    public void setThrowDelayTicks(int ticks) {
        this.throwDelayTicks = Math.max(1, ticks);
    }

    public int getThrowDelayTicks() {
        return throwDelayTicks;
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
            properties.setProperty("delay", String.valueOf(throwDelayTicks));

            for (Item item : itemsToThrow) {
                properties.setProperty(Registries.ITEM.getId(item).toString(), "true");
            }

            for (Integer slot : lockedSlots) {
                properties.setProperty("lock_" + slot, "true");
            }

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

            if (properties.containsKey("toggled")) {
                toggled = Boolean.parseBoolean(properties.getProperty("toggled"));
            }

            if (properties.containsKey("delay")) {
                try {
                    throwDelayTicks = Integer.parseInt(properties.getProperty("delay"));
                } catch (NumberFormatException ignored) {
                }
            }

            for (String key : properties.stringPropertyNames()) {
                if (key.equals("toggled") || key.equals("delay")) continue;

                if (key.startsWith("lock_")) {
                    try {
                        lockedSlots.add(Integer.parseInt(key.substring(5)));
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    Identifier id = Identifier.of(key);
                    if (Registries.ITEM.containsId(id)) {
                        itemsToThrow.add(Registries.ITEM.get(id));
                    }
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
        if (++tickCounter % throwDelayTicks != 0) return;
        if (tickCounter > 100000) tickCounter = 0;

        PlayerScreenHandler handler = client.player.playerScreenHandler;

        for (Slot slot : handler.slots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            if (itemsToThrow.contains(stack.getItem())) {
                if (isSlotLocked(slot.id)) continue;

                client.interactionManager.clickSlot(
                        handler.syncId,
                        slot.id,
                        1,
                        SlotActionType.THROW,
                        client.player
                );
                break;
            }
        }
    }
}