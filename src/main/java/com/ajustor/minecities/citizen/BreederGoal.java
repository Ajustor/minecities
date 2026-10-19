package com.ajustor.minecities.citizen;

import com.ajustor.minecities.compat.EntityCompat;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Stock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * L'éleveur, à la botte de foin. Il veille sur les bêtes à 16 blocs de sa botte : quand une espèce
 * compte au moins deux adultes et moins de {@link #HERD} têtes, il prend au grenier de quoi les
 * nourrir (blé, carottes, graines…) et en met deux en amour ; au-delà de {@link #CULL}, il abat un
 * adulte. Il ramasse ce qu'elles laissent (viande, cuir, laine, plumes, œufs) et le porte au
 * grenier.
 */
class BreederGoal extends WorkGoal {
	static final int PASTURE_RADIUS = 16;
	/** En dessous, il fait naître des petits. */
	static final int HERD = 6;
	/** Au-delà, il abat le surplus. */
	static final int CULL = 8;
	private static final int LOAD = 32;
	/** Ce que les bêtes laissent, et que l'éleveur ramasse. */
	private static final Predicate<ItemStack> ANIMAL_LOOT = s -> s.is(ItemTags.WOOL) || s.is(ItemTags.EGGS)
			|| s.is(Items.LEATHER) || s.is(Items.FEATHER) || s.is(Items.RABBIT_HIDE) || s.is(Items.RABBIT_FOOT)
			|| s.is(Items.BEEF) || s.is(Items.PORKCHOP) || s.is(Items.CHICKEN) || s.is(Items.MUTTON)
			|| s.is(Items.RABBIT);

	private enum Task { NONE, FETCH, FEED, CULL, GET, SHEAR, MILK }

	/** Ses cisailles et ses seaux vides : il les garde, il ne les rapporte pas avec la récolte. */
	private static final Predicate<ItemStack> GEAR = s -> s.is(Items.SHEARS) || s.is(Items.BUCKET);
	/** Combien de seaux de lait il garde d'avance dans les réserves (le remède de la dysenterie). */
	private static final int MILK_KEPT = 2;

	/** L'outil qu'il va chercher aux réserves. */
	private net.minecraft.world.item.Item wanted;

	private Task task = Task.NONE;
	private EntityType<?> species;
	private Animal target;
	private int scan;
	private int repath;
	private boolean delivering;

	BreederGoal(CitizenEntity citizen) {
		super(citizen, Job.BREEDER);
	}

	@Override
	protected void onStop() {
		task = Task.NONE;
		target = null;
	}

	private List<Animal> herd() {
		BlockPos station = employment().station();
		return level().getEntitiesOfClass(Animal.class, new AABB(station).inflate(PASTURE_RADIUS, 4, PASTURE_RADIUS),
				a -> a.isAlive() && town().contains(level().dimension(), a.blockPosition()));
	}

	@Override
	public void tick() {
		if (!stationStands()) {
			rest(IDLE_SHORT);
			return;
		}
		pickUp(ANIMAL_LOOT);
		int produce = produce(GEAR, 64);
		if (delivering || produce >= LOAD || citizen.inventoryFull() || evening() && produce > 0 && task == Task.NONE) {
			delivering = !deliver(GEAR, 64);
			return;
		}
		switch (task) {
			case NONE -> plan(produce);
			case FETCH -> fetch();
			case FEED -> feed();
			case CULL -> cull();
			case GET -> get();
			case SHEAR, MILK -> harvest();
		}
	}

	/** Choisit la prochaine tâche : abattre un surplus, faire naître des petits, ou ramasser. */
	private void plan(int produce) {
		if (collectLitter(employment().station(), PASTURE_RADIUS, ANIMAL_LOOT)) {
			return;
		}
		if (--scan > 0) {
			return;
		}
		scan = 40;
		Map<EntityType<?>, List<Animal>> bySpecies = new HashMap<>();
		for (Animal animal : herd()) {
			bySpecies.computeIfAbsent(animal.getType(), t -> new java.util.ArrayList<>()).add(animal);
		}
		for (Map.Entry<EntityType<?>, List<Animal>> e : bySpecies.entrySet()) {
			List<Animal> adults = e.getValue().stream().filter(a -> !a.isBaby()).toList();
			if (adults.size() > CULL) {
				species = e.getKey();
				target = adults.getFirst();
				task = Task.CULL;
				return;
			}
		}
		for (Map.Entry<EntityType<?>, List<Animal>> e : bySpecies.entrySet()) {
			List<Animal> ready = e.getValue().stream().filter(a -> !a.isBaby() && a.canFallInLove()
					&& a.getAge() == 0).toList();
			if (e.getValue().size() < HERD && ready.size() >= 2 && hasFoodFor(ready.getFirst())) {
				species = e.getKey();
				task = carriesFoodFor(ready.getFirst()) >= 2 ? Task.FEED : Task.FETCH;
				return;
			}
		}
		// La laine et le lait (MC-61) : il tond un mouton prêt, et trait une vache quand le lait manque aux
		// réserves ; il va d'abord chercher cisailles ou seau s'il n'en a pas.
		Optional<Animal> sheep = herd().stream().filter(a -> a instanceof net.minecraft.world.entity.animal.sheep.Sheep s
				&& s.readyForShearing()).findFirst();
		if (sheep.isPresent() && equip(Items.SHEARS)) {
			target = sheep.get();
			task = Task.SHEAR;
			return;
		}
		Optional<Animal> cow = herd().stream().filter(a -> a instanceof net.minecraft.world.entity.animal.cow.AbstractCow
				&& !a.isBaby()).findFirst();
		if (cow.isPresent() && count(pool(supplies()), s -> s.is(Items.MILK_BUCKET)) < MILK_KEPT
				&& citizen.inventory().stream().noneMatch(s -> s.is(Items.MILK_BUCKET)) && equip(Items.BUCKET)) {
			target = cow.get();
			task = Task.MILK;
			return;
		}
		if (produce > 0) {
			delivering = true;
		} else {
			rest(IDLE_SHORT);
		}
	}

	/**
	 * A-t-il cet outil en poche ? Sinon, s'il y en a aux réserves, il part le chercher (et renvoie faux).
	 */
	private boolean equip(net.minecraft.world.item.Item item) {
		if (citizen.inventory().stream().anyMatch(s -> s.is(item))) {
			return true;
		}
		if (find(supplies(), s -> s.is(item)).isPresent()) {
			wanted = item;
			task = Task.GET;
		}
		return false;
	}

	private void get() {
		Optional<BlockPos> source = find(supplies(), s -> s.is(wanted));
		if (source.isEmpty()) {
			task = Task.NONE;
			return;
		}
		if (goTo(source.get())) {
			container(source.get()).ifPresent(c -> citizen.carry(Stock.take(c, s -> s.is(wanted), 1)));
			task = Task.NONE;
		}
	}

	/** Tond le mouton, ou trait la vache, une fois à côté. */
	private void harvest() {
		if (target == null || !target.isAlive()) {
			task = Task.NONE;
			target = null;
			return;
		}
		citizen.getLookControl().setLookAt(target);
		if (citizen.distanceToSqr(target) > 2.5 * 2.5) {
			follow(target);
			return;
		}
		citizen.getNavigation().stop();
		EntityCompat.swing(citizen, InteractionHand.MAIN_HAND);
		if (task == Task.SHEAR && target instanceof net.minecraft.world.entity.animal.sheep.Sheep sheep
				&& sheep.readyForShearing()) {
			citizen.inventory().stream().filter(s -> s.is(Items.SHEARS)).findFirst().ifPresent(shears -> {
				sheep.shear(level(), net.minecraft.sounds.SoundSource.NEUTRAL, shears);
				// Les cisailles s'usent comme celles du joueur.
				shears.setDamageValue(shears.getDamageValue() + 1);
				if (shears.getDamageValue() >= shears.getMaxDamage()) {
					shears.shrink(1);
				}
			});
		} else if (task == Task.MILK) {
			citizen.inventory().stream().filter(s -> s.is(Items.BUCKET)).findFirst().ifPresent(bucket -> {
				bucket.shrink(1);
				citizen.carry(new ItemStack(Items.MILK_BUCKET));
				level().playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.COW_MILK,
						net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
			});
		}
		target = null;
		task = Task.NONE;
	}

	private Optional<Animal> sample() {
		return herd().stream().filter(a -> a.getType() == species).findFirst();
	}

	private boolean hasFoodFor(Animal animal) {
		return carriesFoodFor(animal) >= 2 || find(supplies(), animal::isFood).isPresent();
	}

	private int carriesFoodFor(Animal animal) {
		return citizen.inventory().stream().filter(animal::isFood).mapToInt(ItemStack::getCount).sum();
	}

	private void fetch() {
		Optional<Animal> sample = sample();
		if (sample.isEmpty()) {
			task = Task.NONE;
			return;
		}
		Optional<BlockPos> source = find(supplies(), sample.get()::isFood);
		if (source.isEmpty()) {
			task = Task.NONE;
			return;
		}
		if (goTo(source.get())) {
			container(source.get()).ifPresent(c -> citizen.carry(Stock.take(c, sample.get()::isFood, 4)));
			task = Task.FEED;
		}
	}

	private void feed() {
		if (target == null || !target.isAlive() || !target.canFallInLove() || target.isBaby()) {
			target = nextToFeed();
			if (target == null || carriesFoodFor(target) == 0) {
				task = Task.NONE;
				target = null;
				return;
			}
		}
		citizen.getLookControl().setLookAt(target);
		if (citizen.distanceToSqr(target) > 2.5 * 2.5) {
			follow(target);
			return;
		}
		citizen.getNavigation().stop();
		for (ItemStack stack : citizen.inventory()) {
			if (target.isFood(stack)) {
				stack.shrink(1);
				break;
			}
		}
		EntityCompat.swing(citizen, InteractionHand.MAIN_HAND);
		citizen.act(Action.FEED, 20);
		target.setInLove(null);
		level().sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight(), target.getZ(),
				3, 0.3, 0.2, 0.3, 0);
		// Deux bêtes en amour ne se cherchent qu'à huit blocs : si l'autre est plus loin dans le pré, elles
		// marchent l'une vers l'autre, sinon le blé est perdu quand l'amour passe.
		Animal fed = target;
		if (master()) {
			// Le maître éleveur (MC-63) : quand le couple est formé, un second petit naît avec le premier.
			herd().stream().filter(a -> a != fed && a.getType() == fed.getType() && a.isInLove()
					&& a.distanceToSqr(fed) <= 8 * 8).findFirst().ifPresent(partner -> {
				net.minecraft.world.entity.AgeableMob baby = fed.getBreedOffspring(level(), partner);
				if (baby != null) {
					baby.setBaby(true);
					baby.snapTo(fed.getX(), fed.getY(), fed.getZ(), 0, 0);
					level().addFreshEntity(baby);
				}
			});
		}
		herd().stream().filter(a -> a != fed && a.getType() == fed.getType() && a.isInLove())
				.min(Comparator.comparingDouble(fed::distanceToSqr))
				.filter(partner -> partner.distanceToSqr(fed) > 3 * 3)
				.ifPresent(partner -> {
					fed.getNavigation().moveTo(partner, 1.0);
					partner.getNavigation().moveTo(fed, 1.0);
				});
		target = null;
	}

	/** Suit une bête, en recalculant le chemin une fois par seconde (elle bouge). */
	private void follow(Animal animal) {
		if (--repath <= 0 || citizen.getNavigation().isDone()) {
			repath = 20;
			citizen.getNavigation().moveTo(animal, 0.7);
		}
	}

	/**
	 * La bête à nourrir : la compagne d'une bête déjà en amour s'il y en a une (qu'elles se trouvent
	 * avant que l'amour passe), sinon la bête prête la plus proche d'une autre bête prête.
	 */
	private Animal nextToFeed() {
		List<Animal> herd = herd().stream().filter(a -> a.getType() == species && !a.isBaby()).toList();
		List<Animal> ready = herd.stream().filter(a -> a.canFallInLove() && a.getAge() == 0).toList();
		Optional<Animal> inLove = herd.stream().filter(Animal::isInLove).findFirst();
		if (inLove.isPresent()) {
			return ready.stream().min(Comparator.comparingDouble(inLove.get()::distanceToSqr)).orElse(null);
		}
		Animal best = null;
		double closest = Double.MAX_VALUE;
		for (Animal a : ready) {
			for (Animal b : ready) {
				if (a != b && a.distanceToSqr(b) < closest) {
					closest = a.distanceToSqr(b);
					best = citizen.distanceToSqr(a) <= citizen.distanceToSqr(b) ? a : b;
				}
			}
		}
		return best;
	}

	private void cull() {
		if (target == null || !target.isAlive()) {
			task = Task.NONE;
			target = null;
			return;
		}
		citizen.getLookControl().setLookAt(target);
		if (citizen.distanceToSqr(target) > 2.5 * 2.5) {
			follow(target);
			return;
		}
		EntityCompat.swing(citizen, InteractionHand.MAIN_HAND);
		target.hurtServer(level(), level().damageSources().mobAttack(citizen), Float.MAX_VALUE);
		target = null;
		task = Task.NONE;
	}
}
