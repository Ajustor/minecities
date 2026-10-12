package com.ajustor.minecities.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * La scène sur le seul serveur, sans fenêtre ni rendu. La scène se joue sur son propre fil, au pas
 * du serveur, comme le fait le client de test : tant qu'elle n'attend pas de ticks, le serveur reste
 * arrêté en fin de tick et exécute ce qu'elle lui demande ; quand elle attend, il repart, et la
 * réveille au bon tick. Le serveur de test enchaîne les ticks sans pause : une scène dure le temps
 * de calcul de ses ticks, plus les 50 ms de chacun.
 */
final class ServerStage implements Stage {
	/** Une attente en cours : des ticks à laisser passer, et peut-être une condition à guetter. */
	private static final class Wait {
		int left;
		final Predicate<MinecraftServer> until;
		final CompletableFuture<Boolean> done = new CompletableFuture<>();

		Wait(int left, Predicate<MinecraftServer> until) {
			this.left = left;
			this.until = until;
		}
	}

	private final MinecraftServer server;
	private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
	/** L'attente en cours ; nulle quand la scène a la main (le serveur l'attend). Fil du serveur. */
	private Wait wait;
	private volatile boolean over;

	ServerStage(MinecraftServer server) {
		this.server = server;
	}

	/**
	 * En fin de tick, sur le fil du serveur : avance l'attente en cours ; si elle est finie (ou que la
	 * scène a la main), le serveur fait ce que la scène lui demande, jusqu'à sa prochaine attente.
	 */
	void endTick() {
		if (over) {
			return;
		}
		if (wait != null) {
			wait.left--;
			boolean met;
			try {
				met = wait.until != null && wait.until.test(server);
			} catch (RuntimeException | AssertionError e) {
				wait.done.completeExceptionally(e);
				wait = null;
				serve();
				return;
			}
			if (met || wait.left <= 0) {
				wait.done.complete(met || wait.until == null);
				wait = null;
			} else {
				return;
			}
		}
		serve();
	}

	/** Le serveur, arrêté, exécute les demandes de la scène jusqu'à ce qu'elle attende ou finisse. */
	private void serve() {
		while (wait == null && !over) {
			try {
				tasks.take().run();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	/** La scène est finie (fil de la scène) : le serveur reprend son cours. */
	void finish() {
		tasks.add(() -> over = true);
	}

	@Override
	public <T> T compute(Function<MinecraftServer, T> action) {
		FutureTask<T> task = new FutureTask<>(() -> action.apply(server));
		tasks.add(task);
		try {
			return task.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		} catch (ExecutionException e) {
			throw rethrow(e.getCause());
		}
	}

	private static RuntimeException rethrow(Throwable cause) {
		if (cause instanceof AssertionError error) {
			throw error;
		}
		if (cause instanceof RuntimeException runtime) {
			return runtime;
		}
		return new IllegalStateException(cause);
	}

	@Override
	public void command(String command) {
		run(s -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), command));
	}

	@Override
	public void waitTicks(int ticks) {
		await(new Wait(ticks, null));
	}

	@Override
	public void waitFor(Predicate<MinecraftServer> done, int ticks) {
		if (!waitUntil(done, ticks)) {
			throw new AssertionError("Timed out waiting for predicate");
		}
	}

	@Override
	public boolean waitUntil(Predicate<MinecraftServer> done, int ticks) {
		if (compute(done::test)) {
			return true;
		}
		return await(new Wait(ticks, done));
	}

	private boolean await(Wait next) {
		tasks.add(() -> wait = next);
		try {
			return next.done.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		} catch (ExecutionException e) {
			throw rethrow(e.getCause());
		}
	}

	@Override
	public void shot(String name) {
	}

	@Override
	public void client(Consumer<Minecraft> action) {
	}

	@Override
	public void look(double x, double y, double z, double tx, double ty, double tz) {
	}

	@Override
	public void film(String name, int frames, int interval, Predicate<MinecraftServer> done) {
		waitUntil(done, frames * interval);
	}
}
