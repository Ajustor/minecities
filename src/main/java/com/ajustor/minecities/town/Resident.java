package com.ajustor.minecities.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.Optional;

/**
 * Ce que la ville sait d'un de ses citoyens, même quand il est loin et que son entité n'est pas
 * chargée : son nom, sa maison (la plaque) et son lit.
 */
public record Resident(String name, Optional<BlockPos> home, Optional<BlockPos> bed) {
	public static final Codec<Resident> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Resident::name),
			BlockPos.CODEC.optionalFieldOf("home").forGetter(Resident::home),
			BlockPos.CODEC.optionalFieldOf("bed").forGetter(Resident::bed)
	).apply(i, Resident::new));

	public static Resident homeless(String name) {
		return new Resident(name, Optional.empty(), Optional.empty());
	}

	public Resident housed(BlockPos home, BlockPos bed) {
		return new Resident(name, Optional.of(home), Optional.of(bed));
	}

	public Resident evicted() {
		return homeless(name);
	}

	public boolean hasHome() {
		return home.isPresent();
	}
}
