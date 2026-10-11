package com.ajustor.minecities.network;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.citizen.Hunger;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.MarriageRequest;
import com.ajustor.minecities.town.Resident;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.TownTier;
import com.ajustor.minecities.town.Towns;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
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
			rows.add(new TownSnapshot.CitizenRow(r.name(), r.job().map(j -> j.job().translationKey()).orElse(""),
					hunger, r.hasHome(), spouse, r.isChild(),
					citizen == null ? Optional.empty() : citizen.lastNeed()));
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
		return new TownSnapshot(town.hall(), town.name(), town.tier().translationKey(), town.population(),
				town.citizens(), town.villagers(), town.capacity(), town.homes().size(), town.workstations(),
				(int) employed, meals, next == null ? "" : next.translationKey(),
				next == null ? 0 : next.minCitizens(), next == null ? 0 : next.minHouses(),
				town.canManage(player.getUUID()), rows, requests);
	}

	private static void act(ServerPlayer player, TownActionPayload payload) {
		ServerLevel level = player.level();
		if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(payload.hall())) > REACH * REACH) {
			return;
		}
		Optional<Town> found = Towns.get(level.getServer()).byHall(level.dimension(), payload.hall());
		if (found.isEmpty()) {
			return;
		}
		Town town = found.get();
		switch (payload.action()) {
			case "granary" -> {
				if (level.getBlockEntity(payload.hall()) instanceof TownHallBlockEntity hall) {
					player.openMenu(hall);
				}
				return;
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
