package com.ajustor.minecities.client.render;

import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.citizen.Action;
import com.ajustor.minecities.citizen.CitizenEntity;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
		/** Son métier (rang dans {@link com.ajustor.minecities.town.Job}), ou -1 : son couvre-chef. */
		public int job = -1;
	}

	public CitizenRenderer(EntityRendererProvider.Context context) {
		super(context, new CitizenModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false),
				64, 64).bakeRoot()), 0.5f);
		// L'armure qu'il porte (les gardes), puis le couvre-chef de son métier.
		addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(),
				part -> new HumanoidModel<State>(part)), context.getEquipmentRenderer()));
		addLayer(new CitizenHatLayer(this));
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
		state.job = entity.jobIndex();
	}

	/** À terre (MC-80) : couché sur le flanc, comme un mob qui tombe. */
	@Override
	protected void setupRotations(State state, com.mojang.blaze3d.vertex.PoseStack poseStack, float bodyRot,
			float scale) {
		super.setupRotations(state, poseStack, bodyRot, scale);
		if (state.action == Action.DOWN) {
			poseStack.translate(0, 0.15, 0);
			poseStack.mulPose(new org.joml.Matrix4f().rotationZ((float) Math.toRadians(90)));
		}
	}

	@Override
	protected boolean shouldShowName(CitizenEntity entity, double distanceSqr) {
		return entity.speech().isPresent() && distanceSqr < 32 * 32
				|| entity.trouble() != 0 && distanceSqr < 16 * 16 || super.shouldShowName(entity, distanceSqr);
	}

	/**
	 * Au-dessus de sa tête : ce qu'il dit ; sinon, s'il va mal, de quoi il souffre (faim, sans logis,
	 * malheureux, blessé) ; son nom quand on le regarde.
	 */
	@Override
	protected Component getNameTag(CitizenEntity entity) {
		if (entity.speech().isPresent()) {
			return Component.literal("« ").append(entity.speech().get()).append(" »").withStyle(ChatFormatting.WHITE);
		}
		Component name = super.getNameTag(entity);
		int trouble = entity.trouble();
		if (trouble == 0) {
			return name;
		}
		MutableComponent icons = Component.empty();
		for (int bit : new int[]{CitizenEntity.Trouble.HUNGRY, CitizenEntity.Trouble.HOMELESS,
				CitizenEntity.Trouble.UNHAPPY, CitizenEntity.Trouble.HURT, CitizenEntity.Trouble.SICK}) {
			if ((trouble & bit) != 0) {
				if (!icons.getSiblings().isEmpty()) {
					icons.append(" ");
				}
				icons.append(Component.translatable("hud.minecities.trouble." + bit).withStyle(ChatFormatting.RED));
			}
		}
		boolean looked = Minecraft.getInstance().crosshairPickEntity == entity;
		return looked && name != null ? Component.empty().append(name).append(" ").append(icons) : icons;
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURES[Math.floorMod(state.variant, TEXTURES.length)];
	}
}
