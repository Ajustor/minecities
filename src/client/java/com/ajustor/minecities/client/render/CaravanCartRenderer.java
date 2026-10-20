package com.ajustor.minecities.client.render;

import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.caravan.CaravanCart;
import com.ajustor.minecities.compat.RenderCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/** Le chariot d'une caravane ({@link CaravanCartModel}) ; la nuit, avec son camp. */
public class CaravanCartRenderer extends EntityRenderer<CaravanCart, CaravanCartRenderer.State> {
	private static final Identifier TEXTURE = MineCities.id("textures/entity/caravan.png");

	public static class State extends EntityRenderState {
		public float yRot;
		public boolean camping;
		/** Le chemin parcouru, en blocs : les roues tournent d'autant. */
		public float rolled;
	}

	/** La lumière la plus vive : le feu du camp luit dans la nuit. */
	private static final int FULL_BRIGHT = 0xF000F0;

	private final CaravanCartModel model = new CaravanCartModel(CaravanCartModel.create().bakeRoot(), false);
	private final CaravanCartModel fire = new CaravanCartModel(CaravanCartModel.create().bakeRoot(), true);

	public CaravanCartRenderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 1.2f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CaravanCart cart, State state, float partialTicks) {
		super.extractRenderState(cart, state, partialTicks);
		state.yRot = cart.getYRot(partialTicks);
		state.camping = cart.camping();
		state.rolled = cart.rolled(partialTicks);
	}

	/** Le camp déborde loin du chariot : on le dessine même quand le chariot sort du champ. */
	@Override
	protected boolean affectedByCulling(CaravanCart cart) {
		return false;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		// Le modèle regarde vers +z, comme une entité de lacet nul.
		poseStack.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-state.yRot)));
		RenderCompat.submitModel(collector, model, state, poseStack, TEXTURE, state.lightCoords, state.outlineColor);
		if (state.camping) {
			RenderCompat.submitModel(collector, fire, state, poseStack, TEXTURE, FULL_BRIGHT, state.outlineColor);
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
