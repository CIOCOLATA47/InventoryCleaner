package me.cioco.inventorycleaner.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.cioco.inventorycleaner.config.InventoryCleaner;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import me.cioco.inventorycleaner.config.InventoryCleaner.CleaningMode;

import java.util.Set;
import java.util.stream.Collectors;

public class InventoryCleanerCommand {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static InventoryCleaner inventoryCleaner;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, InventoryCleaner cleaner) {
        inventoryCleaner = cleaner;

        var root = ClientCommandManager.literal("ic");

        root.then(ClientCommandManager.literal("additem")
                .then(ClientCommandManager.argument("item", IdentifierArgumentType.identifier())
                        .executes(InventoryCleanerCommand::addItem)));

        root.then(ClientCommandManager.literal("delitem")
                .then(ClientCommandManager.argument("item", IdentifierArgumentType.identifier())
                        .executes(InventoryCleanerCommand::removeItem)));

        root.then(ClientCommandManager.literal("lockslot")
                .then(ClientCommandManager.argument("slot", IntegerArgumentType.integer())
                        .executes(InventoryCleanerCommand::lockSlot)));

        root.then(ClientCommandManager.literal("unlockslot")
                .then(ClientCommandManager.argument("slot", IntegerArgumentType.integer())
                        .executes(InventoryCleanerCommand::unlockSlot)));

        root.then(ClientCommandManager.literal("list")
                .executes(InventoryCleanerCommand::listItems));

        root.then(ClientCommandManager.literal("config")
                .then(ClientCommandManager.literal("save")
                        .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                .executes(InventoryCleanerCommand::saveConfig)))
                .then(ClientCommandManager.literal("load")
                        .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                .executes(InventoryCleanerCommand::loadConfig))));

        root.then(ClientCommandManager.literal("delay")
                .then(ClientCommandManager.argument("seconds", DoubleArgumentType.doubleArg(0.01))
                        .executes(InventoryCleanerCommand::setDelay)));

        root.then(ClientCommandManager.literal("mode")
                .then(ClientCommandManager.literal("blacklist")
                        .executes(context -> setMode(context, CleaningMode.BLACKLIST)))
                .then(ClientCommandManager.literal("whitelist")
                        .executes(context -> setMode(context, CleaningMode.WHITELIST))));

        dispatcher.register(root);
    }

    private static int setDelay(CommandContext<FabricClientCommandSource> context) {
        double seconds = context.getArgument("seconds", Double.class);

        int ticks = Math.max(1, (int) Math.round(seconds * 20.0));

        inventoryCleaner.setThrowDelayTicks(ticks);
        inventoryCleaner.saveConfiguration();

        mc.player.sendMessage(
                Text.of("§aInventoryCleaner delay set to §f" + seconds + " §aseconds (§f" + ticks + " ticks§a)."),
                false
        );
        return 1;
    }

    private static int setMode(CommandContext<FabricClientCommandSource> context, CleaningMode mode) {
        inventoryCleaner.setMode(mode);
        inventoryCleaner.saveConfiguration();
        String color = (mode == CleaningMode.WHITELIST) ? "§b" : "§8";
        context.getSource().sendFeedback(Text.of("§7Mode set to: " + color + mode.name()));
        return 1;
    }

    private static int addItem(CommandContext<FabricClientCommandSource> context) {
        var player = mc.player;
        Identifier itemId = context.getArgument("item", Identifier.class);
        Item item = Registries.ITEM.get(itemId);

        if (item == null) {
            player.sendMessage(Text.of("§cInvalid item ID: " + itemId), false);
            return 0;
        }

        if (inventoryCleaner.isItemInThrowList(item)) {
            player.sendMessage(Text.of("§eAlready added: " + itemId), false);
        } else {
            inventoryCleaner.addItemToThrow(item);
            inventoryCleaner.saveConfiguration();
            player.sendMessage(Text.of("§aAdded: " + itemId), false);
        }
        return 1;
    }

    private static int removeItem(CommandContext<FabricClientCommandSource> context) {
        var player = mc.player;
        Identifier itemId = context.getArgument("item", Identifier.class);
        Item item = Registries.ITEM.get(itemId);

        if (item == null) {
            player.sendMessage(Text.of("§cInvalid item ID: " + itemId), false);
            return 0;
        }

        if (inventoryCleaner.isItemInThrowList(item)) {
            inventoryCleaner.removeItemToThrow(item);
            inventoryCleaner.saveConfiguration();
            player.sendMessage(Text.of("§aRemoved: " + itemId), false);
        } else {
            player.sendMessage(Text.of("§eItem not found: " + itemId), false);
        }
        return 1;
    }

    private static int lockSlot(CommandContext<FabricClientCommandSource> context) {
        int slotId = context.getArgument("slot", Integer.class);
        inventoryCleaner.lockSlot(slotId);
        mc.player.sendMessage(Text.of("§6Slot " + slotId + " locked."), false);
        inventoryCleaner.saveConfiguration();
        return 1;
    }

    private static int unlockSlot(CommandContext<FabricClientCommandSource> context) {
        int slotId = context.getArgument("slot", Integer.class);
        inventoryCleaner.unlockSlot(slotId);
        mc.player.sendMessage(Text.of("§eSlot " + slotId + " unlocked."), false);
        inventoryCleaner.saveConfiguration();
        return 1;
    }

    private static int listItems(CommandContext<FabricClientCommandSource> context) {
        var player = mc.player;
        Set<Item> items = inventoryCleaner.getItemsToThrow();
        Set<Integer> locked = inventoryCleaner.getLockedSlots();

        if (items.isEmpty() && locked.isEmpty()) {
            player.sendMessage(Text.of("§7No items or locked slots configured."), false);
            return 1;
        }

        if (!items.isEmpty()) {
            String itemList = items.stream()
                    .map(i -> Registries.ITEM.getId(i).toString())
                    .collect(Collectors.joining(", "));
            player.sendMessage(Text.of("§aThrow List: §f" + itemList), false);
        }

        if (!locked.isEmpty()) {
            String lockedList = locked.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            player.sendMessage(Text.of("§6Locked Slots: §f" + lockedList), false);
        }

        return 1;
    }

    private static int saveConfig(CommandContext<FabricClientCommandSource> context) {
        String name = context.getArgument("name", String.class);
        inventoryCleaner.saveConfiguration(name);
        mc.player.sendMessage(Text.of("§aSaved configuration as '" + name + "'."), false);
        return 1;
    }

    private static int loadConfig(CommandContext<FabricClientCommandSource> context) {
        String name = context.getArgument("name", String.class);
        boolean success = inventoryCleaner.loadConfiguration(name);
        if (success) {
            mc.player.sendMessage(Text.of("§aLoaded configuration '" + name + "'."), false);
        } else {
            mc.player.sendMessage(Text.of("§cCould not find configuration '" + name + "'."), false);
        }
        return 1;
    }
}