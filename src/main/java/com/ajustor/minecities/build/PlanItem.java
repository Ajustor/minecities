package com.ajustor.minecities.build;

import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.Towns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

import java.util.Optional;

/**
 * Le plan d'un bâtiment (brief 09), pris à l'hôtel de ville (onglet Plans) : il garde son schéma et
 * son style. Posé au sol, il montre le fantôme du bâtiment devant le joueur, porte vers lui ;
 * accroupi, il le tourne. On valide ou on abandonne depuis le chat ({@code /ville build confirm}).
 * Le plan sert autant de fois qu'on veut.
 */
public class PlanItem extends Item {
	public PlanItem(Properties properties) {
		super(properties);
	}

	/** Un plan de ce schéma, dans ce style. */
	public static ItemStack of(Blueprint blueprint, BuildStyle style) {
		ItemStack plan = new ItemStack(McItems.PLAN);
		CompoundTag tag = new CompoundTag();
		tag.putString("blueprint", blueprint.id().toString());
		tag.putString("style", style.serializedName());
		plan.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		plan.set(DataComponents.ITEM_NAME, Component.translatable("item.minecities.plan.named",
				blueprint.displayName(), Component.translatable(style.translationKey())));
		return plan;
	}

	/** Le schéma d'un plan. */
	public static Optional<Identifier> blueprint(ItemStack plan) {
		CustomData data = plan.get(DataComponents.CUSTOM_DATA);
		return data == null ? Optional.empty()
				: data.copyTag().getString("blueprint").map(Identifier::tryParse);
	}

	/** Le style d'un plan (les plaines s'il n'en a pas). */
	public static BuildStyle style(ItemStack plan) {
		CustomData data = plan.get(DataComponents.CUSTOM_DATA);
		return data == null ? BuildStyle.PLAINS : data.copyTag().getString("style")
				.flatMap(BuildStyle::fromSerializedName).orElse(BuildStyle.PLAINS);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive()) {
			Proposals.rotate(level, player).ifPresent(refusal -> refuse(player, refusal));
			return InteractionResult.SUCCESS;
		}
		ItemStack plan = context.getItemInHand();
		Optional<Blueprint> blueprint = blueprint(plan).flatMap(id -> Blueprints.get(level.getServer(), id));
		if (blueprint.isEmpty()) {
			refuse(player, Component.translatable("command.minecities.no_blueprint"));
			return InteractionResult.FAIL;
		}
		BlockPos standing = context.getClickedPos().above();
		Optional<Town> town = Towns.get(level.getServer()).at(level.dimension(), standing);
		if (town.isEmpty()) {
			refuse(player, Component.translatable("command.minecities.no_town"));
			return InteractionResult.FAIL;
		}
		Rotation rotation = BuildPlan.facing(player.getDirection().getOpposite());
		BlockPos origin = BuildPlan.originFacing(blueprint.get(), standing, rotation);
		Proposals.propose(level, player, town.get(), blueprint.get(), style(plan), origin, rotation)
				.ifPresent(refusal -> refuse(player, refusal));
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		// Dans le vide, accroupi : tourne le fantôme.
		if (player instanceof ServerPlayer server && level instanceof ServerLevel serverLevel
				&& player.isSecondaryUseActive()) {
			Proposals.rotate(serverLevel, server).ifPresent(refusal -> refuse(server, refusal));
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	private static void refuse(ServerPlayer player, Component refusal) {
		player.sendSystemMessage(refusal.copy().withStyle(ChatFormatting.RED));
	}
}
