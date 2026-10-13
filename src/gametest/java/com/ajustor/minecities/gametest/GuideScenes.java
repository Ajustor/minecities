package com.ajustor.minecities.gametest;

import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.guide.Guide;
import com.ajustor.minecities.registry.McItems;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

import static com.ajustor.minecities.gametest.CityScenes.hideGui;

/**
 * Le guide de la ville (v1.6) : le joueur le reçoit une fois, {@code /ville guide} en redonne un, la
 * recette existe, chaque page est traduite, le sommaire mène aux bons chapitres, la dernière page et
 * {@code /ville bug} mènent au formulaire du site. Avec fenêtre, chaque page est photographiée.
 *
 * <p>{@code ./gradlew runGameTest -PminecitiesGametest=guide}
 */
public class GuideScenes implements Scene {
	/** La largeur du texte d'une page, et le nombre de lignes qui y tiennent (128 pixels de 9). */
	private static final int TEXT_WIDTH = 114;
	private static final int LINES = 14;

	@Override
	public String name() {
		return "guide";
	}

	@Override
	public void play(Stage stage) {
		stage.run(server -> {
			ServerPlayer player = player(server);
			player.removeTag("minecities.guide");
			player.getInventory().clearContent();
			check(Guide.welcome(player), "le joueur n'a pas reçu son guide à sa première venue");
			check(!Guide.welcome(player), "le joueur a reçu un second guide en revenant");
			check(guides(player.getInventory()) == 1, "il y a " + guides(player.getInventory()) + " guides");
			check(run(server, player, "ville guide") == 1, "/ville guide a échoué");
			check(guides(player.getInventory()) == 2, "/ville guide n'a pas donné de guide");
			check(run(server, player, "ville bug") == 1, "/ville bug a échoué");
			check(server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, MineCities.id("guide")))
					.isPresent(), "la recette du guide manque");
			List<Component> pages = Guide.pages();
			check(pages.size() == 32, "le guide a " + pages.size() + " pages");
			List<String> missing = new ArrayList<>();
			List<Integer> targets = new ArrayList<>();
			List<String> links = new ArrayList<>();
			for (Component page : pages) {
				walk(page, missing, targets, links);
			}
			check(missing.isEmpty(), "pages sans traduction : " + missing);
			check(targets.size() == 11 && targets.getFirst() == 3 && targets.getLast() == pages.size(),
					"le sommaire mène aux pages " + targets);
			check(links.size() == 1 && links.getFirst().startsWith(Guide.SITE + "report.html?version="),
					"le lien du formulaire : " + links);
			MineCities.LOGGER.info("Guide : {} pages, sommaire {}, lien {}", pages.size(), targets, links);
		});
		// Avec fenêtre : chaque page, en français puis en anglais ; aucune ne doit déborder.
		int count = stage.compute(server -> Guide.pages().size());
		List<String> overflows = new ArrayList<>();
		stage.client(mc -> hideGui(mc, true));
		for (String lang : List.of("fr_fr", "en_us")) {
			stage.client(mc -> Language.inject(ClientLanguage.loadFrom(mc.getResourceManager(),
					List.of("en_us", lang), false)));
			for (int i = 0; i < count; i++) {
				int page = i;
				stage.client(mc -> {
					List<Component> pages = Guide.pages();
					if (mc.font.split(pages.get(page), TEXT_WIDTH).size() > LINES) {
						overflows.add(lang + " p. " + (page + 1));
					}
					BookViewScreen screen = new BookViewScreen(new BookViewScreen.BookAccess(pages));
					mc.gui.setScreen(screen);
					screen.setPage(page);
				});
				stage.waitTicks(2);
				stage.shot("guide_%s_%02d".formatted(lang, page + 1));
			}
		}
		stage.client(mc -> {
			Language.inject(ClientLanguage.loadFrom(mc.getResourceManager(), List.of("en_us"), false));
			mc.gui.setScreen(null);
			hideGui(mc, false);
		});
		check(overflows.isEmpty(), "pages qui débordent : " + overflows);
	}


	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static int run(MinecraftServer server, ServerPlayer player, String command) {
		try {
			return server.getCommands().getDispatcher().execute(command,
					player.createCommandSourceStack());
		} catch (Exception e) {
			throw new AssertionError("/" + command + " : " + e.getMessage(), e);
		}
	}

	private static int guides(Inventory inventory) {
		return inventory.countItem(McItems.GUIDE);
	}

	/** Les clés sans traduction, les pages visées par le sommaire, les liens. */
	private static void walk(Component component, List<String> missing, List<Integer> targets, List<String> links) {
		if (component.getContents() instanceof TranslatableContents translatable
				&& !Language.getInstance().has(translatable.getKey())) {
			missing.add(translatable.getKey());
		}
		ClickEvent click = component.getStyle().getClickEvent();
		if (click instanceof ClickEvent.ChangePage change) {
			targets.add(change.page());
		} else if (click instanceof ClickEvent.OpenUrl url) {
			links.add(url.uri().toString());
		}
		for (Component sibling : component.getSiblings()) {
			// Le sommaire : le style du lien est sur le parent de l'intitulé ; on ne compte que lui.
			walk(sibling, missing, click instanceof ClickEvent.ChangePage ? new ArrayList<>() : targets,
					click instanceof ClickEvent.OpenUrl ? new ArrayList<>() : links);
		}
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new AssertionError(message);
		}
	}
}
