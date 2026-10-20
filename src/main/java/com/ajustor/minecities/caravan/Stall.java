package com.ajustor.minecities.caravan;

import com.ajustor.minecities.town.Market;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * L'échoppe d'une caravane arrivée (MC-80) : en bordure de la ville qui a acheté, le marchand vend
 * aux joueurs, une journée durant, des marchandises venues de sa ville (prises au grenier du vendeur
 * au départ). Ce qu'il gagne et ce qu'il n'a pas vendu repartent ensuite au grenier du vendeur : rien
 * ne se perd, rien ne se crée.
 */
public final class Stall {
	public static final Codec<Stall> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(s -> s.id),
			UUIDUtil.CODEC.fieldOf("from").forGetter(s -> s.from),
			UUIDUtil.CODEC.fieldOf("to").forGetter(s -> s.to),
			BlockPos.CODEC.fieldOf("pos").forGetter(s -> s.pos),
			Codec.FLOAT.optionalFieldOf("yaw", 0f).forGetter(s -> s.yaw),
			Codec.STRING.optionalFieldOf("merchant", "").forGetter(s -> s.merchant),
			ItemStack.CODEC.listOf().optionalFieldOf("wares", List.of()).forGetter(s -> List.copyOf(s.wares)),
			Codec.INT.optionalFieldOf("earned", 0).forGetter(s -> s.earned),
			Codec.LONG.optionalFieldOf("opened", 0L).forGetter(s -> s.opened)
	).apply(i, Stall::new));

	/** Ce qu'on achète d'un coup : tant de pièces pour tant d'émeraudes. */
	public record Lot(int count, int emeralds) {
	}

	private final UUID id;
	private final UUID from;
	private final UUID to;
	private final BlockPos pos;
	/** Vers où regarde le comptoir (la route), en degrés comme le lacet d'une entité. */
	private final float yaw;
	private final String merchant;
	private final List<ItemStack> wares;
	private int earned;
	private final long opened;

	private Stall(UUID id, UUID from, UUID to, BlockPos pos, float yaw, String merchant, List<ItemStack> wares,
			int earned, long opened) {
		this.id = id;
		this.from = from;
		this.to = to;
		this.pos = pos;
		this.yaw = yaw;
		this.merchant = merchant;
		this.wares = new ArrayList<>(wares.stream().filter(w -> !w.isEmpty()).map(ItemStack::copy).toList());
		this.earned = earned;
		this.opened = opened;
	}

	public static Stall open(Caravan caravan, BlockPos pos, float yaw, long now) {
		return new Stall(caravan.id(), caravan.from(), caravan.to(), pos, yaw, caravan.merchant(), caravan.wares(), 0,
				now);
	}

	/**
	 * Le prix pour un joueur : deux fois ce que paient les villageois (la part du marchand), soit la
	 * moitié du cours pour une émeraude ; un bijou, sa valeur et une émeraude de plus ; la nourriture
	 * sans cours, quatre pour une émeraude.
	 */
	public static Lot lot(Item item) {
		Optional<Integer> value = Market.value(item);
		if (value.isPresent()) {
			return new Lot(1, value.get() + 1);
		}
		return new Lot(Math.max(1, Market.rate(item).orElse(8) / 2), 1);
	}

	/** Ce qu'il propose : un lot de chaque marchandise, autant de fois qu'il en a. */
	public MerchantOffers offers() {
		MerchantOffers offers = new MerchantOffers();
		for (ItemStack ware : wares) {
			Lot lot = lot(ware.getItem());
			int uses = ware.getCount() / lot.count();
			if (uses > 0) {
				offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, lot.emeralds()), ware.copyWithCount(lot.count()),
						uses, 0, 0));
			}
		}
		return offers;
	}

	/** Un lot de cette marchandise est vendu : il quitte l'étal, les émeraudes entrent à la caisse. */
	public boolean sell(Item item) {
		Lot lot = lot(item);
		for (ItemStack ware : wares) {
			if (ware.is(item) && ware.getCount() >= lot.count()) {
				ware.shrink(lot.count());
				earned += lot.emeralds();
				wares.removeIf(ItemStack::isEmpty);
				return true;
			}
		}
		return false;
	}

	/** A-t-il encore de quoi vendre (au moins un lot) ? */
	public boolean hasWares() {
		return wares.stream().anyMatch(w -> w.getCount() >= lot(w.getItem()).count());
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

	public BlockPos pos() {
		return pos;
	}

	public float yaw() {
		return yaw;
	}

	public String merchant() {
		return merchant;
	}

	/** Ce qui reste sur l'étal. */
	public List<ItemStack> wares() {
		return wares;
	}

	/** Les émeraudes gagnées. */
	public int earned() {
		return earned;
	}

	public long opened() {
		return opened;
	}
}
