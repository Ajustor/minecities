package com.ajustor.minecities.command;

import com.ajustor.minecities.build.Blueprint;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildPlan;
import com.ajustor.minecities.build.BuildStyle;
import com.ajustor.minecities.build.Builds;
import com.ajustor.minecities.build.Construction;
import com.ajustor.minecities.build.Proposals;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.guide.Guide;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Election;
import com.ajustor.minecities.town.Elections;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Membership;
import com.ajustor.minecities.town.Decree;
import com.ajustor.minecities.town.Festivals;
import com.ajustor.minecities.town.Trade;
import com.ajustor.minecities.town.TownTier;
import com.ajustor.minecities.town.Raids;
import com.ajustor.minecities.citizen.Hunger;
import com.ajustor.minecities.town.Resident;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.Stats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /minecities} (ou {@code /ville}) : l'état de sa ville, la liste des villes, et pour le maire
 * le nom de la ville, la passation de l'écharpe et la nomination des adjoints. Les sous-commandes
 * {@code admin} sont réservées aux opérateurs.
 */
public final class TownCommands {
	private static final SimpleCommandExceptionType NO_TOWN = new SimpleCommandExceptionType(
			Component.translatable("command.minecities.no_town"));
	private static final SimpleCommandExceptionType NOT_MAYOR = new SimpleCommandExceptionType(
			Component.translatable("command.minecities.not_mayor"));
	private static final SimpleCommandExceptionType WHICH_TOWN = new SimpleCommandExceptionType(
			Component.translatable("command.minecities.which_town"));
	private static final SimpleCommandExceptionType NOT_MANAGER = new SimpleCommandExceptionType(
			Component.translatable("command.minecities.not_manager"));
	private static final SimpleCommandExceptionType NO_BLUEPRINT = new SimpleCommandExceptionType(
			Component.translatable("command.minecities.no_blueprint"));
	private static final SimpleCommandExceptionType NO_DECREE = new SimpleCommandExceptionType(
			Component.translatable("command.minecities.no_decree"));

