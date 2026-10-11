package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.House;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Resident;
import com.ajustor.minecities.town.Stock;
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
	private net.minecraft.world.entity.item.ItemEntity litter;
	private int litterTicks;
	private final java.util.Set<java.util.UUID> ignoredLitter = new java.util.HashSet<>();

	protected WorkGoal(CitizenEntity citizen, Job job) {
		this.citizen = citizen;
		this.job = job;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	private boolean onDuty() {
		return citizen.job().filter(j -> j.job() == job).isPresent() && citizen.town().isPresent()
				&& (job.defends() || !SleepInBedGoal.isNight(citizen.level())) && !citizen.isSleeping()
				&& citizen.hunger() >= Hunger.HUNGRY && citizen.level() instanceof ServerLevel;
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

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	/** L'allure du travailleur entre deux tâches (le bâtisseur presse le pas). */
	protected double speed() {
		return 0.6;
	}

	/** Met le travail en pause pour {@code ticks} ticks. */
	protected void rest(int ticks) {
		// Heureux, il reprend plus vite ; malheureux, il traîne.
		idleUntil = citizen.level().getGameTime() + Math.round(ticks * Happiness.workPace(citizen.mood()));
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
		if (!target.equals(errand) || --repath <= 0 || citizen.getNavigation().isDone()) {
			errand = target;
			repath = 40;
			citizen.walkTo(target, speed());
		}
		return false;
	}

	/** Les réserves de l'atelier (ses coffres), puis le grenier de l'hôtel de ville. */
	protected List<BlockPos> supplies() {
		List<BlockPos> supplies = new ArrayList<>();
		town().house(employment().workshop()).ifPresent(h -> supplies.addAll(h.containers()));
		supplies.add(town().hall());
		return supplies;
	}

	/** Le premier contenant de la liste qui a ce qu'on cherche. */
	protected Optional<BlockPos> find(List<BlockPos> containers, Predicate<ItemStack> filter) {
		return containers.stream().filter(p -> level().isLoaded(p) && level().getBlockEntity(p) instanceof Container c
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
	 * Porte au grenier ce qu'il a dans les poches, en gardant ce que {@code kept} dit de garder (des
	 * semences, des pousses), {@code keep} de chaque sorte au plus. Renvoie vrai une fois livré.
	 */
	protected boolean deliver(Predicate<ItemStack> kept, int keep) {
		BlockPos granary = town().hall();
		if (!goTo(granary)) {
			return false;
		}
		container(granary).ifPresent(container -> {
			java.util.Map<net.minecraft.world.item.Item, Integer> held = new java.util.HashMap<>();
			List<ItemStack> inventory = citizen.inventory();
			for (ItemStack stack : inventory) {
				if (stack.isEmpty()) {
					continue;
				}
				int keepHere = 0;
				if (kept.test(stack)) {
					int already = held.getOrDefault(stack.getItem(), 0);
					keepHere = Math.min(stack.getCount(), keep - already);
					held.put(stack.getItem(), already + keepHere);
				}
				ItemStack rest = Stock.insert(container, stack.copyWithCount(stack.getCount() - keepHere));
				stack.setCount(keepHere + rest.getCount());
			}
		});
		return true;
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

	/** Le poste de travail est-il toujours là ? */
	protected boolean stationStands() {
		Resident.Employment work = employment();
		return town().house(work.workshop()).map(House::workstations)
				.filter(l -> l.stream().anyMatch(w -> w.pos().equals(work.station()))).isPresent();
	}
}
