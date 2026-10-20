package com.ajustor.minecities.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * L'échoppe d'une caravane (MC-80), dessinée dans Blockbench : un comptoir de planches sous un auvent
 * rayé rouge et blanc, une enseigne suspendue, et derrière le marchand des caisses, un tonneau et une
 * étagère de pots. Les marchandises posées sur le comptoir sont dessinées à part (de vrais objets).
 *
 * <p>Mêmes conventions que {@link CaravanCartModel} (coordonnées de Blockbench, le devant vers +z, où
 * se tient le client), et même texture.
 */
public class CaravanStallModel extends EntityModel<CaravanStallRenderer.State> {
	/** La hauteur du comptoir, en pixels : les marchandises y sont posées. */
	public static final float COUNTER = 16;

	public CaravanStallModel(ModelPart root) {
		super(root, RenderTypes::entityCutout);
	}

	private static void cube(PartDefinition part, String name, CaravanCartModel.Tile tile, float x, float y, float z,
			float w, float h, float d, float rx, float px, float py, float pz) {
		CubeListBuilder box = CubeListBuilder.create().texOffs((tile.ordinal() % 4) * 128, (tile.ordinal() / 4) * 64)
				.addBox(x - px, y - py, z - pz, w, h, d);
		part.addOrReplaceChild(name, box, PartPose.offsetAndRotation(px, py, pz, (float) Math.toRadians(rx), 0, 0));
	}

	private static void cube(PartDefinition part, String name, CaravanCartModel.Tile tile, float x, float y, float z,
			float w, float h, float d) {
		cube(part, name, tile, x, y, z, w, h, d, 0, x, y, z);
	}

	public static LayerDefinition create() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition stall = mesh.getRoot();
		// Le comptoir.
		cube(stall, "counter", CaravanCartModel.Tile.WOOD, -20, 0, -5, 40, 14, 10);
		cube(stall, "counter_top", CaravanCartModel.Tile.DARK, -21, 14, -6, 42, 2, 12);
		cube(stall, "plinth", CaravanCartModel.Tile.DARK, -20.5f, 0, 4.5f, 41, 2, 1);
		float[] planks = {-12, 0, 12};
		for (int i = 0; i < planks.length; i++) {
			cube(stall, "plank_" + i, CaravanCartModel.Tile.DARK, planks[i] - 0.5f, 2, 4.6f, 1, 12, 0.6f);
		}
		// Les poteaux : l'auvent penche vers l'avant.
		for (String side : new String[]{"l", "r"}) {
			float x = side.equals("l") ? 19 : -21;
			cube(stall, "post_front_" + side, CaravanCartModel.Tile.DARK, x, 0, 6, 2, 36, 2);
			cube(stall, "post_back_" + side, CaravanCartModel.Tile.DARK, x, 0, -14, 2, 42, 2);
		}
		// L'auvent rayé et son lambrequin.
		for (int k = 0; k < 6; k++) {
			CaravanCartModel.Tile tile = k % 2 == 0 ? CaravanCartModel.Tile.RED : CaravanCartModel.Tile.CANVAS;
			float x = -21 + k * 7;
			cube(stall, "awning_" + k, tile, x, 42, -15, 7, 1, 26, 13, 0, 42, -15);
			cube(stall, "valance_" + k, tile, x, 33.5f, 10, 7, 3, 0.6f);
		}
		// Derrière le comptoir : caisses, tonneau, une étagère de pots.
		cube(stall, "crate_a", CaravanCartModel.Tile.CRATE, -18, 0, -13, 8, 8, 7);
		cube(stall, "crate_b", CaravanCartModel.Tile.CRATE, -17, 8, -12, 6, 5, 5);
		cube(stall, "barrel", CaravanCartModel.Tile.BARREL, 9, 0, -13, 7, 10, 7);
		cube(stall, "barrel_hoop", CaravanCartModel.Tile.IRON, 8.7f, 6, -13.3f, 7.6f, 1, 7.6f);
		cube(stall, "shelf", CaravanCartModel.Tile.WOOD, -19, 22, -14, 38, 1.5f, 4);
		cube(stall, "jar_a", CaravanCartModel.Tile.GOLD, -15, 23.5f, -13.5f, 3, 3, 3);
		cube(stall, "jar_b", CaravanCartModel.Tile.CANVAS, -6, 23.5f, -13.5f, 4, 4, 3);
		cube(stall, "jar_c", CaravanCartModel.Tile.CRATE, 5, 23.5f, -13.5f, 5, 3, 3);
		// L'enseigne, suspendue sous l'auvent.
		cube(stall, "sign_chain_l", CaravanCartModel.Tile.IRON, -6, 30, 8.5f, 0.6f, 4, 0.6f);
		cube(stall, "sign_chain_r", CaravanCartModel.Tile.IRON, 5.4f, 30, 8.5f, 0.6f, 4, 0.6f);
		cube(stall, "sign", CaravanCartModel.Tile.WOOD, -8, 25, 8.2f, 16, 5, 1);
		cube(stall, "sign_mark", CaravanCartModel.Tile.GOLD, -2, 26.5f, 9.1f, 4, 2, 0.3f);
		return LayerDefinition.create(mesh, CaravanCartModel.TEXTURE_WIDTH, CaravanCartModel.TEXTURE_HEIGHT);
	}
}
