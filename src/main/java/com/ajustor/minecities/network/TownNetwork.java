package com.ajustor.minecities.network;

import com.ajustor.minecities.compat.EntityCompat;
import net.minecraft.core.BlockPos;
import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.citizen.Hunger;
import com.ajustor.minecities.town.Chronicle;
import com.ajustor.minecities.town.Election;
import com.ajustor.minecities.town.Elections;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Festivals;
import com.ajustor.minecities.town.House;
import com.ajustor.minecities.town.Raids;
import com.ajustor.minecities.town.Stock;
import net.minecraft.world.item.Items;
import com.ajustor.minecities.town.MarriageRequest;
import com.ajustor.minecities.town.Membership;
import com.ajustor.minecities.town.Resident;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.TownTier;
import com.ajustor.minecities.town.Towns;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** L'écran de l'hôtel de ville : ce que le serveur lui envoie, et ce que le joueur y décide. */
public final class TownNetwork {
	/** On agit sur l'hôtel de ville à portée de main, comme sur un coffre. */
	private static final double REACH = 8.0;

	private TownNetwork() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(OpenTownPayload.TYPE, OpenTownPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GhostPayload.TYPE, GhostPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TownActionPayload.TYPE, TownActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TownActionPayload.TYPE,
				(payload, context) -> act(context.player(), payload));
	}

	/** Ouvre l'écran de l'hôtel de ville pour ce joueur. */
	public static void open(ServerLevel level, Town town, ServerPlayer player) {
		ServerPlayNetworking.send(player, new OpenTownPayload(snapshot(level, town, player)));
	}

	public static TownSnapshot snapshot(ServerLevel level, Town town, ServerPlayer player) {
		List<TownSnapshot.CitizenRow> rows = new ArrayList<>();
		Map<UUID, Resident> residents = town.residents();
		for (Map.Entry<UUID, Resident> e : residents.entrySet()) {
			Resident r = e.getValue();
			CitizenEntity citizen = level.getEntity(e.getKey()) instanceof CitizenEntity c ? c : null;
			String hunger = citizen == null ? "command.minecities.citizens.away"
					: Hunger.state(citizen.hunger()).translationKey();
			String spouse = r.spouse().map(residents::get).map(Resident::name).orElse("");
			rows.add(new TownSnapshot.CitizenRow(e.getKey(), r.name(),
					r.job().map(j -> j.job().translationKey()).orElse(""), hunger, r.hasHome(), spouse, r.isChild(),
					town.mood(e.getKey()).orElse(-1), citizen == null ? Optional.empty() : citizen.lastNeed()));
		}
		List<TownSnapshot.BuildingRow> buildings = new ArrayList<>();
		for (House house : town.houses()) {
			int sleepers = (int) residents.values().stream().filter(r -> r.bed().filter(house.beds()::contains)
					.isPresent()).count();
			List<String> roles = new ArrayList<>(house.workstations().stream().map(w -> (w.job().unlockedAt(town.tier())
					? "" : "!") + w.job().translationKey()).toList());
			if (house.warehouse()) {
				// L'entrepôt se montre comme tel, avec ses métiers s'il en a.
				roles.addFirst("screen.minecities.building.warehouse");
			}
			buildings.add(new TownSnapshot.BuildingRow(house.plaque(), house.beds().size(), sleepers, roles,
					house.village(), false));
		}
		// Les monuments, après les bâtiments déclarés.
		for (com.ajustor.minecities.build.Construction construction : town.constructions()) {
			if (construction.done() && com.ajustor.minecities.town.Monument.of(construction.blueprint()).isPresent()) {
				buildings.add(new TownSnapshot.BuildingRow(construction.origin(), 0, 0,
						List.of("blueprint." + construction.blueprint().getNamespace() + "."
								+ construction.blueprint().getPath()), false, true));
			}
		}
		List<TownSnapshot.RequestRow> requests = new ArrayList<>();
		for (MarriageRequest request : town.marriageRequests()) {
			requests.add(new TownSnapshot.RequestRow(request.id(),
					town.resident(request.first()).map(Resident::name).orElse("?"),
					town.resident(request.second()).map(Resident::name).orElse("?")));
		}
		long employed = residents.values().stream().filter(r -> r.job().isPresent()).count();
		int meals = TownService.granary(level, town).map(TownService::countMeals).orElse(0);
		TownTier next = town.tier().next();
		long now = level.getGameTime();
		Chronicle chronicle = town.chronicle();
		TownSnapshot.Life life = new TownSnapshot.Life(town.averageMood().map(m -> (int) Math.round(m * 10)).orElse(-1),
				chronicle.partying(now), chronicle.canCelebrate(now), chronicle.untilNextFestival(now), Festivals.cost(),
				chronicle.alarmed(now) || Raids.underAttack(town.id()),
				TownService.granary(level, town).map(g -> Stock.count(g, s -> s.is(Items.EMERALD))).orElse(0),
				chronicle.earnedToday(now), town.activeAnnexes().size(), town.tier().annexes(), town.appeal(),
				chronicle.disaster(now).orElse(""));
		return new TownSnapshot(town.hall(), town.name(), town.tier().translationKey(), town.population(),
				town.citizens(), town.villagers(), town.capacity(), town.homes().size(), town.workstations(),
				(int) employed, meals, next == null ? "" : next.translationKey(),
				next == null ? 0 : next.minCitizens(), next == null ? 0 : next.minHouses(),
				town.canManage(player.getUUID()), life, council(level, town), rows, buildings, requests,
				plans(level, town), members(town, player), ballot(level, town, player), map(town));
	}

	/** La carte du territoire : seulement cette ville, dans sa dimension. */
	public static TownSnapshot.TownMap map(Town town) {
		List<TownSnapshot.Plot> plots = new ArrayList<>();
		for (House house : town.houses()) {
			BlockPos min = house.min().orElse(house.plaque().offset(-2, 0, -2));
			BlockPos max = house.max().orElse(house.plaque().offset(2, 0, 2));
			String kind = house.warehouse() ? "warehouse" : house.village() ? "village"
					: !house.beds().isEmpty() && !house.workstations().isEmpty() ? "both"
					: !house.beds().isEmpty() ? "home" : "workshop";
			plots.add(new TownSnapshot.Plot(house.plaque(), min.getX(), min.getZ(), max.getX(), max.getZ(), kind));
		}
		for (com.ajustor.minecities.build.Construction c : town.constructions()) {
			boolean monument = com.ajustor.minecities.town.Monument.of(c.blueprint()).isPresent();
			// Un chantier fini est déjà un bâtiment déclaré, sauf un monument (qui n'a pas de plaque).
			if (!c.done() || monument) {
				plots.add(new TownSnapshot.Plot(c.origin(), c.min().getX(), c.min().getZ(), c.max().getX(),
						c.max().getZ(), c.done() ? "monument" : "site"));
			}
		}
		return new TownSnapshot.TownMap(town.radius(), town.activeAnnexes(), Town.ANNEX_RADIUS, plots);
	}

	/** Le bâtiment de la ville qui commence en {@code at} luit ; faux s'il n'y en a pas. */
	public static boolean glow(ServerLevel level, Town town, ServerPlayer player, BlockPos at) {
		Optional<BlockPos[]> bounds = bounds(town, at);
		bounds.ifPresent(b -> {
			com.ajustor.minecities.town.Highlights.add(level, b[0], b[1]);
			player.sendSystemMessage(Component.translatable("message.minecities.map.glow", at.toShortString()));
		});
		return bounds.isPresent();
	}

	/** Le bâtiment de la carte qui commence en {@code at} : sa plaque, ou l'origine de son chantier. */
	private static Optional<BlockPos[]> bounds(Town town, BlockPos at) {
		Optional<House> house = town.house(at);
		if (house.isPresent()) {
			return Optional.of(new BlockPos[]{house.get().min().orElse(at.offset(-2, -1, -2)),
					house.get().max().orElse(at.offset(2, 3, 2))});
		}
		return town.constructions().stream().filter(c -> c.origin().equals(at)).findFirst()
				.map(c -> new BlockPos[]{c.min(), c.max()});
	}

	/** L'élection, vue par ce joueur. */
	private static TownSnapshot.Ballot ballot(ServerLevel level, Town town, ServerPlayer player) {
		Election election = town.election();
		long now = level.getGameTime();
		List<TownSnapshot.PlayerRow> candidates = election.candidates().stream()
				.map(e -> new TownSnapshot.PlayerRow(e.getKey(), e.getValue(), town.rank(e.getKey()).serializedName()))
				.toList();
		return new TownSnapshot.Ballot(election.phase().serializedName(),
				election.phase() == Election.Phase.NONE ? -1 : Math.max(0, election.phaseEnds() - now),
				election.termEnds() > 0 && election.phase() == Election.Phase.NONE
						? Math.max(0, election.termEnds() - now) : -1,
				election.round(), candidates, election.hasVoted(player.getUUID()),
				(int) election.petition().stream().filter(town::isMember).count(), Elections.signaturesNeeded(town),
				election.petition().contains(player.getUUID()), Elections.untilAllowed(town, now),
				election.lastWinner().orElse(""), election.lastVotes());
	}

	/** Les joueurs de la ville : le maire, ses adjoints, les habitants ; les demandes ; les droits. */
	private static TownSnapshot.Members members(Town town, ServerPlayer player) {
		Membership membership = town.membership();
		List<TownSnapshot.PlayerRow> players = new ArrayList<>();
		players.add(new TownSnapshot.PlayerRow(town.mayor(), town.mayorName(), Membership.Rank.MAYOR.serializedName()));
		for (UUID deputy : town.deputies()) {
			ServerPlayer online = player.level().getServer().getPlayerList().getPlayer(deputy);
			String name = online != null ? online.getGameProfile().name()
					: membership.residents().getOrDefault(deputy, deputy.toString().substring(0, 8));
			players.add(new TownSnapshot.PlayerRow(deputy, name, Membership.Rank.DEPUTY.serializedName()));
		}
		for (Map.Entry<UUID, String> resident : membership.residentList()) {
			if (town.rank(resident.getKey()) == Membership.Rank.RESIDENT) {
				players.add(new TownSnapshot.PlayerRow(resident.getKey(), resident.getValue(),
						Membership.Rank.RESIDENT.serializedName()));
			}
		}
		List<TownSnapshot.PlayerRow> requests = membership.requests().stream()
				.map(e -> new TownSnapshot.PlayerRow(e.getKey(), e.getValue(), Membership.Rank.VISITOR.serializedName()))
				.toList();
		List<Boolean> resident = new ArrayList<>();
		List<Boolean> visitor = new ArrayList<>();
		for (Membership.Permission permission : Membership.Permission.values()) {
			resident.add(membership.permissions(Membership.Rank.RESIDENT).contains(permission));
			visitor.add(membership.permissions(Membership.Rank.VISITOR).contains(permission));
		}
		return new TownSnapshot.Members(town.rank(player.getUUID()).serializedName(),
				membership.hasRequested(player.getUUID()), players, requests, resident, visitor);
	}

	/** Les bâtiments qu'on peut commander, chacun à son premier niveau : les ouverts d'abord. */
	public static List<TownSnapshot.PlanRow> plans(ServerLevel level, Town town) {
		return com.ajustor.minecities.build.Blueprints.all(level.getServer()).values().stream()
				.filter(b -> !b.legacy() && b.level() == 1)
				.sorted(java.util.Comparator.comparingInt((com.ajustor.minecities.build.Blueprint b) -> b.tier().ordinal())
						.thenComparing(b -> b.id().toString()))
				.map(b -> new TownSnapshot.PlanRow(b.id().toString(), b.translationKey(), b.id().getPath(), b.width(),
						b.depth(), b.height(), b.kind(),
						town.tier().ordinal() < b.tier().ordinal() ? b.tier().translationKey() : ""))
				.toList();
	}

	private static TownSnapshot.Council council(ServerLevel level, Town town) {
		List<String> deputies = new ArrayList<>();
		for (UUID deputy : town.deputies()) {
			ServerPlayer online = level.getServer().getPlayerList().getPlayer(deputy);
			deputies.add(online != null ? online.getGameProfile().name() : deputy.toString().substring(0, 8));
		}
		List<String> decrees = new ArrayList<>();
		List<Boolean> decreed = new ArrayList<>();
		for (com.ajustor.minecities.town.Decree decree : com.ajustor.minecities.town.Decree.values()) {
			decrees.add(decree.serializedName());
			decreed.add(town.decreed(decree));
		}
		List<String> partners = new ArrayList<>();
		List<Boolean> open = new ArrayList<>();
		for (Town other : Towns.get(level.getServer()).all()) {
			if (town.partners().contains(other.id()) || other.partners().contains(town.id())) {
				partners.add(other.name());
				open.add(com.ajustor.minecities.town.Trade.trading(town, other));
			}
		}
		return new TownSnapshot.Council(town.mayorName(), deputies, decrees, decreed, partners, open);
	}

	public static void act(ServerPlayer player, TownActionPayload payload) {
		ServerLevel level = player.level();
		if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(payload.hall())) > REACH * REACH) {
			return;
		}
		Optional<Town> found = Towns.get(level.getServer()).byHall(level.dimension(), payload.hall());
		if (found.isEmpty()) {
			return;
		}
		Town town = found.get();
		if (payload.action().startsWith("building:")) {
			// « building:x,y,z » : l'interface de ce bâtiment de la ville.
			String[] xyz = payload.action().substring("building:".length()).split(",");
			try {
				BlockPos plaque = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
				if (town.house(plaque).isPresent()) {
					BuildingNetwork.open(level, plaque, player);
				}
			} catch (RuntimeException e) {
				// Une demande mal formée : on l'ignore.
			}
			return;
		}
		if (payload.action().startsWith("glow:")) {
			// « glow:x,y,z » : le bâtiment cliqué sur la carte luit quelques secondes.
			String[] xyz = payload.action().substring("glow:".length()).split(",");
			try {
				glow(level, town, player, new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]),
						Integer.parseInt(xyz[2])));
			} catch (RuntimeException e) {
				// Une demande mal formée : on l'ignore.
			}
			return;
		}
		if (payload.action().startsWith("permission:")) {
			// « permission:<rang>:<droit> » : le maire donne ou retire ce droit à l'habitant ou au visiteur.
			String[] parts = payload.action().split(":", 3);
			if (parts.length == 3 && town.mayor().equals(player.getUUID())) {
				Membership.Rank.fromSerializedName(parts[1]).ifPresent(rank -> Membership.Permission
						.fromSerializedName(parts[2]).ifPresent(permission -> {
							town.membership().set(rank, permission, !town.membership().permissions(rank)
									.contains(permission));
							Towns.get(level.getServer()).changed();
						}));
			}
			open(level, town, player);
			return;
		}
		if (payload.action().startsWith("plan:")) {
			// « plan:<style>:<schéma> » : le maire (ou un adjoint) prend le plan de ce bâtiment.
			if (!town.canManage(player.getUUID())) {
				return;
			}
			String[] parts = payload.action().split(":", 3);
			Optional<com.ajustor.minecities.build.Blueprint> blueprint = parts.length < 3 ? Optional.empty()
					: Optional.ofNullable(net.minecraft.resources.Identifier.tryParse(parts[2]))
					.flatMap(id -> com.ajustor.minecities.build.Blueprints.get(level.getServer(), id));
			if (blueprint.isEmpty() || town.tier().ordinal() < blueprint.get().tier().ordinal()) {
				return;
			}
			com.ajustor.minecities.build.BuildStyle style = com.ajustor.minecities.build.BuildStyle
					.fromSerializedName(parts[1]).orElse(com.ajustor.minecities.build.BuildStyle.PLAINS);
			// Un seul plan (MC-23) : celui qu'on a déjà prend ce schéma ; sinon on en reçoit un.
			Optional<net.minecraft.world.item.ItemStack> owned = PlanNetwork.held(player).or(() -> player.getInventory()
					.getNonEquipmentItems().stream().filter(s -> s.is(com.ajustor.minecities.registry.McItems.PLAN))
					.findFirst());
			if (owned.isPresent()) {
				com.ajustor.minecities.build.PlanItem.choose(owned.get(), blueprint.get(), style);
			} else {
				net.minecraft.world.item.ItemStack stack = com.ajustor.minecities.build.PlanItem.of(blueprint.get(), style);
				if (!player.getInventory().add(stack)) {
					EntityCompat.drop(player, stack);
				}
			}
			player.sendSystemMessage(Component.translatable("message.minecities.plan.given",
					blueprint.get().displayName()).withStyle(ChatFormatting.GOLD));
			return;
		}
		switch (payload.action()) {
			case "granary" -> {
				if (TownService.mayOpenGranary(player, town)
						&& level.getBlockEntity(payload.hall()) instanceof TownHallBlockEntity hall) {
					player.openMenu(hall);
				}
				return;
			}
			case "join" -> TownService.requestJoin(level.getServer(), town, player);
			case "election.call", "election.sign", "election.run", "vote" -> {
				Optional<Elections.Refusal> refusal = switch (payload.action()) {
					case "election.call" -> Elections.call(level, town, player);
					case "election.sign" -> Elections.sign(level, town, player);
					case "election.run" -> Elections.run(level, town, player);
					default -> Elections.cast(level, town, player, payload.request());
				};
				refusal.ifPresent(r -> player.sendSystemMessage(Component.translatable(r.translationKey(),
						Math.max(1, Elections.untilAllowed(town, level.getGameTime()) / 1200)).withStyle(ChatFormatting.RED)));
			}
			case "leave" -> TownService.leave(level.getServer(), town, player);
			case "resident.accept", "resident.refuse" -> {
				if (!town.canManage(player.getUUID())) {
					return;
				}
				TownService.answerJoin(level.getServer(), town, payload.request(),
						payload.action().equals("resident.accept"));
			}
			case "resident.remove" -> {
				if (!town.canManage(player.getUUID())) {
					return;
				}
				TownService.expel(level.getServer(), town, payload.request());
			}
			case "locate" -> {
				// Le citoyen luit quelques secondes, et l'on dit où il est.
				Optional<Resident> resident = town.resident(payload.request());
				if (resident.isEmpty()) {
					return;
				}
				if (level.getEntity(payload.request()) instanceof CitizenEntity citizen) {
					citizen.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
					player.sendSystemMessage(Component.translatable("message.minecities.locate", citizen.getName(),
							citizen.blockPosition().toShortString()));
				} else {
					player.sendSystemMessage(Component.translatable("message.minecities.locate.away",
							resident.get().name()));
				}
				return;
			}
			case "posting" -> {
				// Le maire (ou un adjoint) renvoie ce citoyen à son poste.
				if (!town.canManage(player.getUUID())) {
					return;
				}
				com.ajustor.minecities.town.Postings.Outcome outcome = com.ajustor.minecities.town.Postings.send(level, town,
						payload.request());
				player.sendSystemMessage(outcome.message().copy().withStyle(outcome.sent() ? ChatFormatting.GREEN
						: ChatFormatting.RED));
				return;
			}
			case "decree.rationing", "decree.overtime", "decree.holiday" -> {
				if (!town.canManage(player.getUUID())) {
					return;
				}
				com.ajustor.minecities.town.Decree.fromSerializedName(payload.action().substring("decree.".length()))
						.ifPresent(decree -> TownService.decree(level, town, decree));
			}
			case "festival" -> {
				if (!town.canManage(player.getUUID())) {
					return;
				}
				Festivals.offer(level, town).ifPresent(refusal -> player.sendSystemMessage(Component.translatable(
						"message.minecities.festival.refused." + refusal.name().toLowerCase(), Festivals.cost())
						.withStyle(ChatFormatting.RED)));
			}
			case "accept", "refuse" -> {
				if (!town.canManage(player.getUUID())) {
					return;
				}
				town.marriageRequest(payload.request()).ifPresent(request -> {
					if (payload.action().equals("accept")) {
						Families.accept(level, town, request);
					} else {
						Families.refuse(level, town, request);
					}
				});
			}
			default -> {
				return;
			}
		}
		open(level, town, player);
	}
}
