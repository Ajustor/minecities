package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.House;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Stock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;
import java.util.Optional;

/**
 * Le garde, à la meule. Il prend une épée (et l'armure qu'il trouve) au grenier ou à la caserne, fait
 * ses rondes de maison en maison, jour et nuit, et se bat contre tout ce qui est hostile sur le
 * territoire (le combat lui-même est le but d'attaque vanilla, qui passe avant). Pendant un raid, il
 * monte la garde devant l'hôtel de ville.
 */
class GuardGoal extends WorkGoal {
	/** Il change de point de ronde toutes les 20 secondes. */
	private static final int PATROL = 400;
	private BlockPos patrol;
	private int patrolTicks;

	GuardGoal(CitizenEntity citizen) {
		super(citizen, Job.GUARD);
	}

	@Override
	protected double speed() {
		return 0.75;
	}

	@Override
	public void tick() {
		if (!stationStands()) {
			rest(IDLE_SHORT);
			return;
		}
		if (!ensureTool() || !ensureArmor()) {
			return;
		}
		if (citizen.alarmed()) {
			goTo(town().hall());
			return;
		}
		// Le butin des monstres tués autour de lui (MC-61) : il le ramasse, puis le porte aux réserves.
		if (collectLitter(citizen.blockPosition(), LOOT_RADIUS, MOB_LOOT)) {
			return;
		}
		if (delivering || produce(s -> false, 0) > 0) {
			delivering = !deliver(s -> false, 0);
			return;
		}
		if (patrol == null || --patrolTicks <= 0) {
			patrolTicks = PATROL;
			List<House> houses = town().houses();
			patrol = houses.isEmpty() || citizen.getRandom().nextInt(4) == 0 ? town().hall()
					: houses.get(citizen.getRandom().nextInt(houses.size())).plaque();
		}
		goTo(patrol);
	}

	/** Ce que lâchent les monstres et qui sert en ville : la ficelle du tisserand, les flèches de l'archer… */
	private static final java.util.function.Predicate<ItemStack> MOB_LOOT = s -> s.is(Items.STRING) || s.is(Items.BONE)
			|| s.is(Items.GUNPOWDER) || s.is(Items.ARROW) || s.is(Items.SPIDER_EYE) || s.is(Items.SLIME_BALL)
			|| s.is(Items.ENDER_PEARL);
	/** Jusqu'où, autour de lui, il va ramasser ce butin. */
	private static final int LOOT_RADIUS = 8;
	/** Il porte son butin aux réserves. */
	private boolean delivering;

	/** Tous les combien de ticks il regarde s'il y a de l'armure pour lui. */
	private static final int ARMOR_CHECK = 100;
	/** Le coffre où il va chercher une pièce d'armure, et le temps avant de regarder à nouveau. */
	private Optional<BlockPos> armor = Optional.empty();
	private int armorWait;

	/** La pièce d'armure de cet objet, s'il en est une. */
	private static Optional<EquipmentSlot> armorSlot(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable == null || equippable.slot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR
				? Optional.empty() : Optional.of(equippable.slot());
	}

	/**
	 * S'équipe de l'armure qu'il trouve pour les pièces qui lui manquent. Renvoie vrai quand il n'a
	 * plus rien à aller chercher.
	 */
	private boolean ensureArmor() {
		// Fouiller tous les coffres de la ville à chaque tick coûte cher : il regarde de temps en temps,
		// et garde le coffre trouvé tant qu'il peut y aller (MC-49).
		if (armor.isEmpty() || !reachable(armor.get())) {
			armor = Optional.empty();
			if (--armorWait > 0) {
				return true;
			}
			armorWait = ARMOR_CHECK;
			armor = find(supplies(), s -> armorSlot(s).filter(slot -> citizen.getItemBySlot(slot).isEmpty()).isPresent());
			if (armor.isEmpty()) {
				return true;
			}
		}
		Optional<BlockPos> source = armor;
		if (goTo(source.get())) {
			armor = Optional.empty();
			armorWait = 0;
			container(source.get()).ifPresent(c -> {
				ItemStack piece = Stock.take(c, s -> armorSlot(s).filter(slot -> citizen.getItemBySlot(slot)
						.isEmpty()).isPresent(), 1);
				armorSlot(piece).ifPresent(slot -> {
					citizen.setItemSlot(slot, piece);
					citizen.setDropChance(slot, 2.0f);
				});
			});
		}
		return false;
	}
}
