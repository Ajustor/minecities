package com.ajustor.minecities.command;

import com.ajustor.minecities.build.Blueprint;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildPlan;
import com.ajustor.minecities.build.BuildStyle;
import com.ajustor.minecities.build.Builds;
import com.ajustor.minecities.build.Construction;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Festivals;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
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
				.then(Commands.literal("blueprints").executes(TownCommands::blueprints))
				.then(Commands.literal("build")
						.then(Commands.literal("cancel").executes(TownCommands::cancelBuild))
						.then(Commands.argument("blueprint", IdentifierArgument.id())
								.suggests((c, b) -> SharedSuggestionProvider.suggestResource(
										Blueprints.all(c.getSource().getServer()).keySet(), b))
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
					+ refusal.get().name().toLowerCase(), Festivals.COST));
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

	private static int blueprints(CommandContext<CommandSourceStack> context) {
		var all = Blueprints.all(context.getSource().getServer());
		context.getSource().sendSuccess(() -> Component.translatable("command.minecities.blueprints", all.size())
				.withStyle(ChatFormatting.GOLD), false);
		for (Blueprint blueprint : all.values()) {
			context.getSource().sendSuccess(() -> Component.translatable("command.minecities.blueprints.entry",
					Component.translatable(blueprint.translationKey()), blueprint.id().toString(),
					blueprint.width(), blueprint.depth(), blueprint.height(),
					Component.translatable("blueprint.minecities.kind." + blueprint.kind())), false);
		}
		return all.size();
	}

	private static int build(CommandContext<CommandSourceStack> context, BuildStyle style) throws CommandSyntaxException {
		Town town = managedHere(context);
		ServerPlayer player = context.getSource().getPlayerOrException();
		ServerLevel level = context.getSource().getLevel();
		Identifier id = IdentifierArgument.getId(context, "blueprint");
		Blueprint blueprint = Blueprints.get(context.getSource().getServer(), id).orElseThrow(NO_BLUEPRINT::create);
		Rotation rotation = BuildPlan.facing(player.getDirection().getOpposite());
		BlockPos origin = BuildPlan.originFacing(blueprint, player.blockPosition(), rotation);
		Optional<Component> refusal = Builds.order(level, town, blueprint, style, origin, rotation);
		if (refusal.isPresent()) {
			context.getSource().sendFailure(refusal.get());
			return 0;
		}
		BlockPos[] bounds = BuildPlan.bounds(blueprint, origin, rotation);
		Builds.outline(level, player, bounds[0], bounds[1]);
		context.getSource().sendSuccess(() -> Component.translatable("command.minecities.build.ordered",
				Component.translatable(blueprint.translationKey()), Component.translatable(style.translationKey()),
				town.name()).withStyle(ChatFormatting.GOLD), false);
		Builds.describeMaterials(level, town, BuildPlan.of(blueprint, style, origin, rotation), player);
		if (town.workers(Job.BUILDER) == 0) {
			context.getSource().sendSuccess(() -> Component.translatable("command.minecities.build.no_builder")
					.withStyle(ChatFormatting.YELLOW), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int cancelBuild(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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
