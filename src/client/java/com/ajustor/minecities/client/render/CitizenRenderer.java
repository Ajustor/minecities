package com.ajustor.minecities.client.render;

import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.citizen.Action;
import com.ajustor.minecities.citizen.CitizenEntity;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Les citoyens, sur le squelette du joueur (avec sa surcouche), dans l'une de leurs tenues, avec le
 * geste de ce qu'ils font ({@link CitizenModel}) ; les enfants sont plus petits (attribut d'échelle).
 */
public class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity, CitizenRenderer.State, CitizenModel> {
	private static final Identifier[] TEXTURES = new Identifier[CitizenEntity.VARIANTS];

	static {
		for (int i = 0; i < TEXTURES.length; i++) {
			TEXTURES[i] = MineCities.id("textures/entity/citizen/citizen_" + i + ".png");
		}
	}

	public static class State extends HumanoidRenderState {
		public int variant;
		public Action action = Action.NONE;
	}

	public CitizenRenderer(EntityRendererProvider.Context context) {
		super(context, new CitizenModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false),
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
		state.action = entity.action();
	}

	@Override
	protected boolean shouldShowName(CitizenEntity entity, double distanceSqr) {
		return entity.speech().isPresent() && distanceSqr < 32 * 32 || super.shouldShowName(entity, distanceSqr);
	}

	@Override
	protected Component getNameTag(CitizenEntity entity) {
		return entity.speech().<Component>map(line -> Component.literal("« ").append(line).append(" »")
				.withStyle(ChatFormatting.WHITE)).orElseGet(() -> super.getNameTag(entity));
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURES[Math.floorMod(state.variant, TEXTURES.length)];
	}
}
