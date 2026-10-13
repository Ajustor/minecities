package com.ajustor.minecities.town;

import com.ajustor.minecities.McConfig;
import com.ajustor.minecities.registry.McBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Les catastrophes (MC-14), rares et désactivables ({@code disasters}) :
 * <ul>
 *   <li>l'<b>incendie</b> : par orage, dès la ville, la foudre met le feu au toit d'un bâtiment ; les
 *   citoyens accourent avec les seaux d'eau du grenier ; le feu peut gagner et tuer ;</li>
 *   <li>la <b>disette</b> : un jour durant, les champs ne donnent que de quoi ressemer ;</li>
 *   <li>le <b>tremblement de terre</b> : il frappe toutes les villes à la fois ; dix secondes de
 *   secousses qui font tomber des blocs des bâtiments et blessent, parfois à mort.</li>
 * </ul>
 * Chaque catastrophe est annoncée aux joueurs de la ville, se voit dans le résumé de l'hôtel de
 * ville, et son retour au calme aussi. Pas plus d'une par ville tous les {@code disaster_spacing_days}
 * jours (le séisme, le double), et pas avant que la ville ait cet âge.
 */
public final class Disasters {
	public static final String FIRE = "fire";
	public static final String BLIGHT = "blight";
	public static final String QUAKE = "quake";
	private static final long DAY = 24000L;
	/** Le feu s'éteint de lui-même au bout d'un jour, au plus. */
	private static final long FIRE_DURATION = DAY;
	private static final long BLIGHT_DURATION = DAY;
	/** Dix secondes de secousses. */
	private static final long QUAKE_DURATION = 200;
	/** À chaque contrôle (une minute) : la chance d'un incendie par orage, d'une disette, d'un séisme. */
	public static final double FIRE_ODDS = 0.1;
	public static final double BLIGHT_ODDS = 1.0 / 400;
	public static final double QUAKE_ODDS = 1.0 / 800;

	private static int ticks;

