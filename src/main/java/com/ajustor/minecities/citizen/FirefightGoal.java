package com.ajustor.minecities.citizen;

import com.ajustor.minecities.compat.EntityCompat;
import com.ajustor.minecities.town.Disasters;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Storage;
import com.ajustor.minecities.town.Town;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Au feu (MC-14) ! Quand un bâtiment de la ville brûle, les adultes valides laissent tout : ils
 * prennent un seau d'eau aux réserves (ou au grenier), courent aux flammes et les éteignent, seau
 * après seau. Sans seau d'eau, ils le crient : il faut en mettre au grenier.
 */
class FirefightGoal extends Goal {
	private final CitizenEntity citizen;
	private BlockPos target;
	private int recheck;
	private int cooldown;

	FirefightGoal(CitizenEntity citizen) {
		this.citizen = citizen;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	private Optional<Town> burning() {
		if (!(citizen.level() instanceof ServerLevel level) || citizen.isChild() || citizen.isSleeping()
				|| citizen.bedridden()) {
			return Optional.empty();
		}
		long now = level.getGameTime();
		return citizen.town().filter(t -> t.chronicle().disaster(now).filter(Disasters.FIRE::equals).isPresent());
	}

	/**
	 * Il ne cherche le feu qu'un appel sur dix. Un compteur à lui, et pas {@code tickCount % 20} : le jeu
	 * n'appelle {@code canUse} qu'un tick sur deux, selon la parité de l'identifiant, et la moitié des
	 * citoyens ne voyaient jamais le feu.
	 */
	private int checks;

	@Override
	public boolean canUse() {
		if (cooldown > 0) {
			cooldown--;
			return false;
		}
		if (++checks % 10 != 0 || burning().isEmpty()) {
			return false;
		}
		target = nearestFire();
		return target != null;
	}

	@Override
	public boolean canContinueToUse() {
		return target != null && burning().isPresent();
	}

	@Override
	public void start() {
		recheck = 0;
		citizen.say("fire", Component.translatable("speech.minecities.fire"));
	}

	@Override
	public void stop() {
		target = null;
		citizen.getNavigation().stop();
	}

	private BlockPos nearestFire() {
		ServerLevel level = (ServerLevel) citizen.level();
		return burning().flatMap(t -> Disasters.fires(level, t).stream()
				.min(Comparator.comparingDouble(p -> citizen.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5))))
				.orElse(null);
	}

	private boolean hasWater() {
		return citizen.inventory().stream().anyMatch(s -> s.is(Items.WATER_BUCKET));
	}

	@Override
	public void tick() {
		ServerLevel level = (ServerLevel) citizen.level();
		Town town = burning().orElse(null);
		if (town == null) {
			target = null;
			return;
		}
		if (!hasWater()) {
			// D'abord un seau d'eau : aux réserves, sinon au grenier.
			List<BlockPos> stores = new ArrayList<>(Storage.stores(town));
			if (!stores.contains(town.hall())) {
				stores.add(town.hall());
			}
			Optional<BlockPos> source = stores.stream().filter(p -> level.isLoaded(p)
					&& level.getBlockEntity(p) instanceof Container c && Stock.count(c, s -> s.is(Items.WATER_BUCKET)) > 0)
					.min(Comparator.comparingDouble(p -> citizen.distanceToSqr(p.getX(), p.getY(), p.getZ())));
			if (source.isEmpty()) {
				citizen.need("fire_bucket", Component.translatable("speech.minecities.fire_bucket"));
				target = null;
				cooldown = 200;
				return;
			}
			if (!citizen.reached(source.get())) {
				if (--recheck <= 0) {
					recheck = 20;
					citizen.walkTo(source.get(), 0.75);
				}
				return;
			}
			if (level.getBlockEntity(source.get()) instanceof Container c) {
				ItemStack bucket = Stock.take(c, s -> s.is(Items.WATER_BUCKET), 1);
				citizen.carry(bucket);
				citizen.satisfied("fire_bucket");
			}
			return;
		}
		if (--recheck <= 0) {
			recheck = 20;
			target = nearestFire();
			if (target == null) {
				return;
			}
			citizen.walkTo(target, 0.75);
		}
		if (target != null && within(target)) {
			citizen.getNavigation().stop();
			citizen.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
			EntityCompat.swing(citizen, InteractionHand.MAIN_HAND);
			Disasters.douse(level, target);
			target = null;
			recheck = 10;
		}
	}

	/** Assez près pour jeter son seau : trois blocs à l'horizontale, jusqu'à cinq de haut (un toit). */
	private boolean within(BlockPos fire) {
		double dx = fire.getX() + 0.5 - citizen.getX();
		double dz = fire.getZ() + 0.5 - citizen.getZ();
		return dx * dx + dz * dz <= 9 && Math.abs(fire.getY() - citizen.getY()) <= 5;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}
}
