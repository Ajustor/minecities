package com.ajustor.minecities.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Les métiers. Comme pour les villageois, c'est le poste de travail vanilla posé dans un bâtiment
 * déclaré qui crée l'emploi : un poste, un travailleur. Certains métiers réclament un outil, à
 * fournir au grenier ou dans les coffres de l'atelier.
 *
 * <p>Les blocs qui servent aussi à bâtir (bûches écorcées, bottes de foin) ne comptent comme postes
 * que posés seuls au sol, comme un billot : rien de plein au-dessus. Le baril, qui sert aussi de
 * garde-manger, n'est le poste du pêcheur que près de l'eau.
 */
public enum Job {
	/** Récolte les cultures mûres autour de l'atelier, replante, et porte la récolte au grenier. */
	FARMER("farmer", (level, pos, state) -> state.is(Blocks.COMPOSTER), s -> s.is(ItemTags.HOES)),
	/** Cuit au fumoir ce que le grenier a de cru, et fait du pain avec le blé. */
	COOK("cook", (level, pos, state) -> state.is(Blocks.SMOKER), null),
	/** Abat les arbres autour de l'atelier et replante les pousses ; au billot (une bûche écorcée). */
	LUMBERJACK("lumberjack", (level, pos, state) -> state.is(BlockTags.LOGS)
			&& BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("stripped_")
			&& standsAlone(level, pos), s -> s.is(ItemTags.AXES)),
	/** Fait se reproduire les bêtes de l'enclos voisin et abat le surplus ; à la botte de foin. */
	BREEDER("breeder", (level, pos, state) -> state.is(Blocks.HAY_BLOCK) && standsAlone(level, pos), null),
	/** Pêche au bord de l'eau ; au baril, quand l'atelier est au bord de l'eau. */
	FISHERMAN("fisherman", (level, pos, state) -> state.is(Blocks.BARREL) && nearWater(level, pos),
			s -> s.is(Items.FISHING_ROD)),
	/** Bâtit les chantiers que commande le maire, d'après les schémas ; à la table d'artisan. */
	BUILDER("builder", (level, pos, state) -> state.is(Blocks.CRAFTER), null);

	/** Un bloc, à sa place, est-il ce poste de travail ? */
	@FunctionalInterface
	public interface Workstation {
		boolean test(BlockGetter level, BlockPos pos, BlockState state);
	}

	/** Le baril du pêcheur : de l'eau à 6 blocs au plus. */
	public static final int WATER_RANGE = 6;

	private final String serializedName;
	private final Workstation workstation;
	private final Predicate<ItemStack> tool;

	Job(String serializedName, Workstation workstation, Predicate<ItemStack> tool) {
		this.serializedName = serializedName;
		this.workstation = workstation;
		this.tool = tool;
	}

	public String serializedName() {
		return serializedName;
	}

	/** L'outil exigé, s'il en faut un. */
	public Optional<Predicate<ItemStack>> tool() {
		return Optional.ofNullable(tool);
	}

	public String translationKey() {
		return "job.minecities." + serializedName;
	}

	/** Le métier dont ce bloc, à cette place, est le poste de travail. */
	public static Optional<Job> ofWorkstation(BlockGetter level, BlockPos pos, BlockState state) {
		for (Job job : values()) {
			if (job.workstation.test(level, pos, state)) {
				return Optional.of(job);
			}
		}
		return Optional.empty();
	}

	public static Optional<Job> fromSerializedName(String name) {
		for (Job job : values()) {
			if (job.serializedName.equals(name)) {
				return Optional.of(job);
			}
		}
		return Optional.empty();
	}

	/** Posé seul au sol : un sol plein dessous, rien de plein dessus. */
	private static boolean standsAlone(BlockGetter level, BlockPos pos) {
		return !level.getBlockState(pos.above()).blocksMotion() && level.getBlockState(pos.below()).blocksMotion();
	}

	private static boolean nearWater(BlockGetter level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-WATER_RANGE, -2, -WATER_RANGE),
				pos.offset(WATER_RANGE, 1, WATER_RANGE))) {
			if (level.getFluidState(p).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}
}
