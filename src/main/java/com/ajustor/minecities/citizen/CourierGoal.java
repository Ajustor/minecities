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
 * Le livreur, à la table de cartographie. Il porte aux bâtisseurs ce qu'ils réclament : il prend le
 * matériau au grenier (ou dans les coffres de son atelier), rejoint le bâtisseur sur son chantier
 * et le lui remet en main propre. Le bâtisseur, lui, ne quitte plus son chantier.
 */
class CourierGoal extends WorkGoal {
	/** Il porte au plus une pile par livraison. */
	private static final int LOAD = 64;

	private UUID client;
	private Item cargo;

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
				// Plus personne n'en veut : retour au grenier.
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
			rest(40);
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
					citizen.setItemSlot(EquipmentSlot.MAINHAND, taken.copyWithCount(1));
					citizen.carry(taken.copyWithCount(taken.getCount() - 1));
				}
			});
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
		if (dx * dx + dz * dz > 3.0 * 3.0 || Math.abs(citizen.getY() - customer.getY()) > 10) {
			goTo(groundBelow(customer.blockPosition()));
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
