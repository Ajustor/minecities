package com.ajustor.minecities.town;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Les mods de protection (brief 08, MC-85) : avant de casser un bloc, un citoyen demande la permission
 * comme le ferait son maire, par l'événement qu'écoutent ces mods. Un terrain protégé contre le maire
 * l'est aussi contre ses citoyens.
 */
public final class Protection {
	private Protection() {
	}

	/** Un citoyen de cette ville peut-il casser ce bloc ? */
	public static boolean mayBreak(ServerLevel level, Town town, BlockPos pos) {
		String name = town.mayorName().isEmpty() ? "MineCities" : town.mayorName();
		FakePlayer mayor = FakePlayer.get(level, new GameProfile(town.mayor(), name));
		return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, mayor, pos, level.getBlockState(pos),
				level.getBlockEntity(pos));
	}
}
