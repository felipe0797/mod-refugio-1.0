package com.felpz.refugio.client;

import com.felpz.refugio.ui.RefugioUiPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.Arrays;
import java.util.List;

/** A story-first dialogue panel, independent from inventory/container screens. */
public final class RefugioDialogueScreen extends Screen {
    private static final Identifier PARCHMENT_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/parchment.png");
    private static final Identifier WOOD_FRAME_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/wood_frame.png");
    private static final Identifier TITLE_PLAQUE_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/title_plaque.png");
    private static final Identifier PRIMARY_BUTTON_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/button_primary.png");
    private static final Identifier CLOSE_BUTTON_TEXTURE = Identifier.parse("refugio:textures/gui/dialogue/button_close.png");
    private static final Identifier BALDUR_PORTRAIT = Identifier.parse("refugio:textures/gui/dialogue/portraits/baldur.png");
    private static final Identifier ELDRIN_PORTRAIT = Identifier.parse("refugio:textures/gui/dialogue/portraits/eldrin.png");
    private static final Identifier MARTA_PORTRAIT = Identifier.parse("refugio:textures/gui/dialogue/portraits/marta.png");
    private static final Identifier DARIO_PORTRAIT = Identifier.parse("refugio:textures/gui/dialogue/portraits/dario.png");
    private static final Identifier KAEL_PORTRAIT = Identifier.parse("refugio:textures/gui/dialogue/portraits/kael.png");

