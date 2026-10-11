package com.ajustor.minecities.citizen;

import com.ajustor.minecities.registry.McItems;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

import static com.ajustor.minecities.citizen.CraftGoal.Craft;
import static com.ajustor.minecities.citizen.CraftGoal.Input;
import static com.ajustor.minecities.citizen.CraftGoal.of;

/**
 * Les ouvrages des artisans (brief 11), qui ferment les filières : la mine nourrit le joaillier et le
 * maçon, la rivière et le désert le verrier, l'élevage le tisserand, et tous remplissent les besoins
 * des bâtisseurs (briques, pierre taillée, verre, lits) ou le trésor (les bijoux se vendent cher).
 */
final class Crafts {
	private Crafts() {
	}

	/** Le joaillier : des bijoux, du plus précieux au plus simple ; le marchand les vend cher. */
	static final List<Craft> JEWELER = List.of(
			new Craft(List.of(of(Items.GOLD_INGOT, 3), of(Items.DIAMOND, 1)), new ItemStack(McItems.DIADEM), 0),
			new Craft(List.of(of(Items.GOLD_INGOT, 2), of(Items.LAPIS_LAZULI, 2)), new ItemStack(McItems.GOLD_RING), 0),
			new Craft(List.of(of(Items.COPPER_INGOT, 2), of(Items.AMETHYST_SHARD, 1)),
					new ItemStack(McItems.AMETHYST_BROOCH), 0));

	/** Le maçon : pierre taillée, briques et pierre polie pour les bâtisses et les demeures. */
	static final List<Craft> MASON = List.of(
			new Craft(List.of(of(Items.CLAY_BALL, 4)), new ItemStack(Items.BRICKS), 64),
			new Craft(List.of(of(Items.ANDESITE, 1)), new ItemStack(Items.POLISHED_ANDESITE), 64),
			new Craft(List.of(of(Items.COBBLESTONE, 1)), new ItemStack(Items.STONE_BRICKS), 64));

	/** Le verrier : le verre, les vitres, et les vitraux blancs des demeures. */
	static final List<Craft> GLASSBLOWER = List.of(
			new Craft(List.of(of(Items.GLASS_PANE, 8), of(Items.DYE.white(), 1)),
					new ItemStack(Items.STAINED_GLASS_PANE.white(), 8), 32),
			new Craft(List.of(of(Items.GLASS, 6)), new ItemStack(Items.GLASS_PANE, 16), 64),
			new Craft(List.of(new Input(s -> s.is(ItemTags.SMELTS_TO_GLASS), 1)), new ItemStack(Items.GLASS), 64));

	/** Le tisserand : la laine (de la ficelle), et des lits pour les maisons. */
	static final List<Craft> WEAVER = List.of(
			new Craft(List.of(new Input(s -> s.is(ItemTags.WOOL), 3), new Input(s -> s.is(ItemTags.PLANKS), 3)),
					new ItemStack(Items.BED.white()), 8),
			new Craft(List.of(of(Items.STRING, 4)), new ItemStack(Items.WOOL.white()), 32));

	/** Le bibliothécaire : des livres, pour la bibliothèque et l'école. */
	static final List<Craft> LIBRARIAN = List.of(
			new Craft(List.of(of(Items.PAPER, 3), of(Items.LEATHER, 1)), new ItemStack(Items.BOOK), 16),
			new Craft(List.of(of(Items.SUGAR_CANE, 3)), new ItemStack(Items.PAPER, 3), 24));
}
