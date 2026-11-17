package com.ajustor.minecities.field;

import com.ajustor.minecities.town.Field;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.Towns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

import java.util.Optional;

/**
 * Le piquet de champ : il trace les champs des fermiers. Un clic sur le sol pose un coin, un clic sur le
 * coin opposé trace le champ et ouvre le choix de la culture ; un clic dans un champ rouvre ce choix (ou
 * le supprime). Accroupi, un clic oublie le premier coin. Tenu en main, il montre les champs de la ville.
 */
public class FieldMarkerItem extends Item {
	public FieldMarkerItem(Properties properties) {
		super(properties);
	}

	/** Le premier coin posé, s'il y en a un. */
	static Optional<BlockPos> corner(ItemStack marker) {
		CustomData data = marker.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return Optional.empty();
		}
		CompoundTag tag = data.copyTag();
		Optional<Integer> x = tag.getInt("x");
		Optional<Integer> y = tag.getInt("y");
		Optional<Integer> z = tag.getInt("z");
		if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new BlockPos(x.get(), y.get(), z.get()));
	}

	private static void setCorner(ItemStack marker, BlockPos pos) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("x", pos.getX());
		tag.putInt("y", pos.getY());
		tag.putInt("z", pos.getZ());
		marker.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	private static void clearCorner(ItemStack marker) {
		marker.remove(DataComponents.CUSTOM_DATA);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack marker = context.getItemInHand();
		BlockPos pos = context.getClickedPos();
		if (player.isSecondaryUseActive()) {
			forget(player, marker);
			return InteractionResult.SUCCESS;
		}
		Optional<Town> town = Towns.get(level.getServer()).at(level.dimension(), pos);
		if (town.isEmpty() || !town.get().canManage(player.getUUID())) {
			tell(player, "message.minecities.field.outside", ChatFormatting.RED);
			return InteractionResult.SUCCESS;
		}
		// Un clic dans un champ : son choix de culture.
		Optional<Field> existing = town.get().fieldAt(pos);
		if (existing.isPresent()) {
			clearCorner(marker);
			FieldNetwork.open(player, town.get(), existing.get());
			return InteractionResult.SUCCESS;
		}
		Optional<BlockPos> first = corner(marker);
		if (first.isEmpty()) {
			setCorner(marker, pos);
			tell(player, "message.minecities.field.first", ChatFormatting.GOLD);
			return InteractionResult.SUCCESS;
		}
		clearCorner(marker);
		Field field = Field.between(first.get(), pos, Field.Crop.ANY);
		Optional<String> problem = problem(level, town.get(), field);
		if (problem.isPresent()) {
			player.sendSystemMessage(Component.translatable(problem.get(), Field.MAX_SIDE).withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		town.get().addField(field);
		Towns.get(level.getServer()).changed();
		player.sendSystemMessage(Component.translatable("message.minecities.field.created", field.width(), field.depth())
				.withStyle(ChatFormatting.GREEN));
		FieldNetwork.open(player, town.get(), field);
		return InteractionResult.SUCCESS;
	}

	/** Ce qui empêche de tracer ce champ : trop grand, hors de la ville, ou sur un autre champ. */
	public static Optional<String> problem(ServerLevel level, Town town, Field field) {
		if (field.width() > Field.MAX_SIDE || field.depth() > Field.MAX_SIDE) {
			return Optional.of("message.minecities.field.too_big");
		}
		for (int x = field.min().getX(); x <= field.max().getX(); x++) {
			for (int z = field.min().getZ(); z <= field.max().getZ(); z++) {
				if (!town.contains(level.dimension(), new BlockPos(x, field.min().getY(), z))) {
					return Optional.of("message.minecities.field.outside");
				}
			}
		}
		if (town.fields().stream().anyMatch(field::overlaps)) {
			return Optional.of("message.minecities.field.overlap");
		}
		return Optional.empty();
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		// Dans le vide : accroupi, on oublie le coin ; sinon, on rappelle comment faire.
		if (player instanceof ServerPlayer server) {
			ItemStack marker = player.getItemInHand(hand);
			if (player.isSecondaryUseActive()) {
				forget(server, marker);
			} else {
				tell(server, corner(marker).isPresent() ? "message.minecities.field.second"
						: "message.minecities.field.help", ChatFormatting.GRAY);
			}
		}
		return InteractionResult.SUCCESS;
	}

	private static void forget(ServerPlayer player, ItemStack marker) {
		if (corner(marker).isPresent()) {
			clearCorner(marker);
			tell(player, "message.minecities.field.forgotten", ChatFormatting.GRAY);
		}
	}

	private static void tell(ServerPlayer player, String key, ChatFormatting style) {
		player.sendSystemMessage(Component.translatable(key).withStyle(style));
	}
}
