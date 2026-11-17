package com.ajustor.minecities.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Un bloc posé par un joueur : un poste de bâtisseur posé hors de tout bâtiment devient un poste isolé
 * (la table d'artisan est un bloc vanilla : rien d'autre ne nous prévient de sa pose).
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
			at = @At("RETURN"))
	private void minecities$placed(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue().consumesAction() && context.getLevel() instanceof ServerLevel level) {
			com.ajustor.minecities.town.TownService.blockPlaced(level, context.getClickedPos());
		}
	}
}
