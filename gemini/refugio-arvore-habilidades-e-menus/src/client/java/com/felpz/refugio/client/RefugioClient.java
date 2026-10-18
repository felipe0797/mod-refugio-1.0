package com.felpz.refugio.client;

import com.felpz.refugio.ability.ActivateMovementPayload;
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

import java.lang.reflect.Modifier;

public class RefugioClient implements ClientModInitializer {
    private static RefugioAbilityScreen currentAbilityScreen;
    private static RefugioShopScreen currentShopScreen;
    private static RefugioQuestScreen currentQuestScreen;
    private static long lastReserveFlashUntilMs;

    private static final KeyMapping.Category REFUGIO_KEY_CATEGORY = resolveRefugioKeyCategory();
    private static final KeyMapping JUMP_SKILL_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.refugio.activate_jump", InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_SPACE, REFUGIO_KEY_CATEGORY));
    private static final KeyMapping BURST_SKILL_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.refugio.activate_burst", InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_LEFT_CONTROL, REFUGIO_KEY_CATEGORY));
    private static final KeyMapping MOVEMENT_SKILL_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.refugio.activate_movement", InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_G, REFUGIO_KEY_CATEGORY));

    private static boolean wasJumpKeyDown;
    private static boolean wasBurstKeyDown;
    private static boolean wasMovementKeyDown;

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
                    if (data.startsWith("ABILITY\t")) {
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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                currentAbilityScreen = null;
                currentShopScreen = null;
                currentQuestScreen = null;
                wasJumpKeyDown = false;
                wasBurstKeyDown = false;
                wasMovementKeyDown = false;
                return;
            }
            if (currentShopScreen != null) {
                currentShopScreen.tickScreen(client);
            }
            if (currentQuestScreen != null) {
                currentQuestScreen.tickScreen(client);
            }
            handleMovementKeyInputs(client);
        });
    }

    private static void handleMovementKeyInputs(Minecraft client) {
        if (client.screen != null || client.player == null) {
            while (JUMP_SKILL_KEY.consumeClick()) { }
            while (BURST_SKILL_KEY.consumeClick()) { }
            while (MOVEMENT_SKILL_KEY.consumeClick()) { }
            wasJumpKeyDown = false;
            wasBurstKeyDown = false;
            wasMovementKeyDown = false;
            return;
        }

        InputConstants.Key jumpBound = KeyMappingHelper.getBoundKeyOf(JUMP_SKILL_KEY);
        InputConstants.Key vanillaJumpBound = KeyMappingHelper.getBoundKeyOf(client.options.keyJump);
        InputConstants.Key burstBound = KeyMappingHelper.getBoundKeyOf(BURST_SKILL_KEY);
        InputConstants.Key vanillaSprintBound = KeyMappingHelper.getBoundKeyOf(client.options.keySprint);
        InputConstants.Key movementBound = KeyMappingHelper.getBoundKeyOf(MOVEMENT_SKILL_KEY);

        boolean jumpDown = isPhysicalKeyDown(jumpBound);
        boolean burstDown = isPhysicalKeyDown(burstBound);
        boolean movementDown = isPhysicalKeyDown(movementBound);

        // Preserve vanilla Jump and Sprint states when sharing the same key binding (Space / Left Control)
        if (jumpBound.equals(vanillaJumpBound)) {
            client.options.keyJump.setDown(jumpDown);
        }
        if (burstBound.equals(vanillaSprintBound)) {
            client.options.keySprint.setDown(burstDown);
        }

        while (JUMP_SKILL_KEY.consumeClick()) { }
        while (BURST_SKILL_KEY.consumeClick()) { }
        while (MOVEMENT_SKILL_KEY.consumeClick()) { }

        if (jumpDown && !wasJumpKeyDown) {
            String mode = jumpBound.equals(vanillaJumpBound) ? "JUMP_PASSIVE" : "JUMP_EXPLICIT";
            ClientPlayNetworking.send(new RefugioUiPayload("MOVEMENT_ACTIVATE\t" + mode));
        }
        if (burstDown && !wasBurstKeyDown) {
            String mode = burstBound.equals(vanillaSprintBound) ? "BURST_PASSIVE" : "BURST_EXPLICIT";
            ClientPlayNetworking.send(new RefugioUiPayload("MOVEMENT_ACTIVATE\t" + mode));
        }
        if (movementDown && !wasMovementKeyDown
                && !movementBound.equals(jumpBound) && !movementBound.equals(burstBound)) {
            ClientPlayNetworking.send(new ActivateMovementPayload());
        }

        wasJumpKeyDown = jumpDown;
        wasBurstKeyDown = burstDown;
        wasMovementKeyDown = movementDown;
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

    private static KeyMapping.Category resolveRefugioKeyCategory() {
        Identifier id = Identifier.parse("refugio:refugio");
        try {
            for (var method : KeyMapping.Category.class.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        && method.getReturnType() == KeyMapping.Category.class
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == Identifier.class) {
                    method.setAccessible(true);
                    return (KeyMapping.Category) method.invoke(null, id);
                }
            }
            var ctor = KeyMapping.Category.class.getDeclaredConstructor(Identifier.class);
            ctor.setAccessible(true);
            return ctor.newInstance(id);
        } catch (Throwable ignored) {
            return KeyMapping.Category.MOVEMENT;
        }
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
