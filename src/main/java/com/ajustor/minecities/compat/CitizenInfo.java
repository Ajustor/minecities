package com.ajustor.minecities.compat;

import com.ajustor.minecities.block.HousePlaqueBlock;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.citizen.Hunger;
import com.ajustor.minecities.town.Resident;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Ce que les mods d'infobulle (Jade) montrent d'un citoyen : sa faim, s'il est logé, et sa maladie
 * (vide s'il va bien). Le serveur le relève, le client l'affiche ; rien ici ne dépend de Jade, pour
 * que le mod se charge sans lui.
 */
public record CitizenInfo(Hunger.State hunger, boolean housed, String illness) {
	public static final StreamCodec<ByteBuf, CitizenInfo> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, info -> info.hunger().ordinal(),
			ByteBufCodecs.BOOL, CitizenInfo::housed,
			ByteBufCodecs.STRING_UTF8, CitizenInfo::illness,
			(hunger, housed, illness) -> new CitizenInfo(
					Hunger.State.values()[Math.clamp(hunger, 0, Hunger.State.values().length - 1)], housed, illness));

	/** L'état du citoyen, côté serveur : la ville seule sait où il loge. */
	public static CitizenInfo of(CitizenEntity citizen) {
		return new CitizenInfo(Hunger.state(citizen.hunger()), citizen.resident().flatMap(Resident::home).isPresent(),
				citizen.illness().map(com.ajustor.minecities.citizen.Disease::translationKey).orElse(""));
	}

	/** Les lignes de l'infobulle : la faim, le logement, puis la maladie. */
	public List<Component> lines() {
		ChatFormatting color = switch (hunger) {
			case STARVING -> ChatFormatting.RED;
			case HUNGRY -> ChatFormatting.GOLD;
			default -> ChatFormatting.GREEN;
		};
		List<Component> lines = new java.util.ArrayList<>(List.of(
				Component.translatable("jade.minecities.hunger",
						Component.translatable(hunger.translationKey()).withStyle(color)),
				housed ? Component.translatable("jade.minecities.housed").withStyle(ChatFormatting.GREEN)
						: Component.translatable("jade.minecities.homeless").withStyle(ChatFormatting.RED)));
		if (!illness.isEmpty()) {
			lines.add(Component.translatable("jade.minecities.illness", Component.translatable(illness))
					.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return lines;
	}

	/** La ligne d'une plaque de maison : l'état de son dernier relevé, comme la couleur de sa porte. */
	public static Component plaque(HousePlaqueBlock.Status status) {
		return switch (status) {
			case VALID -> Component.translatable("jade.minecities.plaque.valid").withStyle(ChatFormatting.GREEN);
			case INVALID -> Component.translatable("jade.minecities.plaque.invalid").withStyle(ChatFormatting.RED);
			case NONE -> Component.translatable("jade.minecities.plaque.none").withStyle(ChatFormatting.GRAY);
		};
	}
}
