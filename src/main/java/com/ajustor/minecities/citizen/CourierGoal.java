package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Stock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Le livreur, à la table de cartographie. D'abord, il porte aux bâtisseurs ce qu'ils réclament : il
 * prend le matériau dans les réserves, rejoint le bâtisseur sur son chantier et le lui remet en main
 * propre. Le reste du temps, il fait la navette (brief 12) : il vide les coffres des ateliers de leur
 * production vers l'entrepôt (la nourriture au grenier), et garnit à l'avance les coffres des ateliers
 * de ce que leur métier consomme, pour que personne n'ait à courir aux réserves.
 */
class CourierGoal extends WorkGoal {
	/** Il porte au plus une pile par livraison. */
	private static final int LOAD = 64;
	/** Un atelier qui a moins que ça de ses matières, il le regarnit. */
	private static final int STOCKED = 16;

	private UUID client;
	private Item cargo;
	/** Une course de la navette : d'où, quoi, combien, et où le porter. */
	private record Haul(BlockPos from, java.util.function.Predicate<ItemStack> goods, int count, BlockPos to) {
	}

	private Haul haul;
	private boolean loaded;
	private int plan;

	CourierGoal(CitizenEntity citizen) {
		super(citizen, Job.COURIER);
	}

	@Override
	protected double speed() {
		return 0.85;
	}

	@Override
	protected void onStop() {
		client = null;
		haul = null;
	}

	@Override
	public void tick() {
		if (!stationStands()) {
			rest(IDLE_SHORT);
			return;
		}
		Optional<CitizenEntity> customer = customer();
		if (cargo != null && count(cargo) > 0) {
			if (customer.isEmpty() || customer.get().wanted().filter(cargo::equals).isEmpty()) {
				// Plus personne n'en veut : retour au grenier (ce qu'il tient en main aussi).
				stow();
				if (deliver(s -> false, 0)) {
					cargo = null;
				}
				return;
			}
			hand(customer.get());
			return;
		}
		if (customer.isEmpty()) {
			client = null;
			shuttle();
			return;
		}
		Item item = customer.get().wanted().orElseThrow();
		Optional<BlockPos> source = find(supplies(), s -> s.is(item));
		if (source.isEmpty()) {
			// Rien au grenier : le bâtisseur s'en plaindra lui-même au maire.
			citizen.say("empty:" + item, Component.translatable("speech.minecities.granary_empty",
					item.getName(new ItemStack(item))));
			customer.get().clearRequest();
			client = null;
			return;
		}
		if (goTo(source.get())) {
			container(source.get()).ifPresent(c -> {
				ItemStack taken = Stock.take(c, s -> s.is(item), Math.min(LOAD, customer.get().wantedCount()));
				if (!taken.isEmpty()) {
					cargo = item;
					stow();
					citizen.setItemSlot(EquipmentSlot.MAINHAND, taken.copyWithCount(1));
					citizen.carry(taken.copyWithCount(taken.getCount() - 1));
				}
			});
		}
	}

	/** La navette entre les ateliers et les réserves, quand aucun bâtisseur n'attend. */
	private void shuttle() {
		if (haul == null) {
			// Quelque chose dans les poches (une course interrompue) : aux réserves.
			if (citizen.inventory().stream().anyMatch(s -> !s.isEmpty())) {
				deliver(s -> false, 0);
				return;
			}
			if (--plan > 0) {
				return;
			}
			plan = 100;
			haul = nextHaul().orElse(null);
			if (haul == null) {
				rest(100);
				return;
			}
			loaded = false;
		}
		if (!loaded) {
			if (!goTo(haul.from())) {
				return;
			}
			container(haul.from()).ifPresent(c -> citizen.carry(Stock.take(c, haul.goods(), haul.count())));
			loaded = true;
			if (citizen.inventory().stream().allMatch(ItemStack::isEmpty)) {
				haul = null;
			}
			return;
		}
		if (!goTo(haul.to())) {
			return;
		}
		container(haul.to()).ifPresent(c -> {
			java.util.List<ItemStack> inventory = citizen.inventory();
			for (int i = 0; i < inventory.size(); i++) {
				inventory.set(i, Stock.insert(c, inventory.get(i)));
			}
		});
		citizen.swing(InteractionHand.MAIN_HAND);
		haul = null;
	}

