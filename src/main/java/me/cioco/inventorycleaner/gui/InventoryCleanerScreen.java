package me.cioco.inventorycleaner.gui;

import me.cioco.inventorycleaner.config.InventoryCleaner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InventoryCleanerScreen extends Screen {

    private static final int SPACING_Y      = 24;
    private static final int SECTION_MARGIN = 35;
    private static final int TITLE_HEIGHT   = 20;

    private static final int SLOT_SIZE        = 18;
    private static final int SLOT_COLS        = 9;

    private static final int ACCENT_COLOR     = 0xFFFF4444;
    private static final int PANEL_BORDER     = 0xFF880000;
    private static final int PANEL_BG         = 0x90100000;
    private static final int SLOT_BG          = 0xFF8B8B8B;
    private static final int SLOT_DARK        = 0xFF373737;
    private static final int SLOT_LIGHT       = 0xFF5B5B5B;
    private static final int SLOT_HOVER       = 0x80FFFFFF;
    private static final int SLOT_ACTIVE_ITEM = 0xCC4444AA;
    private static final int SLOT_ACTIVE_LOCK = 0xCCAA8800;

    private static final int LIST_ROW_H   = 18;
    private static final int LIST_ICON_SZ = 17;

    private static final int TAB_SETTINGS = 0;
    private static final int TAB_ITEMS    = 1;
    private static final int TAB_LOCKS    = 2;
    private static final int TAB_PROFILES = 3;

    private static final int MAX_VISIBLE_SUGGESTIONS = 5;
    private static final int SUGGESTION_H            = 12;

    private final Screen parent;
    private final InventoryCleaner config;

    private final List<AbstractWidget> scrollableWidgets = new ArrayList<>();
    private final List<String>         suggestions        = new ArrayList<>();

    private final List<ItemRowData>    itemRows    = new ArrayList<>();
    private final List<SlotRowData>    slotRows    = new ArrayList<>();
    private final List<ProfileRowData> profileRows = new ArrayList<>();

    private int suggestionScroll  = 0;
    private int currentTab        = TAB_SETTINGS;
    private int scrollOffset      = 0;
    private int maxScroll         = 0;
    private int contentHeight     = 0;

    private int itemsLabelBaseY    = 0;
    private int slotsLabelBaseY    = 0;
    private int profilesLabelBaseY = 0;

    private Button tabSettings, tabItems, tabLocks, tabProfiles, doneButton;
    private int    gridOriginX, gridOriginY;
    private String hoverTooltip = null;

    private EditBox itemSearchField;
    private String  itemSearchFeedback      = "";
    private int     itemSearchFeedbackTimer = 0;

    private EditBox profileNameField;
    private String  profileFeedback      = "";
    private int     profileFeedbackTimer = 0;

    private record ItemRowData(Item item, String name, String regPath, int baseY) {}
    private record SlotRowData(String label, int slot, int baseY) {}
    private record ProfileRowData(String name, int baseY) {}

    public InventoryCleanerScreen(Screen parent, InventoryCleaner config) {
        super(Component.literal("Inventory Cleaner"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        scrollableWidgets.clear();
        itemRows.clear();
        slotRows.clear();
        profileRows.clear();
        itemSearchField  = null;
        profileNameField = null;
        suggestions.clear();
        suggestionScroll = 0;

        int cx = width / 2;

        tabSettings = addTabButton("Settings",     TAB_SETTINGS, cx - 205, 36, 95, 20);
        tabItems    = addTabButton("Item List",    TAB_ITEMS,    cx - 105, 36, 95, 20);
        tabLocks    = addTabButton("Locked Slots", TAB_LOCKS,    cx -   5, 36, 95, 20);
        tabProfiles = addTabButton("Profiles",     TAB_PROFILES, cx +  95, 36, 95, 20);

        doneButton = Button.builder(
                Component.literal("SAVE & CLOSE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                b -> this.onClose()
        ).bounds(cx - 60, height - 28, 120, 20).build();
        addRenderableWidget(doneButton);

        gridOriginX = cx - (SLOT_COLS * SLOT_SIZE) / 2;
        gridOriginY = 90;

        if      (currentTab == TAB_SETTINGS) initSettings();
        else if (currentTab == TAB_ITEMS)    initItemList();
        else if (currentTab == TAB_PROFILES) initProfiles();
    }

    private void initSettings() {
        int cx       = width / 2;
        int leftCol  = cx - 155;
        int rightCol = cx + 5;
        int y        = 70;

        addToggle(leftCol, y,
                "Cleaning Mode",
                "Blacklist drops listed items. Whitelist keeps listed items.",
                config.getMode() == InventoryCleaner.CleaningMode.WHITELIST,
                v -> { config.setMode(v ? InventoryCleaner.CleaningMode.WHITELIST : InventoryCleaner.CleaningMode.BLACKLIST); config.saveConfiguration(); });

        addSlider(rightCol, y, 150, "Drop Delay",
                config.getThrowDelayTicks() / 20.0f, 0.05f, 2.0f, "%.2fs",
                v -> {
                    config.setThrowDelayTicks(Math.max(1, (int)(v * 20f)));
                    config.saveConfiguration();
                });
        y += SPACING_Y;


        addToggle(leftCol, y,
                "Inventory Open",
                "When enabled, automatically opens inventory, throws, then closes.",
                config.isInventoryOpenOnly(),
                v -> { config.setInventoryOpenOnly(v); config.saveConfiguration(); });

        addSlider(rightCol, y, 150, "Durability Threshold",
                config.getDurabilityThresholdPercent(), 0f, 100f, "%d%%",
                v -> {
                    config.setDurabilityThresholdPercent(Math.round(v));
                    config.saveConfiguration();
                });

        scrollableWidgets.get(scrollableWidgets.size() - 1)
                .setTooltip(Tooltip.create(Component.literal(
                        "§cOnly affects damageable items (tools/armor).\n" +
                                "§7Set above 0% to only throw items below\n" +
                                "§7that remaining durability percentage.\n" +
                                "§80% = disabled (throw regardless of durability)")));

        y += SPACING_Y + SECTION_MARGIN;

        itemsLabelBaseY = y;
        addScrollableWidget(Button.builder(
                Component.literal("WIPE ITEM LIST").withStyle(ChatFormatting.RED),
                b -> { config.getItemsToThrow().clear(); config.saveConfiguration(); this.init(); }
        ).bounds(cx + 5, y, 150, 20).build());
        y += SPACING_Y;

        for (Item item : config.getItemsToThrow()) {
            itemRows.add(new ItemRowData(item,
                    new ItemStack(item).getHoverName().getString(),
                    BuiltInRegistries.ITEM.getKey(item).getPath(), y));
            y += LIST_ROW_H;
        }
        if (config.getItemsToThrow().isEmpty()) y += LIST_ROW_H;
        y += SECTION_MARGIN;

        slotsLabelBaseY = y;
        addScrollableWidget(Button.builder(
                Component.literal("UNLOCK ALL SLOTS").withStyle(ChatFormatting.GOLD),
                b -> { config.getLockedSlots().clear(); config.saveConfiguration(); this.init(); }
        ).bounds(cx + 5, y, 150, 20).build());
        y += SPACING_Y;

        List<Integer> locked = new ArrayList<>(config.getLockedSlots());
        locked.sort(Integer::compareTo);
        for (int slot : locked) {
            String loc = slot < 9 ? "Hotbar slot " + slot
                    : "Row " + ((slot - 9) / 9 + 1) + ", Col " + ((slot - 9) % 9 + 1) + " (slot " + slot + ")";
            slotRows.add(new SlotRowData(loc, slot, y));
            y += LIST_ROW_H;
        }
        if (locked.isEmpty()) y += LIST_ROW_H;
        y += SECTION_MARGIN;

        contentHeight = y + 40;
        maxScroll     = Math.max(0, contentHeight - (height - 90));
        scrollOffset  = Math.min(scrollOffset, maxScroll);
        applyScrollOffset();
    }

    private void initItemList() {
        int cx     = width / 2;
        int fieldY = height - 56;

        itemSearchField = new EditBox(font, cx - 110, fieldY, 170, 20, Component.literal("Item ID"));
        itemSearchField.setMaxLength(128);
        itemSearchField.setHint(Component.literal("§8e.g. dirt or minecraft:dirt"));
        itemSearchField.setResponder(text -> { updateSuggestions(text.trim()); suggestionScroll = 0; });
        addRenderableWidget(itemSearchField);

        addRenderableWidget(Button.builder(
                Component.literal("ADD/Remove").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                b -> { addItemById(itemSearchField.getValue().trim()); itemSearchField.setValue(""); suggestions.clear(); }
        ).bounds(cx + 65, fieldY, 85, 20).build());
    }

    private void initProfiles() {
        int cx      = width / 2;
        int leftCol = cx - 155;
        int fieldY  = height - 56;

        profileNameField = new EditBox(font, cx - 110, fieldY, 120, 20, Component.literal("Profile name"));
        profileNameField.setMaxLength(48);
        profileNameField.setHint(Component.literal("§8my-profile"));
        addRenderableWidget(profileNameField);

        addRenderableWidget(Button.builder(
                Component.literal("SAVE").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                b -> saveProfile(profileNameField.getValue().trim())
        ).bounds(cx + 15, fieldY, 52, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("LOAD").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                b -> loadProfile(profileNameField.getValue().trim())
        ).bounds(cx + 71, fieldY, 52, 20).build());

        List<String> profiles = listProfiles();
        int y = 70;
        profilesLabelBaseY = y;
        y += SPACING_Y + 4;

        for (int i = 0; i < profiles.size(); i++) {
            final String name = profiles.get(i);
            final int    rowY = y;
            profileRows.add(new ProfileRowData(name, y));
            addScrollableWidget(Button.builder(Component.literal("Load").withStyle(ChatFormatting.AQUA),
                    b -> { loadProfile(name); this.init(); }).bounds(leftCol, rowY, 42, LIST_ROW_H).build());
            addScrollableWidget(Button.builder(Component.literal("✕").withStyle(ChatFormatting.RED),
                    b -> { deleteProfile(name); this.init(); }).bounds(leftCol + 45, rowY, 18, LIST_ROW_H).build());
            y += LIST_ROW_H + 2;
        }

        contentHeight = y + 40;
        maxScroll     = Math.max(0, contentHeight - (height - 90));
        scrollOffset  = Math.min(scrollOffset, maxScroll);
        applyScrollOffset();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        hoverTooltip = null;
        int cx = width / 2;

        ctx.centeredText(font,
                Component.literal("Inventory Cleaner").withStyle(ChatFormatting.RED, ChatFormatting.BOLD, ChatFormatting.UNDERLINE),
                cx, 12, 0xFFFFFFFF);

        ctx.fillGradient(0, 30, width, 40, 0x00000000, 0x60000000);
        ctx.fill(0, 40, width, 52, 0x60000000);
        ctx.fillGradient(0, 52, width, 62, 0x60000000, 0x00000000);

        tabSettings.extractRenderState(ctx, mouseX, mouseY, delta);
        tabItems   .extractRenderState(ctx, mouseX, mouseY, delta);
        tabLocks   .extractRenderState(ctx, mouseX, mouseY, delta);
        tabProfiles.extractRenderState(ctx, mouseX, mouseY, delta);

        switch (currentTab) {
            case TAB_SETTINGS -> renderSettings(ctx, mouseX, mouseY);
            case TAB_ITEMS    -> renderInventoryGrid(ctx, mouseX, mouseY, true);
            case TAB_LOCKS    -> renderInventoryGrid(ctx, mouseX, mouseY, false);
            case TAB_PROFILES -> renderProfiles(ctx, mouseX, mouseY);
        }

        ctx.enableScissor(0, 62, width, height - 60);
        for (AbstractWidget w : scrollableWidgets) {
            if (w.getY() + w.getHeight() > 62 && w.getY() < height - 60) {
                w.visible = true;
                w.extractRenderState(ctx, mouseX, mouseY, delta);
            } else {
                w.visible = false;
            }
        }
        ctx.disableScissor();

        if (itemSearchField  != null) itemSearchField .extractRenderState(ctx, mouseX, mouseY, delta);
        if (profileNameField != null) profileNameField.extractRenderState(ctx, mouseX, mouseY, delta);

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        doneButton.extractRenderState(ctx, mouseX, mouseY, delta);
        drawScrollBar(ctx);

        if (hoverTooltip != null)
            ctx.setTooltipForNextFrame(font, Component.literal(hoverTooltip), mouseX, mouseY);
    }

    private void renderSettings(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int cx      = width / 2;
        int leftCol = cx - 155;
        int panelW  = 325;
        int panelX  = cx - panelW / 2;

        ctx.enableScissor(0, 62, width, height - 32);

        int itemCount = config.getItemsToThrow().size();
        int slotCount = config.getLockedSlots().size();
        int itemListH = Math.max(LIST_ROW_H, itemCount * LIST_ROW_H);
        int slotListH = Math.max(LIST_ROW_H, slotCount * LIST_ROW_H);

        int y1   = 70 - scrollOffset;
        int genH = SPACING_Y * 2 + TITLE_HEIGHT + 10;
        drawPanel(ctx, panelX, y1 - TITLE_HEIGHT - 6, panelW, genH);
        ctx.text(font, "§c§l» §fGeneral Settings", panelX + 8, y1 - TITLE_HEIGHT, 0xFFFFFFFF);

        int y2      = y1 + SPACING_Y * 2 + SECTION_MARGIN;
        int panel2H = SPACING_Y + TITLE_HEIGHT + 10 + itemListH + 6;
        drawPanel(ctx, panelX, y2 - TITLE_HEIGHT - 6, panelW, panel2H);
        ctx.text(font, "§c§l» §fList Management", panelX + 8, y2 - TITLE_HEIGHT, 0xFFFFFFFF);
        ctx.text(font, "Items in List: §c" + itemCount, leftCol, itemsLabelBaseY - scrollOffset + 6, 0xFFFFFFFF);

        if (itemRows.isEmpty()) {
            ctx.text(font, "§8  (none)", leftCol + 4, y2 + SPACING_Y + 2, 0xFFFFFFFF);
        } else {
            for (int i = 0; i < itemRows.size(); i++) {
                ItemRowData row = itemRows.get(i);
                int ry = row.baseY() - scrollOffset;
                if (ry + LIST_ROW_H < 62 || ry > height - 32) continue;
                if (i % 2 == 0) ctx.fill(panelX + 2, ry, panelX + panelW - 2, ry + LIST_ROW_H, 0x20FF4444);
                ctx.fakeItem(new ItemStack(row.item()), leftCol, ry + 1);
                ctx.text(font, "§f" + row.name() + " §8(" + row.regPath() + ")", leftCol + LIST_ICON_SZ + 3, ry + 5, 0xFFFFFFFF);
            }
        }

        int y3      = y2 + SPACING_Y + SECTION_MARGIN + itemListH + 6;
        int panel3H = SPACING_Y + TITLE_HEIGHT + 10 + slotListH + 6;
        drawPanel(ctx, panelX, y3 - TITLE_HEIGHT - 6, panelW, panel3H);
        ctx.text(font, "§c§l» §fProtection", panelX + 8, y3 - TITLE_HEIGHT, 0xFFFFFFFF);
        ctx.text(font, "Locked Slots: §c" + slotCount, leftCol, slotsLabelBaseY - scrollOffset + 6, 0xFFFFFFFF);

        if (slotRows.isEmpty()) {
            ctx.text(font, "§8  (none)", leftCol + 4, y3 + SPACING_Y + 2, 0xFFFFFFFF);
        } else {
            for (int i = 0; i < slotRows.size(); i++) {
                SlotRowData row = slotRows.get(i);
                int ry = row.baseY() - scrollOffset;
                if (ry + LIST_ROW_H < 62 || ry > height - 32) continue;
                if (i % 2 == 0) ctx.fill(panelX + 2, ry, panelX + panelW - 2, ry + LIST_ROW_H, 0x20FFAA00);
                ctx.fill(leftCol + 1, ry + 2, leftCol + 9, ry + LIST_ROW_H - 2, SLOT_ACTIVE_LOCK);
                int color = row.slot() < 9 ? 0xFFFFFF55 : 0xFF55FFFF;
                ctx.text(font, row.label(), leftCol + LIST_ICON_SZ + 3, ry + 5, color);
            }
        }

        ctx.disableScissor();
        drawScrollBar(ctx);
    }

    private void renderProfiles(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int cx      = width / 2;
        int leftCol = cx - 155;
        int panelW  = 325;
        int panelX  = cx - panelW / 2;

        ctx.enableScissor(0, 62, width, height - 32);

        int profCount = profileRows.size();
        int listH     = Math.max(LIST_ROW_H + 2, profCount * (LIST_ROW_H + 2));
        int y1        = profilesLabelBaseY - scrollOffset;
        int panelH    = SPACING_Y + TITLE_HEIGHT + 10 + listH + 6;
        drawPanel(ctx, panelX, y1 - TITLE_HEIGHT - 6, panelW, panelH);
        ctx.text(font, "§c§l» §fSaved Profiles", panelX + 8, y1 - TITLE_HEIGHT, 0xFFFFFFFF);
        ctx.text(font, "§fSaved Profiles: §c" + profCount, leftCol, y1 + 6, 0xFFFFFFFF);

        if (profileRows.isEmpty()) {
            ctx.text(font, "§8  (no profiles yet — type a name below and click SAVE)", leftCol + 4, y1 + SPACING_Y + 6, 0xFFFFFFFF);
        } else {
            for (int i = 0; i < profileRows.size(); i++) {
                ProfileRowData row = profileRows.get(i);
                int ry = row.baseY() - scrollOffset;
                if (ry + LIST_ROW_H < 62 || ry > height - 32) continue;
                if (i % 2 == 0) ctx.fill(panelX + 2, ry, panelX + panelW - 2, ry + LIST_ROW_H, 0x18FFFFFF);
                ctx.text(font, (i % 2 == 0 ? "§f" : "§7") + row.name(), leftCol + 67, ry + 5, 0xFFFFFFFF);
            }
        }

        ctx.disableScissor();

        ctx.centeredText(font, Component.literal("§8Type a name below, then SAVE  ·  Click Load to restore"), cx, height - 72, 0xFFFFFFFF);
        if (!profileFeedback.isEmpty())
            ctx.centeredText(font, Component.literal(profileFeedback), cx, height - 84, 0xFFFFFFFF);

        drawScrollBar(ctx);
    }

    private void renderInventoryGrid(GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean itemMode) {
        int cx = width / 2;

        String modeLabel = itemMode
                ? (config.getMode() == InventoryCleaner.CleaningMode.WHITELIST
                   ? "§bWHITELIST §r- click slots to add/remove items"
                   : "§7BLACKLIST §r- click slots to add/remove items")
                : "§6LOCKED SLOTS §r- click slots to lock/unlock";
        ctx.centeredText(font, Component.literal(modeLabel), cx, 64, 0xFFFFFFFF);
        ctx.centeredText(font, Component.literal("§8" + (itemMode
                ? "Click a slot OR type an item ID below and press ADD"
                : "Click any slot number to toggle its lock")), cx, height - 68, 0xFFFFFFFF);

        ItemStack[] playerInv = getPlayerInventory();
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < SLOT_COLS; col++)
                drawSlot(ctx, mouseX, mouseY,
                        gridOriginX + col * SLOT_SIZE, gridOriginY + row * SLOT_SIZE,
                        9 + row * 9 + col, playerInv, itemMode);

        int hotbarY = gridOriginY + 3 * SLOT_SIZE + 6;
        for (int col = 0; col < SLOT_COLS; col++)
            drawSlot(ctx, mouseX, mouseY, gridOriginX + col * SLOT_SIZE, hotbarY, col, playerInv, itemMode);

        int legendY = hotbarY + SLOT_SIZE + 12;
        if (itemMode) {
            drawLegendBox(ctx, gridOriginX,      legendY, SLOT_ACTIVE_ITEM, "In list");
            drawLegendBox(ctx, gridOriginX + 80, legendY, SLOT_BG,          "Not in list");
        } else {
            drawLegendBox(ctx, gridOriginX,      legendY, SLOT_ACTIVE_LOCK, "Locked");
            drawLegendBox(ctx, gridOriginX + 80, legendY, SLOT_BG,          "Unlocked");
        }

        if (itemMode && !itemSearchFeedback.isEmpty())
            ctx.centeredText(font, Component.literal(itemSearchFeedback), cx, height - 72, 0xFFFFFFFF);

        if (itemMode) renderSuggestionDropdown(ctx, mouseX, mouseY);
    }

    private void renderSuggestionDropdown(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (suggestions.isEmpty() || itemSearchField == null) return;
        int fx = itemSearchField.getX(), fy = itemSearchField.getY(), fw = itemSearchField.getWidth();
        int visible = Math.min(MAX_VISIBLE_SUGGESTIONS, suggestions.size());
        int dropH   = visible * SUGGESTION_H + 4;
        int dropY   = fy - dropH - 2;

        ctx.fill(fx, dropY, fx + fw, fy - 2, 0xE0111111);
        ctx.fill(fx, dropY,      fx + fw, dropY + 1,  0xFF880000);
        ctx.fill(fx, fy - 3,     fx + fw, fy - 2,     0xFF880000);
        ctx.fill(fx, dropY,      fx + 1,  fy - 2,     0xFF880000);
        ctx.fill(fx + fw - 1, dropY, fx + fw, fy - 2, 0xFF880000);

        for (int i = 0; i < visible; i++) {
            int idx = i + suggestionScroll;
            if (idx >= suggestions.size()) break;
            String  id      = suggestions.get(idx);
            int     sy      = dropY + 2 + i * SUGGESTION_H;
            boolean hovered = mouseX >= fx && mouseX < fx + fw - 8 && mouseY >= sy && mouseY < sy + SUGGESTION_H;
            if (hovered) ctx.fill(fx + 1, sy, fx + fw - 1, sy + SUGGESTION_H, 0x80FF4444);
            boolean inList  = config.getItemsToThrow().stream()
                    .anyMatch(item -> BuiltInRegistries.ITEM.getKey(item).getPath().equals(id));
            String display = id;
            while (display.length() > 4 && font.width(display) > fw - 12)
                display = display.substring(0, display.length() - 1);
            if (!display.equals(id)) display += "…";
            ctx.text(font, Component.literal(display), fx + 3, sy + 2, inList ? 0xFF55FFFF : 0xFFCCCCCC);
        }

        if (suggestions.size() > MAX_VISIBLE_SUGGESTIONS) {
            int ax = fx + fw - 8;
            if (suggestionScroll > 0)
                ctx.text(font, Component.literal("▲"), ax, dropY + 2,          0xFFFF4444);
            if (suggestionScroll + MAX_VISIBLE_SUGGESTIONS < suggestions.size())
                ctx.text(font, Component.literal("▼"), ax, dropY + dropH - 10, 0xFFFF4444);
        }
    }

    private void drawSlot(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                          int px, int py, int vanillaSlot, ItemStack[] playerInv, boolean itemMode) {
        ItemStack stack = (playerInv != null && vanillaSlot < playerInv.length)
                ? playerInv[vanillaSlot] : ItemStack.EMPTY;
        boolean isActive = itemMode
                ? (!stack.isEmpty() && config.getItemsToThrow().contains(stack.getItem()))
                : config.getLockedSlots().contains(vanillaSlot);
        boolean hovered  = mouseX >= px && mouseX < px + SLOT_SIZE - 1
                && mouseY >= py && mouseY < py + SLOT_SIZE - 1;

        ctx.fill(px,                 py,                 px + SLOT_SIZE - 1, py + SLOT_SIZE - 1, SLOT_BG);
        ctx.fill(px,                 py,                 px + SLOT_SIZE - 2, py + 1,             SLOT_LIGHT);
        ctx.fill(px,                 py,                 px + 1,             py + SLOT_SIZE - 2, SLOT_LIGHT);
        ctx.fill(px + 1,             py + SLOT_SIZE - 2, px + SLOT_SIZE - 1, py + SLOT_SIZE - 1, SLOT_DARK);
        ctx.fill(px + SLOT_SIZE - 2, py + 1,             px + SLOT_SIZE - 1, py + SLOT_SIZE - 1, SLOT_DARK);

        if (isActive)
            ctx.fill(px + 1, py + 1, px + SLOT_SIZE - 2, py + SLOT_SIZE - 2,
                    itemMode ? SLOT_ACTIVE_ITEM : SLOT_ACTIVE_LOCK);

        if (!stack.isEmpty()) {
            ctx.fakeItem(stack, px + 1, py + 1);
            if (stack.getCount() > 1) {
                String cnt = String.valueOf(stack.getCount());
                ctx.text(font, Component.literal(cnt), px + SLOT_SIZE - 2 - font.width(cnt), py + SLOT_SIZE - 10, 0xFFFFFF);
            }
        }

        if (!itemMode && stack.isEmpty())
            ctx.text(font, Component.literal("§7" + vanillaSlot), px + 2, py + 5, 0xFFAAAAAA);

        if (hovered) {
            ctx.fill(px + 1, py + 1, px + SLOT_SIZE - 2, py + SLOT_SIZE - 2, SLOT_HOVER);
            if (!stack.isEmpty()) {
                String durInfo = "";
                if (stack.isDamageableItem()) {
                    int maxDmg = stack.getMaxDamage();
                    int remaining = (int)(((float)(maxDmg - stack.getDamageValue()) / maxDmg) * 100f);
                    durInfo = " §7[" + remaining + "% durability]";
                }
                hoverTooltip = stack.getHoverName().getString()
                        + (isActive ? (itemMode ? " §a[In List]" : " §6[Locked]") : "")
                        + durInfo;
            } else if (!itemMode) {
                hoverTooltip = "Slot " + vanillaSlot + (isActive ? " §6[Locked]" : " §7[Unlocked]");
            }
        }
    }

    private void drawLegendBox(GuiGraphicsExtractor ctx, int x, int y, int color, String label) {
        ctx.fill(x, y, x + 12, y + 12, color);
        ctx.fill(x, y, x + 12, y + 1,  SLOT_DARK);
        ctx.fill(x, y, x + 1,  y + 12, SLOT_DARK);
        ctx.text(font, Component.literal(label), x + 15, y + 2, 0xFFCCCCCC);
    }

    private void drawScrollBar(GuiGraphicsExtractor ctx) {
        if (maxScroll <= 0) return;
        int trackX = width - 6, trackY = 62, trackH = height - 90;
        int thumbH = Math.max(20, (int)((float) trackH * trackH / contentHeight));
        int thumbY = trackY + (int)((trackH - thumbH) * ((float) scrollOffset / maxScroll));
        ctx.fill(trackX, trackY,  width - 2, trackY + trackH, 0x40000000);
        ctx.fill(trackX, thumbY,  width - 2, thumbY + thumbH, ACCENT_COLOR);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (currentTab == TAB_ITEMS && itemSearchField != null && !suggestions.isEmpty()) {
            int fx = itemSearchField.getX(), fy = itemSearchField.getY(), fw = itemSearchField.getWidth();
            int visible = Math.min(MAX_VISIBLE_SUGGESTIONS, suggestions.size());
            int dropH   = visible * SUGGESTION_H + 4;
            int dropY   = fy - dropH - 2;

            if (mx >= fx && mx < fx + fw && my >= dropY && my < fy - 2) {
                int idx = (my - dropY - 2) / SUGGESTION_H + suggestionScroll;
                if (idx >= 0 && idx < suggestions.size()) {
                    addItemById(suggestions.get(idx));
                    itemSearchField.setValue("");
                    suggestions.clear();
                    return true;
                }
            }
        }

        if (currentTab == TAB_ITEMS || currentTab == TAB_LOCKS) {
            if (handleSlotClick(mx, my)) return true;
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (currentTab == TAB_ITEMS && itemSearchField != null && !suggestions.isEmpty()) {
            int fx = itemSearchField.getX(), fy = itemSearchField.getY(), fw = itemSearchField.getWidth();
            int visible = Math.min(MAX_VISIBLE_SUGGESTIONS, suggestions.size());
            int dropH   = visible * SUGGESTION_H + 4;
            int dropY   = fy - dropH - 2;
            if (mouseX >= fx && mouseX < fx + fw && mouseY >= dropY && mouseY < fy - 2) {
                suggestionScroll = (int) Math.max(0,
                        Math.min(suggestions.size() - MAX_VISIBLE_SUGGESTIONS, suggestionScroll - verticalAmount));
                return true;
            }
        }

        if ((currentTab == TAB_SETTINGS || currentTab == TAB_PROFILES) && maxScroll > 0) {
            int prev = scrollOffset;
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - (verticalAmount * 25)));
            int diff = prev - scrollOffset;
            for (AbstractWidget w : scrollableWidgets) w.setY(w.getY() + diff);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean handleSlotClick(int mx, int my) {
        boolean     itemMode = currentTab == TAB_ITEMS;
        ItemStack[] inv      = getPlayerInventory();

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < SLOT_COLS; col++)
                if (slotHit(mx, my, gridOriginX + col * SLOT_SIZE, gridOriginY + row * SLOT_SIZE)) {
                    toggleSlot(9 + row * 9 + col, inv, itemMode); return true;
                }

        int hotbarY = gridOriginY + 3 * SLOT_SIZE + 6;
        for (int col = 0; col < SLOT_COLS; col++)
            if (slotHit(mx, my, gridOriginX + col * SLOT_SIZE, hotbarY)) {
                toggleSlot(col, inv, itemMode); return true;
            }
        return false;
    }

    private boolean slotHit(int mx, int my, int px, int py) {
        return mx >= px && mx < px + SLOT_SIZE - 1 && my >= py && my < py + SLOT_SIZE - 1;
    }

    private void toggleSlot(int vanillaSlot, ItemStack[] playerInv, boolean itemMode) {
        if (itemMode) {
            ItemStack stack = (playerInv != null && vanillaSlot < playerInv.length)
                    ? playerInv[vanillaSlot] : ItemStack.EMPTY;
            if (stack.isEmpty()) return;
            Item item = stack.getItem();
            if (config.getItemsToThrow().contains(item)) config.getItemsToThrow().remove(item);
            else config.getItemsToThrow().add(item);
        } else {
            if (config.getLockedSlots().contains(vanillaSlot)) config.getLockedSlots().remove((Integer) vanillaSlot);
            else config.getLockedSlots().add(vanillaSlot);
        }
        config.saveConfiguration();
    }

    @Override
    public void tick() {
        super.tick();
        if (itemSearchFeedbackTimer > 0) itemSearchFeedbackTimer--; else itemSearchFeedback = "";
        if (profileFeedbackTimer   > 0) profileFeedbackTimer--;    else profileFeedback = "";
    }

    @Override
    public void onClose() {
        config.saveConfiguration();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private Button addTabButton(String label, int tabIndex, int x, int y, int w, int h) {
        Button btn = Button.builder(tabText(label, tabIndex), b -> {
            currentTab = tabIndex; scrollOffset = 0; this.init();
        }).bounds(x, y, w, h).build();
        addRenderableWidget(btn);
        return btn;
    }

    private Component tabText(String label, int tabIndex) {
        return currentTab == tabIndex
                ? Component.literal(label).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                : Component.literal(label).withStyle(ChatFormatting.GRAY);
    }

    private void addScrollableWidget(AbstractWidget w) {
        scrollableWidgets.add(w);
        addRenderableWidget(w);
    }

    private void addToggle(int x, int y, String label, String tooltip, boolean value, Consumer<Boolean> action) {
        boolean[] state = {value};
        Button btn = Button.builder(toggleText(label, state[0]), b -> {
            state[0] = !state[0];
            action.accept(state[0]);
            b.setMessage(toggleText(label, state[0]));
        }).bounds(x, y, 150, 20).tooltip(Tooltip.create(Component.literal("§c" + tooltip))).build();
        addScrollableWidget(btn);
    }

    private Component toggleText(String label, boolean value) {
        if (label.equals("Cleaning Mode"))
            return Component.literal(label + ": ").append(
                    value ? Component.literal("WHITELIST").withStyle(ChatFormatting.AQUA)
                            : Component.literal("BLACKLIST").withStyle(ChatFormatting.DARK_GRAY));
        return Component.literal(label + ": ").append(
                value ? Component.literal("ON") .withStyle(ChatFormatting.GREEN)
                        : Component.literal("OFF").withStyle(ChatFormatting.DARK_GRAY));
    }

    private void addSlider(int x, int y, int w, String label, float cur, float min, float max,
                           String format, Consumer<Float> action) {
        addScrollableWidget(new GenericSlider(x, y, w, 20, label, cur, min, max, format, action));
    }

    private void addSlider(int x, int y, int w, String label, float cur, float min, float max,
                           Consumer<Float> action) {
        addSlider(x, y, w, label, cur, min, max, "%.2fs", action);
    }

    private void drawPanel(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        ctx.fill(x,         y, x + w,     y + h, PANEL_BG);
        ctx.fill(x,         y, x + 2,     y + h, PANEL_BORDER);
        ctx.fill(x + w - 2, y, x + w,     y + h, PANEL_BORDER);
    }

    private void applyScrollOffset() {
        for (AbstractWidget w : scrollableWidgets) w.setY(w.getY() - scrollOffset);
    }

    private ItemStack[] getPlayerInventory() {
        if (minecraft == null || minecraft.player == null) return null;
        var inv = minecraft.player.getInventory();
        var out = new ItemStack[36];
        for (int i = 0; i < 36; i++) out[i] = inv.getItem(i);
        return out;
    }

    private void updateSuggestions(String query) {
        suggestions.clear();
        if (query.isEmpty()) return;
        String lq = query.toLowerCase();
        BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> id.getPath().contains(lq) || id.toString().contains(lq))
                .map(Identifier::getPath)
                .distinct()
                .sorted((a, b) -> {
                    boolean aS = a.startsWith(lq), bS = b.startsWith(lq);
                    if (aS && !bS) return -1; if (!aS && bS) return 1;
                    return a.compareTo(b);
                })
                .limit(40)
                .forEach(suggestions::add);
    }

    private void addItemById(String rawId) {
        if (rawId.isEmpty()) { setFeedback("§cType an item ID first!", 60); return; }
        if (!rawId.contains(":")) rawId = "minecraft:" + rawId;
        Identifier id;
        try { id = Identifier.parse(rawId); }
        catch (Exception e) { setFeedback("§cInvalid ID: " + rawId, 80); return; }
        if (!BuiltInRegistries.ITEM.containsKey(id)) { setFeedback("§cUnknown item: " + rawId, 80); return; }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == Items.AIR) { setFeedback("§cUnknown item: " + rawId, 80); return; }
        if (config.getItemsToThrow().contains(item)) {
            config.getItemsToThrow().remove(item);
            setFeedback("§eRemoved §f" + rawId, 60);
        } else {
            config.getItemsToThrow().add(item);
            setFeedback("§aAdded §f" + rawId, 60);
        }
        config.saveConfiguration();
    }

    private void setFeedback(String msg, int ticks)        { itemSearchFeedback = msg; itemSearchFeedbackTimer = ticks; }
    private void setProfileFeedback(String msg, int ticks) { profileFeedback    = msg; profileFeedbackTimer    = ticks; }

    private List<String> listProfiles() {
        try {
            Path dir = getConfigDir();
            if (!Files.exists(dir)) return List.of();
            return Files.list(dir)
                    .filter(p -> p.toString().endsWith(".properties"))
                    .map(p -> p.getFileName().toString().replace(".properties", ""))
                    .filter(n -> !n.equals("default")).sorted().collect(Collectors.toList());
        } catch (IOException e) { e.printStackTrace(); return List.of(); }
    }

    private Path getConfigDir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("inventory-cleaner");
    }

    private void saveProfile(String name) {
        if (name.isEmpty()) { setProfileFeedback("§cEnter a profile name first!", 80); return; }
        if (!name.matches("[\\w\\-. ]+")) { setProfileFeedback("§cInvalid name characters", 100); return; }
        config.saveConfiguration(name);
        setProfileFeedback("§aSaved: §f" + name, 80);
        this.init();
    }

    private void loadProfile(String name) {
        if (name.isEmpty()) { setProfileFeedback("§cEnter or select a profile name!", 80); return; }
        boolean ok = config.loadConfiguration(name);
        setProfileFeedback(ok ? "§aLoaded: §f" + name : "§cNot found: §f" + name, 80);
        this.init();
    }

    private void deleteProfile(String name) {
        try {
            Files.deleteIfExists(getConfigDir().resolve(name + ".properties"));
            setProfileFeedback("§eDeleted: §f" + name, 80);
        } catch (IOException e) { e.printStackTrace(); setProfileFeedback("§cFailed to delete: §f" + name, 80); }
    }

    private class GenericSlider extends AbstractSliderButton {
        private final String label;
        private final float  min, max;
        private final String format;
        private final Consumer<Float> callback;

        GenericSlider(int x, int y, int w, int h, String label, float cur, float min, float max,
                      String format, Consumer<Float> callback) {
            super(x, y, w, h, Component.empty(), (double)(cur - min) / (max - min));
            this.label = label; this.min = min; this.max = max;
            this.format = format; this.callback = callback;
            updateMessage();
        }

        @Override protected void updateMessage() {
            float val = min + (float)(value * (max - min));
            String formatted;
            if (format.contains("d")) {
                formatted = String.format(format, Math.round(val));
            } else {
                formatted = String.format(format, val);
            }
            setMessage(Component.literal(label + ": §c" + formatted));
        }

        @Override protected void applyValue() {
            callback.accept(min + (float)(value * (max - min)));
        }
    }
}