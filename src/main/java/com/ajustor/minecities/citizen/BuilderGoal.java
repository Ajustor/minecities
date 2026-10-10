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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Le bâtisseur, à la table d'artisan. Il prend le premier chantier commandé par le maire et le bâtit
 * d'après son schéma, un bloc toutes les demi-secondes : il dégage, puis pose, en allant chercher
 * les matériaux au grenier (ou dans les coffres de son atelier) ; s'il en manque, le maire est
 * prévenu. Ce qu'il retire du terrain va au grenier. Le bâtiment fini se déclare tout seul.
 */
class BuilderGoal extends WorkGoal {
	/** Il pose à cette distance, comme un joueur bien placé. */
	private static final double REACH = 6.0;
	private static final int PACE = 10;
	/** Il emporte au plus tant de chaque matériau par voyage. */
	private static final int HAUL = 64;

	private UUID planFor;
	private List<BuildPlan.Placement> plan;
	private int cooldown;

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
				deliver(s -> false, 0);
			} else {
				rest(IDLE_SHORT);
			}
			return;
		}
		Construction construction = site.get();
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
		if (citizen.inventoryFull()) {
			deliver(s -> false, 0);
			return;
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
			finish(construction);
			return;
		}
		work(construction, plan.get(construction.step()));
	}

	private static boolean satisfied(BlockState current, BlockState target) {
		return target.isAir() ? current.isAir() || current.canBeReplaced() && current.getFluidState().isEmpty()
				: current.equals(target);
	}

	/** Ce qu'on ne casse pas : la roche-mère, l'hôtel de ville, les plaques des autres maisons. */
	private boolean unbreakable(BlockState current, BuildPlan.Placement p) {
		return current.getDestroySpeed(level(), p.pos()) < 0 || current.is(McBlocks.TOWN_HALL)
				|| current.is(McBlocks.HOUSE_PLAQUE) && !p.state().is(McBlocks.HOUSE_PLAQUE);
	}

	private void work(Construction construction, BuildPlan.Placement p) {
		ServerLevel level = level();
		BlockState current = level.getBlockState(p.pos());
		Optional<Item> material = BuildPlan.material(p.state());
		// Les matériaux d'abord : on ne dégage pas pour rien.
		if (material.isPresent() && count(material.get()) == 0) {
			fetch(construction, material.get());
			return;
		}
		if (!near(p.pos())) {
			goTo(new BlockPos(p.pos().getX(), construction.origin().getY(), p.pos().getZ()));
			return;
		}
		citizen.getNavigation().stop();
		citizen.getLookControl().setLookAt(p.pos().getX() + 0.5, p.pos().getY() + 0.5, p.pos().getZ() + 0.5);
		cooldown = PACE;
		citizen.swing(InteractionHand.MAIN_HAND);
		if (!current.isAir() && !current.canBeReplaced() || !current.getFluidState().isEmpty() && !p.state().is(Blocks.WATER)) {
			// Dégager : ce qui était là va au grenier (par ses poches).
			Block.getDrops(current, level, p.pos(), level.getBlockEntity(p.pos()), citizen, ItemStack.EMPTY)
					.forEach(citizen::carry);
			level.levelEvent(2001, p.pos(), Block.getId(current));
			level.setBlock(p.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			if (p.state().isAir()) {
				construction.advance();
			}
			Towns.get(level.getServer()).changed();
			return;
		}
		if (p.state().isAir()) {
			level.setBlock(p.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			construction.advance();
			return;
		}
		material.ifPresent(this::consume);
		level.setBlock(p.pos(), p.state(), Block.UPDATE_ALL);
		// Les portes et les lits posent leur autre moitié d'eux-mêmes.
		p.state().getBlock().setPlacedBy(level, p.pos(), p.state(), citizen, material.map(ItemStack::new).orElse(ItemStack.EMPTY));
		level.playSound(null, p.pos(), p.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.9f);
		construction.advance();
		Towns.get(level.getServer()).changed();
	}

	private boolean near(BlockPos pos) {
		return citizen.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH * REACH;
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
		Optional<BlockPos> source = find(supplies(), s -> s.is(item));
		if (source.isEmpty()) {
			int needed = (int) plan.subList(construction.step(), plan.size()).stream()
					.filter(p -> BuildPlan.material(p.state()).filter(item::equals).isPresent()).count();
			citizen.complain("material:" + item, Component.translatable("message.minecities.builder.missing",
					citizen.getName(), needed, item.getName(new ItemStack(item))));
			rest(400);
			return;
		}
		if (goTo(source.get())) {
			container(source.get()).ifPresent(c -> citizen.carry(Stock.take(c, s -> s.is(item), HAUL)));
		}
	}

	private void finish(Construction construction) {
		construction.finish();
		ServerLevel level = level();
		plan.stream().filter(p -> p.state().is(McBlocks.HOUSE_PLAQUE)).findFirst()
				.ifPresent(p -> TownService.declareBuilt(level, town(), p.pos()));
		Optional<Blueprint> blueprint = Blueprints.get(level.getServer(), construction.blueprint());
		TownService.notify(level.getServer(), town(), Component.translatable("message.minecities.builder.done",
				citizen.getName(), blueprint.<Component>map(b -> Component.translatable(b.translationKey()))
						.orElse(Component.literal(construction.blueprint().toString()))));
		Towns.get(level.getServer()).changed();
		planFor = null;
		plan = null;
	}
}
