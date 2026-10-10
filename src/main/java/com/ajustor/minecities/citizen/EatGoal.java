package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Resident;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Quand il a faim, le citoyen va se servir : dans un coffre de sa maison s'il y trouve à manger,
 * sinon au grenier de l'hôtel de ville. Il mange jusqu'à être rassasié, une bouchée par seconde.
 */
public class EatGoal extends Goal {
	/** S'il n'y a rien à manger nulle part, il ne recompte pas avant une minute. */
	private static final int NOTHING_COOLDOWN = 1200;
	/** S'il n'arrive pas jusqu'au garde-manger, il abandonne au bout de 30 secondes. */
	private static final int GIVE_UP = 600;

	private final CitizenEntity citizen;
	private BlockPos pantry;
	private int cooldown;
	private int elapsed;
	private int bite;

	public EatGoal(CitizenEntity citizen) {
		this.citizen = citizen;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (cooldown > 0) {
			cooldown--;
			return false;
		}
		if (citizen.hunger() >= Hunger.HUNGRY || citizen.isSleeping()
				|| !(citizen.level() instanceof ServerLevel level)) {
			return false;
		}
		Optional<Town> town = citizen.town();
		Optional<Resident> resident = citizen.resident();
		if (town.isEmpty() || resident.isEmpty()) {
			return false;
		}
		List<BlockPos> pantries = TownService.pantries(level, town.get(), resident.get());
		if (pantries.isEmpty()) {
			cooldown = NOTHING_COOLDOWN;
			return false;
		}
		pantry = pantries.getFirst();
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return pantry != null && elapsed < GIVE_UP && citizen.hunger() < Hunger.SATED && !citizen.isSleeping();
	}

	@Override
	public void start() {
		elapsed = 0;
		bite = 0;
		moveToPantry();
	}

	private void moveToPantry() {
		citizen.getNavigation().moveTo(pantry.getX() + 0.5, pantry.getY(), pantry.getZ() + 0.5, 0.6);
	}

	@Override
	public void tick() {
		elapsed++;
		citizen.getLookControl().setLookAt(pantry.getX() + 0.5, pantry.getY() + 0.5, pantry.getZ() + 0.5);
		if (citizen.distanceToSqr(pantry.getX() + 0.5, pantry.getY() + 0.5, pantry.getZ() + 0.5) > 2.5 * 2.5) {
			if (elapsed % 40 == 0) {
				moveToPantry();
			}
			return;
		}
		citizen.getNavigation().stop();
		if (--bite > 0) {
			return;
		}
		bite = 20;
		if (!(citizen.level().getBlockEntity(pantry) instanceof Container container)) {
			pantry = null;
			return;
		}
		ItemStack food = TownService.takeFood(container);
		if (food.isEmpty()) {
			pantry = null;
			return;
		}
		citizen.eat(food);
	}

	@Override
	public void stop() {
		if (pantry != null && elapsed >= GIVE_UP) {
			cooldown = NOTHING_COOLDOWN / 2;
		}
		pantry = null;
		citizen.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}
}
