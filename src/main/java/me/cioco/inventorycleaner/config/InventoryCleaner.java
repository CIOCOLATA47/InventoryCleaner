package me.cioco.inventorycleaner.config;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
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
    private boolean isEnabled = true;
    private final Set<Item> itemsToThrow = new HashSet<>();
    private final Set<Integer> lockedSlots = new HashSet<>();

    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        loadConfiguration(DEFAULT_CONFIG_NAME);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (isEnabled && client.player != null) {
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

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
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

    public void saveConfiguration() {
        saveConfiguration(DEFAULT_CONFIG_NAME);
    }

    public void saveConfiguration(String name) {
        try {
            Path configDir = getConfigDir();
            Files.createDirectories(configDir);
            Path configPath = configDir.resolve(name + ".properties");

            Properties properties = new Properties();

            for (Item item : itemsToThrow) {
                Identifier itemId = Registries.ITEM.getId(item);
                properties.setProperty(itemId.toString(), "true");
            }
            for (Integer slotId : lockedSlots) {
                properties.setProperty("lock_" + slotId, "true");
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
        if (!Files.exists(configPath)) {
            return false;
        }

        itemsToThrow.clear();
        lockedSlots.clear();

        try (InputStream input = Files.newInputStream(configPath)) {
            Properties properties = new Properties();
            properties.load(input);

            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith("lock_")) {
                    try {
                        int slot = Integer.parseInt(key.substring(5));
                        lockedSlots.add(slot);
                    } catch (NumberFormatException ignored) {}
                } else {
                    Identifier itemId = Identifier.of(key);
                    if (Registries.ITEM.containsId(itemId)) {
                        itemsToThrow.add(Registries.ITEM.get(itemId));
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
        if (++tickCounter % 10 != 0) return;

        PlayerScreenHandler screenHandler = client.player.playerScreenHandler;

        for (Slot slot : screenHandler.slots) {
            if (slot.id < 9 || slot.id > 44) continue;
            if (isSlotLocked(slot.id)) continue;

            ItemStack itemStack = slot.getStack();
            if (!itemStack.isEmpty() && itemsToThrow.contains(itemStack.getItem())) {
                client.interactionManager.clickSlot(
                        screenHandler.syncId, slot.id, 1,
                        SlotActionType.THROW, client.player
                );
            }
        }
    }
}
