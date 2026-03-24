package me.cioco.inventorycleaner;

import me.cioco.inventorycleaner.config.InventoryCleaner;
import me.cioco.inventorycleaner.gui.InventoryCleanerScreen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import static me.cioco.inventorycleaner.config.InventoryCleaner.toggled;

public class Main implements ModInitializer {

    public static final String MOD_ID = "inventorycleaner";
    public static final KeyMapping.Category CATEGORY_INVCLEANER = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("inventorycleaner", "key_category")
    );
    public static KeyMapping keyBinding;
    public static KeyMapping guiKeyBinding;
    private InventoryCleaner inventoryCleaner;

    @Override
    public void onInitialize() {

        inventoryCleaner = new InventoryCleaner();
        inventoryCleaner.onInitializeClient();

        keyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + MOD_ID + ".toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY_INVCLEANER
        ));

        guiKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + MOD_ID + ".open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY_INVCLEANER
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (keyBinding.consumeClick()) {
                toggled = !toggled;
                inventoryCleaner.saveConfiguration();
                client.player.sendSystemMessage(
                        Component.literal("InventoryCleaner: ")
                                .append(Component.literal(toggled ? "Enabled" : "Disabled")
                                        .withStyle(toggled ? ChatFormatting.GREEN : ChatFormatting.RED))
                );
            }
            if (guiKeyBinding.consumeClick()) {
                client.setScreen(new InventoryCleanerScreen(client.screen, inventoryCleaner));
            }
        });
    }
}