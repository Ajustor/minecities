package com.ajustor.minecities.gametest;

import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Resident;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import com.ajustor.minecities.registry.McItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.citizens;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Le poste de bâtisseur isolé et les monstres qui chassent les citoyens : une table d'artisan posée
 * par le maire en plein air, hors de tout bâtiment, donne tout de suite un bâtisseur, qui la garde
 * comme atelier (de niveau 1) et la perd quand elle est cassée ; un zombie (un husk, qui ne brûle pas
 * au soleil) et un vindicateur prennent un citoyen pour cible, un piglin zombifié le laisse en paix.
 *
 * <p>{@code ./gradlew runGameTest -PminecitiesGametest=yard}
 */
public class YardScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	/** La table d'artisan, posée au sol de l'esplanade, loin de la maison. */
	private static final BlockPos YARD = new BlockPos(-10, Y, -10);

	@Override
	public String name() {
		return "yard";
	}

	@Override
	public void play(Stage stage) {
		Families.setMinimum(0);
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.command("difficulty normal");
		stage.command("weather clear");
		stage.waitTicks(20);
		stage.run(CityScenes::build);

		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer mayor = server.getPlayerList().getPlayers().getFirst();
			TownService.found(level, HALL, mayor, new ItemStack(McItems.TOWN_HALL));
			Town town = town(server);
			TownService.immigrate(level, town);
			TownService.immigrate(level, town);
			return town.workers(Job.BUILDER) == 0 ? null : "un bâtisseur avant tout poste";
		}));

		// Le maire pose la table d'artisan en plein air, comme à la main : un bâtisseur s'y fait embaucher.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer mayor = server.getPlayerList().getPlayers().getFirst();
			ItemStack crafter = new ItemStack(Items.CRAFTER);
			mayor.setItemInHand(InteractionHand.MAIN_HAND, crafter);
			BlockPos floor = YARD.below();
			BlockPlaceContext context = new BlockPlaceContext(mayor, InteractionHand.MAIN_HAND, crafter,
					new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
			((BlockItem) Items.CRAFTER).place(context);
			if (!level.getBlockState(YARD).is(Blocks.CRAFTER)) {
				return "la table d'artisan n'est pas posée : " + level.getBlockState(YARD);
			}
			return town(server).isYard(YARD) ? null : "la table d'artisan n'est pas un poste isolé";
		}));
		waitOrExplain(stage, server -> town(server).workers(Job.BUILDER) == 1, 200, "pas de bâtisseur au poste isolé");
		check(stage.compute(server -> {
			Town town = town(server);
			Optional<Resident.Employment> job = town.residents().values().stream().flatMap(r -> r.job().stream())
					.filter(j -> j.job() == Job.BUILDER).findFirst();
			if (job.isEmpty() || !job.get().station().equals(YARD) || !job.get().workshop().equals(YARD)) {
				return "emploi du bâtisseur : " + job;
			}
			return town.buildingLevel(YARD) == 1 ? null : "niveau du poste isolé : " + town.buildingLevel(YARD);
		}));
		// Il reste au travail : son poste tient, il ne le quitte pas.
		stage.waitTicks(200);
		check(stage.compute(server -> town(server).workers(Job.BUILDER) == 1 ? null : "le bâtisseur a perdu son poste"));

		// La table cassée : le poste est oublié, le bâtisseur sans emploi.
		stage.run(server -> server.overworld().setBlockAndUpdate(YARD, Blocks.AIR.defaultBlockState()));
		waitOrExplain(stage, server -> town(server).workers(Job.BUILDER) == 0 && !town(server).isYard(YARD), 1200,
				"le poste cassé garde son bâtisseur");

		// Les monstres : un husk et un vindicateur prennent un citoyen pour cible, pas un piglin zombifié.
		List<UUID> hunters = new ArrayList<>();
		UUID piglin = stage.compute(server -> {
			ServerLevel level = server.overworld();
			CitizenEntity prey = citizens(server).getFirst();
			BlockPos near = prey.blockPosition();
			hunters.add(spawn(level, EntityTypes.HUSK, near.offset(6, 0, 0)));
			hunters.add(spawn(level, EntityTypes.VINDICATOR, near.offset(-6, 0, 0)));
			return spawn(level, EntityTypes.ZOMBIFIED_PIGLIN, near.offset(0, 0, 6));
		});
		waitOrExplain(stage, server -> hunters.stream().allMatch(id -> server.overworld().getEntity(id) instanceof Mob m
				&& m.getTarget() instanceof CitizenEntity), 200, "les monstres ne chassent pas les citoyens",
				server -> hunters.stream().map(id -> server.overworld().getEntity(id) instanceof Mob m
						? m.getType().toShortString() + "→" + m.getTarget() : id + " disparu").toList().toString());
		check(stage.compute(server -> server.overworld().getEntity(piglin) instanceof Mob m && m.getTarget() != null
				? "le piglin zombifié vise " + m.getTarget() : null));
		stage.run(server -> {
			hunters.forEach(id -> discard(server, id));
			discard(server, piglin);
		});
	}

	private static UUID spawn(ServerLevel level, EntityType<? extends Mob> type, BlockPos pos) {
		Mob mob = type.create(level, EntitySpawnReason.COMMAND);
		mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		return mob.getUUID();
	}

	private static void discard(MinecraftServer server, UUID id) {
		if (server.overworld().getEntity(id) != null) {
			server.overworld().getEntity(id).discard();
		}
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElse(null);
	}
}
