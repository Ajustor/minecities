package com.ajustor.minecities.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Optional;

/**
 * Un bâtiment reconnu : la plaque qui l'a déclaré, ses lits (une place par lit : c'est une
 * maison), ses postes de travail (un emploi par poste : c'est un atelier), ses coffres et tonneaux
 * (le garde-manger de ses habitants, la réserve de ses travailleurs), et son volume intérieur. Un
 * même bâtiment peut être les deux, comme une ferme où l'on dort.
 *
 * <p>Une maison de village ({@code village}) est une maison d'un village vanilla repris par la
 * ville : relevée depuis le lit d'un villageois, sans plaque ({@code plaque} est alors la case
 * d'où part le relevé), et sans poste de travail (ceux des villageois restent à eux).
 */
public record House(BlockPos plaque, List<BlockPos> beds, List<BlockPos> containers, List<Workstation> workstations,
		int volume, boolean village, Optional<BlockPos> min, Optional<BlockPos> max) {
	public static final Codec<House> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("plaque").forGetter(House::plaque),
			BlockPos.CODEC.listOf().optionalFieldOf("beds", List.of()).forGetter(House::beds),
			BlockPos.CODEC.listOf().optionalFieldOf("containers", List.of()).forGetter(House::containers),
			Workstation.CODEC.listOf().optionalFieldOf("workstations", List.of()).forGetter(House::workstations),
			Codec.INT.optionalFieldOf("volume", 0).forGetter(House::volume),
			Codec.BOOL.optionalFieldOf("village", false).forGetter(House::village),
			BlockPos.CODEC.optionalFieldOf("min").forGetter(House::min),
			BlockPos.CODEC.optionalFieldOf("max").forGetter(House::max)
	).apply(i, House::new));

	/** Un poste de travail : sa position et le métier qu'il offre. */
	public record Workstation(BlockPos pos, Job job) {
		public static final Codec<Workstation> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Workstation::pos),
				Codec.STRING.xmap(s -> Job.fromSerializedName(s).orElse(Job.FARMER), Job::serializedName)
						.fieldOf("job").forGetter(Workstation::job)
		).apply(i, Workstation::new));
	}

	public House {
		beds = List.copyOf(beds);
		containers = List.copyOf(containers);
		workstations = List.copyOf(workstations);
	}

	public House(BlockPos plaque, List<BlockPos> beds, List<BlockPos> containers, List<Workstation> workstations,
			int volume, boolean village) {
		this(plaque, beds, containers, workstations, volume, village, Optional.empty(), Optional.empty());
	}

	/** Le même bâtiment, avec son étendue (de coin à coin, murs compris). */
	public House within(BlockPos min, BlockPos max) {
		return new House(plaque, beds, containers, workstations, volume, village, Optional.of(min), Optional.of(max));
	}

	/** Ce bloc fait-il partie du bâtiment (son intérieur et ses murs) ? */
	public boolean encloses(BlockPos pos) {
		return min.isPresent() && max.isPresent() && pos.getX() >= min.get().getX() && pos.getX() <= max.get().getX()
				&& pos.getY() >= min.get().getY() && pos.getY() <= max.get().getY()
				&& pos.getZ() >= min.get().getZ() && pos.getZ() <= max.get().getZ();
	}

	/** Un bâtiment déclaré par une plaque. */
	public House(BlockPos plaque, List<BlockPos> beds, List<BlockPos> containers, List<Workstation> workstations,
			int volume) {
		this(plaque, beds, containers, workstations, volume, false);
	}

	/** Une maison sans atelier. */
	public House(BlockPos plaque, List<BlockPos> beds, List<BlockPos> containers, int volume) {
		this(plaque, beds, containers, List.of(), volume, false);
	}

	public int capacity() {
		return beds.size();
	}

	/** Une maison : on peut y dormir. */
	public boolean isHome() {
		return !beds.isEmpty();
	}
}
