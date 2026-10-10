package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.House;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Resident;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
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

	protected WorkGoal(CitizenEntity citizen, Job job) {
		this.citizen = citizen;
		this.job = job;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	private boolean onDuty() {
		return citizen.job().filter(j -> j.job() == job).isPresent() && citizen.town().isPresent()
				&& !SleepInBedGoal.isNight(citizen.level()) && !citizen.isSleeping()
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

	/** Met le travail en pause pour {@code ticks} ticks. */
	protected void rest(int ticks) {
		idleUntil = citizen.level().getGameTime() + ticks;
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
			citizen.walkTo(target, 0.6);
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
		Optional<TagKey<Item>> tool = job.tool();
		if (tool.isEmpty() || citizen.getMainHandItem().is(tool.get())) {
			return true;
		}
		Predicate<ItemStack> isTool = s -> s.is(tool.get());
		Optional<BlockPos> source = find(supplies(), isTool);
		if (source.isEmpty()) {
			citizen.complain("tool", Component.translatable("message.minecities.citizen.needs_tool",
					citizen.getName(), Component.translatable(job.translationKey()),
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

	/** Le poste de travail est-il toujours là ? */
	protected boolean stationStands() {
		Resident.Employment work = employment();
		return town().house(work.workshop()).map(House::workstations)
				.filter(l -> l.stream().anyMatch(w -> w.pos().equals(work.station()))).isPresent();
	}
}
