package com.ajustor.minecities.gametest;

import com.ajustor.minecities.build.BuildStyle;
import com.ajustor.minecities.registry.McBlocks;
import com.ajustor.minecities.town.HouseScanner;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static com.ajustor.minecities.gametest.CityScenes.check;

/**
 * Les signatures des bâtiments (v1.16) : chaque bâtiment à niveaux, à chacun de ses niveaux et dans
 * chaque style (le colombage du style neige est un rondin écorcé, poste du bûcheron), posé d'un coup puis relevé depuis sa plaque comme au clic. Il doit rester une pièce close, et offrir autant de
 * postes que son niveau, tous du même métier : un tonneau, une botte de foin ou un rondin du décor ne
 * doit pas créer d'emploi, et un tonneau de la pêcherie doit être assez près de l'eau.
 */
public class SignatureScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos ORIGIN = new BlockPos(0, Y, 0);

	@Override
	public String name() {
		return "signatures";
	}

	@Override
	public void play(Stage stage) {
		stage.command("gamerule spawn_mobs false");
		stage.waitTicks(20);
		List<String> wrong = stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = Town.found("Signatures", level.dimension(), new BlockPos(-40, Y, -40), UUID.randomUUID(),
					"maire", 0L);
			List<String> failures = new ArrayList<>();
			for (BuildStyle style : BuildStyle.values()) {
				for (String family : GalleryScenes.FAMILIES) {
					for (int tier = 1; tier <= 4; tier++) {
						String name = family + "_" + tier;
						clear(level);
						RaidScenes.paste(level, name, ORIGIN, style);
						String failure = survey(level, town, name + " (" + style.serializedName() + ")", family, tier);
						if (failure != null) {
							failures.add(failure);
						}
					}
				}
			}
			return failures;
		});
		check(wrong.isEmpty() ? null : String.join(" ; ", wrong));
	}

	private static String survey(ServerLevel level, Town town, String name, String family, int tier) {
		BlockPos plaque = null;
		for (BlockPos pos : BlockPos.betweenClosed(ORIGIN.offset(-2, 0, -2), ORIGIN.offset(30, 6, 20))) {
			if (level.getBlockState(pos).is(McBlocks.HOUSE_PLAQUE)) {
				plaque = pos.immutable();
				break;
			}
		}
		if (plaque == null) {
			return name + " : pas de plaque";
		}
		HouseScanner.Result result = TownService.resurvey(level, town, plaque);
		if (!result.valid()) {
			return name + " : relevé " + result.status();
		}
		Map<String, Integer> jobs = new TreeMap<>();
		for (HouseScanner.Pos p : result.workstations()) {
			BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
			Job.ofWorkstation(level, pos, level.getBlockState(pos))
					.ifPresent(job -> jobs.merge(job.serializedName(), 1, Integer::sum));
		}
		// L'entrepôt n'a pas de poste : son registre en fait l'entrepôt de la ville.
		int expected = family.equals("warehouse") ? 0 : tier;
		int total = jobs.values().stream().mapToInt(Integer::intValue).sum();
		if (jobs.size() > 1 || total != expected) {
			return name + " : postes " + jobs + " au lieu de " + expected;
		}
		return null;
	}

	/** Vide la place (sans prévenir les voisins : rien ne tombe en objets) et remet la pelouse. */
	private static void clear(ServerLevel level) {
		for (int x = -4; x <= 34; x++) {
			for (int z = -4; z <= 26; z++) {
				for (int y = Y; y < Y + 24; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!level.getBlockState(pos).isAir()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
					}
				}
				level.setBlock(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
			}
		}
	}
}