	/**
	 * La prochaine course : d'abord vider un atelier de ce qu'il a produit (vers la réserve qui lui
	 * revient), sinon garnir un atelier de ce que son métier consomme (pris dans les réserves).
	 */
	private Optional<Haul> nextHaul() {
		com.ajustor.minecities.town.Town town = town();
		java.util.List<com.ajustor.minecities.town.House> workshops = town.houses().stream()
				.filter(h -> !h.warehouse() && !h.workstations().isEmpty() && !h.containers().isEmpty()).toList();
		for (com.ajustor.minecities.town.House house : workshops) {
			java.util.Set<Job> jobs = house.workstations().stream().map(com.ajustor.minecities.town.House.Workstation::job)
					.collect(java.util.stream.Collectors.toSet());
			for (BlockPos chest : house.containers()) {
				Optional<net.minecraft.world.Container> c = container(chest);
				if (c.isEmpty()) {
					continue;
				}
				for (int i = 0; i < c.get().getContainerSize(); i++) {
					ItemStack stack = c.get().getItem(i);
					// Ce que l'atelier consomme reste ; la nourriture d'un atelier où l'on dort aussi (son garde-manger).
					if (stack.isEmpty() || jobs.stream().anyMatch(j -> Needs.keeps(j, town, stack))
							|| house.isHome() && com.ajustor.minecities.town.TownService.isFood(stack)) {
						continue;
					}
					Item item = stack.getItem();
					BlockPos to = com.ajustor.minecities.town.Storage.depot(level(), town, stack);
					return Optional.of(new Haul(chest, s -> s.is(item), LOAD, to));
				}
			}
		}
		for (com.ajustor.minecities.town.House house : workshops) {
			BlockPos chest = house.containers().getFirst();
			Optional<net.minecraft.world.Container> c = container(chest);
			if (c.isEmpty()) {
				continue;
			}
			for (com.ajustor.minecities.town.House.Workstation station : house.workstations()) {
				Optional<java.util.function.Predicate<ItemStack>> inputs = Needs.inputs(station.job(), town);
				if (inputs.isEmpty() || Stock.count(c.get(), inputs.get()) >= STOCKED) {
					continue;
				}
				Optional<BlockPos> source = com.ajustor.minecities.town.Storage.find(level(), town, inputs.get());
				if (source.isPresent() && !source.get().equals(chest)) {
					return Optional.of(new Haul(source.get(), inputs.get(), LOAD / 2, chest));
				}
			}
		}
		return Optional.empty();
	}

	/** Ce qu'il tient en main passe dans ses poches : rien ne se perd quand il prend autre chose. */
	private void stow() {
		ItemStack held = citizen.getMainHandItem();
		if (!held.isEmpty()) {
			citizen.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			citizen.carry(held);
		}
	}

	/** Le bâtisseur qu'il sert : celui qu'il servait déjà, sinon le plus proche qui attend. */
	private Optional<CitizenEntity> customer() {
		if (client != null && level().getEntity(client) instanceof CitizenEntity known && known.isAlive()
				&& known.wanted().isPresent()) {
			return Optional.of(known);
		}
		Optional<CitizenEntity> next = town().residents().keySet().stream()
				.map(id -> level().getEntity(id))
				.filter(e -> e instanceof CitizenEntity c && c.isAlive() && c.wanted().isPresent())
				.map(e -> (CitizenEntity) e)
				.min(Comparator.comparingDouble(citizen::distanceToSqr));
		client = next.map(CitizenEntity::getUUID).orElse(null);
		return next;
	}

	private int count(Item item) {
		int carried = citizen.inventory().stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
		return carried + (citizen.getMainHandItem().is(item) ? citizen.getMainHandItem().getCount() : 0);
	}

	/** Le sol sous ce point : la première case libre au-dessus d'un bloc plein, en descendant. */
	private BlockPos groundBelow(BlockPos pos) {
		BlockPos at = pos;
		for (int i = 0; i < 16 && !level().getBlockState(at.below()).blocksMotion(); i++) {
			at = at.below();
		}
		return at;
	}

	/** Rejoint le bâtisseur et lui remet sa livraison. */
	private void hand(CitizenEntity customer) {
		citizen.getLookControl().setLookAt(customer);
		double dx = citizen.getX() - customer.getX();
		double dz = citizen.getZ() - customer.getZ();
		// Au pied du bâtisseur (qui peut être perché sur un échafaudage) : il lui fait passer la
		// livraison, jusqu'à 10 blocs de haut.
		// Arrivé au pied du bâtisseur selon goTo (à trois blocs), il donne : sans quoi il resterait
		// planté là, ni assez près pour donner, ni assez loin pour repartir.
		if ((dx * dx + dz * dz > 3.0 * 3.0 || Math.abs(citizen.getY() - customer.getY()) > 10)
				&& !goTo(groundBelow(customer.blockPosition()))) {
			return;
		}
		citizen.getNavigation().stop();
		int given = 0;
		ItemStack held = citizen.getMainHandItem();
		if (held.is(cargo)) {
			given += held.getCount();
			customer.carry(held.copy());
			citizen.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		for (int i = 0; i < citizen.inventory().size(); i++) {
			ItemStack stack = citizen.inventory().get(i);
			if (stack.is(cargo)) {
				given += stack.getCount();
				customer.carry(stack.copy());
				citizen.inventory().set(i, ItemStack.EMPTY);
			}
		}
		citizen.swing(InteractionHand.MAIN_HAND);
		citizen.say("delivered", Component.translatable("speech.minecities.delivered", given,
				cargo.getName(new ItemStack(cargo))));
		customer.clearRequest();
		cargo = null;
		client = null;
	}
}