    private static final FontDescription UI_FONT = new FontDescription.Resource(Identifier.parse("minecraft:uniform"));
    private static final Style INK_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0x4A3520);
    private static final Style TITLE_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFFF2D7);
    private static final Style BUTTON_TEXT_STYLE = Style.EMPTY.withFont(UI_FONT).withColor(0xFFF7E9);

    private final String speakerKey;
    private final List<String> lineKeys;
    private final String action;
    private final String actionLabelKey;
    private int dialogueScrollOffset;
    private boolean draggingDialogueScrollbar;
    private double dialogueScrollbarGrabOffset;

    public RefugioDialogueScreen(String encoded) {
        super(Component.translatable("gui.refugio.dialogue.title"));
        String[] parts = encoded.split("\\t", -1);
        if (parts.length < 5 || !parts[0].equals("DIALOGUE")) {
            speakerKey = "entity.refugio.kael";
            lineKeys = List.of("dialogue.refugio.kael.arrival.1");
            action = "DISMISS";
            actionLabelKey = "gui.refugio.dialogue.close";
        } else {
            speakerKey = parts[1];
            lineKeys = Arrays.stream(parts[2].split("\\|", -1)).filter(s -> !s.isBlank()).limit(6).toList();
            action = parts[3];
            actionLabelKey = parts[4];
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x88090604);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        Layout layout = computeLayout();
        drawParchmentAndWoodFrame(graphics, layout.x, layout.y, layout.panelWidth, layout.panelHeight);
        drawTitleBanner(graphics, layout);
        Identifier portrait = portraitTexture();
        if (portrait != null && layout.portraitWidth > 0 && layout.portraitHeight > 0) {
            drawPortraitSection(graphics, portrait, layout.portraitX, layout.portraitY,
                    layout.portraitWidth, layout.portraitHeight);
        }
        int dividerOffset = layout.panelHeight < 210 ? 4 : 7;
        drawOrnamentalDivider(graphics, layout.textX, layout.viewportTop - dividerOffset, layout.textWidth);
        drawOrnamentalDivider(graphics, layout.textX, layout.viewportBottom + dividerOffset - 1, layout.textWidth);
        if (shouldDrawCrest(layout)) drawRefugeCrest(graphics, layout);

        int maxScroll = maxDialogueScroll(layout.viewportTop, layout.viewportBottom, layout.textWidth);
        dialogueScrollOffset = clampDialogueScroll(dialogueScrollOffset, maxScroll);
        int lineY = layout.viewportTop - dialogueScrollOffset;
        graphics.enableScissor(layout.textX, layout.viewportTop,
                layout.textX + layout.textWidth, layout.viewportBottom);
        for (String lineKey : lineKeys) {
            List<FormattedCharSequence> wrapped = font.split(
                    Component.translatable(lineKey).withStyle(INK_STYLE), layout.textWidth);
            for (FormattedCharSequence line : wrapped) {
                graphics.textRenderer().accept(layout.textX + 4, lineY, line);
                lineY += font.lineHeight + 2;
            }
            lineY += 5;
        }
        graphics.disableScissor();
        drawDialogueScrollbar(graphics, layout.scrollbarX, layout.viewportTop, layout.viewportBottom,
                layout.textWidth, maxScroll);

        drawTexturedButton(graphics, layout.actionButtonX, layout.buttonY, layout.actionButtonWidth,
                layout.buttonHeight, Component.translatable(actionLabelKey).withStyle(BUTTON_TEXT_STYLE),
                mouseX, mouseY, true);
        drawTexturedButton(graphics, layout.closeButtonX, layout.buttonY, layout.closeButtonWidth,
                layout.buttonHeight, Component.translatable("gui.refugio.dialogue.close").withStyle(BUTTON_TEXT_STYLE),
                mouseX, mouseY, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        Layout layout = computeLayout();
        if (inside(event, layout.scrollbarX - 5, layout.viewportTop, 14,
                layout.viewportBottom - layout.viewportTop)) {
            int thumbTop = dialogueScrollbarThumbTop(layout.viewportTop, layout.viewportBottom, layout.textWidth);
            int thumbHeight = dialogueScrollbarThumbHeight(layout.viewportTop, layout.viewportBottom, layout.textWidth);
            dialogueScrollbarGrabOffset = event.y() >= thumbTop && event.y() <= thumbTop + thumbHeight
                    ? event.y() - thumbTop : thumbHeight / 2.0;
            draggingDialogueScrollbar = true;
            updateDialogueScrollFromMouse(event.y(), layout.viewportTop, layout.viewportBottom, layout.textWidth);
            return true;
        }
        if (inside(event, layout.actionButtonX, layout.buttonY, layout.actionButtonWidth, layout.buttonHeight)) {
            ClientPlayNetworking.send(new RefugioUiPayload("DIALOGUE_ACTION\t" + action));
            minecraft.setScreenAndShow(null);
            return true;
        }
        if (inside(event, layout.closeButtonX, layout.buttonY, layout.closeButtonWidth, layout.buttonHeight)) {
            closeDialogue();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Layout layout = computeLayout();
        int maxScroll = maxDialogueScroll(layout.viewportTop, layout.viewportBottom, layout.textWidth);
        if (inside(mouseX, mouseY, layout.textX - 4, layout.viewportTop,
                layout.scrollbarX - layout.textX + 12, layout.viewportBottom - layout.viewportTop)
                && maxScroll > 0) {
            dialogueScrollOffset = clampDialogueScroll(
                    dialogueScrollOffset - (int) Math.signum(verticalAmount) * 24, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingDialogueScrollbar) {
            Layout layout = computeLayout();
            updateDialogueScrollFromMouse(event.y(), layout.viewportTop, layout.viewportBottom, layout.textWidth);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && draggingDialogueScrollbar) {
            draggingDialogueScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        closeDialogue();
    }

    private Layout computeLayout() {
        int marginX = width < 400 ? 8 : (width < 600 ? 12 : 18);
        int marginY = height < 240 ? 6 : (height < 340 ? 10 : 16);
        int availableWidth = Math.max(1, width - marginX * 2);
        int availableHeight = Math.max(1, height - marginY * 2);
        double scaleRatio = (width < 460 || height < 270) ? 0.94 : (height < 350 ? 0.88 : 0.82);
        int widthLimit = (int) Math.floor(availableWidth * scaleRatio);
        double panelAspect = 1.68;
        int heightLimit = (int) Math.floor(availableHeight * scaleRatio * panelAspect);
        int panelWidth = Math.max(1, Math.min(620, Math.min(widthLimit, heightLimit)));
        int panelHeight = Math.max(1, Math.min((int) Math.floor(availableHeight * scaleRatio),
                (int) Math.round(panelWidth / panelAspect)));
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;

        int insetX = Math.max(16, (int) Math.ceil(panelWidth * 0.068));
        int insetY = Math.max(14, (int) Math.ceil(panelHeight * 0.104));
        int innerLeft = x + insetX;
        int innerRight = x + panelWidth - insetX;
        int innerTop = y + insetY;
        int innerBottom = y + panelHeight - insetY;
        int innerW = Math.max(80, innerRight - innerLeft);
        int innerH = Math.max(64, innerBottom - innerTop);

        int titleHeight = innerH < 150 ? 20 : Math.max(24, Math.min(36, panelHeight / 8));
        int bannerY = innerTop;
        Component title = Component.translatable(speakerKey).withStyle(TITLE_STYLE);
        int bannerWidth = Math.max(100, Math.min(innerW - 12,
                Math.max(140, Math.min(360, font.width(title) + 48))));
        int bannerX = x + (panelWidth - bannerWidth) / 2;

        int buttonHeight = innerH < 150 ? 18 : Math.min(28, Math.max(20, panelHeight / 10));
        int buttonY = innerBottom - buttonHeight;
        int dividerPad = innerH < 155 ? 6 : 11;
        int viewportTop = bannerY + titleHeight + dividerPad;
        int viewportBottom = Math.max(viewportTop + 20, buttonY - dividerPad);
        int viewportHeight = viewportBottom - viewportTop;

        Identifier portrait = portraitTexture();
        boolean showPortrait = portrait != null && panelWidth >= 290 && viewportHeight >= 54;
        int portraitHeight = showPortrait ? Math.min(140, Math.max(44, viewportHeight - 6)) : 0;
        int portraitWidth = portraitHeight > 0 ? portraitHeight * 3 / 4 : 0;
        int portraitX = innerLeft + 2;
        int portraitY = viewportTop + Math.max(0, (viewportHeight - portraitHeight) / 2);
        int textX = showPortrait ? portraitX + portraitWidth + (panelWidth < 380 ? 10 : 16) : innerLeft + 4;
        int scrollbarX = innerRight - 5;
        int crestReserve = showPortrait && panelWidth >= 480 && viewportHeight >= 76 ? 64 : 0;
        int textWidth = Math.max(24, scrollbarX - textX - 6 - crestReserve);

        int buttonMargin = panelWidth >= 440 ? Math.max(insetX + 6, 36) : insetX;
        int gap = panelWidth >= 420 ? 16 : 8;
        int buttonAreaWidth = Math.max(40, panelWidth - buttonMargin * 2 - gap);
        int actionButtonWidth = Math.max(20, buttonAreaWidth * 6 / 10);
        int closeButtonWidth = Math.max(20, buttonAreaWidth - actionButtonWidth);
        int actionButtonX = x + buttonMargin;
        int closeButtonX = actionButtonX + actionButtonWidth + gap;

        return new Layout(x, y, panelWidth, panelHeight,
                bannerX, bannerY, bannerWidth, titleHeight,
                portraitX, portraitY, portraitWidth, portraitHeight,
                textX, textWidth, viewportTop, viewportBottom, scrollbarX,
                buttonY, buttonHeight, actionButtonX, actionButtonWidth, closeButtonX, closeButtonWidth);
    }

    private void drawParchmentAndWoodFrame(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        int insetX = Math.max(1, (int) Math.round(w * 0.055));
        int insetY = Math.max(1, (int) Math.round(h * 0.085));
        blitFull(graphics, PARCHMENT_TEXTURE, x + insetX, y + insetY,
                Math.max(1, w - insetX * 2), Math.max(1, h - insetY * 2));
        blitFull(graphics, WOOD_FRAME_TEXTURE, x, y, w, h);
    }

    private void drawTitleBanner(GuiGraphicsExtractor graphics, Layout layout) {
        Component title = Component.translatable(speakerKey).withStyle(TITLE_STYLE);
        int bannerWidth = layout.bannerWidth;
        int bannerHeight = layout.titleHeight;
        int x = layout.bannerX;
        int y = layout.bannerY;
        if (bannerWidth > 0 && bannerHeight > 0) {
            graphics.blit(TITLE_PLAQUE_TEXTURE, x, y, x + bannerWidth, y + bannerHeight,
                    0.02F, 0.98F, 0.32F, 0.66F);
        }
        if (bannerWidth < layout.panelWidth - 64) {
            drawDiamond(graphics, x - 9, y + bannerHeight / 2, 3, 0xFFB89455);
            drawDiamond(graphics, x + bannerWidth + 9, y + bannerHeight / 2, 3, 0xFFB89455);
        }

        int textWidth = Math.max(16, bannerWidth - 28);
        List<FormattedCharSequence> lines = font.split(title, textWidth);
        int maxLines = bannerHeight >= font.lineHeight * 2 + 2 ? 2 : 1;
        int count = Math.min(maxLines, lines.size());
        int textY = y + Math.max(0, (bannerHeight - count * font.lineHeight) / 2);
        for (int i = 0; i < count; i++) {
            FormattedCharSequence line = lines.get(i);
            graphics.textRenderer().accept(x + (bannerWidth - font.width(line)) / 2,
                    textY + i * font.lineHeight, line);
        }
    }

    private void drawPortraitSection(GuiGraphicsExtractor graphics, Identifier texture,
                                     int x, int y, int w, int h) {
        graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF5A3A20);
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFD1AD78);
        blitFull(graphics, texture, x, y, w, h);
        border(graphics, x, y, w, h, 0xFF72502E);
    }

    private Identifier portraitTexture() {
        return switch (speakerKey) {
            case "entity.refugio.merchant" -> BALDUR_PORTRAIT;
            case "entity.refugio.quest_giver" -> ELDRIN_PORTRAIT;
            case "entity.refugio.garden_quest_giver" -> MARTA_PORTRAIT;
            case "entity.refugio.expedition_quest_giver" -> DARIO_PORTRAIT;
            case "entity.refugio.kael" -> KAEL_PORTRAIT;
            default -> null;
        };
    }

    private void drawOrnamentalDivider(GuiGraphicsExtractor graphics, int x, int y, int w) {
        if (w < 20) return;
        int middle = x + w / 2;
        graphics.fill(x, y, middle - 5, y + 1, 0x998B6944);
        graphics.fill(middle + 5, y, x + w, y + 1, 0x998B6944);
        drawDiamond(graphics, middle, y, 3, 0xFF9B744A);
    }

    private static boolean shouldDrawCrest(Layout layout) {
        return layout.portraitWidth > 0 && layout.panelWidth >= 480
                && layout.viewportBottom - layout.viewportTop >= 76;
    }

    private void drawRefugeCrest(GuiGraphicsExtractor graphics, Layout layout) {
        int crestWidth = 56;
        int crestHeight = 68;
        int x = layout.scrollbarX - crestWidth - 6;
        int y = layout.viewportTop + Math.max(0,
                (layout.viewportBottom - layout.viewportTop - crestHeight) / 2);
        int center = x + crestWidth / 2;
        int gold = 0xFF9A713F;
        drawDiamond(graphics, center, y + 4, 3, gold);
        drawDiamond(graphics, x + 8, y + 15, 2, gold);
        drawDiamond(graphics, x + crestWidth - 8, y + 15, 2, gold);
        drawDiamond(graphics, center, y + 43, 2, gold);

        graphics.fill(center - 11, y + 25, center + 11, y + 27, gold);
        graphics.fill(center - 8, y + 22, center + 8, y + 25, gold);
        graphics.fill(center - 5, y + 19, center + 5, y + 22, gold);
        graphics.fill(center - 8, y + 27, center + 8, y + 40, 0xFF76502F);
        graphics.fill(center - 2, y + 32, center + 3, y + 40, 0xFF3E2A1C);
        graphics.fill(center - 6, y + 29, center - 4, y + 32, 0xFFE8C987);

        Component crestLabel = Component.translatable("gui.refugio.dialogue.crest").withStyle(INK_STYLE);
        FormattedCharSequence crestText = crestLabel.getVisualOrderText();
        graphics.textRenderer().accept(x + (crestWidth - font.width(crestLabel)) / 2,
                y + 50, crestText);
    }

    private static void drawDiamond(GuiGraphicsExtractor graphics, int centerX, int centerY,
                                    int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int halfWidth = radius - Math.abs(row);
            graphics.fill(centerX - halfWidth, centerY + row,
                    centerX + halfWidth + 1, centerY + row + 1, color);
        }
    }

    private int maxDialogueScroll(int top, int bottom, int textWidth) {
        int contentHeight = lineKeys.stream()
                .mapToInt(key -> font.split(Component.translatable(key).withStyle(INK_STYLE), textWidth).size()
                        * (font.lineHeight + 2) + 5).sum();
        return Math.max(0, contentHeight - (bottom - top));
    }

    private int clampDialogueScroll(int value, int maxScroll) {
        return Math.max(0, Math.min(maxScroll, value));
    }

    private int dialogueScrollbarThumbHeight(int top, int bottom, int textWidth) {
        int track = bottom - top;
        int content = track + maxDialogueScroll(top, bottom, textWidth);
        return content <= track ? track : Math.max(18, track * track / content);
    }

    private int dialogueScrollbarThumbTop(int top, int bottom, int textWidth) {
        int maxScroll = maxDialogueScroll(top, bottom, textWidth);
        int travel = bottom - top - dialogueScrollbarThumbHeight(top, bottom, textWidth);
        return top + (maxScroll == 0 ? 0 : travel * clampDialogueScroll(dialogueScrollOffset, maxScroll) / maxScroll);
    }

    private void updateDialogueScrollFromMouse(double mouseY, int top, int bottom, int textWidth) {
        int maxScroll = maxDialogueScroll(top, bottom, textWidth);
        int travel = bottom - top - dialogueScrollbarThumbHeight(top, bottom, textWidth);
        if (maxScroll <= 0 || travel <= 0) {
            dialogueScrollOffset = 0;
            return;
        }
        double thumbTop = mouseY - dialogueScrollbarGrabOffset;
        double progress = Math.max(0.0, Math.min(1.0, (thumbTop - top) / travel));
        dialogueScrollOffset = (int) Math.round(progress * maxScroll);
    }

    private void drawDialogueScrollbar(GuiGraphicsExtractor graphics, int x, int top, int bottom,
                                       int textWidth, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbHeight = dialogueScrollbarThumbHeight(top, bottom, textWidth);
        int thumbTop = dialogueScrollbarThumbTop(top, bottom, textWidth);
        graphics.fill(x, top, x + 3, bottom, 0x88765030);
        border(graphics, x - 1, thumbTop, 5, thumbHeight, 0xFF684522);
        graphics.fill(x, thumbTop + 1, x + 3, thumbTop + thumbHeight - 1,
                draggingDialogueScrollbar ? 0xFFC49A65 : 0xFF9B744A);
    }

    private void drawTexturedButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                                    MutableComponent label, int mouseX, int mouseY, boolean primary) {
        Identifier texture = primary ? PRIMARY_BUTTON_TEXTURE : CLOSE_BUTTON_TEXTURE;
        if (w > 0 && h > 0) {
            graphics.blit(texture, x, y, x + w, y + h, 0.0F, 1.0F, 0.28F, 0.72F);
        }
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        if (hovered && w > 12 && h > 10) {
            int insetX = w < 50 ? 3 : 6;
            int insetY = h < 20 ? 3 : 5;
            graphics.fill(x + insetX, y + insetY, x + w - insetX, y + h - insetY, 0x24E8B954);
            border(graphics, x + 2, y + 2, w - 4, h - 4, 0xB8D8B677);
            graphics.fill(x + insetX, y + insetY - 1, x + w - insetX, y + insetY, 0xA0FFF0BA);
            if (w >= 48) {
                drawDiamond(graphics, x + 7, y + h / 2, 2, 0xFFE9C77F);
                drawDiamond(graphics, x + w - 8, y + h / 2, 2, 0xFFE9C77F);
            }
        }

        MutableComponent displayLabel = label.setStyle(hovered
                ? BUTTON_TEXT_STYLE.withColor(0xFFFFE7B0) : BUTTON_TEXT_STYLE);
        boolean showIcon = w >= 92 && h >= 18;
        int iconSize = showIcon ? 14 : 0;
        int iconGap = showIcon ? 6 : 0;
        int textPad = w < 100 ? 12 : 20;
        List<FormattedCharSequence> lines = font.split(displayLabel,
                Math.max(8, w - textPad - iconSize - iconGap));
        int maxLines = h >= font.lineHeight * 2 + 2 ? 2 : 1;
        int count = Math.min(maxLines, lines.size());
        int textBlockWidth = lines.stream().limit(count).mapToInt(font::width).max().orElse(0);
        int groupWidth = iconSize + iconGap + textBlockWidth;
        int groupX = x + Math.max(0, (w - groupWidth) / 2);
        int textX = groupX + iconSize + iconGap;
        int textY = y + Math.max(0, (h - count * font.lineHeight) / 2);
        if (showIcon) {
            drawButtonIcon(graphics, groupX, y + (h - 14) / 2,
                    !primary, primary && actionLabelKey.equals("gui.refugio.dialogue.action.shop"));
        }
        for (int i = 0; i < count; i++) {
            FormattedCharSequence line = lines.get(i);
            graphics.textRenderer().accept(textX + (textBlockWidth - font.width(line)) / 2,
                    textY + i * font.lineHeight, line);
        }
    }

    private static void drawButtonIcon(GuiGraphicsExtractor graphics, int x, int y,
                                       boolean close, boolean coinPouch) {
        int gold = 0xFFFFD987;
        if (close) {
            for (int i = 0; i < 7; i++) {
                graphics.fill(x + 2 + i, y + 2 + i, x + 4 + i, y + 4 + i, gold);
                graphics.fill(x + 2 + i, y + 10 - i, x + 4 + i, y + 12 - i, gold);
            }
        } else if (coinPouch) {
            graphics.fill(x + 5, y + 1, x + 9, y + 3, 0xFFEBD17F);
            graphics.fill(x + 4, y + 3, x + 10, y + 5, 0xFF9D682C);
            graphics.fill(x + 3, y + 5, x + 11, y + 12, 0xFFD5A14D);
            graphics.fill(x + 4, y + 12, x + 10, y + 13, 0xFF815126);
            graphics.fill(x + 2, y + 7, x + 3, y + 11, 0xFFD5A14D);
            graphics.fill(x + 11, y + 7, x + 12, y + 11, 0xFFD5A14D);
            graphics.fill(x + 6, y + 7, x + 8, y + 10, 0xFFFFE7A0);
        } else {
            drawDiamond(graphics, x + 7, y + 7, 4, gold);
        }
    }

    private static void blitFull(GuiGraphicsExtractor graphics, Identifier texture,
                                 int x, int y, int width, int height) {
        if (width > 0 && height > 0) {
            graphics.blit(texture, x, y, x + width, y + height, 0.0F, 1.0F, 0.0F, 1.0F);
        }
    }

    private void closeDialogue() {
        ClientPlayNetworking.send(new RefugioUiPayload("DIALOGUE_CLOSE"));
        minecraft.setScreenAndShow(null);
    }

    private static boolean inside(MouseButtonEvent event, int x, int y, int w, int h) {
        return event.x() >= x && event.x() < x + w && event.y() >= y && event.y() < y + h;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static void border(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private record Layout(int x, int y, int panelWidth, int panelHeight,
                          int bannerX, int bannerY, int bannerWidth, int titleHeight,
                          int portraitX, int portraitY, int portraitWidth, int portraitHeight,
                          int textX, int textWidth, int viewportTop, int viewportBottom, int scrollbarX,
                          int buttonY, int buttonHeight, int actionButtonX, int actionButtonWidth,
                          int closeButtonX, int closeButtonWidth) { }
}
