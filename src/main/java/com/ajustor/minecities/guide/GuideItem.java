package com.ajustor.minecities.guide;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Le guide de la ville : en main, un clic l'ouvre. */
public class GuideItem extends Item {
	public GuideItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			Guide.opener.accept(player);
		}
		return InteractionResult.SUCCESS;
	}
}
