package com.ajustor.minecities.town;

import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.citizen.Happiness;
import com.ajustor.minecities.citizen.Hunger;
import com.ajustor.minecities.citizen.SleepInBedGoal;
import com.ajustor.minecities.registry.McEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * La vie des familles (brief 10) : une ville ne descend pas sous {@link #MINIMUM} habitants (des
 * nouveaux venus arrivent) mais ne grandit, au-delà, que par les naissances. Les citoyens qui
 * vivent et travaillent côte à côte s'apprécient ; ceux qui s'entendent demandent au maire de les
 * marier ; un couple marié, logé ensemble, bien nourri, content de son sort, avec un lit libre chez
 * lui, peut avoir un enfant, qui grandit en trois jours.
 */
public final class Families {
	/** Le minimum d'habitants : en dessous, des nouveaux venus arrivent. */
	public static final int MINIMUM = 5;
	/** Un nouveau venu toutes les 20 secondes au plus, tant qu'on est sous le minimum (réglable). */
	public static int newcomerDelay() {
		return com.ajustor.minecities.McConfig.get().newcomerDelay;
	}
	/** À moins de 5 blocs l'un de l'autre, deux citoyens s'apprécient un peu plus à chaque relevé. */
	private static final double CLOSE = 5;
	private static final int AFFINITY_GAIN = 3;
	/** Un enfant devient adulte en trois jours de jeu. */
	public static final long CHILDHOOD = 3 * 24000L;
	/** L'enfance : trois jours, deux si la ville a une bibliothèque (l'école du bibliothécaire). */
	public static long childhood(Town town) {
		return town.workers(Job.LIBRARIAN) > 0 ? CHILDHOOD * 2 / 3 : CHILDHOOD;
	}

	/** Un couple n'a pas plus d'un enfant par jour. */
	private static final long BIRTH_SPACING = 24000L;
	/** Chaque relevé de nuit (toutes les 5 secondes), une chance sur 200 : un peu moins d'une naissance par nuit. */
	private static final int BIRTH_ODDS = 200;

	/** Le minimum d'habitants appliqué (les scènes de test le baissent pour compter les leurs). */
	private static int minimum = -1;

	private Families() {
	}

	public static void setMinimum(int value) {
		minimum = value;
	}

	/** Jusqu'à combien d'habitants les nouveaux venus arrivent d'eux-mêmes. */
	public static int minimum() {
		return minimum >= 0 ? minimum : com.ajustor.minecities.McConfig.get().newcomerMinimum;
	}

	/** Toutes les 5 secondes, pour une ville chargée. */
	static void tick(ServerLevel level, Town town) {
		long now = level.getGameTime();
		int min = minimum();
		if (town.population() < min && now - town.lastArrival() >= newcomerDelay()) {
			TownService.immigrate(level, town);
		} else {
			attract(level, town, now);
		}
		List<CitizenEntity> here = new ArrayList<>();
		for (UUID id : town.residents().keySet()) {
			if (level.getEntity(id) instanceof CitizenEntity citizen && citizen.isAlive()) {
				here.add(citizen);
			}
		}
		befriend(level, town, here);
		births(level, town, now);
		growUp(level, town, now);
	}

	/** Au-delà de ce bonheur moyen, la taverne attire du monde (MC-58). */
	public static final double TAVERN_MOOD = 5;
	/** Ce qu'il faut de bonheur en plus, frontières ouvertes. */
	public static final double OPEN_MOOD = 2;

	/**
	 * Où en est la croissance de la ville, pour le Résumé : {@code key} dit ce qui se passe (des nouveaux
	 * venus sous le minimum, la taverne, ou ce qui l'empêche), {@code days} dans combien de jours arrive
	 * le prochain (ou le minimum d'habitants), {@code level} le niveau de la taverne.
	 */
	public record Growth(String key, int days, int level) {
	}

