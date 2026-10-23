package com.ajustor.minecities.citizen;

import com.ajustor.minecities.build.Blueprint;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildPlan;
import com.ajustor.minecities.build.Construction;
import com.ajustor.minecities.compat.EntityCompat;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le bâtisseur, à la table d'artisan. Il prend le premier chantier commandé par le maire et le bâtit
 * d'après son schéma, un bloc toutes les demi-secondes, depuis le pied du chantier (dehors), à toute
 * hauteur : il dégage avec le bon outil (pioche, pelle,
 * hache, qui s'usent), puis pose, en allant chercher outils et matériaux au grenier (ou dans les
 * coffres de son atelier) ; s'il en manque, il passe les blocs qui en ont besoin et pose tout le reste,
 * puis réclame au maire tout ce qui manque d'un coup. Plusieurs bâtisseurs travaillent
 * ensemble au même chantier, chacun au bloc suivant. Ce qu'il retire du terrain va au grenier. Le bâtiment fini se déclare tout seul.
 */
class BuilderGoal extends WorkGoal {
	/** Il pose à cette distance (à l'horizontale), depuis le pied du chantier. */
	private static final double REACH = 8.0;
	/** Un bloc toutes les 4 ticks : cinq par seconde. */
	private static final int PACE = 4;

	/** Le temps entre deux blocs : moitié moins pour un maître (MC-63). */
	private int pace() {
		return master() ? PACE / 2 : PACE;
	}
	/** Il emporte au plus tant de chaque matériau par voyage. */
	private static final int HAUL = 64;
	/** Au grenier, il emporte les matériaux des 128 prochains blocs, tant qu'il a de la place. */
	private static final int LOOKAHEAD = 128;

