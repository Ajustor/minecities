package com.ajustor.minecities.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * La galerie d'un mineur (brief 11), creuse pour de vrai sous le territoire : un escalier qui descend
 * depuis l'entrée, à l'est de la mine, jusqu'à la profondeur des minerais, puis des couloirs droits,
 * l'un après l'autre (est, sud, ouest, nord) depuis le bas de l'escalier.
 *
 * @param station  le treuil du mineur (une galerie par treuil)
 * @param entrance le haut de l'escalier, dehors
 * @param bottom   le bas de l'escalier, d'où partent les couloirs (vide tant que l'escalier descend)
 * @param face     le prochain bloc à creuser (les pieds ; la tête au-dessus)
 * @param heading  la direction du couloir (ou de l'escalier) en cours
 * @param length   la longueur du couloir en cours
 * @param corridors combien de couloirs sont finis
 * @param dry      plus aucun minerai à portée : le mineur s'arrête, on peut reboucher le puits (MC-20)
 * @param closed   le puits est rebouché
 * @param mined    ce que le mineur en a tiré, par minerai (l'identifiant du bloc, sans « deepslate_ »)
 */
public record Mine(BlockPos station, BlockPos entrance, java.util.Optional<BlockPos> bottom, BlockPos face,
		Direction heading, int length, int corridors, boolean dry, boolean closed,
		java.util.Map<String, Integer> mined) {
	public static final Codec<Mine> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("station").forGetter(Mine::station),
			BlockPos.CODEC.fieldOf("entrance").forGetter(Mine::entrance),
			BlockPos.CODEC.optionalFieldOf("bottom").forGetter(Mine::bottom),
			BlockPos.CODEC.fieldOf("face").forGetter(Mine::face),
			Direction.CODEC.fieldOf("heading").forGetter(Mine::heading),
			Codec.INT.optionalFieldOf("length", 0).forGetter(Mine::length),
			Codec.INT.optionalFieldOf("corridors", 0).forGetter(Mine::corridors),
			Codec.BOOL.optionalFieldOf("dry", false).forGetter(Mine::dry),
			Codec.BOOL.optionalFieldOf("closed", false).forGetter(Mine::closed),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("mined", java.util.Map.of()).forGetter(Mine::mined)
	).apply(i, Mine::new));

	public Mine {
		mined = java.util.Map.copyOf(mined);
	}

	/** Un couloir s'arrête à cette longueur. */
	public static final int CORRIDOR = 48;
	/** Après tant de couloirs (les quatre directions, deux fois), la mine est épuisée. */
	public static final int MAX_CORRIDORS = 8;
	/** Une torche tous les tant de blocs. */
	public static final int TORCH_SPACING = 6;

	/** Une galerie neuve : l'escalier commence à l'entrée et descend vers l'est. */
	public static Mine open(BlockPos station, BlockPos entrance) {
		return new Mine(station, entrance, java.util.Optional.empty(), entrance, Direction.EAST, 0, 0, false, false,
				java.util.Map.of());
	}

	/** L'escalier descend-il encore ? */
	public boolean descending() {
		return bottom.isEmpty();
	}

	/** Plus rien à creuser : tous les couloirs faits, ou plus un minerai à portée. */
	public boolean exhausted() {
		return corridors >= MAX_CORRIDORS || dry;
	}

	/** Plus aucun minerai à portée. */
	public Mine drained() {
		return new Mine(station, entrance, bottom, face, heading, length, corridors, true, closed, mined);
	}

	/** Le puits rebouché. */
	public Mine sealed() {
		return new Mine(station, entrance, bottom, face, heading, length, corridors, true, true, mined);
	}

	/** Ces minerais viennent d'être extraits. */
	public Mine extracted(java.util.Map<String, Integer> more) {
		if (more.isEmpty()) {
			return this;
		}
		java.util.Map<String, Integer> all = new java.util.HashMap<>(mined);
		more.forEach((k, v) -> all.merge(k, v, Integer::sum));
		return new Mine(station, entrance, bottom, face, heading, length, corridors, dry, closed, all);
	}

	/**
	 * Le prochain bloc à creuser : en descendant, un pas en avant et un en bas ; dans un couloir, un pas
	 * en avant.
	 */
	public BlockPos next() {
		BlockPos ahead = face.relative(heading);
		return descending() ? ahead.below() : ahead;
	}

	/** Un bloc de plus est creusé : la face avance. */
	public Mine advanced(BlockPos dug) {
		return new Mine(station, entrance, bottom, dug, heading, length + 1, corridors, dry, closed, mined);
	}

	/** L'escalier a atteint la profondeur : les couloirs partent d'ici. */
	public Mine reachedBottom() {
		return new Mine(station, entrance, java.util.Optional.of(face), face, heading, 0, corridors, dry, closed, mined);
	}

	/** Le couloir est fini (trop long, de l'eau, une maison, le bord du territoire) : le suivant part du bas. */
	public Mine nextCorridor() {
		BlockPos start = bottom.orElse(face);
		return new Mine(station, entrance, bottom, start, heading.getClockWise(), 0, corridors + 1, dry, closed, mined);
	}
}
