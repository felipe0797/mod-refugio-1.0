package com.felpz.refugio.client;

import com.felpz.refugio.ability.ActivateMovementPayload;
import com.felpz.refugio.ui.RefugioUiPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class RefugioClient implements ClientModInitializer {
    private static RefugioAbilityScreen currentAbilityScreen;
    private static final KeyMapping MOVEMENT_SKILL_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.refugio.activate_movement", InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_G, KeyMapping.Category.MOVEMENT));

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
                        if (currentAbilityScreen != null) {
                            currentAbilityScreen.updateState(data);
                        } else {
                            currentAbilityScreen = new RefugioAbilityScreen(data);
                            context.client().setScreenAndShow(currentAbilityScreen);
                        }
                    } else if (data.startsWith("DIALOGUE\t")) {
                        currentAbilityScreen = null;
                        context.client().setScreenAndShow(new RefugioDialogueScreen(data));
                    }
                }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) currentAbilityScreen = null;
            while (MOVEMENT_SKILL_KEY.consumeClick()) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null) {
                    ClientPlayNetworking.send(new ActivateMovementPayload());
                }
            }
        });
    }

    public static void clearAbilityScreen(RefugioAbilityScreen screen) {
        if (currentAbilityScreen == screen) currentAbilityScreen = null;
    }
}
