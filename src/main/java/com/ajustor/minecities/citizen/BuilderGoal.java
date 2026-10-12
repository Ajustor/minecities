package com.ajustor.minecities.citizen;

import com.ajustor.minecities.build.Blueprint;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildPlan;
import com.ajustor.minecities.build.Construction;
import com.ajustor.minecities.registry.McBlocks;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le bâtisseur, à la table d'artisan. Il prend le premier chantier commandé par le maire et le bâtit
 * d'après son schéma, un bloc toutes les demi-secondes, depuis le pied du chantier (dehors) ou du
 * haut d'un échafaudage qu'il dresse et démonte à la fin : il dégage avec le bon outil (pioche, pelle,
 * hache, qui s'usent), puis pose, en allant chercher outils et matériaux au grenier (ou dans les
 * coffres de son atelier) ; s'il en manque, le maire est prévenu. Plusieurs bâtisseurs travaillent
 * ensemble au même chantier, chacun au bloc suivant. Ce qu'il retire du terrain va au grenier. Le bâtiment fini se déclare tout seul.
 */
class BuilderGoal extends WorkGoal {
	/** Il pose à cette distance (à l'horizontale), depuis le pied du chantier. */
	private static final double REACH = 8.0;
	/** Il pose jusqu'à 4 blocs au-dessus de ses pieds ; plus haut, il monte sur un échafaudage. */
	private static final int CLIMB = 4;
	/** Un bloc toutes les 4 ticks : cinq par seconde. */
	private static final int PACE = 4;
	/** Il emporte au plus tant de chaque matériau par voyage. */
	private static final int HAUL = 64;
	/** Au grenier, il emporte les matériaux des 128 prochains blocs, tant qu'il a de la place. */
	private static final int LOOKAHEAD = 128;

	private UUID planFor;
	private List<BuildPlan.Placement> plan;
	private int cooldown;
	private boolean delivering;
	/** Là où il se tient, au sommet d'un échafaudage ; vide quand il est au sol. */
	private BlockPos perch;
	private int repathOut;
	private int stuckInside;
	private int lastCall = -100;

	BuilderGoal(CitizenEntity citizen) {
		super(citizen, Job.BUILDER);
	}

