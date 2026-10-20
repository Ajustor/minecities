package com.ajustor.minecities.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

// Généré par tools/models/caravan.py : modifier le script et le relancer, pas ce fichier.

/**
 * L'échoppe d'une caravane (MC-80) : un long comptoir de planches sous un auvent rayé rouge et blanc,
 * deux caisses ouvertes devant, une enseigne suspendue, et derrière le marchand une étagère à deux
 * rangs, des caisses et un tonneau. Les marchandises (sur le comptoir, dans les caisses, sur
 * l'étagère) sont dessinées à part, de vrais objets ({@link CaravanStallRenderer}).
 *
 * <p>Mêmes conventions que {@link CaravanCartModel} (coordonnées de Blockbench, le devant vers +z, où
 * se tient le client), et même texture.
 */
public class CaravanStallModel extends EntityModel<CaravanStallRenderer.State> {
	static final int TILE_WIDTH = CaravanCartModel.TILE_WIDTH;
	static final int TILE_HEIGHT = CaravanCartModel.TILE_HEIGHT;

	public CaravanStallModel(ModelPart root) {
		super(root, RenderTypes::entityCutout);
	}

	private static void cube(PartDefinition part, String name, CaravanCartModel.Tile tile, float x, float y, float z,
			float w, float h, float d, float px, float py, float pz, float rx, float ry, float rz) {
		CaravanCartModel.cube(part, name, tile, x, y, z, w, h, d, px, py, pz, rx, ry, rz);
	}

