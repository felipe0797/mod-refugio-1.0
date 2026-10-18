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
    private static final int MAX_RANK = 5;
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
    private static final Style SUCCESS_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFF456B3A);
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
            for (int i = 0; i < values.length; i++) ranks[i] = Math.max(0, Math.min(MAX_RANK, Integer.parseInt(values[i])));
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
        int px = panelX();
        int py = panelY();
        int pw = panelWidth();
        int ph = panelHeight();
        drawParchmentAndWoodFrame(graphics, px, py, pw, ph);
        drawHeader(graphics, mouseX, mouseY, px, py, pw);
        if (showingCategories) {
            drawCategoryOverview(graphics, mouseX, mouseY, px, py, pw, ph);
        } else if (showingSubcategories) {
            drawNavigationHeader(graphics, mouseX, mouseY, px, py, pw);
            drawSubcategoryOverview(graphics, mouseX, mouseY, px, py, pw, ph);
        } else if (showingSkillDetails) {
            drawNavigationHeader(graphics, mouseX, mouseY, px, py, pw);
            drawSkillDetailsPage(graphics, mouseX, mouseY, px, py, pw, ph);
        } else {
            drawNavigationHeader(graphics, mouseX, mouseY, px, py, pw);
            drawSkillPath(graphics, mouseX, mouseY, px, py, pw, ph);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        double mx = event.x();
        double my = event.y();
        int px = panelX();
        int py = panelY();
        int pw = panelWidth();
        int ph = panelHeight();
        if (inside(mx, my, closeButtonX(px, pw), py + 24, 22, 22)) {
            closeScreen();
            return true;
        }
        if (showingCategories) {
            for (int i = 0; i < RefugioAbilityData.Branch.values().length; i++) {
                CategoryCardBounds card = categoryCardBounds(i);
                if (!inside(mx, my, card.x(), card.y(), card.width(), card.height())) continue;
                branchIndex = i;
                RefugioAbilityData.Branch branch = currentBranch();
                skillPageIndex = 0;
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
        if (inside(mx, my, px + 18, py + 62, 150, 26)) {
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
            feedbackKey = "";
            return true;
        }
        if (showingSubcategories) {
            List<RefugioAbilityData.Track> tracks = RefugioAbilityData.tracks(currentBranch());
            for (int i = 0; i < tracks.size(); i++) {
                RefugioAbilityData.Track track = tracks.get(i);
                CategoryCardBounds card = trackCardBounds(i, tracks.size(), px, py, pw, ph);
                if (!inside(mx, my, card.x(), card.y(), card.width(), card.height())
                        || RefugioAbilityData.skills(track).isEmpty()) continue;
                selectedTrack = track;
                List<RefugioAbilityData.Skill> trackSkills = currentTrackSkills();
                if (!trackSkills.isEmpty()) selectedSkillId = trackSkills.getFirst().id();
                skillPageIndex = 0;
                feedbackKey = "";
                showingSubcategories = false;
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (showingSkillDetails) {
            int detailX = px + 26;
            int detailWidth = pw - 52;
            int gap = 12;
            int buttonWidth = Math.max(1, (detailWidth - 36 - gap) / 2);
            int buttonY = skillDetailButtonsY(py, ph);
            int leftButtonX = detailX + 18;
            int rightButtonX = leftButtonX + buttonWidth + gap;
            if (inside(mx, my, leftButtonX, buttonY, buttonWidth, 30) && canPurchase(selectedSkill())) {
                ClientPlayNetworking.send(new RefugioUiPayload("BUY\t" + selectedSkillId));
                feedbackKey = "gui.refugio.abilities.feedback.processing";
                return true;
            }
            if (inside(mx, my, rightButtonX, buttonY, buttonWidth, 30) && selectedRank() > 0) {
                ClientPlayNetworking.send(new RefugioUiPayload("TOGGLE\t" + selectedSkillId));
                feedbackKey = "gui.refugio.abilities.feedback.processing";
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        int controlsY = skillPageControlsY(py, ph);
        int previousX = skillPreviousButtonX(px, pw);
        int nextX = skillNextButtonX(px, pw);
        if (inside(mx, my, previousX, controlsY, skillPageButtonWidth(ph), skillPageButtonHeight(ph))) {
            skillPageIndex = Math.max(0, skillPageIndex - 1);
            return true;
        }
        if (inside(mx, my, nextX, controlsY, skillPageButtonWidth(ph), skillPageButtonHeight(ph))) {
            skillPageIndex = Math.min(skillPageCount(pw) - 1, skillPageIndex + 1);
            return true;
        }
        int pageStart = skillPageStart(pw);
        int pageEnd = Math.min(currentTrackSkills().size(), pageStart + skillsPerPage(pw));
        for (int index = pageStart; index < pageEnd; index++) {
            SkillCardBounds card = skillPageCardBounds(index, px, py, pw, ph);
            if (inside(mx, my, card.x(), card.y(), card.width(), card.height())) {
                selectedSkillId = currentTrackSkills().get(index).id();
                feedbackKey = "";
                showingSkillDetails = true;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!showingCategories && !showingSubcategories && !showingSkillDetails && verticalAmount != 0.0) {
            int py = panelY();
            int ph = panelHeight();
            int gridTop = skillPageGridTop(py, ph);
            if (inside(mouseX, mouseY, panelX() + 18, gridTop, panelWidth() - 36,
                    Math.max(1, skillPageGridBottom(py, ph) - gridTop))) {
                skillPageIndex = Math.max(0, Math.min(skillPageCount(panelWidth()) - 1,
                        skillPageIndex - (int) Math.signum(verticalAmount)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    private void drawParchmentAndWoodFrame(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        int insetX = Math.max(1, (int) Math.round(w * 0.055));
        int insetY = Math.max(1, (int) Math.round(h * 0.085));
        blitFull(graphics, PARCHMENT_TEXTURE, x + insetX, y + insetY,
                Math.max(1, w - insetX * 2), Math.max(1, h - insetY * 2));
        blitFull(graphics, WOOD_FRAME_TEXTURE, x, y, w, h);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int px, int py, int pw) {
        Component title = Component.translatable("gui.refugio.abilities.title").withStyle(LIGHT_STYLE);
        boolean compact = pw < 600;
        int badgeWidth = compact ? 96 : 138;
        int closeX = closeButtonX(px, pw);
        int badgeRightInset = compact ? 88 : 94;
        int badgeX = px + pw - badgeWidth - badgeRightInset;
        int bannerWidth = compact ? Math.max(120, Math.min(148, pw - badgeWidth - 78))
                : Math.min(420, Math.max(190, Math.min(pw - 280, font.width(title) + 56)));
        int bannerX = compact ? px + 18 : px + (pw - bannerWidth) / 2;
        int bannerY = py + 20;
        int bannerHeight = 36;
        blitFull(graphics, TITLE_PLAQUE_TEXTURE, bannerX, bannerY, bannerWidth, bannerHeight);
        List<FormattedCharSequence> titleLines = font.split(title, Math.max(8, bannerWidth - 32));
        int titleCount = Math.min(2, titleLines.size());
        int titleY = bannerY + (bannerHeight - titleCount * font.lineHeight) / 2;
        for (int i = 0; i < titleCount; i++) {
            FormattedCharSequence line = titleLines.get(i);
            graphics.textRenderer().accept(bannerX + (bannerWidth - font.width(line)) / 2,
                    titleY + i * font.lineHeight, line);
        }
        if (!compact) {
            text(graphics, Component.translatable("gui.refugio.abilities.kicker").withStyle(MUTED_STYLE), px + 20, py + 35);
            drawDiamond(graphics, bannerX - 11, bannerY + bannerHeight / 2, 3, 0xFFB89455);
            drawDiamond(graphics, bannerX + bannerWidth + 11, bannerY + bannerHeight / 2, 3, 0xFFB89455);
        }
        drawBalanceBadge(graphics, badgeX, py + 24, badgeWidth, 30, compact);
        drawClose(graphics, mouseX, mouseY, closeX, py + 24);
    }

    private void drawBalanceBadge(GuiGraphicsExtractor graphics, int x, int y, int w, int h, boolean compact) {
        graphics.fill(x, y, x + w, y + h, 0xFF56361F);
        border(graphics, x, y, w, h, 0xFFC39A59);
        graphics.fill(x + 2, y + 2, x + w - 2, y + 4, 0xFF79502A);
        drawCoinIcon(graphics, x + 8, y + 9);
        MutableComponent balance = compact ? Component.literal(Integer.toString(coins))
                : Component.translatable("gui.refugio.abilities.balance", coins);
        List<FormattedCharSequence> lines = font.split(balance.withStyle(LIGHT_STYLE), Math.max(8, w - 34));
        if (!lines.isEmpty()) {
            FormattedCharSequence line = lines.getFirst();
            graphics.textRenderer().accept(x + 29, y + (h - font.lineHeight) / 2, line);
        }
    }

    private static void drawCoinIcon(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x + 4, y, x + 10, y + 2, 0xFFFFE79A);
        graphics.fill(x + 2, y + 2, x + 12, y + 10, 0xFFD99B36);
        graphics.fill(x + 4, y + 10, x + 10, y + 12, 0xFF9C6424);
        graphics.fill(x + 4, y + 3, x + 8, y + 8, 0xFFFFD76A);
        graphics.fill(x + 8, y + 5, x + 10, y + 8, 0xFFF2B847);
    }

    private void drawCategoryOverview(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                      int px, int py, int pw, int ph) {
        Component prompt = Component.translatable("gui.refugio.abilities.choose_branch").withStyle(MUTED_STYLE);
        text(graphics, prompt, px + (pw - font.width(prompt)) / 2, py + 78);
        drawDiamond(graphics, px + pw / 2 - font.width(prompt) / 2 - 12, py + 82, 3, 0xFFB89455);
        drawDiamond(graphics, px + pw / 2 + font.width(prompt) / 2 + 12, py + 82, 3, 0xFFB89455);
        for (int i = 0; i < RefugioAbilityData.Branch.values().length; i++) {
            drawCategoryCard(graphics, mouseX, mouseY, i);
        }
    }

    private void drawCategoryCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int index) {
        CategoryCardBounds card = categoryCardBounds(index);
        int x = card.x();
        int y = card.y();
        int w = card.width();
        int h = card.height();
        boolean compact = panelHeight() < 260;
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        int accent = ACCENTS[index];
        graphics.fill(x, y, x + w, y + h, hovered ? 0xFFF5E4C0 : 0xFFEAD8B2);
        border(graphics, x, y, w, h, hovered ? 0xFFD3A34F : 0xFF987348);
        graphics.fill(x + 3, y + 5, x + 7, y + h - 5, accent);

        RefugioAbilityData.Branch branch = RefugioAbilityData.Branch.values()[index];
        List<RefugioAbilityData.Skill> skills = RefugioAbilityData.skills(branch);
        if (!skills.isEmpty()) {
            ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(skills.getFirst().icon())));
            graphics.item(icon, x + 17, y + (h - 16) / 2);
        }
        int textX = x + 48;
        int textWidth = Math.max(8, w - 76);
        List<FormattedCharSequence> nameLines = font.split(branchName(branch).withStyle(INK_STYLE), textWidth);
        int titleLines = Math.min(compact ? 1 : 2, nameLines.size());
        int titleY = y + (compact ? 4 : 10);
        for (int line = 0; line < titleLines; line++) {
            graphics.textRenderer().accept(textX, titleY + line * font.lineHeight, nameLines.get(line));
        }
        Component count = Component.translatable("gui.refugio.abilities.branch_skill_count", skills.size())
                .withStyle(MUTED_STYLE);
        text(graphics, count, textX, titleY + titleLines * font.lineHeight + (compact ? 2 : 3));

        if (!compact && h >= 44) {
            int learned = 0;
            for (RefugioAbilityData.Skill skill : skills) if (rank(skill) > 0) learned++;
            int progressX = textX;
            int progressY = y + h - 6;
            int progressWidth = Math.max(1, w - 66);
            graphics.fill(progressX, progressY, progressX + progressWidth, progressY + 2, 0xFFB89A6A);
            if (!skills.isEmpty() && learned > 0) {
                int filled = Math.max(1, progressWidth * learned / skills.size());
                graphics.fill(progressX, progressY, progressX + filled, progressY + 2, accent);
            }
        }
        drawDiamond(graphics, x + w - 15, y + h / 2, hovered ? 4 : 3, hovered ? 0xFFE8BD67 : accent);
    }

    private void drawSubcategoryOverview(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                         int px, int py, int pw, int ph) {
        Component prompt = Component.translatable("gui.refugio.abilities.choose_track").withStyle(MUTED_STYLE);
        text(graphics, prompt, px + (pw - font.width(prompt)) / 2, py + 78);
        drawDiamond(graphics, px + pw / 2 - font.width(prompt) / 2 - 12, py + 82, 3, 0xFFB89455);
        drawDiamond(graphics, px + pw / 2 + font.width(prompt) / 2 + 12, py + 82, 3, 0xFFB89455);
        List<RefugioAbilityData.Track> tracks = RefugioAbilityData.tracks(currentBranch());
        for (int i = 0; i < tracks.size(); i++) drawTrackCard(graphics, mouseX, mouseY, tracks.get(i), i, tracks.size(), px, py, pw, ph);
    }

    private void drawTrackCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                               RefugioAbilityData.Track track, int index, int count,
                               int px, int py, int pw, int ph) {
        CategoryCardBounds card = trackCardBounds(index, count, px, py, pw, ph);
        int x = card.x();
        int y = card.y();
        int w = card.width();
        int h = card.height();
        List<RefugioAbilityData.Skill> skills = RefugioAbilityData.skills(track);
        boolean empty = skills.isEmpty();
        boolean hovered = !empty && inside(mouseX, mouseY, x, y, w, h);
        int accent = empty ? 0xFF8A8274 : ACCENTS[branchIndex];
        graphics.fill(x, y, x + w, y + h, empty ? 0xFFD4C7AD : hovered ? 0xFFF5E4C0 : 0xFFEAD8B2);
        border(graphics, x, y, w, h, empty ? 0xFF8E806A : hovered ? 0xFFD3A34F : 0xFF987348);
        graphics.fill(x + 3, y + 5, x + 7, y + h - 5, accent);
        ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(track.icon())));
        graphics.item(icon, x + 17, y + (h - 16) / 2);

        int textX = x + 48;
        int textWidth = Math.max(8, w - 76);
        boolean compact = ph < 260;
        List<FormattedCharSequence> nameLines = font.split(trackName(track).withStyle(empty ? MUTED_STYLE : INK_STYLE), textWidth);
        int titleLines = Math.min(compact ? 1 : 2, nameLines.size());
        int titleY = y + (compact ? 4 : 10);
        for (int line = 0; line < titleLines; line++) {
            graphics.textRenderer().accept(textX, titleY + line * font.lineHeight, nameLines.get(line));
        }
        MutableComponent countLabel = empty ? Component.translatable("gui.refugio.abilities.track.coming_soon")
                : Component.translatable("gui.refugio.abilities.branch_skill_count", skills.size());
        text(graphics, countLabel.withStyle(MUTED_STYLE), textX, titleY + titleLines * font.lineHeight + (compact ? 2 : 3));

        if (!empty && !compact && h >= 44) {
            int learned = 0;
            for (RefugioAbilityData.Skill skill : skills) if (rank(skill) > 0) learned++;
            int progressX = textX;
            int progressY = y + h - 6;
            int progressWidth = Math.max(1, w - 66);
            graphics.fill(progressX, progressY, progressX + progressWidth, progressY + 2, 0xFFB89A6A);
            if (learned > 0) graphics.fill(progressX, progressY,
                    progressX + Math.max(1, progressWidth * learned / skills.size()), progressY + 2, accent);
        }
        drawDiamond(graphics, x + w - 15, y + h / 2, hovered ? 4 : 3,
                empty ? 0xFF9A8C75 : hovered ? 0xFFE8BD67 : accent);
    }

    private void drawNavigationHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                      int px, int py, int pw) {
        String backKey = showingSkillDetails ? "gui.refugio.abilities.back_skills"
                : showingSubcategories || !selectedTrack.branch().hasSubcategories()
                ? "gui.refugio.abilities.back_categories" : "gui.refugio.abilities.back_subcategories";
        drawButton(graphics, px + 18, py + 62, 150, 26,
                Component.translatable(backKey), mouseX, mouseY, true, 0xFF293A48);
        ItemStack iconStack;
        Component name;
        if (showingSkillDetails && selectedSkill() != null) {
            iconStack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(selectedSkill().icon())));
            name = Component.literal(selectedSkill().name()).withStyle(GOLD_STYLE);
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
        graphics.item(iconStack, px + 184, py + 67);
        text(graphics, name, px + 208, py + 70);
    }

    private MutableComponent branchName(RefugioAbilityData.Branch branch) {
        return Component.translatable("gui.refugio.abilities.branch." + branch.id());
    }

    private MutableComponent trackName(RefugioAbilityData.Track track) {
        return Component.translatable("gui.refugio.abilities.track." + track.id());
    }

    private CategoryCardBounds categoryCardBounds(int index) {
        int px = panelX();
        int py = panelY();
        int pw = panelWidth();
        int top = py + 96;
        int bottom = panelContentBottom(py, panelHeight()) - 8;
        int gapX = 14;
        int gapY = 10;
        int cardWidth = Math.max(1, (pw - 56 - gapX) / 2);
        int cardHeight = Math.max(1, (bottom - top - gapY) / 2);
        return new CategoryCardBounds(px + 28 + (index % 2) * (cardWidth + gapX),
                top + (index / 2) * (cardHeight + gapY), cardWidth, cardHeight);
    }

    private CategoryCardBounds trackCardBounds(int index, int count, int px, int py, int pw, int ph) {
        int rows = Math.max(1, (count + 1) / 2);
        int top = py + 96;
        int bottom = panelContentBottom(py, ph) - 8;
        int gapX = 14;
        int gapY = 10;
        int cardWidth = Math.max(1, (pw - 56 - gapX) / 2);
        int availableHeight = Math.max(1, bottom - top);
        int cardHeight = Math.max(1, (availableHeight - gapY) / 2);
        int gridHeight = rows * cardHeight + gapY * (rows - 1);
        int gridTop = top + Math.max(0, (availableHeight - gridHeight) / 2);
        int row = index / 2;
        int column = index % 2;
        int rowCount = Math.min(2, count - row * 2);
        int rowWidth = rowCount * cardWidth + Math.max(0, rowCount - 1) * gapX;
        int rowLeft = px + (pw - rowWidth) / 2;
        return new CategoryCardBounds(rowLeft + column * (cardWidth + gapX),
                gridTop + row * (cardHeight + gapY), cardWidth, cardHeight);
    }

    private void drawSkillPath(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int px, int py, int pw, int ph) {
        boolean compact = isCompactLayout(ph);
        int x = px + 18;
        int w = pw - 36;
        int top = py + 94;
        graphics.fill(x, top, x + w, top + 1, 0xFFAC936C);
        Component pathLabel = Component.translatable("gui.refugio.abilities.path_label").withStyle(MUTED_STYLE);
        text(graphics, pathLabel, x + 12, compact ? top + 2 : top + 8);

        List<RefugioAbilityData.Skill> skills = currentTrackSkills();
        int pageStart = skillPageStart(pw);
        int pageEnd = Math.min(skills.size(), pageStart + skillsPerPage(pw));
        for (int i = pageStart; i < pageEnd; i++) {
            RefugioAbilityData.Skill skill = skills.get(i);
            SkillCardBounds card = skillPageCardBounds(i, px, py, pw, ph);
            int cardX = card.x();
            int cardY = card.y();
            int cardWidth = card.width();
            int cardHeight = card.height();
            boolean selected = skill.id().equals(selectedSkillId);
            boolean unlocked = isUnlocked(i);
            int currentRank = rank(skill);
            boolean hovered = inside(mouseX, mouseY, cardX, cardY, cardWidth, cardHeight);
            int bg = selected ? 0xFFF7E8C9 : hovered ? 0xFFF1DFBC : unlocked ? 0xFFEAD8B2 : 0xFFDCC9A4;
            int stroke = currentRank > 0 ? ACCENTS[branchIndex] : unlocked ? 0xFF9A7849 : 0xFFB19A70;
            graphics.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, bg);
            border(graphics, cardX, cardY, cardWidth, cardHeight, selected ? 0xFFD4A34F : hovered ? 0xFFD3A34F : stroke);
            graphics.fill(cardX + 3, cardY + 5, cardX + 6, cardY + cardHeight - 5, ACCENTS[branchIndex]);
            ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(skill.icon())));
            graphics.item(icon, cardX + (cardWidth - 16) / 2, cardY + (compact ? 2 : 6));

            Component skillName = Component.literal(skill.name()).withStyle(unlocked ? INK_STYLE : MUTED_STYLE);
            List<FormattedCharSequence> nameLines = font.split(skillName, Math.max(8, cardWidth - 18));
            int titleLineCount = Math.min(cardHeight < 59 ? 1 : 2, nameLines.size());
            int titleY = cardY + (compact ? 20 : 24);
            for (int line = 0; line < titleLineCount; line++) {
                FormattedCharSequence sequence = nameLines.get(line);
                graphics.textRenderer().accept(cardX + (cardWidth - font.width(sequence)) / 2,
                        titleY + line * font.lineHeight, sequence);
            }

            MutableComponent footer = currentRank > 0
                    ? Component.translatable("gui.refugio.abilities.skill_card.rank", currentRank, MAX_RANK)
                    : isFreeCandidate(skill) ? Component.translatable("gui.refugio.abilities.skill_card.free")
                    : !unlocked ? Component.translatable("gui.refugio.abilities.skill_card.locked")
                    : Component.translatable("gui.refugio.abilities.skill_card.price", RefugioAbilityData.costForRank(1));
            Style footerStyle = currentRank > 0 ? SUCCESS_STYLE : !unlocked ? MUTED_STYLE : GOLD_STYLE;
            List<FormattedCharSequence> footerLines = font.split(footer.withStyle(footerStyle), Math.max(8, cardWidth - 14));
            if (!footerLines.isEmpty()) {
                FormattedCharSequence footerLine = footerLines.getFirst();
                graphics.textRenderer().accept(cardX + (cardWidth - font.width(footerLine)) / 2,
                        cardY + cardHeight - font.lineHeight - (compact ? 3 : 5), footerLine);
            }
        }

        int controlsY = skillPageControlsY(py, ph);
        int previousX = skillPreviousButtonX(px, pw);
        int nextX = skillNextButtonX(px, pw);
        int pageCount = skillPageCount(pw);
        int buttonWidth = skillPageButtonWidth(ph);
        int buttonHeight = skillPageButtonHeight(ph);
        int firstNumber = pageStart + 1;
        int lastNumber = pageEnd;
        if (compact) {
            Component compactPage = Component.translatable("gui.refugio.abilities.page_compact",
                    firstNumber, lastNumber, skills.size(), skillPageIndex + 1, pageCount).withStyle(GOLD_STYLE);
            int pageX = previousX - font.width(compactPage) - 7;
            text(graphics, compactPage, pageX, controlsY + 5);
        } else {
            Component range = Component.translatable("gui.refugio.abilities.page_range",
                    firstNumber, lastNumber, skills.size()).withStyle(MUTED_STYLE);
            text(graphics, range, px + (pw - font.width(range)) / 2, controlsY - font.lineHeight - 5);
        }
        drawButton(graphics, previousX, controlsY, buttonWidth, buttonHeight, Component.literal("‹"),
                mouseX, mouseY, skillPageIndex > 0, 0xFF293A48);
        drawButton(graphics, nextX, controlsY, buttonWidth, buttonHeight, Component.literal("›"),
                mouseX, mouseY, skillPageIndex + 1 < pageCount, 0xFF293A48);
        if (!compact) {
            Component page = Component.translatable("gui.refugio.abilities.page_number", skillPageIndex + 1, pageCount)
                    .withStyle(GOLD_STYLE);
            text(graphics, page, px + (pw - font.width(page)) / 2, controlsY + 7);
        }
    }

    private void drawSkillDetailsPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                      int px, int py, int pw, int ph) {
        int x = px + 26;
        int w = pw - 52;
        boolean compact = isCompactLayout(ph);
        int top = py + (compact ? 92 : 96);
        int bottom = panelContentBottom(py, ph);
        graphics.fill(x, top, x + w, bottom, 0xBCEEDCB8);
        border(graphics, x, top, w, bottom - top, 0xFF967043);
        RefugioAbilityData.Skill skill = selectedSkill();
        if (skill == null) return;

        int rank = rank(skill);
        boolean unlocked = isUnlocked(currentTrackSkills().indexOf(skill));
        boolean free = isFreeCandidate(skill);
        int lineHeight = font.lineHeight;
        int innerX = x + 18;
        int innerWidth = w - 36;
        Component rankLabel = Component.translatable("gui.refugio.abilities.rank", rank, MAX_RANK)
                .withStyle(GOLD_STYLE);
        if (!compact) {
            text(graphics, rankLabel, innerX, top + 9);
            int barY = top + 25;
            int barWidth = innerWidth;
            graphics.fill(innerX, barY, innerX + barWidth, barY + 6, 0xFFB89A6A);
            for (int segment = 0; segment < MAX_RANK; segment++) {
                int segmentLeft = innerX + segment * barWidth / MAX_RANK;
                int segmentRight = innerX + (segment + 1) * barWidth / MAX_RANK;
                if (segment < rank) graphics.fill(segmentLeft + 1, barY + 1,
                        Math.max(segmentLeft + 2, segmentRight - 1), barY + 5, ACCENTS[branchIndex]);
            }
        }

        String stateKey = rank > 0 ? (isActive(skill) ? "gui.refugio.abilities.state_active_short"
                : "gui.refugio.abilities.state_inactive_short")
                : unlocked ? "gui.refugio.abilities.state_unlocked_short" : "gui.refugio.abilities.state_locked_short";
        Style stateStyle = rank > 0 ? (isActive(skill) ? SUCCESS_STYLE : MUTED_STYLE)
                : unlocked ? GOLD_STYLE : MUTED_STYLE;
        String costKey = rank >= MAX_RANK ? "gui.refugio.abilities.cost_max_short"
                : !unlocked ? "gui.refugio.abilities.cost_locked_short"
                : free ? "gui.refugio.abilities.cost_free_short" : "gui.refugio.abilities.cost_next_short";
        MutableComponent cost = costKey.equals("gui.refugio.abilities.cost_next_short")
                ? Component.translatable(costKey, RefugioAbilityData.costForRank(rank + 1))
                : Component.translatable(costKey);

        int buttonY = skillDetailButtonsY(py, ph);
        boolean showFeedback = !feedbackKey.isEmpty()
                && !feedbackKey.equals("gui.refugio.abilities.feedback.enabled")
                && !feedbackKey.equals("gui.refugio.abilities.feedback.disabled");
        int feedbackHeight = showFeedback ? lineHeight + 2 : 0;
        if (compact) {
            drawCompactSkillDetailsPage(graphics, mouseX, mouseY, x, w, top, buttonY,
                    rank, rankLabel, stateKey, stateStyle, costKey, cost, free, skill, showFeedback);
            return;
        }
        int infoY = buttonY - lineHeight - 8 - feedbackHeight;
        int infoX = x + 14;
        int infoWidth = w - 28;
        int columnGap = 12;
        int columnWidth = Math.max(8, (infoWidth - columnGap) / 2);
        graphics.fill(infoX, infoY - 4, infoX + infoWidth, infoY + lineHeight + feedbackHeight + 2, 0x44A9844D);
        border(graphics, infoX, infoY - 4, infoWidth, lineHeight + feedbackHeight + 6, 0xFFB18A54);
        List<FormattedCharSequence> stateLines = font.split(Component.translatable(stateKey).withStyle(stateStyle), columnWidth);
        List<FormattedCharSequence> costLines = font.split(cost.withStyle(GOLD_STYLE), columnWidth);
        if (!stateLines.isEmpty()) graphics.textRenderer().accept(infoX + 8, infoY, stateLines.getFirst());
        if (!costLines.isEmpty()) graphics.textRenderer().accept(infoX + columnWidth + columnGap,
                infoY, costLines.getFirst());
        if (showFeedback) {
            List<FormattedCharSequence> feedback = font.split(
                    Component.translatable(feedbackKey).withStyle(SUCCESS_STYLE), infoWidth - 16);
            if (!feedback.isEmpty()) graphics.textRenderer().accept(infoX + 8,
                    infoY + lineHeight + 2, feedback.getFirst());
        }

        int descriptionTop = top + 38;
        int descriptionBottom = infoY - 8;
        graphics.fill(innerX, descriptionTop, innerX + innerWidth, Math.max(descriptionTop + 1, descriptionBottom), 0x25FFF4D8);
        border(graphics, innerX, descriptionTop, innerWidth, Math.max(1, descriptionBottom - descriptionTop), 0x75B18A54);
        List<FormattedCharSequence> description = font.split(
                Component.literal(skill.description()).withStyle(INK_STYLE), Math.max(8, innerWidth - 16));
        int descriptionY = descriptionTop + 8;
        for (FormattedCharSequence line : description) {
            if (descriptionY + lineHeight > descriptionBottom - 5) break;
            graphics.textRenderer().accept(innerX + 8, descriptionY, line);
            descriptionY += lineHeight + 2;
        }

        int buttonGap = 12;
        int buttonWidth = Math.max(1, (w - 36 - buttonGap) / 2);
        int leftButtonX = innerX;
        int rightButtonX = leftButtonX + buttonWidth + buttonGap;
        String buyKey = free ? "gui.refugio.abilities.claim_free"
                : rank == 0 ? "gui.refugio.abilities.buy" : "gui.refugio.abilities.upgrade";
        drawButton(graphics, leftButtonX, buttonY, buttonWidth, 30, Component.translatable(buyKey),
                mouseX, mouseY, canPurchase(skill), 0xFF24504F);
        drawButton(graphics, rightButtonX, buttonY, buttonWidth, 30,
                Component.translatable(isActive(skill) ? "gui.refugio.abilities.disable" : "gui.refugio.abilities.enable"),
                mouseX, mouseY, rank > 0, 0xFF293A48);
    }

    private void drawCompactSkillDetailsPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                             int x, int w, int top, int buttonY, int rank,
                                             Component rankLabel, String stateKey, Style stateStyle,
                                             String costKey, MutableComponent cost, boolean free,
                                             RefugioAbilityData.Skill skill, boolean showFeedback) {
        int innerX = x + 18;
        int innerWidth = w - 36;
        int lineHeight = font.lineHeight;
        int columnGap = 6;
        int columnWidth = Math.max(8, (innerWidth - columnGap * 2) / 3);
        int rowY = top + 4;
        graphics.fill(innerX, rowY - 3, innerX + innerWidth, rowY + lineHeight + 2, 0x44A9844D);
        border(graphics, innerX, rowY - 3, innerWidth, lineHeight + 5, 0xFFB18A54);

        List<FormattedCharSequence> rankLines = font.split(rankLabel, columnWidth);
        List<FormattedCharSequence> stateLines = font.split(
                Component.translatable(stateKey).withStyle(stateStyle), columnWidth);
        Component compactCost = costKey.equals("gui.refugio.abilities.cost_next_short")
                ? Component.translatable("gui.refugio.abilities.cost_next_compact",
                RefugioAbilityData.costForRank(rank + 1)).withStyle(GOLD_STYLE)
                : cost.withStyle(GOLD_STYLE);
        List<FormattedCharSequence> costLines = font.split(compactCost, columnWidth);
        if (!rankLines.isEmpty()) graphics.textRenderer().accept(innerX + 5, rowY, rankLines.getFirst());
        if (!stateLines.isEmpty()) graphics.textRenderer().accept(
                innerX + columnWidth + columnGap + 3, rowY, stateLines.getFirst());
        if (!costLines.isEmpty()) graphics.textRenderer().accept(
                innerX + (columnWidth + columnGap) * 2 + 1, rowY, costLines.getFirst());

        int barY = top + 18;
        graphics.fill(innerX, barY, innerX + innerWidth, barY + 5, 0xFFB89A6A);
        for (int segment = 0; segment < MAX_RANK; segment++) {
            int segmentLeft = innerX + segment * innerWidth / MAX_RANK;
            int segmentRight = innerX + (segment + 1) * innerWidth / MAX_RANK;
            if (segment < rank) graphics.fill(segmentLeft + 1, barY + 1,
                    Math.max(segmentLeft + 2, segmentRight - 1), barY + 4, ACCENTS[branchIndex]);
        }

        int descriptionTop = top + 26;
        int feedbackY = buttonY - lineHeight - 2;
        int descriptionBottom = showFeedback ? feedbackY - 3 : buttonY - 2;
        graphics.fill(innerX, descriptionTop, innerX + innerWidth,
                Math.max(descriptionTop + 1, descriptionBottom), 0x25FFF4D8);
        border(graphics, innerX, descriptionTop, innerWidth,
                Math.max(1, descriptionBottom - descriptionTop), 0x75B18A54);
        List<FormattedCharSequence> description = font.split(
                Component.literal(skill.description()).withStyle(INK_STYLE), Math.max(8, innerWidth - 16));
        int descriptionY = descriptionTop + 4;
        for (FormattedCharSequence line : description) {
            if (descriptionY + lineHeight > descriptionBottom - 2) break;
            graphics.textRenderer().accept(innerX + 8, descriptionY, line);
            descriptionY += lineHeight;
        }

        if (showFeedback) {
            List<FormattedCharSequence> feedback = font.split(
                    Component.translatable(feedbackKey).withStyle(SUCCESS_STYLE), innerWidth - 8);
            if (!feedback.isEmpty()) graphics.textRenderer().accept(innerX + 4, feedbackY, feedback.getFirst());
        }

        int buttonGap = 12;
        int buttonWidth = Math.max(1, (w - 36 - buttonGap) / 2);
        int leftButtonX = innerX;
        int rightButtonX = leftButtonX + buttonWidth + buttonGap;
        String buyKey = free ? "gui.refugio.abilities.claim_free"
                : rank == 0 ? "gui.refugio.abilities.buy" : "gui.refugio.abilities.upgrade";
        drawButton(graphics, leftButtonX, buttonY, buttonWidth, 30, Component.translatable(buyKey),
                mouseX, mouseY, canPurchase(skill), 0xFF24504F);
        drawButton(graphics, rightButtonX, buttonY, buttonWidth, 30,
                Component.translatable(isActive(skill) ? "gui.refugio.abilities.disable" : "gui.refugio.abilities.enable"),
                mouseX, mouseY, rank > 0, 0xFF293A48);
    }

    private void drawClose(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y) {
        boolean hovered = mouseX >= x && mouseX < x + 22 && mouseY >= y && mouseY < y + 22;
        graphics.fill(x, y, x + 22, y + 22, hovered ? 0xFF80532B : 0xFF5E3E23);
        border(graphics, x, y, 22, 22, hovered ? 0xFFFFD787 : 0xFFB08A50);
        int color = hovered ? 0xFFFFE9B0 : 0xFFE8D6B5;
        for (int i = 0; i < 6; i++) {
            graphics.fill(x + 8 + i, y + 8 + i, x + 10 + i, y + 10 + i, color);
            graphics.fill(x + 8 + i, y + 14 - i, x + 10 + i, y + 16 - i, color);
        }
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h, Component label,
                            int mouseX, int mouseY, boolean enabled, int baseColor) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        Identifier texture = baseColor == 0xFF24504F ? PRIMARY_BUTTON_TEXTURE : SECONDARY_BUTTON_TEXTURE;
        if (w > 0 && h > 0) graphics.blit(texture, x, y, x + w, y + h, 0.0F, 1.0F, 0.28F, 0.72F);
        if (!enabled) {
            graphics.fill(x + 4, y + 3, x + w - 4, y + h - 3, 0x78332213);
        } else if (hovered && w > 12 && h > 10) {
            graphics.fill(x + 6, y + 5, x + w - 6, y + h - 5, 0x24E8B954);
            border(graphics, x + 3, y + 3, w - 6, h - 6, 0xB8D8B677);
            graphics.fill(x + 6, y + 4, x + w - 6, y + 5, 0xA0FFF0BA);
            drawDiamond(graphics, x + 7, y + h / 2, 2, 0xFFE9C77F);
            drawDiamond(graphics, x + w - 8, y + h / 2, 2, 0xFFE9C77F);
        }
        Style labelStyle = !enabled ? DISABLED_BUTTON_STYLE
                : hovered ? Style.EMPTY.withFont(UI_FONT).withColor(0xFFFFE5A0) : LIGHT_STYLE;
        List<FormattedCharSequence> lines = font.split(
                Component.literal(label.getString()).withStyle(labelStyle), Math.max(8, w - 18));
        int count = Math.min(2, lines.size());
        int textY = y + (h - count * font.lineHeight) / 2;
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
        return branchNodeIndex <= 0 || branchNodeIndex > skills.size()
                || rank(skills.get(branchNodeIndex - 1)) > 0;
    }

    private boolean isFreeCandidate(RefugioAbilityData.Skill skill) {
        List<RefugioAbilityData.Skill> track = RefugioAbilityData.skills(skill.track());
        return freeChoice.isEmpty() && rank(skill) == 0 && !track.isEmpty()
                && track.getFirst().id().equals(skill.id());
    }

    private boolean canPurchase(RefugioAbilityData.Skill skill) {
        if (skill == null || rank(skill) >= MAX_RANK) return false;
        int index = currentTrackSkills().indexOf(skill);
        if (!isUnlocked(index)) return false;
        if (rank(skill) == 0 && freeChoice.isEmpty() && !isFreeCandidate(skill)) return false;
        if (isFreeCandidate(skill)) return true;
        return coins >= RefugioAbilityData.costForRank(rank(skill) + 1);
    }

    private int panelWidth() { return Math.max(1, Math.min(1040, width - 32)); }
    private int panelHeight() { return Math.max(1, Math.min(720, height - 32)); }
    private int panelX() { return (width - panelWidth()) / 2; }
    private int panelY() { return (height - panelHeight()) / 2; }
    private int closeButtonX(int px, int pw) { return px + pw - 58; }
    private int panelContentBottom(int py, int ph) {
        int inset = isCompactLayout(ph) ? 20 : Math.max(34, (int) Math.ceil(ph * 0.085));
        return py + ph - inset;
    }
    private int actionBottomInset(int ph) {
        return isCompactLayout(ph) ? 20 : Math.max(38, (int) Math.ceil(ph * 0.10));
    }
    private boolean isCompactLayout(int ph) { return ph < 430; }
    private int skillDetailButtonsY(int py, int ph) {
        return py + ph - actionBottomInset(ph) - 30;
    }
    private int skillPageColumns(int pw) { return pw >= 380 ? SKILLS_PER_PAGE : 2; }
    private int skillsPerPage(int pw) { return SKILLS_PER_PAGE; }
    private int skillPageCount(int pw) {
        int count = currentTrackSkills().size();
        return Math.max(1, (count + skillsPerPage(pw) - 1) / skillsPerPage(pw));
    }
    private int skillPageStart(int pw) {
        skillPageIndex = Math.max(0, Math.min(skillPageIndex, skillPageCount(pw) - 1));
        return skillPageIndex * skillsPerPage(pw);
    }
    private int skillPageControlsY(int py, int ph) {
        return isCompactLayout(ph) ? py + 90 : panelContentBottom(py, ph) - 26;
    }
    private int skillPageGridBottom(int py, int ph) {
        return isCompactLayout(ph) ? panelContentBottom(py, ph) - 8
                : skillPageControlsY(py, ph) - font.lineHeight - 15;
    }
    private int skillPageGridTop(int py, int ph) { return py + (isCompactLayout(ph) ? 116 : 120); }
    private int skillPageButtonWidth(int ph) { return isCompactLayout(ph) ? 28 : 34; }
    private int skillPageButtonHeight(int ph) { return isCompactLayout(ph) ? 20 : 22; }
    private int skillPreviousButtonX(int px, int pw) {
        return isCompactLayout(panelHeight()) ? px + pw - 104 : px + pw / 2 - 85;
    }
    private int skillNextButtonX(int px, int pw) {
        return isCompactLayout(panelHeight()) ? px + pw - 68 : px + pw / 2 + 51;
    }
    private SkillCardBounds skillPageCardBounds(int index, int px, int py, int pw, int ph) {
        int columns = skillPageColumns(pw);
        int pageStart = skillPageStart(pw);
        int itemCount = Math.min(skillsPerPage(pw), currentTrackSkills().size() - pageStart);
        int rows = Math.max(1, (itemCount + columns - 1) / columns);
        int gridX = px + 28;
        int gridWidth = Math.max(1, pw - 56);
        int gapX = columns == 1 ? 0 : 12;
        int cardWidth = Math.max(1, (gridWidth - gapX * (columns - 1)) / columns);
        int compact = isCompactLayout(ph) ? 1 : 0;
        int gridTop = skillPageGridTop(py, ph);
        int gridBottom = skillPageGridBottom(py, ph);
        int gapY = rows == 1 ? 0 : 10;
        int cardHeight = Math.max(compact == 1 ? 44 : 54,
                (gridBottom - gridTop - gapY * (rows - 1)) / rows);
        cardHeight = Math.min(190, cardHeight);
        int visibleIndex = index - pageStart;
        int row = visibleIndex / columns;
        int column = visibleIndex % columns;
        return new SkillCardBounds(gridX + column * (cardWidth + gapX),
                gridTop + row * (cardHeight + gapY), cardWidth, cardHeight);
    }

    private void closeScreen() {
        ClientPlayNetworking.send(new RefugioUiPayload("ABILITY_CLOSE"));
        RefugioClient.clearAbilityScreen(this);
        minecraft.setScreenAndShow(null);
    }

    private void text(GuiGraphicsExtractor graphics, Component component, int x, int y) {
        graphics.textRenderer().accept(x, y, component);
    }

    private static boolean inside(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    private static void border(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private record CategoryCardBounds(int x, int y, int width, int height) { }
    private record SkillCardBounds(int x, int y, int width, int height) { }
}
