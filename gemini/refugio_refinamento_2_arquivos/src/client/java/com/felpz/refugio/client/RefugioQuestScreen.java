package com.felpz.refugio.client;

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
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * Medieval parchment screen for Eldrin's campaign (QUESTS), Marta's garden missions (GARDEN_QUESTS),
 * and Dario's expeditions (EXPEDITION_QUESTS), backed by the server-authoritative RefugioMenu container.
 */
public final class RefugioQuestScreen extends Screen {
    private static final int STATE_DONE = 0;
    private static final int STATE_CLAIM_READY = 1;
    private static final int STATE_IN_PROGRESS = 2;
    private static final int STATE_LOCKED = 3;

    private static final Identifier PARCHMENT_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/parchment.png");
    private static final Identifier WOOD_FRAME_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/wood_frame.png");
    private static final Identifier TITLE_PLAQUE_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/title_plaque.png");
    private static final Identifier PRIMARY_BUTTON_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/button_primary.png");
    private static final Identifier SECONDARY_BUTTON_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/button_close.png");
    private static final Identifier ELDRIN_PORTRAIT = Identifier.parse("refugio:textures/gui/dialogue/portraits/eldrin.png");
    private static final Identifier MARTA_PORTRAIT = Identifier.parse("refugio:textures/gui/dialogue/portraits/marta.png");
    private static final Identifier DARIO_PORTRAIT = Identifier.parse("refugio:textures/gui/dialogue/portraits/dario.png");

