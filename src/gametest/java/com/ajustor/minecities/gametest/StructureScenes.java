package com.ajustor.minecities.gametest;

import com.ajustor.minecities.build.Blueprint;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildPlan;
import com.ajustor.minecities.build.BuildStyle;
import com.ajustor.minecities.build.Builds;
import com.ajustor.minecities.registry.McBlocks;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.command;

/**
 * Les bâtiments des joueurs (brief 09) : une maison bâtie à la main, enregistrée avec un bloc de
 * structure (mode Sauvegarde), devient un schéma de la ville. Il a les mêmes blocs et coûte les mêmes
 * matériaux que la maison d'origine ; c'est une maison (il y a un lit), il porte le nom du fichier,
 * et le maire peut le commander.
 *
 * <p>{@code ./gradlew runGameTest -PminecitiesGametest=structures}
 */
public class StructureScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	/** La maison bâtie à la main, que le joueur enregistre. */
	private static final BlockPos HOUSE = new BlockPos(8, Y, 8);
	/** Là où le maire la commande. */
	private static final BlockPos SITE = new BlockPos(-20, Y, 8);
	private static final Identifier SAVED = Identifier.fromNamespaceAndPath("minecraft", "ma_maison");
	private static final Identifier ORIGINAL = Identifier.fromNamespaceAndPath("minecities", "house_2");

	@Override
	public String name() {
		return "structures";
	}

	@Override
	public void play(Stage stage) {
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.waitTicks(20);

		// La maison bâtie, puis enregistrée comme le fait un bloc de structure en mode Sauvegarde.
		stage.run(server -> {
			ServerLevel level = server.overworld();
			RaidScenes.paste(level, ORIGINAL.getPath(), HOUSE);
			Blueprint original = Blueprints.get(server, ORIGINAL).orElseThrow();
			StructureTemplateManager manager = server.getStructureManager();
			StructureTemplate template = manager.getOrCreate(SAVED);
			template.fillFromWorld(level, HOUSE, new Vec3i(original.width(), original.height(), original.depth()),
					false, List.of(Blocks.STRUCTURE_VOID));
			template.setAuthor("scène");
			manager.save(SAVED);
		});

		// La ville la connaît (au plus tard 5 secondes après) : mêmes dimensions, mêmes matériaux.
		stage.waitFor(server -> Blueprints.get(server, SAVED).isPresent(), 200);
		check(stage.compute(server -> {
			Blueprint saved = Blueprints.get(server, SAVED).orElseThrow();
			Blueprint original = Blueprints.get(server, ORIGINAL).orElseThrow();
			if (saved.width() != original.width() || saved.height() != original.height()
					|| saved.depth() != original.depth()) {
				return "dimensions " + saved.width() + "x" + saved.height() + "x" + saved.depth();
			}
			if (!saved.kind().equals("house")) {
				return "ce n'est pas une maison : " + saved.kind();
			}
			if (!saved.displayName().getString().equals("ma_maison")) {
				return "son nom : " + saved.displayName().getString();
			}
			Map<Item, Integer> mine = BuildPlan.materials(BuildPlan.of(saved, BuildStyle.PLAINS, SITE, Rotation.NONE));
			Map<Item, Integer> theirs = BuildPlan.materials(BuildPlan.of(original, BuildStyle.PLAINS, SITE,
					Rotation.NONE));
			if (!mine.equals(theirs)) {
				return "matériaux : " + mine + " au lieu de " + theirs;
			}
			boolean plaque = BuildPlan.of(saved, BuildStyle.PLAINS, SITE, Rotation.NONE).stream()
					.anyMatch(p -> p.state().is(McBlocks.HOUSE_PLAQUE));
			return plaque ? null : "pas de plaque de maison dans le schéma";
		}));

		// Le maire la commande, comme un schéma du mod.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
			TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
			Town town = Towns.get(server).byHall(level.dimension(), HALL).orElseThrow();
			Optional<String> refusal = Builds.order(level, town, Blueprints.get(server, SAVED).orElseThrow(),
					BuildStyle.PLAINS, SITE, Rotation.NONE).map(c -> c.getString());
			if (refusal.isPresent()) {
				return "commande refusée : " + refusal.get();
			}
			return town.activeConstruction().filter(c -> c.blueprint().equals(SAVED)).isPresent() ? null
					: "pas de chantier : " + town.activeConstruction();
		}));
		check(stage.compute(server -> Blueprints.all(server).containsKey(SAVED) ? null
				: "absent de la liste des schémas"));
	}
}
