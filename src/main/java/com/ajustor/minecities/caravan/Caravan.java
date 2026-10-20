package com.ajustor.minecities.caravan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Une caravane en route (MC-80) : de la ville qui vend à celle qui achète, avec sa cargaison (déjà
 * payée au départ). Elle avance le jour le long de son itinéraire, campe la nuit, et ne prend corps
 * (chariot, cheval, marchand) que près d'un joueur ; ailleurs, sa position est calculée.
 */
public final class Caravan {
	public static final Codec<Caravan> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(c -> c.id),
			UUIDUtil.CODEC.fieldOf("from").forGetter(c -> c.from),
			UUIDUtil.CODEC.fieldOf("to").forGetter(c -> c.to),
			ItemStack.CODEC.listOf().optionalFieldOf("cargo", List.of()).forGetter(c -> List.copyOf(c.cargo)),
			Codec.DOUBLE.optionalFieldOf("traveled", 0.0).forGetter(c -> c.traveled),
			Codec.LONG.optionalFieldOf("departed", 0L).forGetter(c -> c.departed),
			Codec.STRING.optionalFieldOf("merchant", "").forGetter(c -> c.merchant),
			net.minecraft.core.BlockPos.CODEC.listOf().optionalFieldOf("path", List.of()).forGetter(c -> c.path),
			ItemStack.CODEC.listOf().optionalFieldOf("wares", List.of()).forGetter(c -> List.copyOf(c.wares))
	).apply(i, Caravan::new));

	private final UUID id;
	private final UUID from;
	private final UUID to;
	private final List<ItemStack> cargo;
	private double traveled;
	private final long departed;
	/** Le nom du marchand qui la mène (celui de la ville qui vend). */
	private final String merchant;
	/** Son itinéraire, fixé au départ (les retouches de la route vaudront pour la suivante). */
	private final List<net.minecraft.core.BlockPos> path;
	/** Ce que le marchand vendra à son échoppe, à l'arrivée (pas encore payé : c'est au vendeur). */
	private final List<ItemStack> wares;

	private Caravan(UUID id, UUID from, UUID to, List<ItemStack> cargo, double traveled, long departed,
			String merchant, List<net.minecraft.core.BlockPos> path, List<ItemStack> wares) {
		this.id = id;
		this.from = from;
		this.to = to;
		this.cargo = new ArrayList<>(cargo.stream().map(ItemStack::copy).toList());
		this.traveled = traveled;
		this.departed = departed;
		this.merchant = merchant;
		this.path = List.copyOf(path);
		this.wares = new ArrayList<>(wares.stream().map(ItemStack::copy).toList());
	}

	public static Caravan depart(UUID from, UUID to, List<ItemStack> cargo, List<ItemStack> wares, long now,
			String merchant, List<net.minecraft.core.BlockPos> path) {
		return new Caravan(UUID.randomUUID(), from, to, cargo, 0, now, merchant, path, wares);
	}

	/** Ce que le marchand vendra à son échoppe. */
	public List<ItemStack> wares() {
		return wares;
	}

	public List<net.minecraft.core.BlockPos> path() {
		return path;
	}

	/** Est-elle arrivée au bout de son itinéraire ? */
	public boolean arrived() {
		return traveled >= Itinerary.length(path);
	}

	public UUID id() {
		return id;
	}

	public UUID from() {
		return from;
	}

	public UUID to() {
		return to;
	}

	/** Ce qu'elle porte encore (le pillage en retire). */
	public List<ItemStack> cargo() {
		return cargo;
	}

	/** La distance parcourue, en blocs, le long de son itinéraire. */
	public double traveled() {
		return traveled;
	}

	public void advance(double blocks) {
		traveled += blocks;
	}

	public long departed() {
		return departed;
	}

	public String merchant() {
		return merchant;
	}
}
