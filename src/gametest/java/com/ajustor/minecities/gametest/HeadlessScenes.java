package com.ajustor.minecities.gametest;

import com.ajustor.minecities.town.Towns;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Les scènes sur le seul serveur, sans fenêtre : bien plus vite qu'avec le client, et c'est la suite
 * d'avant envoi. Toutes se jouent l'une après l'autre dans le même monde, remis à neuf entre deux
 * (villes dissoutes, créatures retirées, terrain rasé) ; les échecs sont tous rapportés à la fin.
 *
 * <p>{@code ./gradlew runGameTest} ; {@code -PminecitiesGametest=city,work} pour n'en jouer que
 * quelques-unes.
 */
public class HeadlessScenes {
	private static final Logger LOG = LoggerFactory.getLogger("minecities");
	/** Le terrain des scènes, remis à neuf entre deux : de quoi couvrir la plus grande. */
	private static final int HALF = 72;
	private static final int GROUND = 99;
	private static final int SKY = 140;

	private static ServerStage stage;

	static {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (stage != null) {
				stage.endTick();
			}
		});
	}

	private volatile boolean done;
	private final List<String> failures = new ArrayList<>();

	@GameTest(maxTicks = 2_000_000)
	public void scenes(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		// Un joueur factice : les scènes fondent leurs villes et déclarent leurs maisons en son nom.
		helper.makeMockServerPlayerInLevel();
		stage = new ServerStage(server);
		Thread thread = new Thread(() -> {
			try {
				for (Scene scene : Scene.selected()) {
					if (scene.visual()) {
						continue;
					}
					long start = System.nanoTime();
					try {
						stage.run(HeadlessScenes::reset);
						scene.play(stage);
						LOG.info("Scène {} réussie en {} s", scene.name(), (System.nanoTime() - start) / 1_000_000_000L);
					} catch (AssertionError | RuntimeException e) {
						LOG.error("Scène {} en échec", scene.name(), e);
						synchronized (failures) {
							failures.add(scene.name() + " : " + e.getMessage());
						}
					}
				}
			} finally {
				done = true;
				stage.finish();
			}
		}, "Scènes MineCities");
		thread.setDaemon(true);
		thread.start();
		helper.onEachTick(() -> {
			if (!done) {
				return;
			}
			synchronized (failures) {
				if (!failures.isEmpty()) {
					helper.fail(failures.size() + " scène(s) en échec :\n" + String.join("\n", failures));
				}
			}
			helper.succeed();
		});
	}

	/** Remet le monde à neuf pour la scène suivante. */
	private static void reset(MinecraftServer server) {
		Towns towns = Towns.get(server);
		new ArrayList<>(towns.all()).forEach(towns::remove);
		ServerLevel level = server.overworld();
		for (int cx = -HALF >> 4; cx <= HALF >> 4; cx++) {
			for (int cz = -HALF >> 4; cz <= HALF >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		List<Entity> entities = new ArrayList<>();
		level.getAllEntities().forEach(entities::add);
		for (Entity entity : entities) {
			if (!(entity instanceof Player)) {
				entity.discard();
			}
		}
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = -HALF; z <= HALF; z++) {
				for (int y = GROUND; y <= SKY; y++) {
					BlockState want = y == GROUND ? grass : air;
					if (level.getBlockState(pos.set(x, y, z)) != want) {
						level.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					}
				}
			}
		}
		// Le joueur factice, au-dessus du terrain : les créatures alentour restent actives.
		server.getPlayerList().getPlayers().forEach(p -> p.teleportTo(0.5, SKY, 0.5));
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamemode spectator @a");
	}
}
