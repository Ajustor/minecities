package com.ajustor.minecities.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** La scène dans le vrai jeu, avec sa fenêtre : ce qu'il faut pour les captures. */
final class ClientStage implements Stage {
	/** {@code -PminecitiesFilm} : les films image par image (lents : une capture toutes les 20 ticks). */
	private static final boolean FILM = Boolean.getBoolean("minecities.film");

	private final ClientGameTestContext context;
	private final TestSingleplayerContext sp;

	ClientStage(ClientGameTestContext context, TestSingleplayerContext sp) {
		this.context = context;
		this.sp = sp;
	}

	@Override
	public <T> T compute(Function<MinecraftServer, T> action) {
		return sp.getServer().computeOnServer(action::apply);
	}

	@Override
	public void command(String command) {
		sp.getServer().runCommand(command);
	}

	@Override
	public void waitTicks(int ticks) {
		context.waitTicks(ticks);
	}

	@Override
	public void waitFor(Predicate<MinecraftServer> done, int ticks) {
		sp.getServer().waitFor(done, ticks);
	}

	@Override
	public boolean waitUntil(Predicate<MinecraftServer> done, int ticks) {
		try {
			sp.getServer().waitFor(done, ticks);
			return true;
		} catch (AssertionError e) {
			return false;
		}
	}

	@Override
	public void shot(String name) {
		context.takeScreenshot(name);
	}

	@Override
	public void client(Consumer<Minecraft> action) {
		context.runOnClient(action::accept);
	}

	@Override
	public void look(double x, double y, double z, double tx, double ty, double tz) {
		double dx = tx - x, dy = ty - y, dz = tz - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
		command(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", x, y - 1.62, z, yaw, pitch));
	}

	@Override
	public void film(String name, int frames, int interval, Predicate<MinecraftServer> done) {
		if (!FILM) {
			waitUntil(done, frames * interval);
			return;
		}
		for (int i = 0; i < frames; i++) {
			shot("film_%s_%03d".formatted(name, i));
			if (compute(done::test)) {
				return;
			}
			waitTicks(interval);
		}
	}
}
