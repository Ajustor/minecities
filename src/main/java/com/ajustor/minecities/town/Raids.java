package com.ajustor.minecities.town;

import com.ajustor.minecities.citizen.Action;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.mixin.MobAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Les raids de pillards, à la manière des raids vanilla : des vagues de pillards et de vindicateurs
 * arrivent du bord du territoire, marchent sur l'hôtel de ville, s'en prennent aux citoyens et pillent
 * le grenier. Plus la ville a d'habitants et de maisons, plus il y a de vagues. Les gardes et les
 * archers défendent ; les autres se mettent à l'abri. Une barre de boss montre la vague en cours ; la
 * victoire rapporte des émeraudes au grenier.
 */
public final class Raids {
	/**
	 * Le nombre de vagues au plus. Le seuil de population, l'espacement des raids et leur fréquence
	 * se règlent dans la configuration (par défaut : 8 habitants, trois jours, une chance sur trois).
	 */
	public static final int MAX_WAVES = 7;
	/** Entre deux vagues, dix secondes de répit. */
	private static final int RESPITE = 200;
	/** Un raid qui traîne (pillards perdus) s'achève au bout d'une journée. */
	private static final long TIMEOUT = 24000L;

	private static final Map<UUID, Raid> ACTIVE = new HashMap<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();
	private static int ticks;

	/** Un raid en cours sur une ville. */
	private static final class Raid {
		final UUID town;
		final int waves;
		int wave;
		int waveSize;
		final Set<UUID> raiders = new HashSet<>();
		final ServerBossEvent bar;
		long nextWave;
		long started;
		boolean pillaging;

