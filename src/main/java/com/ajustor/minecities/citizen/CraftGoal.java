package com.ajustor.minecities.citizen;

import com.ajustor.minecities.compat.EntityCompat;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Stock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Un artisan (brief 11) : il prend au grenier de quoi faire un ouvrage, le fait à son poste, et le
 * rapporte. Ses recettes sont dans l'ordre de ses préférences ; il ne refait pas ce dont le grenier
 * a déjà assez. Le joaillier, le maçon, le verrier, le tisserand et le bibliothécaire travaillent ainsi.
 */
class CraftGoal extends WorkGoal {
	/** Un ingrédient : ce qui convient, et combien. */
	record Input(Predicate<ItemStack> accepts, int count) {
	}

	/**
	 * Un ouvrage : ses ingrédients, ce qu'il donne, et le stock au-delà duquel le grenier en a assez
	 * (0 : sans limite).
	 */
	record Craft(List<Input> inputs, ItemStack result, int enough) {
	}

	/** On emporte de quoi faire tant d'ouvrages à la fois. */
	private static final int BATCH = 8;

	private final java.util.function.Supplier<List<Craft>> recipes;
	private List<Craft> crafts = List.of();
	private final int workTime;
	private final Action gesture;
	private final SoundEvent sound;
	private final ParticleOptions particle;

	private enum Phase { DECIDE, WORK, DELIVER }

	private Phase phase = Phase.DECIDE;
	private Craft current;
	private int timer;
	/** Le contenant où il va chercher de quoi travailler. */
	private BlockPos source;

	CraftGoal(CitizenEntity citizen, Job job, List<Craft> crafts, int workTime, Action gesture, SoundEvent sound,
			ParticleOptions particle) {
		this(citizen, job, () -> crafts, workTime, gesture, sound, particle);
	}

	CraftGoal(CitizenEntity citizen, Job job, java.util.function.Supplier<List<Craft>> recipes, int workTime,
			Action gesture, SoundEvent sound, ParticleOptions particle) {
		super(citizen, job);
		this.recipes = recipes;
		this.workTime = workTime;
		this.gesture = gesture;
		this.sound = sound;
		this.particle = particle;
	}

	CraftGoal(CitizenEntity citizen, Job job, List<Craft> crafts, int workTime, Action gesture, SoundEvent sound) {
		this(citizen, job, crafts, workTime, gesture, sound, ParticleTypes.CRIT);
	}

	static Input of(net.minecraft.world.item.Item item, int count) {
		return new Input(s -> s.is(item), count);
	}

	@Override
	public void start() {
		super.start();
		// Interrompu (un repas, la nuit) avec de quoi finir son ouvrage : il le reprend, au lieu de tout
		// rapporter et de revenir le chercher.
		if (current != null && has(current)) {
			phase = Phase.WORK;
			return;
		}
		phase = citizen.inventory().stream().allMatch(ItemStack::isEmpty) ? Phase.DECIDE : Phase.DELIVER;
	}

	@Override
	public void tick() {
		if (!stationStands()) {
			rest(IDLE_SHORT);
			return;
		}
		switch (phase) {
			case DECIDE -> decide();
			case WORK -> work();
			case DELIVER -> {
				if (deliver(s -> false, 0)) {
					phase = Phase.DECIDE;
					rest(60);
				}
			}
		}
	}

	/**
	 * Combien d'ouvrages permettent ces réserves (tous les contenants ensemble : l'entrepôt range chaque
	 * sorte à part), sans dépasser ce dont la ville a besoin ({@code stocked} en réserve).
	 */
	static int possible(List<Container> pool, Craft craft, int stocked) {
		int batches = BATCH;
		for (Input input : craft.inputs()) {
			batches = Math.min(batches, count(pool, input.accepts()) / input.count());
		}
		if (craft.enough() > 0) {
			int wanted = (craft.enough() - stocked + craft.result().getCount() - 1) / craft.result().getCount();
			batches = Math.min(batches, Math.max(0, wanted));
		}
		return batches;
	}

	/** Ce que la ville a déjà de cet ouvrage, dans ces réserves. */
	private static int stocked(List<Container> pool, Craft craft) {
		return count(pool, s -> ItemStack.isSameItemSameComponents(s, craft.result()));
	}

	/**
	 * Le premier contenant (de l'atelier, de l'entrepôt, du grenier) où se trouve un ingrédient d'un
	 * ouvrage que les réserves permettent : il y va, et rassemble de là tout ce qu'il lui faut.
	 */
	private Optional<BlockPos> source() {
		List<Container> pool = pool(supplies());
		List<Craft> doable = crafts.stream().filter(craft -> possible(pool, craft, stocked(pool, craft)) > 0).toList();
		if (doable.isEmpty()) {
			return Optional.empty();
		}
		return find(supplies(), s -> doable.stream()
				.anyMatch(craft -> craft.inputs().stream().anyMatch(input -> input.accepts().test(s))));
	}

	private void decide() {
		crafts = recipes.get();
		if (source == null) {
			source = source().orElse(null);
			if (source == null) {
				rest(IDLE_LONG / 2);
				return;
			}
		}
		if (!goTo(source)) {
			return;
		}
		source = null;
		List<Container> pool = pool(supplies());
		for (Craft craft : crafts) {
			int batches = possible(pool, craft, stocked(pool, craft));
			if (batches <= 0) {
				continue;
			}
			for (Input input : craft.inputs()) {
				int left = input.count() * batches;
				while (left > 0) {
					ItemStack taken = take(pool, input.accepts(), left);
					if (taken.isEmpty()) {
						break;
					}
					left -= taken.getCount();
					citizen.carry(taken);
				}
			}
			current = craft;
			timer = 0;
			phase = Phase.WORK;
			return;
		}
		rest(IDLE_LONG / 2);
	}

	private boolean has(Craft craft) {
		return craft.inputs().stream().allMatch(input -> citizen.inventory().stream().filter(input.accepts())
				.mapToInt(ItemStack::getCount).sum() >= input.count());
	}

	private void work() {
		BlockPos station = employment().station();
		if (!goTo(station)) {
			return;
		}
		citizen.act(gesture, 10);
		if (++timer < workTime) {
			if (timer % 10 == 0) {
				level().sendParticles(particle, station.getX() + 0.5, station.getY() + 1.1, station.getZ() + 0.5, 3,
						0.2, 0.1, 0.2, 0.02);
			}
			return;
		}
		timer = 0;
		if (current == null || !has(current)) {
			phase = Phase.DELIVER;
			return;
		}
		for (Input input : current.inputs()) {
			int left = input.count();
			for (ItemStack stack : citizen.inventory()) {
				if (left > 0 && input.accepts().test(stack)) {
					int used = Math.min(left, stack.getCount());
					stack.shrink(used);
					left -= used;
				}
			}
		}
		EntityCompat.swing(citizen, InteractionHand.MAIN_HAND);
		citizen.carry(current.result().copy());
		level().playSound(null, station, sound, SoundSource.BLOCKS, 0.7f, 1.0f);
	}
}
