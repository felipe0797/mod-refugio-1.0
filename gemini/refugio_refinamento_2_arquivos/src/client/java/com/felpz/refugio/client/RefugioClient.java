package com.felpz.refugio.client;

import com.felpz.refugio.ability.RefugioAbilityData;
import com.felpz.refugio.ui.RefugioUiPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class RefugioClient implements ClientModInitializer {
    private static RefugioAbilityScreen currentAbilityScreen;
    private static RefugioShopScreen currentShopScreen;
    private static RefugioQuestScreen currentQuestScreen;
    private static long lastReserveFlashUntilMs;

    private static final KeyMapping.Category REFUGIO_KEY_CATEGORY =
            KeyMapping.Category.register(Identifier.parse("refugio:refugio"));
    private static final KeyMapping JUMP_SKILL_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.refugio.activate_jump", InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_SPACE, REFUGIO_KEY_CATEGORY));
    private static final KeyMapping BURST_SKILL_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.refugio.activate_burst", InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_LEFT_CONTROL, REFUGIO_KEY_CATEGORY));

    private static boolean wasJumpKeyDown;
    private static boolean wasBurstKeyDown;
    private static int clientSuperJumpRank;
    private static int clientDoubleJumpRank;
    private static int clientBurstRank;
    private static boolean clientSuperJumpCharging;
    private static boolean clientSuperJumpWaitingAirOrLand;
    private static boolean clientSuperJumpLeftGroundSinceLaunch;
    private static int clientSuperJumpLaunchTicks;
    private static String lastClientDimension = "";

    @Override
    public void onInitializeClient() {
        boolean appearancePackRegistered = ResourceLoader.registerBuiltinPack(
                Identifier.parse("refugio:npc_skins"),
                FabricLoader.getInstance().getModContainer("refugio").orElseThrow(),
                Component.translatable("resourcePack.refugio.npc_skins"),
                PackActivationType.ALWAYS_ENABLED);
        if (!appearancePackRegistered) {
            throw new IllegalStateException("Não foi possível registrar o pack de aparência dos NPCs do Refúgio.");
        }

        ClientPlayNetworking.registerGlobalReceiver(RefugioUiPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    String data = payload.data();
                    if (data.startsWith("MOVEMENT_STATE\t")) {
                        updateMovementStateFromMask(data);
                    } else if (data.startsWith("ABILITY\t")) {
                        updateMovementStateFromAbilitySnapshot(data);
                        currentShopScreen = null;
                        currentQuestScreen = null;
                        if (currentAbilityScreen != null) {
                            currentAbilityScreen.updateState(data);
                        } else {
                            currentAbilityScreen = new RefugioAbilityScreen(data);
                            context.client().setScreenAndShow(currentAbilityScreen);
                        }
                    } else if (data.startsWith("DIALOGUE\t")) {
                        currentAbilityScreen = null;
                        currentShopScreen = null;
                        currentQuestScreen = null;
                        context.client().setScreenAndShow(new RefugioDialogueScreen(data));
                    } else if (data.startsWith("SHOP\t")) {
                        currentAbilityScreen = null;
                        currentQuestScreen = null;
                        if (currentShopScreen != null) {
                            currentShopScreen.updateState(data);
                            if (context.client().screen != currentShopScreen) {
                                context.client().setScreenAndShow(currentShopScreen);
                            }
                        } else {
                            currentShopScreen = new RefugioShopScreen(data);
                            context.client().setScreenAndShow(currentShopScreen);
                        }
                    } else if (data.startsWith("QUEST\t")) {
                        currentAbilityScreen = null;
                        currentShopScreen = null;
                        if (currentQuestScreen != null) {
                            currentQuestScreen.updateState(data);
                            if (context.client().screen != currentQuestScreen) {
                                context.client().setScreenAndShow(currentQuestScreen);
                            }
                        } else {
                            currentQuestScreen = new RefugioQuestScreen(data);
                            context.client().setScreenAndShow(currentQuestScreen);
                        }
                    } else if (data.startsWith("LAST_RESERVE_FX")) {
                        lastReserveFlashUntilMs = System.currentTimeMillis() + 1200L;
                    } else if (data.startsWith("SHOP_CLOSED\t")) {
                        String[] parts = data.split("\\t", -1);
                        if (parts.length >= 2) {
                            try {
                                int closedId = Integer.parseInt(parts[1]);
                                if (currentShopScreen != null && currentShopScreen.containerId() == closedId) {
                                    RefugioShopScreen closing = currentShopScreen;
                                    currentShopScreen = null;
                                    if (context.client().screen == closing) {
                                        context.client().setScreenAndShow(null);
                                    }
                                }
                                if (currentQuestScreen != null && currentQuestScreen.containerId() == closedId) {
                                    RefugioQuestScreen closing = currentQuestScreen;
                                    currentQuestScreen = null;
                                    if (context.client().screen == closing) {
                                        context.client().setScreenAndShow(null);
                                    }
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }));

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                resetClientMovementState(false);
                return;
            }
            handleMovementKeyInputs(client);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                currentAbilityScreen = null;
                currentShopScreen = null;
                currentQuestScreen = null;
                resetClientMovementState(false);
                return;
            }
            if (currentShopScreen != null) {
                currentShopScreen.tickScreen(client);
            }
            if (currentQuestScreen != null) {
                currentQuestScreen.tickScreen(client);
            }
        });
    }

    private static void updateMovementStateFromMask(String data) {
        String[] parts = data.split("\\t", -1);
        if (parts.length < 2) return;
        try {
            int mask = Integer.parseInt(parts[1]);
            clientSuperJumpRank = mask & 0xF;
            clientDoubleJumpRank = (mask >> 4) & 0xF;
            clientBurstRank = (mask >> 8) & 0xF;
            if (clientSuperJumpRank <= 0 && clientSuperJumpCharging) {
                clientSuperJumpCharging = false;
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private static void updateMovementStateFromAbilitySnapshot(String data) {
        String[] parts = data.split("\\t", -1);
        if (parts.length < 5) return;
        try {
            String[] rankTokens = parts[3].split(",");
            int activeMask = Integer.parseInt(parts[4]);
            List<RefugioAbilityData.Skill> skills = RefugioAbilityData.skills();
            int sj = 0;
            int dj = 0;
            int burst = 0;
            for (int i = 0; i < Math.min(skills.size(), rankTokens.length); i++) {
                boolean active = (activeMask & (1 << i)) != 0;
                if (!active) continue;
                int r = Math.max(0, Integer.parseInt(rankTokens[i]));
                String id = skills.get(i).id();
                if ("super_jump".equals(id)) sj = r;
                else if ("double_jump".equals(id)) dj = r;
                else if ("burst".equals(id)) burst = r;
            }
            clientSuperJumpRank = sj;
            clientDoubleJumpRank = dj;
            clientBurstRank = burst;
            if (clientSuperJumpRank <= 0) {
                clientSuperJumpCharging = false;
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private static void resetClientMovementState(boolean notifyServerCancel) {
        if (clientSuperJumpCharging && notifyServerCancel && ClientPlayNetworking.canSend(RefugioUiPayload.TYPE)) {
            ClientPlayNetworking.send(new RefugioUiPayload("MOVEMENT_ACTIVATE\tSUPER_JUMP_CANCEL"));
        }
        clientSuperJumpCharging = false;
        clientSuperJumpWaitingAirOrLand = false;
        clientSuperJumpLeftGroundSinceLaunch = false;
        clientSuperJumpLaunchTicks = 0;
        wasJumpKeyDown = false;
        wasBurstKeyDown = false;
    }

    private static void handleMovementKeyInputs(Minecraft client) {
        if (client.screen != null || client.player == null || !client.player.isAlive()) {
            while (JUMP_SKILL_KEY.consumeClick()) { }
            while (BURST_SKILL_KEY.consumeClick()) { }
            resetClientMovementState(true);
            return;
        }

        String dim = client.level != null ? client.level.dimension().toString() : "";
        if (!dim.equals(lastClientDimension)) {
            lastClientDimension = dim;
            resetClientMovementState(true);
        }

        if (clientSuperJumpWaitingAirOrLand) {
            clientSuperJumpLaunchTicks++;
            if (!client.player.onGround()) {
                clientSuperJumpLeftGroundSinceLaunch = true;
            } else if (clientSuperJumpLeftGroundSinceLaunch || clientSuperJumpLaunchTicks > 12) {
                clientSuperJumpWaitingAirOrLand = false;
                clientSuperJumpLeftGroundSinceLaunch = false;
                clientSuperJumpLaunchTicks = 0;
            }
        }

        InputConstants.Key jumpBound = KeyMappingHelper.getBoundKeyOf(JUMP_SKILL_KEY);
        InputConstants.Key vanillaJumpBound = KeyMappingHelper.getBoundKeyOf(client.options.keyJump);
        InputConstants.Key burstBound = KeyMappingHelper.getBoundKeyOf(BURST_SKILL_KEY);
        InputConstants.Key vanillaSprintBound = KeyMappingHelper.getBoundKeyOf(client.options.keySprint);

        boolean jumpDown = isPhysicalKeyDown(jumpBound);
        boolean burstDown = isPhysicalKeyDown(burstBound);
        boolean sameJumpAndBurstKey = !jumpBound.equals(InputConstants.UNKNOWN) && jumpBound.equals(burstBound);

        boolean superJumpActive = clientSuperJumpRank > 0;
        boolean doubleJumpActive = !superJumpActive && clientDoubleJumpRank > 0;
        boolean eligibleForSuperJumpGround = superJumpActive
                && !client.player.isPassenger()
                && !client.player.isInWater()
                && !client.player.isFallFlying()
                && !client.player.getAbilities().flying;

        // Manage vanilla jump key when sharing the same binding (default Space)
        if (jumpBound.equals(vanillaJumpBound)) {
            if (eligibleForSuperJumpGround && (client.player.onGround() || clientSuperJumpCharging)) {
                // Suppress immediate vanilla jump while charging/releasing Super Jump on the ground
                client.options.keyJump.setDown(false);
                while (client.options.keyJump.consumeClick()) { }
            } else {
                client.options.keyJump.setDown(jumpDown);
            }
        }
        // Preserve vanilla sprint state when sharing the same binding (default Left Control)
        if (burstBound.equals(vanillaSprintBound)) {
            client.options.keySprint.setDown(burstDown);
        }

        while (JUMP_SKILL_KEY.consumeClick()) { }
        while (BURST_SKILL_KEY.consumeClick()) { }

        if (superJumpActive) {
            if (!eligibleForSuperJumpGround || !client.player.onGround()) {
                if (clientSuperJumpCharging) {
                    clientSuperJumpCharging = false;
                    ClientPlayNetworking.send(new RefugioUiPayload("MOVEMENT_ACTIVATE\tSUPER_JUMP_CANCEL"));
                }
            } else {
                if (jumpDown && !wasJumpKeyDown && !clientSuperJumpWaitingAirOrLand) {
                    clientSuperJumpCharging = true;
                    ClientPlayNetworking.send(new RefugioUiPayload("MOVEMENT_ACTIVATE\tSUPER_JUMP_START"));
                } else if (!jumpDown && clientSuperJumpCharging) {
                    clientSuperJumpCharging = false;
                    clientSuperJumpWaitingAirOrLand = true;
                    clientSuperJumpLeftGroundSinceLaunch = false;
                    clientSuperJumpLaunchTicks = 0;
                    ClientPlayNetworking.send(new RefugioUiPayload("MOVEMENT_ACTIVATE\tSUPER_JUMP_RELEASE"));
                }
            }
        } else if (doubleJumpActive) {
            if (clientSuperJumpCharging) {
                clientSuperJumpCharging = false;
                ClientPlayNetworking.send(new RefugioUiPayload("MOVEMENT_ACTIVATE\tSUPER_JUMP_CANCEL"));
            }
            if (jumpDown && !wasJumpKeyDown && !client.player.onGround()
                    && !client.player.isPassenger() && !client.player.isInWater()) {
                ClientPlayNetworking.send(new RefugioUiPayload("MOVEMENT_ACTIVATE\tDOUBLE_JUMP"));
            }
        } else {
            if (clientSuperJumpCharging) {
                clientSuperJumpCharging = false;
            }
            if (jumpDown && !wasJumpKeyDown && !jumpBound.equals(vanillaJumpBound)
                    && !(sameJumpAndBurstKey && clientBurstRank > 0)) {
                ClientPlayNetworking.send(new RefugioUiPayload("MOVEMENT_ACTIVATE\tJUMP_EXPLICIT"));
            }
        }

        // Prevent a single shared key from activating both Jump and Burst simultaneously
        boolean jumpConsumedSharedKey = sameJumpAndBurstKey && (superJumpActive || doubleJumpActive);
        if (burstDown && !wasBurstKeyDown && !jumpConsumedSharedKey) {
            String mode = burstBound.equals(vanillaSprintBound) ? "BURST_PASSIVE" : "BURST_EXPLICIT";
            ClientPlayNetworking.send(new RefugioUiPayload("MOVEMENT_ACTIVATE\t" + mode));
        }

        wasJumpKeyDown = jumpDown;
        wasBurstKeyDown = burstDown;
    }

    private static boolean isPhysicalKeyDown(InputConstants.Key key) {
        if (key == null || key.equals(InputConstants.UNKNOWN)) return false;
        long window = GLFW.glfwGetCurrentContext();
        if (window == 0L) return false;
        int code = key.getValue();
        if (code < 0) return false;
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
        }
        return false;
    }

    public static Component jumpKeyDisplayName() {
        return JUMP_SKILL_KEY.getTranslatedKeyMessage();
    }

    public static Component burstKeyDisplayName() {
        return BURST_SKILL_KEY.getTranslatedKeyMessage();
    }

    public static boolean isLastReserveFlashing() {
        return System.currentTimeMillis() < lastReserveFlashUntilMs;
    }

    public static void clearAbilityScreen(RefugioAbilityScreen screen) {
        if (currentAbilityScreen == screen) currentAbilityScreen = null;
    }

    public static void clearShopScreen(RefugioShopScreen screen) {
        if (currentShopScreen == screen) currentShopScreen = null;
    }

    public static void clearQuestScreen(RefugioQuestScreen screen) {
        if (currentQuestScreen == screen) currentQuestScreen = null;
    }
}
