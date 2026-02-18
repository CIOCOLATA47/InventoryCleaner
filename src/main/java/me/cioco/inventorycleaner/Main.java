package me.cioco.inventorycleaner;


import me.cioco.inventorycleaner.config.InventoryCleaner;
import me.cioco.inventorycleaner.gui.InventoryCleanerScreen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import static me.cioco.inventorycleaner.config.InventoryCleaner.toggled;

public class Main implements ModInitializer {

    public static final String MOD_ID = "inventorycleaner";
    public static final KeyBinding.Category CATEGORY_INVCLEANER = KeyBinding.Category.create(Identifier.of("inventorycleaner", "key_category"));
    public static KeyBinding keyBinding;
    public static KeyBinding guiKeyBinding;
    private InventoryCleaner inventoryCleaner;

    @Override
    public void onInitialize() {

        inventoryCleaner = new InventoryCleaner();
        inventoryCleaner.onInitializeClient();

        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + MOD_ID + ".toggle",
                InputUtil.UNKNOWN_KEY.getCode(),
                CATEGORY_INVCLEANER
        ));

        guiKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + MOD_ID + ".open_gui",
                InputUtil.UNKNOWN_KEY.getCode(),
                CATEGORY_INVCLEANER
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (keyBinding.wasPressed()) {
                toggled = !toggled;
                inventoryCleaner.saveConfiguration();
                client.player.sendMessage(
                        Text.literal("InventoryCleaner: ")
                                .append(Text.literal(toggled ? "Enabled" : "Disabled")
                                        .formatted(toggled ? Formatting.GREEN : Formatting.RED)),
                        false
                );
            }
            if (guiKeyBinding.wasPressed()) {
                client.setScreen(new InventoryCleanerScreen(client.currentScreen, inventoryCleaner));
            }
        });
    }
}
