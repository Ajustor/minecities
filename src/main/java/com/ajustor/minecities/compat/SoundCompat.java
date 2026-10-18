package com.ajustor.minecities.compat;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Les sons du jeu dont le type change d'une version de Minecraft à l'autre (un son simple devenu une
 * référence de registre), rendus en {@link SoundEvent} quelle que soit la version.
 */
public final class SoundCompat {
	//? if >=26.3 {
	/*public static final SoundEvent AXE_STRIP = SoundEvents.AXE_STRIP.value();
	*///?} else {
	public static final SoundEvent AXE_STRIP = SoundEvents.AXE_STRIP;
	//?}

	private SoundCompat() {
	}
}