	public static LayerDefinition create() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition g_stall = root.addOrReplaceChild("stall", CubeListBuilder.create(), PartPose.offset(0, 0, 0));
		cube(g_stall, "counter", CaravanCartModel.Tile.WOOD, 0, 0, 0, 56, 14, 12, -28, 0, -6, 0, 0, 0);
		cube(g_stall, "counter_top", CaravanCartModel.Tile.DARK, 0, 0, 0, 58, 2, 14, -29, 14, -7, 0, 0, 0);
		cube(g_stall, "plinth", CaravanCartModel.Tile.DARK, 0, 0, 0, 57, 2, 1, -28.5f, 0, 5.5f, 0, 0, 0);
		cube(g_stall, "plank_0", CaravanCartModel.Tile.DARK, 0, 0, 0, 1, 12, 0.6f, -18.5f, 2, 5.6f, 0, 0, 0);
		cube(g_stall, "plank_1", CaravanCartModel.Tile.DARK, 0, 0, 0, 1, 12, 0.6f, -6.5f, 2, 5.6f, 0, 0, 0);
		cube(g_stall, "plank_2", CaravanCartModel.Tile.DARK, 0, 0, 0, 1, 12, 0.6f, 5.5f, 2, 5.6f, 0, 0, 0);
		cube(g_stall, "plank_3", CaravanCartModel.Tile.DARK, 0, 0, 0, 1, 12, 0.6f, 17.5f, 2, 5.6f, 0, 0, 0);
		cube(g_stall, "crate_front_l", CaravanCartModel.Tile.CRATE, 0, 0, 0, 10, 6, 7, -26, 0, 7, 0, 0, 0);
		cube(g_stall, "crate_front_r", CaravanCartModel.Tile.CRATE, 0, 0, 0, 10, 6, 7, 16, 0, 7, 0, 0, 0);
		cube(g_stall, "post_front_l", CaravanCartModel.Tile.DARK, 0, 0, 0, 2, 38, 2, 27, 0, 8, 0, 0, 0);
		cube(g_stall, "post_back_l", CaravanCartModel.Tile.DARK, 0, 0, 0, 2, 44, 2, 27, 0, -16, 0, 0, 0);
		cube(g_stall, "post_front_r", CaravanCartModel.Tile.DARK, 0, 0, 0, 2, 38, 2, -29, 0, 8, 0, 0, 0);
		cube(g_stall, "post_back_r", CaravanCartModel.Tile.DARK, 0, 0, 0, 2, 44, 2, -29, 0, -16, 0, 0, 0);
		cube(g_stall, "awning_0", CaravanCartModel.Tile.RED, -29, 0, 0, 7.25f, 1, 30, 0, 44, -17, 13, 0, 0);
		cube(g_stall, "valance_0", CaravanCartModel.Tile.RED, 0, 0, 0, 7.25f, 3, 0.6f, -29, 34.5f, 12, 0, 0, 0);
		cube(g_stall, "awning_1", CaravanCartModel.Tile.CANVAS, -21.75f, 0, 0, 7.25f, 1, 30, 0, 44, -17, 13, 0, 0);
		cube(g_stall, "valance_1", CaravanCartModel.Tile.CANVAS, 0, 0, 0, 7.25f, 3, 0.6f, -21.75f, 34.5f, 12, 0, 0, 0);
		cube(g_stall, "awning_2", CaravanCartModel.Tile.RED, -14.5f, 0, 0, 7.25f, 1, 30, 0, 44, -17, 13, 0, 0);
		cube(g_stall, "valance_2", CaravanCartModel.Tile.RED, 0, 0, 0, 7.25f, 3, 0.6f, -14.5f, 34.5f, 12, 0, 0, 0);
		cube(g_stall, "awning_3", CaravanCartModel.Tile.CANVAS, -7.25f, 0, 0, 7.25f, 1, 30, 0, 44, -17, 13, 0, 0);
		cube(g_stall, "valance_3", CaravanCartModel.Tile.CANVAS, 0, 0, 0, 7.25f, 3, 0.6f, -7.25f, 34.5f, 12, 0, 0, 0);
		cube(g_stall, "awning_4", CaravanCartModel.Tile.RED, 0, 0, 0, 7.25f, 1, 30, 0, 44, -17, 13, 0, 0);
		cube(g_stall, "valance_4", CaravanCartModel.Tile.RED, 0, 0, 0, 7.25f, 3, 0.6f, 0, 34.5f, 12, 0, 0, 0);
		cube(g_stall, "awning_5", CaravanCartModel.Tile.CANVAS, 7.25f, 0, 0, 7.25f, 1, 30, 0, 44, -17, 13, 0, 0);
		cube(g_stall, "valance_5", CaravanCartModel.Tile.CANVAS, 0, 0, 0, 7.25f, 3, 0.6f, 7.25f, 34.5f, 12, 0, 0, 0);
		cube(g_stall, "awning_6", CaravanCartModel.Tile.RED, 14.5f, 0, 0, 7.25f, 1, 30, 0, 44, -17, 13, 0, 0);
		cube(g_stall, "valance_6", CaravanCartModel.Tile.RED, 0, 0, 0, 7.25f, 3, 0.6f, 14.5f, 34.5f, 12, 0, 0, 0);
		cube(g_stall, "awning_7", CaravanCartModel.Tile.CANVAS, 21.75f, 0, 0, 7.25f, 1, 30, 0, 44, -17, 13, 0, 0);
		cube(g_stall, "valance_7", CaravanCartModel.Tile.CANVAS, 0, 0, 0, 7.25f, 3, 0.6f, 21.75f, 34.5f, 12, 0, 0, 0);
		cube(g_stall, "shelf_low", CaravanCartModel.Tile.WOOD, 0, 0, 0, 54, 1.5f, 5, -27, 20, -15, 0, 0, 0);
		cube(g_stall, "shelf_high", CaravanCartModel.Tile.WOOD, 0, 0, 0, 54, 1.5f, 5, -27, 29, -15, 0, 0, 0);
		cube(g_stall, "crate_a", CaravanCartModel.Tile.CRATE, 0, 0, 0, 9, 8, 7, -26, 0, -14, 0, 0, 0);
		cube(g_stall, "crate_b", CaravanCartModel.Tile.CRATE, 0, 0, 0, 6, 5, 5, -25, 8, -13, 0, 0, 0);
		cube(g_stall, "barrel", CaravanCartModel.Tile.BARREL, 0, 0, 0, 7, 10, 7, 18, 0, -14, 0, 0, 0);
		cube(g_stall, "barrel_hoop", CaravanCartModel.Tile.IRON, 0, 0, 0, 7.6f, 1, 7.6f, 17.7f, 6, -14.3f, 0, 0, 0);
		cube(g_stall, "sign_chain_l", CaravanCartModel.Tile.IRON, 0, 0, 0, 0.6f, 4, 0.6f, -7, 31, 10.5f, 0, 0, 0);
		cube(g_stall, "sign_chain_r", CaravanCartModel.Tile.IRON, 0, 0, 0, 0.6f, 4, 0.6f, 6.4f, 31, 10.5f, 0, 0, 0);
		cube(g_stall, "sign", CaravanCartModel.Tile.WOOD, 0, 0, 0, 18, 5, 1, -9, 26, 10.2f, 0, 0, 0);
		cube(g_stall, "sign_mark", CaravanCartModel.Tile.GOLD, 0, 0, 0, 4, 2, 0.3f, -2, 27.5f, 11.1f, 0, 0, 0);
		return LayerDefinition.create(mesh, CaravanCartModel.TEXTURE_WIDTH, CaravanCartModel.TEXTURE_HEIGHT);
	}
}
