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
	/** Où se posent les marchandises sur le comptoir (en pixels, de gauche à droite vu du client). */
	private static final float[] SPOTS = {12, 0, -12};

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
		// Les marchandises, debout sur le comptoir, tournées vers le client.
		for (int i = 0; i < CaravanStall.SHOWN; i++) {
			if (state.wares[i].isEmpty()) {
				continue;
			}
			poseStack.pushPose();
			poseStack.translate(SPOTS[i] / 16f, CaravanStallModel.COUNTER / 16f + 0.25f, 0.05f);
			poseStack.scale(0.5f, 0.5f, 0.5f);
			state.wares[i].submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
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