	private Disasters() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Disasters::tick);
	}

	// Le hasard, et ce qui le permet

	/** La chance de cette catastrophe à un contrôle, par ce temps, à ce palier. */
	public static double odds(String kind, boolean thunder, TownTier tier) {
		return switch (kind) {
			case FIRE -> thunder && tier.ordinal() >= TownTier.TOWN.ordinal() ? FIRE_ODDS : 0;
			case BLIGHT -> BLIGHT_ODDS;
			case QUAKE -> QUAKE_ODDS;
			default -> 0;
		};
	}

	/**
	 * Une catastrophe peut-elle frapper cette ville ? Les catastrophes permises, aucune en cours, et
	 * assez de temps depuis la dernière (ou depuis la fondation).
	 */
	public static boolean due(Town town, long now, long spacing) {
		Chronicle c = town.chronicle();
		long since = c.lastDisaster() > 0 ? c.lastDisaster() : town.founded();
		return McConfig.get().disasters && c.disaster(now).isEmpty() && c.disasterKind().isEmpty()
				&& now - since >= spacing;
	}

	private static long spacing() {
		return McConfig.get().disasterSpacingDays * DAY;
	}

	// Le temps qui passe

	private static void tick(MinecraftServer server) {
		ticks++;
		boolean roll = ticks % 1200 == 0;
		if (ticks % 20 != 0) {
			return;
		}
		RandomGenerator random = new java.util.Random(server.overworld().getRandom().nextLong());
		for (Town town : List.copyOf(Towns.get(server).all())) {
			ServerLevel level = server.getLevel(town.dimension());
			if (level == null || !level.isPositionEntityTicking(town.hall())) {
				continue;
			}
			long now = level.getGameTime();
			follow(level, town, now);
			if (roll && due(town, now, spacing())) {
				if (random.nextDouble() < odds(FIRE, level.isThundering(), town.tier())) {
					ignite(level, town, random);
				} else if (random.nextDouble() < odds(BLIGHT, false, town.tier())) {
					blight(level, town);
				}
			}
		}
		if (roll && McConfig.get().disasters && random.nextDouble() < QUAKE_ODDS) {
			long now = server.overworld().getGameTime();
			if (Towns.get(server).all().stream().allMatch(t -> due(t, now, 2 * spacing()))
					&& !Towns.get(server).all().isEmpty()) {
				quake(server, random);
			}
		}
	}

	/** Une catastrophe en cours : les secousses, le feu qui s'éteint, la fin annoncée. */
	public static void follow(ServerLevel level, Town town, long now) {
		Chronicle c = town.chronicle();
		String kind = c.disasterKind();
		if (kind.isEmpty()) {
			return;
		}
		boolean over = now >= c.disasterUntil();
		if (kind.equals(QUAKE) && !over) {
			tremor(level, town, new java.util.Random(level.getRandom().nextLong()));
		}
		if (kind.equals(FIRE) && !over && fires(level, town).isEmpty()) {
			over = true;
		}
		if (over) {
			c.recover();
			Towns.get(level.getServer()).changed();
			announce(level.getServer(), town, Component.translatable("message.minecities.disaster." + kind + ".over")
					.withStyle(ChatFormatting.GREEN));
		}
	}

	// L'incendie

	/** La foudre frappe un bâtiment de la ville et met le feu à son toit. Faux s'il n'y a rien à brûler. */
	public static boolean ignite(ServerLevel level, Town town, RandomGenerator random) {
		List<House> houses = town.houses().stream().filter(h -> h.min().isPresent() && h.max().isPresent()).toList();
		if (houses.isEmpty()) {
			return false;
		}
		House house = houses.get(random.nextInt(houses.size()));
		BlockPos min = house.min().get();
		BlockPos max = house.max().get();
		List<BlockPos> lit = new ArrayList<>();
		for (int i = 0; i < 12 && lit.size() < 3; i++) {
			int x = min.getX() + 1 + random.nextInt(Math.max(1, max.getX() - min.getX() - 1));
			int z = min.getZ() + 1 + random.nextInt(Math.max(1, max.getZ() - min.getZ() - 1));
			roof(level, x, z, max.getY() + 3, min.getY()).filter(p -> !lit.contains(p)).ifPresent(p -> {
				level.setBlockAndUpdate(p, BaseFireBlock.getState(level, p));
				lit.add(p);
			});
		}
		if (lit.isEmpty()) {
			return false;
		}
		LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
		if (bolt != null) {
			BlockPos strike = lit.getFirst();
			bolt.snapTo(strike.getX() + 0.5, strike.getY(), strike.getZ() + 0.5);
			// L'éclair se voit et s'entend ; c'est nous qui mettons le feu, où il faut.
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		long now = level.getGameTime();
		town.chronicle().strike(FIRE, now, now + FIRE_DURATION);
		town.chronicle().burning(min, max);
		Towns.get(level.getServer()).changed();
		announce(level.getServer(), town, Component.translatable("message.minecities.disaster.fire",
				house.plaque().toShortString()).withStyle(ChatFormatting.RED));
		return true;
	}

	/** Le dessus du toit en (x, z) : la case d'air au-dessus du plus haut bloc, s'il y en a. */
	private static Optional<BlockPos> roof(ServerLevel level, int x, int z, int top, int bottom) {
		for (int y = top; y > bottom; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!level.getBlockState(pos).isAir()) {
				BlockPos above = pos.above();
				return level.getBlockState(above).isAir() ? Optional.of(above) : Optional.empty();
			}
		}
		return Optional.empty();
	}

	/** Les flammes du bâtiment en feu (et de ses abords). */
	public static List<BlockPos> fires(ServerLevel level, Town town) {
		Chronicle c = town.chronicle();
		List<BlockPos> fires = new ArrayList<>();
		if (c.fireMin().isEmpty() || c.fireMax().isEmpty()) {
			return fires;
		}
		for (BlockPos pos : BlockPos.betweenClosed(c.fireMin().get().offset(-3, -1, -3), c.fireMax().get().offset(3, 4, 3))) {
			if (level.isLoaded(pos) && level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE)) {
				fires.add(pos.immutable());
			}
		}
		return fires;
	}

	/** Un seau d'eau jeté là : les flammes alentour (un bloc autour) s'éteignent. */
	public static int douse(ServerLevel level, BlockPos at) {
		int out = 0;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
			if (level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE)) {
				level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				out++;
			}
		}
		if (out > 0) {
			level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.0f);
			level.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.6, 0.4,
					0.6, 0.02);
		}
		return out;
	}

	// La disette

	/** Les récoltes sont perdues pour un jour. */
	public static void blight(ServerLevel level, Town town) {
		long now = level.getGameTime();
		town.chronicle().strike(BLIGHT, now, now + BLIGHT_DURATION);
		Towns.get(level.getServer()).changed();
		announce(level.getServer(), town, Component.translatable("message.minecities.disaster.blight")
				.withStyle(ChatFormatting.GOLD));
	}

	/** Les champs de la ville ne donnent rien (que de quoi ressemer) ? */
	public static boolean blighted(Town town, long now) {
		return town.chronicle().disaster(now).filter(BLIGHT::equals).isPresent();
	}

	// Le tremblement de terre

	/** La terre tremble sous toutes les villes à la fois. */
	public static void quake(MinecraftServer server, RandomGenerator random) {
		server.getPlayerList().broadcastSystemMessage(Component.translatable("message.minecities.disaster.quake.world")
				.withStyle(ChatFormatting.RED), false);
		for (Town town : Towns.get(server).all()) {
			ServerLevel level = server.getLevel(town.dimension());
			if (level == null) {
				continue;
			}
			long now = level.getGameTime();
			town.chronicle().strike(QUAKE, now, now + QUAKE_DURATION);
			announce(server, town, Component.translatable("message.minecities.disaster.quake").withStyle(ChatFormatting.RED));
		}
		Towns.get(server).changed();
	}

	/**
	 * Une secousse : un grondement, de la poussière, deux blocs qui tombent de bâtiments au hasard (ni
	 * plaque, ni lit, ni hôtel de ville), et des coups pour qui est dehors.
	 */
	public static void tremor(ServerLevel level, Town town, RandomGenerator random) {
		BlockPos hall = town.hall();
		level.playSound(null, hall, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 0.6f, 0.4f);
		List<House> houses = town.houses().stream().filter(h -> h.min().isPresent() && h.max().isPresent()).toList();
		for (int i = 0; i < 2 && !houses.isEmpty(); i++) {
			House house = houses.get(random.nextInt(houses.size()));
			BlockPos min = house.min().get();
			BlockPos max = house.max().get();
			BlockPos pos = new BlockPos(min.getX() + random.nextInt(max.getX() - min.getX() + 1),
					min.getY() + 1 + random.nextInt(Math.max(1, max.getY() - min.getY())),
					min.getZ() + random.nextInt(max.getZ() - min.getZ() + 1));
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && level.isLoaded(pos) && !state.is(McBlocks.HOUSE_PLAQUE) && !state.is(McBlocks.TOWN_HALL)
					&& !state.is(McBlocks.TOWN_ANNEX)
					&& !(state.getBlock() instanceof BedBlock) && state.getDestroySpeed(level, pos) >= 0) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5,
						pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
				level.destroyBlock(pos, true);
			}
		}
		for (var citizen : level.getEntitiesOfClass(com.ajustor.minecities.citizen.CitizenEntity.class,
				new net.minecraft.world.phys.AABB(hall).inflate(town.radius(), 32, town.radius()),
				c -> c.townId().filter(town.id()::equals).isPresent())) {
			if (random.nextDouble() < 0.3) {
				citizen.hurtServer(level, level.damageSources().generic(), 1 + random.nextInt(3));
			}
		}
		for (ServerPlayer player : level.players()) {
			if (town.contains(level.dimension(), player.blockPosition()) && random.nextDouble() < 0.3) {
				player.hurtServer(level, level.damageSources().generic(), 1);
			}
		}
	}

	// L'annonce

	/** Aux joueurs de la ville, et à ceux qui sont sur son territoire. */
	private static void announce(MinecraftServer server, Town town, Component message) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (town.isMember(player.getUUID()) || town.contains(player.level().dimension(), player.blockPosition())) {
				player.sendSystemMessage(Component.translatable("message.minecities.prefix", town.name())
						.withStyle(ChatFormatting.DARK_AQUA).append(message));
			}
		}
	}
}
