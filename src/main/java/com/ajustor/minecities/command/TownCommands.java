package com.ajustor.minecities.command;

import com.ajustor.minecities.citizen.CitizenEntity;
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
import net.minecraft.commands.arguments.EntityArgument;
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
				.then(Commands.literal("admin")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("immigrate").executes(TownCommands::immigrate))
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
