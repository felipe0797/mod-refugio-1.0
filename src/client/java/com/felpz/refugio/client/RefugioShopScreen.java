package com.felpz.refugio.client;

import com.felpz.refugio.shop.ShopCatalog;
import com.felpz.refugio.ui.RefugioUiPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Medieval custom screen for Baldur's shop, backed by the server-authoritative RefugioMenu container. */
public final class RefugioShopScreen extends Screen {
    private static final int[] CATEGORY_ACCENTS = {
            0xFF6B8E3D, 0xFFB76545, 0xFF4C8878, 0xFF5A6B9C, 0xFFC0944B
    };
    private static final int[] HOME_CATEGORY_SLOTS = {10, 12, 14, 16, 28};
    private static final int HOME_SELL_SLOT = 40;
    private static final int[] DETAIL_QUANTITY_SLOTS = {10, 12, 14, 16, 28};
    private static final int[] DETAIL_QUANTITIES = {1, 8, 16, 32, 64};
    private static final int[] SELL_DEPOSIT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final Identifier PARCHMENT_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/parchment.png");
    private static final Identifier WOOD_FRAME_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/wood_frame.png");
    private static final Identifier TITLE_PLAQUE_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/title_plaque.png");
    private static final Identifier PRIMARY_BUTTON_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/button_primary.png");
    private static final Identifier SECONDARY_BUTTON_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/button_close.png");

