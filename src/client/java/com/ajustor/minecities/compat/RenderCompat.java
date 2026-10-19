package com.ajustor.minecities.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Les appels de rendu dont la forme change d'une version à l'autre (côté client ; voir {@link EntityCompat}). */
public final class RenderCompat {
	private RenderCompat() {
	}

	/** Dessine ce modèle, avec cette texture, à cette lumière. */
	public static <S> void submitModel(SubmitNodeCollector collector, Model<? super S> model, S state,
			PoseStack poseStack, Identifier texture, int light, int outlineColor) {
		//? if >=26.3 {
		/*collector.submitModel(model, state, poseStack, texture, light, OverlayTexture.NO_OVERLAY, outlineColor);
		*///?} else {
		collector.submitModel(model, state, poseStack, texture, light, OverlayTexture.NO_OVERLAY, outlineColor, null);
		//?}
	}
}
