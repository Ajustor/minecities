package com.ajustor.minecities.build;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Un chantier de la ville : un schéma, un style, une origine et une rotation, où en est le
 * bâtisseur, et l'emprise (pour retrouver le bâtiment quand on voudra l'agrandir). Un chantier
 * fini reste connu de la ville.
 */
public final class Construction {
	public static final Codec<Construction> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(c -> c.id),
			Identifier.CODEC.fieldOf("blueprint").forGetter(c -> c.blueprint),
			Codec.STRING.fieldOf("style").forGetter(c -> c.style.serializedName()),
			BlockPos.CODEC.fieldOf("origin").forGetter(c -> c.origin),
			Rotation.CODEC.fieldOf("rotation").forGetter(c -> c.rotation),
			BlockPos.CODEC.fieldOf("min").forGetter(c -> c.min),
			BlockPos.CODEC.fieldOf("max").forGetter(c -> c.max),
			Codec.INT.optionalFieldOf("step", 0).forGetter(c -> c.step),
			Codec.BOOL.optionalFieldOf("done", false).forGetter(c -> c.done),
			BlockPos.CODEC.listOf().optionalFieldOf("scaffolding", List.of()).forGetter(c -> List.copyOf(c.scaffolding)),
			BlockPos.CODEC.optionalFieldOf("clear_min").forGetter(c -> c.clearMin),
			BlockPos.CODEC.optionalFieldOf("clear_max").forGetter(c -> c.clearMax),
			Codec.BOOL.optionalFieldOf("paused", false).forGetter(c -> c.paused)
	).apply(i, (id, blueprint, style, origin, rotation, min, max, step, done, scaffolding, clearMin, clearMax,
			paused) -> {
		Construction c = new Construction(id, blueprint, BuildStyle.fromSerializedName(style).orElse(BuildStyle.PLAINS),
				origin, rotation, min, max, step, done, scaffolding);
		c.clearMin = clearMin;
		c.clearMax = clearMax;
		c.paused = paused;
		return c;
	}));

	private final UUID id;
	private final Identifier blueprint;
	private final BuildStyle style;
	private final BlockPos origin;
	private final Rotation rotation;
	private final BlockPos min;
	private final BlockPos max;
	private int step;
	private boolean done;
	/** Un bâtiment fait à la main que ce chantier remplace : le bâtisseur le dégage d'abord. */
	private Optional<BlockPos> clearMin = Optional.empty();
	private Optional<BlockPos> clearMax = Optional.empty();
	/** Le maire l'a suspendu (MC-84) : les bâtisseurs passent au suivant, ou attendent. */
	private boolean paused;

	public boolean paused() {
		return paused;
	}

	/** Suspend le chantier, ou le reprend ; renvoie vrai s'il est désormais suspendu. */
	public boolean togglePause() {
		paused = !paused;
		claims.clear();
		return paused;
	}
	/** Les échafaudages que le bâtisseur a montés, à démonter avant de finir. */
	private final List<BlockPos> scaffolding;
	/** Les blocs du plan déjà faits au-delà de {@code step} (non sauvegardé : on les revoit au besoin). */
	private final BitSet placed = new BitSet();
	/** Qui travaille à quel bloc du plan, pour que plusieurs bâtisseurs ne se gênent pas. */
	private final Map<Integer, UUID> claims = new HashMap<>();

	private Construction(UUID id, Identifier blueprint, BuildStyle style, BlockPos origin, Rotation rotation,
			BlockPos min, BlockPos max, int step, boolean done, List<BlockPos> scaffolding) {
		this.id = id;
		this.blueprint = blueprint;
		this.style = style;
		this.origin = origin;
		this.rotation = rotation;
		this.min = min;
		this.max = max;
		this.step = step;
		this.done = done;
		this.scaffolding = new ArrayList<>(scaffolding);
	}

	public static Construction order(Blueprint blueprint, BuildStyle style, BlockPos origin, Rotation rotation) {
		BlockPos[] bounds = BuildPlan.bounds(blueprint, origin, rotation);
		return new Construction(UUID.randomUUID(), blueprint.id(), style, origin, rotation, bounds[0], bounds[1], 0,
				false, List.of());
	}

	/**
	 * Rebâtir à la place d'un bâtiment fait à la main (de {@code oldMin} à {@code oldMax}) : l'emprise
	 * couvre l'ancien et le nouveau, et l'ancien est dégagé d'abord.
	 */
	public static Construction rebuild(Blueprint blueprint, BuildStyle style, BlockPos origin, Rotation rotation,
			BlockPos oldMin, BlockPos oldMax) {
		BlockPos[] bounds = BuildPlan.bounds(blueprint, origin, rotation);
		BlockPos min = new BlockPos(Math.min(bounds[0].getX(), oldMin.getX()), Math.min(bounds[0].getY(), oldMin.getY()),
				Math.min(bounds[0].getZ(), oldMin.getZ()));
		BlockPos max = new BlockPos(Math.max(bounds[1].getX(), oldMax.getX()), Math.max(bounds[1].getY(), oldMax.getY()),
				Math.max(bounds[1].getZ(), oldMax.getZ()));
		Construction c = new Construction(UUID.randomUUID(), blueprint.id(), style, origin, rotation, min, max, 0, false,
				List.of());
		c.clearMin = Optional.of(oldMin.immutable());
		c.clearMax = Optional.of(oldMax.immutable());
		return c;
	}

	/** Le bâtiment à dégager avant de bâtir, s'il y en a un (de, à). */
	public Optional<BlockPos[]> clearing() {
		return clearMin.isPresent() && clearMax.isPresent()
				? Optional.of(new BlockPos[]{clearMin.get(), clearMax.get()}) : Optional.empty();
	}

	public UUID id() {
		return id;
	}

	public Identifier blueprint() {
		return blueprint;
	}

	public BuildStyle style() {
		return style;
	}

	public BlockPos origin() {
		return origin;
	}

	public Rotation rotation() {
		return rotation;
	}

	public BlockPos min() {
		return min;
	}

	public BlockPos max() {
		return max;
	}

	public int step() {
		return step;
	}

	public void advance() {
		markPlaced(step);
	}

	public boolean isPlaced(int index) {
		return index < step || placed.get(index);
	}

	/** Reprend le chantier à ce bloc : ce qui suit sera revu (ce qui est déjà en place se passe vite). */
	public void redo(int index) {
		step = Math.min(step, index);
		placed.clear();
		claims.clear();
	}

	/** Ce bloc est fait ; l'avancement passe tous les blocs faits qui se suivent. */
	public void markPlaced(int index) {
		placed.set(index);
		claims.remove(index);
		while (placed.get(step)) {
			placed.clear(step);
			step++;
		}
	}

	/** Le bâtisseur qui a réservé ce bloc, s'il y en a un. */
	public Optional<UUID> claimant(int index) {
		return Optional.ofNullable(claims.get(index));
	}

	/** Réserve ce bloc pour ce bâtisseur (et libère ce qu'il avait réservé avant). */
	public void claim(int index, UUID builder) {
		release(builder);
		claims.put(index, builder);
	}

	public void release(UUID builder) {
		claims.values().removeIf(builder::equals);
	}

	public boolean done() {
		return done;
	}

	public void finish() {
		done = true;
	}

	public List<BlockPos> scaffolding() {
		return scaffolding;
	}

	public boolean contains(BlockPos pos) {
		return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() - 1
				&& pos.getY() <= max.getY() && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
	}

	/** Les deux emprises se touchent-elles ? */
	public boolean overlaps(BlockPos otherMin, BlockPos otherMax) {
		return min.getX() <= otherMax.getX() && max.getX() >= otherMin.getX() && min.getZ() <= otherMax.getZ()
				&& max.getZ() >= otherMin.getZ() && min.getY() <= otherMax.getY() && max.getY() >= otherMin.getY();
	}
}
