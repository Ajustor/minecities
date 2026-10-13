package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.citizen.Disease;
import com.ajustor.minecities.citizen.Happiness;
import com.ajustor.minecities.citizen.Health;
import com.ajustor.minecities.compat.CitizenInfo;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Resident;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.citizens;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Les maladies (MC-12) : une ville nourrie et au sec ne tombe pas malade ; la chair putréfiée donne
 * la dysenterie, qui couve avant de se déclarer ; affamé et sans autre chose au grenier, on en mange ;
 * le manteau se donne et se porte ; la fièvre cloue au lit en plein jour, se voit (icône, humeur,
 * infobulle) ; le médecin réclame son remède, puis soigne ; on guérit et l'on est protégé un temps.
 *
 * <p>{@code ./gradlew runGameTest -PminecitiesGametest=health}
 */
public class HealthScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos HOUSE = new BlockPos(-6, Y, -16);
	private static final BlockPos INFIRMARY = new BlockPos(8, Y, -16);

	@Override
	public String name() {
		return "health";
	}

	@Override
	public void play(Stage stage) {
		Families.setMinimum(0);
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.command("weather clear");
		stage.waitTicks(20);
		stage.run(HealthScenes::build);

		// La ville : quatre citoyens, une maison de quatre lits, une infirmerie et son médecin.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = mayor(server);
			TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
			Town town = town(server);
			for (int i = 0; i < 4; i++) {
				TownService.immigrate(level, town);
			}
			for (BlockPos origin : new BlockPos[]{HOUSE, INFIRMARY}) {
				RaidScenes.plaque(level, origin).ifPresent(p -> TownService.declareHouse(level, p, player));
			}
			return town.workers(Job.DOCTOR) == 1 ? null : "pas de médecin : " + town.houses();
		}));

		// Nourrie et au sec, la ville ne tombe pas malade.
		stage.waitTicks(1200);
		check(stage.compute(server -> citizens(server).stream().allMatch(c -> c.health().disease().isEmpty()) ? null
				: "des malades sans raison : " + citizens(server).stream().map(c -> c.getName().getString() + " "
						+ c.health().disease()).toList()));

		// La chair putréfiée : la dysenterie, qui couve d'abord. Affamé, sans autre chose, on en mange.
		check(stage.compute(server -> {
			CitizenEntity eater = civilians(server).get(0);
			for (int i = 0; i < 30 && eater.health().disease().isEmpty(); i++) {
				eater.eat(new ItemStack(Items.ROTTEN_FLESH));
			}
			List<String> wrong = new ArrayList<>();
			if (eater.health().disease().filter(Disease.DYSENTERY::equals).isEmpty() || eater.illness().isPresent()) {
				wrong.add("dysenterie : " + eater.health().disease() + ", symptômes " + eater.illness());
			}
			SimpleContainer famine = new SimpleContainer(new ItemStack(Items.ROTTEN_FLESH, 3));
			SimpleContainer bread = new SimpleContainer(new ItemStack(Items.ROTTEN_FLESH, 3), new ItemStack(Items.BREAD));
			if (!TownService.takeTainted(famine).is(Items.ROTTEN_FLESH) || !TownService.takeTainted(bread).isEmpty()
					|| TownService.hasFood(famine) || !TownService.hasFood(famine, true)) {
				wrong.add("le gâté n'est pris qu'en dernier recours");
			}
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// Le manteau : le maire en donne un ; le citoyen le porte.
		check(stage.compute(server -> {
			ServerPlayer player = mayor(server);
			CitizenEntity dressed = civilians(server).get(1);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(McItems.COAT));
			// Le citoyen reçoit ce que le joueur lui tend (même un joueur spectateur, dans les scènes filmées).
			dressed.interact(player, InteractionHand.MAIN_HAND, dressed.position());
			return dressed.wearsCoat() && dressed.getItemBySlot(EquipmentSlot.CHEST).is(McItems.COAT)
					&& player.getMainHandItem().isEmpty() ? null : "le manteau n'est pas porté";
		}));
		// En image : le citoyen au manteau, de face.
		double[] at = stage.compute(server -> {
			CitizenEntity dressed = civilians(server).get(1);
			dressed.setNoAi(true);
			dressed.snapTo(6.5, Y, 6.5, 180, 0);
			dressed.setYHeadRot(180);
			return new double[]{dressed.getX(), dressed.getZ()};
		});
		stage.client(mc -> hideGui(mc, true));
		stage.look(at[0], Y + 2.0, at[1] - 2.8, at[0], Y + 1.0, at[1]);
		stage.waitTicks(20);
		stage.shot("health_0_coat");
		stage.run(server -> civilians(server).get(1).setNoAi(false));

		// La fièvre : il se met au lit en plein jour ; ça se voit et ça pèse sur son humeur.
		UUID feverish = stage.compute(server -> {
			CitizenEntity c = civilians(server).stream()
					.filter(x -> town(server).resident(x.getUUID()).flatMap(Resident::bed).isPresent())
					.filter(x -> x.health().disease().isEmpty()).findFirst().orElseThrow();
			long now = server.overworld().getGameTime();
			// Elle couve depuis assez longtemps : elle se déclare tout de suite.
			c.health().contract(Disease.FEVER, now - Disease.FEVER.incubation());
			return c.getUUID();
		});
		waitOrExplain(stage, server -> citizen(server, feverish).isSleeping(), 1200, "le fiévreux n'est pas au lit",
				server -> "à " + citizen(server, feverish).blockPosition().toShortString() + ", lit "
						+ citizen(server, feverish).bed() + " " + citizen(server, feverish).bed().map(b -> server.overworld()
						.getBlockState(b).toString()).orElse("-") + ", alité " + citizen(server, feverish).bedridden()
						+ ", maladie " + citizen(server, feverish).health().disease() + " "
						+ citizen(server, feverish).illness());
		waitOrExplain(stage, server -> citizen(server, feverish).moodDetail().factors()
				.contains(new Happiness.Factor("sick_fever", Disease.FEVER.mood())), 300, "la fièvre ne pèse pas");
		// En image : au lit, avec son icône.
		double[] bed = stage.compute(server -> {
			BlockPos b = citizen(server, feverish).bed().orElseThrow();
			return new double[]{b.getX() + 0.5, b.getY(), b.getZ() + 0.5};
		});
		stage.client(mc -> hideGui(mc, false));
		// De l'intérieur de la maison, au pied du lit.
		stage.look(bed[0] + 1.2, bed[1] + 1.7, bed[2] - 2.6, bed[0], bed[1] + 0.4, bed[2]);
		stage.client(mc -> mc.gui.hud.getChat().clearMessages(false));
		stage.waitTicks(20);
		stage.shot("health_1_fever");
		check(stage.compute(server -> {
			CitizenEntity c = citizen(server, feverish);
			CitizenInfo info = CitizenInfo.of(c);
			return (c.trouble() & CitizenEntity.Trouble.SICK) != 0 && info.illness().equals(Disease.FEVER.translationKey())
					? null : "on ne voit pas la fièvre : " + c.trouble() + " " + info;
		}));

		// Le médecin : sans remède, il le réclame ; avec, il soigne.
		waitOrExplain(stage, server -> doctor(server).lastNeed().isPresent(), 2400, "le médecin ne réclame rien");
		stage.run(server -> {
			if (server.overworld().getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
				hall.setItem(10, new ItemStack(Items.GLISTERING_MELON_SLICE, 2));
			}
		});
		waitOrExplain(stage, server -> citizen(server, feverish).health().treated(), 2400,
				"le médecin n'a pas soigné la fièvre", server -> "médecin à "
						+ doctor(server).blockPosition().toShortString() + ", besoin " + doctor(server).lastNeed());

		// Sauvegardée, la santé se relit ; il guérit à l'échéance, et en est protégé.
		check(stage.compute(server -> {
			CitizenEntity c = citizen(server, feverish);
			Health read = Health.CODEC.parse(NbtOps.INSTANCE, Health.CODEC.encodeStart(NbtOps.INSTANCE, c.health())
					.getOrThrow()).getOrThrow();
			if (!read.disease().equals(Optional.of(Disease.FEVER)) || !read.treated() || read.until() != c.health().until()) {
				return "relue : " + read.disease() + " " + read.treated();
			}
			long until = c.health().until();
			c.health().tick(until);
			return c.health().disease().isEmpty() && c.health().immune(Disease.FEVER, until + 1)
					&& !c.infect(Disease.FEVER) ? null : "pas guéri : " + c.health().disease();
		}));
		stage.waitTicks(200);
		check(stage.compute(server -> citizen(server, feverish).isSleeping() ? "guéri, il reste au lit" : null));
		stage.client(mc -> hideGui(mc, false));
	}

	private static CitizenEntity citizen(MinecraftServer server, UUID id) {
		return (CitizenEntity) server.overworld().getEntity(id);
	}

	private static CitizenEntity doctor(MinecraftServer server) {
		return citizens(server).stream().filter(c -> c.job().filter(j -> j.job() == Job.DOCTOR).isPresent())
				.findFirst().orElseThrow();
	}

	/** Les citoyens qui ne sont pas médecins, dans l'ordre de leurs UUID. */
	private static List<CitizenEntity> civilians(MinecraftServer server) {
		return citizens(server).stream().filter(c -> c.job().filter(j -> j.job() == Job.DOCTOR).isEmpty())
				.sorted(java.util.Comparator.comparing(CitizenEntity::getUUID)).toList();
	}

	private static ServerPlayer mayor(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -30; x <= 30; x++) {
			for (int z = -30; z <= 20; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				for (int y = Y; y < Y + 14; y++) {
					if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
						level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		RaidScenes.paste(level, "house_large", HOUSE);
		RaidScenes.paste(level, "infirmary", INFIRMARY);
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		if (level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
			hall.setItem(0, new ItemStack(Items.BREAD, 32));
			hall.setItem(1, new ItemStack(Items.COOKED_BEEF, 16));
			hall.setItem(2, new ItemStack(Items.BAKED_POTATO, 16));
		}
	}
}
