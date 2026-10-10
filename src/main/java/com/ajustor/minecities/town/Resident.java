package com.ajustor.minecities.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.Optional;

/**
 * Ce que la ville sait d'un de ses citoyens, même quand il est loin et que son entité n'est pas
 * chargée : son nom, sa maison (la plaque) et son lit, et son emploi.
 */
public record Resident(String name, Optional<BlockPos> home, Optional<BlockPos> bed, Optional<Employment> job) {
	public static final Codec<Resident> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Resident::name),
			BlockPos.CODEC.optionalFieldOf("home").forGetter(Resident::home),
			BlockPos.CODEC.optionalFieldOf("bed").forGetter(Resident::bed),
			Employment.CODEC.optionalFieldOf("job").forGetter(Resident::job)
	).apply(i, Resident::new));

	/** Un emploi : le métier, l'atelier (sa plaque) et le poste de travail. */
	public record Employment(Job job, BlockPos workshop, BlockPos station) {
		public static final Codec<Employment> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.xmap(s -> Job.fromSerializedName(s).orElse(Job.FARMER), Job::serializedName)
						.fieldOf("job").forGetter(Employment::job),
				BlockPos.CODEC.fieldOf("workshop").forGetter(Employment::workshop),
				BlockPos.CODEC.fieldOf("station").forGetter(Employment::station)
		).apply(i, Employment::new));
	}

	public Resident(String name, Optional<BlockPos> home, Optional<BlockPos> bed) {
		this(name, home, bed, Optional.empty());
	}

	public static Resident homeless(String name) {
		return new Resident(name, Optional.empty(), Optional.empty());
	}

	public Resident housed(BlockPos home, BlockPos bed) {
		return new Resident(name, Optional.of(home), Optional.of(bed), job);
	}

	public Resident evicted() {
		return new Resident(name, Optional.empty(), Optional.empty(), job);
	}

	public Resident employed(Employment employment) {
		return new Resident(name, home, bed, Optional.of(employment));
	}

	public Resident unemployed() {
		return new Resident(name, home, bed, Optional.empty());
	}

	public boolean hasHome() {
		return home.isPresent();
	}
}