	public static Growth growth(Town town, long now) {
		int min = minimum();
		if (town.population() < min) {
			return new Growth("newcomers", min, 0);
		}
		java.util.Optional<Resident.Employment> taverner = town.residents().values().stream()
				.flatMap(r -> r.job().stream()).filter(j -> j.job() == Job.TAVERNER).findFirst();
		if (taverner.isEmpty()) {
			return new Growth("no_tavern", 0, 0);
		}
		int level = town.buildingLevel(taverner.get().workshop());
		if (town.freeBeds() <= 0) {
			return new Growth("no_bed", 0, level);
		}
		// Frontières ouvertes (MC-66) : deux jours de moins entre deux arrivants, mais une ville plus heureuse.
		boolean open = town.decreed(Decree.OPEN_BORDERS);
		double wanted = open ? TAVERN_MOOD + OPEN_MOOD : TAVERN_MOOD;
		if (town.averageMood().filter(m -> m < wanted).isPresent()) {
			return new Growth("unhappy", 0, level);
		}
		long spacing = Math.max(1, 6 - level - town.appeal() / 10 - (open ? 2 : 0)) * 24000L;
		long left = Math.max(0, town.lastArrival() + spacing - now);
		return new Growth("tavern", (int) ((left + 23999) / 24000), level);
	}

	/**
	 * La taverne attire un nouveau venu : avec un tavernier, un lit libre et une ville assez heureuse,
	 * un tous les {@code 6 - niveau de la taverne - attrait / 10} jours (au moins un). Vrai s'il en est venu un.
	 */
	public static boolean attract(ServerLevel level, Town town, long now) {
		Growth growth = growth(town, now);
		if (!growth.key().equals("tavern") || growth.days() > 0) {
			return false;
		}
		TownService.immigrate(level, town);
		return true;
	}

	/** Les enfants arrivés à l'âge adulte grandissent (et le disent). */
	public static void growUp(ServerLevel level, Town town, long now) {
		for (UUID grown : town.growUp(now)) {
			if (level.getEntity(grown) instanceof CitizenEntity citizen) {
				citizen.setChild(false);
				citizen.say("grown", Component.translatable("speech.minecities.grown"));
			}
		}
	}

	/** Les citoyens proches s'apprécient ; ceux qui s'entendent demandent à se marier. */
	private static void befriend(ServerLevel level, Town town, List<CitizenEntity> here) {
		for (int i = 0; i < here.size(); i++) {
			for (int j = i + 1; j < here.size(); j++) {
				CitizenEntity a = here.get(i);
				CitizenEntity b = here.get(j);
				if (a.isChild() || b.isChild() || a.distanceToSqr(b) > CLOSE * CLOSE) {
					continue;
				}
				int before = town.affinity(a.getUUID(), b.getUUID());
				int after = town.addAffinity(a.getUUID(), b.getUUID(), AFFINITY_GAIN);
				if (before < Town.FRIENDS && after >= Town.FRIENDS) {
					level.sendParticles(ParticleTypes.HEART, (a.getX() + b.getX()) / 2, a.getY() + 2.2,
							(a.getZ() + b.getZ()) / 2, 3, 0.4, 0.2, 0.4, 0);
				}
				town.askToMarry(a.getUUID(), b.getUUID(), level.getGameTime()).ifPresent(request -> {
					a.say("marry", Component.translatable("speech.minecities.ask_marry", b.getName()));
					b.say("marry", Component.translatable("speech.minecities.ask_marry", a.getName()));
					TownService.notify(level.getServer(), town, Component.translatable(
							"message.minecities.marriage.request", a.getName(), b.getName()));
				});
			}
		}
	}