	private TownCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(root("minecities"));
			dispatcher.register(root("ville"));
		});
	}

	private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
		return Commands.literal(name)
				.executes(TownCommands::info)
				.then(Commands.literal("info").executes(TownCommands::info))
				.then(Commands.literal("list").executes(TownCommands::list))
				.then(Commands.literal("guide").executes(TownCommands::guide))
				.then(Commands.literal("bug").executes(TownCommands::bug))
				.then(Commands.literal("citizens").executes(TownCommands::citizens))
				.then(Commands.literal("rename")
						.then(Commands.argument("name", StringArgumentType.greedyString())
								.executes(TownCommands::rename)))
				.then(Commands.literal("mayor")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(TownCommands::transfer)))
				.then(Commands.literal("deputy")
						.then(Commands.literal("add")
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> deputy(c, true))))
						.then(Commands.literal("remove")
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> deputy(c, false)))))
				.then(Commands.literal("join")
						.executes(c -> join(c, Optional.empty()))
						.then(Commands.argument("town", StringArgumentType.greedyString())
								.suggests((c, b) -> {
									for (Town t : Towns.get(c.getSource().getServer()).all()) {
										b.suggest(t.name());
									}
									return b.buildFuture();
								})
								.executes(c -> join(c, Optional.of(StringArgumentType.getString(c, "town"))))))
				.then(Commands.literal("leave").executes(TownCommands::leave))
				.then(Commands.literal("resident")
						.executes(TownCommands::residents)
						.then(Commands.literal("accept").then(Commands.argument("player", StringArgumentType.word())
								.suggests((c, b) -> suggestPlayers(c, b, true))
								.executes(c -> answer(c, "accept"))))
						.then(Commands.literal("refuse").then(Commands.argument("player", StringArgumentType.word())
								.suggests((c, b) -> suggestPlayers(c, b, true))
								.executes(c -> answer(c, "refuse"))))
						.then(Commands.literal("remove").then(Commands.argument("player", StringArgumentType.word())
								.suggests((c, b) -> suggestPlayers(c, b, false))
								.executes(c -> answer(c, "remove")))))
				.then(Commands.literal("permission")
						.then(Commands.argument("rank", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of(
										Membership.Rank.RESIDENT.serializedName(), Membership.Rank.VISITOR.serializedName()), b))
								.then(Commands.argument("permission", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(
												java.util.Arrays.stream(Membership.Permission.values())
														.map(Membership.Permission::serializedName), b))
										.then(Commands.argument("allowed", com.mojang.brigadier.arguments.BoolArgumentType.bool())
												.executes(TownCommands::permission)))))
				.then(Commands.literal("election")
						.executes(TownCommands::election)
						.then(Commands.literal("call").executes(c -> elect(c, "call")))
						.then(Commands.literal("sign").executes(c -> elect(c, "sign")))
						.then(Commands.literal("run").executes(c -> elect(c, "run"))))
				.then(Commands.literal("vote")
						.then(Commands.argument("candidate", StringArgumentType.word())
								.suggests((c, b) -> {
									try {
										return SharedSuggestionProvider.suggest(memberTown(c).election().candidates()
												.stream().map(Map.Entry::getValue), b);
									} catch (CommandSyntaxException e) {
										return b.buildFuture();
									}
								})
								.executes(TownCommands::vote)))
				.then(Commands.literal("blueprints").executes(TownCommands::blueprints))
				.then(Commands.literal("build")
						.then(Commands.literal("cancel").executes(TownCommands::cancelBuild))
						.then(Commands.literal("rotate").executes(TownCommands::rotateBuild))
						.then(Commands.literal("confirm").executes(TownCommands::confirmBuild))
						.then(Commands.argument("blueprint", IdentifierArgument.id())
								.suggests((c, b) -> SharedSuggestionProvider.suggestResource(
										Blueprints.all(c.getSource().getServer()).values().stream()
												.filter(p -> !p.legacy()).map(Blueprint::id), b))
								.executes(c -> build(c, BuildStyle.PLAINS))
								.then(Commands.argument("style", StringArgumentType.word())
										.suggests((c, b) -> {
											for (BuildStyle style : BuildStyle.values()) {
												b.suggest(style.serializedName());
											}
											return b.buildFuture();
										})
										.executes(c -> build(c, BuildStyle.fromSerializedName(
												StringArgumentType.getString(c, "style")).orElse(BuildStyle.PLAINS))))))
				.then(Commands.literal("upgrade").executes(TownCommands::upgrade))
				.then(Commands.literal("marriages").executes(TownCommands::marriages))
				.then(Commands.literal("festival").executes(TownCommands::festival))
				.then(Commands.literal("decree")
						.then(Commands.argument("decree", StringArgumentType.word())
								.suggests((c, b) -> {
									for (Decree d : Decree.values()) {
										b.suggest(d.serializedName());
									}
									return b.buildFuture();
								})
								.executes(TownCommands::decree)))
				.then(Commands.literal("trade")
						.executes(TownCommands::tradeList)
						.then(Commands.argument("town", StringArgumentType.greedyString())
								.suggests((c, b) -> {
									for (Town t : Towns.get(c.getSource().getServer()).all()) {
										b.suggest(t.name());
									}
									return b.buildFuture();
								})
								.executes(TownCommands::trade)))
				.then(Commands.literal("marry")
						.then(Commands.literal("accept").then(Commands.argument("number",
								com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
								.executes(c -> marry(c, true))))
						.then(Commands.literal("refuse").then(Commands.argument("number",
								com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
								.executes(c -> marry(c, false)))))
				.then(Commands.literal("admin")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("immigrate").executes(TownCommands::immigrate))
						.then(Commands.literal("raid").executes(c -> {
							Town town = here(c);
							ServerLevel level = c.getSource().getServer().getLevel(town.dimension());
							return level != null && Raids.start(level, town) ? 1 : 0;
						}))
						.then(Commands.literal("raid_stop").executes(c -> {
							Raids.stop(c.getSource().getServer(), here(c).id());
							return 1;
						}))
						.then(Commands.literal("tier")
								.then(Commands.argument("tier", StringArgumentType.word())
										.suggests((c, b) -> {
											b.suggest("auto");
											for (TownTier t : TownTier.values()) {
												b.suggest(t.serializedName());
											}
											return b.buildFuture();
										})
										.executes(TownCommands::forceTier)))
						.then(Commands.literal("festival").executes(c -> {
							Town town = here(c);
							ServerLevel level = c.getSource().getServer().getLevel(town.dimension());
							if (level == null) {
								return 0;
							}
							Festivals.celebrate(level, town, "message.minecities.festival.offered");
							return 1;
						}))
						.then(Commands.literal("feed").executes(TownCommands::feed))
						.then(Commands.literal("starve").executes(TownCommands::starve)));
	}

	/** La ville où se trouve le joueur, sinon celle qu'il gère. */
	private static Town here(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		Towns towns = Towns.get(source.getServer());
		Optional<Town> town = towns.at(source.getLevel().dimension(), net.minecraft.core.BlockPos.containing(
				source.getPosition()));
		if (town.isEmpty() && source.getEntity() instanceof ServerPlayer player) {
			town = towns.managedBy(player.getUUID()).stream().findFirst();
		}
		return town.orElseThrow(NO_TOWN::create);
	}

	/**
	 * La ville dont le joueur est maire : celle où il se trouve, ou la seule qu'il dirige. S'il en
	 * dirige plusieurs, il doit se rendre dans celle qu'il veut gérer.
	 */
	private static Town mayorTown(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		Towns towns = Towns.get(source.getServer());
		Optional<Town> here = towns.at(source.getLevel().dimension(), player.blockPosition())
				.filter(t -> t.mayor().equals(player.getUUID()));
		if (here.isPresent()) {
			return here.get();
		}
		List<Town> mine = towns.ofMayor(player.getUUID());
		if (mine.isEmpty()) {
			throw NOT_MAYOR.create();
		}
		if (mine.size() > 1) {
			throw WHICH_TOWN.create();
		}
		return mine.getFirst();
	}

	/** Un opérateur impose un palier à la ville où il se trouve, ou le rend à la population. */
	private static int forceTier(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Town town = here(context);
		String name = StringArgumentType.getString(context, "tier");
		ServerLevel level = context.getSource().getServer().getLevel(town.dimension());
		if (name.equals("auto")) {
			town.forceTier(Optional.empty());
			context.getSource().sendSuccess(() -> Component.translatable("command.minecities.admin.tier.auto",
					town.name()), true);
		} else {
			// Une faute de frappe n'impose pas le village en silence.
			if (java.util.Arrays.stream(TownTier.values()).noneMatch(t -> t.serializedName().equals(name))) {
				context.getSource().sendFailure(Component.translatable("command.minecities.admin.tier.unknown", name,
						String.join(", ", java.util.Arrays.stream(TownTier.values()).map(TownTier::serializedName)
								.toList())));
				return 0;
			}
			TownTier tier = TownTier.fromSerializedName(name);
			town.forceTier(Optional.of(tier));
			context.getSource().sendSuccess(() -> Component.translatable("command.minecities.admin.tier",
					town.name(), Component.translatable(tier.translationKey())), true);
		}
		if (level != null) {
			TownService.retier(level, town);
		}
		Towns.get(context.getSource().getServer()).changed();
		return Command.SINGLE_SUCCESS;
	}

	/** Le maire (ou un adjoint) prend ou lève un décret. */
	private static int decree(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Town town = managedHere(context);
		Decree decree = Decree.fromSerializedName(StringArgumentType.getString(context, "decree"))
				.orElseThrow(NO_DECREE::create);
		ServerLevel level = context.getSource().getServer().getLevel(town.dimension());
		Optional<Component> refusal = TownService.decree(level != null ? level : context.getSource().getLevel(),
				town, decree);
		if (refusal.isPresent()) {
			context.getSource().sendFailure(refusal.get());
			return 0;
		}
		return Command.SINGLE_SUCCESS;
	}

	/** Le maire signe (ou dénonce) un accord de commerce avec une autre ville. */
	private static int trade(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Town town = managedHere(context);
		Towns towns = Towns.get(context.getSource().getServer());
		Town other = towns.byName(StringArgumentType.getString(context, "town"))
				.filter(t -> !t.id().equals(town.id())).orElseThrow(NO_TOWN::create);
		if (town.partners().contains(other.id())) {
			town.removePartner(other.id());
			TownService.notify(context.getSource().getServer(), town, Component.translatable(
					"message.minecities.trade.ended", town.name(), other.name()));
			TownService.notify(context.getSource().getServer(), other, Component.translatable(
					"message.minecities.trade.ended", town.name(), other.name()));
		} else {
			town.addPartner(other.id());
			String key = Trade.trading(town, other) ? "message.minecities.trade.open" : "message.minecities.trade.offered";
			TownService.notify(context.getSource().getServer(), town, Component.translatable(key, town.name(), other.name()));
			TownService.notify(context.getSource().getServer(), other, Component.translatable(key, town.name(),
					other.name()));
		}
		towns.changed();
		return Command.SINGLE_SUCCESS;
	}

	/** Les accords de commerce de la ville. */
	private static int tradeList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Town town = here(context);
		Towns towns = Towns.get(context.getSource().getServer());
		context.getSource().sendSuccess(() -> Component.translatable("command.minecities.trade", town.name())
				.withStyle(ChatFormatting.GOLD), false);
		for (Town other : towns.all()) {
			boolean ours = town.partners().contains(other.id());
			boolean theirs = other.partners().contains(town.id());
			if (ours || theirs) {
				String key = ours && theirs ? "command.minecities.trade.open" : ours ? "command.minecities.trade.waiting"
						: "command.minecities.trade.offer";
				context.getSource().sendSuccess(() -> Component.translatable(key, other.name()), false);
			}
		}
		return Command.SINGLE_SUCCESS;
	}

	/** Le maire (ou un adjoint) offre une fête, contre quelques émeraudes du grenier. */
	private static int festival(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Town town = here(context);
		ServerPlayer player = context.getSource().getPlayerOrException();
		if (!town.canManage(player.getUUID())) {
			throw NOT_MAYOR.create();
		}
		ServerLevel level = context.getSource().getServer().getLevel(town.dimension());
		if (level == null) {
			return 0;
		}
		Optional<Festivals.Refusal> refusal = Festivals.offer(level, town);
		if (refusal.isPresent()) {
			context.getSource().sendFailure(Component.translatable("message.minecities.festival.refused."
					+ refusal.get().name().toLowerCase(), Festivals.cost()));
			return 0;
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Town town = here(context);
		ServerLevel level = context.getSource().getServer().getLevel(town.dimension());
		ServerPlayer player = context.getSource().getPlayerOrException();
		TownService.describe(level != null ? level : context.getSource().getLevel(), town, player);
		return Command.SINGLE_SUCCESS;
	}

	private static int guide(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Guide.give(context.getSource().getPlayerOrException());
		context.getSource().sendSuccess(() -> Component.translatable("command.minecities.guide"), false);
		return Command.SINGLE_SUCCESS;
	}

	/**
	 * Un lien vers le formulaire de signalement du site, prérempli avec ce qui aide à corriger : les
	 * versions, les autres mods, le temps de jeu, le jour, le mode de jeu et la ville où l'on est.
	 */
	private static int bug(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		MinecraftServer server = context.getSource().getServer();
		Map<String, String> fields = Guide.setup();
		int ticks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
		fields.put("playtime", String.format(Locale.ROOT, "%.1f", ticks / 72_000.0));
		fields.put("day", Long.toString(server.overworld().getOverworldClockTime() / 24_000L + 1));
		fields.put("env", server.isDedicatedServer() ? "server" : server.isPublished() ? "lan" : "solo");
		Towns.get(server).at(player.level().dimension(), player.blockPosition()).ifPresent(town ->
				fields.put("town", town.tier().name().toLowerCase(Locale.ROOT) + ", " + town.population()));
		ClickEvent open = new ClickEvent.OpenUrl(Guide.report(fields));
		Component link = Component.translatable("command.minecities.bug.link").withStyle(s -> s
				.withColor(ChatFormatting.AQUA).withUnderlined(true).withClickEvent(open)
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("command.minecities.bug.hover"))));
		context.getSource().sendSuccess(() -> Component.translatable("command.minecities.bug").append(" ").append(link), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int list(CommandContext<CommandSourceStack> context) {
		Towns towns = Towns.get(context.getSource().getServer());
		if (towns.all().isEmpty()) {
			context.getSource().sendSuccess(() -> Component.translatable("command.minecities.list.empty"), false);
			return 0;
		}
		for (Town town : towns.all()) {
			context.getSource().sendSuccess(() -> Component.translatable("command.minecities.list.entry",
					town.name(), Component.translatable(town.tier().translationKey()), town.population(),
					town.mayorName(), town.hall().toShortString()), false);
		}
		return towns.all().size();
	}

	private static int citizens(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Town town = here(context);
		ServerLevel level = context.getSource().getServer().getLevel(town.dimension());
		context.getSource().sendSuccess(() -> Component.translatable("command.minecities.citizens", town.name(),
				town.citizens()).withStyle(ChatFormatting.GOLD), false);
		for (Map.Entry<UUID, Resident> e : town.residents().entrySet()) {
			Entity entity = level != null ? level.getEntity(e.getKey()) : null;
			Component hunger = entity instanceof CitizenEntity citizen
					? Component.translatable(Hunger.state(citizen.hunger()).translationKey())
					: Component.translatable("command.minecities.citizens.away");
			Component home = e.getValue().home()
					.<Component>map(p -> Component.translatable("citizen.minecities.home", p.toShortString()))
					.orElse(Component.translatable("citizen.minecities.homeless"));
			context.getSource().sendSuccess(() -> Component.translatable("command.minecities.citizens.entry",
					e.getValue().name(), hunger, home), false);
		}
		return town.citizens();
	}

	private static int rename(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Town town = mayorTown(context);
		String name = StringArgumentType.getString(context, "name").trim();
		Towns towns = Towns.get(context.getSource().getServer());
		if (name.isEmpty() || towns.byName(name).filter(t -> t != town).isPresent()) {
			context.getSource().sendFailure(Component.translatable("command.minecities.rename.taken", name));
			return 0;
		}
		String before = town.name();
		town.rename(name);
		towns.changed();
		context.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
				"command.minecities.rename.done", before, name).withStyle(ChatFormatting.GOLD), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int transfer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Town town = mayorTown(context);
		ServerPlayer successor = EntityArgument.getPlayer(context, "player");
		Towns towns = Towns.get(context.getSource().getServer());
		if (town.election().phase() != com.ajustor.minecities.town.Election.Phase.NONE) {
			context.getSource().sendFailure(Component.translatable("command.minecities.mayor.election"));
			return 0;
		}
		if (!town.isMember(successor.getUUID())) {
			context.getSource().sendFailure(Component.translatable("command.minecities.not_member",
					successor.getDisplayName(), town.name()));
			return 0;
		}
		// L'ancien maire reste habitant de sa ville.
		town.membership().addResident(town.mayor(), town.mayorName());
		town.setMayor(successor.getUUID(), successor.getGameProfile().name());
		towns.changed();
		context.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
				"command.minecities.mayor.done", successor.getDisplayName(), town.name())
				.withStyle(ChatFormatting.GOLD), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int deputy(CommandContext<CommandSourceStack> context, boolean add) throws CommandSyntaxException {
		Town town = mayorTown(context);
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		if (add && !town.isMember(target.getUUID())) {
			context.getSource().sendFailure(Component.translatable("command.minecities.not_member",
					target.getDisplayName(), town.name()));
			return 0;
		}
		boolean changed = add ? town.addDeputy(target.getUUID()) : town.removeDeputy(target.getUUID());
		if (!changed) {
			context.getSource().sendFailure(Component.translatable("command.minecities.deputy.unchanged"));
			return 0;
		}
		Towns.get(context.getSource().getServer()).changed();
		String key = add ? "command.minecities.deputy.added" : "command.minecities.deputy.removed";
		context.getSource().sendSuccess(() -> Component.translatable(key, target.getDisplayName(), town.name()), true);
		target.sendSystemMessage(Component.translatable(key, target.getDisplayName(), town.name())
				.withStyle(ChatFormatting.GOLD));
		return Command.SINGLE_SUCCESS;
	}

	// Habitants joueurs

	/** Demande à s'installer dans la ville nommée, ou dans celle où l'on se trouve. */
	private static int join(CommandContext<CommandSourceStack> context, Optional<String> name)
			throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Towns towns = Towns.get(context.getSource().getServer());
		Town town = (name.isPresent() ? towns.byName(name.get())
				: towns.at(context.getSource().getLevel().dimension(), player.blockPosition())).orElseThrow(NO_TOWN::create);
		return TownService.requestJoin(context.getSource().getServer(), town, player) ? Command.SINGLE_SUCCESS : 0;
	}

	/** Quitte sa ville (le maire passe d'abord l'écharpe). */
	private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		List<Town> mine = Towns.get(context.getSource().getServer()).memberOf(player.getUUID());
		Optional<Town> town = mine.stream().filter(t -> !t.mayor().equals(player.getUUID())).findFirst();
		if (town.isEmpty()) {
			context.getSource().sendFailure(Component.translatable(mine.isEmpty() ? "command.minecities.leave.none"
					: "command.minecities.leave.mayor"));
			return 0;
		}
		return TownService.leave(context.getSource().getServer(), town.get(), player) ? Command.SINGLE_SUCCESS : 0;
	}

	/** Les joueurs de la ville (là où l'on est, sinon celle qu'on gère), les demandes et les droits. */
	private static int residents(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Town town = here(context);
		Membership membership = town.membership();
		CommandSourceStack source = context.getSource();
		source.sendSuccess(() -> Component.translatable("command.minecities.resident.title", town.name())
				.withStyle(ChatFormatting.GOLD), false);
		source.sendSuccess(() -> Component.translatable("command.minecities.resident.row",
				Component.translatable(Membership.Rank.MAYOR.translationKey()), town.mayorName()), false);
		for (Map.Entry<UUID, String> resident : membership.residentList()) {
			Membership.Rank rank = town.rank(resident.getKey());
			source.sendSuccess(() -> Component.translatable("command.minecities.resident.row",
					Component.translatable(rank.translationKey()), resident.getValue()), false);
		}
		for (Map.Entry<UUID, String> request : membership.requests()) {
			source.sendSuccess(() -> Component.translatable("command.minecities.resident.request", request.getValue())
					.withStyle(ChatFormatting.AQUA), false);
		}
		for (Membership.Rank rank : List.of(Membership.Rank.RESIDENT, Membership.Rank.VISITOR)) {
			Component allowed = membership.permissions(rank).isEmpty()
					? Component.translatable("command.minecities.resident.nothing")
					: Component.literal(String.join(", ", membership.permissions(rank).stream()
							.map(p -> Component.translatable(p.translationKey()).getString()).toList()));
			source.sendSuccess(() -> Component.translatable("command.minecities.resident.rights",
					Component.translatable(rank.translationKey()), allowed).withStyle(ChatFormatting.GRAY), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	/** Le maire ou un adjoint accepte, refuse une demande, ou renvoie un habitant. */
	private static int answer(CommandContext<CommandSourceStack> context, String what) throws CommandSyntaxException {
		Town town = here(context);
		ServerPlayer player = context.getSource().getPlayerOrException();
		if (!town.canManage(player.getUUID())) {
			context.getSource().sendFailure(Component.translatable("command.minecities.resident.not_manager"));
			return 0;
		}
		String name = StringArgumentType.getString(context, "player");
		List<Map.Entry<UUID, String>> among = what.equals("remove") ? town.membership().residentList()
				: town.membership().requests();
		Optional<UUID> target = among.stream().filter(e -> e.getValue().equalsIgnoreCase(name)).map(Map.Entry::getKey)
				.findFirst();
		MinecraftServer server = context.getSource().getServer();
		boolean done = target.isPresent() && (what.equals("remove") ? TownService.expel(server, town, target.get())
				: TownService.answerJoin(server, town, target.get(), what.equals("accept")));
		if (!done) {
			context.getSource().sendFailure(Component.translatable("command.minecities.resident.unknown", name));
			return 0;
		}
		return Command.SINGLE_SUCCESS;
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayers(
			CommandContext<CommandSourceStack> context, com.mojang.brigadier.suggestion.SuggestionsBuilder builder,
			boolean requests) {
		try {
			Membership membership = here(context).membership();
			return SharedSuggestionProvider.suggest((requests ? membership.requests() : membership.residentList())
					.stream().map(Map.Entry::getValue), builder);
		} catch (CommandSyntaxException e) {
			return builder.buildFuture();
		}
	}

	// Élections

	/** La ville dont le joueur fait partie : celle où il se trouve, sinon la sienne. */
	private static Town memberTown(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Towns towns = Towns.get(context.getSource().getServer());
		Optional<Town> here = towns.at(context.getSource().getLevel().dimension(), player.blockPosition())
				.filter(t -> t.isMember(player.getUUID()));
		return here.or(() -> towns.memberOf(player.getUUID()).stream().findFirst()).orElseThrow(NO_TOWN::create);
	}

	/** Où en est l'élection de la ville. */
	private static int election(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Town town = here(context);
		Election election = town.election();
		long now = context.getSource().getLevel().getGameTime();
		CommandSourceStack source = context.getSource();
		if (election.termEnds() > 0 && election.phase() == Election.Phase.NONE) {
			source.sendSuccess(() -> Component.translatable("command.minecities.election.term", town.mayorName(),
					minutes(election.termEnds() - now)), false);
		}
		election.lastWinner().ifPresent(w -> source.sendSuccess(() -> Component.translatable(
				"command.minecities.election.last", w, election.lastVotes()), false));
		switch (election.phase()) {
			case NONE -> source.sendSuccess(() -> Component.translatable("command.minecities.election.none",
					election.petition().size(), Elections.signaturesNeeded(town)), false);
			case CANDIDACY -> source.sendSuccess(() -> Component.translatable("command.minecities.election.candidacy",
					names(election), minutes(election.phaseEnds() - now)), false);
			case VOTING -> source.sendSuccess(() -> Component.translatable("command.minecities.election.voting",
					election.round(), names(election), minutes(election.phaseEnds() - now)), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static String names(Election election) {
		return String.join(", ", election.candidates().stream().map(Map.Entry::getValue).toList());
	}

	/** Des ticks de jeu en minutes (au moins une). */
	private static long minutes(long ticks) {
		return Math.max(1, ticks / 1200);
	}

	/** Le maire ouvre une élection, un habitant signe la pétition, un joueur se porte candidat. */
	private static int elect(CommandContext<CommandSourceStack> context, String what) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Town town = memberTown(context);
		ServerLevel level = context.getSource().getServer().getLevel(town.dimension());
		if (level == null) {
			return 0;
		}
		Optional<Elections.Refusal> refusal = switch (what) {
			case "call" -> Elections.call(level, town, player);
			case "sign" -> Elections.sign(level, town, player);
			default -> Elections.run(level, town, player);
		};
		return refused(context, town, refusal, level.getGameTime());
	}

	private static int refused(CommandContext<CommandSourceStack> context, Town town, Optional<Elections.Refusal> refusal,
			long now) {
		if (refusal.isEmpty()) {
			return Command.SINGLE_SUCCESS;
		}
		context.getSource().sendFailure(Component.translatable(refusal.get().translationKey(),
				minutes(Elections.untilAllowed(town, now))));
		return 0;
	}

	/** Le joueur vote pour ce candidat. */
	private static int vote(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Town town = memberTown(context);
		ServerLevel level = context.getSource().getServer().getLevel(town.dimension());
		String name = StringArgumentType.getString(context, "candidate");
		Optional<UUID> candidate = town.election().candidates().stream().filter(e -> e.getValue().equalsIgnoreCase(name))
				.map(Map.Entry::getKey).findFirst();
		if (level == null || candidate.isEmpty()) {
			context.getSource().sendFailure(Component.translatable(Elections.Refusal.NOT_CANDIDATE.translationKey()));
			return 0;
		}
		Optional<Elections.Refusal> refusal = Elections.cast(level, town, player, candidate.get());
		if (refusal.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.translatable("command.minecities.election.voted", name),
					false);
		}
		return refused(context, town, refusal, level.getGameTime());
	}

	/** Le maire donne ou retire un droit à l'habitant ou au visiteur. */
	private static int permission(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Town town = mayorTown(context);
		Optional<Membership.Rank> rank = Membership.Rank.fromSerializedName(StringArgumentType.getString(context, "rank"))
				.filter(r -> r == Membership.Rank.RESIDENT || r == Membership.Rank.VISITOR);
		Optional<Membership.Permission> permission = Membership.Permission.fromSerializedName(
				StringArgumentType.getString(context, "permission"));
		if (rank.isEmpty() || permission.isEmpty()) {
			context.getSource().sendFailure(Component.translatable("command.minecities.permission.unknown"));
			return 0;
		}
		boolean allowed = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "allowed");
		town.membership().set(rank.get(), permission.get(), allowed);
		Towns.get(context.getSource().getServer()).changed();
		context.getSource().sendSuccess(() -> Component.translatable(allowed ? "command.minecities.permission.granted"
				: "command.minecities.permission.revoked", Component.translatable(permission.get().translationKey()),
				Component.translatable(rank.get().translationKey()), town.name()), true);
		return Command.SINGLE_SUCCESS;
	}

	/** La ville que le joueur gère (maire ou adjoint) et où il se trouve. */
	private static Town managedHere(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Town town = Towns.get(context.getSource().getServer()).at(context.getSource().getLevel().dimension(),
				player.blockPosition()).orElseThrow(NO_TOWN::create);
		if (!town.canManage(player.getUUID())) {
			throw NOT_MANAGER.create();
		}
		return town;
	}

	/** Les bâtiments qu'on peut commander, chacun à son premier niveau (les suivants s'y greffent). */
	private static int blueprints(CommandContext<CommandSourceStack> context) {
		var all = Blueprints.all(context.getSource().getServer()).values().stream()
				.filter(b -> !b.legacy() && b.level() == 1)
				.collect(java.util.stream.Collectors.toMap(Blueprint::id, b -> b, (a, b) -> a,
						java.util.LinkedHashMap::new));
		context.getSource().sendSuccess(() -> Component.translatable("command.minecities.blueprints", all.size())
				.withStyle(ChatFormatting.GOLD), false);
		for (Blueprint blueprint : all.values()) {
			context.getSource().sendSuccess(() -> Component.translatable("command.minecities.blueprints.entry",
					blueprint.displayName(), blueprint.id().toString(),
					blueprint.width(), blueprint.depth(), blueprint.height(),
					Component.translatable("blueprint.minecities.kind." + blueprint.kind())), false);
		}
		return all.size();
	}

	/**
	 * Propose un chantier devant le joueur, porte vers lui : son fantôme apparaît, et il le tourne, le
	 * valide ou l'abandonne ({@code rotate}, {@code confirm}, {@code cancel}).
	 */
	private static int build(CommandContext<CommandSourceStack> context, BuildStyle style) throws CommandSyntaxException {
		Town town = managedHere(context);
		ServerPlayer player = context.getSource().getPlayerOrException();
		ServerLevel level = context.getSource().getLevel();
		Identifier id = IdentifierArgument.getId(context, "blueprint");
		Blueprint blueprint = Blueprints.get(context.getSource().getServer(), id).orElseThrow(NO_BLUEPRINT::create);
		Rotation rotation = BuildPlan.facing(player.getDirection().getOpposite());
		BlockPos origin = BuildPlan.originFacing(blueprint, player.blockPosition(), rotation);
		Optional<Component> refusal = Proposals.propose(level, player, town, blueprint, style, origin, rotation);
		if (refusal.isPresent()) {
			context.getSource().sendFailure(refusal.get());
			return 0;
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int rotateBuild(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Optional<Component> refusal = Proposals.rotate(context.getSource().getLevel(), player);
		refusal.ifPresent(context.getSource()::sendFailure);
		return refusal.isEmpty() ? Command.SINGLE_SUCCESS : 0;
	}

	private static int confirmBuild(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Optional<Component> refusal = Proposals.confirm(context.getSource().getLevel(), player);
		refusal.ifPresent(context.getSource()::sendFailure);
		return refusal.isEmpty() ? Command.SINGLE_SUCCESS : 0;
	}

	/** Abandonne le chantier proposé, s'il y en a un ; sinon annule le chantier en cours. */
	private static int cancelBuild(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		if (context.getSource().getEntity() instanceof ServerPlayer player
				&& Proposals.cancel(context.getSource().getServer(), player.getUUID())) {
			context.getSource().sendSuccess(() -> Component.translatable("message.minecities.proposal.cancelled"),
					false);
			return Command.SINGLE_SUCCESS;
		}
		Town town = managedHere(context);
		Optional<Construction> active = town.activeConstruction();
		if (active.isEmpty()) {
			context.getSource().sendFailure(Component.translatable("command.minecities.build.none"));
			return 0;
		}
		town.cancel(active.get());
		Towns.get(context.getSource().getServer()).changed();
		context.getSource().sendSuccess(() -> Component.translatable("command.minecities.build.cancelled"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int upgrade(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Town town = managedHere(context);
		ServerPlayer player = context.getSource().getPlayerOrException();
		Optional<Component> refusal = Builds.upgrade(context.getSource().getLevel(), town, player.blockPosition());
		if (refusal.isPresent()) {
			context.getSource().sendFailure(refusal.get());
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.translatable("command.minecities.build.upgrade")
				.withStyle(ChatFormatting.GOLD), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int marriages(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Town town = here(context);
		List<com.ajustor.minecities.town.MarriageRequest> requests = town.marriageRequests();
		if (requests.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.translatable("command.minecities.marriages.none"), false);
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.translatable("command.minecities.marriages", town.name())
				.withStyle(ChatFormatting.GOLD), false);
		for (int i = 0; i < requests.size(); i++) {
			var request = requests.get(i);
			int number = i + 1;
			context.getSource().sendSuccess(() -> Component.translatable("command.minecities.marriages.entry", number,
					town.resident(request.first()).map(Resident::name).orElse("?"),
					town.resident(request.second()).map(Resident::name).orElse("?")), false);
		}
		return requests.size();
	}

	private static int marry(CommandContext<CommandSourceStack> context, boolean accept) throws CommandSyntaxException {
		Town town = managedHere(context);
		int number = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "number");
		List<com.ajustor.minecities.town.MarriageRequest> requests = town.marriageRequests();
		if (number > requests.size()) {
			context.getSource().sendFailure(Component.translatable("command.minecities.marriages.unknown", number));
			return 0;
		}
		var request = requests.get(number - 1);
		if (accept) {
			Families.accept(context.getSource().getLevel(), town, request);
		} else {
			Families.refuse(context.getSource().getLevel(), town, request);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int immigrate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Town town = here(context);
		ServerLevel level = context.getSource().getServer().getLevel(town.dimension());
		if (level == null || TownService.immigrate(level, town).isEmpty()) {
			return 0;
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int feed(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		return setHunger(context, Hunger.MAX);
	}

	private static int starve(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		return setHunger(context, Hunger.HUNGRY - 1);
	}

	private static int setHunger(CommandContext<CommandSourceStack> context, float hunger) throws CommandSyntaxException {
		Town town = here(context);
		ServerLevel level = context.getSource().getServer().getLevel(town.dimension());
		int count = 0;
		for (UUID id : town.residents().keySet()) {
			if (level != null && level.getEntity(id) instanceof CitizenEntity citizen) {
				citizen.setHunger(hunger);
				count++;
			}
		}
		int done = count;
		context.getSource().sendSuccess(() -> Component.translatable("command.minecities.hunger.set", done), true);
		return count;
	}
}
