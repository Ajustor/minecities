package com.ajustor.minecities.town;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Les métiers. Comme pour les villageois, c'est le poste de travail vanilla posé dans un bâtiment
 * déclaré qui crée l'emploi : un poste, un travailleur. Certains métiers réclament un outil, à
 * fournir au grenier ou dans les coffres de l'atelier.
 */
public enum Job {
	/** Récolte les cultures mûres autour de l'atelier, replante, et porte la récolte au grenier. */
	FARMER("farmer", Blocks.COMPOSTER, ItemTags.HOES),
	/** Cuit au fumoir ce que le grenier a de cru, et fait du pain avec le blé. */
	COOK("cook", Blocks.SMOKER, null);

	private final String serializedName;
	private final Block workstation;
	private final TagKey<Item> tool;

	Job(String serializedName, Block workstation, TagKey<Item> tool) {
		this.serializedName = serializedName;
		this.workstation = workstation;
		this.tool = tool;
	}

	public String serializedName() {
		return serializedName;
	}

	public Block workstation() {
		return workstation;
	}

	/** L'outil exigé (une étiquette d'objets), s'il en faut un. */
	public Optional<TagKey<Item>> tool() {
		return Optional.ofNullable(tool);
	}

	public String translationKey() {
		return "job.minecities." + serializedName;
	}

	/** Le métier dont ce bloc est le poste de travail. */
	public static Optional<Job> ofWorkstation(BlockState state) {
		for (Job job : values()) {
			if (state.is(job.workstation)) {
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
}
