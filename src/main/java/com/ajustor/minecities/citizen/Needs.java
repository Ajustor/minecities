package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Town;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Ce que chaque métier consomme (brief 12) : les livreurs en garnissent à l'avance les coffres des
 * ateliers, et ne remportent pas à l'entrepôt ce qui y sert.
 */
final class Needs {
	private Needs() {
	}

	private static Predicate<ItemStack> anyOf(List<CraftGoal.Craft> crafts) {
		return stack -> crafts.stream().anyMatch(c -> c.inputs().stream().anyMatch(i -> i.accepts().test(stack)));
	}

	/** Ce que consomme ce métier ; vide s'il ne consomme rien (ou le prend lui-même dans la nature). */
	static Optional<Predicate<ItemStack>> inputs(Job job, Town town) {
		Predicate<ItemStack> inputs = switch (job) {
			case COOK -> town.workers(Job.BAKER) > 0 ? Cooking.COOK_WITH_BAKER::isIngredient : Cooking.COOK::isIngredient;
			case BAKER -> Cooking.BAKER::isIngredient;
			case SMITH -> s -> s.is(Items.RAW_IRON) || s.is(Items.RAW_GOLD) || s.is(Items.RAW_COPPER)
					|| s.is(Items.IRON_INGOT) || s.is(Items.COBBLESTONE) || s.is(Items.STICK) || s.is(Items.STRING);
			case JEWELER -> anyOf(Crafts.JEWELER);
			case MASON -> anyOf(Crafts.MASON);
			case GLASSBLOWER -> anyOf(Crafts.GLASSBLOWER);
			case WEAVER -> anyOf(Crafts.WEAVER);
			case LIBRARIAN -> anyOf(Crafts.LIBRARIAN);
			case TANNER -> anyOf(Crafts.TANNER);
			case CARPENTER -> anyOf(Crafts.CARPENTER);
			case INVENTOR -> s -> s.is(Items.IRON_BLOCK) || s.is(Items.CARVED_PUMPKIN) || s.is(Items.SNOW_BLOCK);
			case ARCHER -> s -> s.is(ItemTags.ARROWS) || s.is(Items.FLINT) || s.is(Items.FEATHER) || s.is(Items.STICK);
			case BEEKEEPER -> s -> s.is(Items.GLASS_BOTTLE);
			case MINER -> s -> s.is(Items.STICK) || s.is(Items.TORCH);
			case TAVERNER -> TavernerGoal.DRINK;
			default -> null;
		};
		return Optional.ofNullable(inputs);
	}

	/** Ce que ce métier garde dans son atelier : ses matières et son outil. */
	static boolean keeps(Job job, Town town, ItemStack stack) {
		return inputs(job, town).filter(p -> p.test(stack)).isPresent()
				|| job.tool().filter(p -> p.test(stack)).isPresent();
	}
}
