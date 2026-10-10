package com.ajustor.minecities.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Un bâtiment reconnu : la plaque qui l'a déclaré, ses lits (une place par lit : c'est une
 * maison), ses postes de travail (un emploi par poste : c'est un atelier), ses coffres et tonneaux
 * (le garde-manger de ses habitants, la réserve de ses travailleurs), et son volume intérieur. Un
 * même bâtiment peut être les deux, comme une ferme où l'on dort.
 */
public record House(BlockPos plaque, List<BlockPos> beds, List<BlockPos> containers, List<Workstation> workstations,
		int volume) {
	public static final Codec<House> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("plaque").forGetter(House::plaque),
			BlockPos.CODEC.listOf().optionalFieldOf("beds", List.of()).forGetter(House::beds),
			BlockPos.CODEC.listOf().optionalFieldOf("containers", List.of()).forGetter(House::containers),
			Workstation.CODEC.listOf().optionalFieldOf("workstations", List.of()).forGetter(House::workstations),
			Codec.INT.optionalFieldOf("volume", 0).forGetter(House::volume)
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

	/** Une maison sans atelier. */
	public House(BlockPos plaque, List<BlockPos> beds, List<BlockPos> containers, int volume) {
		this(plaque, beds, containers, List.of(), volume);
	}

	public int capacity() {
		return beds.size();
	}

	/** Une maison : on peut y dormir. */
	public boolean isHome() {
		return !beds.isEmpty();
	}
}
