package com.ajustor.minecities.client.render;

import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.caravan.CaravanStall;
import com.ajustor.minecities.compat.RenderCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;

/** L'échoppe d'une caravane ({@link CaravanStallModel}), avec ses marchandises sur le comptoir. */
public class CaravanStallRenderer extends EntityRenderer<CaravanStall, CaravanStallRenderer.State> {
	private static final Identifier TEXTURE = MineCities.id("textures/entity/caravan.png");
	/**
	 * Où se montrent les marchandises (en pixels, dans le repère de CaravanStallModel) : pour chacune,
	 * trois exemplaires sur le comptoir, deux sur l'étagère, et dans une caisse de devant pour la
	 * première et la dernière. Chaque place : x, y, z, taille, à plat (1) ou debout (0).
	 */
	private static final float[][][] SPOTS = {
			{{23, 16, 1, 0.42f, 0}, {18, 16, 0, 0.42f, 0}, {13, 16, 1.5f, 0.42f, 0},
					{20, 21.6f, -12.5f, 0.38f, 0}, {20, 30.6f, -12.5f, 0.38f, 0}, {21, 6.2f, 10.5f, 0.5f, 1}, {19, 6.4f, 9.5f, 0.5f, 1}},
			{{5, 16, 1, 0.42f, 0}, {0, 16, 0, 0.42f, 0}, {-5, 16, 1.5f, 0.42f, 0},
					{2, 21.6f, -12.5f, 0.38f, 0}, {-2, 30.6f, -12.5f, 0.38f, 0}},
			{{-13, 16, 1, 0.42f, 0}, {-18, 16, 0, 0.42f, 0}, {-23, 16, 1.5f, 0.42f, 0},
					{-20, 21.6f, -12.5f, 0.38f, 0}, {-20, 30.6f, -12.5f, 0.38f, 0}, {-21, 6.2f, 10.5f, 0.5f, 1}, {-23, 6.4f, 11.5f, 0.5f, 1}}};

	public static class State extends EntityRenderState {
		public float yRot;
		public final ItemStackRenderState[] wares = new ItemStackRenderState[CaravanStall.SHOWN];

		public State() {
			for (int i = 0; i < wares.length; i++) {
				wares[i] = new ItemStackRenderState();
			}
		}
	}

	private final CaravanStallModel model = new CaravanStallModel(CaravanStallModel.create().bakeRoot());
	private final ItemModelResolver items;

	public CaravanStallRenderer(EntityRendererProvider.Context context) {
		super(context);
		items = context.getItemModelResolver();
		shadowRadius = 1.4f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CaravanStall stall, State state, float partialTicks) {
		super.extractRenderState(stall, state, partialTicks);
		state.yRot = stall.getYRot(partialTicks);
		for (int i = 0; i < CaravanStall.SHOWN; i++) {
			items.updateForNonLiving(state.wares[i], stall.ware(i), ItemDisplayContext.FIXED, stall);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-state.yRot)));
		RenderCompat.submitModel(collector, model, state, poseStack, TEXTURE, state.lightCoords, state.outlineColor);
		// Les marchandises, tournées vers le client : sur le comptoir, l'étagère, dans les caisses.
		for (int i = 0; i < CaravanStall.SHOWN; i++) {
			if (state.wares[i].isEmpty()) {
				continue;
			}
			for (float[] spot : SPOTS[i]) {
				poseStack.pushPose();
				poseStack.translate(spot[0] / 16f, spot[1] / 16f + (spot[4] > 0 ? 0 : spot[3] * 0.5f), spot[2] / 16f);
				if (spot[4] > 0) {
					poseStack.mulPose(new Matrix4f().rotationX((float) Math.toRadians(-90)));
				}
				poseStack.scale(spot[3], spot[3], spot[3]);
				state.wares[i].submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
				poseStack.popPose();
			}
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** L'auvent déborde : on le dessine même quand le pied de l'échoppe sort du champ. */
	@Override
	protected boolean affectedByCulling(CaravanStall stall) {
		return false;
	}
}
