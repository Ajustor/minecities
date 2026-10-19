package com.ajustor.minecities.build;

import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Commander un chantier, l'agrandir, et le montrer au joueur. */
public final class Builds {
	private Builds() {
	}

	/**
	 * Commande un chantier. Renvoie le refus, s'il y en a un : emprise hors du territoire, ou sur
	 * un chantier pas encore fini.
	 */
	public static Optional<Component> order(ServerLevel level, Town town, Blueprint blueprint, BuildStyle style,
			BlockPos origin, Rotation rotation) {
		return order(level, town, blueprint, style, origin, rotation, Optional.empty());
	}

	/** Commande un chantier qui remplace le bâtiment de cette emprise (il peut la recouvrir). */
	private static Optional<Component> order(ServerLevel level, Town town, Blueprint blueprint, BuildStyle style,
			BlockPos origin, Rotation rotation, Optional<BlockPos[]> replaced) {
		Optional<Component> refusal = check(level, town, blueprint, origin, rotation, replaced);
		if (refusal.isPresent()) {
			return refusal;
		}
		com.ajustor.minecities.town.Budget.pay(level, town, blueprint.level());
		town.order(Construction.order(blueprint, style, origin, rotation));
		Towns.get(level.getServer()).changed();
		return Optional.empty();
	}

	/**
	 * Ce qui empêcherait de commander ce chantier ici : schéma pas encore ouvert à ce palier, emprise
	 * hors du territoire, sur un chantier pas encore fini ou sur un bâtiment de la ville.
	 */
	public static Optional<Component> check(ServerLevel level, Town town, Blueprint blueprint, BlockPos origin,
			Rotation rotation) {
		return check(level, town, blueprint, origin, rotation, Optional.empty());
	}

	/** De même, pour un chantier qui remplace le bâtiment de l'emprise {@code replaced}. */
	public static Optional<Component> check(ServerLevel level, Town town, Blueprint blueprint, BlockPos origin,
			Rotation rotation, Optional<BlockPos[]> replaced) {
		// Les schémas d'avant les quatre niveaux ne servent plus qu'aux chantiers déjà commencés.
		if (blueprint.legacy()) {
			return Optional.of(Component.translatable("message.minecities.build.legacy", blueprint.displayName()));
		}
		if (town.tier().ordinal() < blueprint.tier().ordinal()) {
			return Optional.of(Component.translatable("message.minecities.build.locked",
					blueprint.displayName(), Component.translatable(blueprint.tier().translationKey())));
		}
		BlockPos[] bounds = BuildPlan.bounds(blueprint, origin, rotation);
		for (BlockPos corner : new BlockPos[]{bounds[0], bounds[1], new BlockPos(bounds[0].getX(), 0, bounds[1].getZ()),
				new BlockPos(bounds[1].getX(), 0, bounds[0].getZ())}) {
			if (!town.contains(level.dimension(), corner)) {
				return Optional.of(Component.translatable("message.minecities.build.outside", town.name()));
			}
		}
		if (town.constructions().stream().anyMatch(c -> !c.done() && c.overlaps(bounds[0], bounds[1]))) {
			return Optional.of(Component.translatable("message.minecities.build.overlap"));
		}
		if (occupied(town, bounds, replaced)) {
			return Optional.of(Component.translatable("message.minecities.build.occupied"));
		}
		// Les chantiers à partir du niveau 2 se paient en émeraudes (MC-57).
		Optional<Component> unaffordable = com.ajustor.minecities.town.Budget.unaffordable(level, town, blueprint.level());
		if (unaffordable.isPresent()) {
			return unaffordable;
		}
		// Un seul de chaque monument par ville, bâti ou en chantier.
		if (com.ajustor.minecities.town.Monument.of(blueprint.id()).isPresent()
				&& town.constructions().stream().anyMatch(c -> c.blueprint().equals(blueprint.id()))) {
			return Optional.of(Component.translatable("message.minecities.build.monument_once", blueprint.displayName()));
		}
		return Optional.empty();
	}

