package com.ajustor.minecities.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * Ce que la ville sait d'un de ses citoyens, même quand il est loin et que son entité n'est pas
 * chargée : son nom, sa maison (la plaque) et son lit, son emploi, et sa famille : son conjoint, et
 * pour un enfant, le moment où il deviendra adulte ; pour un parent, sa dernière naissance.
 */
public record Resident(String name, Optional<BlockPos> home, Optional<BlockPos> bed, Optional<Employment> job,
		Optional<UUID> spouse, Optional<Long> grownUpAt, long lastBirth) {
	public static final Codec<Resident> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Resident::name),
			BlockPos.CODEC.optionalFieldOf("home").forGetter(Resident::home),
			BlockPos.CODEC.optionalFieldOf("bed").forGetter(Resident::bed),
			Employment.CODEC.optionalFieldOf("job").forGetter(Resident::job),
			UUIDUtil.CODEC.optionalFieldOf("spouse").forGetter(Resident::spouse),
			Codec.LONG.optionalFieldOf("grown_up_at").forGetter(Resident::grownUpAt),
			Codec.LONG.optionalFieldOf("last_birth", 0L).forGetter(Resident::lastBirth)
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

	public Resident(String name, Optional<BlockPos> home, Optional<BlockPos> bed, Optional<Employment> job) {
		this(name, home, bed, job, Optional.empty(), Optional.empty(), 0L);
	}

	public Resident(String name, Optional<BlockPos> home, Optional<BlockPos> bed) {
		this(name, home, bed, Optional.empty());
	}

	public static Resident homeless(String name) {
		return new Resident(name, Optional.empty(), Optional.empty());
	}

	/** Un enfant qui vient de naître, adulte à {@code grownUpAt}. */
	public static Resident child(String name, long grownUpAt) {
		return new Resident(name, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.of(grownUpAt), 0L);
	}

	public Resident housed(BlockPos home, BlockPos bed) {
		return new Resident(name, Optional.of(home), Optional.of(bed), job, spouse, grownUpAt, lastBirth);
	}

	public Resident evicted() {
		return new Resident(name, Optional.empty(), Optional.empty(), job, spouse, grownUpAt, lastBirth);
	}

	public Resident employed(Employment employment) {
		return new Resident(name, home, bed, Optional.of(employment), spouse, grownUpAt, lastBirth);
	}

	public Resident unemployed() {
		return new Resident(name, home, bed, Optional.empty(), spouse, grownUpAt, lastBirth);
	}

	public Resident married(UUID spouse) {
		return new Resident(name, home, bed, job, Optional.of(spouse), grownUpAt, lastBirth);
	}

	public Resident widowed() {
		return new Resident(name, home, bed, job, Optional.empty(), grownUpAt, lastBirth);
	}

	public Resident grownUp() {
		return new Resident(name, home, bed, job, spouse, Optional.empty(), lastBirth);
	}

	public Resident gaveBirth(long now) {
		return new Resident(name, home, bed, job, spouse, grownUpAt, now);
	}

	public boolean hasHome() {
		return home.isPresent();
	}

	/** Un enfant : il ne travaille pas, ne se marie pas. */
	public boolean isChild() {
		return grownUpAt.isPresent();
	}

	/** Son nom de famille (le dernier mot de son nom), que prennent ses enfants. */
	public String surname() {
		int space = name.lastIndexOf(' ');
		return space < 0 ? name : name.substring(space + 1);
	}
}