	@Override
	public void tick() {
		if (!stationStands()) {
			rest(IDLE_SHORT);
			return;
		}
		Optional<Construction> site = town().activeConstruction();
		if (site.isEmpty()) {
			if (citizen.inventory().stream().anyMatch(s -> !s.isEmpty())) {
				deliver(BuilderGoal::isTool, 1);
			} else {
				rest(IDLE_SHORT);
			}
			return;
		}
		Construction construction = site.get();
		if (citizen.isInWall()) {
			// Pris dans un mur malgré tout : il est remis dehors, devant le chantier.
			BlockPos out = outside(construction, construction.origin());
			citizen.snapTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5);
			citizen.getNavigation().stop();
		}
		if (!construction.id().equals(planFor)) {
			Optional<Blueprint> blueprint = Blueprints.get(level().getServer(), construction.blueprint());
			if (blueprint.isEmpty()) {
				// Un schéma disparu (datapack retiré) : le chantier est abandonné.
				town().cancel(construction);
				Towns.get(level().getServer()).changed();
				return;
			}
			plan = BuildPlan.of(blueprint.get(), construction.style(), construction.origin(), construction.rotation());
			planFor = construction.id();
		}
		// Ses poches se remplissent de ce qu'il dégage : il le porte au grenier quand il ne lui reste
		// qu'une case, en gardant ses outils et ce qui servira encore au chantier.
		if (delivering || freeSlots() <= 1) {
			java.util.Set<Item> needed = new java.util.HashSet<>();
			for (BuildPlan.Placement p : plan.subList(construction.step(), plan.size())) {
				BuildPlan.material(p.state()).ifPresent(needed::add);
			}
			java.util.function.Predicate<ItemStack> kept = s -> isTool(s) || s.is(Items.SCAFFOLDING)
					|| needed.contains(s.getItem());
			// Rien que des outils et des matériaux utiles : il n'y a rien à rapporter.
			if (delivering || citizen.inventory().stream().anyMatch(s -> !s.isEmpty() && !kept.test(s))) {
				delivering = !deliver(kept, HAUL);
				return;
			}
		}
		if (--cooldown > 0) {
			return;
		}
		// Les blocs déjà en place ne coûtent rien : on les passe, quelques-uns par tick.
		for (int skipped = 0; construction.step() < plan.size() && skipped < 32; skipped++) {
			BuildPlan.Placement p = plan.get(construction.step());
			BlockState current = level().getBlockState(p.pos());
			if (!satisfied(current, p.state()) && !unbreakable(current, p)) {
				break;
			}
			construction.advance();
		}
		if (construction.step() >= plan.size()) {
			if (!construction.scaffolding().isEmpty()) {
				dismantle(construction);
				return;
			}
			finish(construction);
			return;
		}
		int index = choose(construction);
		if (index < 0) {
			// Tout ce qui est à faire est réservé par les autres bâtisseurs : il attend son tour.
			cooldown = 20;
			return;
		}
		construction.claim(index, citizen.getUUID());
		work(construction, index, plan.get(index));
	}

	/** Combien de blocs à venir il regarde pour choisir le sien. */
	private static final int WINDOW = 64;

	/**
	 * Choisit le bloc à poser : celui qu'il a déjà réservé, sinon, parmi les prochains du plan que
	 * personne d'autre n'a réservés, le premier qu'il peut poser tout de suite (il a le matériau et
	 * l'outil), à défaut le premier libre. Ainsi plusieurs bâtisseurs se partagent le chantier sans
	 * se gêner. Renvoie -1 si tout est réservé.
	 */
	private int choose(Construction construction) {
		int fallback = -1;
		int end = Math.min(plan.size(), construction.step() + WINDOW);
		for (int i = construction.step(); i < end; i++) {
			if (construction.isPlaced(i)) {
				continue;
			}
			BuildPlan.Placement p = plan.get(i);
			BlockState current = level().getBlockState(p.pos());
			if (satisfied(current, p.state()) || unbreakable(current, p)) {
				construction.markPlaced(i);
				continue;
			}
			Optional<UUID> owner = construction.claimant(i);
			if (owner.filter(citizen.getUUID()::equals).isPresent()) {
				return i;
			}
			if (owner.isPresent() && level().getEntity(owner.get()) instanceof CitizenEntity other && other.isAlive()) {
				continue;
			}
			if (fallback < 0) {
				fallback = i;
			}
			if (ready(current, p)) {
				return i;
			}
		}
		return fallback;
	}

	/** A-t-il de quoi poser ce bloc tout de suite : le matériau, et l'outil pour dégager ? */
	private boolean ready(BlockState current, BuildPlan.Placement p) {
		if (BuildPlan.material(p.state()).filter(m -> count(m) == 0).isPresent()) {
			return false;
		}
		boolean clearing = !current.isAir() && !current.canBeReplaced();
		return !clearing || toolFor(current).map(this::has).orElse(true);
	}

	private boolean has(TagKey<Item> tool) {
		return citizen.getMainHandItem().is(tool) || citizen.inventory().stream().anyMatch(s -> s.is(tool));
	}

	/**
	 * Le bloc voulu est-il en place ? On compare le bloc et ce qui se choisit à la pose (orientation,
	 * moitié, type de dalle…), pas ce qui change tout seul : les raccords des vitres et des barrières,
	 * la forme des escaliers, une porte ouverte, un lit occupé. Sinon le bâtisseur reposerait sans
	 * fin une vitre qui s'est raccordée à ses voisins.
	 */
	static boolean satisfied(BlockState current, BlockState target) {
		if (target.isAir()) {
			return current.isAir() || current.canBeReplaced() && current.getFluidState().isEmpty();
		}
		if (current.getBlock() != target.getBlock()) {
			return false;
		}
		for (Property<?> property : PLACEMENT) {
			if (target.hasProperty(property) && !current.getValue(property).equals(target.getValue(property))) {
				return false;
			}
		}
		return true;
	}

	private static final List<Property<?>> PLACEMENT = List.of(BlockStateProperties.FACING,
			BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.AXIS, BlockStateProperties.HORIZONTAL_AXIS,
			BlockStateProperties.HALF, BlockStateProperties.DOUBLE_BLOCK_HALF, BlockStateProperties.BED_PART,
			BlockStateProperties.SLAB_TYPE, BlockStateProperties.ATTACH_FACE, BlockStateProperties.DOOR_HINGE);

	/** Ce qu'on ne casse pas : la roche-mère, l'hôtel de ville, les plaques des autres maisons. */
	private boolean unbreakable(BlockState current, BuildPlan.Placement p) {
		return current.getDestroySpeed(level(), p.pos()) < 0 || current.is(McBlocks.TOWN_HALL)
				|| current.is(McBlocks.HOUSE_PLAQUE) && !p.state().is(McBlocks.HOUSE_PLAQUE);
	}

	private void work(Construction construction, int index, BuildPlan.Placement p) {
		ServerLevel level = level();
		BlockState current = level.getBlockState(p.pos());
		Optional<Item> material = BuildPlan.material(p.state());
		// Les matériaux d'abord : on ne dégage pas pour rien.
		// Ce qu'il a réclamé est arrivé, ou ne sert plus à ce bloc : la demande tombe.
		if (citizen.wanted().filter(w -> material.isEmpty() || !w.equals(material.get())
				|| count(material.get()) > 0).isPresent()) {
			citizen.wanted().ifPresent(w -> citizen.satisfied("wait:" + w));
			citizen.clearRequest();
		}
		material.filter(m -> count(m) > 0).ifPresent(m -> citizen.satisfied("material:" + m));
		if (material.isPresent() && count(material.get()) == 0) {
			fetch(construction, material.get());
			return;
		}
		boolean clearing = !current.isAir() && !current.canBeReplaced()
				|| !current.getFluidState().isEmpty() && !p.state().is(Blocks.WATER);
		// Pour dégager, le bon outil : pioche pour la pierre, pelle pour la terre, hache pour le bois.
		Optional<TagKey<Item>> tool = clearing ? toolFor(current) : Optional.empty();
		if (tool.isPresent() && !equip(tool.get())) {
			fetchTool(tool.get());
			return;
		}
		// Il travaille du dehors : il ne s'emmure pas dans le bâtiment qu'il élève. Trop haut pour le
		// sol, il monte sur un échafaudage dressé contre le chantier.
		if (!position(construction, p.pos())) {
			return;
		}
		citizen.getNavigation().stop();
		citizen.getLookControl().setLookAt(p.pos().getX() + 0.5, p.pos().getY() + 0.5, p.pos().getZ() + 0.5);
		cooldown = PACE;
		citizen.swing(InteractionHand.MAIN_HAND);
		citizen.act(Action.BUILD, 10);
		if (clearing) {
			// Dégager : ce qui était là va au grenier (par ses poches).
			ItemStack held = tool.isPresent() ? citizen.getMainHandItem() : ItemStack.EMPTY;
			drops(level, p.pos(), current, held).forEach(citizen::carry);
			if (!held.isEmpty()) {
				held.hurtAndBreak(1, citizen, EquipmentSlot.MAINHAND);
			}
			level.levelEvent(2001, p.pos(), Block.getId(current));
			level.setBlock(p.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			if (p.state().isAir()) {
				construction.markPlaced(index);
			}
			Towns.get(level.getServer()).changed();
			return;
		}
		if (p.state().isAir()) {
			level.setBlock(p.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			construction.markPlaced(index);
			return;
		}
		if (!p.state().getCollisionShape(level, p.pos()).isEmpty() && clearTheWay(construction, p.pos())) {
			// Quelqu'un se tient là : on ne pose pas un bloc sur lui ; on lui demande de bouger.
			cooldown = 20;
			return;
		}
		material.ifPresent(this::consume);
		level.setBlock(p.pos(), p.state(), Block.UPDATE_ALL);
		// Les portes et les lits posent leur autre moitié d'eux-mêmes.
		p.state().getBlock().setPlacedBy(level, p.pos(), p.state(), citizen, material.map(ItemStack::new).orElse(ItemStack.EMPTY));
		level.playSound(null, p.pos(), p.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.9f);
		construction.markPlaced(index);
		Towns.get(level.getServer()).changed();
	}

	/**
	 * Ce que laisse ce bloc. Un lit ne laisse son objet que par sa tête, une porte que par sa moitié
	 * basse : démonter l'autre moitié emporte la première sans rien laisser, alors on prend ce que
	 * laisse celle-là. Sans quoi le lit ou la porte se perdrait.
	 */
	private List<ItemStack> drops(ServerLevel level, BlockPos pos, BlockState state, ItemStack held) {
		List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), citizen, held);
		if (!drops.isEmpty()) {
			return drops;
		}
		BlockPos other = null;
		if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
			other = pos.relative(net.minecraft.world.level.block.BedBlock.getConnectedDirection(state));
		} else if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			other = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
					== net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER ? pos.below() : pos.above();
		}
		if (other == null || !level.getBlockState(other).is(state.getBlock())) {
			return drops;
		}
		return Block.getDrops(level.getBlockState(other), level, other, level.getBlockEntity(other), citizen, held);
	}

	/** L'outil qu'il faut pour retirer ce bloc, s'il en faut un. */
	static Optional<TagKey<Item>> toolFor(BlockState state) {
		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return Optional.of(ItemTags.PICKAXES);
		}
		if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			return Optional.of(ItemTags.SHOVELS);
		}
		if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
			return Optional.of(ItemTags.AXES);
		}
		return Optional.empty();
	}

	private static String toolName(TagKey<Item> tool) {
		return tool == ItemTags.PICKAXES ? "pickaxe" : tool == ItemTags.SHOVELS ? "shovel" : "axe";
	}

	private static boolean isTool(ItemStack stack) {
		return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.AXES);
	}

	/** Prend en main un outil de cette sorte, s'il en a un dans ses poches. */
	private boolean equip(TagKey<Item> tool) {
		if (citizen.getMainHandItem().is(tool)) {
			citizen.satisfied("tool:" + toolName(tool));
			return true;
		}
		List<ItemStack> inventory = citizen.inventory();
		for (int i = 0; i < inventory.size(); i++) {
			if (inventory.get(i).is(tool)) {
				ItemStack previous = citizen.getMainHandItem();
				citizen.setItemSlot(EquipmentSlot.MAINHAND, inventory.get(i));
				inventory.set(i, previous);
				citizen.satisfied("tool:" + toolName(tool));
				return true;
			}
		}
		return false;
	}

	/** Va chercher l'outil au grenier ou à l'atelier ; s'il n'y en a pas, prévient le maire. */
	private void fetchTool(TagKey<Item> tool) {
		if (freeSlots() == 0) {
			makeRoom();
			return;
		}
		Optional<BlockPos> source = find(supplies(), s -> s.is(tool));
		if (source.isEmpty()) {
			String name = toolName(tool);
			citizen.complain("tool:" + name, Component.translatable("message.minecities.builder.needs_tool",
					citizen.getName(), Component.translatable("tool.minecities." + name)),
					Component.translatable("speech.minecities.needs_tool", Component.translatable("tool.minecities." + name)));
			rest(400);
			return;
		}
		if (goTo(source.get())) {
			container(source.get()).ifPresent(c -> citizen.carry(Stock.take(c, s -> s.is(tool), 1)));
		}
	}

	/**
	 * Les poches pleines (de matériaux qui serviront plus tard) : il en rapporte aux réserves, sans quoi
	 * ce qu'il va chercher tomberait à ses pieds. Il garde un outil de chaque sorte.
	 */
	private void makeRoom() {
		deliver(BuilderGoal::isTool, 1);
	}

	private int freeSlots() {
		return (int) citizen.inventory().stream().filter(ItemStack::isEmpty).count();
	}

	/** À portée : 8 blocs à l'horizontale, 4 en hauteur (au-delà, il lui faut un échafaudage). */
	@Override
	protected double speed() {
		return 0.85;
	}

	private boolean near(BlockPos pos) {
		double dx = citizen.getX() - (pos.getX() + 0.5);
		double dz = citizen.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dz * dz <= REACH * REACH && Math.abs(citizen.getY() - pos.getY()) <= CLIMB;
	}

	/**
	 * Se met en place pour poser ce bloc : au pied du chantier, dehors, s'il est assez bas ; sinon au
	 * sommet d'une colonne d'échafaudage dressée au pied du chantier, qu'il monte au besoin. Renvoie
	 * vrai quand il est en place.
	 */
	private boolean position(Construction construction, BlockPos pos) {
		int ground = construction.origin().getY();
		int standY = pos.getY() - ground <= CLIMB ? ground : pos.getY() - 2;
		BlockPos foot = outside(construction, pos);
		if (standY > ground) {
			// Une colonne déjà dressée à portée fait l'affaire.
			for (BlockPos column : construction.scaffolding()) {
				double dx = column.getX() - pos.getX();
				double dz = column.getZ() - pos.getZ();
				if (dx * dx + dz * dz <= (REACH - 1) * (REACH - 1)) {
					foot = new BlockPos(column.getX(), ground, column.getZ());
					break;
				}
			}
		}
		BlockPos stand = new BlockPos(foot.getX(), standY, foot.getZ());
		if (perch != null && !perch.equals(stand)) {
			// Ailleurs : il redescend et remporte sa colonne, pour la dresser là où il va.
			BlockPos column = perch;
			descend(ground);
			takeDown(construction, column);
			return false;
		}
		if (perch == null && citizen.getY() > ground + 1.5) {
			// Perché sans le savoir (après un rechargement) : il redescend.
			descend(ground);
			return false;
		}
		if (inside(construction)) {
			leave(foot);
			return false;
		}
		stuckInside = 0;
		if (standY == ground) {
			if (!near(pos)) {
				goTo(foot);
				return false;
			}
			return true;
		}
		if (perch != null) {
			if (near(pos)) {
				return true;
			}
			// Hors de portée depuis cette colonne : il redescend, pour aller s'installer ailleurs.
			BlockPos column = perch;
			descend(ground);
			takeDown(construction, column);
			return false;
		}
		// Au pied de la colonne...
		if (citizen.distanceToSqr(foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5) > 2.0 * 2.0) {
			// Jusqu'au pied même de la colonne : « à portée » ne suffit pas.
			walkExactly(foot);
			return false;
		}
		// ... il la dresse, un échafaudage à la fois...
		for (int y = ground; y < standY; y++) {
			BlockPos cell = new BlockPos(foot.getX(), y, foot.getZ());
			BlockState there = level().getBlockState(cell);
			if (there.is(Blocks.SCAFFOLDING)) {
				continue;
			}
			if (!there.canBeReplaced()) {
				// La place est prise (un arbre, un mur voisin) : il pose de là où il est, en tendant le bras.
				return true;
			}
			if (count(Items.SCAFFOLDING) == 0) {
				fetch(construction, Items.SCAFFOLDING);
				return false;
			}
			consume(Items.SCAFFOLDING);
			level().setBlock(cell, Blocks.SCAFFOLDING.defaultBlockState()
					.setValue(ScaffoldingBlock.DISTANCE, ScaffoldingBlock.getDistance(level(), cell)), Block.UPDATE_ALL);
			level().playSound(null, cell, SoundEvents.SCAFFOLDING_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
			construction.scaffolding().add(cell);
			citizen.swing(InteractionHand.MAIN_HAND);
			cooldown = PACE;
			Towns.get(level().getServer()).changed();
			return false;
		}
		// ... puis il y grimpe.
		citizen.getNavigation().stop();
		citizen.snapTo(foot.getX() + 0.5, standY, foot.getZ() + 0.5, citizen.getYRot(), citizen.getXRot());
		citizen.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		level().playSound(null, stand, SoundEvents.SCAFFOLDING_STEP, SoundSource.BLOCKS, 1.0f, 1.0f);
		perch = stand;
		return near(pos);
	}

	/**
	 * Sort de l'emprise du chantier jusqu'au point donné, sans s'arrêter en chemin : « à portée » ne
	 * suffit pas, il doit être dehors. S'il n'y arrive pas, il est remis dehors.
	 */
	private void leave(BlockPos foot) {
		// Coincé (sur un mur, dans un coin) : au bout de 3 secondes, il est remis dehors.
		if (++stuckInside > 60) {
			stuckInside = 0;
			citizen.getNavigation().stop();
			citizen.snapTo(foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5, citizen.getYRot(), citizen.getXRot());
			citizen.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
			citizen.fallDistance = 0;
			return;
		}
		if (--repathOut <= 0 || citizen.getNavigation().isDone()) {
			repathOut = 20;
			citizen.getNavigation().moveTo(foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5, 0, 0.7);
		}
	}

	/**
	 * Demande à ceux qui se tiennent sur la case de ce bloc de laisser la place : un citoyen s'écarte
	 * hors du chantier, un joueur est prévenu, une bête est poussée dehors. Renvoie vrai si la case
	 * est encore occupée.
	 */
	private boolean clearTheWay(Construction construction, BlockPos pos) {
		List<net.minecraft.world.entity.LivingEntity> there = level().getEntitiesOfClass(
				net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(pos));
		if (there.isEmpty()) {
			return false;
		}
		boolean speak = citizen.tickCount - lastCall >= 20;
		for (net.minecraft.world.entity.LivingEntity someone : there) {
			BlockPos away = outside(construction, someone.blockPosition());
			if (someone == citizen) {
				leave(away);
			} else if (someone instanceof CitizenEntity other) {
				other.stepAside(away);
			} else if (someone instanceof ServerPlayer player) {
				if (speak) {
					player.sendOverlayMessage(Component.translatable("message.minecities.builder.make_way",
							citizen.getName()));
				}
			} else if (someone instanceof net.minecraft.world.entity.Mob mob) {
				mob.getNavigation().moveTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5, 1.0);
				net.minecraft.world.phys.Vec3 push = net.minecraft.world.phys.Vec3.atCenterOf(away)
						.subtract(mob.position()).normalize().scale(0.3);
				mob.push(push.x, 0.1, push.z);
			}
		}
		if (speak) {
			lastCall = citizen.tickCount;
			citizen.getLookControl().setLookAt(there.getFirst());
			citizen.playSound(SoundEvents.VILLAGER_AMBIENT, 1.0f, 1.0f);
			if (there.stream().anyMatch(e -> e != citizen)) {
				citizen.say("make_way", Component.translatable("speech.minecities.make_way"));
			}
		}
		return true;
	}

	private void walkExactly(BlockPos target) {
		if (--repathOut <= 0 || citizen.getNavigation().isDone()) {
			repathOut = 20;
			citizen.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0, speed());
		}
	}

	/** Redescend au pied de son échafaudage. */
	private void descend(int ground) {
		BlockPos at = perch != null ? perch : citizen.blockPosition();
		citizen.snapTo(at.getX() + 0.5, ground, at.getZ() + 0.5, citizen.getYRot(), citizen.getXRot());
		citizen.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		citizen.fallDistance = 0;
		perch = null;
	}

	/**
	 * Démonte d'un coup la colonne d'échafaudage à cet endroit ; les échafaudages retournent dans ses
	 * poches. Ainsi il ne lui en faut jamais plus d'une colonne à la fois.
	 */
	private void takeDown(Construction construction, BlockPos column) {
		List<BlockPos> cells = construction.scaffolding().stream()
				.filter(c -> c.getX() == column.getX() && c.getZ() == column.getZ())
				.sorted(java.util.Comparator.comparingInt((BlockPos c) -> c.getY()).reversed()).toList();
		for (BlockPos cell : cells) {
			if (level().getBlockState(cell).is(Blocks.SCAFFOLDING)) {
				level().removeBlock(cell, false);
				citizen.carry(new ItemStack(Items.SCAFFOLDING));
			}
			construction.scaffolding().remove(cell);
		}
		if (!cells.isEmpty()) {
			level().playSound(null, column, SoundEvents.SCAFFOLDING_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
			Towns.get(level().getServer()).changed();
		}
	}

	/** Démonte ses échafaudages, du haut vers le bas ; ils retournent dans ses poches. */
	private void dismantle(Construction construction) {
		if (perch != null) {
			descend(construction.origin().getY());
			return;
		}
		List<BlockPos> scaffolding = construction.scaffolding();
		BlockPos top = scaffolding.stream().max(java.util.Comparator.comparingInt(BlockPos::getY)).orElseThrow();
		if (citizen.distanceToSqr(top.getX() + 0.5, citizen.getY(), top.getZ() + 0.5) > REACH * REACH) {
			goTo(new BlockPos(top.getX(), construction.origin().getY(), top.getZ()));
			return;
		}
		if (level().getBlockState(top).is(Blocks.SCAFFOLDING)) {
			level().removeBlock(top, false);
			level().playSound(null, top, SoundEvents.SCAFFOLDING_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
			citizen.carry(new ItemStack(Items.SCAFFOLDING));
			citizen.swing(InteractionHand.MAIN_HAND);
		}
		scaffolding.remove(top);
		cooldown = PACE;
		Towns.get(level().getServer()).changed();
	}

	@Override
	protected void onStop() {
		citizen.town().flatMap(t -> t.activeConstruction()).ifPresent(c -> c.release(citizen.getUUID()));
		// Le soir venu ou la faim pressante, il ne reste pas perché.
		if (perch != null && citizen.level() instanceof ServerLevel) {
			descend(citizen.town().flatMap(t -> t.activeConstruction()).map(c -> c.origin().getY())
					.orElse(perch.getY()));
		}
	}

	/** Est-il dans l'emprise du chantier ? */
	private boolean inside(Construction construction) {
		BlockPos at = citizen.blockPosition();
		return at.getX() >= construction.min().getX() && at.getX() <= construction.max().getX()
				&& at.getZ() >= construction.min().getZ() && at.getZ() <= construction.max().getZ()
				&& at.getY() >= construction.min().getY() - 1 && at.getY() <= construction.max().getY();
	}

	/** Le point au pied de l'emprise, juste dehors, le plus proche de ce bloc. */
	private static BlockPos outside(Construction construction, BlockPos pos) {
		BlockPos min = construction.min();
		BlockPos max = construction.max();
		int x = Math.clamp(pos.getX(), min.getX() - 1, max.getX() + 1);
		int z = Math.clamp(pos.getZ(), min.getZ() - 1, max.getZ() + 1);
		// Ramené sur le pourtour : le côté le plus proche.
		int toWest = x - (min.getX() - 1), toEast = max.getX() + 1 - x;
		int toNorth = z - (min.getZ() - 1), toSouth = max.getZ() + 1 - z;
		int least = Math.min(Math.min(toWest, toEast), Math.min(toNorth, toSouth));
		if (least == toWest) {
			x = min.getX() - 1;
		} else if (least == toEast) {
			x = max.getX() + 1;
		} else if (least == toNorth) {
			z = min.getZ() - 1;
		} else {
			z = max.getZ() + 1;
		}
		return new BlockPos(x, construction.origin().getY(), z);
	}

	private int count(Item item) {
		return citizen.inventory().stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
	}

	private void consume(Item item) {
		for (ItemStack stack : citizen.inventory()) {
			if (stack.is(item)) {
				stack.shrink(1);
				break;
			}
		}
		if (item == Items.WATER_BUCKET) {
			citizen.carry(new ItemStack(Items.BUCKET));
		}
	}

	/** Va chercher un matériau ; s'il n'y en a nulle part, prévient le maire et attend. */
	private void fetch(Construction construction, Item item) {
		if (freeSlots() == 0) {
			makeRoom();
			return;
		}
		Optional<BlockPos> source = find(supplies(), s -> s.is(item));
		// Un livreur dans la ville : le bâtisseur reste sur son chantier et lui réclame le matériau.
		if (source.isPresent() && town().workers(Job.COURIER) > 0 && citizen.wanted().isEmpty()) {
			int needed = (int) plan.subList(construction.step(), plan.size()).stream()
					.filter(p -> BuildPlan.material(p.state()).filter(item::equals).isPresent()).count();
			citizen.request(item, Math.max(1, Math.min(HAUL, needed)));
			citizen.need("wait:" + item, Component.translatable("speech.minecities.wait_courier",
					Math.max(1, Math.min(HAUL, needed)), item.getName(new ItemStack(item))));
		}
		if (citizen.wanted().filter(item::equals).isPresent()) {
			cooldown = 20;
			return;
		}
		if (source.isEmpty() && saw(construction, item)) {
			return;
		}
		if (source.isEmpty()) {
			int needed = Math.max(1, (int) plan.subList(construction.step(), plan.size()).stream()
					.filter(p -> BuildPlan.material(p.state()).filter(item::equals).isPresent()).count());
			citizen.complain("material:" + item, Component.translatable("message.minecities.builder.missing",
					citizen.getName(), needed, item.getName(new ItemStack(item))),
					Component.translatable("speech.minecities.missing", needed, item.getName(new ItemStack(item))));
			rest(400);
			return;
		}
		if (goTo(source.get())) {
			container(source.get()).ifPresent(c -> {
				citizen.carry(Stock.take(c, s -> s.is(item), HAUL));
				// Tant qu'il est là, il emporte aussi ce qu'il faudra pour la suite du chantier.
				Map<Item, Integer> ahead = BuildPlan.materials(plan.subList(construction.step(),
						Math.min(plan.size(), construction.step() + LOOKAHEAD)));
				for (Map.Entry<Item, Integer> e : ahead.entrySet()) {
					if (freeSlots() <= 1) {
						break;
					}
					int missing = Math.min(HAUL, e.getValue()) - count(e.getKey());
					if (missing > 0) {
						citizen.carry(Stock.take(c, s -> s.is(e.getKey()), missing));
					}
				}
			});
		}
	}

	/**
	 * Plus de planches nulle part : il scie lui-même des bûches de la même essence (celles des poteaux
	 * qu'il a démontés, ou celles des réserves), quatre planches par bûche. Vrai s'il s'en occupe.
	 */
	private boolean saw(Construction construction, Item planks) {
		Optional<Item> log = Crafts.logOf(planks);
		if (log.isEmpty()) {
			return false;
		}
		// Les bûches que le chantier pose lui-même restent des bûches.
		int logsKept = (int) plan.subList(construction.step(), plan.size()).stream()
				.filter(p -> BuildPlan.material(p.state()).filter(log.get()::equals).isPresent()).count();
		int spare = count(log.get()) - logsKept;
		if (spare > 0) {
			int needed = (int) plan.subList(construction.step(), plan.size()).stream()
					.filter(p -> BuildPlan.material(p.state()).filter(planks::equals).isPresent()).count();
			int sawn = Math.min(spare, Math.min(HAUL / 4, (needed + 3) / 4));
			for (int i = 0; i < sawn; i++) {
				consume(log.get());
			}
			citizen.carry(new ItemStack(planks, sawn * 4));
			citizen.swing(InteractionHand.MAIN_HAND);
			level().playSound(null, citizen.blockPosition(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 0.8F, 1.0F);
			cooldown = 20;
			return true;
		}
		// Il a déjà en poche les bûches du chantier : il va en chercher d'autres à scier.
		Optional<BlockPos> logs = find(supplies(), s -> s.is(log.get()));
		if (logs.isEmpty() || spare < 0) {
			return false;
		}
		if (goTo(logs.get())) {
			container(logs.get()).ifPresent(c -> citizen.carry(Stock.take(c, s -> s.is(log.get()), HAUL / 4)));
		}
		return true;
	}

	private void finish(Construction construction) {
		construction.finish();
		ServerLevel level = level();
		plan.stream().filter(p -> p.state().is(McBlocks.HOUSE_PLAQUE)).findFirst()
				.ifPresent(p -> TownService.declareBuilt(level, town(), p.pos()));
		Optional<Blueprint> blueprint = Blueprints.get(level.getServer(), construction.blueprint());
		TownService.notify(level.getServer(), town(), Component.translatable("message.minecities.builder.done",
				citizen.getName(), blueprint.map(Blueprint::displayName)
						.orElse(Component.literal(construction.blueprint().toString()))));
		Towns.get(level.getServer()).changed();
		planFor = null;
		plan = null;
	}
}
