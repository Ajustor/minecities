package com.ajustor.minecities.town;

import com.ajustor.minecities.McConfig;
import com.ajustor.minecities.block.TaxBoxBlockEntity;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.compat.EntityCompat;
import com.ajustor.minecities.registry.McBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Le budget de la ville (brief 02, MC-57). Chaque matin, les adultes paient l'impôt au taux que fixe le
 * maire, versé dans la boîte à impôts ; les gardes et les archers touchent leur salaire, pris au grenier.
 * C'est au maire de porter l'argent de la boîte au grenier : un défenseur impayé traîne et maugrée. Les
 * chantiers (à partir du niveau 2) coûtent aussi des émeraudes, prises au grenier à la commande.
 */
public final class Budget {
	/** Le salaire d'un garde ou d'un archer, chaque matin, en émeraudes. */
	public static final int WAGE = 1;

	/** Le taux de l'impôt : ce que verse chaque adulte chaque matin, et ce qu'en pense la ville. */
	public enum TaxRate {
		NONE("none", 0, 0),
		LOW("low", 1, 0),
		NORMAL("normal", 2, -1),
		HIGH("high", 4, -2);

		private final String serializedName;
		private final int emeralds;
		private final int mood;

		TaxRate(String serializedName, int emeralds, int mood) {
			this.serializedName = serializedName;
			this.emeralds = emeralds;
			this.mood = mood;
		}

		public String serializedName() {
			return serializedName;
		}

		/** Ce que verse chaque adulte, chaque matin. */
		public int emeralds() {
			return emeralds;
		}

		/** Ce que l'impôt fait au moral de chaque adulte. */
		public int mood() {
			return mood;
		}

		public String translationKey() {
			return "tax.minecities." + serializedName;
		}

		/** Le taux suivant, pour le bouton qui les fait défiler. */
		public TaxRate next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public static TaxRate fromSerializedName(String name) {
			for (TaxRate rate : values()) {
				if (rate.serializedName.equals(name)) {
					return rate;
				}
			}
			return LOW;
		}
	}

