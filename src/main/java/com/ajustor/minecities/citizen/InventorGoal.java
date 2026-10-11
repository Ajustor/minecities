package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Raids;
import com.ajustor.minecities.town.Stock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.Optional;

/**
 * L'inventeur, à la table de forge. Quand la ville est attaquée (un raid, ou des monstres autour de
 * l'hôtel de ville), il prend au grenier (ou dans son atelier) de quoi faire un golem et l'assemble
 * devant l'hôtel de ville : quatre blocs de fer et une citrouille sculptée font un golem de fer, deux
 * blocs de neige et une citrouille un golem de neige. Les golems défendent la ville et restent.
 */
class InventorGoal extends WorkGoal {
	/** Un golem toutes les 15 secondes au plus. */
	private static final int ASSEMBLY = 300;
	/** Au-delà de tant de monstres près de l'hôtel de ville, la ville est attaquée. */
	private static final int THREAT = 3;

	private static final Map<Item, Integer> IRON_GOLEM = Map.of(Items.IRON_BLOCK, 4, Items.CARVED_PUMPKIN, 1);
	private static final Map<Item, Integer> SNOW_GOLEM = Map.of(Items.SNOW_BLOCK, 2, Items.CARVED_PUMPKIN, 1);

	private long nextGolem;

	InventorGoal(CitizenEntity citizen) {
		super(citizen, Job.INVENTOR);
	}

	@Override
	protected double speed() {
		return 0.8;
	}

	private boolean underAttack() {
		if (Raids.underAttack(town().id())) {
			return true;
		}
		BlockPos hall = town().hall();
		return level().getEntitiesOfClass(Mob.class, new AABB(hall).inflate(24, 8, 24), m -> m instanceof Enemy
				&& m.isAlive()).size() >= THREAT;
	}

	@Override
	public void tick() {
		if (!stationStands()) {
			rest(IDLE_SHORT);
			return;
		}
		if (!underAttack()) {
			// En temps de paix, il bricole à son établi.
			if (goTo(employment().station())) {
				citizen.act(Action.BUILD, 20);
			}
			rest(40);
			return;
		}
		if (level().getGameTime() < nextGolem) {
			return;
		}
		Map<Item, Integer> recipe = carries(IRON_GOLEM) ? IRON_GOLEM : carries(SNOW_GOLEM) ? SNOW_GOLEM
				: available(IRON_GOLEM) ? IRON_GOLEM : available(SNOW_GOLEM) ? SNOW_GOLEM : null;
		if (recipe == null) {
			citizen.complain("golem", Component.translatable("message.minecities.inventor.missing", citizen.getName()),
					Component.translatable("speech.minecities.no_golem"));
			rest(200);
			return;
		}
		if (!carries(recipe)) {
			fetch(recipe);
			return;
		}
		// Il assemble le golem devant l'hôtel de ville.
		BlockPos front = town().hall().relative(net.minecraft.core.Direction.NORTH, 3);
		if (!goTo(front)) {
			return;
		}
		assemble(recipe, front);
	}

	private int count(Item item) {
		return citizen.inventory().stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
	}

	private boolean carries(Map<Item, Integer> recipe) {
		return recipe.entrySet().stream().allMatch(e -> count(e.getKey()) >= e.getValue());
	}

	private boolean available(Map<Item, Integer> recipe) {
		return recipe.entrySet().stream().allMatch(e -> count(e.getKey()) >= e.getValue()
				|| find(supplies(), s -> s.is(e.getKey())).isPresent());
	}

	private void fetch(Map<Item, Integer> recipe) {
		for (Map.Entry<Item, Integer> e : recipe.entrySet()) {
			int missing = e.getValue() - count(e.getKey());
			if (missing <= 0) {
				continue;
			}
			Optional<BlockPos> source = find(supplies(), s -> s.is(e.getKey()));
			if (source.isEmpty()) {
				return;
			}
			if (goTo(source.get())) {
				container(source.get()).ifPresent(c -> citizen.carry(Stock.take(c, s -> s.is(e.getKey()), missing)));
			}
			return;
		}
	}

	private void assemble(Map<Item, Integer> recipe, BlockPos at) {
		for (Map.Entry<Item, Integer> e : recipe.entrySet()) {
			int left = e.getValue();
			for (ItemStack stack : citizen.inventory()) {
				if (left > 0 && stack.is(e.getKey())) {
					int used = Math.min(left, stack.getCount());
					stack.shrink(used);
					left -= used;
				}
			}
		}
		Mob golem = recipe == IRON_GOLEM ? EntityTypes.IRON_GOLEM.create(level(), EntitySpawnReason.MOB_SUMMONED)
				: EntityTypes.SNOW_GOLEM.create(level(), EntitySpawnReason.MOB_SUMMONED);
		if (golem == null) {
			return;
		}
		if (golem instanceof IronGolem iron) {
			// Fait de main d'homme : il ne s'en prend ni aux joueurs ni aux citoyens.
			iron.setPlayerCreated(true);
		}
		golem.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, citizen.getYRot(), 0);
		golem.setPersistenceRequired();
		level().addFreshEntity(golem);
		citizen.swing(InteractionHand.MAIN_HAND);
		citizen.act(Action.CHEER, 40);
		citizen.say("golem", Component.translatable("speech.minecities.golem"));
		level().playSound(null, at, recipe == IRON_GOLEM ? SoundEvents.IRON_GOLEM_REPAIR : SoundEvents.SNOW_GOLEM_AMBIENT,
				SoundSource.NEUTRAL, 1.0f, 1.0f);
		level().sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 20, 0.5, 1.0, 0.5, 0.02);
		nextGolem = level().getGameTime() + ASSEMBLY;
	}
}
