package com.felpz.refugio.client;

import com.felpz.refugio.ability.RefugioAbilityData;
import com.felpz.refugio.ui.RefugioUiPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Custom, non-container skill tree. Server snapshots drive all ranks, balance and activation state. */
public final class RefugioAbilityScreen extends Screen {
    private static final int[] ACCENTS = {0xFFB76545, 0xFF4C8878, 0xFFC0944B, 0xFF82934F};
    private static final int SKILLS_PER_PAGE = 4;
    private static final Identifier PARCHMENT_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/parchment.png");
    private static final Identifier WOOD_FRAME_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/wood_frame.png");
    private static final Identifier TITLE_PLAQUE_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/title_plaque.png");
    private static final Identifier PRIMARY_BUTTON_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/button_primary.png");
    private static final Identifier SECONDARY_BUTTON_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/button_close.png");
    private static final FontDescription UI_FONT = new FontDescription.Resource(Identifier.parse("minecraft:uniform"));
    private static final Style INK_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF4A3520);
    private static final Style MUTED_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF80684B);
    private static final Style GOLD_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF87551F);
    private static final Style SUCCESS_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF2D5B22);
    private static final Style LIGHT_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFFFFF0D0);
    private static final Style DISABLED_BUTTON_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFFBBAA8D);

    private int coins;
    private String freeChoice = "";
    private int[] ranks = new int[0];
    private int activeMask;
    private String feedbackKey = "";
    private boolean showingCategories = true;
    private boolean showingSubcategories;
    private boolean showingSkillDetails;
    private int branchIndex;
    private RefugioAbilityData.Track selectedTrack = RefugioAbilityData.Track.SWORD;
    private int skillPageIndex;
    private String selectedSkillId;
    private int detailScrollOffset;

    public RefugioAbilityScreen(String encodedState) {
        super(Component.translatable("gui.refugio.abilities.title"));
        updateState(encodedState);
    }

    public void updateState(String encodedState) {
        String[] parts = encodedState.split("\\t", -1);
        if (parts.length < 6 || !parts[0].equals("ABILITY")) return;
        try {
            coins = Integer.parseInt(parts[1]);
            freeChoice = parts[2];
            String[] values = parts[3].split(",");
            ranks = new int[values.length];
            List<RefugioAbilityData.Skill> skills = RefugioAbilityData.skills();
            for (int i = 0; i < values.length; i++) {
                int maxRank = i < skills.size() ? RefugioAbilityData.maxRank(skills.get(i).id()) : RefugioAbilityData.maxRank();
                ranks[i] = Math.max(0, Math.min(maxRank, Integer.parseInt(values[i])));
            }
            activeMask = Integer.parseInt(parts[4]);
            feedbackKey = parts[5];
        } catch (NumberFormatException ignored) {
            ranks = new int[0];
        }
        if (selectedSkillId == null || RefugioAbilityData.skill(selectedSkillId) == null) selectFirstSkill();
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
        AbilityLayout layout = computeLayout();
        drawParchmentAndWoodFrame(graphics, layout.panel);
        drawHeader(graphics, mouseX, mouseY, layout);
        if (showingCategories) {
            drawCategoryOverview(graphics, mouseX, mouseY, layout);
        } else if (showingSubcategories) {
            drawNavigationHeader(graphics, mouseX, mouseY, layout);
            drawSubcategoryOverview(graphics, mouseX, mouseY, layout);
        } else if (showingSkillDetails) {
            drawNavigationHeader(graphics, mouseX, mouseY, layout);
            drawSkillDetailsPage(graphics, mouseX, mouseY, layout);
        } else {
            drawNavigationHeader(graphics, mouseX, mouseY, layout);
            drawSkillPath(graphics, mouseX, mouseY, layout);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        double mx = event.x();
        double my = event.y();
        AbilityLayout layout = computeLayout();
        if (layout.closeButton.contains(mx, my)) {
            closeScreen();
            return true;
        }
        if (showingCategories) {
            RefugioAbilityData.Branch[] branches = RefugioAbilityData.Branch.values();
            for (int i = 0; i < Math.min(branches.length, layout.categoryCards.size()); i++) {
                if (!layout.categoryCards.get(i).contains(mx, my)) continue;
                branchIndex = i;
                RefugioAbilityData.Branch branch = currentBranch();
                skillPageIndex = 0;
                detailScrollOffset = 0;
                feedbackKey = "";
                showingSkillDetails = false;
                showingCategories = false;
                if (branch.hasSubcategories()) {
                    showingSubcategories = true;
                } else {
                    selectedTrack = RefugioAbilityData.tracks(branch).getFirst();
                    showingSubcategories = false;
                    selectFirstSkill();
                }
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (layout.backButton.contains(mx, my)) {
            if (showingSkillDetails) {
                showingSkillDetails = false;
            } else if (showingSubcategories) {
                showingSubcategories = false;
                showingCategories = true;
            } else if (selectedTrack.branch().hasSubcategories()) {
                showingSubcategories = true;
            } else {
                showingCategories = true;
            }
            skillPageIndex = 0;
            detailScrollOffset = 0;
            feedbackKey = "";
            return true;
        }
        if (showingSubcategories) {
            List<RefugioAbilityData.Track> tracks = RefugioAbilityData.tracks(currentBranch());
            for (int i = 0; i < Math.min(tracks.size(), layout.trackCards.size()); i++) {
                RefugioAbilityData.Track track = tracks.get(i);
                if (!layout.trackCards.get(i).contains(mx, my)
                        || RefugioAbilityData.skills(track).isEmpty()) continue;
                selectedTrack = track;
                List<RefugioAbilityData.Skill> trackSkills = currentTrackSkills();
                if (!trackSkills.isEmpty()) selectedSkillId = trackSkills.getFirst().id();
                skillPageIndex = 0;
                detailScrollOffset = 0;
                feedbackKey = "";
                showingSubcategories = false;
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (showingSkillDetails) {
            if (layout.buyButton.contains(mx, my) && canPurchase(selectedSkill())) {
                ClientPlayNetworking.send(new RefugioUiPayload("BUY\t" + selectedSkillId));
                feedbackKey = "gui.refugio.abilities.feedback.processing";
                return true;
            }
            if (layout.toggleButton.contains(mx, my) && selectedRank() > 0) {
                ClientPlayNetworking.send(new RefugioUiPayload("TOGGLE\t" + selectedSkillId));
                feedbackKey = "gui.refugio.abilities.feedback.processing";
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        if (layout.prevPageButton.contains(mx, my) && skillPageIndex > 0) {
            skillPageIndex = Math.max(0, skillPageIndex - 1);
            return true;
        }
        if (layout.nextPageButton.contains(mx, my) && skillPageIndex + 1 < layout.pageCount) {
            skillPageIndex = Math.min(layout.pageCount - 1, skillPageIndex + 1);
            return true;
        }
        List<RefugioAbilityData.Skill> trackSkills = currentTrackSkills();
        for (int i = 0; i < layout.skillCards.size(); i++) {
            int skillIndex = layout.pageStart + i;
            if (skillIndex >= trackSkills.size()) break;
            if (layout.skillCards.get(i).contains(mx, my)) {
                selectedSkillId = trackSkills.get(skillIndex).id();
                detailScrollOffset = 0;
                feedbackKey = "";
                showingSkillDetails = true;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0.0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        AbilityLayout layout = computeLayout();
        if (!showingCategories && !showingSubcategories && !showingSkillDetails) {
            if (layout.skillGridArea.contains(mouseX, mouseY) && layout.pageCount > 1) {
                skillPageIndex = Math.max(0, Math.min(layout.pageCount - 1,
                        skillPageIndex - (int) Math.signum(verticalAmount)));
                return true;
            }
        } else if (showingSkillDetails) {
            RefugioAbilityData.Skill skill = selectedSkill();
            if (skill != null && layout.detailDescriptionBox.contains(mouseX, mouseY)) {
                int maxScroll = maxDetailScroll(skill, layout);
                if (maxScroll > 0) {
                    detailScrollOffset = Math.max(0, Math.min(maxScroll,
                            detailScrollOffset - (int) Math.signum(verticalAmount) * (font.lineHeight + 2)));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    private AbilityLayout computeLayout() {
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
        Component fullBalanceText = Component.translatable("gui.refugio.abilities.balance", coins).withStyle(LIGHT_STYLE);
        Component shortBalanceText = Component.literal(Integer.toString(coins)).withStyle(LIGHT_STYLE);
        boolean compactBalance = innerW < 450;
        int desiredBadgeW = compactBalance
                ? Math.max(48, Math.min(86, font.width(shortBalanceText) + 28))
                : Math.min(156, Math.max(106, font.width(fullBalanceText) + 34));
        int badgeH = closeSize;
        int badgeX = closeX - headerGap - desiredBadgeW;
        int badgeY = headerY + (headerH - badgeH) / 2;
        Rect balanceBadge = new Rect(badgeX, badgeY, desiredBadgeW, badgeH);

        Component titleComponent = Component.translatable("gui.refugio.abilities.title").withStyle(LIGHT_STYLE);
        Component kickerComponent = Component.translatable("gui.refugio.abilities.kicker").withStyle(MUTED_STYLE);
        int maxBannerRight = badgeX - headerGap;
        int availBannerArea = Math.max(64, maxBannerRight - innerX);
        int idealBannerW = Math.min(340, Math.max(140, font.width(titleComponent) + 42));
        int centeredBannerX = px + (pw - idealBannerW) / 2;
        boolean canCenterBanner = innerW >= 460
                && centeredBannerX >= innerX
                && centeredBannerX + idealBannerW <= maxBannerRight;
        int bannerW = canCenterBanner ? idealBannerW : Math.min(idealBannerW, availBannerArea);
        int bannerX = canCenterBanner ? centeredBannerX : innerX;
        Rect titleBanner = new Rect(bannerX, headerY, bannerW, headerH);
        boolean showKicker = canCenterBanner && (bannerX - innerX >= font.width(kickerComponent) + 22);

        int sectionGap = innerH < 145 ? 2 : (innerH < 185 ? 3 : (innerH < 230 ? 5 : 7));
        int subHeaderTop = headerY + headerH + sectionGap;

        int promptY = subHeaderTop + (innerH < 180 ? 0 : 2);
        int categoryGridTop = promptY + font.lineHeight + sectionGap;
        int categoryGridH = Math.max(36, inner.bottom() - categoryGridTop);
        int catGapX = innerW < 340 ? 6 : 12;
        int catGapY = categoryGridH < 90 ? 4 : (categoryGridH < 120 ? 6 : 10);
        int catCardW = Math.max(50, (innerW - catGapX) / 2);
        int catCardH = Math.max(16, (categoryGridH - catGapY) / 2);
        List<Rect> categoryCards = new ArrayList<>(4);
        for (int i = 0; i < RefugioAbilityData.Branch.values().length; i++) {
            int col = i % 2;
            int row = i / 2;
            categoryCards.add(new Rect(
                    innerX + col * (catCardW + catGapX),
                    categoryGridTop + row * (catCardH + catGapY),
                    catCardW, catCardH));
        }

        int navH = innerH < 140 ? 14 : (innerH < 175 ? 16 : (innerH < 220 ? 20 : (innerH < 270 ? 22 : 25)));
        int navY = subHeaderTop;
        int backW = Math.max(76, Math.min(146, innerW * 34 / 100));
        Rect backButton = new Rect(innerX, navY, backW, navH);

        int subPromptY = navY + navH + sectionGap;
        int trackGridTop = subPromptY + font.lineHeight + sectionGap;
        int trackAvailH = Math.max(36, inner.bottom() - trackGridTop);
        List<RefugioAbilityData.Track> tracks = RefugioAbilityData.tracks(currentBranch());
        int trackCount = Math.max(1, tracks.size());
        int trackRows = Math.max(1, (trackCount + 1) / 2);
        int trackGapX = catGapX;
        int trackGapY = trackAvailH < 90 ? 4 : (trackAvailH < 120 ? 6 : 10);
        int trackCardW = Math.max(50, (innerW - trackGapX) / 2);
        int maxTrackCardH = Math.max(16, (trackAvailH - trackGapY * Math.max(0, trackRows - 1)) / trackRows);
        int trackCardH = Math.min(82, maxTrackCardH);
        int totalTrackGridH = trackRows * trackCardH + Math.max(0, trackRows - 1) * trackGapY;
        int actualTrackTop = trackGridTop + Math.max(0, (trackAvailH - totalTrackGridH) / 2);
        List<Rect> trackCards = new ArrayList<>(trackCount);
        for (int i = 0; i < tracks.size(); i++) {
            int row = i / 2;
            int col = i % 2;
            int rowCount = Math.min(2, tracks.size() - row * 2);
            int rowWidth = rowCount * trackCardW + Math.max(0, rowCount - 1) * trackGapX;
            int rowLeft = innerX + (innerW - rowWidth) / 2;
            trackCards.add(new Rect(
                    rowLeft + col * (trackCardW + trackGapX),
                    actualTrackTop + row * (trackCardH + trackGapY),
                    trackCardW, trackCardH));
        }

        int dividerY = navY + navH + Math.max(2, sectionGap - 1);
        int pathAreaTop = dividerY + 2;
        int pathAvailH = Math.max(40, inner.bottom() - pathAreaTop);
        boolean compactPath = pathAvailH < 132;

        List<RefugioAbilityData.Skill> trackSkills = currentTrackSkills();
        int pageCount = Math.max(1, (trackSkills.size() + SKILLS_PER_PAGE - 1) / SKILLS_PER_PAGE);
        skillPageIndex = Math.max(0, Math.min(skillPageIndex, pageCount - 1));
        int pageStart = skillPageIndex * SKILLS_PER_PAGE;
        int visibleSkillCount = Math.max(0, Math.min(SKILLS_PER_PAGE, trackSkills.size() - pageStart));

        int pageBtnW = compactPath ? (pathAvailH < 88 ? 20 : 24) : 32;
        int pageBtnH = compactPath ? (pathAvailH < 88 ? 13 : (pathAvailH < 105 ? 15 : 18)) : 20;
        Rect prevPageButton;
        Rect nextPageButton;
        int pathLabelY;
        int skillGridTop;
        int skillGridBottom;

        if (compactPath) {
            int controlsY = pathAreaTop + 1;
            nextPageButton = new Rect(inner.right() - pageBtnW, controlsY, pageBtnW, pageBtnH);
            prevPageButton = new Rect(nextPageButton.x - 4 - pageBtnW, controlsY, pageBtnW, pageBtnH);
            pathLabelY = controlsY + Math.max(0, (pageBtnH - font.lineHeight) / 2);
            skillGridTop = controlsY + pageBtnH + (pathAvailH < 96 ? 2 : 4);
            skillGridBottom = inner.bottom();
        } else {
            pathLabelY = pathAreaTop + 2;
            int controlsY = inner.bottom() - pageBtnH;
            int center = innerX + innerW / 2;
            int halfSpan = Math.min(82, Math.max(54, innerW / 6));
            prevPageButton = new Rect(center - halfSpan - pageBtnW, controlsY, pageBtnW, pageBtnH);
            nextPageButton = new Rect(center + halfSpan, controlsY, pageBtnW, pageBtnH);
            skillGridTop = pathLabelY + font.lineHeight + 5;
            skillGridBottom = controlsY - 6;
        }

        int gridAvailH = Math.max(28, skillGridBottom - skillGridTop);
        int skillColumns = (innerW >= 320 && gridAvailH >= 48) ? SKILLS_PER_PAGE : 2;
        int skillRows = Math.max(1, (Math.max(1, visibleSkillCount) + skillColumns - 1) / skillColumns);
        int skillGapX = skillColumns == 1 ? 0 : (innerW < 380 ? 6 : 10);
        int skillGapY = skillRows == 1 ? 0 : (gridAvailH < 84 ? 3 : 7);
        int skillCardW = Math.max(44, (innerW - skillGapX * (skillColumns - 1)) / skillColumns);
        int maxAllowedCardH = Math.max(16, (gridAvailH - skillGapY * (skillRows - 1)) / skillRows);
        int preferredCardH = skillRows == 1
                ? Math.min(132, Math.max(48, (int) Math.round(skillCardW * 1.12)))
                : Math.min(80, maxAllowedCardH);
        int skillCardH = Math.min(maxAllowedCardH, preferredCardH);
        int totalSkillGridH = skillRows * skillCardH + Math.max(0, skillRows - 1) * skillGapY;
        int centeredSkillTop = skillGridTop + Math.max(0, (gridAvailH - totalSkillGridH) / 2);
        Rect skillGridArea = new Rect(innerX, skillGridTop, innerW, gridAvailH);

        List<Rect> skillCards = new ArrayList<>(visibleSkillCount);
        for (int i = 0; i < visibleSkillCount; i++) {
            int row = i / skillColumns;
            int col = i % skillColumns;
            skillCards.add(new Rect(
                    innerX + col * (skillCardW + skillGapX),
                    centeredSkillTop + row * (skillCardH + skillGapY),
                    skillCardW, skillCardH));
        }

        int detailTop = navY + navH + Math.max(2, sectionGap - 1);
        int detailH = Math.max(52, inner.bottom() - detailTop);
        Rect detailPanel = new Rect(innerX, detailTop, innerW, detailH);
        int detailPadX = innerW < 340 ? 6 : 14;
        int detailPadY = detailH < 95 ? 3 : (detailH < 140 ? 6 : 10);
        int detailInnerX = detailPanel.x + detailPadX;
        int detailInnerW = Math.max(60, detailPanel.w - detailPadX * 2);
        int actionBtnH = detailH < 90 ? 14 : (detailH < 125 ? 18 : (detailH < 170 ? 22 : 28));
        int actionBtnY = detailPanel.bottom() - detailPadY - actionBtnH;
        int actionGap = innerW < 340 ? 6 : 12;
        int actionBtnW = Math.max(40, (detailInnerW - actionGap) / 2);
        Rect buyButton = new Rect(detailInnerX, actionBtnY, actionBtnW, actionBtnH);
        Rect toggleButton = new Rect(buyButton.right() + actionGap, actionBtnY, actionBtnW, actionBtnH);

        boolean showFeedback = !feedbackKey.isEmpty()
                && !feedbackKey.equals("gui.refugio.abilities.feedback.enabled")
                && !feedbackKey.equals("gui.refugio.abilities.feedback.disabled");
        int availAboveButtons = actionBtnY - (detailPanel.y + detailPadY) - (detailH < 100 ? 2 : 5);
        boolean compactDetails = availAboveButtons < 96;
        Rect detailStatusBox;
        Rect detailRankBar;
        Rect detailDescriptionBox;
        int detailFeedbackY;

        if (compactDetails) {
            int statusH = font.lineHeight + (availAboveButtons < 56 ? 2 : 4);
            detailStatusBox = new Rect(detailInnerX, detailPanel.y + detailPadY, detailInnerW, statusH);
            int rankBarY = detailStatusBox.bottom() + (availAboveButtons < 56 ? 1 : 2);
            int rankBarH = availAboveButtons < 56 ? 3 : 4;
            detailRankBar = new Rect(detailInnerX, rankBarY, detailInnerW, rankBarH);
            int descTop = detailRankBar.bottom() + (availAboveButtons < 56 ? 2 : 3);
            int feedbackReserve = (showFeedback && availAboveButtons >= 58) ? (font.lineHeight + 1) : 0;
            detailFeedbackY = actionBtnY - feedbackReserve - 1;
            int descBottom = Math.max(descTop + font.lineHeight + 2,
                    (feedbackReserve > 0 ? detailFeedbackY - 2 : actionBtnY - 2));
            detailDescriptionBox = new Rect(detailInnerX, descTop, detailInnerW, Math.max(10, descBottom - descTop));
        } else {
            int rankBarY = detailPanel.y + detailPadY + font.lineHeight + 3;
            detailRankBar = new Rect(detailInnerX, rankBarY, detailInnerW, 6);
            int feedbackReserve = showFeedback ? (font.lineHeight + 2) : 0;
            int statusH = font.lineHeight + feedbackReserve + 6;
            int statusY = actionBtnY - 6 - statusH;
            detailStatusBox = new Rect(detailInnerX, statusY, detailInnerW, statusH);
            detailFeedbackY = statusY + 3 + font.lineHeight + 2;
            int descTop = detailRankBar.bottom() + 5;
            int descBottom = Math.max(descTop + font.lineHeight + 6, statusY - 5);
            detailDescriptionBox = new Rect(detailInnerX, descTop, detailInnerW, Math.max(16, descBottom - descTop));
        }

        return new AbilityLayout(
                panel, inner, titleBanner, balanceBadge, closeButton,
                compactBalance, showKicker, promptY, categoryCards,
                backButton, subPromptY, trackCards,
                dividerY, pathLabelY, compactPath, pageStart, pageCount,
                prevPageButton, nextPageButton, skillGridArea, skillCards,
                detailPanel, compactDetails, showFeedback,
                detailStatusBox, detailRankBar, detailDescriptionBox, detailFeedbackY,
                buyButton, toggleButton
        );
    }

    private void drawParchmentAndWoodFrame(GuiGraphicsExtractor graphics, Rect panel) {
        int insetX = Math.max(1, (int) Math.round(panel.w * 0.052));
        int insetY = Math.max(1, (int) Math.round(panel.h * 0.082));
        blitFull(graphics, PARCHMENT_TEXTURE, panel.x + insetX, panel.y + insetY,
                Math.max(1, panel.w - insetX * 2), Math.max(1, panel.h - insetY * 2));
        blitFull(graphics, WOOD_FRAME_TEXTURE, panel.x, panel.y, panel.w, panel.h);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY, AbilityLayout layout) {
        Component title = Component.translatable("gui.refugio.abilities.title").withStyle(LIGHT_STYLE);
        Rect banner = layout.titleBanner;
        if (banner.w > 0 && banner.h > 0) {
            graphics.blit(TITLE_PLAQUE_TEXTURE, banner.x, banner.y, banner.right(), banner.bottom(),
                    0.02F, 0.98F, 0.32F, 0.66F);
        }
        List<FormattedCharSequence> titleLines = font.split(title, Math.max(16, banner.w - 24));
        int maxLines = banner.h >= font.lineHeight * 2 + 4 ? 2 : 1;
        int titleCount = Math.min(maxLines, titleLines.size());
        int titleY = banner.y + Math.max(0, (banner.h - titleCount * font.lineHeight) / 2);
        for (int i = 0; i < titleCount; i++) {
            FormattedCharSequence line = titleLines.get(i);
            graphics.textRenderer().accept(banner.x + (banner.w - font.width(line)) / 2,
                    titleY + i * font.lineHeight, line);
        }
        if (layout.showKicker) {
            Component kicker = Component.translatable("gui.refugio.abilities.kicker").withStyle(MUTED_STYLE);
            text(graphics, kicker, layout.inner.x, banner.y + (banner.h - font.lineHeight) / 2);
            drawDiamond(graphics, banner.x - 9, banner.y + banner.h / 2, 3, 0xFFB89455);
            drawDiamond(graphics, banner.right() + 9, banner.y + banner.h / 2, 3, 0xFFB89455);
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
                : Component.translatable("gui.refugio.abilities.balance", coins);
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

    private void drawCategoryOverview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, AbilityLayout layout) {
        drawCenteredPrompt(graphics, Component.translatable("gui.refugio.abilities.choose_branch").withStyle(MUTED_STYLE),
                layout.inner, layout.promptY);
        RefugioAbilityData.Branch[] branches = RefugioAbilityData.Branch.values();
        for (int i = 0; i < Math.min(branches.length, layout.categoryCards.size()); i++) {
            drawCategoryCard(graphics, mouseX, mouseY, i, layout.categoryCards.get(i));
        }
    }

    private void drawCenteredPrompt(GuiGraphicsExtractor graphics, Component prompt, Rect inner, int y) {
        List<FormattedCharSequence> lines = font.split(prompt, Math.max(40, inner.w - 32));
        if (lines.isEmpty()) return;
        FormattedCharSequence line = lines.getFirst();
        int lineW = font.width(line);
        int textX = inner.x + (inner.w - lineW) / 2;
        graphics.textRenderer().accept(textX, y, line);
        if (lineW + 28 < inner.w) {
            int diamondY = y + font.lineHeight / 2;
            drawDiamond(graphics, textX - 10, diamondY, 3, 0xFFB89455);
            drawDiamond(graphics, textX + lineW + 10, diamondY, 3, 0xFFB89455);
        }
    }

    private void drawCategoryCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int index, Rect card) {
        boolean hovered = card.contains(mouseX, mouseY);
        int accent = ACCENTS[index % ACCENTS.length];
        RefugioAbilityData.Branch branch = RefugioAbilityData.Branch.values()[index];
        List<RefugioAbilityData.Skill> skills = RefugioAbilityData.skills(branch);
        String iconId = skills.isEmpty() ? "minecraft:book" : skills.getFirst().icon();
        int learned = 0;
        for (RefugioAbilityData.Skill skill : skills) if (rank(skill) > 0) learned++;
        Component countLabel = Component.translatable("gui.refugio.abilities.branch_skill_count", skills.size())
                .withStyle(MUTED_STYLE);
        drawOverviewCard(graphics, card, iconId, branchName(branch).withStyle(INK_STYLE),
                countLabel, learned, skills.size(), false, hovered, accent);
    }

    private void drawSubcategoryOverview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, AbilityLayout layout) {
        drawCenteredPrompt(graphics, Component.translatable("gui.refugio.abilities.choose_track").withStyle(MUTED_STYLE),
                layout.inner, layout.subPromptY);
        List<RefugioAbilityData.Track> tracks = RefugioAbilityData.tracks(currentBranch());
        for (int i = 0; i < Math.min(tracks.size(), layout.trackCards.size()); i++) {
            drawTrackCard(graphics, mouseX, mouseY, tracks.get(i), layout.trackCards.get(i));
        }
    }

    private void drawTrackCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                               RefugioAbilityData.Track track, Rect card) {
        List<RefugioAbilityData.Skill> skills = RefugioAbilityData.skills(track);
        boolean empty = skills.isEmpty();
        boolean hovered = !empty && card.contains(mouseX, mouseY);
        int accent = empty ? 0xFF8A8274 : ACCENTS[branchIndex % ACCENTS.length];
        int learned = 0;
        for (RefugioAbilityData.Skill skill : skills) if (rank(skill) > 0) learned++;
        MutableComponent countLabel = empty ? Component.translatable("gui.refugio.abilities.track.coming_soon")
                : Component.translatable("gui.refugio.abilities.branch_skill_count", skills.size());
        drawOverviewCard(graphics, card, track.icon(),
                trackName(track).withStyle(empty ? MUTED_STYLE : INK_STYLE),
                countLabel.withStyle(MUTED_STYLE), learned, skills.size(), empty, hovered, accent);
    }

    private void drawOverviewCard(GuiGraphicsExtractor graphics, Rect card, String iconId,
                                  Component title, Component subtitle, int learned, int total,
                                  boolean disabled, boolean hovered, int accent) {
        int x = card.x;
        int y = card.y;
        int w = card.w;
        int h = card.h;
        graphics.fill(x, y, x + w, y + h, disabled ? 0xFFD4C7AD : hovered ? 0xFFF5E4C0 : 0xFFEAD8B2);
        border(graphics, x, y, w, h, disabled ? 0xFF8E806A : hovered ? 0xFFD3A34F : 0xFF987348);
        graphics.fill(x + 3, y + 3, x + 6, y + h - 3, accent);

        boolean narrow = w < 145;
        int iconX = x + (narrow ? 9 : 15);
        int iconY = y + Math.max(1, (h - 16) / 2);
        ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(iconId)));
        graphics.item(icon, iconX, iconY);

        int textX = iconX + (narrow ? 19 : 25);
        int rightPad = narrow ? 14 : 20;
        int textWidth = Math.max(16, w - (textX - x) - rightPad);
        boolean showProgress = !disabled && h >= 42;
        int availTextH = showProgress ? (h - 10) : (h - 4);
        List<FormattedCharSequence> nameLines = font.split(title, textWidth);
        int maxTitleLines = availTextH >= font.lineHeight * 3 + 4 ? 2 : 1;
        int titleCount = Math.min(maxTitleLines, nameLines.size());
        boolean showSub = availTextH >= font.lineHeight * 2 + 1;
        List<FormattedCharSequence> subLines = showSub ? font.split(subtitle, textWidth) : List.of();
        int subCount = subLines.isEmpty() ? 0 : 1;
        int gap = h < 34 ? 1 : 2;
        int totalBlockH = titleCount * font.lineHeight + (subCount > 0 ? gap + font.lineHeight : 0);
        int startY = y + Math.max(1, ((showProgress ? h - 6 : h) - totalBlockH) / 2);

        for (int i = 0; i < titleCount; i++) {
            graphics.textRenderer().accept(textX, startY + i * font.lineHeight, nameLines.get(i));
        }
        if (subCount > 0) {
            graphics.textRenderer().accept(textX, startY + titleCount * font.lineHeight + gap, subLines.getFirst());
        }

        if (showProgress) {
            int progressY = y + h - 5;
            int progressWidth = Math.max(8, w - (textX - x) - rightPad);
            graphics.fill(textX, progressY, textX + progressWidth, progressY + 2, 0xFFB89A6A);
            if (total > 0 && learned > 0) {
                int filled = Math.max(1, progressWidth * learned / total);
                graphics.fill(textX, progressY, textX + filled, progressY + 2, accent);
            }
        }
        if (w >= 90) {
            drawDiamond(graphics, x + w - (narrow ? 9 : 14), y + h / 2, hovered ? 4 : 3,
                    disabled ? 0xFF9A8C75 : hovered ? 0xFFE8BD67 : accent);
        }
    }

    private void drawNavigationHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY, AbilityLayout layout) {
        String backKey = showingSkillDetails ? "gui.refugio.abilities.back_skills"
                : showingSubcategories || !selectedTrack.branch().hasSubcategories()
                ? "gui.refugio.abilities.back_categories" : "gui.refugio.abilities.back_subcategories";
        Rect back = layout.backButton;
        drawButton(graphics, back.x, back.y, back.w, back.h,
                Component.translatable(backKey), mouseX, mouseY, true, 0xFF293A48);
        ItemStack iconStack;
        Component name;
        if (showingSkillDetails && selectedSkill() != null) {
            iconStack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(selectedSkill().icon())));
            name = skillNameComponent(selectedSkill()).withStyle(GOLD_STYLE);
        } else if (showingSubcategories) {
            RefugioAbilityData.Skill first = RefugioAbilityData.skills(currentBranch()).stream().findFirst().orElse(null);
            iconStack = first == null
                    ? new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:book")))
                    : new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(first.icon())));
            name = branchName(currentBranch()).withStyle(GOLD_STYLE);
        } else {
            iconStack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(selectedTrack.icon())));
            name = trackName(selectedTrack).withStyle(GOLD_STYLE);
        }
        int gap = layout.inner.w < 340 ? 5 : 10;
        int iconX = back.right() + gap;
        int iconY = back.y + (back.h - 16) / 2;
        graphics.item(iconStack, iconX, iconY);
        int textX = iconX + 19;
        int maxTextW = Math.max(24, layout.inner.right() - textX);
        List<FormattedCharSequence> lines = font.split(name, maxTextW);
        if (!lines.isEmpty()) {
            graphics.textRenderer().accept(textX, back.y + (back.h - font.lineHeight) / 2, lines.getFirst());
        }
    }

    private MutableComponent branchName(RefugioAbilityData.Branch branch) {
        return Component.translatable("gui.refugio.abilities.branch." + branch.id());
    }

    private MutableComponent trackName(RefugioAbilityData.Track track) {
        return Component.translatable("gui.refugio.abilities.track." + track.id());
    }

    private MutableComponent skillNameComponent(RefugioAbilityData.Skill skill) {
        if (skill == null) return Component.empty();
        return Component.translatableWithFallback("skill.refugio." + skill.id() + ".name", skill.name());
    }

    private MutableComponent skillDescriptionComponent(RefugioAbilityData.Skill skill) {
        if (skill == null) return Component.empty();
        String key = "skill.refugio." + skill.id() + ".desc";
        if ("burst".equals(skill.id())) {
            return Component.translatable(key, RefugioClient.burstKeyDisplayName());
        }
        if ("super_jump".equals(skill.id()) || "double_jump".equals(skill.id())) {
            return Component.translatable(key, RefugioClient.jumpKeyDisplayName());
        }
        return Component.translatableWithFallback(key, skill.description());
    }

    private void drawSkillPath(GuiGraphicsExtractor graphics, int mouseX, int mouseY, AbilityLayout layout) {
        Rect inner = layout.inner;
        graphics.fill(inner.x, layout.dividerY, inner.right(), layout.dividerY + 1, 0xFFAC936C);

        List<RefugioAbilityData.Skill> skills = currentTrackSkills();
        int firstNumber = skills.isEmpty() ? 0 : layout.pageStart + 1;
        int lastNumber = Math.min(skills.size(), layout.pageStart + layout.skillCards.size());

        Component pathLabel = Component.translatable("gui.refugio.abilities.path_label").withStyle(MUTED_STYLE);
        if (layout.compactPath) {
            Component compactPage = Component.translatable("gui.refugio.abilities.page_compact",
                    firstNumber, lastNumber, skills.size(), skillPageIndex + 1, layout.pageCount).withStyle(GOLD_STYLE);
            int pageW = font.width(compactPage);
            int pageX = layout.prevPageButton.x - pageW - 6;
            int maxPathLabelW = Math.max(24, pageX - inner.x - 8);
            List<FormattedCharSequence> labelLines = font.split(pathLabel, maxPathLabelW);
            if (!labelLines.isEmpty() && maxPathLabelW >= 40) {
                graphics.textRenderer().accept(inner.x + 2, layout.pathLabelY, labelLines.getFirst());
            }
            if (pageX >= inner.x) {
                text(graphics, compactPage, pageX, layout.pathLabelY);
            }
        } else {
            text(graphics, pathLabel, inner.x + 4, layout.pathLabelY);
            Component range = Component.translatable("gui.refugio.abilities.page_range",
                    firstNumber, lastNumber, skills.size()).withStyle(MUTED_STYLE);
            int rangeW = font.width(range);
            if (inner.right() - rangeW > inner.x + font.width(pathLabel) + 12) {
                text(graphics, range, inner.right() - rangeW - 2, layout.pathLabelY);
            }
        }

        for (int i = 0; i < layout.skillCards.size(); i++) {
            int skillIndex = layout.pageStart + i;
            if (skillIndex >= skills.size()) break;
            drawSkillCard(graphics, mouseX, mouseY, skills.get(skillIndex), skillIndex, layout.skillCards.get(i));
        }

        Rect prev = layout.prevPageButton;
        Rect next = layout.nextPageButton;
        drawButton(graphics, prev.x, prev.y, prev.w, prev.h, Component.literal("‹"),
                mouseX, mouseY, skillPageIndex > 0, 0xFF293A48);
        drawButton(graphics, next.x, next.y, next.w, next.h, Component.literal("›"),
                mouseX, mouseY, skillPageIndex + 1 < layout.pageCount, 0xFF293A48);
        if (!layout.compactPath) {
            Component page = Component.translatable("gui.refugio.abilities.page_number",
                    skillPageIndex + 1, layout.pageCount).withStyle(GOLD_STYLE);
            int midX = (prev.right() + next.x) / 2;
            text(graphics, page, midX - font.width(page) / 2, prev.y + (prev.h - font.lineHeight) / 2);
        }
    }

    private void drawSkillCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                               RefugioAbilityData.Skill skill, int skillIndex, Rect card) {
        int cardX = card.x;
        int cardY = card.y;
        int cardW = card.w;
        int cardH = card.h;
        boolean selected = skill.id().equals(selectedSkillId);
        boolean unlocked = isUnlocked(skillIndex);
        int currentRank = rank(skill);
        int rankLimit = RefugioAbilityData.maxRank(skill.id());
        boolean owned = currentRank > 0;
        boolean maxRank = currentRank >= rankLimit;
        boolean active = owned && isActive(skill);
        boolean hovered = card.contains(mouseX, mouseY);
        int accent = ACCENTS[branchIndex % ACCENTS.length];

        // Visual separation of Locked vs Available vs Owned (Active / Inactive) vs Max Rank
        int bg;
        int stroke;
        int leftStripe;
        if (maxRank) {
            bg = hovered ? 0xFFFFF1CE : 0xFFF7E5B8;
            stroke = hovered ? 0xFFFFDF7A : 0xFFC88A28;
            leftStripe = 0xFFD4921A;
        } else if (owned) {
            bg = active
                    ? (hovered ? 0xFFDFEDD2 : 0xFFD5E5C6)
                    : (hovered ? 0xFFF2E3C2 : 0xFFE8D8B4);
            stroke = active
                    ? (hovered ? 0xFF5A8F49 : 0xFF3A682E)
                    : (hovered ? 0xFFD3A34F : accent);
            leftStripe = active ? 0xFF2D5B22 : accent;
        } else if (unlocked) {
            bg = hovered ? 0xFFF5E4C0 : 0xFFEAD8B2;
            stroke = hovered ? 0xFFD3A34F : 0xFF987348;
            leftStripe = accent;
        } else {
            bg = hovered ? 0xFFD4C8B4 : 0xFFCBC0AA;
            stroke = hovered ? 0xFF948773 : 0xFF7D7262;
            leftStripe = 0xFF756C5E;
        }

        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, bg);
        border(graphics, cardX, cardY, cardW, cardY + cardH - cardY, selected ? 0xFFD4A34F : stroke);
        if (owned) {
            int innerBorder = maxRank ? 0x99FFE08A : (active ? 0x887BA868 : 0x66C39A59);
            border(graphics, cardX + 1, cardY + 1, cardW - 2, cardH - 2, innerBorder);
        }
        graphics.fill(cardX + 2, cardY + 2, cardX + 6, cardY + cardH - 2, leftStripe);

        ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(skill.icon())));
        Component skillName = skillNameComponent(skill).withStyle(unlocked || owned ? INK_STYLE : MUTED_STYLE);

        MutableComponent footer;
        Style footerStyle;
        if (maxRank) {
            footer = Component.translatable("gui.refugio.abilities.skill_card.max_rank", currentRank, rankLimit);
            footerStyle = GOLD_STYLE;
        } else if (owned) {
            footer = Component.translatable("gui.refugio.abilities.skill_card.rank", currentRank, rankLimit);
            footerStyle = SUCCESS_STYLE;
        } else if (isFreeCandidate(skill)) {
            footer = Component.translatable("gui.refugio.abilities.skill_card.free");
            footerStyle = GOLD_STYLE;
        } else if (!unlocked) {
            footer = Component.translatable("gui.refugio.abilities.skill_card.locked");
            footerStyle = MUTED_STYLE;
        } else {
            footer = Component.translatable("gui.refugio.abilities.skill_card.price", RefugioAbilityData.costForRank(skill.id(), 1));
            footerStyle = GOLD_STYLE;
        }

        if (cardH < 52) {
            int iconX = cardX + 8;
            int iconY = cardY + Math.max(0, (cardH - 16) / 2);
            graphics.item(icon, iconX, iconY);
            if (owned) {
                drawOwnedCheckSeal(graphics, iconX + 10, iconY + 10, maxRank);
            }
            int textX = iconX + 19;
            int textW = Math.max(16, cardW - (textX - cardX) - 5);
            List<FormattedCharSequence> nameLines = font.split(skillName, textW);
            MutableComponent compactStatus = owned
                    ? footer.copy().append(Component.literal(" • "))
                    .append(Component.translatable(active
                            ? "gui.refugio.abilities.state_active_short"
                            : "gui.refugio.abilities.state_inactive_short"))
                    : footer;
            List<FormattedCharSequence> footerLines = font.split(compactStatus.withStyle(footerStyle), textW);
            if (cardH >= font.lineHeight * 2 + 3) {
                int blockH = font.lineHeight * 2 + 1;
                int startY = cardY + Math.max(1, (cardH - blockH) / 2);
                if (!nameLines.isEmpty()) {
                    graphics.textRenderer().accept(textX, startY, nameLines.getFirst());
                }
                if (!footerLines.isEmpty()) {
                    graphics.textRenderer().accept(textX, startY + font.lineHeight + 1, footerLines.getFirst());
                }
            } else if (!nameLines.isEmpty()) {
                graphics.textRenderer().accept(textX, cardY + Math.max(1, (cardH - font.lineHeight) / 2),
                        nameLines.getFirst());
            }
            return;
        }

        // Top-right activation / availability pill badge
        drawCardStatePill(graphics, cardX + cardW - 3, cardY + 3, cardW - 10, owned, maxRank, active, unlocked, isFreeCandidate(skill));

        int footerPadBottom = cardH < 68 ? 3 : 5;
        int footerY = cardY + cardH - font.lineHeight - footerPadBottom;
        int rankBarH = cardH >= 64 ? 4 : 3;
        int rankBarY = footerY - rankBarH - (cardH >= 74 ? 3 : 2);
        drawCardRankSegments(graphics, cardX + 9, rankBarY, Math.max(16, cardW - 16), rankBarH,
                currentRank, rankLimit, maxRank ? 0xFFD4921A : (active ? 0xFF2D5B22 : accent), unlocked || owned);

        boolean showMedallion = cardH >= 78 && cardW >= 68;
        int iconY = cardY + (cardH >= 96 ? 14 : (cardH >= 74 ? 11 : 6));
        int iconCenterX = cardX + cardW / 2;
        if (showMedallion) {
            int boxSize = 22;
            int boxX = iconCenterX - boxSize / 2;
            int boxY = iconY - 3;
            int medBg = maxRank ? 0x44D4921A : owned ? 0x443A682E : 0x38987348;
            int medBorder = maxRank ? 0xCCD4921A : owned ? 0xAA3A682E : 0x88987348;
            graphics.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, medBg);
            border(graphics, boxX, boxY, boxSize, boxSize, medBorder);
        }
        graphics.item(icon, iconCenterX - 8, iconY);
        if (owned) {
            drawOwnedCheckSeal(graphics, iconCenterX + 3, iconY + 10, maxRank);
        }

        int nameAreaTop = iconY + (showMedallion ? 21 : 17);
        int nameAreaBottom = rankBarY - 2;
        int availNameH = Math.max(font.lineHeight, nameAreaBottom - nameAreaTop);
        List<FormattedCharSequence> nameLines = font.split(skillName, Math.max(12, cardW - 12));
        int maxTitleLines = availNameH >= font.lineHeight * 2 ? 2 : 1;
        int titleLineCount = Math.min(maxTitleLines, nameLines.size());
        int titleBlockH = titleLineCount * font.lineHeight;
        int titleY = nameAreaTop + Math.max(0, (availNameH - titleBlockH) / 2);
        for (int line = 0; line < titleLineCount; line++) {
            FormattedCharSequence sequence = nameLines.get(line);
            graphics.textRenderer().accept(cardX + (cardW - font.width(sequence)) / 2,
                    titleY + line * font.lineHeight, sequence);
        }

        List<FormattedCharSequence> footerLines = font.split(footer.withStyle(footerStyle), Math.max(12, cardW - 10));
        if (!footerLines.isEmpty()) {
            FormattedCharSequence footerLine = footerLines.getFirst();
            graphics.textRenderer().accept(cardX + (cardW - font.width(footerLine)) / 2, footerY, footerLine);
        }
    }

    private void drawCardStatePill(GuiGraphicsExtractor graphics, int rightX, int topY, int maxW,
                                   boolean owned, boolean maxRank, boolean active, boolean unlocked, boolean free) {
        if (maxW < 36) return;
        Component badgeText;
        int fill;
        int stroke;
        int textColor;
        if (owned) {
            badgeText = Component.translatable(active
                    ? "gui.refugio.abilities.card.active_badge"
                    : "gui.refugio.abilities.card.inactive_badge");
            fill = active ? 0xFF2D5B22 : 0xFF6B5035;
            stroke = active ? 0xFFB89A52 : 0xFFA88964;
            textColor = active ? 0xFFEFFAE6 : 0xFFF2E2C4;
        } else if (free) {
            badgeText = Component.translatable("gui.refugio.abilities.skill_card.free");
            fill = 0xFF24504F;
            stroke = 0xFFFFD878;
            textColor = 0xFFFFF0B8;
        } else if (unlocked) {
            badgeText = Component.translatable("gui.refugio.abilities.state_unlocked_short");
            fill = 0xFF6B4C2B;
            stroke = 0xFFB89455;
            textColor = 0xFFF5E6C8;
        } else {
            badgeText = Component.translatable("gui.refugio.abilities.state_locked_short");
            fill = 0xFF5C5246;
            stroke = 0xFF8A7E6E;
            textColor = 0xFFD2C5B0;
        }

        Style pillStyle = Style.EMPTY.withFont(UI_FONT).withColor(textColor);
        List<FormattedCharSequence> lines = font.split(Component.literal(badgeText.getString()).withStyle(pillStyle),
                Math.max(20, maxW - 6));
        if (lines.isEmpty()) return;
        FormattedCharSequence seq = lines.getFirst();
        int pillW = font.width(seq) + 6;
        int pillH = font.lineHeight + 1;
        int px = rightX - pillW;
        graphics.fill(px, topY, px + pillW, topY + pillH, fill);
        border(graphics, px, topY, pillW, pillH, stroke);
        graphics.textRenderer().accept(px + 3, topY + 1, seq);
    }

    private static void drawCardRankSegments(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                                             int currentRank, int rankLimit, int fillColor, boolean unlocked) {
        int trackBg = unlocked ? 0xFF9C7E54 : 0xFF8A7E6E;
        graphics.fill(x, y, x + w, y + h, trackBg);
        for (int segment = 0; segment < rankLimit; segment++) {
            int segLeft = x + segment * w / rankLimit;
            int segRight = x + (segment + 1) * w / rankLimit;
            int innerLeft = segLeft + 1;
            int innerRight = Math.max(innerLeft + 1, segRight - 1);
            int segColor = segment < currentRank ? fillColor : 0xFFD8C5A2;
            graphics.fill(innerLeft, y + 1, innerRight, y + h - 1, segColor);
        }
    }

    private static void drawOwnedCheckSeal(GuiGraphicsExtractor graphics, int x, int y, boolean maxRank) {
        int bg = maxRank ? 0xFFD4921A : 0xFF2D5B22;
        int stroke = maxRank ? 0xFFFFF0B8 : 0xFFE6C672;
        graphics.fill(x, y, x + 7, y + 7, bg);
        border(graphics, x, y, 7, 7, stroke);
        graphics.fill(x + 1, y + 3, x + 2, y + 4, 0xFFFFFFFF);
        graphics.fill(x + 2, y + 4, x + 3, y + 5, 0xFFFFFFFF);
        graphics.fill(x + 3, y + 3, x + 4, y + 4, 0xFFFFFFFF);
        graphics.fill(x + 4, y + 2, x + 5, y + 3, 0xFFFFFFFF);
        graphics.fill(x + 5, y + 1, x + 6, y + 2, 0xFFFFFFFF);
    }

    private void drawSkillDetailsPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, AbilityLayout layout) {
        Rect panel = layout.detailPanel;
        graphics.fill(panel.x, panel.y, panel.right(), panel.bottom(), 0xBCEEDCB8);
        border(graphics, panel.x, panel.y, panel.w, panel.h, 0xFF967043);
        RefugioAbilityData.Skill skill = selectedSkill();
        if (skill == null) return;

        int rank = rank(skill);
        int rankLimit = RefugioAbilityData.maxRank(skill.id());
        boolean owned = rank > 0;
        boolean maxRank = rank >= rankLimit;
        boolean active = owned && isActive(skill);
        boolean unlocked = isUnlocked(currentTrackSkills().indexOf(skill));
        boolean free = isFreeCandidate(skill);

        Component rankLabel = maxRank
                ? Component.translatable("gui.refugio.abilities.skill_card.max_rank", rank, rankLimit).withStyle(GOLD_STYLE)
                : Component.translatable("gui.refugio.abilities.rank", rank, rankLimit).withStyle(owned ? SUCCESS_STYLE : GOLD_STYLE);

        Component stateComp;
        Style stateStyle;
        if (owned) {
            stateComp = Component.translatable(active
                    ? "gui.refugio.abilities.detail_owned_active"
                    : "gui.refugio.abilities.detail_owned_inactive");
            stateStyle = active ? SUCCESS_STYLE : GOLD_STYLE;
        } else if (unlocked) {
            stateComp = Component.translatable("gui.refugio.abilities.state_unlocked_short");
            stateStyle = GOLD_STYLE;
        } else {
            stateComp = Component.translatable("gui.refugio.abilities.state_locked_short");
            stateStyle = MUTED_STYLE;
        }

        String costKey = maxRank ? "gui.refugio.abilities.cost_max_short"
                : !unlocked ? "gui.refugio.abilities.cost_locked_short"
                : free ? "gui.refugio.abilities.cost_free_short" : "gui.refugio.abilities.cost_next_short";
        MutableComponent cost = costKey.equals("gui.refugio.abilities.cost_next_short")
                ? Component.translatable(costKey, RefugioAbilityData.costForRank(skill.id(), rank + 1))
                : Component.translatable(costKey);

        Rect statusBox = layout.detailStatusBox;
        Rect rankBar = layout.detailRankBar;

        if (layout.compactDetails) {
            graphics.fill(statusBox.x, statusBox.y, statusBox.right(), statusBox.bottom(), 0x44A9844D);
            border(graphics, statusBox.x, statusBox.y, statusBox.w, statusBox.h, 0xFFB18A54);
            int columnGap = 6;
            int columnWidth = Math.max(12, (statusBox.w - columnGap * 2 - 8) / 3);
            int textY = statusBox.y + Math.max(1, (statusBox.h - font.lineHeight) / 2);
            List<FormattedCharSequence> rankLines = font.split(rankLabel, columnWidth);
            List<FormattedCharSequence> stateLines = font.split(stateComp.copy().withStyle(stateStyle), columnWidth);
            Component compactCost = costKey.equals("gui.refugio.abilities.cost_next_short")
                    ? Component.translatable("gui.refugio.abilities.cost_next_compact",
                    RefugioAbilityData.costForRank(skill.id(), rank + 1)).withStyle(GOLD_STYLE)
                    : cost.withStyle(GOLD_STYLE);
            List<FormattedCharSequence> costLines = font.split(compactCost, columnWidth);
            if (!rankLines.isEmpty()) {
                graphics.textRenderer().accept(statusBox.x + 4, textY, rankLines.getFirst());
            }
            if (!stateLines.isEmpty()) {
                graphics.textRenderer().accept(statusBox.x + 4 + columnWidth + columnGap, textY, stateLines.getFirst());
            }
            if (!costLines.isEmpty()) {
                graphics.textRenderer().accept(statusBox.x + 4 + (columnWidth + columnGap) * 2, textY, costLines.getFirst());
            }
            if (layout.showFeedback && layout.detailFeedbackY > layout.detailDescriptionBox.bottom()) {
                List<FormattedCharSequence> feedback = font.split(
                        Component.translatable(feedbackKey).withStyle(SUCCESS_STYLE), Math.max(16, statusBox.w - 8));
                if (!feedback.isEmpty()) {
                    graphics.textRenderer().accept(statusBox.x + 4, layout.detailFeedbackY, feedback.getFirst());
                }
            }
        } else {
            text(graphics, rankLabel, rankBar.x, rankBar.y - font.lineHeight - 2);
            graphics.fill(statusBox.x, statusBox.y, statusBox.right(), statusBox.bottom(), 0x44A9844D);
            border(graphics, statusBox.x, statusBox.y, statusBox.w, statusBox.h, 0xFFB18A54);
            int columnGap = 12;
            int columnWidth = Math.max(16, (statusBox.w - columnGap - 16) / 2);
            int textY = statusBox.y + 3;
            List<FormattedCharSequence> stateLines = font.split(stateComp.copy().withStyle(stateStyle), columnWidth);
            List<FormattedCharSequence> costLines = font.split(cost.withStyle(GOLD_STYLE), columnWidth);
            if (!stateLines.isEmpty()) {
                graphics.textRenderer().accept(statusBox.x + 8, textY, stateLines.getFirst());
            }
            if (!costLines.isEmpty()) {
                graphics.textRenderer().accept(statusBox.x + 8 + columnWidth + columnGap, textY, costLines.getFirst());
            }
            if (layout.showFeedback) {
                List<FormattedCharSequence> feedback = font.split(
                        Component.translatable(feedbackKey).withStyle(SUCCESS_STYLE), Math.max(16, statusBox.w - 16));
                if (!feedback.isEmpty()) {
                    graphics.textRenderer().accept(statusBox.x + 8, layout.detailFeedbackY, feedback.getFirst());
                }
            }
        }

        graphics.fill(rankBar.x, rankBar.y, rankBar.right(), rankBar.bottom(), 0xFFB89A6A);
        int fillBarColor = maxRank ? 0xFFD4921A : (active ? 0xFF2D5B22 : ACCENTS[branchIndex % ACCENTS.length]);
        for (int segment = 0; segment < rankLimit; segment++) {
            int segmentLeft = rankBar.x + segment * rankBar.w / rankLimit;
            int segmentRight = rankBar.x + (segment + 1) * rankBar.w / rankLimit;
            if (segment < rank) {
                graphics.fill(segmentLeft + 1, rankBar.y + 1,
                        Math.max(segmentLeft + 2, segmentRight - 1), rankBar.bottom() - 1,
                        fillBarColor);
            }
        }

        Rect descBox = layout.detailDescriptionBox;
        graphics.fill(descBox.x, descBox.y, descBox.right(), descBox.bottom(), 0x25FFF4D8);
        border(graphics, descBox.x, descBox.y, descBox.w, descBox.h, 0x75B18A54);
        int descPadY = descBox.h < 36 ? 2 : 6;
        int lineStep = layout.compactDetails ? font.lineHeight + 1 : font.lineHeight + 2;
        int maxScroll = maxDetailScroll(skill, layout);
        detailScrollOffset = Math.max(0, Math.min(maxScroll, detailScrollOffset));
        List<FormattedCharSequence> description = font.split(
                skillDescriptionComponent(skill).withStyle(INK_STYLE), Math.max(16, descBox.w - 16));
        graphics.enableScissor(descBox.x + 1, descBox.y + 1, descBox.right() - 1, descBox.bottom() - 1);
        int descriptionY = descBox.y + descPadY - detailScrollOffset;
        for (FormattedCharSequence line : description) {
            if (descriptionY + font.lineHeight >= descBox.y && descriptionY <= descBox.bottom()) {
                graphics.textRenderer().accept(descBox.x + 8, descriptionY, line);
            }
            descriptionY += lineStep;
        }
        graphics.disableScissor();
        if (maxScroll > 0 && descBox.h >= 16) {
            int trackTop = descBox.y + 2;
            int trackH = descBox.h - 4;
            int thumbH = Math.max(6, trackH * trackH / (trackH + maxScroll));
            int thumbTop = trackTop + (trackH - thumbH) * detailScrollOffset / maxScroll;
            int barX = descBox.right() - 4;
            graphics.fill(barX, trackTop, barX + 2, trackTop + trackH, 0x44765030);
            graphics.fill(barX, thumbTop, barX + 2, thumbTop + thumbH, 0xFF9B744A);
        }

        Rect buyBtn = layout.buyButton;
        Rect toggleBtn = layout.toggleButton;
        String buyKey = maxRank ? "gui.refugio.abilities.cost_max_short"
                : free ? "gui.refugio.abilities.claim_free"
                : rank == 0 ? "gui.refugio.abilities.buy" : "gui.refugio.abilities.upgrade";
        drawButton(graphics, buyBtn.x, buyBtn.y, buyBtn.w, buyBtn.h, Component.translatable(buyKey),
                mouseX, mouseY, canPurchase(skill), 0xFF24504F);
        Component toggleLabel = owned
                ? Component.translatable(active ? "gui.refugio.abilities.disable_on" : "gui.refugio.abilities.enable_off")
                : Component.translatable("gui.refugio.abilities.enable");
        drawButton(graphics, toggleBtn.x, toggleBtn.y, toggleBtn.w, toggleBtn.h,
                toggleLabel, mouseX, mouseY, owned, active ? 0xFF24504F : 0xFF293A48);
    }

    private int maxDetailScroll(RefugioAbilityData.Skill skill, AbilityLayout layout) {
        Rect descBox = layout.detailDescriptionBox;
        int descPadY = descBox.h < 36 ? 2 : 6;
        int lineStep = layout.compactDetails ? font.lineHeight + 1 : font.lineHeight + 2;
        List<FormattedCharSequence> lines = font.split(
                skillDescriptionComponent(skill).withStyle(INK_STYLE), Math.max(16, descBox.w - 16));
        int contentH = lines.size() * lineStep;
        int viewportH = Math.max(1, descBox.h - descPadY * 2);
        return Math.max(0, contentH - viewportH);
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

    private void selectFirstSkill() {
        List<RefugioAbilityData.Skill> skills = currentTrackSkills();
        if (!skills.isEmpty()) selectedSkillId = skills.getFirst().id();
    }

    private RefugioAbilityData.Branch currentBranch() {
        RefugioAbilityData.Branch[] branches = RefugioAbilityData.Branch.values();
        if (branchIndex < 0 || branchIndex >= branches.length) branchIndex = 0;
        return branches[branchIndex];
    }

    private List<RefugioAbilityData.Skill> currentTrackSkills() {
        if (selectedTrack == null) selectedTrack = RefugioAbilityData.Track.SWORD;
        return RefugioAbilityData.skills(selectedTrack);
    }

    private RefugioAbilityData.Skill selectedSkill() {
        return RefugioAbilityData.skill(selectedSkillId);
    }

    private int selectedRank() {
        return rank(selectedSkill());
    }

    private int rank(RefugioAbilityData.Skill skill) {
        int index = RefugioAbilityData.skills().indexOf(skill);
        return skill == null || index < 0 || index >= ranks.length ? 0 : ranks[index];
    }

    private boolean isActive(RefugioAbilityData.Skill skill) {
        int index = RefugioAbilityData.skills().indexOf(skill);
        return index >= 0 && index < Integer.SIZE && (activeMask & (1 << index)) != 0;
    }

    private boolean isUnlocked(int branchNodeIndex) {
        List<RefugioAbilityData.Skill> skills = currentTrackSkills();
        return branchNodeIndex <= 0 || branchNodeIndex >= skills.size()
                || rank(skills.get(branchNodeIndex - 1)) > 0;
    }

    private boolean isFreeCandidate(RefugioAbilityData.Skill skill) {
        List<RefugioAbilityData.Skill> track = RefugioAbilityData.skills(skill.track());
        return freeChoice.isEmpty() && rank(skill) == 0 && !track.isEmpty()
                && track.getFirst().id().equals(skill.id());
    }

    private boolean canPurchase(RefugioAbilityData.Skill skill) {
        if (skill == null || rank(skill) >= RefugioAbilityData.maxRank(skill.id())) return false;
        int index = currentTrackSkills().indexOf(skill);
        if (!isUnlocked(index)) return false;
        if (rank(skill) == 0 && freeChoice.isEmpty() && !isFreeCandidate(skill)) return false;
        if (isFreeCandidate(skill)) return true;
        return coins >= RefugioAbilityData.costForRank(skill.id(), rank(skill) + 1);
    }

    private void closeScreen() {
        ClientPlayNetworking.send(new RefugioUiPayload("ABILITY_CLOSE"));
        RefugioClient.clearAbilityScreen(this);
        minecraft.setScreenAndShow(null);
    }

    private void text(GuiGraphicsExtractor graphics, Component component, int x, int y) {
        graphics.textRenderer().accept(x, y, component);
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

    private record AbilityLayout(
            Rect panel, Rect inner, Rect titleBanner, Rect balanceBadge, Rect closeButton,
            boolean compactBalance, boolean showKicker, int promptY, List<Rect> categoryCards,
            Rect backButton, int subPromptY, List<Rect> trackCards,
            int dividerY, int pathLabelY, boolean compactPath, int pageStart, int pageCount,
            Rect prevPageButton, Rect nextPageButton, Rect skillGridArea, List<Rect> skillCards,
            Rect detailPanel, boolean compactDetails, boolean showFeedback,
            Rect detailStatusBox, Rect detailRankBar, Rect detailDescriptionBox, int detailFeedbackY,
            Rect buyButton, Rect toggleButton
    ) { }
}
