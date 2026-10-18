package com.felpz.refugio.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Client-only bridge to the active screen field in Minecraft 26.2's Gui. */
@Mixin(Gui.class)
public interface RefugioGuiAccessor {
    @Accessor("screen")
    Screen refugio$getScreen();
}
