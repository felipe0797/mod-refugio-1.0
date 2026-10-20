package com.felpz.refugio.client;

import com.felpz.refugio.quest.RefugioCommanderRewards;
import com.felpz.refugio.ui.RefugioUiPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Custom medieval parchment screen for Commander Elias Varga ("Última Linha").
 * Displays the 60 zombie-kill milestones, statistics, and 12 titles/patentes without using a vanilla chest GUI.
 */
public final class RefugioCommanderScreen extends Screen {
    private static final int STATE_CLAIMED = 0;
    private static final int STATE_READY = 1;
    private static final int STATE_LOCKED = 2;

    private static final int TAB_REWARDS = 0;
    private static final int TAB_STATS = 1;
    private static final int TAB_RANKS = 2;

    private static final Identifier PARCHMENT_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/parchment.png");
    private static final Identifier WOOD_FRAME_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/wood_frame.png");
    private static final Identifier TITLE_PLAQUE_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/title_plaque.png");
    private static final Identifier PRIMARY_BUTTON_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/button_primary.png");
    private static final Identifier SECONDARY_BUTTON_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/button_close.png");
    private static final Identifier ELIAS_PORTRAIT = Identifier.parse("refugio:textures/gui/dialogue/portraits/elias.png");

    private static final FontDescription UI_FONT = new FontDescription.Resource(Identifier.parse("minecraft:uniform"));
    private static final Style INK_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF4A3520);
    private static final Style MUTED_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF80684B);
    private static final Style GOLD_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF87551F);
    private static final Style SUCCESS_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF2D5B22);
    private static final Style DANGER_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF9E3B2B);
    private static final Style LIGHT_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFFFFF0D0);
    private static final Style DISABLED_BUTTON_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFFBBAA8D);
    private static final int FACTION_ACCENT = 0xFF8B2E24;

    private int coins;
    private int zombieKills;
    private final Set<Integer> unlockedMilestones = new HashSet<>();
    private final Set<Integer> claimedMilestones = new HashSet<>();
    private final Set<Integer> unlockedTitles = new HashSet<>();
    private String feedbackKey = "";

    private int activeTab = TAB_REWARDS;
    private int rewardsPage;
    private int ranksPage;
    private int selectedMilestoneNumber = -1;
    private int detailScrollOffset;
    private boolean focusInitialPage = true;

    public RefugioCommanderScreen(String encodedState) {
        super(Component.translatable("gui.refugio.commander.title"));
        updateState(encodedState);
    }

    public void updateState(String encodedState) {
        String[] parts = encodedState.split("\\t", -1);
        if (parts.length < 7 || !parts[0].equals("COMMANDER")) return;
        try {
            coins = Integer.parseInt(parts[1]);
            zombieKills = Math.max(0, Integer.parseInt(parts[2]));
            parseSet(parts[3], unlockedMilestones);
            parseSet(parts[4], claimedMilestones);
            parseSet(parts[5], unlockedTitles);
            feedbackKey = parts[6];
        } catch (NumberFormatException ignored) {
        }
    }

    private static void parseSet(String csv, Set<Integer> target) {
        target.clear();
        if (csv == null || csv.isBlank()) return;
        for (String token : csv.split(",")) {
            if (token.isBlank()) continue;
            try {
                target.add(Integer.parseInt(token.trim()));
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
        CommanderLayout layout = computeLayout();
        drawParchmentAndWoodFrame(graphics, layout.panel);
        drawHeader(graphics, mouseX, mouseY, layout);
        drawNpcBanner(graphics, layout);
        drawTabBar(graphics, mouseX, mouseY, layout);

        RefugioCommanderRewards.MilestoneSpec hoveredMilestone = null;
        if (activeTab == TAB_REWARDS) {
            if (selectedMilestoneNumber > 0) {
                drawMilestoneDetailView(graphics, mouseX, mouseY, layout);
            } else {
                List<RefugioCommanderRewards.MilestoneSpec> all = RefugioCommanderRewards.activeMilestones();
                for (int i = 0; i < layout.milestoneCards.size(); i++) {
                    SlotRect slot = layout.milestoneCards.get(i);
                    int idx = layout.pageStart + i;
                    if (idx >= all.size()) break;
                    RefugioCommanderRewards.MilestoneSpec spec = all.get(idx);
                    boolean hovered = slot.rect.contains(mouseX, mouseY);
                    drawMilestoneCard(graphics, slot.rect, spec, hovered, mouseX, mouseY);
                    if (hovered) {
                        hoveredMilestone = spec;
                    }
                }
            }
        } else if (activeTab == TAB_STATS) {
            drawStatsView(graphics, layout);
        } else if (activeTab == TAB_RANKS) {
            drawRanksView(graphics, layout);
        }

        drawFooterBar(graphics, mouseX, mouseY, layout);

        if (hoveredMilestone != null && selectedMilestoneNumber <= 0) {
            drawMilestoneTooltip(graphics, hoveredMilestone, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        double mx = event.x();
        double my = event.y();
        CommanderLayout layout = computeLayout();

        if (layout.closeButton.contains(mx, my)) {
            closeCommanderScreen();
            return true;
        }
        if (layout.tabRewardsButton.contains(mx, my)) {
            activeTab = TAB_REWARDS;
            selectedMilestoneNumber = -1;
            detailScrollOffset = 0;
            feedbackKey = "";
            return true;
        }
        if (layout.tabStatsButton.contains(mx, my)) {
            activeTab = TAB_STATS;
            selectedMilestoneNumber = -1;
            feedbackKey = "";
            return true;
        }
        if (layout.tabRanksButton.contains(mx, my)) {
            activeTab = TAB_RANKS;
            selectedMilestoneNumber = -1;
            feedbackKey = "";
            return true;
        }

        if (activeTab == TAB_REWARDS && selectedMilestoneNumber > 0) {
            if (layout.primaryNavButton.contains(mx, my)) {
                selectedMilestoneNumber = -1;
                detailScrollOffset = 0;
                feedbackKey = "";
                return true;
            }
            if (layout.claimDetailButton.w > 0 && layout.claimDetailButton.contains(mx, my)) {
                if (milestoneState(selectedMilestoneNumber) == STATE_READY) {
                    sendClaimRequest(selectedMilestoneNumber);
                }
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        if (layout.primaryNavButton.contains(mx, my)) {
            closeCommanderScreen();
            return true;
        }

        if (activeTab == TAB_REWARDS) {
            if (layout.prevPageButton.contains(mx, my) && rewardsPage > 0) {
                rewardsPage--;
                return true;
            }
            if (layout.nextPageButton.contains(mx, my) && rewardsPage + 1 < layout.pageCount) {
                rewardsPage++;
                return true;
            }
            for (SlotRect card : layout.milestoneCards) {
                if (card.rect.contains(mx, my)) {
                    int number = card.slotId;
                    Rect quickClaim = quickClaimRect(card.rect);
                    if (milestoneState(number) == STATE_READY && quickClaim.contains(mx, my)) {
                        sendClaimRequest(number);
                    } else {
                        selectedMilestoneNumber = number;
                        detailScrollOffset = 0;
                        feedbackKey = "";
                    }
                    return true;
                }
            }
        } else if (activeTab == TAB_RANKS) {
            if (layout.prevPageButton.contains(mx, my) && ranksPage > 0) {
                ranksPage--;
                return true;
            }
            if (layout.nextPageButton.contains(mx, my) && ranksPage + 1 < layout.pageCount) {
                ranksPage++;
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0.0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        CommanderLayout layout = computeLayout();
        int delta = -(int) Math.signum(verticalAmount);
        if (activeTab == TAB_REWARDS && selectedMilestoneNumber > 0) {
            if (layout.contentArea.contains(mouseX, mouseY)) {
                detailScrollOffset = Math.max(0, detailScrollOffset + delta * (font.lineHeight + 2));
                return true;
            }
        } else if (activeTab == TAB_REWARDS && layout.contentArea.contains(mouseX, mouseY) && layout.pageCount > 1) {
            rewardsPage = Math.max(0, Math.min(layout.pageCount - 1, rewardsPage + delta));
            return true;
        } else if (activeTab == TAB_RANKS && layout.contentArea.contains(mouseX, mouseY) && layout.pageCount > 1) {
            ranksPage = Math.max(0, Math.min(layout.pageCount - 1, ranksPage + delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        closeCommanderScreen();
    }

    private void sendClaimRequest(int milestoneNumber) {
        feedbackKey = "gui.refugio.abilities.feedback.processing";
        ClientPlayNetworking.send(new RefugioUiPayload("COMMANDER_CLAIM\t" + milestoneNumber));
    }

    private void closeCommanderScreen() {
        ClientPlayNetworking.send(new RefugioUiPayload("COMMANDER_CLOSE"));
        RefugioClient.clearCommanderScreen(this);
        if (minecraft != null) {
            minecraft.setScreenAndShow(null);
        }
    }

    private int milestoneState(int milestoneNumber) {
        if (claimedMilestones.contains(milestoneNumber)) {
            return STATE_CLAIMED;
        }
        RefugioCommanderRewards.MilestoneSpec spec = RefugioCommanderRewards.milestone(milestoneNumber);
        if (unlockedMilestones.contains(milestoneNumber)
                || (spec != null && zombieKills >= spec.requiredKills())) {
            return STATE_READY;
        }
        return STATE_LOCKED;
    }

    private boolean isTitleUnlocked(RefugioCommanderRewards.TitleSpec title) {
        return unlockedTitles.contains(title.index()) || zombieKills >= title.requiredKills();
    }

    private CommanderLayout computeLayout() {
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

        Component titleComp = Component.translatable("gui.refugio.commander.title").withStyle(LIGHT_STYLE);
        int maxBannerRight = badgeX - headerGap;
        int availBannerArea = Math.max(64, maxBannerRight - innerX);
        int idealBannerW = Math.min(360, Math.max(140, font.width(titleComp) + 44));
        int centeredBannerX = px + (pw - idealBannerW) / 2;
        boolean canCenterBanner = innerW >= 460
                && centeredBannerX >= innerX
                && centeredBannerX + idealBannerW <= maxBannerRight;
        int bannerW = canCenterBanner ? idealBannerW : Math.min(idealBannerW, availBannerArea);
        int bannerX = canCenterBanner ? centeredBannerX : innerX;
        Rect titleBanner = new Rect(bannerX, headerY, bannerW, headerH);

        int sectionGap = innerH < 145 ? 2 : (innerH < 185 ? 3 : (innerH < 230 ? 4 : 5));
        int npcBoxY = headerY + headerH + sectionGap;
        int npcBoxH = innerH < 145 ? 16 : (innerH < 190 ? 20 : (innerH < 245 ? 24 : 28));
        Rect npcBannerBox = new Rect(innerX, npcBoxY, innerW, npcBoxH);

        int tabBarY = npcBannerBox.bottom() + sectionGap;
        int tabBarH = innerH < 155 ? 14 : (innerH < 210 ? 16 : 19);
        int tabGap = 6;
        int tabW = Math.max(44, (innerW - tabGap * 2) / 3);
        Rect tabRewardsButton = new Rect(innerX, tabBarY, tabW, tabBarH);
        Rect tabStatsButton = new Rect(tabRewardsButton.right() + tabGap, tabBarY, tabW, tabBarH);
        Rect tabRanksButton = new Rect(tabStatsButton.right() + tabGap, tabBarY, inner.right() - (tabStatsButton.right() + tabGap), tabBarH);

        int footerH = innerH < 145 ? 14 : (innerH < 185 ? 16 : (innerH < 230 ? 19 : 22));
        int footerY = inner.bottom() - footerH;

        int navBtnW = Math.max(58, Math.min(126, innerW * 25 / 100));
        Rect primaryNavButton = new Rect(innerX, footerY, navBtnW, footerH);

        int claimBtnW = (activeTab == TAB_REWARDS && selectedMilestoneNumber > 0)
                ? Math.max(96, Math.min(190, innerW * 38 / 100)) : 0;
        Rect claimDetailButton = claimBtnW > 0
                ? new Rect(inner.right() - claimBtnW, footerY, claimBtnW, footerH)
                : new Rect(inner.right(), footerY, 0, 0);

        int pageBtnW = innerW < 320 ? 20 : 28;
        Rect nextPageButton = new Rect(inner.right() - pageBtnW, footerY, pageBtnW, footerH);
        Component samplePage = Component.translatable("gui.refugio.page", 15, 15).withStyle(GOLD_STYLE);
        int pageLabelSpan = Math.max(56, Math.min(114, font.width(samplePage) + 14));
        Rect prevPageButton = new Rect(nextPageButton.x - pageLabelSpan - pageBtnW, footerY, pageBtnW, footerH);

        int contentTop = tabBarY + tabBarH + sectionGap;
        int contentBottom = footerY - sectionGap;
        int contentAvailH = Math.max(32, contentBottom - contentTop);
        Rect contentArea = new Rect(innerX, contentTop, innerW, contentAvailH);

        int cols = innerW >= 330 ? 2 : 1;
        int rows = contentAvailH >= 96 ? 2 : 1;
        int cardsPerPage = Math.max(1, cols * rows);

        List<RefugioCommanderRewards.MilestoneSpec> allMilestones = RefugioCommanderRewards.activeMilestones();
        List<RefugioCommanderRewards.TitleSpec> allTitles = RefugioCommanderRewards.activeTitles();

        int totalItems = activeTab == TAB_RANKS ? allTitles.size() : allMilestones.size();
        int rankRows = contentAvailH >= 110 ? 3 : 2;
        int rankCardsPerPage = Math.max(1, cols * rankRows);
        int perPage = activeTab == TAB_RANKS ? rankCardsPerPage : cardsPerPage;
        int pageCount = Math.max(1, (Math.max(1, totalItems) + perPage - 1) / perPage);

        if (focusInitialPage && !allMilestones.isEmpty()) {
            int focusIdx = 0;
            boolean foundReady = false;
            for (int i = 0; i < allMilestones.size(); i++) {
                int state = milestoneState(allMilestones.get(i).number());
                if (state == STATE_READY) {
                    focusIdx = i;
                    foundReady = true;
                    break;
                }
            }
            if (!foundReady) {
                for (int i = 0; i < allMilestones.size(); i++) {
                    if (milestoneState(allMilestones.get(i).number()) == STATE_LOCKED) {
                        focusIdx = i;
                        break;
                    }
                }
            }
            rewardsPage = focusIdx / cardsPerPage;
            focusInitialPage = false;
        }

        int activePage = activeTab == TAB_RANKS ? ranksPage : rewardsPage;
        activePage = Math.max(0, Math.min(activePage, pageCount - 1));
        if (activeTab == TAB_RANKS) {
            ranksPage = activePage;
        } else {
            rewardsPage = activePage;
        }
        int pageStart = activePage * perPage;
        int visibleCount = Math.max(0, Math.min(perPage, totalItems - pageStart));

        int activeRows = activeTab == TAB_RANKS ? rankRows : rows;
        int gapX = cols == 1 ? 0 : (innerW < 400 ? 6 : 10);
        int gapY = activeRows == 1 ? 0 : (contentAvailH < 130 ? 4 : 7);
        int cardW = Math.max(60, (innerW - gapX * (cols - 1)) / cols);
        int cardH = Math.max(24, (contentAvailH - gapY * (activeRows - 1)) / activeRows);
        int actualRows = Math.max(1, (Math.max(1, visibleCount) + cols - 1) / cols);
        int usedH = actualRows * cardH + Math.max(0, actualRows - 1) * gapY;
        int gridStartY = contentTop + Math.max(0, (contentAvailH - usedH) / 2);

        List<SlotRect> milestoneCards = new ArrayList<>(visibleCount);
        for (int i = 0; i < visibleCount; i++) {
            int col = i % cols;
            int row = i / cols;
            int id = activeTab == TAB_RANKS
                    ? allTitles.get(pageStart + i).index()
                    : allMilestones.get(pageStart + i).number();
            milestoneCards.add(new SlotRect(id, new Rect(
                    innerX + col * (cardW + gapX),
                    gridStartY + row * (cardH + gapY),
                    cardW, cardH)));
        }

        return new CommanderLayout(
                panel, inner, titleBanner, balanceBadge, closeButton,
                compactBalance, npcBannerBox,
                tabRewardsButton, tabStatsButton, tabRanksButton,
                contentArea, pageStart, pageCount, milestoneCards,
                primaryNavButton, claimDetailButton, prevPageButton, nextPageButton
        );
    }

    private Rect quickClaimRect(Rect card) {
        int btnW = Math.min(64, Math.max(42, card.w / 3));
        int btnH = Math.min(14, Math.max(11, font.lineHeight + 3));
        return new Rect(card.right() - btnW - 4, card.bottom() - btnH - 3, btnW, btnH);
    }

    private void drawParchmentAndWoodFrame(GuiGraphicsExtractor graphics, Rect panel) {
        int insetX = Math.max(1, (int) Math.round(panel.w * 0.052));
        int insetY = Math.max(1, (int) Math.round(panel.h * 0.082));
        blitFull(graphics, PARCHMENT_TEXTURE, panel.x + insetX, panel.y + insetY,
                Math.max(1, panel.w - insetX * 2), Math.max(1, panel.h - insetY * 2));
        blitFull(graphics, WOOD_FRAME_TEXTURE, panel.x, panel.y, panel.w, panel.h);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CommanderLayout layout) {
        Component title = Component.translatable("gui.refugio.commander.title").withStyle(LIGHT_STYLE);
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

    private void drawNpcBanner(GuiGraphicsExtractor graphics, CommanderLayout layout) {
        Rect box = layout.npcBannerBox;
        graphics.fill(box.x, box.y, box.right(), box.bottom(), 0x55DEC89E);
        border(graphics, box.x, box.y, box.w, box.h, 0xFF9C784C);
        graphics.fill(box.x + 2, box.y + 2, box.x + 5, box.bottom() - 2, FACTION_ACCENT);

        int textLeft = box.x + 10;
        if (box.h >= 20 && box.w >= 240) {
            int pSize = box.h - 4;
            int px = box.x + 8;
            int py = box.y + 2;
            graphics.fill(px - 1, py - 1, px + pSize + 1, py + pSize + 1, 0xFF5A3A20);
            blitFull(graphics, ELIAS_PORTRAIT, px, py, pSize, pSize);
            border(graphics, px, py, pSize, pSize, 0xFFC39A59);
            textLeft = px + pSize + 8;
        }

        int totalMilestones = RefugioCommanderRewards.TOTAL_MILESTONES;
        int claimedCount = claimedMilestones.size();
        Component killsComp = Component.translatable("gui.refugio.commander.kills_short",
                zombieKills, claimedCount, totalMilestones).withStyle(GOLD_STYLE);
        int progTextW = font.width(killsComp);
        int rightStart = Math.max(textLeft + 40, box.right() - progTextW - 8);

        int textY = box.y + Math.max(1, (box.h - font.lineHeight) / 2);
        String currentTitle = RefugioCommanderRewards.highestUnlockedTitle(zombieKills);
        Component rankDisplay = currentTitle.isEmpty()
                ? Component.translatable("gui.refugio.commander.no_rank")
                : Component.literal(currentTitle);
        MutableComponent speaker = Component.translatable("entity.refugio.commander_elias")
                .withStyle(INK_STYLE)
                .append(Component.literal(" — ").withStyle(MUTED_STYLE))
                .append(rankDisplay.copy().withStyle(GOLD_STYLE));

        int availSpeakerW = Math.max(30, rightStart - textLeft - 6);
        List<FormattedCharSequence> speakerLines = font.split(speaker, availSpeakerW);
        if (!speakerLines.isEmpty()) {
            graphics.textRenderer().accept(textLeft, textY, speakerLines.getFirst());
        }
        List<FormattedCharSequence> killLines = font.split(killsComp, Math.max(30, box.right() - rightStart - 4));
        if (!killLines.isEmpty()) {
            graphics.textRenderer().accept(rightStart, textY, killLines.getFirst());
        }
    }

    private void drawTabBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CommanderLayout layout) {
        int readyCount = 0;
        for (RefugioCommanderRewards.MilestoneSpec m : RefugioCommanderRewards.activeMilestones()) {
            if (milestoneState(m.number()) == STATE_READY) readyCount++;
        }
        Component rewardsTabLabel = readyCount > 0
                ? Component.translatable("gui.refugio.commander.tab.rewards_ready", readyCount)
                : Component.translatable("gui.refugio.commander.tab.rewards");
        drawButton(graphics, layout.tabRewardsButton.x, layout.tabRewardsButton.y,
                layout.tabRewardsButton.w, layout.tabRewardsButton.h,
                rewardsTabLabel, mouseX, mouseY, true,
                activeTab == TAB_REWARDS ? 0xFF24504F : 0xFF293A48);
        drawButton(graphics, layout.tabStatsButton.x, layout.tabStatsButton.y,
                layout.tabStatsButton.w, layout.tabStatsButton.h,
                Component.translatable("gui.refugio.commander.tab.stats"), mouseX, mouseY, true,
                activeTab == TAB_STATS ? 0xFF24504F : 0xFF293A48);
        drawButton(graphics, layout.tabRanksButton.x, layout.tabRanksButton.y,
                layout.tabRanksButton.w, layout.tabRanksButton.h,
                Component.translatable("gui.refugio.commander.tab.ranks"), mouseX, mouseY, true,
                activeTab == TAB_RANKS ? 0xFF24504F : 0xFF293A48);
    }

    private void drawMilestoneCard(GuiGraphicsExtractor graphics, Rect card,
                                   RefugioCommanderRewards.MilestoneSpec spec,
                                   boolean hovered, int mouseX, int mouseY) {
        int x = card.x;
        int y = card.y;
        int w = card.w;
        int h = card.h;
        int state = milestoneState(spec.number());

        int bgColor = switch (state) {
            case STATE_CLAIMED -> hovered ? 0xFFEFE3C2 : 0xFFE5D7B3;
            case STATE_READY -> hovered ? 0xFFFDF0CE : 0xFFF6E4B8;
            default -> hovered ? 0xFFDED1B7 : 0xFFD4C7AD;
        };
        int borderColor = switch (state) {
            case STATE_CLAIMED -> hovered ? 0xFFD3A34F : 0xFF5B7F46;
            case STATE_READY -> hovered ? 0xFFFFD878 : 0xFFD49B35;
            default -> hovered ? 0xFFA99577 : 0xFF8E806A;
        };
        int accentColor = switch (state) {
            case STATE_CLAIMED -> 0xFF2D5B22;
            case STATE_READY -> 0xFF24504F;
            default -> FACTION_ACCENT;
        };

        graphics.fill(x, y, x + w, y + h, bgColor);
        border(graphics, x, y, w, h, borderColor);
        if (state == STATE_READY) {
            border(graphics, x + 1, y + 1, w - 2, h - 2, 0x88FFD982);
        }
        graphics.fill(x + 2, y + 2, x + 5, y + h - 2, accentColor);

        int iconBoxSize = h >= 48 ? 20 : 16;
        int iconBoxX = x + 8;
        int iconBoxY = y + (h >= 54 ? 6 : Math.max(2, (h - iconBoxSize) / 2));
        if (h >= 48) {
            graphics.fill(iconBoxX - 1, iconBoxY - 1, iconBoxX + iconBoxSize + 1, iconBoxY + iconBoxSize + 1, 0x34987348);
            border(graphics, iconBoxX - 1, iconBoxY - 1, iconBoxSize + 2, iconBoxSize + 2, 0x80987348);
        }
        ItemStack iconStack = createStack(spec.iconItemId(), 1);
        graphics.item(iconStack, iconBoxX + (iconBoxSize - 16) / 2, iconBoxY + (iconBoxSize - 16) / 2);

        int textLeft = iconBoxX + iconBoxSize + 6;
        int textRight = x + w - 5;
        int availTextW = Math.max(24, textRight - textLeft);

        Style titleStyle = state == STATE_LOCKED ? MUTED_STYLE : INK_STYLE;
        String numStr = String.format("#%02d ", spec.number());
        MutableComponent numberedTitle = Component.literal(numStr)
                .withStyle(GOLD_STYLE)
                .append(Component.literal(spec.name()).withStyle(titleStyle));

        Component stateComp = stateBadgeComponent(state);

        if (h < 42) {
            List<FormattedCharSequence> titleLines = font.split(numberedTitle, availTextW);
            if (!titleLines.isEmpty()) {
                graphics.textRenderer().accept(textLeft, y + Math.max(1, (h - font.lineHeight) / 2), titleLines.getFirst());
            }
            return;
        }

        int topY = y + 4;
        List<FormattedCharSequence> titleLines = font.split(numberedTitle, availTextW);
        if (!titleLines.isEmpty()) {
            graphics.textRenderer().accept(textLeft, topY, titleLines.getFirst());
        }

        int footerH = font.lineHeight + 4;
        int footerY = y + h - footerH - 2;
        int statusY = footerY - font.lineHeight - 1;

        int midTop = topY + font.lineHeight + 2;
        int curKills = Math.min(zombieKills, spec.requiredKills());
        Style progStyle = zombieKills >= spec.requiredKills() ? SUCCESS_STYLE : GOLD_STYLE;
        Component reqLine = Component.translatable("gui.refugio.commander.kill_requirement",
                curKills, spec.requiredKills()).withStyle(progStyle);
        if (midTop + font.lineHeight <= statusY) {
            List<FormattedCharSequence> reqLines = font.split(reqLine, availTextW);
            if (!reqLines.isEmpty()) {
                graphics.textRenderer().accept(textLeft, midTop, reqLines.getFirst());
            }
        }

        if (statusY >= topY + font.lineHeight) {
            List<FormattedCharSequence> stateLines = font.split(stateComp, availTextW);
            if (!stateLines.isEmpty()) {
                graphics.textRenderer().accept(textLeft, statusY, stateLines.getFirst());
            }
        }

        drawCardRewardRow(graphics, textLeft, footerY, availTextW, footerH, spec, state, card, mouseX, mouseY);
    }

    private void drawCardRewardRow(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                                   RefugioCommanderRewards.MilestoneSpec spec, int state,
                                   Rect cardRect, int mouseX, int mouseY) {
        graphics.fill(x, y - 1, x + w, y, 0x44987348);
        int textY = y + Math.max(0, (h - font.lineHeight) / 2);

        Rect claimBtn = state == STATE_READY ? quickClaimRect(cardRect) : new Rect(0, 0, 0, 0);
        int maxRight = state == STATE_READY ? (claimBtn.x - 4) : (x + w);

        int curX = x;
        drawCoinIcon(graphics, curX, y + Math.max(0, (h - 12) / 2));
        curX += 14;
        Component coinsText = Component.literal("+" + spec.coins()).withStyle(GOLD_STYLE);
        graphics.textRenderer().accept(curX, textY, coinsText);
        curX += font.width(coinsText) + 5;

        for (RefugioCommanderRewards.RewardItemSpec r : spec.items()) {
            if (curX + 18 > maxRight) break;
            ItemStack stack = createRewardItemStack(r);
            int iconY = y + (h - 16) / 2;
            drawItemWithCount(graphics, stack, curX, iconY, r.count());
            curX += 18;
        }

        if (state == STATE_READY && claimBtn.w > 0) {
            drawButton(graphics, claimBtn.x, claimBtn.y, claimBtn.w, claimBtn.h,
                    Component.translatable("gui.refugio.commander.claim_short"),
                    mouseX, mouseY, true, 0xFF24504F);
        }
    }

    private void drawMilestoneDetailView(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CommanderLayout layout) {
        Rect area = layout.contentArea;
        graphics.fill(area.x, area.y, area.right(), area.bottom(), 0xD8EAD8B2);
        border(graphics, area.x, area.y, area.w, area.h, 0xFF967043);

        RefugioCommanderRewards.MilestoneSpec spec = RefugioCommanderRewards.milestone(selectedMilestoneNumber);
        if (spec == null) return;
        int state = milestoneState(spec.number());

        List<Component> lines = new ArrayList<>();
        String numStr = String.format("Marco #%02d (%d zumbis) — %s",
                spec.number(), spec.requiredKills(), spec.name());
        lines.add(Component.literal(numStr).withStyle(GOLD_STYLE));
        lines.add(Component.literal(spec.objective()).withStyle(MUTED_STYLE));
        lines.add(Component.translatable("gui.refugio.commander.kill_requirement",
                Math.min(zombieKills, spec.requiredKills()), spec.requiredKills())
                .withStyle(zombieKills >= spec.requiredKills() ? SUCCESS_STYLE : GOLD_STYLE));
        lines.add(stateBadgeComponent(state));
        lines.add(Component.translatable("gui.refugio.commander.coins_reward", spec.coins()).withStyle(GOLD_STYLE));
        lines.add(Component.translatable("gui.refugio.commander.items_header").withStyle(INK_STYLE));

        for (RefugioCommanderRewards.RewardItemSpec itemSpec : spec.items()) {
            lines.add(Component.literal(" • ").withStyle(GOLD_STYLE).append(formatRewardItemLine(itemSpec)));
        }
        if (!spec.exclusiveTitle().isEmpty()) {
            lines.add(Component.translatable("gui.refugio.commander.exclusive_title", spec.exclusiveTitle())
                    .withStyle(SUCCESS_STYLE));
        }
        if (!feedbackKey.isEmpty()) {
            lines.add(Component.translatable(feedbackKey).withStyle(GOLD_STYLE));
        }

        int padX = 10;
        int padY = 6;
        int maxTextW = Math.max(40, area.w - padX * 2);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component comp : lines) {
            wrapped.addAll(font.split(comp, maxTextW));
        }

        int contentH = wrapped.size() * (font.lineHeight + 2);
        int viewH = Math.max(16, area.h - padY * 2);
        int maxScroll = Math.max(0, contentH - viewH);
        detailScrollOffset = Math.max(0, Math.min(detailScrollOffset, maxScroll));

        graphics.enableScissor(area.x + 2, area.y + 2, area.right() - 2, area.bottom() - 2);
        int curY = area.y + padY - detailScrollOffset;
        for (FormattedCharSequence line : wrapped) {
            if (curY + font.lineHeight >= area.y && curY <= area.bottom()) {
                graphics.textRenderer().accept(area.x + padX, curY, line);
            }
            curY += font.lineHeight + 2;
        }
        graphics.disableScissor();
    }

    private void drawStatsView(GuiGraphicsExtractor graphics, CommanderLayout layout) {
        Rect area = layout.contentArea;
        graphics.fill(area.x, area.y, area.right(), area.bottom(), 0xD8EAD8B2);
        border(graphics, area.x, area.y, area.w, area.h, 0xFF967043);

        int unlockedCount = 0;
        int claimedCount = 0;
        int readyCount = 0;
        int earnedCoins = 0;
        for (RefugioCommanderRewards.MilestoneSpec m : RefugioCommanderRewards.activeMilestones()) {
            int st = milestoneState(m.number());
            if (st == STATE_CLAIMED) {
                claimedCount++;
                unlockedCount++;
                earnedCoins += m.coins();
            } else if (st == STATE_READY) {
                readyCount++;
                unlockedCount++;
            }
        }

        RefugioCommanderRewards.MilestoneSpec nextMs = RefugioCommanderRewards.nextUnreachedMilestone(zombieKills);
        String currentTitle = RefugioCommanderRewards.highestUnlockedTitle(zombieKills);
        RefugioCommanderRewards.TitleSpec nextTitle = RefugioCommanderRewards.nextTitle(zombieKills);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.refugio.commander.stats.header").withStyle(GOLD_STYLE));
        lines.add(Component.translatable("gui.refugio.commander.stats.total_kills", zombieKills).withStyle(INK_STYLE));
        if (nextMs != null) {
            int remaining = Math.max(0, nextMs.requiredKills() - zombieKills);
            lines.add(Component.translatable("gui.refugio.commander.stats.next_milestone",
                    nextMs.number(), nextMs.name(), zombieKills, nextMs.requiredKills(), remaining).withStyle(GOLD_STYLE));
        } else {
            lines.add(Component.translatable("gui.refugio.commander.stats.all_milestones_reached").withStyle(SUCCESS_STYLE));
        }
        lines.add(Component.translatable("gui.refugio.commander.stats.milestones_unlocked",
                unlockedCount, RefugioCommanderRewards.TOTAL_MILESTONES).withStyle(INK_STYLE));
        lines.add(Component.translatable("gui.refugio.commander.stats.milestones_claimed",
                claimedCount, RefugioCommanderRewards.TOTAL_MILESTONES, readyCount).withStyle(SUCCESS_STYLE));
        lines.add(Component.translatable("gui.refugio.commander.stats.coins_progress",
                earnedCoins, RefugioCommanderRewards.TOTAL_TRACK_COINS).withStyle(GOLD_STYLE));
        lines.add(Component.translatable("gui.refugio.commander.stats.current_title",
                currentTitle.isEmpty() ? Component.translatable("gui.refugio.commander.no_rank") : Component.literal(currentTitle))
                .withStyle(INK_STYLE));
        if (nextTitle != null) {
            lines.add(Component.translatable("gui.refugio.commander.stats.next_title",
                    nextTitle.title(), nextTitle.requiredKills()).withStyle(MUTED_STYLE));
        } else {
            lines.add(Component.translatable("gui.refugio.commander.stats.max_title").withStyle(SUCCESS_STYLE));
        }

        int padX = 12;
        int curY = area.y + 8;
        int maxW = Math.max(40, area.w - padX * 2);
        for (Component line : lines) {
            for (FormattedCharSequence seq : font.split(line, maxW)) {
                if (curY + font.lineHeight > area.bottom() - 4) break;
                graphics.textRenderer().accept(area.x + padX, curY, seq);
                curY += font.lineHeight + 3;
            }
        }
    }

    private void drawRanksView(GuiGraphicsExtractor graphics, CommanderLayout layout) {
        List<RefugioCommanderRewards.TitleSpec> titles = RefugioCommanderRewards.activeTitles();
        for (int i = 0; i < layout.milestoneCards.size(); i++) {
            SlotRect slot = layout.milestoneCards.get(i);
            int idx = layout.pageStart + i;
            if (idx >= titles.size()) break;
            RefugioCommanderRewards.TitleSpec title = titles.get(idx);
            Rect r = slot.rect;
            boolean unlocked = isTitleUnlocked(title);

            graphics.fill(r.x, r.y, r.right(), r.bottom(), unlocked ? 0xFFEFE3C2 : 0xFFD4C7AD);
            border(graphics, r.x, r.y, r.w, r.h, unlocked ? 0xFF5B7F46 : 0xFF8E806A);
            graphics.fill(r.x + 2, r.y + 2, r.x + 5, r.bottom() - 2, unlocked ? 0xFF2D5B22 : FACTION_ACCENT);

            MutableComponent header = Component.literal(title.title())
                    .withStyle(unlocked ? GOLD_STYLE : MUTED_STYLE);
            Component req = Component.translatable("gui.refugio.commander.rank_req",
                    Math.min(zombieKills, title.requiredKills()), title.requiredKills(),
                    Component.translatable(unlocked ? "gui.refugio.commander.rank_unlocked" : "gui.refugio.commander.rank_locked"))
                    .withStyle(unlocked ? SUCCESS_STYLE : INK_STYLE);

            int textX = r.x + 10;
            int maxW = Math.max(24, r.w - 14);
            List<FormattedCharSequence> hLines = font.split(header, maxW);
            List<FormattedCharSequence> rLines = font.split(req, maxW);
            int startY = r.y + Math.max(2, (r.h - font.lineHeight * 2 - 2) / 2);
            if (!hLines.isEmpty()) {
                graphics.textRenderer().accept(textX, startY, hLines.getFirst());
            }
            if (!rLines.isEmpty() && startY + font.lineHeight + 2 + font.lineHeight <= r.bottom()) {
                graphics.textRenderer().accept(textX, startY + font.lineHeight + 2, rLines.getFirst());
            }
        }
    }

    private void drawFooterBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CommanderLayout layout) {
        if (activeTab == TAB_REWARDS && selectedMilestoneNumber > 0) {
            drawButton(graphics, layout.primaryNavButton.x, layout.primaryNavButton.y,
                    layout.primaryNavButton.w, layout.primaryNavButton.h,
                    Component.translatable("gui.refugio.back"), mouseX, mouseY, true, 0xFF293A48);
            int state = milestoneState(selectedMilestoneNumber);
            Component claimLabel = switch (state) {
                case STATE_CLAIMED -> Component.translatable("gui.refugio.quest_done");
                case STATE_READY -> Component.translatable("gui.refugio.commander.claim_button");
                default -> Component.translatable("gui.refugio.quest_locked");
            };
            drawButton(graphics, layout.claimDetailButton.x, layout.claimDetailButton.y,
                    layout.claimDetailButton.w, layout.claimDetailButton.h,
                    claimLabel, mouseX, mouseY, state == STATE_READY, 0xFF24504F);
            return;
        }

        drawButton(graphics, layout.primaryNavButton.x, layout.primaryNavButton.y,
                layout.primaryNavButton.w, layout.primaryNavButton.h,
                Component.translatable("gui.refugio.close"), mouseX, mouseY, true, 0xFF293A48);

        if (activeTab == TAB_REWARDS || activeTab == TAB_RANKS) {
            int currentPage = activeTab == TAB_RANKS ? ranksPage : rewardsPage;
            Rect prev = layout.prevPageButton;
            Rect next = layout.nextPageButton;
            drawButton(graphics, prev.x, prev.y, prev.w, prev.h, Component.literal("‹"),
                    mouseX, mouseY, currentPage > 0, 0xFF293A48);
            drawButton(graphics, next.x, next.y, next.w, next.h, Component.literal("›"),
                    mouseX, mouseY, currentPage + 1 < layout.pageCount, 0xFF293A48);

            Component pageComp = Component.translatable("gui.refugio.page", currentPage + 1, layout.pageCount)
                    .withStyle(GOLD_STYLE);
            int midX = (prev.right() + next.x) / 2;
            graphics.textRenderer().accept(midX - font.width(pageComp) / 2,
                    prev.y + (prev.h - font.lineHeight) / 2, pageComp);
        }
    }

    private void drawMilestoneTooltip(GuiGraphicsExtractor graphics,
                                      RefugioCommanderRewards.MilestoneSpec spec,
                                      int mouseX, int mouseY) {
        int state = milestoneState(spec.number());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(String.format("Marco #%02d — %s", spec.number(), spec.name()))
                .withStyle(GOLD_STYLE));
        lines.add(Component.literal(spec.objective()).withStyle(MUTED_STYLE));
        lines.add(Component.translatable("gui.refugio.commander.kill_requirement",
                Math.min(zombieKills, spec.requiredKills()), spec.requiredKills()).withStyle(LIGHT_STYLE));
        lines.add(Component.translatable("gui.refugio.commander.coins_reward", spec.coins()).withStyle(GOLD_STYLE));

        for (RefugioCommanderRewards.RewardItemSpec itemSpec : spec.items()) {
            lines.add(Component.literal("• ").withStyle(GOLD_STYLE)
                    .append(formatRewardItemLine(itemSpec).copy().withStyle(LIGHT_STYLE)));
        }
        if (!spec.exclusiveTitle().isEmpty()) {
            lines.add(Component.translatable("gui.refugio.commander.exclusive_title", spec.exclusiveTitle())
                    .withStyle(SUCCESS_STYLE));
        }
        lines.add(stateBadgeComponent(state));

        int maxW = Math.min(260, Math.max(110, width * 3 / 5));
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
            case STATE_CLAIMED -> Component.translatable("gui.refugio.quest_done").withStyle(SUCCESS_STYLE);
            case STATE_READY -> Component.translatable("gui.refugio.quest_claim_ready").withStyle(GOLD_STYLE);
            default -> Component.translatable("gui.refugio.quest_locked").withStyle(MUTED_STYLE);
        };
    }

    private MutableComponent formatRewardItemLine(RefugioCommanderRewards.RewardItemSpec spec) {
        ItemStack stack = createRewardItemStack(spec);
        MutableComponent line = Component.literal(spec.count() + "× ").append(stack.getHoverName());
        if (!spec.enchantments().isEmpty()) {
            line.append(Component.literal(" ("));
            for (int i = 0; i < spec.enchantments().size(); i++) {
                if (i > 0) line.append(Component.literal(", "));
                RefugioCommanderRewards.EnchantSpec ench = spec.enchantments().get(i);
                line.append(formatEnchantmentName(ench.enchantmentId(), ench.level()));
            }
            line.append(Component.literal(")"));
        }
        return line.withStyle(INK_STYLE);
    }

    private Component formatEnchantmentName(String enchantmentId, int level) {
        String path = enchantmentId.contains(":") ? enchantmentId.substring(enchantmentId.indexOf(':') + 1) : enchantmentId;
        MutableComponent name = Component.translatable("enchantment.minecraft." + path);
        if (level > 1 || (!"flame".equals(path) && !"infinity".equals(path)
                && !"mending".equals(path) && !"channeling".equals(path))) {
            name.append(Component.literal(" " + toRoman(level)));
        }
        return name;
    }

    private static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(level);
        };
    }

    private ItemStack createRewardItemStack(RefugioCommanderRewards.RewardItemSpec spec) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(spec.itemId()));
        if (item == Items.AIR) {
            item = BuiltInRegistries.ITEM.getValue(Identifier.parse(spec.fallbackItemId()));
        }
        if (item == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, Math.max(1, spec.count()));
        if (!spec.enchantments().isEmpty()) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            if (minecraft != null && minecraft.level != null) {
                try {
                    var reg = minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                    for (RefugioCommanderRewards.EnchantSpec ench : spec.enchantments()) {
                        var key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(ench.enchantmentId()));
                        var holderOpt = reg.get(key);
                        if (holderOpt.isPresent()) {
                            Holder<Enchantment> holder = holderOpt.get();
                            if (stack.is(Items.ENCHANTED_BOOK)) {
                                ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(
                                        stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY));
                                stored.set(holder, ench.level());
                                stack.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
                            } else {
                                stack.enchant(holder, ench.level());
                            }
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

    private record CommanderLayout(
            Rect panel, Rect inner, Rect titleBanner, Rect balanceBadge, Rect closeButton,
            boolean compactBalance, Rect npcBannerBox,
            Rect tabRewardsButton, Rect tabStatsButton, Rect tabRanksButton,
            Rect contentArea, int pageStart, int pageCount, List<SlotRect> milestoneCards,
            Rect primaryNavButton, Rect claimDetailButton, Rect prevPageButton, Rect nextPageButton
    ) { }
}
