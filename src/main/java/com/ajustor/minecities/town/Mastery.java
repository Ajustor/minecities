package com.ajustor.minecities.town;

import com.ajustor.minecities.block.TownHallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * La maîtrise (brief 11, MC-63) : un atelier de niveau 4 fait de ses travailleurs des maîtres, une
 * fois que le bibliothécaire y a prêté un livre (pris aux réserves, et consommé). Chaque métier a
 * alors sa spécialité.
 */
public final class Mastery {
	/** Le niveau du bâtiment qui ouvre la maîtrise. */
	public static final int LEVEL = 4;

	private Mastery() {
	}

	/** Les travailleurs de cet atelier sont-ils des maîtres ? */
	public static boolean active(Town town, BlockPos workshop) {
		return town.buildingLevel(workshop) >= LEVEL && town.chronicle().mastered(workshop);
	}

	/**
	 * Où en est la maîtrise de cet atelier, pour l'écran du poste : {@code active}, {@code level}
	 * (pas encore au niveau 4), {@code library} (pas de bibliothécaire), {@code book} (pas de livre aux réserves).
	 */
	public static String state(ServerLevel level, Town town, BlockPos workshop) {
		if (town.buildingLevel(workshop) < LEVEL) {
			return "level";
		}
		if (town.chronicle().mastered(workshop)) {
			return "active";
		}
		if (town.workers(Job.LIBRARIAN) == 0) {
			return "library";
		}
		return "book";
	}

	/** De temps en temps : le bibliothécaire prête un livre à chaque atelier de niveau 4 qui n'en a pas. */
	static void tick(ServerLevel level, Town town) {
		if (town.workers(Job.LIBRARIAN) == 0) {
			return;
		}
		Set<BlockPos> workshops = new LinkedHashSet<>();
		town.residents().values().forEach(r -> r.job().ifPresent(j -> workshops.add(j.workshop())));
		for (BlockPos workshop : workshops) {
			if (town.buildingLevel(workshop) < LEVEL || town.chronicle().mastered(workshop)) {
				continue;
			}
			Optional<Container> shelf = book(level, town);
			if (shelf.isEmpty()) {
				return;
			}
			Stock.take(shelf.get(), s -> s.is(Items.BOOK), 1);
			town.chronicle().master(workshop);
			Job job = town.residents().values().stream().flatMap(r -> r.job().stream())
					.filter(j -> j.workshop().equals(workshop)).map(Resident.Employment::job).findFirst().orElse(Job.BUILDER);
			TownService.notify(level.getServer(), town, Component.translatable("message.minecities.mastery.granted",
					town.name(), Component.translatable(job.translationKey())));
			Towns.get(level.getServer()).changed();
		}
	}

	/** Un contenant des réserves qui a un livre : le grenier, puis les entrepôts. */
	private static Optional<Container> book(ServerLevel level, Town town) {
		for (BlockPos pos : Storage.stores(town)) {
			if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Container c
					&& Stock.count(c, s -> s.is(Items.BOOK)) > 0) {
				return Optional.of(c);
			}
		}
		return Optional.empty();
	}
}
