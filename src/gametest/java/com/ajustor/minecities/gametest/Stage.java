package com.ajustor.minecities.gametest;

import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Le théâtre d'une scène : le vrai jeu avec sa fenêtre ({@link ClientStage}, pour les captures), ou le
 * seul serveur, sans rendu, qui enchaîne les ticks aussi vite qu'il peut ({@link ServerStage}). Une
 * scène écrite pour l'un se joue telle quelle sur l'autre ; sans fenêtre, ce qui ne sert qu'à l'image
 * (captures, caméra, écrans) ne fait rien.
 */
public interface Stage {
	/** Calcule sur le fil du serveur, entre deux ticks. */
	<T> T compute(Function<MinecraftServer, T> action);

	/** Agit sur le fil du serveur, entre deux ticks. */
	default void run(Consumer<MinecraftServer> action) {
		compute(server -> {
			action.accept(server);
			return null;
		});
	}

	/** Une commande, comme depuis la console du serveur. */
	void command(String command);

	/** Laisse passer ces ticks. */
	void waitTicks(int ticks);

	/** Attend la condition, au plus ces ticks ; sinon échoue (« Timed out waiting for predicate »). */
	void waitFor(Predicate<MinecraftServer> done, int ticks);

	/** Comme {@link #waitFor}, sans échouer : vrai si la condition est venue à temps. */
	boolean waitUntil(Predicate<MinecraftServer> done, int ticks);

	/** Une capture d'écran (dans build/run/clientGameTest/screenshots). */
	void shot(String name);

	/** Agit sur le client : la caméra, l'interface, les écrans (rien sans fenêtre). */
	void client(Consumer<net.minecraft.client.Minecraft> action);

	/** Se poste à (x, y, z), les yeux vers (tx, ty, tz) (rien sans fenêtre). */
	void look(double x, double y, double z, double tx, double ty, double tz);

	/**
	 * Filme : une capture toutes les {@code interval} ticks, jusqu'à {@code frames} images ou jusqu'à
	 * ce que {@code done} soit vrai ; tools/captures/animate.py en fait une vidéo animée. Sans
	 * {@code -PminecitiesFilm} (ou sans fenêtre), il attend seulement, aussi longtemps.
	 */
	void film(String name, int frames, int interval, Predicate<MinecraftServer> done);
}