	private UUID planFor;
	private List<BuildPlan.Placement> plan;
	private int cooldown;
	private boolean delivering;
	private int repathOut;
	private int stuckInside;
	private int lastCall = -100;
	/**
	 * Les matériaux introuvables (ni aux réserves, ni chez un autre bâtisseur, ni à scier), jusqu'à
	 * quand : il passe les blocs qui en ont besoin, puis regarde de nouveau.
	 */
	private final Map<Item, Long> shortages = new java.util.HashMap<>();
	/** Il regarde de nouveau si un matériau introuvable est arrivé toutes les 20 secondes. */
	private static final int RECHECK = 400;

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
			// Pris dans un mur malgré tout : il est remis dehors, au bord du chantier le plus proche.
			BlockPos out = outside(construction, citizen.blockPosition());
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
			plan = com.ajustor.minecities.build.Builds.plan(level().getServer(), construction);
			planFor = construction.id();
		}
		// Ses poches se remplissent de ce qu'il dégage : il le porte au grenier quand il ne lui reste
		// qu'une case, en gardant ses outils et ce qui servira encore au chantier.
		if (delivering || freeSlots() <= 1) {
			java.util.Set<Item> needed = new java.util.HashSet<>();
			for (BuildPlan.Placement p : plan.subList(construction.step(), plan.size())) {
				BuildPlan.material(p.state()).ifPresent(needed::add);
			}
			java.util.function.Predicate<ItemStack> kept = s -> isTool(s) || needed.contains(s.getItem());
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
			// Une moitié de lit ou de porte posée d'avance a pu sauter depuis, sous la mise à jour d'un
			// voisin : on la refait avant de se dire fini (MC-50).
			int broken = brokenHalf();
			if (broken >= 0) {
				construction.redo(broken);
				return;
			}
			finish(construction);
			return;
		}
		int index = choose(construction);
		if (index < 0 && !shortages.isEmpty()) {
			// Il a posé tout ce qu'il pouvait : il réclame d'un coup ce qui manque, et attend.
			claimShortages(construction);
			return;
		}
		if (index < 0) {
			// Tout ce qui est à faire est réservé par les autres bâtisseurs : il attend son tour.
			cooldown = 20;
			return;
		}
		construction.claim(index, citizen.getUUID());
		work(construction, index, plan.get(index));
	}

	/** Le premier lit ou la première porte du plan qui n'est plus en place ; -1 s'il n'y en a pas. */
	private int brokenHalf() {
		for (int i = 0; i < plan.size(); i++) {
			BuildPlan.Placement p = plan.get(i);
			if ((p.state().hasProperty(BlockStateProperties.BED_PART)
					|| p.state().hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))
					&& !satisfied(level().getBlockState(p.pos()), p.state())) {
				return i;
			}
		}
		return -1;
	}

	/** Combien de blocs à venir il regarde pour choisir le sien. */
	private static final int WINDOW = 64;
	/** Jusqu'où il cherche un autre bâtisseur à qui emprunter, autour du chantier. */
	private static final int LEND_RANGE = 48;

	/**
	 * Choisit le bloc à poser : celui qu'il a déjà réservé, sinon, parmi les prochains du plan que
	 * personne d'autre n'a réservés, le premier qu'il peut poser tout de suite (il a le matériau et
	 * l'outil), à défaut le premier libre. Ainsi plusieurs bâtisseurs se partagent le chantier sans
	 * se gêner. Les blocs dont le matériau est introuvable ne comptent pas : il va plus loin dans le
	 * plan. Renvoie -1 si tout est réservé ou en attente d'un matériau.
	 */
	private int choose(Construction construction) {
		long now = level().getGameTime();
		shortages.entrySet().removeIf(e -> e.getValue() <= now || count(e.getKey()) > 0);
		int fallback = -1;
		int seen = 0;
		for (int i = construction.step(); i < plan.size() && seen < WINDOW; i++) {
			// La fenêtre compte les blocs qui restent à faire : ceux déjà posés plus loin (passés devant un
			// matériau manquant) ne la remplissent pas.
			if (construction.isPlaced(i)) {
				continue;
			}
			BuildPlan.Placement p = plan.get(i);
			BlockState current = level().getBlockState(p.pos());
			if (satisfied(current, p.state()) || unbreakable(current, p)) {
				construction.markPlaced(i);
				continue;
			}
			if (waiting(p)) {
				continue;
			}
			seen++;
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

	/**
	 * Ce bloc attend-il un matériau introuvable ? Tant qu'il en manque, il ne pose pas non plus ce qui
	 * ne tiendrait pas encore (une torche, une porte sans son appui, du sable sur le vide) : l'appui
	 * qu'il a passé viendra d'abord.
	 */
	private boolean waiting(BuildPlan.Placement p) {
		if (shortages.isEmpty()) {
			return false;
		}
		Optional<Item> material = BuildPlan.material(p.state());
		if (material.filter(m -> shortages.containsKey(m) && count(m) == 0).isPresent()) {
			return true;
		}
		if (p.state().isAir()) {
			return false;
		}
		return !p.state().canSurvive(level(), p.pos())
				|| p.state().getBlock() instanceof net.minecraft.world.level.block.FallingBlock
						&& net.minecraft.world.level.block.FallingBlock.isFree(level().getBlockState(p.pos().below()));
	}

	/**
	 * Il a posé tout ce qu'il pouvait : il dit ce qui manque encore (combien de chaque matériau pour
	 * finir le chantier), et le maire en est prévenu d'un seul message.
	 */
	private void claimShortages(Construction construction) {
		Map<Item, Integer> left = new java.util.LinkedHashMap<>();
		for (int i = construction.step(); i < plan.size(); i++) {
			if (!construction.isPlaced(i)) {
				BuildPlan.material(plan.get(i).state()).filter(shortages::containsKey)
						.ifPresent(m -> left.merge(m, 1, Integer::sum));
			}
		}
		if (left.isEmpty()) {
			shortages.clear();
			return;
		}
		net.minecraft.network.chat.MutableComponent list = Component.empty();
		left.forEach((item, count) -> {
			if (!list.getSiblings().isEmpty()) {
				list.append(", ");
			}
			list.append(Component.translatable("message.minecities.build.supplied_item", count,
					item.getName(new ItemStack(item))));
		});
		String key = "short:" + left.keySet().stream().map(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM
				.getKey(i).toString()).sorted().collect(java.util.stream.Collectors.joining(","));
		citizen.complain(key, Component.translatable("message.minecities.builder.short", citizen.getName(), list),
				Component.translatable("speech.minecities.short", list));
		rest(RECHECK);
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

	/** Ce qu'on ne casse pas : la roche-mère, l'hôtel de ville, ses annexes et sa boîte à impôts, les plaques des autres maisons. */
	private boolean unbreakable(BlockState current, BuildPlan.Placement p) {
		return current.getDestroySpeed(level(), p.pos()) < 0 || current.is(McBlocks.TOWN_HALL)
				|| current.is(McBlocks.TOWN_ANNEX) || current.is(McBlocks.TAX_BOX)
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
		// Il travaille du dehors : il ne s'emmure pas dans le bâtiment qu'il élève.
		if (!position(construction, p.pos())) {
			return;
		}
		citizen.getNavigation().stop();
		citizen.getLookControl().setLookAt(p.pos().getX() + 0.5, p.pos().getY() + 0.5, p.pos().getZ() + 0.5);
		cooldown = pace();
		EntityCompat.swing(citizen, InteractionHand.MAIN_HAND);
		citizen.act(Action.BUILD, 10);
		if (clearing && !com.ajustor.minecities.town.Protection.mayBreak(level, town(), p.pos())) {
			// Un terrain protégé (un mod de protection, MC-85) : il le laisse tel quel.
			citizen.complain("protected", Component.translatable("message.minecities.protected", citizen.getName(),
					p.pos().toShortString()), Component.translatable("speech.minecities.protected"));
			construction.markPlaced(index);
			return;
		}
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

	@Override
	protected double speed() {
		return 0.85;
	}

	/** À portée : 8 blocs à l'horizontale, à toute hauteur. */
	private boolean near(BlockPos pos) {
		double dx = citizen.getX() - (pos.getX() + 0.5);
		double dz = citizen.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dz * dz <= REACH * REACH;
	}

	/**
	 * Se met en place pour poser ce bloc : au pied du chantier, dehors, à portée à l'horizontale ; il
	 * pose à toute hauteur de là. Renvoie vrai quand il est en place.
	 */
	private boolean position(Construction construction, BlockPos pos) {
		if (level().getBlockState(citizen.blockPosition().below()).is(Blocks.SCAFFOLDING)) {
			// Resté sur un échafaudage d'avant (un monde d'une version précédente) : il redescend.
			descend();
			return false;
		}
		// Le sol au pied du chantier n'est pas toujours celui du chantier : une butte, un creux.
		BlockPos foot = outside(construction, pos);
		if (inside(construction)) {
			leave(foot);
			return false;
		}
		stuckInside = 0;
		if (!near(pos)) {
			goTo(foot);
			return false;
		}
		return true;
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

	/** Redescend au pied de l'échafaudage où il se tient. */
	private void descend() {
		BlockPos at = citizen.blockPosition();
		BlockPos base = footing(at.getX(), at.getY(), at.getZ());
		citizen.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, citizen.getYRot(), citizen.getXRot());
		citizen.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		citizen.fallDistance = 0;
	}

	/**
	 * Démonte les échafaudages laissés par un chantier d'une version précédente, du haut vers le bas ;
	 * ils retournent dans ses poches, puis au grenier.
	 */
	private void dismantle(Construction construction) {
		List<BlockPos> scaffolding = construction.scaffolding();
		BlockPos top = scaffolding.stream().max(java.util.Comparator.comparingInt(BlockPos::getY)).orElseThrow();
		if (citizen.distanceToSqr(top.getX() + 0.5, citizen.getY(), top.getZ() + 0.5) > REACH * REACH) {
			goTo(footing(top.getX(), top.getY(), top.getZ()));
			return;
		}
		if (level().getBlockState(top).is(Blocks.SCAFFOLDING)) {
			level().removeBlock(top, false);
			level().playSound(null, top, SoundEvents.SCAFFOLDING_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
			citizen.carry(new ItemStack(Items.SCAFFOLDING));
			EntityCompat.swing(citizen, InteractionHand.MAIN_HAND);
		}
		scaffolding.remove(top);
		cooldown = pace();
		Towns.get(level().getServer()).changed();
	}

	@Override
	protected void onStop() {
		citizen.town().flatMap(t -> t.activeConstruction()).ifPresent(c -> c.release(citizen.getUUID()));
	}

	/** Est-il dans l'emprise du chantier ? */
	private boolean inside(Construction construction) {
		BlockPos at = citizen.blockPosition();
		return at.getX() >= construction.min().getX() && at.getX() <= construction.max().getX()
				&& at.getZ() >= construction.min().getZ() && at.getZ() <= construction.max().getZ()
				&& at.getY() >= construction.min().getY() - 1 && at.getY() <= construction.max().getY();
	}

	/** Le point au pied de l'emprise, juste dehors, le plus proche de ce bloc, sur le sol qu'on y trouve. */
	private BlockPos outside(Construction construction, BlockPos pos) {
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
		return footing(x, construction.origin().getY(), z);
	}

	/** Jusqu'où il cherche le sol, en montant puis en descendant. */
	private static final int FOOTING = 16;

	/**
	 * Où se tenir à la verticale de (x, z), à partir de cette hauteur : la première case dégagée en
	 * montant (le chantier adossé à une butte), puis le sol en descendant (un creux, le pied d'un
	 * échafaudage). Sans quoi, remis dehors à la hauteur du chantier, il le serait dans la butte, à y
	 * étouffer.
	 */
	private BlockPos footing(int x, int y, int z) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, y, z);
		for (int up = 0; up < FOOTING && !open(at); up++) {
			at.move(net.minecraft.core.Direction.UP);
		}
		if (!open(at)) {
			return new BlockPos(x, y, z);
		}
		for (int down = 0; down < FOOTING && passable(at.below()) && level().getFluidState(at.below()).isEmpty()
				&& at.getY() > level().getMinY(); down++) {
			at.move(net.minecraft.core.Direction.DOWN);
		}
		return at.immutable();
	}

	/** Y tient-il debout : rien de solide aux pieds ni à la tête ? */
	private boolean open(BlockPos pos) {
		return passable(pos) && passable(pos.above());
	}

	/** Rien de solide là ; un échafaudage se traverse (on monte dedans, on en descend). */
	private boolean passable(BlockPos pos) {
		BlockState state = level().getBlockState(pos);
		return state.is(Blocks.SCAFFOLDING) || state.getCollisionShape(level(), pos).isEmpty();
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

	/** Il avance par à-coups autour du chantier : il ne renonce pas à une cible faute de s'en approcher. */
	@Override
	protected boolean givesUp() {
		return false;
	}

	/** Depuis quand il attend le livreur, et jusqu'à quand il va lui-même chercher ses matériaux. */
	private long requestedAt;
	private long selfServeUntil;
	/** Il attend le livreur une minute au plus ; puis y va lui-même pendant deux minutes. */
	private static final int COURIER_PATIENCE = 1200;
	private static final int SELF_SERVE = 2400;

	/** Va chercher un matériau ; s'il n'y en a nulle part, prévient le maire et attend. */
	private void fetch(Construction construction, Item item) {
		if (freeSlots() == 0) {
			makeRoom();
			return;
		}
		Optional<BlockPos> source = find(supplies(), s -> s.is(item));
		// Un livreur dans la ville : le bâtisseur reste sur son chantier et lui réclame le matériau.
		long now = level().getGameTime();
		if (source.isPresent() && town().workers(Job.COURIER) > 0 && citizen.wanted().isEmpty()
				&& now >= selfServeUntil) {
			requestedAt = now;
			int needed = (int) plan.subList(construction.step(), plan.size()).stream()
					.filter(p -> BuildPlan.material(p.state()).filter(item::equals).isPresent()).count();
			citizen.request(item, Math.max(1, Math.min(HAUL, needed)));
			citizen.need("wait:" + item, Component.translatable("speech.minecities.wait_courier",
					Math.max(1, Math.min(HAUL, needed)), item.getName(new ItemStack(item))));
		}
		if (citizen.wanted().filter(item::equals).isPresent()) {
			if (now - requestedAt <= COURIER_PATIENCE) {
				cooldown = 20;
				return;
			}
			// Le livreur ne vient pas (alité, affamé, coincé) : il cesse de l'attendre et y va lui-même
			// pendant un moment.
			citizen.clearRequest();
			citizen.satisfied("wait:" + item);
			selfServeUntil = now + SELF_SERVE;
		}
		// Plus rien dans les réserves, mais un autre bâtisseur en a en poche sans en avoir besoin : il
		// le lui emprunte. Sinon le dernier bloc d'un chantier attendrait sans fin ce que garde un autre.
		if (source.isEmpty()) {
			Optional<CitizenEntity> lender = lender(construction, item);
			if (lender.isPresent()) {
				borrow(construction, lender.get(), item);
				return;
			}
		}
		if (source.isEmpty() && saw(construction, item)) {
			return;
		}
		if (source.isEmpty()) {
			// Introuvable : il passe les blocs qui en ont besoin et continue le reste du chantier ; il
			// le réclamera quand il aura posé tout ce qu'il pouvait.
			int needed = Math.max(1, (int) plan.subList(construction.step(), plan.size()).stream()
					.filter(p -> BuildPlan.material(p.state()).filter(item::equals).isPresent()).count());
			citizen.need("material:" + item, Component.translatable("speech.minecities.missing", needed,
					item.getName(new ItemStack(item))));
			shortages.put(item, level().getGameTime() + RECHECK);
			construction.release(citizen.getUUID());
			cooldown = 1;
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
	 * Un autre bâtisseur du chantier qui porte ce matériau sans en avoir besoin tout de suite : aucun des
	 * blocs qu'il a réservés ne le demande.
	 */
	private Optional<CitizenEntity> lender(Construction construction, Item item) {
		BlockPos site = construction.origin();
		for (CitizenEntity other : level().getEntitiesOfClass(CitizenEntity.class, new net.minecraft.world.phys.AABB(site)
						.inflate(LEND_RANGE), o -> o != citizen && o.isAlive()
						&& o.job().filter(j -> j.job() == Job.BUILDER).isPresent()
						&& o.townId().equals(citizen.townId()))) {
			if (other.inventory().stream().noneMatch(s -> s.is(item))) {
				continue;
			}
			if (!claimsNeeding(construction, other, item)) {
				return Optional.of(other);
			}
		}
		return Optional.empty();
	}

	/** Un des blocs que cet autre bâtisseur a réservés demande-t-il ce matériau ? */
	private boolean claimsNeeding(Construction construction, CitizenEntity other, Item item) {
		int end = Math.min(plan.size(), construction.step() + WINDOW);
		for (int i = construction.step(); i < end; i++) {
			if (construction.claimant(i).filter(other.getUUID()::equals).isPresent()
					&& BuildPlan.material(plan.get(i).state()).filter(item::equals).isPresent()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Va trouver ce bâtisseur et lui prend ce qu'il faut de ce matériau (de quoi finir le chantier, une
	 * brassée au plus).
	 */
	private void borrow(Construction construction, CitizenEntity lender, Item item) {
		if (citizen.distanceToSqr(lender) > REACH * REACH) {
			goTo(lender.blockPosition());
			return;
		}
		int wanted = Math.max(1, Math.min(HAUL, (int) plan.subList(construction.step(), plan.size()).stream()
				.filter(p -> BuildPlan.material(p.state()).filter(item::equals).isPresent()).count()));
		for (ItemStack stack : lender.inventory()) {
			if (wanted <= 0 || freeSlots() == 0) {
				break;
			}
			if (stack.is(item)) {
				int moved = Math.min(wanted, stack.getCount());
				citizen.carry(stack.split(moved));
				wanted -= moved;
			}
		}
		citizen.getLookControl().setLookAt(lender);
		lender.getLookControl().setLookAt(citizen);
		EntityCompat.swing(citizen, InteractionHand.MAIN_HAND);
		cooldown = pace();
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
			EntityCompat.swing(citizen, InteractionHand.MAIN_HAND);
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
