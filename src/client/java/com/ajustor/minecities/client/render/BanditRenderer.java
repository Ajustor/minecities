package com.ajustor.minecities.client.render;

import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.caravan.Bandit;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Le bandit (MC-80), sur le squelette du joueur : la surcouche fait sa capuche et le haut de sa cape,
 * le foulard rouge lui couvre le nez (textures/entity/bandit.png, générée par tools/textures/generate.py).
 * Il tient sa hache ou son épée en main.
 */
public class BanditRenderer extends HumanoidMobRenderer<Bandit, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURE = MineCities.id("textures/entity/bandit.png");

	public BanditRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false),
				64, 64).bakeRoot()), 0.5f);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}
}