    private static final FontDescription UI_FONT = new FontDescription.Resource(Identifier.parse("minecraft:uniform"));
    private static final Style INK_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF4A3520);
    private static final Style MUTED_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF80684B);
    private static final Style GOLD_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF87551F);
    private static final Style SUCCESS_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF2D5B22);
    private static final Style DANGER_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF9E3B2B);
    private static final Style LIGHT_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFFFFF0D0);
    private static final Style DISABLED_BUTTON_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFFBBAA8D);

    private int containerId = -1;
    private String viewName = "QUESTS";
    private int coins;
    private int claimedProgress;
    private int totalMissions = 8;
    private final List<QuestEntry> quests = new ArrayList<>();

    private int clientPage;
    private boolean focusInitialPage = true;
    private boolean menuBound;

    public RefugioQuestScreen(String encodedState) {
        super(Component.translatable("gui.refugio.quest_title"));
        updateState(encodedState);
    }

    public int containerId() {
        return containerId;
    }

    public void tickScreen(Minecraft client) {
        if (client.player == null) return;
        AbstractContainerMenu activeMenu = client.player.containerMenu;
        if (activeMenu != null && activeMenu.containerId == containerId) {
            menuBound = true;
            if (client.screen != this) {
                client.setScreenAndShow(this);
            }
        } else if (menuBound && (activeMenu == null || activeMenu == client.player.inventoryMenu)) {
            RefugioClient.clearQuestScreen(this);
            if (client.screen == this) {
                client.setScreenAndShow(null);
            }
        }
    }

    public void updateState(String encodedState) {
        String[] parts = encodedState.split("\\t", -1);
        if (parts.length < 7 || !parts[0].equals("QUEST")) return;
        try {
            int newContainerId = Integer.parseInt(parts[1]);
            String newView = parts[2];
            if (newContainerId != containerId || !newView.equals(viewName)) {
                clientPage = 0;
                focusInitialPage = true;
            }
            if (newContainerId != containerId) {
                menuBound = false;
            }
            containerId = newContainerId;
            viewName = newView;
            coins = Integer.parseInt(parts[3]);
            claimedProgress = Math.max(0, Integer.parseInt(parts[4]));
            totalMissions = Math.max(1, Integer.parseInt(parts[5]));

            quests.clear();
            String entriesRaw = parts[6];
            if (!entriesRaw.isEmpty()) {
                for (String entryStr : entriesRaw.split(";")) {
                    String[] f = entryStr.split("\\|", -1);
                    if (f.length < 10) continue;
                    int slotId = Integer.parseInt(f[0]);
                    int index = Integer.parseInt(f[1]);
                    int state = Integer.parseInt(f[2]);
                    String titleKey = f[3];
                    String descKey = f[4];
                    String iconId = f[5];
                    boolean rewardVisible = "1".equals(f[6]);
                    int rewardCoins = Integer.parseInt(f[7]);
                    List<ProgressSpec> progressList = parseProgressSpecs(f[8]);
                    List<RewardSpec> rewardList = rewardVisible ? parseRewardSpecs(f[9]) : List.of();
                    quests.add(new QuestEntry(
                            slotId, index, state, titleKey, descKey, iconId,
                            rewardVisible, rewardCoins, progressList, rewardList));
                }
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private static List<ProgressSpec> parseProgressSpecs(String raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<ProgressSpec> result = new ArrayList<>();
        for (String token : raw.split("~")) {
            String[] p = token.split("\\^", -1);
            if (p.length >= 4) {
                try {
                    result.add(new ProgressSpec(p[0], Long.parseLong(p[1]), Long.parseLong(p[2]), p[3]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    private static List<RewardSpec> parseRewardSpecs(String raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<RewardSpec> result = new ArrayList<>();
        for (String token : raw.split("~")) {
            String[] p = token.split("\\^", -1);
            if (p.length >= 5) {
                try {
                    result.add(new RewardSpec(p[0], Integer.parseInt(p[1]), p[2], Integer.parseInt(p[3]), p[4]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
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
        QuestLayout layout = computeLayout();
        drawParchmentAndWoodFrame(graphics, layout.panel);
        drawHeader(graphics, mouseX, mouseY, layout);
        drawNpcBanner(graphics, layout);

        QuestEntry hoveredQuest = null;
        for (int i = 0; i < layout.questCards.size(); i++) {
            SlotRect cardSlot = layout.questCards.get(i);
            int questIdx = layout.pageStart + i;
            if (questIdx >= quests.size()) break;
            QuestEntry entry = quests.get(questIdx);
            boolean hovered = cardSlot.rect.contains(mouseX, mouseY);
            drawQuestCard(graphics, cardSlot.rect, entry, hovered);
            if (hovered) {
                hoveredQuest = entry;
            }
        }

        drawFooterBar(graphics, mouseX, mouseY, layout);

        if (hoveredQuest != null) {
            drawQuestTooltip(graphics, hoveredQuest, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        double mx = event.x();
        double my = event.y();
        QuestLayout layout = computeLayout();

        if (layout.closeButton.contains(mx, my)) {
            closeQuestScreen();
            return true;
        }
        if (layout.primaryNavButton.contains(mx, my)) {
            sendQuestClick(45);
            return true;
        }
        if (layout.secondaryNavButton.w > 0 && layout.secondaryNavButton.contains(mx, my)) {
            sendQuestClick(53);
            return true;
        }
        if (layout.prevPageButton.contains(mx, my)) {
            if (clientPage > 0) clientPage--;
            return true;
        }
        if (layout.nextPageButton.contains(mx, my)) {
            if (clientPage + 1 < layout.pageCount) clientPage++;
            return true;
        }
        for (SlotRect card : layout.questCards) {
            if (card.rect.contains(mx, my)) {
                sendQuestClick(card.slotId);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0.0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        QuestLayout layout = computeLayout();
        if (layout.contentArea.contains(mouseX, mouseY) && layout.pageCount > 1) {
            int delta = -(int) Math.signum(verticalAmount);
            clientPage = Math.max(0, Math.min(layout.pageCount - 1, clientPage + delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        closeQuestScreen();
    }

    private void sendQuestClick(int slotId) {
        if (containerId < 0) return;
        ClientPlayNetworking.send(new RefugioUiPayload("QUEST_CLICK\t" + containerId + "\t" + slotId));
    }

    private void closeQuestScreen() {
        if (containerId >= 0) {
            ClientPlayNetworking.send(new RefugioUiPayload("QUEST_CLOSE\t" + containerId));
        }
        RefugioClient.clearQuestScreen(this);
        if (minecraft != null) {
            minecraft.setScreenAndShow(null);
        }
    }

    private QuestLayout computeLayout() {
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
        int idealBannerW = Math.min(350, Math.max(140, font.width(titleComp) + 44));
        int centeredBannerX = px + (pw - idealBannerW) / 2;
        boolean canCenterBanner = innerW >= 460
                && centeredBannerX >= innerX
                && centeredBannerX + idealBannerW <= maxBannerRight;
        int bannerW = canCenterBanner ? idealBannerW : Math.min(idealBannerW, availBannerArea);
        int bannerX = canCenterBanner ? centeredBannerX : innerX;
        Rect titleBanner = new Rect(bannerX, headerY, bannerW, headerH);

        int sectionGap = innerH < 145 ? 2 : (innerH < 185 ? 3 : (innerH < 230 ? 5 : 6));
        int npcBoxY = headerY + headerH + sectionGap;
        int npcBoxH = innerH < 145 ? 16 : (innerH < 190 ? 22 : (innerH < 245 ? 28 : 34));
        Rect npcBannerBox = new Rect(innerX, npcBoxY, innerW, npcBoxH);

        int footerH = innerH < 145 ? 14 : (innerH < 185 ? 16 : (innerH < 230 ? 19 : 22));
        int footerY = inner.bottom() - footerH;

        int navBtnW = Math.max(52, Math.min(108, innerW * 22 / 100));
        Rect primaryNavButton = new Rect(innerX, footerY, navBtnW, footerH);

        boolean showShopShortcut = "QUESTS".equals(viewName) && innerW >= 360;
        int secondaryW = showShopShortcut ? Math.max(64, Math.min(126, innerW * 25 / 100)) : 0;
        Rect secondaryNavButton = showShopShortcut
                ? new Rect(primaryNavButton.right() + 6, footerY, secondaryW, footerH)
                : new Rect(primaryNavButton.right(), footerY, 0, 0);

        int pageBtnW = innerW < 320 ? 20 : 28;
        Rect nextPageButton = new Rect(inner.right() - pageBtnW, footerY, pageBtnW, footerH);
        Component samplePage = Component.translatable("gui.refugio.page", 8, 8).withStyle(GOLD_STYLE);
        int pageLabelSpan = Math.max(54, Math.min(110, font.width(samplePage) + 14));
        Rect prevPageButton = new Rect(nextPageButton.x - pageLabelSpan - pageBtnW, footerY, pageBtnW, footerH);

        int contentTop = npcBannerBox.bottom() + sectionGap;
        int contentBottom = footerY - sectionGap;
        int contentAvailH = Math.max(32, contentBottom - contentTop);
        Rect contentArea = new Rect(innerX, contentTop, innerW, contentAvailH);

        int cols = innerW >= 330 ? 2 : 1;
        int rows = contentAvailH >= 104 ? 2 : 1;
        int cardsPerPage = Math.max(1, cols * rows);
        int count = Math.max(1, quests.size());
        int pageCount = Math.max(1, (count + cardsPerPage - 1) / cardsPerPage);
        if (focusInitialPage && !quests.isEmpty()) {
            int focusIdx = Math.min(quests.size() - 1, Math.max(0, claimedProgress));
            clientPage = focusIdx / cardsPerPage;
            focusInitialPage = false;
        }
        clientPage = Math.max(0, Math.min(clientPage, pageCount - 1));
        int pageStart = clientPage * cardsPerPage;
        int visibleCount = Math.max(0, Math.min(cardsPerPage, quests.size() - pageStart));

        int gapX = cols == 1 ? 0 : (innerW < 400 ? 6 : 10);
        int gapY = rows == 1 ? 0 : (contentAvailH < 130 ? 4 : 8);
        int cardW = Math.max(60, (innerW - gapX * (cols - 1)) / cols);
        int cardH = Math.max(26, (contentAvailH - gapY * (rows - 1)) / rows);
        int actualRows = Math.max(1, (Math.max(1, visibleCount) + cols - 1) / cols);
        int usedH = actualRows * cardH + Math.max(0, actualRows - 1) * gapY;
        int gridStartY = contentTop + Math.max(0, (contentAvailH - usedH) / 2);

        List<SlotRect> questCards = new ArrayList<>(visibleCount);
        for (int i = 0; i < visibleCount; i++) {
            int col = i % cols;
            int row = i / cols;
            QuestEntry q = quests.get(pageStart + i);
            questCards.add(new SlotRect(q.slotId, new Rect(
                    innerX + col * (cardW + gapX),
                    gridStartY + row * (cardH + gapY),
                    cardW, cardH)));
        }

        return new QuestLayout(
                panel, inner, titleBanner, balanceBadge, closeButton,
                compactBalance, npcBannerBox, contentArea,
                pageStart, pageCount, questCards,
                primaryNavButton, secondaryNavButton, prevPageButton, nextPageButton
        );
    }

    private Component headerTitleComponent() {
        return switch (viewName) {
            case "GARDEN_QUESTS" -> Component.translatable("gui.refugio.garden_quests_title");
            case "EXPEDITION_QUESTS" -> Component.translatable("gui.refugio.expedition_quests_title");
            default -> Component.translatable("gui.refugio.quest_title");
        };
    }

    private Component npcSpeakerComponent() {
        return switch (viewName) {
            case "GARDEN_QUESTS" -> Component.translatable("entity.refugio.garden_quest_giver");
            case "EXPEDITION_QUESTS" -> Component.translatable("entity.refugio.expedition_quest_giver");
            default -> Component.translatable("entity.refugio.quest_giver");
        };
    }

    private Identifier npcPortraitTexture() {
        return switch (viewName) {
            case "GARDEN_QUESTS" -> MARTA_PORTRAIT;
            case "EXPEDITION_QUESTS" -> DARIO_PORTRAIT;
            default -> ELDRIN_PORTRAIT;
        };
    }

    private int trackAccentColor() {
        return switch (viewName) {
            case "GARDEN_QUESTS" -> 0xFF6B8E3D;
            case "EXPEDITION_QUESTS" -> 0xFF4C8878;
            default -> 0xFFC0944B;
        };
    }

    private void drawParchmentAndWoodFrame(GuiGraphicsExtractor graphics, Rect panel) {
        int insetX = Math.max(1, (int) Math.round(panel.w * 0.052));
        int insetY = Math.max(1, (int) Math.round(panel.h * 0.082));
        blitFull(graphics, PARCHMENT_TEXTURE, panel.x + insetX, panel.y + insetY,
                Math.max(1, panel.w - insetX * 2), Math.max(1, panel.h - insetY * 2));
        blitFull(graphics, WOOD_FRAME_TEXTURE, panel.x, panel.y, panel.w, panel.h);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY, QuestLayout layout) {
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

    private void drawNpcBanner(GuiGraphicsExtractor graphics, QuestLayout layout) {
        Rect box = layout.npcBannerBox;
        int accent = trackAccentColor();
        graphics.fill(box.x, box.y, box.right(), box.bottom(), 0x55DEC89E);
        border(graphics, box.x, box.y, box.w, box.h, 0xFF9C784C);
        graphics.fill(box.x + 2, box.y + 2, box.x + 5, box.bottom() - 2, accent);

        int textLeft = box.x + 10;
        if (box.h >= 22 && box.w >= 240) {
            int pSize = box.h - 6;
            int px = box.x + 8;
            int py = box.y + 3;
            graphics.fill(px - 1, py - 1, px + pSize + 1, py + pSize + 1, 0xFF5A3A20);
            blitFull(graphics, npcPortraitTexture(), px, py, pSize, pSize);
            border(graphics, px, py, pSize, pSize, 0xFFC39A59);
            textLeft = px + pSize + 8;
        }

        int completed = Math.min(totalMissions, claimedProgress);
        Component progressComp = Component.literal(completed + " / " + totalMissions).withStyle(GOLD_STYLE);
        int progTextW = font.width(progressComp);
        int barW = box.w >= 340 ? 84 : (box.w >= 240 ? 52 : 0);
        int rightBlockW = progTextW + (barW > 0 ? barW + 8 : 0) + 8;
        int rightStart = box.right() - rightBlockW;

        int textY = box.y + Math.max(1, (box.h - font.lineHeight) / 2);
        Component speaker = npcSpeakerComponent().withStyle(INK_STYLE);
        int availSpeakerW = Math.max(30, rightStart - textLeft - 6);
        List<FormattedCharSequence> speakerLines = font.split(speaker, availSpeakerW);
        if (!speakerLines.isEmpty()) {
            graphics.textRenderer().accept(textLeft, textY, speakerLines.getFirst());
        }

        graphics.textRenderer().accept(rightStart, textY, progressComp);
        if (barW > 0) {
            int barX = rightStart + progTextW + 6;
            int barH = Math.min(6, Math.max(4, box.h / 4));
            int barY = box.y + (box.h - barH) / 2;
            graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF8A6E48);
            border(graphics, barX, barY, barW, barH, 0xFF5A3E22);
            if (completed > 0 && totalMissions > 0) {
                int fillW = Math.max(2, (barW - 2) * completed / totalMissions);
                graphics.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, accent);
            }
        }
    }

    private void drawQuestCard(GuiGraphicsExtractor graphics, Rect card, QuestEntry entry, boolean hovered) {
        int x = card.x;
        int y = card.y;
        int w = card.w;
        int h = card.h;

        // Distinct visual palette per mission state (DONE vs CLAIM_READY vs IN_PROGRESS vs LOCKED)
        int bgColor = switch (entry.state) {
            case STATE_DONE -> hovered ? 0xFFD9E9CA : 0xFFCFE0BF;
            case STATE_CLAIM_READY -> hovered ? 0xFFFFF2C6 : 0xFFFCE6A8;
            case STATE_IN_PROGRESS -> hovered ? 0xFFF5E4C0 : 0xFFEAD8B2;
            default -> hovered ? 0xFFD4C8B4 : 0xFFCBC0AA;
        };
        int borderColor = switch (entry.state) {
            case STATE_DONE -> hovered ? 0xFF5A8F49 : 0xFF3A682E;
            case STATE_CLAIM_READY -> hovered ? 0xFFFFDF7A : 0xFFD4921A;
            case STATE_IN_PROGRESS -> hovered ? 0xFFD3A34F : 0xFF987348;
            default -> hovered ? 0xFF948773 : 0xFF7D7262;
        };
        int accentColor = switch (entry.state) {
            case STATE_DONE -> 0xFF2D5B22;
            case STATE_CLAIM_READY -> 0xFFD4921A;
            case STATE_IN_PROGRESS -> trackAccentColor();
            default -> 0xFF756C5E;
        };

        graphics.fill(x, y, x + w, y + h, bgColor);
        border(graphics, x, y, w, h, borderColor);
        if (entry.state == STATE_DONE) {
            border(graphics, x + 1, y + 1, w - 2, h - 2, 0x887BA868);
        } else if (entry.state == STATE_CLAIM_READY) {
            border(graphics, x + 1, y + 1, w - 2, h - 2, 0xAAFFE07D);
        }
        graphics.fill(x + 2, y + 2, x + 6, y + h - 2, accentColor);

        int iconBoxSize = h >= 48 ? 20 : 16;
        int iconBoxX = x + 9;
        int iconBoxY = y + (h >= 54 ? 6 : Math.max(2, (h - iconBoxSize) / 2));
        if (h >= 48) {
            int medallionBg = entry.state == STATE_DONE ? 0x443A682E
                    : entry.state == STATE_CLAIM_READY ? 0x44D4921A : 0x34987348;
            int medallionBorder = entry.state == STATE_DONE ? 0xAA3A682E
                    : entry.state == STATE_CLAIM_READY ? 0xCCD4921A : 0x80987348;
            graphics.fill(iconBoxX - 1, iconBoxY - 1, iconBoxX + iconBoxSize + 1, iconBoxY + iconBoxSize + 1, medallionBg);
            border(graphics, iconBoxX - 1, iconBoxY - 1, iconBoxSize + 2, iconBoxSize + 2, medallionBorder);
        }
        ItemStack iconStack = createStack(entry.iconId, 1);
        graphics.item(iconStack, iconBoxX + (iconBoxSize - 16) / 2, iconBoxY + (iconBoxSize - 16) / 2);

        // Draw corner seal on the icon box for claimed missions
        if (entry.state == STATE_DONE) {
            drawCheckSeal(graphics, iconBoxX + iconBoxSize - 6, iconBoxY + iconBoxSize - 6);
        }

        int textLeft = iconBoxX + iconBoxSize + 6;
        int textRight = x + w - 5;
        int availTextW = Math.max(24, textRight - textLeft);

        Style titleStyle = entry.state == STATE_DONE ? SUCCESS_STYLE
                : entry.state == STATE_LOCKED ? MUTED_STYLE : INK_STYLE;
        MutableComponent numberedTitle = Component.literal("#" + (entry.index + 1) + " ")
                .withStyle(entry.state == STATE_DONE ? SUCCESS_STYLE : GOLD_STYLE)
                .append(Component.translatable(entry.titleKey).withStyle(titleStyle));

        Component stateComp = stateBadgeComponent(entry.state);

        if (h < 42) {
            List<FormattedCharSequence> titleLines = font.split(numberedTitle, availTextW);
            List<FormattedCharSequence> stateLines = font.split(stateComp, availTextW);
            boolean twoLines = h >= font.lineHeight * 2 + 3 && !stateLines.isEmpty();
            int startY = y + Math.max(1, (h - (twoLines ? font.lineHeight * 2 + 1 : font.lineHeight)) / 2);
            if (!titleLines.isEmpty()) {
                graphics.textRenderer().accept(textLeft, startY, titleLines.getFirst());
            }
            if (twoLines) {
                graphics.textRenderer().accept(textLeft, startY + font.lineHeight + 1, stateLines.getFirst());
            }
            return;
        }

        int topY = y + 4;
        int pillReservedW = drawTopRightStateSeal(graphics, x + w - 4, topY - 1, availTextW, entry.state);
        int titleAvailW = Math.max(24, availTextW - pillReservedW);
        List<FormattedCharSequence> titleLines = font.split(numberedTitle, titleAvailW);
        if (!titleLines.isEmpty()) {
            graphics.textRenderer().accept(textLeft, topY, titleLines.getFirst());
        }

        int footerH = font.lineHeight + 4;
        int footerY = y + h - footerH - 2;
        int statusY = footerY - font.lineHeight - 1;

        int midTop = topY + font.lineHeight + 2;
        int midBottom = statusY - 1;
        int midAvailH = midBottom - midTop;

        if (midAvailH >= font.lineHeight) {
            List<Component> midComponents = new ArrayList<>();
            midComponents.add(Component.translatable(entry.descKey)
                    .withStyle(entry.state == STATE_LOCKED ? MUTED_STYLE : INK_STYLE));
            for (ProgressSpec prog : entry.progressSpecs) {
                midComponents.add(formatProgressSpec(prog));
            }
            int curY = midTop;
            for (Component comp : midComponents) {
                for (FormattedCharSequence line : font.split(comp, availTextW)) {
                    if (curY + font.lineHeight > midBottom) break;
                    graphics.textRenderer().accept(textLeft, curY, line);
                    curY += font.lineHeight + 1;
                }
            }
        }

        if (statusY >= topY + font.lineHeight) {
            List<FormattedCharSequence> stateLines = font.split(stateComp, availTextW);
            if (!stateLines.isEmpty()) {
                graphics.textRenderer().accept(textLeft, statusY, stateLines.getFirst());
            }
        }

        drawCardRewardRow(graphics, textLeft, footerY, availTextW, footerH, entry);
    }

    private int drawTopRightStateSeal(GuiGraphicsExtractor graphics, int rightX, int topY,
                                      int availTextW, int state) {
        if (availTextW < 92) return 0;
        Component label = switch (state) {
            case STATE_DONE -> Component.translatable("gui.refugio.quest_badge.done");
            case STATE_CLAIM_READY -> Component.translatable("gui.refugio.quest_badge.claim_ready");
            case STATE_IN_PROGRESS -> Component.translatable("gui.refugio.quest_badge.in_progress");
            default -> Component.translatable("gui.refugio.quest_badge.locked");
        };
        int pillFill = switch (state) {
            case STATE_DONE -> 0xFF2D5B22;
            case STATE_CLAIM_READY -> 0xFF24504F;
            case STATE_IN_PROGRESS -> 0xFF6B4C2B;
            default -> 0xFF5C5246;
        };
        int pillBorder = switch (state) {
            case STATE_DONE -> 0xFFB89A52;
            case STATE_CLAIM_READY -> 0xFFFFD878;
            case STATE_IN_PROGRESS -> 0xFFB89455;
            default -> 0xFF8A7E6E;
        };
        Style textStyle = Style.EMPTY.withFont(UI_FONT).withColor(switch (state) {
            case STATE_DONE -> 0xFFEFFAE6;
            case STATE_CLAIM_READY -> 0xFFFFF0B8;
            case STATE_IN_PROGRESS -> 0xFFF5E6C8;
            default -> 0xFFD2C5B0;
        });

        Component styled = Component.literal(label.getString()).withStyle(textStyle);
        int maxPillW = Math.max(36, availTextW * 45 / 100);
        List<FormattedCharSequence> lines = font.split(styled, maxPillW - 8);
        if (lines.isEmpty()) return 0;
        FormattedCharSequence seq = lines.getFirst();
        int pillW = font.width(seq) + 8;
        int pillH = font.lineHeight + 2;
        int px = rightX - pillW;
        graphics.fill(px, topY, px + pillW, topY + pillH, pillFill);
        border(graphics, px, topY, pillW, pillH, pillBorder);
        graphics.textRenderer().accept(px + 4, topY + 1, seq);
        return pillW + 4;
    }

    private static void drawCheckSeal(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 7, y + 7, 0xFF2D5B22);
        border(graphics, x, y, 7, 7, 0xFFE6C672);
        // Pixel checkmark inside 7x7 box
        graphics.fill(x + 1, y + 3, x + 2, y + 4, 0xFFEFFAE6);
        graphics.fill(x + 2, y + 4, x + 3, y + 5, 0xFFEFFAE6);
        graphics.fill(x + 3, y + 3, x + 4, y + 4, 0xFFEFFAE6);
        graphics.fill(x + 4, y + 2, x + 5, y + 3, 0xFFEFFAE6);
        graphics.fill(x + 5, y + 1, x + 6, y + 2, 0xFFEFFAE6);
    }

    private void drawCardRewardRow(GuiGraphicsExtractor graphics, int x, int y, int w, int h, QuestEntry entry) {
        int dividerColor = entry.state == STATE_DONE ? 0x553A682E : 0x44987348;
        graphics.fill(x, y - 1, x + w, y, dividerColor);
        int textY = y + Math.max(0, (h - font.lineHeight) / 2);
        if (!entry.rewardVisible) {
            Component hidden = Component.translatable("gui.refugio.side_quest_reward_hidden").withStyle(MUTED_STYLE);
            List<FormattedCharSequence> lines = font.split(hidden, w);
            if (!lines.isEmpty()) {
                graphics.textRenderer().accept(x, textY, lines.getFirst());
            }
            return;
        }

        int curX = x;
        if (entry.state == STATE_DONE) {
            Component check = Component.literal("✓ ").withStyle(SUCCESS_STYLE);
            graphics.textRenderer().accept(curX, textY, check);
            curX += font.width(check);
        }
        drawCoinIcon(graphics, curX, y + Math.max(0, (h - 12) / 2));
        curX += 14;
        Style rewardStyle = entry.state == STATE_DONE ? SUCCESS_STYLE : GOLD_STYLE;
        Component coinsText = Component.literal("+" + entry.rewardCoins).withStyle(rewardStyle);
        graphics.textRenderer().accept(curX, textY, coinsText);
        curX += font.width(coinsText) + 6;

        for (RewardSpec r : entry.rewardSpecs) {
            if (curX + 18 > x + w) break;
            ItemStack stack = createRewardStack(r);
            int iconY = y + (h - 16) / 2;
            drawItemWithCount(graphics, stack, curX, iconY, r.count);
            curX += 19;
        }

        if (curX + 24 < x + w && !entry.rewardSpecs.isEmpty()) {
            Style nameStyle = entry.state == STATE_DONE ? SUCCESS_STYLE : INK_STYLE;
            Component names = formatRewardSummaryNames(entry.rewardSpecs).withStyle(nameStyle);
            List<FormattedCharSequence> split = font.split(names, (x + w) - curX);
            if (!split.isEmpty()) {
                graphics.textRenderer().accept(curX, textY, split.getFirst());
            }
        }
    }

    private void drawFooterBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, QuestLayout layout) {
        Component primaryLabel = "QUESTS".equals(viewName)
                ? Component.translatable("gui.refugio.back")
                : Component.translatable("gui.refugio.close");
        drawButton(graphics, layout.primaryNavButton.x, layout.primaryNavButton.y,
                layout.primaryNavButton.w, layout.primaryNavButton.h,
                primaryLabel, mouseX, mouseY, true, 0xFF293A48);

        if (layout.secondaryNavButton.w > 0) {
            drawButton(graphics, layout.secondaryNavButton.x, layout.secondaryNavButton.y,
                    layout.secondaryNavButton.w, layout.secondaryNavButton.h,
                    Component.translatable("gui.refugio.shop_title"), mouseX, mouseY, true, 0xFF24504F);
        }

        Rect prev = layout.prevPageButton;
        Rect next = layout.nextPageButton;
        drawButton(graphics, prev.x, prev.y, prev.w, prev.h, Component.literal("‹"),
                mouseX, mouseY, clientPage > 0, 0xFF293A48);
        drawButton(graphics, next.x, next.y, next.w, next.h, Component.literal("›"),
                mouseX, mouseY, clientPage + 1 < layout.pageCount, 0xFF293A48);

        Component pageComp = Component.translatable("gui.refugio.page", clientPage + 1, layout.pageCount)
                .withStyle(GOLD_STYLE);
        int midX = (prev.right() + next.x) / 2;
        graphics.textRenderer().accept(midX - font.width(pageComp) / 2,
                prev.y + (prev.h - font.lineHeight) / 2, pageComp);
    }

    private void drawQuestTooltip(GuiGraphicsExtractor graphics, QuestEntry entry, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("#" + (entry.index + 1) + " — ")
                .withStyle(GOLD_STYLE)
                .append(Component.translatable(entry.titleKey).withStyle(LIGHT_STYLE)));
        lines.add(Component.translatable(entry.descKey).withStyle(MUTED_STYLE));

        for (ProgressSpec prog : entry.progressSpecs) {
            lines.add(formatProgressSpec(prog).copy().withStyle(LIGHT_STYLE));
        }

        if (entry.rewardVisible) {
            lines.add(Component.translatable("gui.refugio.side_quest_reward",
                    entry.rewardCoins, formatRewardSummaryNames(entry.rewardSpecs)).withStyle(GOLD_STYLE));
        } else {
            lines.add(Component.translatable("gui.refugio.side_quest_reward_hidden").withStyle(MUTED_STYLE));
        }
        lines.add(stateBadgeComponent(entry.state));

        int maxW = Math.min(240, Math.max(100, width * 3 / 5));
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

        graphics.fill(bx, by, bx + boxW, by + boxH, 0xF22B1C11);
        border(graphics, bx, by, boxW, boxH, 0xFFC39A59);
        int ty = by + 4;
        for (FormattedCharSequence seq : wrapped) {
            graphics.textRenderer().accept(bx + 6, ty, seq);
            ty += font.lineHeight + 1;
        }
    }

    private Component stateBadgeComponent(int state) {
        return switch (state) {
            case STATE_DONE -> Component.literal("✓ ")
                    .append(Component.translatable("gui.refugio.quest_done"))
                    .withStyle(SUCCESS_STYLE);
            case STATE_CLAIM_READY -> Component.literal("★ ")
                    .append(Component.translatable("gui.refugio.quest_claim_ready"))
                    .withStyle(GOLD_STYLE);
            case STATE_IN_PROGRESS -> Component.literal("● ")
                    .append(Component.translatable("gui.refugio.quest_in_progress"))
                    .withStyle(INK_STYLE);
            default -> Component.translatable("gui.refugio.quest_locked").withStyle(MUTED_STYLE);
        };
    }

    private Component formatProgressSpec(ProgressSpec spec) {
        Style style = spec.current >= spec.required ? SUCCESS_STYLE : GOLD_STYLE;
        if ("BOAT".equals(spec.type)) {
            return Component.translatable("gui.refugio.side_quest_boat_progress", spec.current, spec.required)
                    .withStyle(style);
        }
        Component targetName = "@group".equals(spec.target) || spec.target.startsWith("@")
                ? Component.translatable("gui.refugio.side_quest_delivery_group")
                : createStack(spec.target, 1).getHoverName();
        return Component.translatable("gui.refugio.side_quest_delivery_progress",
                spec.current, spec.required, targetName).withStyle(style);
    }

    private MutableComponent formatRewardSummaryNames(List<RewardSpec> rewards) {
        MutableComponent summary = Component.empty();
        for (int i = 0; i < rewards.size(); i++) {
            if (i > 0) summary.append(Component.literal(", "));
            RewardSpec r = rewards.get(i);
            ItemStack stack = createRewardStack(r);
            summary.append(Component.literal(r.count + "× ")).append(stack.getHoverName());
        }
        return summary;
    }

    private ItemStack createRewardStack(RewardSpec r) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(r.itemId));
        if (item == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack;
        if (r.potionId != null && !r.potionId.isBlank()) {
            Holder<Potion> potion = switch (r.potionId) {
                case "minecraft:fire_resistance" -> Potions.FIRE_RESISTANCE;
                case "minecraft:long_fire_resistance" -> Potions.LONG_FIRE_RESISTANCE;
                case "minecraft:strong_strength" -> Potions.STRONG_STRENGTH;
                default -> Potions.FIRE_RESISTANCE;
            };
            stack = PotionContents.createItemStack(item, potion);
            stack.setCount(Math.max(1, r.count));
        } else {
            stack = new ItemStack(item, Math.max(1, r.count));
        }
        if (r.enchantmentId != null && !r.enchantmentId.isBlank() && r.enchantmentLevel > 0) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            if (minecraft != null && minecraft.level != null) {
                try {
                    var reg = minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                    var key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(r.enchantmentId));
                    var holderOpt = reg.get(key);
                    if (holderOpt.isPresent()) {
                        Holder<Enchantment> holder = holderOpt.get();
                        if (stack.is(Items.ENCHANTED_BOOK)) {
                            ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                            stored.set(holder, r.enchantmentLevel);
                            stack.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
                        } else {
                            stack.enchant(holder, r.enchantmentLevel);
                        }
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }
        return stack;
    }

    private static ItemStack createStack(String itemId, int count) {
        if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, Math.max(1, count));
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

    private static void drawCoinIcon(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x + 4, y, x + 10, y + 2, 0xFFFFE79A);
        graphics.fill(x + 2, y + 2, x + 12, y + 10, 0xFFD99B36);
        graphics.fill(x + 4, y + 10, x + 10, y + 12, 0xFF9C6424);
        graphics.fill(x + 4, y + 3, x + 8, y + 8, 0xFFFFD76A);
        graphics.fill(x + 8, y + 5, x + 10, y + 8, 0xFFF2B847);
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
    private record ProgressSpec(String type, long current, long required, String target) { }
    private record RewardSpec(String itemId, int count, String enchantmentId, int enchantmentLevel, String potionId) { }
    private record QuestEntry(
            int slotId, int index, int state, String titleKey, String descKey,
            String iconId, boolean rewardVisible, int rewardCoins,
            List<ProgressSpec> progressSpecs, List<RewardSpec> rewardSpecs
    ) { }

    private record QuestLayout(
            Rect panel, Rect inner, Rect titleBanner, Rect balanceBadge, Rect closeButton,
            boolean compactBalance, Rect npcBannerBox, Rect contentArea,
            int pageStart, int pageCount, List<SlotRect> questCards,
            Rect primaryNavButton, Rect secondaryNavButton, Rect prevPageButton, Rect nextPageButton
    ) { }
}
