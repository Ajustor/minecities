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
 * Le chariot d'une caravane (MC-80), dessiné dans Blockbench : plateau, ridelles, bâche sur trois
 * arceaux rouges, caisses et tonneau à l'arrière, banc du cocher, lanterne, quatre roues à rayons et
 * les brancards du cheval vers l'avant. La nuit, le camp s'y ajoute derrière : feu entouré de pierres
 * (là où {@link com.ajustor.minecities.caravan.CaravanCart#FIRE_Z} fait monter la fumée), tente avec
 * sa couche, bûche où s'assoit le marchand, une caisse déchargée.
 *
 * <p>Les coordonnées sont celles de Blockbench (en pixels, y vers le haut, l'avant vers +z) : le
 * modèle est rendu sans le retournement des modèles vanilla. La texture (textures/entity/caravan.png,
 * générée par tools/textures/generate.py) est faite de pavés d'une couleur ; chaque cube prend le sien.
 */
public class CaravanCartModel extends EntityModel<CaravanCartRenderer.State> {
	/** La texture : 512 x 256, des pavés de 128 x 64. */
	public static final int TEXTURE_WIDTH = 512;
	public static final int TEXTURE_HEIGHT = 256;

	/** Les couleurs des pavés, dans l'ordre de la texture (voir tools/textures/generate.py). */
	enum Tile { WOOD, DARK, CANVAS, RED, IRON, CRATE, BARREL, GOLD, FIRE, FLAME, STONE, TENT }

	private final ModelPart cart;
	private final ModelPart camp;
	private final ModelPart fire;
	private final ModelPart flame;
	/** Ce modèle ne montre que le feu du camp (dessiné à pleine lumière), ou tout le reste. */
	private final boolean glow;

	public CaravanCartModel(ModelPart root, boolean glow) {
		super(root, RenderTypes::entityCutout);
		cart = root.getChild("cart");
		camp = root.getChild("camp");
		fire = camp.getChild("fire");
		flame = camp.getChild("flame");
		this.glow = glow;
	}

	@Override
	public void setupAnim(CaravanCartRenderer.State state) {
		super.setupAnim(state);
		camp.visible = state.camping;
		cart.visible = !glow;
		camp.getAllParts().forEach(part -> part.skipDraw = glow);
		camp.skipDraw = true;
		fire.skipDraw = !glow;
		flame.skipDraw = !glow;
	}

	/** Un cube de cette couleur, de (x, y, z) et de cette taille, tourné (en degrés) autour du pivot. */
	private static void cube(PartDefinition part, String name, Tile tile, float x, float y, float z, float w, float h,
			float d, float rx, float ry, float rz, float px, float py, float pz) {
		CubeListBuilder box = CubeListBuilder.create().texOffs((tile.ordinal() % 4) * 128, (tile.ordinal() / 4) * 64)
				.addBox(x - px, y - py, z - pz, w, h, d);
		part.addOrReplaceChild(name, box, PartPose.offsetAndRotation(px, py, pz, (float) Math.toRadians(rx),
				(float) Math.toRadians(ry), (float) Math.toRadians(rz)));
	}

	private static void cube(PartDefinition part, String name, Tile tile, float x, float y, float z, float w, float h,
			float d) {
		cube(part, name, tile, x, y, z, w, h, d, 0, 0, 0, x, y, z);
	}

	public static LayerDefinition create() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		cart(root.addOrReplaceChild("cart", CubeListBuilder.create(), PartPose.ZERO));
		camp(root.addOrReplaceChild("camp", CubeListBuilder.create(), PartPose.ZERO));
		return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
	}

	private static void cart(PartDefinition cart) {
		cube(cart, "bed", Tile.DARK, -11, 10, -16, 22, 2, 32);
		cube(cart, "floor", Tile.WOOD, -10, 12, -15, 20, 1, 30);
		cube(cart, "side_l", Tile.WOOD, 10, 12, -16, 1, 6, 32);
		cube(cart, "side_r", Tile.WOOD, -11, 12, -16, 1, 6, 32);
		cube(cart, "back", Tile.WOOD, -10, 12, -16, 20, 6, 1);
		cube(cart, "front", Tile.WOOD, -10, 12, 15, 20, 6, 1);
		// La bâche : deux pans un peu penchés vers l'intérieur et un toit, sur trois arceaux.
		cube(cart, "cover_l", Tile.CANVAS, 9, 18, -14, 1, 10, 28, 0, 0, -14, 10, 18, 0);
		cube(cart, "cover_r", Tile.CANVAS, -10, 18, -14, 1, 10, 28, 0, 0, 14, -10, 18, 0);
		cube(cart, "cover_top", Tile.CANVAS, -8.8f, 27, -14, 17.6f, 2, 28);
		float[] ribs = {-14.5f, -0.5f, 13.5f};
		for (int i = 0; i < ribs.length; i++) {
			cube(cart, "rib_top_" + i, Tile.RED, -8.5f, 28.6f, ribs[i], 17, 0.8f, 1);
			cube(cart, "rib_l_" + i, Tile.RED, 9.6f, 18, ribs[i], 0.8f, 10, 1, 0, 0, -14, 10, 18, 0);
			cube(cart, "rib_r_" + i, Tile.RED, -10.4f, 18, ribs[i], 0.8f, 10, 1, 0, 0, 14, -10, 18, 0);
		}
		// Le chargement, qu'on voit par l'arrière.
		cube(cart, "crate", Tile.CRATE, -8, 13, -14, 7, 6, 7);
		cube(cart, "crate_top", Tile.CRATE, -7, 19, -13, 5, 4, 5);
		cube(cart, "barrel", Tile.BARREL, 1, 13, -14, 6, 8, 6);
		cube(cart, "barrel_hoop", Tile.IRON, 0.7f, 16, -14.3f, 6.6f, 1, 6.6f);
		cube(cart, "bench", Tile.WOOD, -9, 18, 12, 18, 1.5f, 3);
		// Les roues : une jante de huit planches et quatre rayons autour d'un moyeu de fer.
		for (String side : new String[]{"l", "r"}) {
			float x = side.equals("l") ? 11.5f : -12.5f;
			for (String end : new String[]{"front", "back"}) {
				float z = end.equals("front") ? 10 : -10;
				float y = 7;
				String wheel = side + "_" + end;
				for (int k = 0; k < 8; k++) {
					cube(cart, "rim_" + wheel + "_" + k, Tile.DARK, x, y + 5.4f, z - 2.9f, 1, 1.6f, 5.8f, k * 45, 0, 0,
							x, y, z);
				}
				for (int k = 0; k < 4; k++) {
					cube(cart, "spoke_" + wheel + "_" + k, Tile.WOOD, x + 0.1f, y - 0.5f, z - 6, 0.8f, 1, 12, k * 45, 0,
							0, x, y, z);
				}
				cube(cart, "hub_" + wheel, Tile.IRON, side.equals("l") ? x + 0.5f : x - 0.5f, y - 1.5f, z - 1.5f, 1, 3, 3);
			}
		}
		cube(cart, "axle_front", Tile.IRON, -12, 6, 9, 24, 2, 2);
		cube(cart, "axle_back", Tile.IRON, -12, 6, -11, 24, 2, 2);
		// Les brancards, jusqu'au cheval.
		cube(cart, "shaft_l", Tile.WOOD, 5, 10, 16, 1.5f, 1.5f, 22);
		cube(cart, "shaft_r", Tile.WOOD, -6.5f, 10, 16, 1.5f, 1.5f, 22);
		cube(cart, "crossbar", Tile.WOOD, -6.5f, 10, 37, 13, 1.5f, 1.5f);
		cube(cart, "lantern_post", Tile.DARK, 10, 18, 15, 1, 9, 1);
		cube(cart, "lantern", Tile.GOLD, 9.5f, 25, 15.5f, 2, 2.5f, 2);
	}

	private static void camp(PartDefinition camp) {
		// Le feu, entouré de pierres.
		cube(camp, "stone_n", Tile.STONE, -5, 0, -35, 10, 2, 2);
		cube(camp, "stone_s", Tile.STONE, -5, 0, -43, 10, 2, 2);
		cube(camp, "stone_w", Tile.STONE, -5, 0, -41, 2, 2, 6);
		cube(camp, "stone_e", Tile.STONE, 3, 0, -41, 2, 2, 6);
		cube(camp, "log_a", Tile.DARK, -4, 1, -38.75f, 8, 1.5f, 1.5f, 0, 45, 0, 0, 1, -38);
		cube(camp, "log_b", Tile.DARK, -4, 1, -38.75f, 8, 1.5f, 1.5f, 0, -45, 0, 0, 1, -38);
		cube(camp, "fire", Tile.FIRE, -2, 2, -40, 4, 3, 4);
		cube(camp, "flame", Tile.FLAME, -1, 3, -39, 2, 5, 2);
		// La tente : deux pans sur un faîte, une couche dedans, un fanion.
		cube(camp, "tent_l", Tile.TENT, 31.5f, -5.2f, -70, 1, 27.2f, 28, 0, 0, 36, 32, 22, -56);
		cube(camp, "tent_r", Tile.TENT, 31.5f, -5.2f, -70, 1, 27.2f, 28, 0, 0, -36, 32, 22, -56);
		cube(camp, "ridge", Tile.DARK, 31.25f, 21.5f, -71, 1.5f, 1.5f, 30);
		cube(camp, "pole_front", Tile.DARK, 31.5f, 0, -42, 1, 22, 1);
		cube(camp, "pole_back", Tile.DARK, 31.5f, 0, -71, 1, 22, 1);
		cube(camp, "pennant", Tile.RED, 31.85f, 23, -73, 0.3f, 3, 4);
		cube(camp, "bedroll", Tile.RED, 26, 0, -66, 12, 1, 6);
		cube(camp, "pillow", Tile.CANVAS, 27, 1, -66, 4, 1, 3);
		cube(camp, "seat", Tile.DARK, -25, 0, -53, 8, 3, 4);
		cube(camp, "crate", Tile.CRATE, 12, 0, -58, 6, 6, 6);
	}
}
