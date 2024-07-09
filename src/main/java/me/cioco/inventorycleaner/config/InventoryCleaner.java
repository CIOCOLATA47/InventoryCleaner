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
    private static final String CONFIG_FILE = "inventory-cleaner.properties";
    private boolean isEnabled = true;
    private final Set<Item> itemsToThrow = new HashSet<>();

    @Override
    public void onInitializeClient() {
        loadConfiguration();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (isEnabled) {
                cleanInventory(client);
            }
        });
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

    private void loadConfiguration() {
        Path configPath = getConfigPath();
        if (!Files.exists(configPath)) {
            return;
        }
        try (InputStream input = Files.newInputStream(configPath)) {
            Properties properties = new Properties();
            properties.load(input);

            for (String itemIdentifier : properties.stringPropertyNames()) {
                Identifier itemId = Identifier.of(itemIdentifier);
                Item item = Registries.ITEM.get(itemId);
                itemsToThrow.add(item);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveConfiguration() {
        try {
            Path configPath = getConfigPath();
            Files.createDirectories(configPath.getParent());

            try (OutputStream output = Files.newOutputStream(configPath)) {
                Properties properties = new Properties();

                for (Item item : itemsToThrow) {
                    Identifier itemId = Registries.ITEM.getId(item);
                    properties.setProperty(itemId.toString(), "true");
                }

                properties.store(output, "InventoryCleaner config.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
    }

    private void cleanInventory(MinecraftClient client) {
        if (client.player != null && isEnabled) {
            PlayerScreenHandler screenHandler = client.player.playerScreenHandler;

            for (Slot slot : screenHandler.slots) {
                ItemStack itemStack = slot.getStack();

                if (!itemStack.isEmpty() && itemsToThrow.contains(itemStack.getItem())) {
                    client.interactionManager.clickSlot(screenHandler.syncId, slot.id, 1, SlotActionType.THROW, client.player);
                }
            }
        }
    }

    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }
}