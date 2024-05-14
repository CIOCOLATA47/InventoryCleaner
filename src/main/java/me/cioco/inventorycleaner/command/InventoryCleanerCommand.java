package me.cioco.inventorycleaner.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import me.cioco.inventorycleaner.config.InventoryCleaner;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class InventoryCleanerCommand {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static  PlayerEntity player = mc.player;
    private static InventoryCleaner inventoryCleaner;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, InventoryCleaner cleaner) {
        inventoryCleaner = cleaner;

        dispatcher.register(ClientCommandManager.literal("ic")
                .then(ClientCommandManager.literal("additem")
                        .then(ClientCommandManager.argument("item", IdentifierArgumentType.identifier())
                                .executes(InventoryCleanerCommand::addItem))));

        dispatcher.register(ClientCommandManager.literal("ic")
                .then(ClientCommandManager.literal("delitem")
                        .then(ClientCommandManager.argument("item", IdentifierArgumentType.identifier())
                                .executes(InventoryCleanerCommand::removeItem))));
    }

    private static int addItem(CommandContext<FabricClientCommandSource> context) {
        Identifier itemId = context.getArgument("item", Identifier.class);
        Item item = Registries.ITEM.get(itemId);
        if (item != Items.AIR) {
            if (!inventoryCleaner.isItemInThrowList(item)) {
                inventoryCleaner.addItemToThrow(item);
                inventoryCleaner.saveConfiguration();
                player.sendMessage(Text.of("Added item to InventoryCleaner: " + itemId), false);
            } else {
                player.sendMessage(Text.of("Item already added to InventoryCleaner: " + itemId), false);
            }
        } else {
            player.sendMessage(Text.of("Invalid item ID: " + itemId), false);
        }
        return 1;
    }

    private static int removeItem(CommandContext<FabricClientCommandSource> context) {
        Identifier itemId = context.getArgument("item", Identifier.class);
        Item item = Registries.ITEM.get(itemId);
        if (item != Items.AIR) {
            if (inventoryCleaner.isItemInThrowList(item)) {
                inventoryCleaner.removeItemToThrow(item);
                inventoryCleaner.saveConfiguration();
                player.sendMessage(Text.of("Removed item from InventoryCleaner: " + itemId), false);
            } else {
                player.sendMessage(Text.of("Item not found in InventoryCleaner: " + itemId), false);
            }
        } else {
            player.sendMessage(Text.of("Invalid item ID: " + itemId), false);
        }
        return 1;
    }
}