	/** Les comptes de la ville : le taux, la boîte à impôts, et ce que la journée a rapporté et coûté. */
	public static final class Ledger {
		public static final Codec<Ledger> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("tax", TaxRate.LOW.serializedName()).forGetter(l -> l.rate.serializedName()),
				BlockPos.CODEC.optionalFieldOf("tax_box").forGetter(l -> l.taxBox),
				Codec.LONG.optionalFieldOf("day", -1L).forGetter(l -> l.day),
				Codec.INT.optionalFieldOf("taxes", 0).forGetter(l -> l.taxes),
				Codec.INT.optionalFieldOf("wages", 0).forGetter(l -> l.wages),
				Codec.INT.optionalFieldOf("works", 0).forGetter(l -> l.works),
				UUIDUtil.CODEC.listOf().optionalFieldOf("unpaid", List.of()).forGetter(l -> List.copyOf(l.unpaid))
		).apply(i, (tax, taxBox, day, taxes, wages, works, unpaid) -> {
			Ledger ledger = new Ledger();
			ledger.rate = TaxRate.fromSerializedName(tax);
			ledger.taxBox = taxBox;
			ledger.day = day;
			ledger.taxes = taxes;
			ledger.wages = wages;
			ledger.works = works;
			ledger.unpaid.addAll(unpaid);
			return ledger;
		}));

		private TaxRate rate = TaxRate.LOW;
		private Optional<BlockPos> taxBox = Optional.empty();
		/** Le jour (temps de jeu / 24000) des comptes ci-dessous ; -1 tant que la ville n'a pas eu de matin. */
		private long day = -1;
		private int taxes;
		private int wages;
		private int works;
		/** Les défenseurs impayés ce jour-là. */
		private final List<UUID> unpaid = new ArrayList<>();

		public TaxRate rate() {
			return rate;
		}

		public void setRate(TaxRate rate) {
			this.rate = rate;
		}

		public Optional<BlockPos> taxBox() {
			return taxBox;
		}

		public void setTaxBox(Optional<BlockPos> taxBox) {
			this.taxBox = taxBox;
		}

		public long day() {
			return day;
		}

		/** L'impôt perçu ce jour. */
		public int taxes() {
			return taxes;
		}

		/** Les salaires versés ce jour. */
		public int wages() {
			return wages;
		}

		/** Ce que les chantiers ont coûté ce jour. */
		public int works() {
			return works;
		}

		public boolean unpaid(UUID citizen) {
			return unpaid.contains(citizen);
		}

		/** Un nouveau jour : les comptes repartent de zéro. */
		void startDay(long day) {
			this.day = day;
			taxes = 0;
			wages = 0;
			works = 0;
			unpaid.clear();
		}
	}

	private Budget() {
	}

	private static boolean enabled() {
		return McConfig.get().budget;
	}

	/** À chaque passage : un nouveau matin ouvre une nouvelle journée de comptes. */
	public static void tick(ServerLevel level, Town town) {
		if (!enabled()) {
			return;
		}
		long day = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		Ledger ledger = town.chronicle().ledger();
		if (ledger.day() < 0) {
			// La première fois : la journée commence, sans rattraper celles d'avant le budget.
			ledger.startDay(day);
			Towns.get(level.getServer()).changed();
		} else if (day > ledger.day()) {
			morning(level, town, day);
		}
	}

	/** Le matin : l'impôt dans la boîte, les salaires pris au grenier. */
	public static void morning(ServerLevel level, Town town, long day) {
		Ledger ledger = town.chronicle().ledger();
		ledger.startDay(day);
		int adults = (int) town.residents().values().stream().filter(r -> !r.isChild()).count();
		int due = adults * ledger.rate().emeralds();
		if (due > 0) {
			Optional<TaxBoxBlockEntity> box = taxBox(level, town);
			if (box.isPresent()) {
				int left = due;
				while (left > 0) {
					int n = Math.min(left, 64);
					ItemStack rest = Stock.insert(box.get(), new ItemStack(Items.EMERALD, n));
					left -= n - rest.getCount();
					if (!rest.isEmpty()) {
						break;
					}
				}
				ledger.taxes = due - left;
			} else {
				TownService.notify(level.getServer(), town, Component.translatable("message.minecities.budget.no_box",
						due).withStyle(ChatFormatting.GOLD));
			}
		}
		List<UUID> defenders = town.residents().entrySet().stream()
				.filter(e -> e.getValue().job().filter(j -> j.job() == Job.GUARD || j.job() == Job.ARCHER).isPresent())
				.map(java.util.Map.Entry::getKey).toList();
		Optional<com.ajustor.minecities.block.TownHallBlockEntity> granary = TownService.granary(level, town);
		for (UUID defender : defenders) {
			ItemStack wage = granary.map(g -> Stock.take(g, s -> s.is(Items.EMERALD), WAGE)).orElse(ItemStack.EMPTY);
			if (wage.getCount() >= WAGE) {
				ledger.wages += WAGE;
				continue;
			}
			// Une partie de salaire ne paie pas : elle retourne au grenier.
			granary.ifPresent(g -> Stock.insert(g, wage));
			ledger.unpaid.add(defender);
			if (level.getEntity(defender) instanceof CitizenEntity citizen) {
				citizen.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 24000, 0, false, false));
				citizen.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 24000, 0, false, false));
			}
		}
		if (!ledger.unpaid.isEmpty()) {
			TownService.notify(level.getServer(), town, Component.translatable("message.minecities.budget.unpaid",
					ledger.unpaid.size()).withStyle(ChatFormatting.RED));
		}
		Towns.get(level.getServer()).changed();
	}

	// Chantiers

	/** Ce que coûte un chantier de ce niveau, en émeraudes. */
	public static int cost(int buildingLevel) {
		return switch (buildingLevel) {
			case 2 -> 4;
			case 3 -> 12;
			case 4 -> 32;
			default -> 0;
		};
	}

	/** Pourquoi la ville ne peut pas payer un chantier de ce niveau ; vide si elle le peut. */
	public static Optional<Component> unaffordable(ServerLevel level, Town town, int buildingLevel) {
		int cost = cost(buildingLevel);
		if (!enabled() || cost == 0) {
			return Optional.empty();
		}
		int have = TownService.granary(level, town).map(g -> Stock.count(g, s -> s.is(Items.EMERALD))).orElse(0);
		return have >= cost ? Optional.empty()
				: Optional.of(Component.translatable("message.minecities.build.cost", cost, have));
	}

	/** La ville paie un chantier de ce niveau (déjà vérifié par {@link #unaffordable}). */
	public static void pay(ServerLevel level, Town town, int buildingLevel) {
		int cost = cost(buildingLevel);
		if (!enabled() || cost == 0) {
			return;
		}
		TownService.granary(level, town).ifPresent(g -> Stock.take(g, s -> s.is(Items.EMERALD), cost));
		town.chronicle().ledger().works += cost;
	}

	// La boîte à impôts

	/** La boîte à impôts de la ville, si elle en a une (et qu'elle est chargée). */
	public static Optional<TaxBoxBlockEntity> taxBox(ServerLevel level, Town town) {
		return town.chronicle().ledger().taxBox()
				.filter(level::isLoaded)
				.map(level::getBlockEntity)
				.filter(TaxBoxBlockEntity.class::isInstance)
				.map(TaxBoxBlockEntity.class::cast);
	}

	/** Ce que contient la boîte à impôts, en émeraudes ; -1 sans boîte. */
	public static int inTaxBox(ServerLevel level, Town town) {
		return taxBox(level, town).map(b -> Stock.count(b, s -> s.is(Items.EMERALD))).orElse(-1);
	}

	/** La ville dont c'est la boîte à impôts. */
	public static Optional<Town> owner(ServerLevel level, BlockPos pos) {
		return Towns.get(level.getServer()).all().stream()
				.filter(t -> t.dimension().equals(level.dimension()) && t.chronicle().ledger().taxBox().filter(pos::equals)
						.isPresent()).findFirst();
	}

	/** Une boîte à impôts est posée : la ville l'adopte, ou le joueur la récupère avec la raison. */
	public static void placeTaxBox(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
		Optional<Town> found = Towns.get(level.getServer()).at(level.dimension(), pos);
		Optional<String> refusal = found.isEmpty() ? Optional.of("outside")
				: !found.get().canManage(player.getUUID()) ? Optional.of("not_mayor")
				: taxBox(level, found.get()).filter(b -> !b.getBlockPos().equals(pos)).isPresent()
				? Optional.of("already") : Optional.empty();
		if (refusal.isPresent()) {
			player.sendSystemMessage(Component.translatable("message.minecities.tax_box.refused." + refusal.get())
					.withStyle(ChatFormatting.RED));
			level.removeBlock(pos, false);
			if (!player.isCreative()) {
				EntityCompat.placeBack(player, stack.copyWithCount(1));
			}
			return;
		}
		Town town = found.get();
		town.chronicle().ledger().setTaxBox(Optional.of(pos.immutable()));
		Towns.get(level.getServer()).changed();
		player.sendSystemMessage(Component.translatable("message.minecities.tax_box.placed", town.name())
				.withStyle(ChatFormatting.GREEN));
	}

	/** La boîte à impôts a disparu : la ville n'en a plus. */
	public static void taxBoxRemoved(ServerLevel level, BlockPos pos) {
		owner(level, pos).ifPresent(town -> {
			if (!level.getBlockState(pos).is(McBlocks.TAX_BOX)) {
				town.chronicle().ledger().setTaxBox(Optional.empty());
				Towns.get(level.getServer()).changed();
			}
		});
	}

	/** Le maire change le taux de l'impôt (au suivant). */
	public static void nextRate(ServerLevel level, Town town) {
		Ledger ledger = town.chronicle().ledger();
		ledger.setRate(ledger.rate().next());
		Towns.get(level.getServer()).changed();
	}

	/** Ce que l'impôt et le salaire font au moral de ce citoyen. */
	public static List<com.ajustor.minecities.citizen.Happiness.Factor> mood(Town town, UUID citizen, boolean child) {
		List<com.ajustor.minecities.citizen.Happiness.Factor> factors = new ArrayList<>();
		if (!enabled()) {
			return factors;
		}
		Ledger ledger = town.chronicle().ledger();
		if (!child && ledger.rate().mood() != 0) {
			factors.add(new com.ajustor.minecities.citizen.Happiness.Factor("taxes", ledger.rate().mood()));
		}
		if (ledger.unpaid(citizen)) {
			factors.add(new com.ajustor.minecities.citizen.Happiness.Factor("unpaid", -2));
		}
		return factors;
	}
}