	/** Le maire marie deux citoyens : cérémonie à l'hôtel de ville, puis ils emménagent ensemble. */
	public static boolean accept(ServerLevel level, Town town, MarriageRequest request) {
		if (!town.marry(request)) {
			return false;
		}
		String a = town.resident(request.first()).map(Resident::name).orElse("?");
		String b = town.resident(request.second()).map(Resident::name).orElse("?");
		level.playSound(null, town.hall(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0f, 1.0f);
		level.sendParticles(ParticleTypes.HEART, town.hall().getX() + 0.5, town.hall().getY() + 1.5,
				town.hall().getZ() + 0.5, 12, 0.8, 0.5, 0.8, 0);
		for (UUID id : new UUID[]{request.first(), request.second()}) {
			if (level.getEntity(id) instanceof CitizenEntity citizen) {
				citizen.say("married", Component.translatable("speech.minecities.married"));
				citizen.act(com.ajustor.minecities.citizen.Action.CHEER, 60);
				level.sendParticles(ParticleTypes.HEART, citizen.getX(), citizen.getY() + 2, citizen.getZ(), 5,
						0.3, 0.3, 0.3, 0);
			}
		}
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
				"message.minecities.marriage.done", a, b, town.name()), false);
		Towns.get(level.getServer()).changed();
		return true;
	}

	public static void refuse(ServerLevel level, Town town, MarriageRequest request) {
		town.refuse(request);
		Towns.get(level.getServer()).changed();
	}

	/** La nuit, les couples qui en ont les moyens peuvent avoir un enfant. */
	private static void births(ServerLevel level, Town town, long now) {
		if (!SleepInBedGoal.isNight(level)) {
			return;
		}
		for (UUID[] couple : town.couples()) {
			if (level.getRandom().nextInt(BIRTH_ODDS) == 0) {
				birth(level, town, couple[0], couple[1], now);
			}
		}
	}

	/**
	 * Un enfant naît de ce couple, s'il en a les moyens : logés ensemble, bien nourris, un lit libre
	 * chez eux, pas d'autre naissance depuis un jour. Renvoie l'enfant.
	 */
	public static Optional<CitizenEntity> birth(ServerLevel level, Town town, UUID first, UUID second, long now) {
		Optional<Resident> a = town.resident(first);
		Optional<Resident> b = town.resident(second);
		if (a.isEmpty() || b.isEmpty() || a.get().home().isEmpty() || !a.get().home().equals(b.get().home())
				|| now - a.get().lastBirth() < BIRTH_SPACING && a.get().lastBirth() > 0
				|| town.freeBedIn(a.get().home().get()).isEmpty()) {
			return Optional.empty();
		}
		Entity ea = level.getEntity(first);
		Entity eb = level.getEntity(second);
		if (!(ea instanceof CitizenEntity mother) || !(eb instanceof CitizenEntity father)
				|| mother.hunger() < Hunger.WELL_FED || father.hunger() < Hunger.WELL_FED
				|| mother.mood() < Happiness.CONTENT || father.mood() < Happiness.CONTENT) {
			return Optional.empty();
		}
		CitizenEntity child = McEntities.CITIZEN.create(level, EntitySpawnReason.BREEDING);
		if (child == null) {
			return Optional.empty();
		}
		String surname = level.getRandom().nextBoolean() ? a.get().surname() : b.get().surname();
		String name = Names.firstName(level.getRandom()::nextInt) + " " + surname;
		child.snapTo(mother.getX(), mother.getY(), mother.getZ(), mother.getYRot(), 0);
		child.joinTown(town, name);
		child.setChild(true);
		level.addFreshEntity(child);
		town.born(child.getUUID(), name, first, second, now, now + childhood(town));
		level.sendParticles(ParticleTypes.HEART, child.getX(), child.getY() + 1, child.getZ(), 8, 0.4, 0.4, 0.4, 0);
		mother.act(com.ajustor.minecities.citizen.Action.CHEER, 60);
		father.act(com.ajustor.minecities.citizen.Action.CHEER, 60);
		TownService.notify(level.getServer(), town, Component.translatable("message.minecities.birth", name,
				mother.getName(), father.getName()));
		town.updateTier();
		Towns.get(level.getServer()).changed();
		return Optional.of(child);
	}
}
