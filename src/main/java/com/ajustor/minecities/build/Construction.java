package com.ajustor.minecities.build;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

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
			Codec.BOOL.optionalFieldOf("done", false).forGetter(c -> c.done)
	).apply(i, (id, blueprint, style, origin, rotation, min, max, step, done) -> new Construction(id, blueprint,
			BuildStyle.fromSerializedName(style).orElse(BuildStyle.PLAINS), origin, rotation, min, max, step, done)));

	private final UUID id;
	private final Identifier blueprint;
	private final BuildStyle style;
	private final BlockPos origin;
	private final Rotation rotation;
	private final BlockPos min;
	private final BlockPos max;
	private int step;
	private boolean done;

	private Construction(UUID id, Identifier blueprint, BuildStyle style, BlockPos origin, Rotation rotation,
			BlockPos min, BlockPos max, int step, boolean done) {
		this.id = id;
		this.blueprint = blueprint;
		this.style = style;
		this.origin = origin;
		this.rotation = rotation;
		this.min = min;
		this.max = max;
		this.step = step;
		this.done = done;
	}

	public static Construction order(Blueprint blueprint, BuildStyle style, BlockPos origin, Rotation rotation) {
		BlockPos[] bounds = BuildPlan.bounds(blueprint, origin, rotation);
		return new Construction(UUID.randomUUID(), blueprint.id(), style, origin, rotation, bounds[0], bounds[1], 0,
				false);
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
		step++;
	}

	public boolean done() {
		return done;
	}

	public void finish() {
		done = true;
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