    private static final FontDescription UI_FONT = new FontDescription.Resource(Identifier.parse("minecraft:uniform"));
    private static final Style INK_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF4A3520);
    private static final Style MUTED_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF80684B);
    private static final Style GOLD_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF87551F);
    private static final Style SUCCESS_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF456B3A);
    private static final Style DANGER_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF9E3B2B);
    private static final Style LIGHT_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFFFFF0D0);
    private static final Style DISABLED_BUTTON_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFFBBAA8D);
    private static final Item EMPTY_SLOT_DECORATION = BuiltInRegistries.ITEM.getValue(
            Identifier.parse("minecraft:gray_stained_glass_pane"));

    private int containerId = -1;
    private String viewName = "SHOP_HOME";
    private int coins;
    private int categoryIndex;
    private String selectedSubcategory = "";
    private String searchTerm = "";
    private int sortMode;
    private int serverPage;
    private int serverTotalPages = 1;
    private int totalItems;
    private String detailItemId = "minecraft:air";
    private long detailUnitPrice;
    private int detailQuantity = 1;
    private long detailTotal;
    private int detailStackLimit = 64;
    private int detailReqClaims;
    private boolean detailCanAction;
    private long sellTotal;
    private int sellAccepted;
    private int sellRejected;
    private int catUnlockMask = 0x1F;
    private int[] catCounts = {0, 0, 0, 0, 0};
    private int[] catReqs = {0, 1, 2, 4, 8};
    private String carriedItemId = "";
    private int carriedCount;
    private List<ShopCatalog.EnchantmentSpec> detailEnchantments = List.of();

    private final List<SubcategoryEntry> subcategories = new ArrayList<>();
    private final List<OfferEntry> offers = new ArrayList<>();
    private final Map<Integer, SellSlotEntry> sellSlots = new HashMap<>();

    private int clientSubPage;
    private int detailScrollOffset;
    private boolean menuBound;
    private boolean displayed;
    private int dragButton = -1;
    private final Set<Integer> draggedSlots = new HashSet<>();

    public RefugioShopScreen(String encodedState) {
        super(Component.translatable("gui.refugio.shop_title"));
        updateState(encodedState);
    }

    public int containerId() {
        return containerId;
    }

    public boolean isDisplayed() {
        return displayed;
    }

    public void markDisplayed() {
        displayed = true;
    }

    @Override
    public void removed() {
        displayed = false;
    }

    public void tickScreen(Minecraft client) {
        if (client.player == null) return;
        AbstractContainerMenu activeMenu = client.player.containerMenu;
        if (activeMenu != null && activeMenu.containerId == containerId) {
            menuBound = true;
        } else if (menuBound && (activeMenu == null || activeMenu == client.player.inventoryMenu)) {
            RefugioClient.clearShopScreen(this);
            if (displayed) {
                client.setScreenAndShow(null);
            }
        }
    }

    public void updateState(String encodedState) {
        String[] parts = encodedState.split("\\t", -1);
        if (parts.length < 27 || !parts[0].equals("SHOP")) return;
        try {
            int newContainerId = Integer.parseInt(parts[1]);
            String newView = parts[2];
            int newServerPage = Integer.parseInt(parts[8]);
            int newCatIndex = Integer.parseInt(parts[4]);
            String newSubcat = parts[5];
            if (newContainerId != containerId || !newView.equals(viewName)
                    || newServerPage != serverPage || newCatIndex != categoryIndex
                    || !newSubcat.equals(selectedSubcategory)) {
                clientSubPage = 0;
                detailScrollOffset = 0;
            }
            if (newContainerId != containerId) {
                menuBound = false;
            }
            containerId = newContainerId;
            viewName = newView;
            coins = Integer.parseInt(parts[3]);
            categoryIndex = Math.max(0, Math.min(4, newCatIndex));
            selectedSubcategory = newSubcat;
            searchTerm = parts[6];
            sortMode = Integer.parseInt(parts[7]);
            serverPage = newServerPage;
            serverTotalPages = Math.max(1, Integer.parseInt(parts[9]));
            totalItems = Math.max(0, Integer.parseInt(parts[10]));
            detailItemId = parts[11];
            detailUnitPrice = Long.parseLong(parts[12]);
            detailQuantity = Integer.parseInt(parts[13]);
            detailTotal = Long.parseLong(parts[14]);
            detailStackLimit = Math.max(1, Integer.parseInt(parts[15]));
            detailReqClaims = Integer.parseInt(parts[16]);
            detailCanAction = "1".equals(parts[17]);
            sellTotal = Long.parseLong(parts[18]);
            sellAccepted = Integer.parseInt(parts[19]);
            sellRejected = Integer.parseInt(parts[20]);
            catUnlockMask = Integer.parseInt(parts[21]);
            parseFixedInts(parts[22], catCounts);
            parseFixedInts(parts[23], catReqs);
            carriedItemId = parts[24];
            carriedCount = Integer.parseInt(parts[25]);

            subcategories.clear();
            offers.clear();
            sellSlots.clear();
            String itemsRaw = parts[26];
            if (!itemsRaw.isEmpty()) {
                String[] entries = itemsRaw.split(";");
                if ("SUBCATEGORIES".equals(viewName)) {
                    for (String entry : entries) {
                        String[] fields = entry.split("\\|", -1);
                        if (fields.length >= 4) {
                            subcategories.add(new SubcategoryEntry(
                                    Integer.parseInt(fields[0]), fields[1],
                                    Integer.parseInt(fields[2]), fields[3]));
                        }
                    }
                } else if ("CATEGORY".equals(viewName) || "SEARCH".equals(viewName)) {
                    for (String entry : entries) {
                        String[] fields = entry.split("\\|", -1);
                        if (fields.length >= 6) {
                            List<ShopCatalog.EnchantmentSpec> ench = fields.length >= 7
                                    ? parseEnchantmentSpecs(fields[6]) : List.of();
                            offers.add(new OfferEntry(
                                    Integer.parseInt(fields[0]), fields[1],
                                    Integer.parseInt(fields[2]), Integer.parseInt(fields[3]),
                                    Integer.parseInt(fields[4]), "1".equals(fields[5]), ench));
                        }
                    }
                } else if ("SELL".equals(viewName)) {
                    for (String entry : entries) {
                        String[] fields = entry.split("\\|", -1);
                        if (fields.length >= 4) {
                            int slot = Integer.parseInt(fields[0]);
                            sellSlots.put(slot, new SellSlotEntry(
                                    slot, fields[1], Integer.parseInt(fields[2]), Integer.parseInt(fields[3])));
                        }
                    }
                }
            }
            detailEnchantments = parts.length >= 28 ? parseEnchantmentSpecs(parts[27]) : List.of();
        } catch (NumberFormatException ignored) {
        }
    }

    private static List<ShopCatalog.EnchantmentSpec> parseEnchantmentSpecs(String raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<ShopCatalog.EnchantmentSpec> result = new ArrayList<>();
        for (String token : raw.split("~")) {
            String[] p = token.split("\\^", -1);
            if (p.length >= 2 && !p[0].isBlank()) {
                try {
                    result.add(new ShopCatalog.EnchantmentSpec(p[0], Integer.parseInt(p[1])));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return List.copyOf(result);
    }

    private static void parseFixedInts(String csv, int[] target) {
        String[] tokens = csv.split(",");
        for (int i = 0; i < Math.min(tokens.length, target.length); i++) {
            try {
                target[i] = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x74090604);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        ShopLayout layout = computeLayout();
        drawParchmentAndWoodFrame(graphics, layout.panel);
        drawHeader(graphics, mouseX, mouseY, layout);

        HoveredSlot hoveredSlot = null;
        switch (viewName) {
            case "SHOP_HOME" -> hoveredSlot = drawHomeView(graphics, mouseX, mouseY, layout);
            case "SUBCATEGORIES" -> {
                drawNavigationHeader(graphics, mouseX, mouseY, layout);
                hoveredSlot = drawSubcategoriesView(graphics, mouseX, mouseY, layout);
            }
            case "CATEGORY", "SEARCH" -> {
                drawNavigationHeader(graphics, mouseX, mouseY, layout);
                hoveredSlot = drawOffersView(graphics, mouseX, mouseY, layout);
            }
            case "PRODUCT_DETAIL", "SELL_CONFIRM" -> {
                drawNavigationHeader(graphics, mouseX, mouseY, layout);
                hoveredSlot = drawDetailView(graphics, mouseX, mouseY, layout);
            }
            case "SELL" -> {
                drawNavigationHeader(graphics, mouseX, mouseY, layout);
                hoveredSlot = drawSellView(graphics, mouseX, mouseY, layout);
            }
            default -> hoveredSlot = drawHomeView(graphics, mouseX, mouseY, layout);
        }

        ItemStack carried = currentCarriedStack();
        if (!carried.isEmpty()) {
            drawItemWithCount(graphics, carried, mouseX - 8, mouseY - 8, carried.getCount());
        } else if (hoveredSlot != null && !hoveredSlot.stack.isEmpty()) {
            drawItemTooltip(graphics, hoveredSlot.stack, hoveredSlot.extraLines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int btn = event.button();
        if (btn != 0 && btn != 1) return super.mouseClicked(event, doubleClick);
        double mx = event.x();
        double my = event.y();
        ShopLayout layout = computeLayout();

        if (layout.closeButton.contains(mx, my)) {
            closeShop();
            return true;
        }

        if ("SHOP_HOME".equals(viewName)) {
            for (SlotRect card : layout.homeCards) {
                if (card.rect.contains(mx, my)) {
                    sendSlotClick(card.slotId, 0, false);
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        if (layout.backButton.contains(mx, my)) {
            sendSlotClick(45, 0, false);
            return true;
        }

        if ("SUBCATEGORIES".equals(viewName)) {
            if (layout.searchButton.contains(mx, my)) {
                sendSlotClick(47, 0, false);
                return true;
            }
            if (layout.sellNavButton.contains(mx, my)) {
                sendSlotClick(53, 0, false);
                return true;
            }
            if (layout.prevPageButton.contains(mx, my)) {
                stepPage(-1, layout);
                return true;
            }
            if (layout.nextPageButton.contains(mx, my)) {
                stepPage(1, layout);
                return true;
            }
            for (SlotRect card : layout.gridCards) {
                if (card.rect.contains(mx, my)) {
                    sendSlotClick(card.slotId, 0, false);
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        if ("CATEGORY".equals(viewName) || "SEARCH".equals(viewName)) {
            if (layout.searchButton.contains(mx, my)) {
                sendSlotClick(47, 0, false);
                return true;
            }
            if (layout.sortButton.contains(mx, my)) {
                sendSlotClick(50, 0, false);
                return true;
            }
            if (layout.sellNavButton.contains(mx, my)) {
                sendSlotClick(53, 0, false);
                return true;
            }
            if (layout.prevPageButton.contains(mx, my)) {
                stepPage(-1, layout);
                return true;
            }
            if (layout.nextPageButton.contains(mx, my)) {
                stepPage(1, layout);
                return true;
            }
            for (SlotRect card : layout.gridCards) {
                if (card.rect.contains(mx, my)) {
                    sendSlotClick(card.slotId, btn, false);
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        if ("PRODUCT_DETAIL".equals(viewName) || "SELL_CONFIRM".equals(viewName)) {
            for (SlotRect qBtn : layout.detailQtyButtons) {
                if (qBtn.rect.contains(mx, my)) {
                    sendSlotClick(qBtn.slotId, 0, false);
                    return true;
                }
            }
            if (layout.confirmButton.contains(mx, my)) {
                sendSlotClick(49, 0, false);
                return true;
            }
            if (layout.cancelButton.contains(mx, my)) {
                sendSlotClick(53, 0, false);
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        if ("SELL".equals(viewName)) {
            if (layout.sellReturnButton.contains(mx, my)) {
                sendSlotClick(46, 0, false);
                return true;
            }
            if (layout.confirmButton.contains(mx, my)) {
                sendSlotClick(53, 0, false);
                return true;
            }
            boolean shift = isShiftDown();
            for (SlotRect slot : layout.sellDepositSlots) {
                if (slot.rect.contains(mx, my)) {
                    dragButton = btn;
                    draggedSlots.clear();
                    draggedSlots.add(slot.slotId);
                    sendSlotClick(slot.slotId, btn, shift);
                    return true;
                }
            }
            for (SlotRect slot : layout.sellInventorySlots) {
                if (slot.rect.contains(mx, my)) {
                    dragButton = btn;
                    draggedSlots.clear();
                    draggedSlots.add(slot.slotId);
                    sendSlotClick(slot.slotId, btn, shift);
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if ("SELL".equals(viewName) && (dragButton == 0 || dragButton == 1) && !currentCarriedStack().isEmpty()) {
            double mx = event.x();
            double my = event.y();
            ShopLayout layout = computeLayout();
            for (SlotRect slot : layout.sellDepositSlots) {
                if (slot.rect.contains(mx, my) && draggedSlots.add(slot.slotId)) {
                    if (getSellSlotStack(slot.slotId).isEmpty() || dragButton == 1) {
                        sendSlotClick(slot.slotId, 1, false);
                    }
                    return true;
                }
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragButton != -1) {
            dragButton = -1;
            draggedSlots.clear();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0.0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        ShopLayout layout = computeLayout();
        int delta = -(int) Math.signum(verticalAmount);
        if (("SUBCATEGORIES".equals(viewName) || "CATEGORY".equals(viewName) || "SEARCH".equals(viewName))
                && layout.contentArea.contains(mouseX, mouseY)) {
            stepPage(delta, layout);
            return true;
        }
        if (("PRODUCT_DETAIL".equals(viewName) || "SELL_CONFIRM".equals(viewName))
                && layout.detailInfoBox.contains(mouseX, mouseY)) {
            detailScrollOffset = Math.max(0, detailScrollOffset + delta * (font.lineHeight + 2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        closeShop();
    }

    private void stepPage(int direction, ShopLayout layout) {
        if (direction < 0) {
            if (clientSubPage > 0) {
                clientSubPage--;
            } else if (serverPage > 0) {
                sendSlotClick(46, 0, false);
            }
        } else if (direction > 0) {
            if (clientSubPage + 1 < layout.clientSubPageCount) {
                clientSubPage++;
            } else if (serverPage + 1 < serverTotalPages) {
                sendSlotClick(52, 0, false);
            }
        }
    }

    private void sendSlotClick(int slotId, int mouseButton, boolean quickMove) {
        if (containerId < 0) return;
        String payload = "SHOP_CLICK\t" + containerId + "\t" + slotId + "\t"
                + mouseButton + "\t" + (quickMove ? "1" : "0");
        ClientPlayNetworking.send(new RefugioUiPayload(payload));
    }

    private void closeShop() {
        if (containerId >= 0) {
            ClientPlayNetworking.send(new RefugioUiPayload("SHOP_CLOSE\t" + containerId));
        }
        RefugioClient.clearShopScreen(this);
        if (minecraft != null) {
            minecraft.setScreenAndShow(null);
        }
    }

    private static boolean isShiftDown() {
        long window = GLFW.glfwGetCurrentContext();
        if (window == 0L) return false;
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private ShopLayout computeLayout() {
        int marginX = width < 400 ? 6 : (width < 640 ? 12 : 18);
        int marginY = height < 240 ? 4 : (height < 360 ? 10 : 16);
        int availW = Math.max(180, width - marginX * 2);
        int availH = Math.max(136, height - marginY * 2);

        double targetAspect = 1.72;
        int maxW = Math.min(860, availW);
        int maxH = Math.min(520, availH);
        int pw = maxW;
        int ph = maxH;
        if (pw > (int) Math.round(ph * 1.84)) {
            pw = Math.max(Math.min(maxW, 320), (int) Math.round(ph * targetAspect));
        } else if (ph > (int) Math.round(pw / 1.36)) {
            ph = Math.max(Math.min(maxH, 210), (int) Math.round(pw / 1.48));
        }
        pw = Math.min(maxW, pw);
        ph = Math.min(maxH, ph);
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;
        Rect panel = new Rect(px, py, pw, ph);

        int insetX = Math.max(14, (int) Math.ceil(pw * 0.068));
        int insetY = Math.max(12, (int) Math.ceil(ph * 0.104));
        int innerX = px + insetX;
        int innerY = py + insetY;
        int innerW = Math.max(120, pw - insetX * 2);
        int innerH = Math.max(90, ph - insetY * 2);
        Rect inner = new Rect(innerX, innerY, innerW, innerH);

        int headerH = innerH < 140 ? 16 : (innerH < 175 ? 18 : (innerH < 220 ? 22 : (innerH < 280 ? 26 : 30)));
        int headerY = innerY;
        int closeSize = Math.max(14, Math.min(22, headerH - (headerH <= 18 ? 0 : 2)));
        int closeX = inner.right() - closeSize;
        int closeY = headerY + (headerH - closeSize) / 2;
        Rect closeButton = new Rect(closeX, closeY, closeSize, closeSize);

        int headerGap = innerW < 340 ? 4 : 8;
        Component fullBalance = Component.translatable("gui.refugio.balance_value", coins).withStyle(LIGHT_STYLE);
        Component shortBalance = Component.literal(Integer.toString(coins)).withStyle(LIGHT_STYLE);
        boolean compactBalance = innerW < 450;
        int badgeW = compactBalance
                ? Math.max(48, Math.min(86, font.width(shortBalance) + 28))
                : Math.min(164, Math.max(106, font.width(fullBalance) + 34));
        int badgeH = closeSize;
        int badgeX = closeX - headerGap - badgeW;
        int badgeY = headerY + (headerH - badgeH) / 2;
        Rect balanceBadge = new Rect(badgeX, badgeY, badgeW, badgeH);

        Component titleComp = headerTitleComponent().withStyle(LIGHT_STYLE);
        int maxBannerRight = badgeX - headerGap;
        int availBannerArea = Math.max(64, maxBannerRight - innerX);
        int idealBannerW = Math.min(340, Math.max(140, font.width(titleComp) + 44));
        int centeredBannerX = px + (pw - idealBannerW) / 2;
        boolean canCenterBanner = innerW >= 460
                && centeredBannerX >= innerX
                && centeredBannerX + idealBannerW <= maxBannerRight;
        int bannerW = canCenterBanner ? idealBannerW : Math.min(idealBannerW, availBannerArea);
        int bannerX = canCenterBanner ? centeredBannerX : innerX;
        Rect titleBanner = new Rect(bannerX, headerY, bannerW, headerH);

        int sectionGap = innerH < 145 ? 2 : (innerH < 185 ? 3 : (innerH < 230 ? 5 : 7));
        int subHeaderTop = headerY + headerH + sectionGap;

        // Home Grid (6 cards: 5 categories + 1 sell)
        int homeCols = innerW >= 320 ? 3 : 2;
        int homeRows = 6 / homeCols;
        int homeTop = subHeaderTop + (innerH >= 190 ? font.lineHeight + sectionGap : 0);
        int homeAvailH = Math.max(36, inner.bottom() - homeTop);
        int homeGapX = innerW < 340 ? 6 : 10;
        int homeGapY = homeAvailH < 95 ? 4 : 8;
        int homeCardW = Math.max(44, (innerW - homeGapX * (homeCols - 1)) / homeCols);
        int homeCardH = Math.max(18, Math.min(92, (homeAvailH - homeGapY * (homeRows - 1)) / homeRows));
        int totalHomeH = homeRows * homeCardH + (homeRows - 1) * homeGapY;
        int homeStartY = homeTop + Math.max(0, (homeAvailH - totalHomeH) / 2);
        List<SlotRect> homeCards = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            int col = i % homeCols;
            int row = i / homeCols;
            int slotId = i < 5 ? HOME_CATEGORY_SLOTS[i] : HOME_SELL_SLOT;
            homeCards.add(new SlotRect(slotId, new Rect(
                    innerX + col * (homeCardW + homeGapX),
                    homeStartY + row * (homeCardH + homeGapY),
                    homeCardW, homeCardH)));
        }

        // Navigation bar for non-home views
        int navH = innerH < 140 ? 14 : (innerH < 175 ? 16 : (innerH < 220 ? 19 : 23));
        int navY = subHeaderTop;
        int backW = Math.max(54, Math.min(104, innerW * 22 / 100));
        Rect backButton = new Rect(innerX, navY, backW, navH);

        int navBtnGap = innerW < 340 ? 4 : 6;
        int sellNavW = ("SUBCATEGORIES".equals(viewName) || "CATEGORY".equals(viewName) || "SEARCH".equals(viewName))
                ? Math.max(52, Math.min(92, innerW * 19 / 100)) : 0;
        Rect sellNavButton = sellNavW > 0
                ? new Rect(inner.right() - sellNavW, navY, sellNavW, navH)
                : new Rect(inner.right(), navY, 0, 0);

        int sortBtnW = ("CATEGORY".equals(viewName) || "SEARCH".equals(viewName))
                ? Math.max(58, Math.min(112, innerW * 23 / 100)) : 0;
        Rect sortButton = sortBtnW > 0
                ? new Rect(sellNavButton.x - navBtnGap - sortBtnW, navY, sortBtnW, navH)
                : new Rect(sellNavButton.x, navY, 0, 0);

        int searchRightAnchor = sortBtnW > 0 ? sortButton.x : sellNavButton.x;
        int searchBtnW = ("SUBCATEGORIES".equals(viewName) || "CATEGORY".equals(viewName) || "SEARCH".equals(viewName))
                ? Math.max(52, Math.min(96, innerW * 19 / 100)) : 0;
        Rect searchButton = searchBtnW > 0
                ? new Rect(searchRightAnchor - navBtnGap - searchBtnW, navY, searchBtnW, navH)
                : new Rect(searchRightAnchor, navY, 0, 0);

        int dividerY = navY + navH + Math.max(2, sectionGap - 1);
        int contentTop = dividerY + 3;
        int contentAvailH = Math.max(36, inner.bottom() - contentTop);
        Rect contentArea = new Rect(innerX, contentTop, innerW, contentAvailH);

        // Grid cards & pagination for SUBCATEGORIES, CATEGORY, SEARCH
        int pageBtnH = contentAvailH < 105 ? 14 : (contentAvailH < 140 ? 16 : 20);
        int pageBtnW = contentAvailH < 105 ? 22 : 30;
        int pageControlsY = inner.bottom() - pageBtnH;
        int pageCenter = innerX + innerW / 2;
        int pageHalfSpan = Math.min(82, Math.max(48, innerW / 6));
        Rect prevPageButton = new Rect(pageCenter - pageHalfSpan - pageBtnW, pageControlsY, pageBtnW, pageBtnH);
        Rect nextPageButton = new Rect(pageCenter + pageHalfSpan, pageControlsY, pageBtnW, pageBtnH);

        int gridBottom = pageControlsY - (contentAvailH < 105 ? 2 : 5);
        int gridAvailH = Math.max(24, gridBottom - contentTop);
        int gridCols = innerW >= 520 ? 4 : (innerW >= 320 ? 3 : 2);
        int minDesiredCardH = "SUBCATEGORIES".equals(viewName) ? 24 : 30;
        int gridRows = gridAvailH >= minDesiredCardH * 3 + 12 ? 3 : (gridAvailH >= minDesiredCardH * 2 + 4 ? 2 : 1);
        int cardsPerView = Math.max(1, gridCols * gridRows);

        int itemCountOnServerPage = "SUBCATEGORIES".equals(viewName) ? subcategories.size() : offers.size();
        int clientSubPageCount = Math.max(1, (itemCountOnServerPage + cardsPerView - 1) / cardsPerView);
        clientSubPage = Math.max(0, Math.min(clientSubPage, clientSubPageCount - 1));
        int subStart = clientSubPage * cardsPerView;
        int visibleCount = Math.max(0, Math.min(cardsPerView, itemCountOnServerPage - subStart));

        int gridGapX = innerW < 340 ? 5 : 8;
        int gridGapY = gridRows == 1 ? 0 : (gridAvailH < 80 ? 3 : 6);
        int gridCardW = Math.max(44, (innerW - gridGapX * (gridCols - 1)) / gridCols);
        int maxGridCardH = Math.max(16, (gridAvailH - gridGapY * (gridRows - 1)) / gridRows);
        int gridCardH = Math.min(68, maxGridCardH);
        int actualRowsUsed = Math.max(1, (Math.max(1, visibleCount) + gridCols - 1) / gridCols);
        int totalUsedGridH = actualRowsUsed * gridCardH + Math.max(0, actualRowsUsed - 1) * gridGapY;
        int gridStartY = contentTop + Math.max(0, (gridAvailH - totalUsedGridH) / 2);

        List<SlotRect> gridCards = new ArrayList<>(visibleCount);
        for (int i = 0; i < visibleCount; i++) {
            int col = i % gridCols;
            int row = i / gridCols;
            int idx = subStart + i;
            int slotId = "SUBCATEGORIES".equals(viewName)
                    ? subcategories.get(idx).slotId
                    : offers.get(idx).slotId;
            gridCards.add(new SlotRect(slotId, new Rect(
                    innerX + col * (gridCardW + gridGapX),
                    gridStartY + row * (gridCardH + gridGapY),
                    gridCardW, gridCardH)));
        }

        // PRODUCT_DETAIL and SELL_CONFIRM layout
        int detailBtnH = contentAvailH < 95 ? 14 : (contentAvailH < 130 ? 18 : 24);
        int detailBtnY = inner.bottom() - detailBtnH;
        int detailBtnGap = innerW < 340 ? 6 : 12;
        int detailBtnW = Math.max(40, (innerW - detailBtnGap) / 2);
        Rect confirmButton = new Rect(innerX, detailBtnY, detailBtnW, detailBtnH);
        Rect cancelButton = new Rect(confirmButton.right() + detailBtnGap, detailBtnY, detailBtnW, detailBtnH);

        int detailBodyH = Math.max(28, detailBtnY - contentTop - (contentAvailH < 100 ? 3 : 6));
        int leftInfoW = Math.max(70, innerW * 52 / 100);
        int rightQtyX = innerX + leftInfoW + (innerW < 340 ? 6 : 10);
        int rightQtyW = Math.max(50, inner.right() - rightQtyX);
        Rect detailInfoBox = new Rect(innerX, contentTop, leftInfoW, detailBodyH);
        Rect detailQtyBox = new Rect(rightQtyX, contentTop, rightQtyW, detailBodyH);

        List<SlotRect> detailQtyButtons = new ArrayList<>(7);
        int qtyCols = 2;
        int totalQtyButtons = "SELL_CONFIRM".equals(viewName) ? 7 : 6;
        int qtyRows = (totalQtyButtons + qtyCols - 1) / qtyCols;
        int qtyGapX = 4;
        int qtyGapY = detailBodyH < 75 ? 2 : 4;
        int qtyBtnW = Math.max(24, (rightQtyW - qtyGapX) / 2);
        int qtyBtnH = Math.max(12, Math.min(24, (detailBodyH - qtyGapY * (qtyRows - 1)) / qtyRows));
        int totalQtyH = qtyRows * qtyBtnH + (qtyRows - 1) * qtyGapY;
        int qtyStartY = contentTop + Math.max(0, (detailBodyH - totalQtyH) / 2);
        for (int i = 0; i < 5; i++) {
            int col = i % 2;
            int row = i / 2;
            detailQtyButtons.add(new SlotRect(DETAIL_QUANTITY_SLOTS[i], new Rect(
                    rightQtyX + col * (qtyBtnW + qtyGapX),
                    qtyStartY + row * (qtyBtnH + qtyGapY),
                    qtyBtnW, qtyBtnH)));
        }
        detailQtyButtons.add(new SlotRect(30, new Rect(
                rightQtyX + qtyBtnW + qtyGapX,
                qtyStartY + 2 * (qtyBtnH + qtyGapY),
                qtyBtnW, qtyBtnH)));
        if ("SELL_CONFIRM".equals(viewName)) {
            detailQtyButtons.add(new SlotRect(32, new Rect(
                    rightQtyX,
                    qtyStartY + 3 * (qtyBtnH + qtyGapY),
                    rightQtyW, qtyBtnH)));
        }

        // SELL view layout (21 deposit slots 7x3 on left + 36 inventory slots 9x4 on right)
        int sellActionBtnH = contentAvailH < 95 ? 14 : (contentAvailH < 130 ? 17 : 22);
        int sellActionY = inner.bottom() - sellActionBtnH;
        Rect sellReturnButton = new Rect(innerX, sellActionY, detailBtnW, sellActionBtnH);
        Rect sellConfirmButton = new Rect(sellReturnButton.right() + detailBtnGap, sellActionY, detailBtnW, sellActionBtnH);
        if ("SELL".equals(viewName)) {
            confirmButton = sellConfirmButton;
        }

        int sellBodyTop = contentTop + (contentAvailH >= 110 ? font.lineHeight + 2 : 0);
        int sellBodyBottom = sellActionY - (contentAvailH < 100 ? 2 : 4);
        int sellBodyH = Math.max(64, sellBodyBottom - sellBodyTop);
        int maxSlotByW = Math.max(16, (innerW - 8) / 16);
        int maxSlotByH = Math.max(16, (sellBodyH - 2) / 4);
        int slotSize = Math.max(16, Math.min(20, Math.min(maxSlotByW, maxSlotByH)));
        int depGridW = 7 * slotSize;
        int invGridW = 9 * slotSize;
        int columnsGap = Math.max(6, Math.min(20, innerW - depGridW - invGridW));
        int totalSellGridsW = depGridW + columnsGap + invGridW;
        int sellLeftX = innerX + Math.max(0, (innerW - totalSellGridsW) / 2);
        int sellRightX = sellLeftX + depGridW + columnsGap;

        List<SlotRect> sellDepositSlots = new ArrayList<>(21);
        for (int i = 0; i < SELL_DEPOSIT_SLOTS.length; i++) {
            int col = i % 7;
            int row = i / 7;
            sellDepositSlots.add(new SlotRect(SELL_DEPOSIT_SLOTS[i], new Rect(
                    sellLeftX + col * slotSize,
                    sellBodyTop + row * slotSize,
                    slotSize, slotSize)));
        }
        int depBottom = sellBodyTop + 3 * slotSize;
        int summaryTop = depBottom + 2;
        int summaryH = Math.max(0, sellBodyBottom - summaryTop);
        Rect sellSummaryBox = new Rect(sellLeftX, summaryTop, depGridW, summaryH);

        List<SlotRect> sellInventorySlots = new ArrayList<>(36);
        int hotbarGap = sellBodyH >= 4 * slotSize + 4 ? 3 : 1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotId = 54 + row * 9 + col;
                sellInventorySlots.add(new SlotRect(slotId, new Rect(
                        sellRightX + col * slotSize,
                        sellBodyTop + row * slotSize,
                        slotSize, slotSize)));
            }
        }
        int hotbarY = sellBodyTop + 3 * slotSize + hotbarGap;
        for (int col = 0; col < 9; col++) {
            int slotId = 81 + col;
            sellInventorySlots.add(new SlotRect(slotId, new Rect(
                    sellRightX + col * slotSize,
                    hotbarY,
                    slotSize, slotSize)));
        }

        return new ShopLayout(
                panel, inner, titleBanner, balanceBadge, closeButton,
                compactBalance, subHeaderTop, homeCards,
                backButton, searchButton, sortButton, sellNavButton,
                dividerY, contentArea, prevPageButton, nextPageButton,
                clientSubPageCount, subStart, gridCards,
                detailInfoBox, detailQtyBox, detailQtyButtons,
                confirmButton, cancelButton,
                sellLeftX, sellRightX, sellBodyTop, slotSize,
                sellDepositSlots, sellInventorySlots, sellSummaryBox, sellReturnButton
        );
    }

    private MutableComponent headerTitleComponent() {
        return switch (viewName) {
            case "SEARCH" -> Component.translatable("gui.refugio.search_results");
            case "SELL", "SELL_CONFIRM" -> Component.translatable("gui.refugio.sell_title");
            default -> Component.translatable("gui.refugio.shop_title");
        };
    }

    private void drawParchmentAndWoodFrame(GuiGraphicsExtractor graphics, Rect panel) {
        int insetX = Math.max(1, (int) Math.round(panel.w * 0.052));
        int insetY = Math.max(1, (int) Math.round(panel.h * 0.082));
        blitFull(graphics, PARCHMENT_TEXTURE, panel.x + insetX, panel.y + insetY,
                Math.max(1, panel.w - insetX * 2), Math.max(1, panel.h - insetY * 2));
        blitFull(graphics, WOOD_FRAME_TEXTURE, panel.x, panel.y, panel.w, panel.h);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY, ShopLayout layout) {
        Component title = headerTitleComponent().withStyle(LIGHT_STYLE);
        Rect banner = layout.titleBanner;
        if (banner.w > 0 && banner.h > 0) {
            graphics.blit(TITLE_PLAQUE_TEXTURE, banner.x, banner.y, banner.right(), banner.bottom(),
                    0.02F, 0.98F, 0.32F, 0.66F);
        }
        List<FormattedCharSequence> titleLines = font.split(title, Math.max(16, banner.w - 24));
        int maxLines = banner.h >= font.lineHeight * 2 + 4 ? 2 : 1;
        int count = Math.min(maxLines, titleLines.size());
        int titleY = banner.y + Math.max(0, (banner.h - count * font.lineHeight) / 2);
        for (int i = 0; i < count; i++) {
            FormattedCharSequence line = titleLines.get(i);
            graphics.textRenderer().accept(banner.x + (banner.w - font.width(line)) / 2,
                    titleY + i * font.lineHeight, line);
        }
        if (banner.x > layout.inner.x + 14) {
            drawDiamond(graphics, banner.x - 8, banner.y + banner.h / 2, 3, 0xFFB89455);
            drawDiamond(graphics, banner.right() + 8, banner.y + banner.h / 2, 3, 0xFFB89455);
        }
        drawBalanceBadge(graphics, layout.balanceBadge, layout.compactBalance);
        drawClose(graphics, mouseX, mouseY, layout.closeButton);
    }

    private void drawBalanceBadge(GuiGraphicsExtractor graphics, Rect badge, boolean compact) {
        graphics.fill(badge.x, badge.y, badge.right(), badge.bottom(), 0xFF56361F);
        border(graphics, badge.x, badge.y, badge.w, badge.h, 0xFFC39A59);
        if (badge.h >= 18) {
            graphics.fill(badge.x + 2, badge.y + 2, badge.right() - 2, badge.y + 4, 0xFF79502A);
        }
        int coinY = badge.y + Math.max(1, (badge.h - 12) / 2);
        drawCoinIcon(graphics, badge.x + 5, coinY);
        MutableComponent balance = compact ? Component.literal(Integer.toString(coins))
                : Component.translatable("gui.refugio.balance_value", coins);
        List<FormattedCharSequence> lines = font.split(balance.withStyle(LIGHT_STYLE), Math.max(8, badge.w - 24));
        if (!lines.isEmpty()) {
            FormattedCharSequence line = lines.getFirst();
            graphics.textRenderer().accept(badge.x + 20, badge.y + (badge.h - font.lineHeight) / 2, line);
        }
    }

    private static void drawCoinIcon(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x + 4, y, x + 10, y + 2, 0xFFFFE79A);
        graphics.fill(x + 2, y + 2, x + 12, y + 10, 0xFFD99B36);
        graphics.fill(x + 4, y + 10, x + 10, y + 12, 0xFF9C6424);
        graphics.fill(x + 4, y + 3, x + 8, y + 8, 0xFFFFD76A);
        graphics.fill(x + 8, y + 5, x + 10, y + 8, 0xFFF2B847);
    }

    private HoveredSlot drawHomeView(GuiGraphicsExtractor graphics, int mouseX, int mouseY, ShopLayout layout) {
        if (layout.inner.h >= 190) {
            Component subtitle = Component.translatable("entity.refugio.merchant").withStyle(MUTED_STYLE);
            int subW = font.width(subtitle);
            int subX = layout.inner.x + (layout.inner.w - subW) / 2;
            graphics.textRenderer().accept(subX, layout.subHeaderTop, subtitle);
            if (subW + 28 < layout.inner.w) {
                drawDiamond(graphics, subX - 10, layout.subHeaderTop + font.lineHeight / 2, 2, 0xFFB89455);
                drawDiamond(graphics, subX + subW + 10, layout.subHeaderTop + font.lineHeight / 2, 2, 0xFFB89455);
            }
        }
        HoveredSlot hovered = null;
        for (int i = 0; i < layout.homeCards.size(); i++) {
            SlotRect slotRect = layout.homeCards.get(i);
            Rect card = slotRect.rect;
            boolean isHovered = card.contains(mouseX, mouseY);
            if (i < 5) {
                boolean unlocked = (catUnlockMask & (1 << i)) != 0;
                ShopCatalog.Category cat = ShopCatalog.category(i);
                String iconId = unlocked ? cat.iconItem() : "minecraft:barrier";
                ItemStack icon = createStack(iconId, 1);
                Component title = Component.translatable(cat.titleKey())
                        .withStyle(unlocked ? INK_STYLE : MUTED_STYLE);
                Component sub = unlocked
                        ? Component.translatable("gui.refugio.category_count", catCounts[i]).withStyle(MUTED_STYLE)
                        : Component.translatable("gui.refugio.category_requires", catReqs[i]).withStyle(DANGER_STYLE);
                int accent = unlocked ? CATEGORY_ACCENTS[i % CATEGORY_ACCENTS.length] : 0xFF8A8274;
                drawOverviewCard(graphics, card, icon, title, sub, !unlocked, isHovered, accent);
                if (isHovered) {
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.translatable("gui.refugio.category_count", catCounts[i]).withStyle(MUTED_STYLE));
                    if (!unlocked) {
                        lore.add(Component.translatable("gui.refugio.category_requires", catReqs[i]).withStyle(DANGER_STYLE));
                    }
                    ItemStack tipStack = icon.copy();
                    tipStack.set(DataComponents.CUSTOM_NAME, Component.translatable(cat.titleKey()).withStyle(INK_STYLE));
                    hovered = new HoveredSlot(tipStack, lore);
                }
            } else {
                ItemStack sellIcon = createStack("minecraft:barrel", 1);
                Component title = Component.translatable("gui.refugio.sell").withStyle(GOLD_STYLE);
                Component sub = Component.translatable("gui.refugio.sell_lore").withStyle(MUTED_STYLE);
                drawOverviewCard(graphics, card, sellIcon, title, sub, false, isHovered, 0xFF24504F);
                if (isHovered) {
                    ItemStack tipStack = sellIcon.copy();
                    tipStack.set(DataComponents.CUSTOM_NAME, title);
                    hovered = new HoveredSlot(tipStack, List.of(sub));
                }
            }
        }
        return hovered;
    }

    private void drawOverviewCard(GuiGraphicsExtractor graphics, Rect card, ItemStack icon,
                                  Component title, Component subtitle, boolean locked,
                                  boolean hovered, int accent) {
        int x = card.x;
        int y = card.y;
        int w = card.w;
        int h = card.h;
        graphics.fill(x, y, x + w, y + h, locked ? 0xFFD4C7AD : hovered ? 0xFFF5E4C0 : 0xFFEAD8B2);
        border(graphics, x, y, w, h, locked ? 0xFF8E806A : hovered ? 0xFFD3A34F : 0xFF987348);
        graphics.fill(x + 3, y + 3, x + 6, y + h - 3, accent);

        if (h >= 54 && w >= 72) {
            int iconY = y + (h >= 68 ? 7 : 4);
            int centerX = x + w / 2;
            if (h >= 66) {
                graphics.fill(centerX - 11, iconY - 2, centerX + 11, iconY + 20, 0x30987348);
                border(graphics, centerX - 11, iconY - 2, 22, 22, 0x70987348);
            }
            graphics.item(icon, centerX - 8, iconY);
            int textTop = iconY + (h >= 66 ? 21 : 17);
            int subY = y + h - font.lineHeight - 3;
            int availTitleH = Math.max(font.lineHeight, subY - textTop - 1);
            List<FormattedCharSequence> titleLines = font.split(title, Math.max(16, w - 14));
            int maxLines = availTitleH >= font.lineHeight * 2 ? 2 : 1;
            int count = Math.min(maxLines, titleLines.size());
            int startY = textTop + Math.max(0, (availTitleH - count * font.lineHeight) / 2);
            for (int i = 0; i < count; i++) {
                FormattedCharSequence line = titleLines.get(i);
                graphics.textRenderer().accept(x + (w - font.width(line)) / 2, startY + i * font.lineHeight, line);
            }
            List<FormattedCharSequence> subLines = font.split(subtitle, Math.max(16, w - 12));
            if (!subLines.isEmpty() && subY >= startY + count * font.lineHeight) {
                FormattedCharSequence subLine = subLines.getFirst();
                graphics.textRenderer().accept(x + (w - font.width(subLine)) / 2, subY, subLine);
            }
            return;
        }

        int iconX = x + 9;
        int iconY = y + Math.max(1, (h - 16) / 2);
        graphics.item(icon, iconX, iconY);
        int textX = iconX + 19;
        int textW = Math.max(16, w - (textX - x) - 6);
        List<FormattedCharSequence> nameLines = font.split(title, textW);
        List<FormattedCharSequence> subLines = font.split(subtitle, textW);
        boolean showSub = h >= font.lineHeight * 2 + 4 && !subLines.isEmpty();
        int blockH = font.lineHeight * (showSub ? 2 : 1) + (showSub ? 1 : 0);
        int startY = y + Math.max(1, (h - blockH) / 2);
        if (!nameLines.isEmpty()) {
            graphics.textRenderer().accept(textX, startY, nameLines.getFirst());
        }
        if (showSub) {
            graphics.textRenderer().accept(textX, startY + font.lineHeight + 1, subLines.getFirst());
        }
    }

    private void drawNavigationHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY, ShopLayout layout) {
        Rect back = layout.backButton;
        drawButton(graphics, back.x, back.y, back.w, back.h,
                Component.translatable("gui.refugio.back"), mouseX, mouseY, true, 0xFF293A48);

        if (layout.searchButton.w > 0) {
            drawButton(graphics, layout.searchButton.x, layout.searchButton.y,
                    layout.searchButton.w, layout.searchButton.h,
                    Component.translatable("gui.refugio.search"), mouseX, mouseY, true, 0xFF293A48);
        }
        if (layout.sortButton.w > 0) {
            Component sortLabel = Component.translatable("gui.refugio.sort_mode." + sortMode);
            drawButton(graphics, layout.sortButton.x, layout.sortButton.y,
                    layout.sortButton.w, layout.sortButton.h,
                    sortLabel, mouseX, mouseY, true, 0xFF293A48);
        }
        if (layout.sellNavButton.w > 0) {
            drawButton(graphics, layout.sellNavButton.x, layout.sellNavButton.y,
                    layout.sellNavButton.w, layout.sellNavButton.h,
                    Component.translatable("gui.refugio.sell"), mouseX, mouseY, true, 0xFF24504F);
        }

        int rightLimit = layout.searchButton.w > 0 ? layout.searchButton.x - 6 : layout.inner.right();
        int crumbX = back.right() + (layout.inner.w < 340 ? 5 : 8);
        int availCrumbW = rightLimit - crumbX;
        if (availCrumbW >= 36) {
            Component crumb = currentBreadcrumb().withStyle(GOLD_STYLE);
            List<FormattedCharSequence> lines = font.split(crumb, availCrumbW);
            if (!lines.isEmpty()) {
                graphics.textRenderer().accept(crumbX, back.y + (back.h - font.lineHeight) / 2, lines.getFirst());
            }
        }
        graphics.fill(layout.inner.x, layout.dividerY, layout.inner.right(), layout.dividerY + 1, 0xFFAC936C);
    }

    private MutableComponent currentBreadcrumb() {
        return switch (viewName) {
            case "SUBCATEGORIES" -> Component.translatable("gui.refugio.category." + categoryIndex);
            case "CATEGORY" -> selectedSubcategory == null || selectedSubcategory.isBlank()
                    ? Component.translatable("gui.refugio.category." + categoryIndex)
                    : Component.translatable("gui.refugio.subcategory." + selectedSubcategory);
            case "SEARCH" -> Component.translatable("gui.refugio.search_query", searchTerm.isBlank() ? "—" : searchTerm);
            case "PRODUCT_DETAIL", "SELL_CONFIRM" -> {
                ItemStack preview = getMenuSlotItem(22);
                String name = !preview.isEmpty() ? preview.getHoverName().getString()
                        : createStack(detailItemId, 1).getHoverName().getString();
                yield Component.literal(name);
            }
            case "SELL" -> Component.translatable("gui.refugio.sell_deposit_hint");
            default -> Component.translatable("gui.refugio.shop_title");
        };
    }

    private HoveredSlot drawSubcategoriesView(GuiGraphicsExtractor graphics, int mouseX, int mouseY, ShopLayout layout) {
        HoveredSlot hovered = null;
        int accent = CATEGORY_ACCENTS[categoryIndex % CATEGORY_ACCENTS.length];
        for (int i = 0; i < layout.gridCards.size(); i++) {
            SlotRect slotRect = layout.gridCards.get(i);
            int idx = layout.subStart + i;
            if (idx >= subcategories.size()) break;
            SubcategoryEntry sub = subcategories.get(idx);
            Rect card = slotRect.rect;
            boolean isHovered = card.contains(mouseX, mouseY);
            ItemStack icon = getMenuSlotItem(sub.slotId);
            if (icon.isEmpty()) icon = createStack(sub.iconId, 1);
            Component title = Component.translatable("gui.refugio.subcategory." + sub.key).withStyle(INK_STYLE);
            Component count = Component.translatable("gui.refugio.category_count", sub.offerCount).withStyle(MUTED_STYLE);
            drawOverviewCard(graphics, card, icon, title, count, false, isHovered, accent);
            if (isHovered) {
                ItemStack tipStack = icon.copy();
                tipStack.set(DataComponents.CUSTOM_NAME, title);
                hovered = new HoveredSlot(tipStack, List.of(count));
            }
        }
        drawPaginationBar(graphics, mouseX, mouseY, layout);
        return hovered;
    }

    private HoveredSlot drawOffersView(GuiGraphicsExtractor graphics, int mouseX, int mouseY, ShopLayout layout) {
        HoveredSlot hovered = null;
        int accent = CATEGORY_ACCENTS[categoryIndex % CATEGORY_ACCENTS.length];
        for (int i = 0; i < layout.gridCards.size(); i++) {
            SlotRect slotRect = layout.gridCards.get(i);
            int idx = layout.subStart + i;
            if (idx >= offers.size()) break;
            OfferEntry offer = offers.get(idx);
            Rect card = slotRect.rect;
            boolean isHovered = card.contains(mouseX, mouseY);
            ItemStack stack = getMenuSlotItem(offer.slotId);
            if (stack.isEmpty()) {
                stack = createEnchantedStack(offer.itemId, offer.amount, offer.enchantments);
            }
            drawOfferCard(graphics, card, stack, offer, isHovered, accent);
            if (isHovered) {
                hovered = new HoveredSlot(stack, buildRichOfferTooltipLines(stack, offer.enchantments));
            }
        }
        drawPaginationBar(graphics, mouseX, mouseY, layout);
        return hovered;
    }

    private void drawOfferCard(GuiGraphicsExtractor graphics, Rect card, ItemStack stack,
                               OfferEntry offer, boolean hovered, int accent) {
        int x = card.x;
        int y = card.y;
        int w = card.w;
        int h = card.h;
        boolean locked = offer.locked;
        graphics.fill(x, y, x + w, y + h, locked ? 0xFFD4C7AD : hovered ? 0xFFF5E4C0 : 0xFFEAD8B2);
        border(graphics, x, y, w, h, locked ? 0xFF8E806A : hovered ? 0xFFD3A34F : 0xFF987348);
        graphics.fill(x + 2, y + 3, x + 5, y + h - 3, locked ? 0xFF8A8274 : accent);

        int iconX = x + 8;
        int iconY = y + Math.max(1, (h - 16) / 2);
        drawItemWithCount(graphics, stack, iconX, iconY, offer.amount);

        int textX = iconX + 19;
        int textW = Math.max(16, w - (textX - x) - 5);
        Component itemName = stack.getHoverName().copy().withStyle(locked ? MUTED_STYLE : INK_STYLE);
        Component priceLabel = locked
                ? Component.translatable("gui.refugio.category_requires", offer.requiredClaims).withStyle(DANGER_STYLE)
                : Component.translatable("gui.refugio.sell_total_value", offer.price).withStyle(GOLD_STYLE);

        Component enchSummary = formatCompactEnchantmentSummary(stack, offer.enchantments);
        List<FormattedCharSequence> nameLines = font.split(itemName, textW);
        List<FormattedCharSequence> enchLines = enchSummary != null ? font.split(enchSummary, textW) : List.of();
        List<FormattedCharSequence> priceLines = font.split(priceLabel, textW);

        boolean showEnch = !enchLines.isEmpty() && h >= font.lineHeight * 3 + 4;
        int maxNameLines = (!showEnch && h >= font.lineHeight * 3 + 6 && nameLines.size() > 1) ? 2 : 1;
        int nameCount = Math.min(maxNameLines, nameLines.size());
        boolean showPrice = h >= font.lineHeight * 2 + 2 && !priceLines.isEmpty();
        int totalH = nameCount * font.lineHeight
                + (showEnch ? font.lineHeight + 1 : 0)
                + (showPrice ? font.lineHeight + 1 : 0);
        int startY = y + Math.max(1, (h - totalH) / 2);

        int curY = startY;
        for (int i = 0; i < nameCount; i++) {
            graphics.textRenderer().accept(textX, curY, nameLines.get(i));
            curY += font.lineHeight;
        }
        if (showEnch) {
            curY += 1;
            graphics.textRenderer().accept(textX, curY, enchLines.getFirst());
            curY += font.lineHeight;
        }
        if (showPrice) {
            curY += 1;
            graphics.textRenderer().accept(textX, curY, priceLines.getFirst());
        }
    }

    private void drawPaginationBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, ShopLayout layout) {
        boolean canPrev = clientSubPage > 0 || serverPage > 0;
        boolean canNext = (clientSubPage + 1 < layout.clientSubPageCount) || (serverPage + 1 < serverTotalPages);
        Rect prev = layout.prevPageButton;
        Rect next = layout.nextPageButton;
        drawButton(graphics, prev.x, prev.y, prev.w, prev.h, Component.literal("‹"),
                mouseX, mouseY, canPrev, 0xFF293A48);
        drawButton(graphics, next.x, next.y, next.w, next.h, Component.literal("›"),
                mouseX, mouseY, canNext, 0xFF293A48);

        int displayPage = serverPage * layout.clientSubPageCount + clientSubPage + 1;
        int displayTotal = Math.max(displayPage, serverTotalPages * layout.clientSubPageCount);
        Component pageComp = Component.translatable("gui.refugio.page", displayPage, displayTotal).withStyle(GOLD_STYLE);
        int midX = (prev.right() + next.x) / 2;
        graphics.textRenderer().accept(midX - font.width(pageComp) / 2,
                prev.y + (prev.h - font.lineHeight) / 2, pageComp);
    }

    private HoveredSlot drawDetailView(GuiGraphicsExtractor graphics, int mouseX, int mouseY, ShopLayout layout) {
        HoveredSlot hovered = null;
        Rect infoBox = layout.detailInfoBox;
        graphics.fill(infoBox.x, infoBox.y, infoBox.right(), infoBox.bottom(), 0xD8EAD8B2);
        border(graphics, infoBox.x, infoBox.y, infoBox.w, infoBox.h, 0xFF967043);

        List<ShopCatalog.EnchantmentSpec> activeSpecs = resolveDetailEnchantmentSpecs();
        ItemStack product = getMenuSlotItem(22);
        if (product.isEmpty()) {
            product = createEnchantedStack(detailItemId, Math.max(1, Math.min(64, detailQuantity)), activeSpecs);
        }

        int iconBoxX = infoBox.x + 8;
        int iconBoxY = infoBox.y + 6;
        graphics.fill(iconBoxX - 2, iconBoxY - 2, iconBoxX + 18, iconBoxY + 18, 0x38987348);
        border(graphics, iconBoxX - 2, iconBoxY - 2, 20, 20, 0x88987348);
        drawItemWithCount(graphics, product, iconBoxX, iconBoxY, detailQuantity);
        if (mouseX >= iconBoxX - 2 && mouseX < iconBoxX + 18 && mouseY >= iconBoxY - 2 && mouseY < iconBoxY + 18) {
            hovered = new HoveredSlot(product, buildRichOfferTooltipLines(product, activeSpecs));
        }

        Component itemName = product.getHoverName().copy().withStyle(INK_STYLE);
        int nameX = iconBoxX + 22;
        int nameW = Math.max(16, infoBox.right() - nameX - 6);
        List<FormattedCharSequence> nameLines = font.split(itemName, nameW);
        int nameCount = Math.min(2, nameLines.size());
        for (int i = 0; i < nameCount; i++) {
            graphics.textRenderer().accept(nameX, iconBoxY + i * font.lineHeight, nameLines.get(i));
        }

        List<Component> summaryLines = new ArrayList<>();
        List<Component> enchAndEffects = extractEnchantmentAndEffectLines(product, activeSpecs);
        if (!enchAndEffects.isEmpty()) {
            summaryLines.add(Component.translatable("gui.refugio.offer_enchantments_header").withStyle(GOLD_STYLE));
            summaryLines.addAll(enchAndEffects);
        }
        summaryLines.add(Component.translatable("gui.refugio.purchase_unit_price", detailUnitPrice).withStyle(INK_STYLE));
        summaryLines.add(Component.translatable("gui.refugio.purchase_quantity", detailQuantity).withStyle(INK_STYLE));
        summaryLines.add(Component.translatable("gui.refugio.purchase_total", detailTotal).withStyle(GOLD_STYLE));
        long afterBalance = "SELL_CONFIRM".equals(viewName)
                ? (long) coins + detailTotal
                : Math.max(0L, (long) coins - detailTotal);
        summaryLines.add(Component.translatable("gui.refugio.purchase_remaining", afterBalance)
                .withStyle(afterBalance >= 0 && ("SELL_CONFIRM".equals(viewName) || coins >= detailTotal) ? SUCCESS_STYLE : DANGER_STYLE));
        if ("SELL_CONFIRM".equals(viewName)) {
            summaryLines.add(Component.translatable("gui.refugio.sell_remaining_stack",
                    Math.max(0, detailStackLimit - detailQuantity)).withStyle(MUTED_STYLE));
        } else {
            summaryLines.add(Component.translatable("gui.refugio.product_stack_size", detailStackLimit).withStyle(MUTED_STYLE));
            if (detailReqClaims > 0) {
                summaryLines.add(Component.translatable("gui.refugio.offer_requires", detailReqClaims).withStyle(MUTED_STYLE));
            }
        }

        int listTop = iconBoxY + 20;
        int listBottom = infoBox.bottom() - 4;
        if (listBottom > listTop + font.lineHeight) {
            graphics.fill(infoBox.x + 6, listTop - 2, infoBox.right() - 6, listTop - 1, 0x55987348);
            int wrapW = Math.max(16, infoBox.w - 16);
            List<FormattedCharSequence> allWrapped = new ArrayList<>();
            for (Component line : summaryLines) {
                allWrapped.addAll(font.split(line, wrapW));
            }
            int lineStep = font.lineHeight + 1;
            int totalContentH = allWrapped.size() * lineStep;
            int viewH = Math.max(1, listBottom - listTop);
            int maxScroll = Math.max(0, totalContentH - viewH);
            detailScrollOffset = Math.max(0, Math.min(maxScroll, detailScrollOffset));

            graphics.enableScissor(infoBox.x + 2, listTop, infoBox.right() - 2, listBottom);
            int lineY = listTop + 1 - detailScrollOffset;
            for (FormattedCharSequence wrapped : allWrapped) {
                if (lineY + font.lineHeight >= listTop && lineY <= listBottom) {
                    graphics.textRenderer().accept(infoBox.x + 7, lineY, wrapped);
                }
                lineY += lineStep;
            }
            graphics.disableScissor();
            if (maxScroll > 0 && viewH >= 14) {
                int barX = infoBox.right() - 4;
                int thumbH = Math.max(6, viewH * viewH / (viewH + maxScroll));
                int thumbY = listTop + (viewH - thumbH) * detailScrollOffset / maxScroll;
                graphics.fill(barX, listTop, barX + 2, listBottom, 0x44765030);
                graphics.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xFF9B744A);
            }
        }

        for (int i = 0; i < layout.detailQtyButtons.size(); i++) {
            SlotRect qSlot = layout.detailQtyButtons.get(i);
            Rect r = qSlot.rect;
            if (i < 5) {
                int qty = DETAIL_QUANTITIES[i];
                boolean unavailable = "PRODUCT_DETAIL".equals(viewName) && detailStackLimit == 1 && qty > 1;
                boolean selected = detailQuantity == qty;
                Component label = Component.translatable("gui.refugio.quantity." + qty);
                drawButton(graphics, r.x, r.y, r.w, r.h, label, mouseX, mouseY,
                        !unavailable, selected ? 0xFF24504F : 0xFF293A48);
                if (selected) {
                    border(graphics, r.x, r.y, r.w, r.h, 0xFFFFD787);
                }
            } else if (qSlot.slotId == 30) {
                Component customLabel = Component.translatable(
                        "SELL_CONFIRM".equals(viewName) ? "gui.refugio.sell_custom" : "gui.refugio.purchase_custom");
                drawButton(graphics, r.x, r.y, r.w, r.h, customLabel, mouseX, mouseY, true, 0xFF293A48);
            } else if (qSlot.slotId == 32) {
                Component allLabel = Component.translatable("gui.refugio.sell_all_stack");
                drawButton(graphics, r.x, r.y, r.w, r.h, allLabel, mouseX, mouseY, true, 0xFF293A48);
            }
        }

        Component confirmLabel = Component.translatable(
                "SELL_CONFIRM".equals(viewName) ? "gui.refugio.sell_confirm" : "gui.refugio.purchase_confirm");
        drawButton(graphics, layout.confirmButton.x, layout.confirmButton.y,
                layout.confirmButton.w, layout.confirmButton.h,
                confirmLabel, mouseX, mouseY, detailCanAction, 0xFF24504F);
        drawButton(graphics, layout.cancelButton.x, layout.cancelButton.y,
                layout.cancelButton.w, layout.cancelButton.h,
                Component.translatable("gui.refugio.purchase_cancel"), mouseX, mouseY, true, 0xFF293A48);
        return hovered;
    }

    private HoveredSlot drawSellView(GuiGraphicsExtractor graphics, int mouseX, int mouseY, ShopLayout layout) {
        HoveredSlot hovered = null;
        if (layout.contentArea.h >= 110) {
            Component depTitle = Component.translatable("gui.refugio.sell_deposit_hint").withStyle(GOLD_STYLE);
            Component invTitle = Component.translatable("gui.refugio.balance").withStyle(INK_STYLE);
            graphics.textRenderer().accept(layout.sellLeftX, layout.contentArea.y, depTitle);
            graphics.textRenderer().accept(layout.sellRightX, layout.contentArea.y, invTitle);
        }

        for (SlotRect slotRect : layout.sellDepositSlots) {
            HoveredSlot h = drawSellSlotCell(graphics, slotRect, mouseX, mouseY, true);
            if (h != null) hovered = h;
        }
        for (SlotRect slotRect : layout.sellInventorySlots) {
            HoveredSlot h = drawSellSlotCell(graphics, slotRect, mouseX, mouseY, false);
            if (h != null) hovered = h;
        }

        Rect sum = layout.sellSummaryBox;
        if (sum.h >= font.lineHeight + 4) {
            graphics.fill(sum.x, sum.y, sum.right(), sum.bottom(), 0x44A9844D);
            border(graphics, sum.x, sum.y, sum.w, sum.h, 0xFFB18A54);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.refugio.sell_total_amount", sellTotal).withStyle(GOLD_STYLE));
            lines.add(Component.translatable("gui.refugio.sell_accepted_count", sellAccepted).withStyle(SUCCESS_STYLE));
            if (sellRejected > 0) {
                lines.add(Component.translatable("gui.refugio.sell_rejected_count", sellRejected).withStyle(DANGER_STYLE));
            }
            lines.add(Component.translatable("gui.refugio.sell_balance_after_value", (long) coins + sellTotal).withStyle(INK_STYLE));

            int y = sum.y + 2;
            for (Component line : lines) {
                if (y + font.lineHeight > sum.bottom() - 1) break;
                List<FormattedCharSequence> split = font.split(line, Math.max(16, sum.w - 8));
                if (!split.isEmpty()) {
                    graphics.textRenderer().accept(sum.x + 4, y, split.getFirst());
                    y += font.lineHeight + 1;
                }
            }
        }

        drawButton(graphics, layout.sellReturnButton.x, layout.sellReturnButton.y,
                layout.sellReturnButton.w, layout.sellReturnButton.h,
                Component.translatable("gui.refugio.sell_return_items"), mouseX, mouseY,
                sellAccepted + sellRejected > 0, 0xFF293A48);
        drawButton(graphics, layout.confirmButton.x, layout.confirmButton.y,
                layout.confirmButton.w, layout.confirmButton.h,
                Component.translatable("gui.refugio.sell_confirm_amount", sellTotal), mouseX, mouseY,
                sellTotal > 0, 0xFF24504F);
        return hovered;
    }

    private HoveredSlot drawSellSlotCell(GuiGraphicsExtractor graphics, SlotRect slotRect,
                                         int mouseX, int mouseY, boolean depositSlot) {
        Rect r = slotRect.rect;
        boolean hovered = r.contains(mouseX, mouseY);
        ItemStack stack = getSellSlotStack(slotRect.slotId);
        SellSlotEntry synced = sellSlots.get(slotRect.slotId);
        int unitPrice = synced != null ? synced.unitPrice : 0;
        boolean hasItem = !stack.isEmpty();

        int bgColor = depositSlot
                ? (hovered ? 0xFFF7E8C9 : 0xFFE6D2AA)
                : (hovered ? 0xFFF3E2BE : 0xFFDCC79E);
        int borderColor = hovered ? 0xFFD4A34F
                : (hasItem && depositSlot ? (unitPrice > 0 ? 0xFF456B3A : 0xFFB74535) : 0xFF8E6E46);

        graphics.fill(r.x, r.y, r.right(), r.bottom(), bgColor);
        border(graphics, r.x, r.y, r.w, r.h, borderColor);

        if (hasItem) {
            int ix = r.x + (r.w - 16) / 2;
            int iy = r.y + (r.h - 16) / 2;
            int count = synced != null ? synced.count : stack.getCount();
            drawItemWithCount(graphics, stack, ix, iy, count);
            if (!depositSlot && unitPrice > 0 && r.w >= 16) {
                graphics.fill(r.x + 2, r.y + 2, r.x + 4, r.y + 4, 0xFF456B3A);
            }
        }

        if (hovered && hasItem) {
            List<Component> extra = new ArrayList<>();
            if (unitPrice > 0) {
                int count = synced != null ? synced.count : stack.getCount();
                extra.add(Component.translatable("gui.refugio.sell_unit_price", unitPrice).withStyle(GOLD_STYLE));
                extra.add(Component.translatable("gui.refugio.sell_pack_value", count, (long) unitPrice * count).withStyle(SUCCESS_STYLE));
            } else {
                extra.add(Component.translatable("gui.refugio.sell_item_rejected").withStyle(DANGER_STYLE));
            }
            return new HoveredSlot(stack, extra);
        }
        return null;
    }

    private void drawItemWithCount(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int count) {
        if (stack.isEmpty()) return;
        graphics.item(stack, x, y);
        if (count > 1) {
            String text = Integer.toString(count);
            Component comp = Component.literal(text).withStyle(LIGHT_STYLE);
            int tw = font.width(comp);
            int tx = x + 17 - tw;
            int ty = y + 17 - font.lineHeight;
            graphics.fill(tx - 1, ty - 1, tx + tw + 1, ty + font.lineHeight, 0xB02A1B10);
            graphics.textRenderer().accept(tx, ty, comp);
        }
    }

    private void drawItemTooltip(GuiGraphicsExtractor graphics, ItemStack stack, List<Component> extraLines,
                                 int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(stack.getHoverName().copy().withStyle(LIGHT_STYLE));
        lines.addAll(extraLines);

        int maxW = Math.min(220, Math.max(90, width / 2));
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(font.split(line, maxW));
        }
        if (wrapped.isEmpty()) return;

        int boxW = wrapped.stream().mapToInt(font::width).max().orElse(60) + 12;
        int boxH = wrapped.size() * (font.lineHeight + 1) + 8;
        int bx = mouseX + 10;
        int by = mouseY - 6;
        if (bx + boxW > width - 4) bx = Math.max(4, mouseX - boxW - 6);
        if (by + boxH > height - 4) by = Math.max(4, height - boxH - 4);
        if (by < 4) by = 4;

        graphics.fill(bx, by, bx + boxW, by + boxH, 0xF02B1C11);
        border(graphics, bx, by, boxW, boxH, 0xFFC39A59);
        int ty = by + 4;
        for (FormattedCharSequence seq : wrapped) {
            graphics.textRenderer().accept(bx + 6, ty, seq);
            ty += font.lineHeight + 1;
        }
    }

    private List<ShopCatalog.EnchantmentSpec> resolveDetailEnchantmentSpecs() {
        if (detailEnchantments != null && !detailEnchantments.isEmpty()) {
            return detailEnchantments;
        }
        if ("PRODUCT_DETAIL".equals(viewName)) {
            for (ShopCatalog.Offer offer : ShopCatalog.offers(categoryIndex)) {
                if (offer.itemId().equals(detailItemId)
                        && offer.price() == detailUnitPrice
                        && !offer.enchantments().isEmpty()) {
                    return offer.enchantments();
                }
            }
        }
        return List.of();
    }

    private Component formatCompactEnchantmentSummary(ItemStack stack, List<ShopCatalog.EnchantmentSpec> fallbackSpecs) {
        List<Component> enchLines = extractEnchantmentNamesOnly(stack, fallbackSpecs);
        if (enchLines.isEmpty()) return null;
        MutableComponent combined = Component.literal("✦ ").withStyle(SUCCESS_STYLE);
        for (int i = 0; i < enchLines.size(); i++) {
            if (i > 0) combined.append(Component.literal(", ").withStyle(SUCCESS_STYLE));
            combined.append(enchLines.get(i).copy().withStyle(SUCCESS_STYLE));
        }
        return combined;
    }

    private List<Component> buildRichOfferTooltipLines(ItemStack stack, List<ShopCatalog.EnchantmentSpec> fallbackSpecs) {
        List<Component> result = new ArrayList<>();
        List<Component> enchAndEffects = extractEnchantmentAndEffectLines(stack, fallbackSpecs);
        if (!enchAndEffects.isEmpty()) {
            result.add(Component.translatable("gui.refugio.offer_enchantments_header").withStyle(GOLD_STYLE));
            for (Component ench : enchAndEffects) {
                result.add(ench.copy().withStyle(LIGHT_STYLE));
            }
        }
        String headerText = Component.translatable("gui.refugio.offer_enchantments_header").getString();
        for (Component loreLine : extractLoreLines(stack)) {
            String plain = loreLine.getString();
            if (plain.equals(headerText) || plain.startsWith("• ")) continue;
            result.add(loreLine);
        }
        return result;
    }

    private List<Component> extractEnchantmentAndEffectLines(ItemStack stack,
                                                             List<ShopCatalog.EnchantmentSpec> fallbackSpecs) {
        List<Component> names = extractEnchantmentNamesOnly(stack, fallbackSpecs);
        List<Component> result = new ArrayList<>(names.size() + 2);
        for (Component name : names) {
            result.add(Component.literal("• ").withStyle(GOLD_STYLE)
                    .append(name.copy().withStyle(SUCCESS_STYLE)));
        }
        PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
        if (potionContents != null) {
            for (var effect : potionContents.getAllEffects()) {
                MutableComponent effName = Component.translatable(effect.getDescriptionId());
                if (effect.getAmplifier() > 0) {
                    effName.append(" ").append(Component.translatable("potion.potency." + effect.getAmplifier()));
                }
                result.add(Component.literal("• ").withStyle(GOLD_STYLE)
                        .append(effName.withStyle(SUCCESS_STYLE)));
            }
        }
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers != null && !modifiers.modifiers().isEmpty()) {
            for (var entry : modifiers.modifiers()) {
                double amount = entry.modifier().amount();
                if (Math.abs(amount) < 0.0001) continue;
                String sign = amount > 0 ? "+" : "";
                Component attrName = Component.translatable(entry.attribute().value().getDescriptionId());
                result.add(Component.literal("• " + sign + String.format(java.util.Locale.ROOT, "%.1f ", amount))
                        .withStyle(MUTED_STYLE)
                        .append(attrName.copy().withStyle(MUTED_STYLE)));
            }
        }
        return result;
    }

    private List<Component> extractEnchantmentNamesOnly(ItemStack stack,
                                                        List<ShopCatalog.EnchantmentSpec> fallbackSpecs) {
        List<Component> names = new ArrayList<>();
        if (minecraft != null && minecraft.level != null && fallbackSpecs != null && !fallbackSpecs.isEmpty()) {
            try {
                var reg = minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                for (ShopCatalog.EnchantmentSpec spec : fallbackSpecs) {
                    var key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(spec.id()));
                    var holderOpt = reg.get(key);
                    if (holderOpt.isPresent()) {
                        names.add(Enchantment.getFullname(holderOpt.get(), spec.level()));
                    } else {
                        names.add(fallbackEnchantmentComponent(spec.id(), spec.level()));
                    }
                }
                if (!names.isEmpty()) return names;
            } catch (RuntimeException ignored) {
            }
        }

        ItemEnchantments direct = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : direct.entrySet()) {
            names.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
        }
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : stored.entrySet()) {
            names.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
        }
        if (names.isEmpty() && fallbackSpecs != null) {
            for (ShopCatalog.EnchantmentSpec spec : fallbackSpecs) {
                names.add(fallbackEnchantmentComponent(spec.id(), spec.level()));
            }
        }
        return names;
    }

    private static Component fallbackEnchantmentComponent(String id, int level) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        MutableComponent name = Component.translatable("enchantment.minecraft." + path);
        if (level > 1) {
            name.append(" ").append(Component.translatable("enchantment.level." + level));
        }
        return name;
    }

    private ItemStack createEnchantedStack(String itemId, int count,
                                           List<ShopCatalog.EnchantmentSpec> enchantments) {
        ItemStack stack = createStack(itemId, count);
        if (stack.isEmpty() || enchantments == null || enchantments.isEmpty()) return stack;
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        if (minecraft != null && minecraft.level != null) {
            try {
                var reg = minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                boolean book = stack.is(Items.ENCHANTED_BOOK);
                ItemEnchantments.Mutable stored = book ? new ItemEnchantments.Mutable(ItemEnchantments.EMPTY) : null;
                for (ShopCatalog.EnchantmentSpec spec : enchantments) {
                    var key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(spec.id()));
                    var holderOpt = reg.get(key);
                    if (holderOpt.isPresent()) {
                        Holder<Enchantment> holder = holderOpt.get();
                        if (book) stored.set(holder, spec.level());
                        else stack.enchant(holder, spec.level());
                    }
                }
                if (book) {
                    stack.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
                }
            } catch (RuntimeException ignored) {
            }
        }
        return stack;
    }

    private List<Component> extractLoreLines(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) return List.of();
        List<Component> result = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) {
            result.add(line.copy().withStyle(MUTED_STYLE));
        }
        return result;
    }

    private ItemStack getMenuSlotItem(int slotId) {
        if (minecraft == null || minecraft.player == null) return ItemStack.EMPTY;
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (menu == null || menu.containerId != containerId) return ItemStack.EMPTY;
        if (slotId < 0 || slotId >= 90) return ItemStack.EMPTY;
        final ItemStack stack;
        try {
            stack = menu.getSlot(slotId).getItem();
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (slotId < 54 && stack.is(EMPTY_SLOT_DECORATION)
                && " ".equals(stack.getHoverName().getString())) {
            return ItemStack.EMPTY;
        }
        return stack;
    }

    private ItemStack getSellSlotStack(int slotId) {
        ItemStack live = getMenuSlotItem(slotId);
        if (!live.isEmpty()) return live;
        SellSlotEntry synced = sellSlots.get(slotId);
        if (synced != null && synced.count > 0) {
            return createStack(synced.itemId, synced.count);
        }
        return ItemStack.EMPTY;
    }

    private ItemStack currentCarriedStack() {
        if (carriedItemId == null || carriedItemId.isEmpty() || carriedCount <= 0) {
            return ItemStack.EMPTY;
        }
        return createStack(carriedItemId, carriedCount);
    }

    private static ItemStack createStack(String itemId, int count) {
        if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, Math.max(1, count));
    }

    private void drawClose(GuiGraphicsExtractor graphics, int mouseX, int mouseY, Rect rect) {
        int x = rect.x;
        int y = rect.y;
        int s = rect.w;
        boolean hovered = rect.contains(mouseX, mouseY);
        graphics.fill(x, y, x + s, y + s, hovered ? 0xFF80532B : 0xFF5E3E23);
        border(graphics, x, y, s, s, hovered ? 0xFFFFD787 : 0xFFB08A50);
        int color = hovered ? 0xFFFFE9B0 : 0xFFE8D6B5;
        int crossSize = Math.max(4, Math.min(6, s - 10));
        int startX = x + (s - crossSize - 2) / 2;
        int startY = y + (s - crossSize - 2) / 2;
        for (int i = 0; i < crossSize; i++) {
            graphics.fill(startX + i, startY + i, startX + i + 2, startY + i + 2, color);
            graphics.fill(startX + i, startY + crossSize - i, startX + i + 2, startY + crossSize - i + 2, color);
        }
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h, Component label,
                            int mouseX, int mouseY, boolean enabled, int baseColor) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        Identifier texture = baseColor == 0xFF24504F ? PRIMARY_BUTTON_TEXTURE : SECONDARY_BUTTON_TEXTURE;
        if (w > 0 && h > 0) graphics.blit(texture, x, y, x + w, y + h, 0.0F, 1.0F, 0.28F, 0.72F);
        if (!enabled) {
            graphics.fill(x + 3, y + 2, x + w - 3, y + h - 2, 0x78332213);
        } else if (hovered && w > 12 && h > 10) {
            int insetX = w < 40 ? 3 : 6;
            int insetY = h < 18 ? 2 : 4;
            graphics.fill(x + insetX, y + insetY, x + w - insetX, y + h - insetY, 0x24E8B954);
            border(graphics, x + 2, y + 2, w - 4, h - 4, 0xB8D8B677);
            graphics.fill(x + insetX, y + insetY - 1, x + w - insetX, y + insetY, 0xA0FFF0BA);
            if (w >= 48) {
                drawDiamond(graphics, x + 7, y + h / 2, 2, 0xFFE9C77F);
                drawDiamond(graphics, x + w - 8, y + h / 2, 2, 0xFFE9C77F);
            }
        }
        Style labelStyle = !enabled ? DISABLED_BUTTON_STYLE
                : hovered ? Style.EMPTY.withFont(UI_FONT).withColor(0xFFFFE5A0) : LIGHT_STYLE;
        int textPad = w < 48 ? 6 : (w < 110 ? 10 : 16);
        List<FormattedCharSequence> lines = font.split(
                Component.literal(label.getString()).withStyle(labelStyle), Math.max(8, w - textPad));
        int maxLines = h >= font.lineHeight * 2 + 2 ? 2 : 1;
        int count = Math.min(maxLines, lines.size());
        int textY = y + Math.max(0, (h - count * font.lineHeight) / 2);
        for (int i = 0; i < count; i++) {
            FormattedCharSequence line = lines.get(i);
            graphics.textRenderer().accept(x + (w - font.width(line)) / 2,
                    textY + i * font.lineHeight, line);
        }
    }

    private static void blitFull(GuiGraphicsExtractor graphics, Identifier texture,
                                 int x, int y, int width, int height) {
        if (width > 0 && height > 0) {
            graphics.blit(texture, x, y, x + width, y + height, 0.0F, 1.0F, 0.0F, 1.0F);
        }
    }

    private static void drawDiamond(GuiGraphicsExtractor graphics, int centerX, int centerY,
                                    int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int halfWidth = radius - Math.abs(dy);
            graphics.fill(centerX - halfWidth, centerY + dy,
                    centerX + halfWidth + 1, centerY + dy + 1, color);
        }
    }

    private static void border(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private record Rect(int x, int y, int w, int h) {
        int right() { return x + w; }
        int bottom() { return y + h; }
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record SlotRect(int slotId, Rect rect) { }
    private record SubcategoryEntry(int slotId, String key, int offerCount, String iconId) { }
    private record OfferEntry(int slotId, String itemId, int amount, int price, int requiredClaims, boolean locked,
                              List<ShopCatalog.EnchantmentSpec> enchantments) { }
    private record SellSlotEntry(int slotId, String itemId, int count, int unitPrice) { }
    private record HoveredSlot(ItemStack stack, List<Component> extraLines) { }

    private record ShopLayout(
            Rect panel, Rect inner, Rect titleBanner, Rect balanceBadge, Rect closeButton,
            boolean compactBalance, int subHeaderTop, List<SlotRect> homeCards,
            Rect backButton, Rect searchButton, Rect sortButton, Rect sellNavButton,
            int dividerY, Rect contentArea, Rect prevPageButton, Rect nextPageButton,
            int clientSubPageCount, int subStart, List<SlotRect> gridCards,
            Rect detailInfoBox, Rect detailQtyBox, List<SlotRect> detailQtyButtons,
            Rect confirmButton, Rect cancelButton,
            int sellLeftX, int sellRightX, int sellBodyTop, int slotSize,
            List<SlotRect> sellDepositSlots, List<SlotRect> sellInventorySlots,
            Rect sellSummaryBox, Rect sellReturnButton
    ) { }
}
