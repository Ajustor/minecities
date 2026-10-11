package com.ajustor.minecities.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Le citoyen, sur le squelette du joueur, avec un geste par action : par-dessus l'animation
 * ordinaire (marche, regard), les bras et la tête jouent ce qu'il fait.
 */
public class CitizenModel extends HumanoidModel<CitizenRenderer.State> {
	public CitizenModel(ModelPart root) {
		super(root);
	}

	@Override
	public void setupAnim(CitizenRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		switch (state.action) {
			case EAT -> {
				// La main porte la nourriture à la bouche, la tête s'incline.
				rightArm.xRot = -1.45f + Mth.sin(t * 0.8f) * 0.12f;
				rightArm.yRot = -0.45f;
				head.xRot = 0.2f + Mth.sin(t * 0.8f) * 0.05f;
			}
			case HOE -> {
				// Coups de houe vers le sol, à deux mains.
				float swing = Mth.sin(t * 0.45f);
				rightArm.xRot = -0.9f + swing * 0.8f;
				leftArm.xRot = -0.7f + swing * 0.6f;
				head.xRot = 0.35f;
			}
			case CHOP -> {
				// Grands coups de hache, bras au-dessus de la tête puis en bas.
				float swing = Math.abs(Mth.sin(t * 0.3f));
				rightArm.xRot = -2.5f + swing * 2.0f;
				leftArm.xRot = -2.4f + swing * 1.9f;
				rightArm.yRot = -0.25f;
				leftArm.yRot = 0.25f;
			}
			case FISH -> {
				// La canne tendue vers l'eau, qui frémit.
				rightArm.xRot = -1.1f + Mth.sin(t * 0.12f) * 0.06f;
				rightArm.yRot = -0.1f;
				head.xRot = 0.25f;
			}
			case BUILD -> {
				// Pose de blocs, à petits coups vifs.
				rightArm.xRot = -1.35f + Mth.sin(t * 0.9f) * 0.45f;
				leftArm.xRot = -0.4f;
			}
			case COOK -> {
				// Brasse devant le fumoir.
				rightArm.xRot = -1.0f + Mth.cos(t * 0.3f) * 0.15f;
				rightArm.yRot = Mth.sin(t * 0.3f) * 0.45f;
				leftArm.xRot = -0.8f;
				head.xRot = 0.3f;
			}
			case FEED -> {
				// Tend la nourriture aux bêtes, penché.
				rightArm.xRot = -1.2f + Mth.sin(t * 0.2f) * 0.1f;
				head.xRot = 0.45f;
			}
			case TALK -> {
				// Parle en gesticulant.
				rightArm.xRot = -0.55f + Mth.sin(t * 0.4f) * 0.3f;
				rightArm.zRot = 0.15f + Mth.cos(t * 0.4f) * 0.15f;
				head.xRot += Mth.sin(t * 0.6f) * 0.08f;
			}
			case CHEER -> {
				// Lève les bras de joie.
				rightArm.xRot = -2.9f + Mth.sin(t * 0.6f) * 0.2f;
				leftArm.xRot = -2.9f + Mth.cos(t * 0.6f) * 0.2f;
				rightArm.zRot = 0.35f;
				leftArm.zRot = -0.35f;
			}
			case NONE -> {
			}
		}
	}
}