		Raid(UUID town, int waves, long now) {
			this.town = town;
			this.waves = waves;
			this.started = now;
			this.nextWave = now;
			this.bar = new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.RED,
					BossEvent.BossBarOverlay.NOTCHED_10);
		}
	}

	private Raids() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Raids::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.values().forEach(r -> r.bar.removeAllPlayers());
			ACTIVE.clear();
			LAST.clear();
		});
	}

	public static boolean underAttack(UUID town) {
		return ACTIVE.containsKey(town);
	}

	/** Le nombre de vagues que vaut une ville : plus elle est peuplée et bâtie, plus il y en a. */
	public static int waves(int population, int homes) {
		return Math.clamp(1 + population / 8 + homes / 4, 1, MAX_WAVES);
	}

	/** La taille d'une vague : elle croît avec le rang de la vague et la population. */
	public static int waveSize(int wave, int population) {
		return Math.min(12, 2 + wave + population / 10);
	}

	/** À chaque relevé de la ville (toutes les 5 secondes) : un raid commence-t-il cette nuit ? */
	static void consider(ServerLevel level, Town town) {
		com.ajustor.minecities.McConfig config = com.ajustor.minecities.McConfig.get();
		if (!config.raids || ACTIVE.containsKey(town.id()) || town.population() < config.raidMinPopulation) {
			return;
		}
		long spacing = config.raidSpacingDays * 24000L;
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		long now = level.getGameTime();
		boolean dusk = time >= 13000 && time < 13100;
		if (dusk && now - LAST.getOrDefault(town.id(), now - spacing) >= spacing
				&& level.getRandom().nextInt(Math.max(1, config.raidOdds)) == 0) {
			start(level, town);
		}
	}

	/** Lance un raid sur cette ville (la commande d'administration le peut à tout moment). */
	public static boolean start(ServerLevel level, Town town) {
		if (ACTIVE.containsKey(town.id())) {
			return false;
		}
		Raid raid = new Raid(town.id(), waves(town.population(), town.homes().size()), level.getGameTime());
		ACTIVE.put(town.id(), raid);
		level.playSound(null, town.hall(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 64.0f, 1.0f);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.minecities.raid.start",
				town.name(), raid.waves).withStyle(ChatFormatting.RED), false);
		return true;
	}

	public static void stop(MinecraftServer server, UUID town) {
		Raid raid = ACTIVE.remove(town);
		if (raid != null) {
			raid.bar.removeAllPlayers();
			for (UUID id : raid.raiders) {
				for (ServerLevel level : server.getAllLevels()) {
					Entity raider = level.getEntity(id);
					if (raider != null) {
						raider.discard();
					}
				}
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (++ticks % 20 != 0 || ACTIVE.isEmpty()) {
			return;
		}
		Towns towns = Towns.get(server);
		for (Raid raid : List.copyOf(ACTIVE.values())) {
			Optional<Town> found = towns.byId(raid.town);
			ServerLevel level = found.map(t -> server.getLevel(t.dimension())).orElse(null);
			if (found.isEmpty() || level == null) {
				stop(server, raid.town);
				continue;
			}
			step(level, found.get(), raid);
		}
	}

	private static void step(ServerLevel level, Town town, Raid raid) {
		long now = level.getGameTime();
		raid.raiders.removeIf(id -> !(level.getEntity(id) instanceof Mob mob) || !mob.isAlive());
		// La barre : ceux qui sont sur le territoire la voient.
		for (ServerPlayer player : level.players()) {
			if (town.contains(level.dimension(), player.blockPosition())) {
				raid.bar.addPlayer(player);
			} else {
				raid.bar.removePlayer(player);
			}
		}
		if (now - raid.started > TIMEOUT) {
			level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
					"message.minecities.raid.fled", town.name()).withStyle(ChatFormatting.GOLD), false);
			LAST.put(town.id(), now);
			stop(level.getServer(), town.id());
			return;
		}
		if (raid.raiders.isEmpty()) {
			if (raid.wave >= raid.waves) {
				victory(level, town, raid);
				return;
			}
			if (now >= raid.nextWave) {
				spawnWave(level, town, raid);
			} else {
				raid.bar.setName(Component.translatable("bossbar.minecities.raid.respite", raid.wave, raid.waves));
				raid.bar.setProgress(0);
			}
			return;
		}
		raid.bar.setName(Component.translatable("bossbar.minecities.raid", town.name(), raid.wave, raid.waves));
		raid.bar.setProgress(raid.raiders.size() / (float) Math.max(1, raid.waveSize));
		raid.nextWave = now + RESPITE;
		pillage(level, town, raid);
	}

	/** Une vague sort du bord du territoire, d'un côté tiré au sort. */
	private static void spawnWave(ServerLevel level, Town town, Raid raid) {
		raid.wave++;
		raid.waveSize = waveSize(raid.wave, town.population());
		double angle = level.getRandom().nextDouble() * Math.PI * 2;
		int distance = Math.max(24, town.radius() - 8);
		BlockPos edge = town.hall().offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
		for (int i = 0; i < raid.waveSize; i++) {
			EntityType<? extends Raider> type = i % 3 == 2 ? EntityTypes.VINDICATOR : EntityTypes.PILLAGER;
			Raider raider = type.create(level, EntitySpawnReason.EVENT);
			if (raider == null) {
				continue;
			}
			int x = edge.getX() + level.getRandom().nextInt(7) - 3;
			int z = edge.getZ() + level.getRandom().nextInt(7) - 3;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			raider.snapTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360, 0);
			raider.finalizeSpawn(level, level.getCurrentDifficultyAt(raider.blockPosition()), EntitySpawnReason.EVENT,
					null);
			raider.setCanJoinRaid(false);
			raider.setPersistenceRequired();
			raider.getGoalSelector().addGoal(4, new MarchOnHall(raider, town.hall()));
			((MobAccessor) raider).minecities$targetSelector().addGoal(3,
					new NearestAttackableTargetGoal<>(raider, CitizenEntity.class, true));
			level.addFreshEntity(raider);
			raid.raiders.add(raider.getUUID());
		}
		level.playSound(null, edge, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 64.0f, 1.0f);
		TownService.notify(level.getServer(), town, Component.translatable("message.minecities.raid.wave", raid.wave,
				raid.waves, raid.waveSize));
	}

	/** Les pillards qui atteignent l'hôtel de ville vident le grenier, une bouchée à la fois. */
	private static void pillage(ServerLevel level, Town town, Raid raid) {
		TownService.granary(level, town).ifPresent(granary -> {
			for (UUID id : raid.raiders) {
				if (level.getEntity(id) instanceof Mob raider
						&& raider.distanceToSqr(town.hall().getX() + 0.5, town.hall().getY(), town.hall().getZ() + 0.5) < 9) {
					ItemStack stolen = Stock.take(granary, s -> !s.isEmpty(), 4);
					if (!stolen.isEmpty()) {
						level.sendParticles(ParticleTypes.ANGRY_VILLAGER, raider.getX(), raider.getY() + 2, raider.getZ(), 2,
								0.3, 0.2, 0.3, 0);
						if (!raid.pillaging) {
							raid.pillaging = true;
							level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
									"message.minecities.raid.pillage", town.name()).withStyle(ChatFormatting.RED), false);
						}
					}
				}
			}
		});
	}

	private static void victory(ServerLevel level, Town town, Raid raid) {
		int emeralds = raid.waves * 2;
		TownService.granary(level, town).ifPresent(granary -> Stock.insert(granary, new ItemStack(Items.EMERALD, emeralds)));
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.minecities.raid.victory",
				town.name(), raid.waves, emeralds).withStyle(ChatFormatting.GREEN), false);
		level.playSound(null, town.hall(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.NEUTRAL, 2.0f, 1.0f);
		for (UUID id : town.residents().keySet()) {
			if (level.getEntity(id) instanceof CitizenEntity citizen) {
				citizen.act(Action.CHEER, 100);
				citizen.say("victory", Component.translatable("speech.minecities.victory"));
			}
		}
		// La victoire se fête.
		Festivals.celebrate(level, town, "message.minecities.festival.victory");
		LAST.put(town.id(), level.getGameTime());
		raid.bar.removeAllPlayers();
		ACTIVE.remove(town.id());
	}

	/** Les pillards marchent sur l'hôtel de ville quand ils n'ont personne à attaquer. */
	private static final class MarchOnHall extends Goal {
		private final Mob raider;
		private final BlockPos hall;
		private int repath;

		MarchOnHall(Mob raider, BlockPos hall) {
			this.raider = raider;
			this.hall = hall;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return raider.getTarget() == null
					&& raider.distanceToSqr(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5) > 4;
		}

		@Override
		public void tick() {
			if (--repath <= 0 || raider.getNavigation().isDone()) {
				repath = 40;
				raider.getNavigation().moveTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5, 1.0);
			}
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}
	}

	/** Pour les tests : les pillards du raid en cours. */
	public static List<UUID> raiders(UUID town) {
		Raid raid = ACTIVE.get(town);
		return raid == null ? List.of() : new ArrayList<>(raid.raiders);
	}

	/** Pour les tests : la vague en cours et le nombre de vagues. */
	public static int[] progress(UUID town) {
		Raid raid = ACTIVE.get(town);
		return raid == null ? new int[]{0, 0} : new int[]{raid.wave, raid.waves};
	}
}
