package com.ajustor.minecities.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Une maison reconnue : la plaque qui l'a déclarée, ses lits (une place par lit), ses coffres et
 * tonneaux (le garde-manger de ses habitants), et son volume intérieur.
 */
public record House(BlockPos plaque, List<BlockPos> beds, List<BlockPos> containers, int volume) {
	public static final Codec<House> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("plaque").forGetter(House::plaque),
			BlockPos.CODEC.listOf().optionalFieldOf("beds", List.of()).forGetter(House::beds),
			BlockPos.CODEC.listOf().optionalFieldOf("containers", List.of()).forGetter(House::containers),
			Codec.INT.optionalFieldOf("volume", 0).forGetter(House::volume)
	).apply(i, House::new));

	public House {
		beds = List.copyOf(beds);
		containers = List.copyOf(containers);
	}

	public int capacity() {
		return beds.size();
	}
}
