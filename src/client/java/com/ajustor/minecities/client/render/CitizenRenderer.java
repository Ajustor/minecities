package com.ajustor.minecities.client.render;

import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.citizen.CitizenEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Les citoyens, sur le squelette du joueur (avec sa surcouche), dans l'une de leurs tenues. */
public class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity, CitizenRenderer.State, HumanoidModel<CitizenRenderer.State>> {
	private static final Identifier[] TEXTURES = new Identifier[CitizenEntity.VARIANTS];

	static {
		for (int i = 0; i < TEXTURES.length; i++) {
			TEXTURES[i] = MineCities.id("textures/entity/citizen/citizen_" + i + ".png");
		}
	}

	public static class State extends HumanoidRenderState {
		public int variant;
	}

	public CitizenRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false),
				64, 64).bakeRoot()), 0.5f);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CitizenEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.variant = entity.variant();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURES[Math.floorMod(state.variant, TEXTURES.length)];
	}
}
