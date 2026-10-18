package com.ajustor.minecities.citizen;

import com.ajustor.minecities.compat.EntityCompat;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Stock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Le forgeron, au haut fourneau (brief 11). D'abord, il forge ce qui manque : l'outil qu'un travailleur
 * réclame (en fer s'il y a des lingots, sinon en pierre), un plastron de fer pour un garde qui n'en a
 * pas. Sinon il fond les minerais bruts que rapporte le mineur (fer, or, cuivre) en lingots. Tout
 * revient au grenier.
 */
class SmithGoal extends WorkGoal {
	/** Un lingot toutes les secondes et demie, un objet forgé en trois secondes. */
	private static final int SMELT_TIME = 30;
	private static final int FORGE_TIME = 60;
	private static final Map<Item, Item> SMELTS = Map.of(Items.RAW_IRON, Items.IRON_INGOT, Items.RAW_GOLD,
			Items.GOLD_INGOT, Items.RAW_COPPER, Items.COPPER_INGOT);

	/** Un objet à forger et ce qu'il coûte. */
	record Forging(Item result, int count, Map<Item, Integer> materials) {
		Forging(Item result, Map<Item, Integer> materials) {
			this(result, 1, materials);
		}
	}

	/** Les pièces de forge que réclament les chantiers des niveaux 3 et 4 (brief 11), et de quoi les faire. */
	static final Map<Item, Forging> MATERIALS = materials();

	/** Un objet vanilla par son nom (le cuivre ciré n'a pas de champ à lui dans {@code Items}). */
	private static Item vanilla(String name) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
				net.minecraft.resources.Identifier.withDefaultNamespace(name));
	}

	private static Map<Item, Forging> materials() {
		Map<Item, Forging> map = new LinkedHashMap<>();
		for (Forging f : new Forging[]{
				new Forging(Items.LANTERN, 1, of(Items.IRON_INGOT, 1, Items.COAL, 1, Items.STICK, 1)),
				new Forging(Items.IRON_BARS, 8, of(Items.IRON_INGOT, 3)),
				new Forging(Items.CAULDRON, 1, of(Items.IRON_INGOT, 7)),
				new Forging(Items.ANVIL, 1, of(Items.IRON_INGOT, 31)),
				new Forging(Items.RAIL, 16, of(Items.IRON_INGOT, 6, Items.STICK, 1)),
				new Forging(Items.GOLD_BLOCK, 1, of(Items.GOLD_INGOT, 9)),
				new Forging(vanilla("waxed_cut_copper"), 4, of(Items.COPPER_INGOT, 9, Items.HONEYCOMB, 1)),
				new Forging(vanilla("waxed_cut_copper_stairs"), 4, of(Items.COPPER_INGOT, 9, Items.HONEYCOMB, 1))}) {
			map.put(f.result(), f);
		}
		return map;
	}

	private enum Phase { DECIDE, WORK, DELIVER }

	private Phase phase = Phase.DECIDE;
	private Forging forging;
	private int timer;

	SmithGoal(CitizenEntity citizen) {
		super(citizen, Job.SMITH);
	}

	private static Map<Item, Integer> of(Object... pairs) {
		Map<Item, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((Item) pairs[i], (Integer) pairs[i + 1]);
		}
		return map;
	}

	/** Les façons de faire l'outil d'un besoin (« tool » d'un métier, « tool:pickaxe »…), la meilleure d'abord. */
	static List<Forging> forgings(String tool) {
		return switch (tool) {
			case "hoe" -> List.of(new Forging(Items.IRON_HOE, of(Items.IRON_INGOT, 2, Items.STICK, 2)),
					new Forging(Items.STONE_HOE, of(Items.COBBLESTONE, 2, Items.STICK, 2)));
			case "axe" -> List.of(new Forging(Items.IRON_AXE, of(Items.IRON_INGOT, 3, Items.STICK, 2)),
					new Forging(Items.STONE_AXE, of(Items.COBBLESTONE, 3, Items.STICK, 2)));
			case "pickaxe" -> List.of(new Forging(Items.IRON_PICKAXE, of(Items.IRON_INGOT, 3, Items.STICK, 2)),
					new Forging(Items.STONE_PICKAXE, of(Items.COBBLESTONE, 3, Items.STICK, 2)));
			case "shovel" -> List.of(new Forging(Items.IRON_SHOVEL, of(Items.IRON_INGOT, 1, Items.STICK, 2)),
					new Forging(Items.STONE_SHOVEL, of(Items.COBBLESTONE, 1, Items.STICK, 2)));
			case "sword" -> List.of(new Forging(Items.IRON_SWORD, of(Items.IRON_INGOT, 2, Items.STICK, 1)),
					new Forging(Items.STONE_SWORD, of(Items.COBBLESTONE, 2, Items.STICK, 1)));
			case "rod" -> List.of(new Forging(Items.FISHING_ROD, of(Items.STICK, 3, Items.STRING, 2)));
			case "bow" -> List.of(new Forging(Items.BOW, of(Items.STICK, 3, Items.STRING, 3)));
			case "chestplate" -> List.of(new Forging(Items.IRON_CHESTPLATE, of(Items.IRON_INGOT, 8)));
			case "helmet" -> List.of(new Forging(Items.IRON_HELMET, of(Items.IRON_INGOT, 5)));
			case "shears" -> List.of(new Forging(Items.SHEARS, of(Items.IRON_INGOT, 2)));
			case "iron_block" -> List.of(new Forging(Items.IRON_BLOCK, of(Items.IRON_INGOT, 9)));
			case "leggings" -> List.of(new Forging(Items.IRON_LEGGINGS, of(Items.IRON_INGOT, 7)));
			case "boots" -> List.of(new Forging(Items.IRON_BOOTS, of(Items.IRON_INGOT, 4)));
			default -> tool.startsWith("material:") ? MATERIALS.values().stream()
					.filter(f -> tool.equals("material:" + f.result())).toList() : List.of();
		};
	}

	/** L'outil que réclame un métier. */
	static Optional<String> toolOf(Job job) {
		return Optional.ofNullable(switch (job) {
			case FARMER -> "hoe";
			case LUMBERJACK -> "axe";
			case FISHERMAN -> "rod";
			case GUARD -> "sword";
			case ARCHER -> "bow";
			case MINER -> "pickaxe";
			case BEEKEEPER -> "shears";
			default -> null;
		});
	}

	@Override
	public void start() {
		super.start();
		// Interrompu avec de quoi finir sa pièce (ou des minerais à fondre) : il reprend à la forge.
		if (forging != null ? forging.materials().entrySet().stream().allMatch(e -> citizen.inventory().stream()
				.filter(s -> s.is(e.getKey())).mapToInt(ItemStack::getCount).sum() >= e.getValue())
				: citizen.inventory().stream().anyMatch(s -> SMELTS.containsKey(s.getItem()))) {
			phase = Phase.WORK;
			return;
		}
		phase = citizen.inventory().stream().allMatch(ItemStack::isEmpty) ? Phase.DECIDE : Phase.DELIVER;
	}

	@Override
	protected void onStop() {
		light(false);
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
				// Une pièce d'armure qu'un garde attend : il la lui porte et l'en habille, sans passer par le
				// grenier (plein, il ne pourrait pas l'y déposer, et le garde ne l'aurait jamais).
				if (dress()) {
					return;
				}
				if (deliver(s -> false, 0)) {
					phase = Phase.DECIDE;
					rest(100);
				}
			}
		}
	}

	private void decide() {
		// Il va là où sont ses matières (son atelier, l'entrepôt, le grenier), et y rassemble le reste.
		Optional<BlockPos> source = find(supplies(), s -> SMELTS.containsKey(s.getItem()) || s.is(Items.IRON_INGOT)
				|| s.is(Items.COBBLESTONE) || s.is(Items.STICK) || s.is(Items.STRING) || s.is(Items.GOLD_INGOT)
				|| s.is(Items.COPPER_INGOT));
		if (source.isEmpty()) {
			rest(IDLE_LONG / 2);
			return;
		}
		if (!goTo(source.get())) {
			return;
		}
		List<Container> c = pool(supplies());
		// Un travailleur attend un outil, un garde un plastron : on le forge, si la ville en a de quoi.
		for (String wanted : wanted(c)) {
			for (Forging f : forgings(wanted)) {
				if (f.materials().entrySet().stream().allMatch(e -> count(c, s -> s.is(e.getKey())) >= e.getValue())) {
					f.materials().forEach((item, n) -> citizen.carry(take(c, s -> s.is(item), n)));
					forging = f;
					phase = Phase.WORK;
					timer = 0;
					return;
				}
			}
		}
		// Sinon, il fond les minerais bruts.
		forging = null;
		ItemStack raw = take(c, s -> SMELTS.containsKey(s.getItem()), 16);
		if (!raw.isEmpty()) {
			citizen.carry(raw);
			phase = Phase.WORK;
			timer = 0;
			return;
		}
		rest(IDLE_LONG / 2);
	}

	/** Ce qu'attendent les travailleurs, et qui n'est pas déjà dans les réserves. */
	private List<String> wanted(List<Container> granary) {
		List<String> wanted = new java.util.ArrayList<>();
		BlockPos hall = town().hall();
		int r = town().radius();
		for (CitizenEntity worker : level().getEntitiesOfClass(CitizenEntity.class, new AABB(hall).inflate(r, 48, r),
				w -> w.townId().filter(town().id()::equals).isPresent() && w.job().isPresent())) {
			Job job = worker.job().get().job();
			Optional<String> tool = Optional.empty();
			if (worker.hasNeed("tool:")) {
				for (String name : new String[]{"pickaxe", "shovel", "axe"}) {
					if (worker.hasNeed("tool:" + name)) {
						tool = Optional.of(name);
						break;
					}
				}
			} else if (worker.hasNeed("tool")) {
				tool = toolOf(job);
			}
			if (job == Job.GUARD && worker.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
					&& count(granary, s -> s.is(Items.IRON_CHESTPLATE)) == 0) {
				wanted.add("chestplate");
			}
			// Le forgeron d'une demeure habille les gardes de pied en cap.
			if (job == Job.GUARD && master()) {
				for (Object[] piece : new Object[][]{{EquipmentSlot.HEAD, Items.IRON_HELMET, "helmet"},
						{EquipmentSlot.LEGS, Items.IRON_LEGGINGS, "leggings"}, {EquipmentSlot.FEET, Items.IRON_BOOTS, "boots"}}) {
					if (worker.getItemBySlot((EquipmentSlot) piece[0]).isEmpty()
							&& count(granary, s -> s.is((Item) piece[1])) == 0) {
						wanted.add((String) piece[2]);
					}
				}
			}
			tool.filter(t -> forgings(t).stream().noneMatch(f -> count(granary, s -> s.is(f.result())) > 0))
					.ifPresent(wanted::add);
		}
		// Ce que réclament les chantiers, s'il sait le forger.
		for (Item material : CitizenEntity.missingMaterials(level(), town())) {
			if (MATERIALS.containsKey(material) && count(granary, s -> s.is(material)) == 0) {
				wanted.add("material:" + material);
			}
		}
		// Les golems de l'inventeur : quatre blocs de fer d'avance.
		if (town().workers(Job.INVENTOR) > 0 && count(granary, s -> s.is(Items.IRON_BLOCK)) < 4) {
			wanted.add("iron_block");
		}
		return wanted;
	}

	private void work() {
		BlockPos station = employment().station();
		if (!goTo(station)) {
			return;
		}
		light(true);
		citizen.act(forging != null ? Action.BUILD : Action.COOK, 10);
		int time = forging != null ? FORGE_TIME : SMELT_TIME;
		if (++timer < time) {
			if (timer % 10 == 0) {
				level().sendParticles(forging != null ? ParticleTypes.CRIT : ParticleTypes.SMOKE, station.getX() + 0.5,
						station.getY() + 1.1, station.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.02);
			}
			return;
		}
		timer = 0;
		EntityCompat.swing(citizen, InteractionHand.MAIN_HAND);
		if (forging != null) {
			// Il use ses matériaux, et seulement eux : le reste de ses poches (une pièce pas encore livrée) reste.
			forging.materials().forEach((item, n) -> {
				int left = n;
				for (ItemStack stack : citizen.inventory()) {
					if (left > 0 && stack.is(item)) {
						int used = Math.min(left, stack.getCount());
						stack.shrink(used);
						left -= used;
					}
				}
			});
			citizen.carry(new ItemStack(forging.result(), forging.count()));
			level().playSound(null, station, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.0f);
			citizen.say("forged", Component.translatable("speech.minecities.forged",
					Component.translatable(forging.result().getDescriptionId())));
			forging = null;
			light(false);
			phase = Phase.DELIVER;
			return;
		}
		for (ItemStack stack : citizen.inventory()) {
			Item ingot = SMELTS.get(stack.getItem());
			if (ingot != null && !stack.isEmpty()) {
				stack.shrink(1);
				citizen.carry(new ItemStack(ingot));
				level().playSound(null, station, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0f, 1.0f);
				return;
			}
		}
		light(false);
		phase = Phase.DELIVER;
	}

	/**
	 * Habille un garde qui attend la pièce d'armure qu'il porte. Vrai tant qu'il s'en occupe (en route,
	 * ou s'il vient d'en habiller un) ; faux s'il n'a rien à porter à un garde.
	 */
	private boolean dress() {
		for (ItemStack stack : citizen.inventory()) {
			net.minecraft.world.item.equipment.Equippable equippable = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
			if (stack.isEmpty() || equippable == null || equippable.slot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
				continue;
			}
			EquipmentSlot slot = equippable.slot();
			BlockPos hall = town().hall();
			int r = town().radius();
			Optional<CitizenEntity> guard = level().getEntitiesOfClass(CitizenEntity.class, new AABB(hall).inflate(r, 48, r),
					g -> g.isAlive() && g.townId().filter(town().id()::equals).isPresent()
							&& g.job().filter(j -> j.job() == Job.GUARD).isPresent() && g.getItemBySlot(slot).isEmpty())
					.stream().min(java.util.Comparator.comparingDouble(citizen::distanceToSqr));
			if (guard.isEmpty()) {
				continue;
			}
			if (citizen.distanceToSqr(guard.get()) > 2.5 * 2.5) {
				citizen.getNavigation().moveTo(guard.get(), speed());
				return true;
			}
			citizen.getNavigation().stop();
			guard.get().setItemSlot(slot, stack.split(1));
			EntityCompat.swing(citizen, InteractionHand.MAIN_HAND);
			level().playSound(null, guard.get().blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL,
					0.8f, 1.0f);
			return true;
		}
		return false;
	}

	/** Allume ou éteint le haut fourneau pendant qu'il travaille. */
	private void light(boolean lit) {
		if (citizen.job().isEmpty()) {
			return;
		}
		BlockPos station = employment().station();
		BlockState state = level().getBlockState(station);
		if (state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT) != lit) {
			level().setBlock(station, state.setValue(AbstractFurnaceBlock.LIT, lit), Block.UPDATE_ALL);
		}
	}
}
