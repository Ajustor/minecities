package com.ajustor.minecities.guide;

import com.ajustor.minecities.McConfig;
import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.compat.EntityCompat;
import com.ajustor.minecities.registry.McItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Le guide de la ville : un livre que chaque joueur reçoit à sa première venue (et par
 * {@code /ville guide}), avec un sommaire cliquable et, à la fin, le lien vers le formulaire de
 * signalement du site, prérempli avec la version du mod.
 */
public final class Guide {
	/** Le site du mod, où se trouve le formulaire de signalement. */
	public static final String SITE = "https://ajustor.github.io/minecities/";
	/** L'étiquette posée sur le joueur qui a reçu son guide : on ne le donne qu'une fois. */
	private static final String RECEIVED = "minecities.guide";

	/** Ce qui n'est pas « un autre mod » : le nôtre, Fabric et les modules de Fabric API. */
	private static final Pattern OWN = Pattern.compile(
			"minecities(-gametest)?|fabricloader|fabric-api(-base)?|fabric-.*-v\\d+|mixinextras|java|minecraft");

	private record Chapter(String key, int pages) {
	}

	private static final List<Chapter> CHAPTERS = List.of(new Chapter("found", 3), new Chapter("house", 2),
			new Chapter("citizens", 4), new Chapter("jobs", 6), new Chapter("build", 4), new Chapter("storage", 2),
			new Chapter("growth", 2), new Chapter("defense", 2), new Chapter("council", 4),
			new Chapter("commands", 2), new Chapter("help", 1));

	/** Ouvre le livre, sur le client : branché par le client, rien sur un serveur dédié. */
	public static Consumer<Player> opener = player -> {
	};

	private Guide() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			teachRecipes(handler.getPlayer());
			welcome(handler.getPlayer());
		});
	}

	/**
	 * Le joueur connaît toutes les recettes du mod : sans mod de recettes (JEI, EMI), le livre de recettes
	 * montre l'hôtel de ville, la plaque, les postes… À chaque venue, pour les mondes d'avant et les
	 * recettes ajoutées depuis.
	 */
	public static void teachRecipes(ServerPlayer player) {
		java.util.List<RecipeHolder<?>> ours = player.level().getServer().getRecipeManager().getRecipes().stream()
				.filter(holder -> holder.id().identifier().getNamespace().equals(MineCities.MOD_ID)).toList();
		player.awardRecipes(ours);
	}

	/** À sa première venue, le joueur reçoit son guide ; vrai s'il vient de le recevoir. */
	public static boolean welcome(ServerPlayer player) {
		if (!McConfig.get().giveGuide || !player.addTag(RECEIVED)) {
			return false;
		}
		give(player);
		player.sendSystemMessage(Component.translatable("message.minecities.guide.welcome"));
		return true;
	}

	/** Donne un guide au joueur (par terre si ses poches sont pleines). */
	public static void give(ServerPlayer player) {
		ItemStack guide = new ItemStack(McItems.GUIDE);
		if (!player.getInventory().add(guide)) {
			EntityCompat.drop(player, guide);
		}
	}

	/** Les pages du livre : la couverture, le sommaire, puis les chapitres. */
	public static List<Component> pages() {
		List<Component> pages = new ArrayList<>();
		pages.add(Component.empty().append(title("guide.minecities.title")).append("\n\n")
				.append(Component.translatable("guide.minecities.cover")));
		MutableComponent contents = Component.empty().append(title("guide.minecities.contents")).append("\n");
		// Les pages se comptent à partir de 1 : la couverture, le sommaire, puis le premier chapitre.
		int page = 3;
		for (Chapter chapter : CHAPTERS) {
			int target = page;
			contents.append(Component.literal("\n").append(Component.translatable("guide.minecities." + chapter.key()))
					.withStyle(s -> s.withColor(ChatFormatting.DARK_BLUE).withClickEvent(new ClickEvent.ChangePage(target))));
			page += chapter.pages();
		}
		pages.add(contents);
		for (Chapter chapter : CHAPTERS) {
			for (int i = 1; i <= chapter.pages(); i++) {
				MutableComponent text = i == 1
						? Component.empty().append(title("guide.minecities." + chapter.key())).append("\n\n")
						: Component.empty();
				text.append(Component.translatable("guide.minecities." + chapter.key() + "." + i));
				if (chapter.key().equals("help")) {
					text.append("\n\n").append(Component.translatable("guide.minecities.help.link")
							.withStyle(s -> s.withColor(ChatFormatting.DARK_BLUE).withUnderlined(true)
									.withClickEvent(new ClickEvent.OpenUrl(report(setup())))));
				}
				pages.add(text);
			}
		}
		return pages;
	}

	private static MutableComponent title(String key) {
		return Component.translatable(key).withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_GREEN);
	}

	/**
	 * Ce que sait le jeu de la configuration du joueur : versions du mod, de Minecraft, de Fabric, et
	 * les autres mods installés.
	 */
	public static Map<String, String> setup() {
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("version", version("minecities"));
		fields.put("mc", version("minecraft"));
		fields.put("loader", version("fabricloader"));
		fields.put("api", version("fabric-api"));
		String mods = FabricLoader.getInstance().getAllMods().stream()
				.filter(m -> m.getContainingMod().isEmpty())
				.map(ModContainer::getMetadata)
				.filter(m -> !m.getType().equals("builtin") && !OWN.matcher(m.getId()).matches())
				.map(m -> m.getId() + " " + m.getVersion().getFriendlyString())
				.sorted()
				.collect(Collectors.joining(", "));
		// Une adresse trop longue est refusée : la liste est tronquée au besoin.
		fields.put("mods", mods.length() > 1200 ? mods.substring(0, 1200) + "…" : mods);
		return fields;
	}

	private static String version(String mod) {
		return FabricLoader.getInstance().getModContainer(mod)
				.map(m -> m.getMetadata().getVersion().getFriendlyString())
				.orElse("");
	}

	/** L'adresse du formulaire de signalement, préremplie avec ces champs. */
	public static URI report(Map<String, String> fields) {
		String query = fields.entrySet().stream()
				.filter(e -> !e.getValue().isEmpty())
				.map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
		return URI.create(SITE + "report.html" + (query.isEmpty() ? "" : "?" + query));
	}
}
