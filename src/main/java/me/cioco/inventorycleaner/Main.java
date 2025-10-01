package me.cioco.inventorycleaner;

import me.cioco.inventorycleaner.command.InventoryCleanerCommand;
import me.cioco.inventorycleaner.config.InventoryCleaner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class Main implements ModInitializer {
    public static final String MOD_ID = "inventorycleaner";
    public static KeyBinding keyBinding;
    public static boolean toggled = false;
    private InventoryCleaner inventoryCleaner;

    @Override
    public void onInitialize() {
        inventoryCleaner = new InventoryCleaner();
        inventoryCleaner.setEnabled(false);
        inventoryCleaner.onInitializeClient();
        addCommands();
        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + MOD_ID + ".toggle",
                InputUtil.UNKNOWN_KEY.getCode(),
                KeyBinding.Category.MISC
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != null && client.player != null) {
                if (client.world.getTime() == 1) {
                    toggled = false;
                    inventoryCleaner.setEnabled(false);
                    client.player.sendMessage(Text
                                    .literal("InventoryCleaner: ")
                                    .append(Text
                                            .literal("Disabled")
                                            .formatted(Formatting.RED)),
                            false);
                }

                if (keyBinding.wasPressed()) {
                    toggled = !toggled;
                    inventoryCleaner.setEnabled(toggled);
                    client.player.sendMessage(Text
                                    .literal("InventoryCleaner: ")
                                    .append(Text
                                            .literal(toggled ? "Enabled" : "Disabled")
                                            .formatted(toggled ? Formatting.GREEN : Formatting.RED)),
                            false);
                }
            }
        });
    }

    private void addCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> InventoryCleanerCommand.register(dispatcher, inventoryCleaner));
    }
}
