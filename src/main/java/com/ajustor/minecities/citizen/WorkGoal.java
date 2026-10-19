package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.House;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Resident;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Storage;
import com.ajustor.minecities.town.Town;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Le travail d'un citoyen employé : le jour, quand il n'a ni faim ni sommeil. Cette base tient
 * l'horaire, va chercher l'outil du métier (dans les coffres de l'atelier, sinon au grenier) et
 * mène le citoyen d'un bloc à l'autre ; chaque métier y ajoute son ouvrage.
 */
abstract class WorkGoal extends Goal {
	/** Rien à faire : il attend une minute avant de recompter. */
	protected static final int IDLE_LONG = 1200;
	protected static final int IDLE_SHORT = 200;
	/** Sans outil, il revérifie toutes les 20 secondes. */
	protected static final int IDLE_TOOL = 400;

	protected final CitizenEntity citizen;
	private final Job job;
	/** Jusqu'à ce moment (temps de jeu), le citoyen ne travaille pas : rien à faire, outil manquant… */
	private long idleUntil;
	private BlockPos errand;
	private int repath;
	/** Depuis quand il va vers sa cible sans s'en approcher, et le plus près qu'il en a été. */
	private long errandSince;
	private double errandBest;
	/** Les cibles qu'il n'a pas pu atteindre, et jusqu'à quand il les laisse de côté. */
	private final java.util.Map<BlockPos, Long> unreachable = new java.util.HashMap<>();
	/** Sans s'approcher d'un bloc en tant de ticks, il renonce à la cible ; pour tant de ticks. */
	private static final int GIVE_UP = 300;
	private static final int SHUNNED = 2400;
	private net.minecraft.world.entity.item.ItemEntity litter;
	private int litterTicks;
	private final java.util.Set<java.util.UUID> ignoredLitter = new java.util.HashSet<>();

	protected WorkGoal(CitizenEntity citizen, Job job) {
		this.citizen = citizen;
		this.job = job;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	/**
	 * Ceux qui nourrissent la ville travaillent tant qu'ils ne sont pas tout à fait affamés : s'ils
	 * s'arrêtaient dès la faim, un grenier vide ne se remplirait jamais plus.
	 */
	private static final java.util.Set<Job> FEEDERS = java.util.EnumSet.of(Job.FARMER, Job.FISHERMAN, Job.COOK,
			Job.BAKER, Job.BREEDER);

	private boolean onDuty() {
		Optional<Town> town = citizen.town();
		boolean starving = FEEDERS.contains(job) ? citizen.hunger() <= 0 : citizen.hunger() < Hunger.HUNGRY;
		if (citizen.job().filter(j -> j.job() == job).isEmpty() || town.isEmpty() || citizen.isSleeping()
				|| starving || citizen.bedridden() || !(citizen.level() instanceof ServerLevel)) {
			return false;
		}
		if (job.defends()) {
			return true;
		}
		// Jour férié : on ne travaille pas. Heures supplémentaires : de l'aube jusqu'à la nuit noire.
		if (town.get().decreed(com.ajustor.minecities.town.Decree.HOLIDAY)) {
			return false;
		}
		if (town.get().decreed(com.ajustor.minecities.town.Decree.OVERTIME)) {
			long time = Math.floorMod(citizen.level().getOverworldClockTime(), 24000L);
			return time < 13500 || time >= 23000;
		}
		return !SleepInBedGoal.bedtime(citizen);
	}

	@Override
	public boolean canUse() {
		return citizen.level().getGameTime() >= idleUntil && onDuty();
	}

	@Override
	public boolean canContinueToUse() {
		return citizen.level().getGameTime() >= idleUntil && onDuty();
	}

	@Override
	public void start() {
		errand = null;
	}

	@Override
	public void stop() {
		errand = null;
		citizen.getNavigation().stop();
		onStop();
	}

	protected void onStop() {
	}

	/**
	 * À chaque tick, tant qu'il peut continuer : entre deux ticks complets, le jeu fait tourner les buts en
	 * cours sans le leur demander. Un travailleur qui vient de se mettre au repos referait un tour (le
	 * joaillier reprenait au grenier ce qu'il venait de rendre, sans fin), et celui dont la ville vient de
	 * disparaître travaillerait sans ville ni emploi (le serveur plantait).
	 */
	@Override
	public boolean requiresUpdateEveryTick() {
		return canContinueToUse();
	}

	/** L'allure du travailleur entre deux tâches (le bâtisseur presse le pas). */
	protected double speed() {
		return 0.6;
	}

	/** Met le travail en pause pour {@code ticks} ticks. */
	protected void rest(int ticks) {
		// Heureux, il reprend plus vite ; malheureux, il traîne. Un atelier de haut niveau, mieux outillé,
		// fait gagner du temps (un dixième par niveau).
		float level = citizen.job().map(j -> town().buildingLevel(j.workshop())).orElse(1);
		float workshop = Math.max(0.6f, 1.1f - 0.1f * level);
		// Malade, il traîne encore ; expérimenté, il s'y remet plus vite (MC-83).
		double sick = citizen.illness().map(Disease::pace).orElse(1.0);
		float experience = citizen.job().map(j -> com.ajustor.minecities.town.Experience.of(j, citizen.level()
				.getGameTime()).pace()).orElse(1f);
		idleUntil = citizen.level().getGameTime() + Math.round(ticks * Happiness.workPace(citizen.mood()) * workshop
				* sick * experience);
	}

	protected ServerLevel level() {
		return (ServerLevel) citizen.level();
	}

	protected Town town() {
		return citizen.town().orElseThrow();
	}

	protected Resident.Employment employment() {
		return citizen.job().orElseThrow();
	}

	/**
	 * Avance vers ce bloc ; renvoie vrai une fois à portée de bras. Le chemin est recalculé toutes
	 * les deux secondes.
	 */
	protected boolean goTo(BlockPos target) {
		citizen.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
		if (citizen.reached(target)) {
			citizen.getNavigation().stop();
			return true;
		}
		long now = citizen.level().getGameTime();
		double distance = citizen.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
		if (!target.equals(errand)) {
			errand = target;
			repath = 0;
			errandSince = now;
			errandBest = distance;
		} else if (distance < errandBest - 1) {
			errandBest = distance;
			errandSince = now;
		} else if (now - errandSince > GIVE_UP && givesUp() && !target.equals(town().hall())) {
			// Inaccessible (derrière une clôture, sur un toit, sur un îlot) : il la laisse de côté un moment
			// et en choisira une autre, au lieu de s'y user toute la journée.
			unreachable.put(target.immutable(), now + SHUNNED);
			errand = null;
			citizen.getNavigation().stop();
			gaveUp(target);
			return false;
		}
		if (--repath <= 0 || citizen.getNavigation().isDone()) {
			repath = 40;
			citizen.walkTo(target, speed());
		}
		return false;
	}

	/**
	 * Renonce-t-il à une cible qu'il n'approche plus ? Oui, sauf ceux qui ont leur propre façon d'y aller
	 * (le bâtisseur, sur ses échafaudages). Le grenier de l'hôtel de ville, lui, n'est jamais laissé de côté.
	 */
	protected boolean givesUp() {
		return true;
	}

	/** Peut-il aller là ? (faux s'il vient d'y renoncer, faute de chemin) */
	protected boolean reachable(BlockPos pos) {
		Long until = unreachable.get(pos);
		return until == null || until <= citizen.level().getGameTime();
	}

	/** Il vient de renoncer à cette cible : à chaque métier d'en choisir une autre. */
	protected void gaveUp(BlockPos target) {
	}

	/** Les réserves de l'atelier (ses coffres), puis le grenier de l'hôtel de ville. */
	protected List<BlockPos> supplies() {
		List<BlockPos> supplies = new ArrayList<>();
		town().house(employment().workshop()).ifPresent(h -> supplies.addAll(h.containers()));
		// Puis les réserves communes : l'entrepôt, le grenier (brief 12).
		supplies.addAll(com.ajustor.minecities.town.Storage.stores(town()));
		return supplies;
	}

	/** Les contenants chargés de cette liste. */
	protected List<Container> pool(List<BlockPos> positions) {
		List<Container> pool = new ArrayList<>();
		for (BlockPos pos : positions) {
			container(pos).ifPresent(pool::add);
		}
		return pool;
	}

	/** Combien il y en a en tout dans ces contenants. */
	protected static int count(List<Container> pool, Predicate<ItemStack> filter) {
		return pool.stream().mapToInt(c -> Stock.count(c, filter)).sum();
	}

	/** En prend autant (au plus), d'une même sorte, dans ces contenants l'un après l'autre. */
	protected static ItemStack take(List<Container> pool, Predicate<ItemStack> filter, int count) {
		ItemStack all = ItemStack.EMPTY;
		for (Container c : pool) {
			while (count > 0) {
				ItemStack sample = all;
				ItemStack part = Stock.take(c, s -> filter.test(s) && (sample.isEmpty()
						|| ItemStack.isSameItemSameComponents(s, sample)), count);
				if (part.isEmpty()) {
					break;
				}
				if (all.isEmpty()) {
					all = part;
				} else {
					all.grow(part.getCount());
				}
				count -= part.getCount();
			}
		}
		return all;
	}

	/** Le premier contenant de la liste qui a ce qu'on cherche. */
	protected Optional<BlockPos> find(List<BlockPos> containers, Predicate<ItemStack> filter) {
		return containers.stream().filter(p -> reachable(p) && level().isLoaded(p) && level().getBlockEntity(p) instanceof Container c
				&& Stock.count(c, filter) > 0).findFirst();
	}

	protected Optional<Container> container(BlockPos pos) {
		return level().isLoaded(pos) && level().getBlockEntity(pos) instanceof Container c ? Optional.of(c)
				: Optional.empty();
	}

	/**
	 * S'assure d'avoir l'outil du métier en main. Renvoie vrai s'il l'a ; sinon il est en route pour
	 * aller le chercher, ou il n'y en a nulle part (le maire est prévenu, et le travail attend).
	 */
	protected boolean ensureTool() {
		Optional<Predicate<ItemStack>> tool = job.tool();
		if (tool.isEmpty() || tool.get().test(citizen.getMainHandItem())) {
			citizen.satisfied("tool");
			return true;
		}
		Predicate<ItemStack> isTool = tool.get();
		Optional<BlockPos> source = find(supplies(), isTool);
		if (source.isEmpty()) {
			citizen.complain("tool", Component.translatable("message.minecities.citizen.needs_tool",
					citizen.getName(), Component.translatable(job.translationKey()),
					Component.translatable("tool.minecities." + job.serializedName())),
					Component.translatable("speech.minecities.needs_tool",
							Component.translatable("tool.minecities." + job.serializedName())));
			rest(IDLE_TOOL);
			return false;
		}
		if (goTo(source.get())) {
			container(source.get()).ifPresent(c -> {
				ItemStack taken = Stock.take(c, isTool, 1);
				if (!taken.isEmpty()) {
					ItemStack previous = citizen.getMainHandItem();
					citizen.setItemSlot(EquipmentSlot.MAINHAND, taken);
					if (!previous.isEmpty()) {
						citizen.carry(previous);
					}
				}
			});
		}
		return false;
	}

	/**
	 * Dépose ce qu'il a dans les poches, en gardant ce que {@code kept} dit de garder (des semences, des
	 * pousses), {@code keep} de chaque sorte au plus (brief 12). Avec des livreurs en ville, il le laisse
	 * dans le coffre de son atelier (ils feront la navette) ; sinon, il le porte lui-même : la
	 * nourriture au grenier, le reste à l'entrepôt (au grenier s'il n'y en a pas). Il fait autant de
	 * voyages qu'il faut. Renvoie vrai une fois tout déposé (ou quand plus rien n'a de place).
	 */
	protected boolean deliver(Predicate<ItemStack> kept, int keep) {
		java.util.Map<Integer, Integer> spare = spare(kept, keep);
		if (spare.isEmpty()) {
			return true;
		}
		ItemStack first = citizen.inventory().get(spare.keySet().iterator().next());
		BlockPos target = depot(first);
		if (!goTo(target)) {
			return false;
		}
		boolean moved = false;
		Optional<Container> into = container(target);
		if (into.isPresent()) {
			boolean workshop = !Storage.stores(town()).contains(target);
			for (java.util.Map.Entry<Integer, Integer> e : spare.entrySet()) {
				ItemStack stack = citizen.inventory().get(e.getKey());
				if (!workshop && !Storage.depot(level(), town(), stack).equals(target)) {
					continue;
				}
				int count = e.getValue();
				ItemStack rest = Stock.insert(into.get(), stack.copyWithCount(count));
				if (rest.getCount() < count) {
					moved = true;
				}
				stack.setCount(stack.getCount() - count + rest.getCount());
			}
		}
		// Rien n'a trouvé de place : il garde le reste, plutôt que de tourner en rond.
		return !moved || spare(kept, keep).isEmpty();
	}

	/** Ce qu'il peut déposer, case par case (sans ce qu'il garde). */
	private java.util.Map<Integer, Integer> spare(Predicate<ItemStack> kept, int keep) {
		java.util.Map<net.minecraft.world.item.Item, Integer> held = new java.util.HashMap<>();
		java.util.Map<Integer, Integer> spare = new java.util.LinkedHashMap<>();
		List<ItemStack> inventory = citizen.inventory();
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.get(i);
			if (stack.isEmpty()) {
				continue;
			}
			int keepHere = 0;
			if (kept.test(stack)) {
				int already = held.getOrDefault(stack.getItem(), 0);
				keepHere = Math.max(0, Math.min(stack.getCount(), keep - already));
				held.put(stack.getItem(), already + keepHere);
			}
			if (stack.getCount() > keepHere) {
				spare.put(i, stack.getCount() - keepHere);
			}
		}
		return spare;
	}

	/**
	 * Où il dépose cette marchandise : le coffre de son atelier si des livreurs font la navette (et qu'il
	 * y a de la place), sinon la réserve commune qui lui revient.
	 */
	private BlockPos depot(ItemStack stack) {
		if (job != Job.COURIER && town().workers(Job.COURIER) > 0) {
			for (BlockPos chest : town().house(employment().workshop()).map(House::containers).orElse(List.of())) {
				if (container(chest).filter(c -> Storage.room(c, stack)).isPresent()) {
					return chest;
				}
			}
		}
		return Storage.depot(level(), town(), stack);
	}

	/** Ce qu'il porte, hors ce qu'il garde ({@code keep} de chaque sorte que {@code kept} retient). */
	protected int produce(Predicate<ItemStack> kept, int keep) {
		java.util.Map<net.minecraft.world.item.Item, Integer> held = new java.util.HashMap<>();
		int produce = 0;
		for (ItemStack stack : citizen.inventory()) {
			if (stack.isEmpty()) {
				continue;
			}
			if (kept.test(stack)) {
				int total = held.merge(stack.getItem(), stack.getCount(), Integer::sum);
				produce += Math.max(0, Math.min(stack.getCount(), total - keep));
			} else {
				produce += stack.getCount();
			}
		}
		return produce;
	}

	/** Le soir approche : on rentre déposer la journée. */
	protected boolean evening() {
		return Math.floorMod(level().getOverworldClockTime(), 24000L) >= 11000;
	}

	/** Ramasse ce qui traîne à ses pieds (pousses, pommes, butin des bêtes…). */
	protected void pickUp(Predicate<ItemStack> wanted) {
		for (net.minecraft.world.entity.item.ItemEntity item : level().getEntitiesOfClass(
				net.minecraft.world.entity.item.ItemEntity.class, citizen.getBoundingBox().inflate(1.5, 0.5, 1.5),
				e -> e.isAlive() && wanted.test(e.getItem()))) {
			ItemStack rest = Stock.insert(citizen.inventory(), item.getItem());
			if (rest.isEmpty()) {
				citizen.take(item, item.getItem().getCount());
				item.discard();
			} else {
				item.setItem(rest);
			}
		}
	}

	/**
	 * Va ramasser ce qui traîne par terre dans ce rayon autour d'un point (au sol, pas sur la cime
	 * d'un arbre). Un objet qu'il n'atteint pas en 10 secondes est laissé. Renvoie vrai tant qu'il
	 * est occupé à ramasser.
	 */
	protected boolean collectLitter(BlockPos center, int radius, Predicate<ItemStack> wanted) {
		pickUp(wanted);
		if (litter != null && (!litter.isAlive() || ++litterTicks > 200)) {
			if (litter.isAlive()) {
				ignoredLitter.add(litter.getUUID());
			}
			litter = null;
		}
		if (litter == null) {
			litterTicks = 0;
			litter = level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
							new net.minecraft.world.phys.AABB(center).inflate(radius, 4, radius),
							e -> e.isAlive() && e.onGround() && !ignoredLitter.contains(e.getUUID())
									&& wanted.test(e.getItem()))
					.stream().min(java.util.Comparator.comparingDouble(citizen::distanceToSqr)).orElse(null);
		}
		if (litter == null) {
			return false;
		}
		goTo(litter.blockPosition());
		return true;
	}

	/** Un atelier de niveau 4 (une demeure) : ses travailleurs y ont l'outillage du maître (brief 11). */
	protected boolean master() {
		return citizen.job().filter(j -> com.ajustor.minecities.town.Mastery.active(town(), j.workshop())).isPresent();
	}

	/**
	 * L'outil tel que le manie un maître : au niveau 4, comme enchanté (fortune pour le fermier et le
	 * mineur, chance de la mer pour le pêcheur). Ailleurs, l'outil tel quel. L'outil lui-même n'est pas
	 * touché : c'est le savoir-faire qui compte.
	 */
	protected ItemStack masterTool(ItemStack tool, net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> enchantment,
			int level) {
		if (!master() || tool.isEmpty()) {
			return tool;
		}
		ItemStack copy = tool.copy();
		copy.enchant(level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
				.getOrThrow(enchantment), level);
		return copy;
	}

	/** Le poste de travail est-il toujours là ? */
	protected boolean stationStands() {
		Resident.Employment work = employment();
		return town().house(work.workshop()).map(House::workstations)
				.filter(l -> l.stream().anyMatch(w -> w.pos().equals(work.station()))).isPresent();
	}
}
