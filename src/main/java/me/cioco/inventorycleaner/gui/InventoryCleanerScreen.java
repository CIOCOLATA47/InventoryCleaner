package me.cioco.inventorycleaner.gui;

import me.cioco.inventorycleaner.config.InventoryCleaner;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InventoryCleanerScreen extends Screen {

    private static final int SPACING_Y = 24;
    private static final int SECTION_MARGIN = 35;
    private static final int TITLE_HEIGHT = 20;

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_COLS = 9;

    private static final int ACCENT_COLOR = 0xFFFF4444;
    private static final int PANEL_BORDER = 0xFF880000;
    private static final int PANEL_BG = 0x90100000;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFF5B5B5B;
    private static final int SLOT_HOVER = 0x80FFFFFF;
    private static final int SLOT_ACTIVE_ITEM = 0xCC4444AA;
    private static final int SLOT_ACTIVE_LOCK = 0xCCAA8800;

    private static final int LIST_ROW_H = 18;
    private static final int LIST_ICON_SZ = 17;

    private static final int TAB_SETTINGS = 0;
    private static final int TAB_ITEMS = 1;
    private static final int TAB_LOCKS = 2;
    private static final int TAB_PROFILES = 3;

    private static final int MAX_VISIBLE_SUGGESTIONS = 5;
    private static final int SUGGESTION_H = 12;
    private final Screen parent;
    private final InventoryCleaner config;
    private final List<ClickableWidget> scrollableWidgets = new ArrayList<>();
    private final List<String> suggestions = new ArrayList<>();
    private int suggestionScroll = 0;
    private int currentTab = TAB_SETTINGS;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int contentHeight = 0;

    private ButtonWidget tabSettings, tabItems, tabLocks, tabProfiles, doneButton;
    private int gridOriginX, gridOriginY;
    private String hoverTooltip = null;

    private TextFieldWidget itemSearchField;
    private String itemSearchFeedback = "";
    private int itemSearchFeedbackTimer = 0;

    private TextFieldWidget profileNameField;
    private String profileFeedback = "";
    private int profileFeedbackTimer = 0;

    public InventoryCleanerScreen(Screen parent, InventoryCleaner config) {
        super(Text.literal("Inventory Cleaner"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        clearChildren();
        scrollableWidgets.clear();
        itemSearchField = null;
        profileNameField = null;
        suggestions.clear();
        suggestionScroll = 0;

        int cx = width / 2;

        tabSettings = addTabButton("Settings", TAB_SETTINGS, cx - 205, 36, 95, 20);
        tabItems = addTabButton("Item List", TAB_ITEMS, cx - 105, 36, 95, 20);
        tabLocks = addTabButton("Locked Slots", TAB_LOCKS, cx - 5, 36, 95, 20);
        tabProfiles = addTabButton("Profiles", TAB_PROFILES, cx + 95, 36, 95, 20);

        doneButton = ButtonWidget.builder(
                Text.literal("SAVE & CLOSE").formatted(Formatting.RED, Formatting.BOLD),
                b -> close()
        ).dimensions(cx - 60, height - 28, 120, 20).build();
        addDrawableChild(doneButton);

        gridOriginX = cx - (SLOT_COLS * SLOT_SIZE) / 2;
        gridOriginY = 70;

        if (currentTab == TAB_SETTINGS) initSettings();
        else if (currentTab == TAB_ITEMS) initItemList();
        else if (currentTab == TAB_PROFILES) initProfiles();
    }

    private void initSettings() {
        int cx = width / 2;
        int leftCol = cx - 155;
        int rightCol = cx + 5;
        int y = 70;

        addToggle(leftCol, y,
                "Cleaning Mode",
                "Blacklist drops listed items. Whitelist keeps listed items.",
                config.getMode() == InventoryCleaner.CleaningMode.WHITELIST,
                v -> {
                    config.setMode(v ? InventoryCleaner.CleaningMode.WHITELIST : InventoryCleaner.CleaningMode.BLACKLIST);
                    config.saveConfiguration();
                });

        float delaySeconds = config.getThrowDelayTicks() / 20.0f;
        addSlider(rightCol, y, "Drop Delay", delaySeconds, 0.05f, 2.0f, v -> {
            config.setThrowDelayTicks(Math.max(1, (int) (v * 20f)));
            config.saveConfiguration();
        });

        y += SPACING_Y;

        addToggle(leftCol, y,
                "Inventory Open",
                "When enabled, it will automatically open the inventory throw then close.",
                config.isInventoryOpenOnly(),
                v -> {
                    config.setInventoryOpenOnly(v);
                    config.saveConfiguration();
                });

        y += SPACING_Y + SECTION_MARGIN;

        addLabel(leftCol, y, "Items in List: §c" + config.getItemsToThrow().size());
        addScrollable(ButtonWidget.builder(
                Text.literal("WIPE ITEM LIST").formatted(Formatting.RED),
                b -> {
                    config.getItemsToThrow().clear();
                    config.saveConfiguration();
                    init();
                }
        ).dimensions(cx + 5, y, 150, 20).build());

        y += SPACING_Y;

        List<Item> items = new ArrayList<>(config.getItemsToThrow());
        if (items.isEmpty()) {
            addLabel(leftCol + 4, y, "§8  (none)");
            y += LIST_ROW_H;
        } else {
            for (int i = 0; i < items.size(); i++) {
                final Item rowItem = items.get(i);
                final int rowIdx = i;
                final int rowY = y;
                String name = rowItem.getName().getString();
                String regPath = Registries.ITEM.getId(rowItem).getPath();

                ClickableWidget row = new ClickableWidget(leftCol, rowY, 300, LIST_ROW_H,
                        Text.literal("§f" + name + " §8(" + regPath + ")")) {
                    @Override
                    protected void renderWidget(DrawContext ctx, int mx, int my, float delta) {
                        if (rowIdx % 2 == 0)
                            ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20FF4444);
                        ctx.drawItem(new ItemStack(rowItem), getX() + 1, getY() + 1);
                        ctx.drawTextWithShadow(textRenderer, getMessage(),
                                getX() + LIST_ICON_SZ + 3, getY() + 5, 0xFFDDDDDD);
                    }

                    @Override
                    protected void appendClickableNarrations(NarrationMessageBuilder b) {
                    }
                };
                addScrollable(row);
                y += LIST_ROW_H;
            }
        }

        y += SECTION_MARGIN;

        addLabel(leftCol, y, "Locked Slots: §c" + config.getLockedSlots().size());
        addScrollable(ButtonWidget.builder(
                Text.literal("UNLOCK ALL SLOTS").formatted(Formatting.GOLD),
                b -> {
                    config.getLockedSlots().clear();
                    config.saveConfiguration();
                    init();
                }
        ).dimensions(cx + 5, y, 150, 20).build());

        y += SPACING_Y;

        List<Integer> lockedSlots = new ArrayList<>(config.getLockedSlots());
        lockedSlots.sort(Integer::compareTo);

        if (lockedSlots.isEmpty()) {
            addLabel(leftCol + 4, y, "§8  (none)");
            y += LIST_ROW_H;
        } else {
            for (int i = 0; i < lockedSlots.size(); i++) {
                int slot = lockedSlots.get(i);
                int rowIdx = i;
                int rowY = y;
                String loc;
                if (slot < 9) {
                    loc = "§eHotbar slot " + slot;
                } else {
                    int row2 = (slot - 9) / 9;
                    int col2 = (slot - 9) % 9;
                    loc = "§bRow " + (row2 + 1) + ", Col " + (col2 + 1) + " §8(slot " + slot + ")";
                }
                final String display = loc;

                ClickableWidget row = new ClickableWidget(leftCol, rowY, 300, LIST_ROW_H,
                        Text.literal(display)) {
                    @Override
                    protected void renderWidget(DrawContext ctx, int mx, int my, float delta) {
                        if (rowIdx % 2 == 0)
                            ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20FFAA00);
                        ctx.fill(getX() + 1, getY() + 2,
                                getX() + 9, getY() + getHeight() - 2, SLOT_ACTIVE_LOCK);
                        ctx.drawTextWithShadow(textRenderer, getMessage(),
                                getX() + LIST_ICON_SZ + 3, getY() + 2, 0xFFDDDDDD);
                    }

                    @Override
                    protected void appendClickableNarrations(NarrationMessageBuilder b) {
                    }
                };
                addScrollable(row);
                y += LIST_ROW_H;
            }
        }

        y += SECTION_MARGIN;

        contentHeight = y + 40;
        maxScroll = Math.max(0, contentHeight - (height - 90));
        scrollOffset = Math.min(scrollOffset, maxScroll);
        applyScrollOffset();
    }

    private void initItemList() {
        int cx = width / 2;
        int fieldY = height - 56;

        itemSearchField = new TextFieldWidget(
                textRenderer, cx - 110, fieldY, 170, 20,
                Text.literal("Item ID"));
        itemSearchField.setMaxLength(128);
        itemSearchField.setSuggestion("e.g. dirt or minecraft:dirt");
        itemSearchField.setChangedListener(text -> {
            if (text.isEmpty()) itemSearchField.setSuggestion("e.g. dirt or minecraft:dirt");
            else itemSearchField.setSuggestion("");
            updateSuggestions(text.trim());
            suggestionScroll = 0;
        });
        addDrawableChild(itemSearchField);

        addDrawableChild(ButtonWidget.builder(
                Text.literal("ADD/Remove").formatted(Formatting.GREEN, Formatting.BOLD),
                b -> {
                    addItemById(itemSearchField.getText().trim());
                    itemSearchField.setText("");
                    itemSearchField.setSuggestion("e.g. dirt or minecraft:dirt");
                    suggestions.clear();
                }
        ).dimensions(cx + 65, fieldY, 85, 20).build());
    }

    private void initProfiles() {
        int cx = width / 2;
        int leftCol = cx - 155;

        int fieldY = height - 56;
        profileNameField = new TextFieldWidget(
                textRenderer, cx - 110, fieldY, 120, 20,
                Text.literal("Profile name"));
        profileNameField.setMaxLength(48);
        profileNameField.setSuggestion("my-profile");
        profileNameField.setChangedListener(t -> {
            if (t.isEmpty()) profileNameField.setSuggestion("my-profile");
            else profileNameField.setSuggestion("");
        });
        addDrawableChild(profileNameField);

        addDrawableChild(ButtonWidget.builder(
                Text.literal("SAVE").formatted(Formatting.GREEN, Formatting.BOLD),
                b -> saveProfile(profileNameField.getText().trim())
        ).dimensions(cx + 15, fieldY, 52, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("LOAD").formatted(Formatting.AQUA, Formatting.BOLD),
                b -> loadProfile(profileNameField.getText().trim())
        ).dimensions(cx + 71, fieldY, 52, 20).build());

        List<String> profiles = listProfiles();

        int y = 70;
        addLabel(leftCol, y, "§fSaved Profiles: §c" + profiles.size());
        y += SPACING_Y + 4;

        if (profiles.isEmpty()) {
            addLabel(leftCol + 4, y, "§8  (no profiles saved yet — type a name below and click SAVE)");
            y += LIST_ROW_H;
        } else {
            for (int i = 0; i < profiles.size(); i++) {
                final String name = profiles.get(i);
                final int rowIdx = i;
                final int rowY = y;
                final int rowX = leftCol;

                ButtonWidget loadBtn = ButtonWidget.builder(
                        Text.literal("Load").formatted(Formatting.AQUA),
                        b -> {
                            loadProfile(name);
                            init();
                        }
                ).dimensions(rowX, rowY, 42, LIST_ROW_H).build();
                addScrollable(loadBtn);

                ButtonWidget delBtn = ButtonWidget.builder(
                        Text.literal("✕").formatted(Formatting.RED),
                        b -> {
                            deleteProfile(name);
                            init();
                        }
                ).dimensions(rowX + 45, rowY, 18, LIST_ROW_H).build();
                addScrollable(delBtn);

                ClickableWidget label = new ClickableWidget(
                        rowX + 67, rowY, 250, LIST_ROW_H,
                        Text.literal((rowIdx % 2 == 0 ? "§f" : "§7") + name)) {
                    @Override
                    protected void renderWidget(DrawContext ctx, int mx, int my, float delta) {
                        if (rowIdx % 2 == 0)
                            ctx.fill(rowX, getY(), rowX + 320, getY() + getHeight(), 0x18FFFFFF);
                        ctx.drawTextWithShadow(textRenderer, getMessage(),
                                getX(), getY() + 5, 0xFFDDDDDD);
                    }

                    @Override
                    protected void appendClickableNarrations(NarrationMessageBuilder b) {
                    }
                };
                addScrollable(label);

                y += LIST_ROW_H + 2;
            }
        }

        contentHeight = y + 40;
        maxScroll = Math.max(0, contentHeight - (height - 90));
        scrollOffset = Math.min(scrollOffset, maxScroll);
        applyScrollOffset();
    }

    private List<String> listProfiles() {
        try {
            Path dir = getConfigDir();
            if (!Files.exists(dir)) return List.of();
            return Files.list(dir)
                    .filter(p -> p.toString().endsWith(".properties"))
                    .map(p -> p.getFileName().toString().replace(".properties", ""))
                    .filter(n -> !n.equals("default"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    private Path getConfigDir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("inventory-cleaner");
    }

    private void saveProfile(String name) {
        if (name.isEmpty()) {
            setProfileFeedback("§cEnter a profile name first!", 80);
            return;
        }
        if (!name.matches("[\\w\\-. ]+")) {
            setProfileFeedback("§cName may only contain letters, digits, -, _, . and spaces", 100);
            return;
        }
        config.saveConfiguration(name);
        setProfileFeedback("§aSaved profile: §f" + name, 80);
        init();
    }

    private void loadProfile(String name) {
        if (name.isEmpty()) {
            setProfileFeedback("§cEnter or select a profile name!", 80);
            return;
        }
        boolean ok = config.loadConfiguration(name);
        if (ok) setProfileFeedback("§aLoaded profile: §f" + name, 80);
        else setProfileFeedback("§cProfile not found: §f" + name, 80);
        init();
    }

    private void deleteProfile(String name) {
        try {
            Files.deleteIfExists(getConfigDir().resolve(name + ".properties"));
            setProfileFeedback("§eDeleted profile: §f" + name, 80);
        } catch (IOException e) {
            e.printStackTrace();
            setProfileFeedback("§cFailed to delete: §f" + name, 80);
        }
    }

    private void setProfileFeedback(String msg, int ticks) {
        profileFeedback = msg;
        profileFeedbackTimer = ticks;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderInGameBackground(ctx);
        hoverTooltip = null;

        int cx = width / 2;
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Inventory Cleaner")
                        .formatted(Formatting.RED, Formatting.BOLD, Formatting.UNDERLINE),
                cx, 12, 0xFFFFFFFF);

        switch (currentTab) {
            case TAB_SETTINGS -> renderSettingsBackground(ctx, mouseX, mouseY, delta);
            case TAB_ITEMS -> renderInventoryGrid(ctx, mouseX, mouseY, true);
            case TAB_LOCKS -> renderInventoryGrid(ctx, mouseX, mouseY, false);
            case TAB_PROFILES -> renderProfilesBackground(ctx, mouseX, mouseY);
        }

        super.render(ctx, mouseX, mouseY, delta);

        if (hoverTooltip != null)
            ctx.drawTooltip(textRenderer, Text.literal(hoverTooltip), mouseX, mouseY);
    }

    private void renderProfilesBackground(DrawContext ctx, int mouseX, int mouseY) {
        int cx = width / 2;
        int panelW = 325;
        int panelX = cx - panelW / 2;

        ctx.enableScissor(0, 58, width, height - 32);

        int profCount = listProfiles().size();
        int listH = Math.max(LIST_ROW_H, (profCount == 0 ? 1 : profCount) * (LIST_ROW_H + 2));
        int y1 = 70 - scrollOffset;
        int panelH = SPACING_Y + TITLE_HEIGHT + 10 + listH + 6;
        drawPanel(ctx, panelX, y1 - TITLE_HEIGHT - 6, panelW, panelH);
        ctx.drawTextWithShadow(textRenderer, "§c§l» §fSaved Profiles",
                panelX + 8, y1 - TITLE_HEIGHT, 0xFFFFFFFF);

        ctx.disableScissor();

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§8Type a name below, then SAVE  ·  Click Load to restore a profile"),
                cx, height - 72, 0xFFFFFFFF);

        if (!profileFeedback.isEmpty())
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(profileFeedback), cx, height - 84, 0xFFFFFFFF);

        drawScrollbar(ctx);
    }

    private void renderSettingsBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cx = width / 2;
        int panelW = 325;
        int panelX = cx - panelW / 2;

        ctx.enableScissor(0, 58, width, height - 32);

        int itemCount = config.getItemsToThrow().size();
        int slotCount = config.getLockedSlots().size();
        int itemListH = Math.max(LIST_ROW_H, (itemCount == 0 ? 1 : itemCount) * LIST_ROW_H);
        int slotListH = Math.max(LIST_ROW_H, (slotCount == 0 ? 1 : slotCount) * LIST_ROW_H);

        int y1 = 70 - scrollOffset;
        int genH = SPACING_Y * 2 + TITLE_HEIGHT + 10;
        drawPanel(ctx, panelX, y1 - TITLE_HEIGHT - 6, panelW, genH);
        ctx.drawTextWithShadow(textRenderer, "§c§l» §fGeneral Settings",
                panelX + 8, y1 - TITLE_HEIGHT, 0xFFFFFFFF);

        int y2 = y1 + SPACING_Y * 2 + SECTION_MARGIN;
        int panel2H = SPACING_Y + TITLE_HEIGHT + 10 + itemListH + 6;
        drawPanel(ctx, panelX, y2 - TITLE_HEIGHT - 6, panelW, panel2H);
        ctx.drawTextWithShadow(textRenderer, "§c§l» §fList Management",
                panelX + 8, y2 - TITLE_HEIGHT, 0xFFFFFFFF);

        int y3 = y2 + SPACING_Y + SECTION_MARGIN + itemListH + 6;
        int panel3H = SPACING_Y + TITLE_HEIGHT + 10 + slotListH + 6;
        drawPanel(ctx, panelX, y3 - TITLE_HEIGHT - 6, panelW, panel3H);
        ctx.drawTextWithShadow(textRenderer, "§c§l» §fProtection",
                panelX + 8, y3 - TITLE_HEIGHT, 0xFFFFFFFF);

        ctx.disableScissor();
        drawScrollbar(ctx);
    }

    private void renderInventoryGrid(DrawContext ctx, int mouseX, int mouseY, boolean itemMode) {
        int cx = width / 2;

        String modeLabel = itemMode
                ? (config.getMode() == InventoryCleaner.CleaningMode.WHITELIST
                ? "§bWHITELIST §r- click slots to add/remove items"
                : "§7BLACKLIST §r- click slots to add/remove items")
                : "§6LOCKED SLOTS §r- click slots to lock/unlock";

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(modeLabel), cx, 60, 0xFFFFFFFF);

        String hint = itemMode
                ? "Click a slot OR type an item ID below and press ADD"
                : "Click any slot number to toggle its lock";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§8" + hint), cx, height - 68, 0xFFFFFFFF);

        ItemStack[] playerInv = getPlayerInventory();

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < SLOT_COLS; col++) {
                int slot = 9 + row * 9 + col;
                drawSlot(ctx, mouseX, mouseY,
                        gridOriginX + col * SLOT_SIZE, gridOriginY + row * SLOT_SIZE,
                        slot, playerInv, itemMode);
            }

        int hotbarY = gridOriginY + 3 * SLOT_SIZE + 6;
        for (int col = 0; col < SLOT_COLS; col++)
            drawSlot(ctx, mouseX, mouseY,
                    gridOriginX + col * SLOT_SIZE, hotbarY, col, playerInv, itemMode);

        int legendY = hotbarY + SLOT_SIZE + 12;
        if (itemMode) {
            drawLegendBox(ctx, gridOriginX, legendY, SLOT_ACTIVE_ITEM, "In list");
            drawLegendBox(ctx, gridOriginX + 80, legendY, SLOT_BG, "Not in list");
        } else {
            drawLegendBox(ctx, gridOriginX, legendY, SLOT_ACTIVE_LOCK, "Locked");
            drawLegendBox(ctx, gridOriginX + 80, legendY, SLOT_BG, "Unlocked");
        }

        if (itemMode && !itemSearchFeedback.isEmpty())
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(itemSearchFeedback), cx, height - 72, 0xFFFFFFFF);

        if (itemMode) renderSuggestionDropdown(ctx, mouseX, mouseY);
    }

    private void renderSuggestionDropdown(DrawContext ctx, int mouseX, int mouseY) {
        if (suggestions.isEmpty() || itemSearchField == null) return;

        int fx = itemSearchField.getX();
        int fy = itemSearchField.getY();
        int fw = itemSearchField.getWidth();

        int visible = Math.min(MAX_VISIBLE_SUGGESTIONS, suggestions.size());
        int dropH = visible * SUGGESTION_H + 4;
        int dropY = fy - dropH - 2;

        ctx.fill(fx, dropY, fx + fw, fy - 2, 0xE0111111);
        ctx.fill(fx, dropY, fx + fw, dropY + 1, 0xFF880000);
        ctx.fill(fx, fy - 3, fx + fw, fy - 2, 0xFF880000);
        ctx.fill(fx, dropY, fx + 1, fy - 2, 0xFF880000);
        ctx.fill(fx + fw - 1, dropY, fx + fw, fy - 2, 0xFF880000);

        for (int i = 0; i < visible; i++) {
            int idx = i + suggestionScroll;
            if (idx >= suggestions.size()) break;
            String id = suggestions.get(idx);
            int sy = dropY + 2 + i * SUGGESTION_H;
            boolean hovered = mouseX >= fx && mouseX < fx + fw - 8
                    && mouseY >= sy && mouseY < sy + SUGGESTION_H;
            if (hovered) ctx.fill(fx + 1, sy, fx + fw - 1, sy + SUGGESTION_H, 0x80FF4444);
            boolean inList = config.getItemsToThrow().stream()
                    .anyMatch(item -> Registries.ITEM.getId(item).toString().equals(id));
            int textColor = inList ? 0xFF55FFFF : 0xFFCCCCCC;
            String display = id;
            while (display.length() > 4 && textRenderer.getWidth(display) > fw - 12)
                display = display.substring(0, display.length() - 1);
            if (!display.equals(id)) display += "…";
            ctx.drawTextWithShadow(textRenderer, Text.literal(display), fx + 3, sy + 2, textColor);
        }

        if (suggestions.size() > MAX_VISIBLE_SUGGESTIONS) {
            int ax = fx + fw - 8;
            if (suggestionScroll > 0)
                ctx.drawTextWithShadow(textRenderer, Text.literal("▲"), ax, dropY + 2, 0xFFFF4444);
            if (suggestionScroll + MAX_VISIBLE_SUGGESTIONS < suggestions.size())
                ctx.drawTextWithShadow(textRenderer, Text.literal("▼"), ax, dropY + dropH - 10, 0xFFFF4444);
        }
    }

    private void drawSlot(DrawContext ctx, int mouseX, int mouseY,
                          int px, int py, int vanillaSlot,
                          ItemStack[] playerInv, boolean itemMode) {

        ItemStack stack = (playerInv != null && vanillaSlot < playerInv.length)
                ? playerInv[vanillaSlot] : ItemStack.EMPTY;

        boolean isActive = itemMode
                ? (!stack.isEmpty() && config.getItemsToThrow().contains(stack.getItem()))
                : config.getLockedSlots().contains(vanillaSlot);
        boolean hovered = mouseX >= px && mouseX < px + SLOT_SIZE - 1
                && mouseY >= py && mouseY < py + SLOT_SIZE - 1;

        ctx.fill(px, py, px + SLOT_SIZE - 1, py + SLOT_SIZE - 1, SLOT_BG);
        ctx.fill(px, py, px + SLOT_SIZE - 2, py + 1, SLOT_LIGHT);
        ctx.fill(px, py, px + 1, py + SLOT_SIZE - 2, SLOT_LIGHT);
        ctx.fill(px + 1, py + SLOT_SIZE - 2, px + SLOT_SIZE - 1, py + SLOT_SIZE - 1, SLOT_DARK);
        ctx.fill(px + SLOT_SIZE - 2, py + 1, px + SLOT_SIZE - 1, py + SLOT_SIZE - 1, SLOT_DARK);

        if (isActive)
            ctx.fill(px + 1, py + 1, px + SLOT_SIZE - 2, py + SLOT_SIZE - 2,
                    itemMode ? SLOT_ACTIVE_ITEM : SLOT_ACTIVE_LOCK);

        if (!stack.isEmpty()) {
            ctx.drawItem(stack, px + 1, py + 1);
            if (stack.getCount() > 1) {
                String count = String.valueOf(stack.getCount());
                ctx.drawTextWithShadow(textRenderer, Text.literal(count),
                        px + SLOT_SIZE - 2 - textRenderer.getWidth(count), py + SLOT_SIZE - 10, 0xFFFFFF);
            }
        }

        if (!itemMode && stack.isEmpty())
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("§7" + vanillaSlot), px + 2, py + 5, 0xFFAAAAAA);

        if (hovered) {
            ctx.fill(px + 1, py + 1, px + SLOT_SIZE - 2, py + SLOT_SIZE - 2, SLOT_HOVER);
            if (!stack.isEmpty())
                hoverTooltip = stack.getName().getString()
                        + (isActive ? (itemMode ? " §a[In List]" : " §6[Locked]") : "");
            else if (!itemMode)
                hoverTooltip = "Slot " + vanillaSlot + (isActive ? " §6[Locked]" : " §7[Unlocked]");
        }
    }

    private void drawLegendBox(DrawContext ctx, int x, int y, int color, String label) {
        ctx.fill(x, y, x + 12, y + 12, color);
        ctx.fill(x, y, x + 12, y + 1, SLOT_DARK);
        ctx.fill(x, y, x + 1, y + 12, SLOT_DARK);
        ctx.drawTextWithShadow(textRenderer, Text.literal(label), x + 15, y + 2, 0xFFCCCCCC);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean bl) {
        int mx = (int) click.x();
        int my = (int) click.y();

        if (currentTab == TAB_ITEMS && itemSearchField != null && !suggestions.isEmpty()) {
            int fx = itemSearchField.getX();
            int fy = itemSearchField.getY();
            int fw = itemSearchField.getWidth();
            int visible = Math.min(MAX_VISIBLE_SUGGESTIONS, suggestions.size());
            int dropH = visible * SUGGESTION_H + 4;
            int dropY = fy - dropH - 2;
            if (mx >= fx && mx < fx + fw && my >= dropY && my < fy - 2) {
                int idx = (my - dropY - 2) / SUGGESTION_H + suggestionScroll;
                if (idx >= 0 && idx < suggestions.size()) {
                    addItemById(suggestions.get(idx));
                    itemSearchField.setText("");
                    suggestions.clear();
                    return true;
                }
            }
        }

        if (currentTab == TAB_ITEMS || currentTab == TAB_LOCKS)
            if (handleSlotClick(mx, my)) return true;

        return super.mouseClicked(click, bl);
    }

    private boolean handleSlotClick(int mx, int my) {
        boolean itemMode = currentTab == TAB_ITEMS;
        ItemStack[] inv = getPlayerInventory();

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < SLOT_COLS; col++) {
                int slot = 9 + row * 9 + col;
                int px = gridOriginX + col * SLOT_SIZE;
                int py = gridOriginY + row * SLOT_SIZE;
                if (slotHit(mx, my, px, py)) {
                    toggleSlot(slot, inv, itemMode);
                    return true;
                }
            }

        int hotbarY = gridOriginY + 3 * SLOT_SIZE + 6;
        for (int col = 0; col < SLOT_COLS; col++) {
            int px = gridOriginX + col * SLOT_SIZE;
            if (slotHit(mx, my, px, hotbarY)) {
                toggleSlot(col, inv, itemMode);
                return true;
            }
        }
        return false;
    }

    private boolean slotHit(int mx, int my, int px, int py) {
        return mx >= px && mx < px + SLOT_SIZE - 1
                && my >= py && my < py + SLOT_SIZE - 1;
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
            if (config.getLockedSlots().contains(vanillaSlot)) config.getLockedSlots().remove(vanillaSlot);
            else config.getLockedSlots().add(vanillaSlot);
        }
        config.saveConfiguration();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (currentTab == TAB_ITEMS && itemSearchField != null && !suggestions.isEmpty()) {
            int fx = itemSearchField.getX();
            int fy = itemSearchField.getY();
            int fw = itemSearchField.getWidth();
            int visible = Math.min(MAX_VISIBLE_SUGGESTIONS, suggestions.size());
            int dropH = visible * SUGGESTION_H + 4;
            int dropY = fy - dropH - 2;
            if (mx >= fx && mx < fx + fw && my >= dropY && my < fy - 2) {
                suggestionScroll = (int) Math.max(0,
                        Math.min(suggestions.size() - MAX_VISIBLE_SUGGESTIONS, suggestionScroll - scrollY));
                return true;
            }
        }

        if ((currentTab == TAB_SETTINGS || currentTab == TAB_PROFILES) && maxScroll > 0) {
            int prev = scrollOffset;
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 25));
            int diff = prev - scrollOffset;
            for (ClickableWidget w : scrollableWidgets) w.setY(w.getY() + diff);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public void tick() {
        super.tick();
        if (itemSearchFeedbackTimer > 0) itemSearchFeedbackTimer--;
        else itemSearchFeedback = "";
        if (profileFeedbackTimer > 0) profileFeedbackTimer--;
        else profileFeedback = "";
    }

    @Override
    public void close() {
        config.saveConfiguration();
        if (client != null) client.setScreen(parent);
    }

    private void updateSuggestions(String query) {
        suggestions.clear();
        if (query.isEmpty()) return;
        String lq = query.toLowerCase();
        Registries.ITEM.getIds().stream()
                .filter(id -> id.getPath().contains(lq) || id.toString().contains(lq))
                .map(Identifier::getPath)
                .distinct()
                .sorted((a, b) -> {
                    boolean aS = a.startsWith(lq), bS = b.startsWith(lq);
                    if (aS && !bS) return -1;
                    if (!aS && bS) return 1;
                    return a.compareTo(b);
                })
                .limit(40)
                .forEach(suggestions::add);
    }

    private void addItemById(String rawId) {
        if (rawId.isEmpty()) {
            setFeedback("§cType an item ID first!", 60);
            return;
        }
        if (!rawId.contains(":")) rawId = "minecraft:" + rawId;
        Identifier id;
        try {
            id = Identifier.of(rawId);
        } catch (Exception e) {
            setFeedback("§cInvalid ID: " + rawId, 80);
            return;
        }
        if (!Registries.ITEM.containsId(id)) {
            setFeedback("§cUnknown item: " + rawId, 80);
            return;
        }
        Item item = Registries.ITEM.get(id);
        if (item == Items.AIR) {
            setFeedback("§cUnknown item: " + rawId, 80);
            return;
        }
        if (config.getItemsToThrow().contains(item)) {
            config.getItemsToThrow().remove(item);
            setFeedback("§eRemoved §f" + rawId, 60);
        } else {
            config.getItemsToThrow().add(item);
            setFeedback("§aAdded §f" + rawId, 60);
        }
        config.saveConfiguration();
    }

    private void setFeedback(String msg, int durationTicks) {
        itemSearchFeedback = msg;
        itemSearchFeedbackTimer = durationTicks;
    }

    private ItemStack[] getPlayerInventory() {
        if (client == null || client.player == null) return null;
        var inv = client.player.getInventory();
        ItemStack[] out = new ItemStack[36];
        for (int i = 0; i < 36; i++) out[i] = inv.getStack(i);
        return out;
    }

    private ButtonWidget addTabButton(String label, int tabIndex, int x, int y, int w, int h) {
        ButtonWidget btn = ButtonWidget.builder(tabText(label, tabIndex), b -> {
            currentTab = tabIndex;
            scrollOffset = 0;
            init();
        }).dimensions(x, y, w, h).build();
        addDrawableChild(btn);
        return btn;
    }

    private Text tabText(String label, int tabIndex) {
        return currentTab == tabIndex
                ? Text.literal(label).formatted(Formatting.RED, Formatting.BOLD)
                : Text.literal(label).formatted(Formatting.GRAY);
    }

    private void addScrollable(ClickableWidget w) {
        scrollableWidgets.add(w);
        addDrawableChild(w);
    }

    private void addLabel(int x, int y, String text) {
        ClickableWidget label = new ClickableWidget(x, y, 150, 20, Text.literal(text)) {
            @Override
            protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
                ctx.drawTextWithShadow(textRenderer, getMessage(), getX(), getY() + 6, 0xFFFFFFFF);
            }

            @Override
            protected void appendClickableNarrations(NarrationMessageBuilder b) {
            }
        };
        addScrollable(label);
    }

    private void addToggle(int x, int y, String label, String tooltip, boolean value, Consumer<Boolean> action) {
        boolean[] state = {value};
        ButtonWidget btn = ButtonWidget.builder(toggleText(label, state[0]), b -> {
                    state[0] = !state[0];
                    action.accept(state[0]);
                    b.setMessage(toggleText(label, state[0]));
                }).dimensions(x, y, 150, 20)
                .tooltip(Tooltip.of(Text.literal("§c" + tooltip)))
                .build();
        addScrollable(btn);
    }


    private Text toggleText(String label, boolean value) {
        if (label.equals("Cleaning Mode")) {
            return Text.literal(label + ": ")
                    .append(value
                            ? Text.literal("WHITELIST").formatted(Formatting.AQUA)
                            : Text.literal("BLACKLIST").formatted(Formatting.DARK_GRAY));
        }
        return Text.literal(label + ": ")
                .append(value
                        ? Text.literal("ON").formatted(Formatting.GREEN)
                        : Text.literal("OFF").formatted(Formatting.DARK_GRAY));
    }

    private void addSlider(int x, int y, String label, float cur, float min, float max, Consumer<Float> action) {
        addScrollable(new GenericSlider(x, y, 150, 20, label, cur, min, max, action));
    }

    private void drawPanel(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, PANEL_BG);
        ctx.fill(x, y, x + 2, y + h, PANEL_BORDER);
        ctx.fill(x + w - 2, y, x + w, y + h, PANEL_BORDER);
    }

    private void drawScrollbar(DrawContext ctx) {
        if (maxScroll <= 0) return;
        int trackX = width - 6, trackY = 58, trackH = height - 90;
        int thumbH = Math.max(20, (int) ((float) trackH * trackH / contentHeight));
        int thumbY = trackY + (int) ((trackH - thumbH) * ((float) scrollOffset / maxScroll));
        ctx.fill(trackX, trackY, width - 2, trackY + trackH, 0x40000000);
        ctx.fill(trackX, thumbY, width - 2, thumbY + thumbH, ACCENT_COLOR);
    }

    private void applyScrollOffset() {
        for (ClickableWidget w : scrollableWidgets) w.setY(w.getY() - scrollOffset);
    }

    private static class GenericSlider extends SliderWidget {
        private final String label;
        private final float min, max;
        private final Consumer<Float> action;

        GenericSlider(int x, int y, int w, int h,
                      String label, float cur, float min, float max,
                      Consumer<Float> action) {
            super(x, y, w, h, Text.empty(), (cur - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.action = action;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            float val = min + (float) (value * (max - min));
            setMessage(Text.literal(label + ": §c" + String.format("%.2fs", val)));
        }

        @Override
        protected void applyValue() {
            action.accept(min + (float) (value * (max - min)));
        }
    }
}