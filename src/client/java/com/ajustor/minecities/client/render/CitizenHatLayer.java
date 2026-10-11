package com.ajustor.minecities.client.render;

import com.ajustor.minecities.MineCities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;

/** Le couvre-chef du métier ({@link CitizenHatModel}), par-dessus le citoyen. */
public class CitizenHatLayer extends RenderLayer<CitizenRenderer.State, CitizenModel> {
	private static final Identifier TEXTURE = MineCities.id("textures/entity/citizen/hats.png");

	private final CitizenHatModel model = new CitizenHatModel(CitizenHatModel.create().bakeRoot());

	public CitizenHatLayer(RenderLayerParent<CitizenRenderer.State, CitizenModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, CitizenRenderer.State state,
			float yRot, float xRot) {
		// Un casque cache le couvre-chef.
		if (state.job < 0 || state.isInvisible || !state.headEquipment.isEmpty()) {
			return;
		}
		renderColoredCutoutModel(model, TEXTURE, poseStack, collector, light, state, -1, 1);
	}
}
