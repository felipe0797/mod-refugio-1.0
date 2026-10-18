package com.felpz.refugio.ui;

import com.felpz.refugio.ability.RefugioAbilityData;
import com.felpz.refugio.data.RefugioPlayerData;
import com.felpz.refugio.menu.RefugioMenu;
import com.felpz.refugio.npc.RefugioNPCs;
import com.felpz.refugio.quest.RefugioSideQuests;
import com.felpz.refugio.story.RefugioStory;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Opens client-only custom screens while keeping every game-changing action on the server. */
public final class RefugioUiService {
    private static final long SESSION_MILLIS = 10 * 60 * 1000L;
    private static final Map<UUID, Long> ABILITY_SESSIONS = new HashMap<>();
    private static final Map<UUID, DialogueSession> DIALOGUE_SESSIONS = new HashMap<>();

    private RefugioUiService() { }

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(RefugioUiPayload.TYPE, RefugioUiPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RefugioUiPayload.TYPE, RefugioUiPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RefugioUiPayload.TYPE, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload.data())));
    }

    public static void openAbilities(ServerPlayer player) {
        if (!RefugioNPCs.isKaelAtCastle((net.minecraft.server.level.ServerLevel) player.level())) {
            player.sendSystemMessage(Component.translatable("message.refugio.ability.castle_only"));
            return;
        }
        long expiresAt = System.currentTimeMillis() + SESSION_MILLIS;
        ABILITY_SESSIONS.put(player.getUUID(), expiresAt);
        DIALOGUE_SESSIONS.remove(player.getUUID());
        sendAbilityState(player, "");
    }

    public static void openDialogue(ServerPlayer player, String speakerKey, List<String> lineKeys,
                                   String action, String actionLabelKey) {
        if (lineKeys.isEmpty() || lineKeys.size() > 6) return;
        long expiresAt = System.currentTimeMillis() + SESSION_MILLIS;
        DIALOGUE_SESSIONS.put(player.getUUID(), new DialogueSession(action, expiresAt));
        ABILITY_SESSIONS.remove(player.getUUID());
        String encoded = "DIALOGUE\t" + speakerKey + "\t" + String.join("|", lineKeys)
                + "\t" + action + "\t" + actionLabelKey;
        ServerPlayNetworking.send(player, new RefugioUiPayload(encoded));
    }

    private static void handle(ServerPlayer player, String data) {
        String[] parts = data.split("\\t", -1);
        if (parts.length == 0) return;
        switch (parts[0]) {
            case "BUY" -> {
                if (parts.length < 2 || !hasLiveAbilitySession(player)) return;
                RefugioAbilityData.PurchaseResult result = RefugioAbilityData.buyNextRank(player, parts[1]);
                sendAbilityState(player, feedbackKey(result));
            }
            case "TOGGLE" -> {
                if (parts.length < 2 || !hasLiveAbilitySession(player)) return;
                String id = parts[1];
                boolean next = !RefugioAbilityData.isActive(player, id);
                String feedback = RefugioAbilityData.setActive(player, id, next)
                        ? (next ? "gui.refugio.abilities.feedback.enabled" : "gui.refugio.abilities.feedback.disabled")
                        : "gui.refugio.abilities.feedback.not_learned";
                sendAbilityState(player, feedback);
            }
            case "ABILITY_CLOSE" -> ABILITY_SESSIONS.remove(player.getUUID());
            case "DIALOGUE_ACTION" -> handleDialogueAction(player, parts);
            case "DIALOGUE_CLOSE" -> DIALOGUE_SESSIONS.remove(player.getUUID());
            default -> { }
        }
    }

    private static boolean hasLiveAbilitySession(ServerPlayer player) {
        Long expiry = ABILITY_SESSIONS.get(player.getUUID());
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            ABILITY_SESSIONS.remove(player.getUUID());
            return false;
        }
        return player.isAlive();
    }

    private static void handleDialogueAction(ServerPlayer player, String[] parts) {
        if (parts.length < 2) return;
        DialogueSession session = DIALOGUE_SESSIONS.remove(player.getUUID());
        if (session == null || System.currentTimeMillis() > session.expiresAt() || !session.action().equals(parts[1])) return;
        switch (session.action()) {
            case "OPEN_SKILLS" -> openAbilities(player);
            case "MOVE_KAEL_TO_CASTLE" -> {
                if (RefugioNPCs.moveKaelToCastle(player)) RefugioStory.acknowledgeArrivalDialogue(player);
            }
            case "CASTLE_INTRO_DONE" -> {
                RefugioStory.acknowledgeCastleDialogue(player);
                openAbilities(player);
            }
            case "STARTER_PACKAGE_ACK" -> RefugioStory.acknowledgeStarterPackageDialogue(player);
            case "OPEN_SHOP" -> RefugioMenu.openShop(player);
            case "OPEN_QUESTS" -> RefugioMenu.openQuests(player);
            case "OPEN_GARDEN_QUESTS" -> RefugioMenu.openSideQuests(player, RefugioSideQuests.Track.GARDEN);
            case "OPEN_EXPEDITION_QUESTS" -> RefugioMenu.openSideQuests(player, RefugioSideQuests.Track.EXPEDITION);
            case "DISMISS" -> { }
            default -> { }
        }
    }

    private static void sendAbilityState(ServerPlayer player, String feedbackKey) {
        List<RefugioAbilityData.Skill> skills = RefugioAbilityData.skills();
        String ranks = skills.stream().map(skill -> Integer.toString(RefugioAbilityData.rank(player, skill.id())))
                .collect(Collectors.joining(","));
        int activeMask = 0;
        for (int i = 0; i < skills.size(); i++) {
            if (RefugioAbilityData.isActive(player, skills.get(i).id())) activeMask |= (1 << i);
        }
        String payload = "ABILITY\t" + RefugioPlayerData.getCoins(player) + "\t"
                + RefugioAbilityData.freeChoice(player) + "\t" + ranks + "\t" + activeMask + "\t" + feedbackKey;
        ServerPlayNetworking.send(player, new RefugioUiPayload(payload));
    }

    private static String feedbackKey(RefugioAbilityData.PurchaseResult result) {
        return switch (result) {
            case FREE -> "gui.refugio.abilities.feedback.free";
            case PURCHASED -> "gui.refugio.abilities.feedback.purchased";
            case NO_COINS -> "gui.refugio.abilities.feedback.no_coins";
            case LOCKED -> "gui.refugio.abilities.feedback.locked";
            case MAX_RANK -> "gui.refugio.abilities.feedback.max_rank";
            case CHOOSE_FREE_FIRST -> "gui.refugio.abilities.feedback.choose_free";
            case INVALID -> "gui.refugio.abilities.feedback.invalid";
        };
    }

    private record DialogueSession(String action, long expiresAt) { }
}