	/**
	 * Le plan complet d'un chantier : d'abord, s'il remplace un bâtiment fait à la main, l'air partout
	 * où l'ancien était et où le nouveau ne pose rien ; puis le plan du schéma. Vide si le schéma a disparu.
	 */
	public static List<BuildPlan.Placement> plan(net.minecraft.server.MinecraftServer server, Construction construction) {
		Optional<Blueprint> blueprint = Blueprints.get(server, construction.blueprint());
		if (blueprint.isEmpty()) {
			return List.of();
		}
		List<BuildPlan.Placement> plan = BuildPlan.of(blueprint.get(), construction.style(), construction.origin(),
				construction.rotation());
		Optional<BlockPos[]> clearing = construction.clearing();
		if (clearing.isEmpty()) {
			return plan;
		}
		java.util.Set<BlockPos> planned = new java.util.HashSet<>();
		plan.forEach(p -> planned.add(p.pos()));
		List<BuildPlan.Placement> full = new java.util.ArrayList<>();
		// Du haut vers le bas : le toit tombe avant les murs.
		BlockPos lo = clearing.get()[0];
		BlockPos hi = clearing.get()[1];
		// Les maisons voisines (murs compris) restent : un mur mitoyen n'est pas à nous seuls (MC-45).
		List<BlockPos[]> neighbours = Towns.get(server).all().stream()
				.filter(t -> t.constructions().contains(construction)).findFirst()
				.map(t -> t.houses().stream().filter(h -> h.min().isPresent() && h.max().isPresent()
						&& !(h.min().get().equals(lo) && h.max().get().equals(hi)))
						.map(h -> new BlockPos[]{h.min().get(), h.max().get()}).toList())
				.orElse(List.of());
		for (int y = hi.getY(); y >= lo.getY(); y--) {
			for (int x = lo.getX(); x <= hi.getX(); x++) {
				for (int z = lo.getZ(); z <= hi.getZ(); z++) {
					BlockPos pos = new BlockPos(x, y, z);
					// Le sol reste : le nouveau bâtiment s'y pose.
					if (!planned.contains(pos) && y > lo.getY() && neighbours.stream().noneMatch(n -> inside(pos, pos, n))) {
						full.add(new BuildPlan.Placement(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
					}
				}
			}
		}
		full.addAll(plan);
		return full;
	}

	/** Les familles de schémas des ateliers, par métier : un bâtiment fait main se rebâtit dans la sienne. */
	private static final java.util.Map<com.ajustor.minecities.town.Job, String> FAMILIES = java.util.Map.ofEntries(
			java.util.Map.entry(com.ajustor.minecities.town.Job.FARMER, "farm"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.COOK, "kitchen"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.LUMBERJACK, "sawmill"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.BREEDER, "barn"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.FISHERMAN, "fishery"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.BUILDER, "builder_hut"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.GUARD, "barracks"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.INVENTOR, "inventor_workshop"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.DOCTOR, "infirmary"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.MERCHANT, "market"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.BAKER, "bakery"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.SMITH, "smithy"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.MINER, "mine"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.JEWELER, "jeweler_workshop"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.MASON, "masonry"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.GLASSBLOWER, "glassworks"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.WEAVER, "weavery"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.BEEKEEPER, "apiary"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.LIBRARIAN, "library"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.TAVERNER, "tavern"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.TANNER, "tannery"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.CARPENTER, "carpentry"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.COURIER, "courier_post"),
			java.util.Map.entry(com.ajustor.minecities.town.Job.ARCHER, "archery"));

	/**
	 * Le schéma qui remplacerait ce bâtiment fait à la main (qui compte pour le niveau 1) : le niveau 2
	 * de sa famille. Ce qui a des lits devient une maison, un entrepôt un entrepôt, un atelier sans lit
	 * l'atelier de son premier métier. Vide s'il n'y a pas de famille (une tour de garde, un bureau de livreur…).
	 */
	public static Optional<Blueprint> replacement(net.minecraft.server.MinecraftServer server, com.ajustor.minecities.town.House house) {
		String family = house.warehouse() ? "warehouse" : !house.beds().isEmpty() ? "house"
				: !house.workstations().isEmpty() ? FAMILIES.get(house.workstations().getFirst().job()) : null;
		return family == null ? Optional.empty()
				: Blueprints.get(server, net.minecraft.resources.Identifier.fromNamespaceAndPath("minecities", family + "_2"));
	}

	/**
	 * L'emprise écraserait-elle une maison déclarée, un bâtiment fini, l'hôtel de ville ou une annexe
	 * (MC-45) ? Seul l'intérieur des bâtiments compte : un mur mitoyen peut servir aux deux. Le bâtiment
	 * qu'on remplace ne compte pas.
	 */
	private static boolean occupied(Town town, BlockPos[] bounds, Optional<BlockPos[]> replaced) {
		for (com.ajustor.minecities.town.House house : town.houses()) {
			if (house.min().isEmpty() || house.max().isEmpty()) {
				if (inside(house.plaque(), house.plaque(), bounds) && replaced.filter(r -> inside(house.plaque(),
						house.plaque(), r)).isEmpty()) {
					return true;
				}
				continue;
			}
			BlockPos min = house.min().get();
			BlockPos max = house.max().get();
			if (replaced.filter(r -> inside(min, max, grown(r))).isEmpty()
					&& overlaps(bounds, min.offset(1, 1, 1), max.offset(-1, -1, -1))) {
				return true;
			}
		}
		for (Construction built : town.constructions()) {
			if (built.done() && replaced.filter(r -> inside(built.min(), built.max(), grown(r))).isEmpty()
					&& overlaps(bounds, built.min().offset(1, 1, 1), built.max().offset(-1, -1, -1))) {
				return true;
			}
		}
		java.util.List<BlockPos> halls = new java.util.ArrayList<>(town.annexes());
		halls.add(town.hall());
		return halls.stream().anyMatch(h -> h.getX() >= bounds[0].getX() && h.getX() <= bounds[1].getX()
				&& h.getZ() >= bounds[0].getZ() && h.getZ() <= bounds[1].getZ());
	}

	private static boolean overlaps(BlockPos[] bounds, BlockPos min, BlockPos max) {
		return bounds[0].getX() <= max.getX() && bounds[1].getX() >= min.getX() && bounds[0].getZ() <= max.getZ()
				&& bounds[1].getZ() >= min.getZ() && bounds[0].getY() <= max.getY() && bounds[1].getY() >= min.getY();
	}

	/** De {@code min} à {@code max}, tout est-il dans cette emprise ? */
	private static boolean inside(BlockPos min, BlockPos max, BlockPos[] box) {
		return min.getX() >= box[0].getX() && min.getY() >= box[0].getY() && min.getZ() >= box[0].getZ()
				&& max.getX() <= box[1].getX() && max.getY() <= box[1].getY() && max.getZ() <= box[1].getZ();
	}

	/** L'emprise élargie d'un bloc (et de deux vers le bas : le sol, le dessous de la plaque). */
	private static BlockPos[] grown(BlockPos[] box) {
		return new BlockPos[]{box[0].offset(-1, -2, -1), box[1].offset(1, 1, 1)};
	}

	/** Où poser le schéma de remplacement : centré sur l'ancien bâtiment, posé sur son sol, porte au nord. */
	public static BlockPos replacementOrigin(Blueprint blueprint, com.ajustor.minecities.town.House house) {
		BlockPos min = house.min().orElse(house.plaque());
		BlockPos max = house.max().orElse(house.plaque());
		int cx = (min.getX() + max.getX()) / 2;
		int cz = (min.getZ() + max.getZ()) / 2;
		return new BlockPos(cx - blueprint.width() / 2, min.getY(), cz - blueprint.depth() / 2);
	}

	/** Rebâtit au niveau 2 le bâtiment fait à la main de cette plaque. Renvoie le refus, s'il y en a un. */
	public static Optional<Component> rebuild(ServerLevel level, Town town, BlockPos plaque) {
		Optional<com.ajustor.minecities.town.House> house = town.house(plaque);
		if (house.isEmpty() || house.get().min().isEmpty() || house.get().max().isEmpty()) {
			return Optional.of(Component.translatable("message.minecities.build.not_built"));
		}
		Optional<Blueprint> next = replacement(level.getServer(), house.get());
		if (next.isEmpty()) {
			return Optional.of(Component.translatable("screen.minecities.building.no_family"));
		}
		BlockPos origin = replacementOrigin(next.get(), house.get());
		Optional<Component> refusal = check(level, town, next.get(), origin, Rotation.NONE,
				Optional.of(new BlockPos[]{house.get().min().get(), house.get().max().get()}));
		if (refusal.isPresent()) {
			return refusal;
		}
		com.ajustor.minecities.town.Budget.pay(level, town, next.get().level());
		town.order(Construction.rebuild(next.get(), BuildStyle.PLAINS, origin, Rotation.NONE, house.get().min().get(),
				house.get().max().get()));
		Towns.get(level.getServer()).changed();
		return Optional.empty();
	}

	/** Agrandit le bâtiment bâti par la ville à cet endroit, d'après le schéma qui lui succède. */
	public static Optional<Component> upgrade(ServerLevel level, Town town, BlockPos where) {
		Optional<Construction> built = town.builtAt(where);
		if (built.isEmpty()) {
			return Optional.of(Component.translatable("message.minecities.build.not_built"));
		}
		Optional<Blueprint> current = Blueprints.get(level.getServer(), built.get().blueprint());
		Optional<Blueprint> next = current.flatMap(Blueprint::upgrade).flatMap(id -> Blueprints.get(level.getServer(), id));
		if (next.isEmpty()) {
			return Optional.of(Component.translatable("message.minecities.build.no_upgrade"));
		}
		return order(level, town, next.get(), built.get().style(), built.get().origin(), built.get().rotation(),
				Optional.of(new BlockPos[]{built.get().min(), built.get().max()}));
	}

	/** Dit au joueur ce que coûte le plan, et ce que le grenier en a déjà. */
	public static void describeMaterials(ServerLevel level, Town town, List<BuildPlan.Placement> plan,
			ServerPlayer player) {
		Map<Item, Integer> needs = BuildPlan.materials(plan);
		player.sendSystemMessage(Component.translatable("message.minecities.build.materials")
				.withStyle(ChatFormatting.GRAY));
		int height = plan.stream().mapToInt(p -> p.pos().getY()).max().orElse(0)
				- plan.stream().mapToInt(p -> p.pos().getY()).min().orElse(0);
		if (height > 4) {
			player.sendSystemMessage(Component.translatable("message.minecities.build.scaffolding", height - 2)
					.withStyle(ChatFormatting.GRAY));
		}
		TownService.granary(level, town).ifPresent(granary -> needs.forEach((item, count) -> {
			int stocked = Stock.count(granary, s -> s.is(item));
			player.sendSystemMessage(Component.translatable("message.minecities.build.material", count,
					item.getName(new ItemStack(item)), Math.min(stocked, count))
					.withStyle(stocked >= count ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		}));
	}

	/** Trace l'emprise au sol en particules, pour le joueur seul. */
	public static void outline(ServerLevel level, ServerPlayer player, BlockPos min, BlockPos max) {
		double y = min.getY() + 0.2;
		for (int x = min.getX(); x <= max.getX() + 1; x++) {
			for (double z : new double[]{min.getZ(), max.getZ() + 1}) {
				level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, false, x, y, z, 1, 0, 0, 0, 0);
			}
		}
		for (int z = min.getZ(); z <= max.getZ() + 1; z++) {
			for (double x : new double[]{min.getX(), max.getX() + 1}) {
				level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, false, x, y, z, 1, 0, 0, 0, 0);
			}
		}
	}
}
