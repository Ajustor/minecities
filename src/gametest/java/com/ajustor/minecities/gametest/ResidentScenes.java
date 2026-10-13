package com.ajustor.minecities.gametest;

import com.ajustor.minecities.client.screen.TownHallScreen;
import com.ajustor.minecities.network.TownNetwork;
import com.ajustor.minecities.network.TownSnapshot;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.House;
import com.ajustor.minecities.town.Membership;
import com.ajustor.minecities.town.Membership.Permission;
import com.ajustor.minecities.town.Membership.Rank;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;

/**
 * Les habitants joueurs et leurs rangs (MC-16) : un visiteur ne déclare pas de maison, n'ouvre pas le
 * grenier et ne casse pas les bâtiments d'une ville nouvelle ; il demande à s'installer (par la
 * commande, ou en dormant dans un lit de la ville), le maire l'accepte, et l'habitant a les droits de
 * son rang. On n'est habitant que d'une ville. Le maire règle les droits de chaque rang ; une ville
 * d'avant les rangs garde les droits d'avant. Tout cela se sauvegarde, et se montre dans l'onglet
 * Habitants.
 *
 * <p>{@code ./gradlew runGameTest -PminecitiesGametest=residents}
 */
public class ResidentScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos PLAQUE = new BlockPos(5, Y + 1, 4);
	/** Un mur de la maison déclarée. */
	private static final BlockPos WALL = new BlockPos(3, Y + 1, 7);
	/** L'hôtel de ville d'une autre ville, loin. */
	private static final BlockPos ELSEWHERE = new BlockPos(2000, Y, 0);
	private static final GameProfile VISITOR = new GameProfile(UUID.fromString("00000000-0000-4000-8000-000000000016"),
			"Visiteur");
	private static final GameProfile NEWCOMER = new GameProfile(UUID.fromString("00000000-0000-4000-8000-000000000017"),
			"Arrivant");

	@Override
	public String name() {
		return "residents";
	}

	@Override
	public void play(Stage stage) {
		Families.setMinimum(0);
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.command("weather clear");
		stage.waitTicks(20);
		stage.run(CityScenes::build);

		// Une ville nouvelle et sa maison, déclarée par le maire.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer mayor = mayor(server);
			TownService.found(level, HALL, mayor, new ItemStack(McItems.TOWN_HALL));
			Town town = town(server);
			if (town == null) {
				return "pas de ville";
			}
			boolean declared = TownService.declareHouse(level, PLAQUE, mayor);
			return declared && town.house(PLAQUE).isPresent() && town.rank(mayor.getUUID()) == Rank.MAYOR ? null
					: "le maire n'a pas déclaré sa maison : " + town.houses();
		}));

		// Le visiteur : ni maison, ni grenier, ni bâtiment cassé.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			ServerPlayer visitor = FakePlayer.get(level, VISITOR);
			List<String> wrong = new ArrayList<>();
			if (town.rank(visitor.getUUID()) != Rank.VISITOR) {
				wrong.add("rang " + town.rank(visitor.getUUID()));
			}
			if (TownService.declareHouse(level, PLAQUE, visitor)) {
				wrong.add("il a déclaré une maison");
			}
			if (TownService.mayOpenGranary(visitor, town)) {
				wrong.add("il ouvre le grenier");
			}
			if (breaks(level, visitor, WALL)) {
				wrong.add("il casse un mur de la maison");
			}
			if (!breaks(level, visitor, WALL.offset(0, 0, 6))) {
				wrong.add("il ne casse pas un bloc hors des bâtiments");
			}
			if (!breaks(level, mayor(server), WALL) || !TownService.mayOpenGranary(mayor(server), town)) {
				wrong.add("le maire est empêché");
			}
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// Il demande à s'installer ; le maire accepte ; l'habitant déclare et ouvre le grenier, sans casser.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			ServerPlayer visitor = FakePlayer.get(level, VISITOR);
			List<String> wrong = new ArrayList<>();
			if (!TownService.requestJoin(server, town, visitor) || !town.membership().hasRequested(visitor.getUUID())) {
				wrong.add("pas de demande");
			}
			if (TownService.requestJoin(server, town, visitor)) {
				wrong.add("une deuxième demande");
			}
			if (town.rank(visitor.getUUID()) != Rank.VISITOR) {
				wrong.add("habitant avant d'être accepté");
			}
			if (run(server, mayor(server), "ville resident accept Visiteur") == 0) {
				wrong.add("le maire n'a pas pu accepter");
			}
			if (town.rank(visitor.getUUID()) != Rank.RESIDENT || town.membership().hasRequested(visitor.getUUID())) {
				wrong.add("accepté, il est " + town.rank(visitor.getUUID()));
			}
			if (!TownService.declareHouse(level, PLAQUE, visitor) || !TownService.mayOpenGranary(visitor, town)) {
				wrong.add("l'habitant ne déclare pas ou n'ouvre pas le grenier");
			}
			if (breaks(level, visitor, WALL)) {
				wrong.add("l'habitant casse un mur");
			}
			if (TownService.requestJoin(server, town, visitor)) {
				wrong.add("il redemande la ville où il habite");
			}
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// Une seule ville à la fois : l'habitant ne demande pas une autre ville, ni le maire.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town other = Town.found("Ailleurs", level.dimension(), ELSEWHERE, UUID.randomUUID(), "autre", 0L);
			Towns.get(server).add(other);
			try {
				List<String> wrong = new ArrayList<>();
				if (TownService.requestJoin(server, other, FakePlayer.get(level, VISITOR))) {
					wrong.add("l'habitant demande une autre ville");
				}
				if (TownService.requestJoin(server, other, mayor(server))) {
					wrong.add("le maire demande une autre ville");
				}
				// Demandé ici, mais installé ailleurs entre-temps : l'acceptation ne le prend pas.
				ServerPlayer newcomer = FakePlayer.get(level, NEWCOMER);
				TownService.requestJoin(server, town(server), newcomer);
				TownService.requestJoin(server, other, newcomer);
				TownService.answerJoin(server, other, newcomer.getUUID(), true);
				TownService.answerJoin(server, town(server), newcomer.getUUID(), true);
				if (town(server).isMember(newcomer.getUUID()) || !other.isMember(newcomer.getUUID())) {
					wrong.add("il est des deux villes, ou d'aucune");
				}
				return wrong.isEmpty() ? null : String.join(" ; ", wrong);
			} finally {
				Towns.get(server).remove(other);
			}
		}));

		// Le maire règle les droits : le visiteur peut déclarer, puis l'habitant peut casser.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			ServerPlayer mayor = mayor(server);
			ServerPlayer resident = FakePlayer.get(level, VISITOR);
			List<String> wrong = new ArrayList<>();
			if (run(server, mayor, "ville permission visitor declare true") == 0
					|| !town.may(UUID.randomUUID(), Permission.DECLARE)) {
				wrong.add("le visiteur ne déclare toujours pas");
			}
			if (run(server, mayor, "ville permission resident break true") == 0 || !breaks(level, resident, WALL)) {
				wrong.add("l'habitant ne casse toujours pas");
			}
			if (run(server, resident, "ville permission visitor granary true") != 0
					|| town.may(UUID.randomUUID(), Permission.GRANARY)) {
				wrong.add("un habitant règle les droits");
			}
			run(server, mayor, "ville permission visitor declare false");
			run(server, mayor, "ville permission resident break false");
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// Sauvegardé et relu ; une ville d'avant les rangs garde les droits d'avant.
		check(stage.compute(server -> {
			Town town = town(server);
			List<String> wrong = new ArrayList<>();
			Tag saved = Town.CODEC.encodeStart(NbtOps.INSTANCE, town).getOrThrow();
			Town read = Town.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
			if (read.rank(VISITOR.id()) != Rank.RESIDENT
					|| !read.membership().permissions(Rank.RESIDENT).equals(EnumSet.of(Permission.DECLARE, Permission.GRANARY))
					|| !read.membership().permissions(Rank.VISITOR).isEmpty()) {
				wrong.add("relue : " + read.membership().residents() + " " + read.membership().permissions(Rank.RESIDENT)
						+ " " + read.membership().permissions(Rank.VISITOR));
			}
			CompoundTag old = ((CompoundTag) saved).copy();
			old.remove("player_residents");
			old.remove("resident_permissions");
			old.remove("visitor_permissions");
			Town before = Town.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
			if (!before.may(UUID.randomUUID(), Permission.DECLARE) || !before.may(UUID.randomUUID(), Permission.GRANARY)
					|| !before.may(UUID.randomUUID(), Permission.BREAK)) {
				wrong.add("une ville d'avant : " + before.membership().permissions(Rank.VISITOR));
			}
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// Il part ; il revient en dormant dans un lit de la ville ; le maire le renvoie ; le maire ne part pas.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			ServerPlayer resident = FakePlayer.get(level, VISITOR);
			List<String> wrong = new ArrayList<>();
			if (run(server, resident, "ville leave") == 0 || town.isMember(resident.getUUID())) {
				wrong.add("il n'est pas parti");
			}
			House house = town.house(PLAQUE).orElseThrow();
			EntitySleepEvents.START_SLEEPING.invoker().onStartSleeping(resident, house.beds().getFirst());
			if (!town.membership().hasRequested(resident.getUUID())) {
				wrong.add("dormir dans un lit de la ville n'a rien demandé");
			}
			TownService.answerJoin(server, town, resident.getUUID(), true);
			if (run(server, mayor(server), "ville resident remove Visiteur") == 0 || town.isMember(resident.getUUID())) {
				wrong.add("le maire ne l'a pas renvoyé");
			}
			if (TownService.leave(server, town, mayor(server)) || !town.isMember(mayor(server).getUUID())) {
				wrong.add("le maire est parti");
			}
			// Pour l'onglet : un habitant et une demande.
			TownService.requestJoin(server, town, resident);
			TownService.answerJoin(server, town, resident.getUUID(), true);
			TownService.requestJoin(server, town, FakePlayer.get(level, NEWCOMER));
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// L'onglet Habitants : ce que le serveur envoie, le client le relit tel quel.
		TownSnapshot snapshot = stage.compute(server -> TownNetwork.snapshot(server.overworld(), town(server),
				mayor(server)));
		check(stage.compute(server -> {
			TownSnapshot.Members members = snapshot.members();
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
			TownSnapshot.CODEC.encode(buffer, snapshot);
			TownSnapshot read = TownSnapshot.CODEC.decode(buffer);
			if (!read.members().equals(members)) {
				return "relu : " + read.members() + " au lieu de " + members;
			}
			return members.rank().equals("mayor") && members.players().size() == 2 && members.requests().size() == 1
					&& members.resident().equals(List.of(true, true, false))
					&& members.visitor().equals(List.of(false, false, false)) ? null : "l'onglet : " + members;
		}));
		stage.client(mc -> {
			hideGui(mc, false);
			mc.gui.setScreen(new TownHallScreen(snapshot));
		});
		stage.waitTicks(3);
		stage.client(mc -> ((TownHallScreen) mc.gui.screen()).select(TownHallScreen.Tab.RESIDENTS));
		stage.waitTicks(3);
		stage.shot("residents_0_tab");
		stage.client(mc -> {
			mc.gui.setScreen(null);
			hideGui(mc, true);
		});
	}

	/** Ce joueur peut-il casser ce bloc ? */
	private static boolean breaks(ServerLevel level, ServerPlayer player, BlockPos pos) {
		return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, level.getBlockState(pos),
				level.getBlockEntity(pos));
	}

	private static ServerPlayer mayor(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElse(null);
	}

	private static int run(MinecraftServer server, ServerPlayer player, String command) {
		try {
			return server.getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			return 0;
		}
	}
}
