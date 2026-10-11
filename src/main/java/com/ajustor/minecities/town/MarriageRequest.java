package com.ajustor.minecities.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/** Deux citoyens qui s'entendent demandent au maire de les marier. */
public record MarriageRequest(UUID id, UUID first, UUID second, long since) {
	public static final Codec<MarriageRequest> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(MarriageRequest::id),
			UUIDUtil.CODEC.fieldOf("first").forGetter(MarriageRequest::first),
			UUIDUtil.CODEC.fieldOf("second").forGetter(MarriageRequest::second),
			Codec.LONG.fieldOf("since").forGetter(MarriageRequest::since)
	).apply(i, MarriageRequest::new));

	public boolean concerns(UUID citizen) {
		return first.equals(citizen) || second.equals(citizen);
	}
}
