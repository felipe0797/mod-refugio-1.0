package com.felpz.refugio.story;

import com.felpz.refugio.Refugio;
import com.felpz.refugio.ui.RefugioUiService;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-player, persistent story beats for the Refugio introduction. */
public final class RefugioStory {
    private static final AttachmentType<Boolean> ARRIVAL_INTRO_SEEN = AttachmentRegistry.create(
            Refugio.id("arrival_intro_seen"), builder -> builder.persistent(Codec.BOOL)
                    .initializer(() -> false).copyOnDeath());
    private static final AttachmentType<Boolean> CASTLE_INTRO_SEEN = AttachmentRegistry.create(
            Refugio.id("castle_intro_seen"), builder -> builder.persistent(Codec.BOOL)
                    .initializer(() -> false).copyOnDeath());
    private static final AttachmentType<Boolean> ARRIVAL_DIALOGUE_SCREEN_SEEN = AttachmentRegistry.create(
            Refugio.id("arrival_dialogue_screen_seen"), builder -> builder.persistent(Codec.BOOL)
                    .initializer(() -> false).copyOnDeath());
    private static final AttachmentType<Boolean> ARRIVAL_DIALOGUE_ACKNOWLEDGED = AttachmentRegistry.create(
            Refugio.id("arrival_dialogue_acknowledged"), builder -> builder.persistent(Codec.BOOL)
                    .initializer(() -> false).copyOnDeath());
    private static final AttachmentType<Boolean> CASTLE_DIALOGUE_ACKNOWLEDGED = AttachmentRegistry.create(
            Refugio.id("castle_dialogue_acknowledged"), builder -> builder.persistent(Codec.BOOL)
                    .initializer(() -> false).copyOnDeath());
    private static final AttachmentType<Boolean> STARTER_PACKAGE_DIALOGUE_ACKNOWLEDGED = AttachmentRegistry.create(
            Refugio.id("starter_package_dialogue_acknowledged"), builder -> builder.persistent(Codec.BOOL)
                    .initializer(() -> false).copyOnDeath());
    private static final Map<UUID, Integer> PENDING_STARTER_DIALOGUES = new HashMap<>();
    private static final int STARTER_DIALOGUE_DELAY_TICKS = 20;

    private RefugioStory() { }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(RefugioStory::tickStarterDialogues);
    }

    public static void scheduleStarterPackageDialogue(ServerPlayer player) {
        if (player.getAttachedOrCreate(STARTER_PACKAGE_DIALOGUE_ACKNOWLEDGED)) return;
        PENDING_STARTER_DIALOGUES.put(player.getUUID(), STARTER_DIALOGUE_DELAY_TICKS);
    }

    private static void tickStarterDialogues(MinecraftServer server) {
        var iterator = PENDING_STARTER_DIALOGUES.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            int remaining = entry.getValue() - 1;
            if (remaining > 0) {
                entry.setValue(remaining);
                continue;
            }
            iterator.remove();
            if (player.getAttachedOrCreate(STARTER_PACKAGE_DIALOGUE_ACKNOWLEDGED)) continue;
            RefugioUiService.openDialogue(player, "entity.refugio.narrator", List.of(
                            "dialogue.refugio.starter_bag.1", "dialogue.refugio.starter_bag.2", "dialogue.refugio.starter_bag.3"),
                    "STARTER_PACKAGE_ACK", "gui.refugio.dialogue.continue");
        }
    }

    public static void talkToKael(ServerPlayer player, boolean atCastle) {
        if (!atCastle) {
            RefugioUiService.openDialogue(player, "entity.refugio.kael", List.of(
                    "dialogue.refugio.kael.arrival.1", "dialogue.refugio.kael.arrival.2", "dialogue.refugio.kael.arrival.3"),
                    "MOVE_KAEL_TO_CASTLE", "gui.refugio.dialogue.action.castle");
            return;
        }

        if (!player.getAttachedOrCreate(CASTLE_DIALOGUE_ACKNOWLEDGED)) {
            RefugioUiService.openDialogue(player, "entity.refugio.kael", List.of(
                    "dialogue.refugio.kael.castle.1", "dialogue.refugio.kael.castle.2", "dialogue.refugio.kael.castle.3"),
                    "CASTLE_INTRO_DONE", "gui.refugio.dialogue.action.skills");
        } else {
            RefugioUiService.openDialogue(player, "entity.refugio.kael",
                    List.of("dialogue.refugio.kael.castle.reminder"),
                    "OPEN_SKILLS", "gui.refugio.dialogue.action.skills");
        }
    }

    public static boolean hasSeenCastleIntro(ServerPlayer player) {
        return player.getAttachedOrCreate(CASTLE_DIALOGUE_ACKNOWLEDGED);
    }

    public static boolean hasSeenArrivalIntro(ServerPlayer player) {
        return player.getAttachedOrCreate(ARRIVAL_DIALOGUE_ACKNOWLEDGED);
    }

    public static void acknowledgeArrivalDialogue(ServerPlayer player) {
        player.setAttached(ARRIVAL_INTRO_SEEN, true);
        player.setAttached(ARRIVAL_DIALOGUE_SCREEN_SEEN, true);
        player.setAttached(ARRIVAL_DIALOGUE_ACKNOWLEDGED, true);
    }

    public static void acknowledgeCastleDialogue(ServerPlayer player) {
        player.setAttached(CASTLE_INTRO_SEEN, true);
        player.setAttached(CASTLE_DIALOGUE_ACKNOWLEDGED, true);
    }

    public static void acknowledgeStarterPackageDialogue(ServerPlayer player) {
        player.setAttached(STARTER_PACKAGE_DIALOGUE_ACKNOWLEDGED, true);
    }

}
