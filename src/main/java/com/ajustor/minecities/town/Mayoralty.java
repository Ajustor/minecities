package com.ajustor.minecities.town;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * Ce que décide le maire pour sa mairie (MC-75) : renommer la ville, la transmettre, nommer ou retirer
 * des adjoints, proposer ou rompre un accord de commerce. Les commandes et l'écran passent par ici ;
 * chaque décision renvoie son refus, s'il y en a un.
 */
public final class Mayoralty {
	private Mayoralty() {
	}

	/** Renomme la ville ; le nom doit être libre. */
	public static Optional<Component> rename(MinecraftServer server, Town town, String wanted) {
		String name = wanted.trim();
		Towns towns = Towns.get(server);
		if (name.isEmpty() || towns.byName(name).filter(t -> t != town).isPresent()) {
			return Optional.of(Component.translatable("command.minecities.rename.taken", name));
		}
		String before = town.name();
		town.rename(name);
		towns.changed();
		server.getPlayerList().broadcastSystemMessage(Component.translatable("command.minecities.rename.done", before,
				name).withStyle(ChatFormatting.GOLD), false);
		return Optional.empty();
	}

	/** Transmet la mairie à un joueur de la ville (pas pendant une élection) ; l'ancien maire reste habitant. */
	public static Optional<Component> transfer(MinecraftServer server, Town town, UUID successor, String name) {
		if (town.election().phase() != Election.Phase.NONE) {
			return Optional.of(Component.translatable("command.minecities.mayor.election"));
		}
		if (!town.isMember(successor) || successor.equals(town.mayor())) {
			return Optional.of(Component.translatable("command.minecities.not_member", name, town.name()));
		}
		town.membership().addResident(town.mayor(), town.mayorName());
		town.setMayor(successor, name);
		Towns.get(server).changed();
		server.getPlayerList().broadcastSystemMessage(Component.translatable("command.minecities.mayor.done", name,
				town.name()).withStyle(ChatFormatting.GOLD), false);
		return Optional.empty();
	}

	/** Nomme (ou retire) un adjoint, joueur de la ville. */
	public static Optional<Component> deputy(MinecraftServer server, Town town, UUID player, String name, boolean add) {
		if (add && !town.isMember(player)) {
			return Optional.of(Component.translatable("command.minecities.not_member", name, town.name()));
		}
		boolean changed = add ? town.addDeputy(player) : town.removeDeputy(player);
		if (!changed) {
			return Optional.of(Component.translatable("command.minecities.deputy.unchanged"));
		}
		Towns.get(server).changed();
		String key = add ? "command.minecities.deputy.added" : "command.minecities.deputy.removed";
		ServerPlayer target = server.getPlayerList().getPlayer(player);
		if (target != null) {
			target.sendSystemMessage(Component.translatable(key, name, town.name()).withStyle(ChatFormatting.GOLD));
		}
		TownService.notify(server, town, Component.translatable(key, name, town.name()));
		return Optional.empty();
	}

	/** Propose un accord de commerce à cette ville, ou rompt celui qu'on a. */
	public static Optional<Component> trade(MinecraftServer server, Town town, Town other) {
		if (other.id().equals(town.id())) {
			return Optional.of(Component.translatable("command.minecities.no_town"));
		}
		if (town.partners().contains(other.id())) {
			town.removePartner(other.id());
			TownService.notify(server, town, Component.translatable("message.minecities.trade.ended", town.name(),
					other.name()));
			TownService.notify(server, other, Component.translatable("message.minecities.trade.ended", town.name(),
					other.name()));
		} else {
			town.addPartner(other.id());
			String key = Trade.trading(town, other) ? "message.minecities.trade.open" : "message.minecities.trade.offered";
			TownService.notify(server, town, Component.translatable(key, town.name(), other.name()));
			TownService.notify(server, other, Component.translatable(key, town.name(), other.name()));
		}
		Towns.get(server).changed();
		return Optional.empty();
	}
}
