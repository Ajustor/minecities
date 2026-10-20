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
 * Le chariot d'une caravane (MC-80), à la façon des chariots bâchés de l'Ouest américain : caisse bleue
 * au fond bombé, train rouge, grandes roues à rayons (plus grandes à l'arrière), bâche blanche sur ses
 * arceaux, évasée aux deux bouts ; le banc du cocher (le marchand y conduit), le frein, une lanterne,
 * le tonneau d'eau, le coffre à outils, la mangeoire et le seau ; les brancards du cheval. La nuit,
 * le camp s'y ajoute derrière : feu entouré de pierres (là où
 * {@link com.ajustor.minecities.caravan.CaravanCart#FIRE_Z} fait monter la fumée), tente et couche,
 * bûche où s'assoit le marchand, une caisse déchargée.
 *
 * <p>Les coordonnées sont celles de Blockbench (en pixels, y vers le haut, l'avant vers +z) : le
 * modèle est rendu sans le retournement des modèles vanilla. Les roues tournent quand il roule. La
 * texture (textures/entity/caravan.png) est faite de pavés d'une couleur ; chaque cube prend le sien.
 */
public class CaravanCartModel extends EntityModel<CaravanCartRenderer.State> {
	/** La texture : 1024 x 512, des pavés de 256 x 128. */
	public static final int TEXTURE_WIDTH = 1024;
	public static final int TEXTURE_HEIGHT = 512;
	static final int TILE_WIDTH = 256;
	static final int TILE_HEIGHT = 128;
	/** Le rayon des roues, en blocs : elles tournent d'autant que le chariot roule. */
	private static final float FRONT_RADIUS = 8 / 16f;
	private static final float BACK_RADIUS = 11 / 16f;

	/** Les couleurs des pavés, dans l'ordre de la texture (voir tools/textures/generate.py). */
	enum Tile { WOOD, DARK, CANVAS, RED, IRON, CRATE, BARREL, GOLD, FIRE, FLAME, STONE, TENT, BLUE, GEAR }

	private final ModelPart cart;
	private final ModelPart camp;
	private final ModelPart fire;
	private final ModelPart flame;
	private final ModelPart[] front;
	private final ModelPart[] back;
	/** Ce modèle ne montre que le feu du camp (dessiné à pleine lumière), ou tout le reste. */
	private final boolean glow;

	public CaravanCartModel(ModelPart root, boolean glow) {
		super(root, RenderTypes::entityCutout);
		cart = root.getChild("cart");
		camp = root.getChild("camp");
		fire = camp.getChild("fire");
		flame = camp.getChild("flame");
		front = new ModelPart[]{cart.getChild("wheel_front_l"), cart.getChild("wheel_front_r")};
		back = new ModelPart[]{cart.getChild("wheel_back_l"), cart.getChild("wheel_back_r")};
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
		for (ModelPart wheel : front) {
			wheel.xRot = state.rolled / FRONT_RADIUS;
		}
		for (ModelPart wheel : back) {
			wheel.xRot = state.rolled / BACK_RADIUS;
		}
	}

	/** Un cube de cette couleur : sa boîte (depuis son pivot), son pivot (depuis le groupe), sa rotation en degrés. */
	static void cube(PartDefinition part, String name, Tile tile, float x, float y, float z, float w, float h, float d,
			float px, float py, float pz, float rx, float ry, float rz) {
		CubeListBuilder box = CubeListBuilder.create().texOffs((tile.ordinal() % 4) * TILE_WIDTH, (tile.ordinal() / 4) * TILE_HEIGHT)
				.addBox(x, y, z, w, h, d);
		part.addOrReplaceChild(name, box, PartPose.offsetAndRotation(px, py, pz, (float) Math.toRadians(rx),
				(float) Math.toRadians(ry), (float) Math.toRadians(rz)));
	}

	public static LayerDefinition create() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition g_cart = root.addOrReplaceChild("cart", CubeListBuilder.create(), PartPose.offset(0, 0, 0));
		PartDefinition g_camp = root.addOrReplaceChild("camp", CubeListBuilder.create(), PartPose.offset(0, 0, 0));
		PartDefinition g_wheel_front_l = g_cart.addOrReplaceChild("wheel_front_l", CubeListBuilder.create(), PartPose.offset(17, 8, 20));
		PartDefinition g_wheel_front_r = g_cart.addOrReplaceChild("wheel_front_r", CubeListBuilder.create(), PartPose.offset(-17, 8, 20));
		PartDefinition g_wheel_back_l = g_cart.addOrReplaceChild("wheel_back_l", CubeListBuilder.create(), PartPose.offset(17, 11, -19));
		PartDefinition g_wheel_back_r = g_cart.addOrReplaceChild("wheel_back_r", CubeListBuilder.create(), PartPose.offset(-17, 11, -19));
		cube(g_cart, "bed", Tile.BLUE, 0, 0, 0, 28, 2, 60, -14, 17, -30, 0, 0, 0);
		cube(g_cart, "side_l", Tile.BLUE, 0, 0, 0, 1.5f, 10, 48, 13, 19, -24, 0, 0, 0);
		cube(g_cart, "side_l_front", Tile.BLUE, 0, 0, 0, 1.5f, 10, 9, 13, 19, 24, -12, 0, 0);
		cube(g_cart, "side_l_back", Tile.BLUE, 0, 0, -9, 1.5f, 10, 9, 13, 19, -24, 12, 0, 0);
		cube(g_cart, "trim_l", Tile.DARK, 0, 0, 0, 1.9f, 1.2f, 48, 12.8f, 28.4f, -24, 0, 0, 0);
		cube(g_cart, "post_l_0", Tile.DARK, 0, 0, 0, 2, 10, 1, 12.75f, 19, -18.5f, 0, 0, 0);
		cube(g_cart, "post_l_1", Tile.DARK, 0, 0, 0, 2, 10, 1, 12.75f, 19, -6.5f, 0, 0, 0);
		cube(g_cart, "post_l_2", Tile.DARK, 0, 0, 0, 2, 10, 1, 12.75f, 19, 5.5f, 0, 0, 0);
		cube(g_cart, "post_l_3", Tile.DARK, 0, 0, 0, 2, 10, 1, 12.75f, 19, 17.5f, 0, 0, 0);
		cube(g_cart, "side_r", Tile.BLUE, 0, 0, 0, 1.5f, 10, 48, -14.5f, 19, -24, 0, 0, 0);
		cube(g_cart, "side_r_front", Tile.BLUE, 0, 0, 0, 1.5f, 10, 9, -14.5f, 19, 24, -12, 0, 0);
		cube(g_cart, "side_r_back", Tile.BLUE, 0, 0, -9, 1.5f, 10, 9, -14.5f, 19, -24, 12, 0, 0);
		cube(g_cart, "trim_r", Tile.DARK, 0, 0, 0, 1.9f, 1.2f, 48, -14.7f, 28.4f, -24, 0, 0, 0);
		cube(g_cart, "post_r_0", Tile.DARK, 0, 0, 0, 2, 10, 1, -14.75f, 19, -18.5f, 0, 0, 0);
		cube(g_cart, "post_r_1", Tile.DARK, 0, 0, 0, 2, 10, 1, -14.75f, 19, -6.5f, 0, 0, 0);
		cube(g_cart, "post_r_2", Tile.DARK, 0, 0, 0, 2, 10, 1, -14.75f, 19, 5.5f, 0, 0, 0);
		cube(g_cart, "post_r_3", Tile.DARK, 0, 0, 0, 2, 10, 1, -14.75f, 19, 17.5f, 0, 0, 0);
		cube(g_cart, "end_front", Tile.BLUE, 0, 0, 0, 26, 10, 1.5f, -13, 21, 31, 0, 0, 0);
		cube(g_cart, "end_back", Tile.BLUE, 0, 0, 0, 26, 10, 1.5f, -13, 21, -32.5f, 0, 0, 0);
		cube(g_cart, "axle_front", Tile.GEAR, 0, 0, 0, 36, 2, 2, -18, 7, 19, 0, 0, 0);
		cube(g_cart, "axle_back", Tile.GEAR, 0, 0, 0, 36, 2, 2, -18, 10, -20, 0, 0, 0);
		cube(g_cart, "bolster_front", Tile.GEAR, 0, 0, 0, 26, 8, 4, -13, 9, 18, 0, 0, 0);
		cube(g_cart, "bolster_back", Tile.GEAR, 0, 0, 0, 26, 5, 4, -13, 12, -21, 0, 0, 0);
		cube(g_cart, "reach", Tile.GEAR, 0, 0, 0, 2, 2, 40, -1, 12, -20, 0, 0, 0);
		cube(g_wheel_front_l, "rim_0", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 0, 0, 0);
		cube(g_wheel_front_l, "rim_1", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 36, 0, 0);
		cube(g_wheel_front_l, "rim_2", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 72, 0, 0);
		cube(g_wheel_front_l, "rim_3", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 108, 0, 0);
		cube(g_wheel_front_l, "rim_4", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 144, 0, 0);
		cube(g_wheel_front_l, "rim_5", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 180, 0, 0);
		cube(g_wheel_front_l, "rim_6", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 216, 0, 0);
		cube(g_wheel_front_l, "rim_7", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 252, 0, 0);
		cube(g_wheel_front_l, "rim_8", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 288, 0, 0);
		cube(g_wheel_front_l, "rim_9", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 324, 0, 0);
		cube(g_wheel_front_l, "spoke_0", Tile.GEAR, -0.5f, -0.6f, -6.8f, 1, 1.2f, 13.6f, 0, 0, 0, 0, 0, 0);
		cube(g_wheel_front_l, "spoke_1", Tile.GEAR, -0.5f, -0.6f, -6.8f, 1, 1.2f, 13.6f, 0, 0, 0, 45, 0, 0);
		cube(g_wheel_front_l, "spoke_2", Tile.GEAR, -0.5f, -0.6f, -6.8f, 1, 1.2f, 13.6f, 0, 0, 0, 90, 0, 0);
		cube(g_wheel_front_l, "spoke_3", Tile.GEAR, -0.5f, -0.6f, -6.8f, 1, 1.2f, 13.6f, 0, 0, 0, 135, 0, 0);
		cube(g_wheel_front_l, "hub", Tile.IRON, 0, 0, 0, 3, 3.6f, 3.6f, -1, -1.8f, -1.8f, 0, 0, 0);
		cube(g_wheel_front_r, "rim_0", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 0, 0, 0);
		cube(g_wheel_front_r, "rim_1", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 36, 0, 0);
		cube(g_wheel_front_r, "rim_2", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 72, 0, 0);
		cube(g_wheel_front_r, "rim_3", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 108, 0, 0);
		cube(g_wheel_front_r, "rim_4", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 144, 0, 0);
		cube(g_wheel_front_r, "rim_5", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 180, 0, 0);
		cube(g_wheel_front_r, "rim_6", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 216, 0, 0);
		cube(g_wheel_front_r, "rim_7", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 252, 0, 0);
		cube(g_wheel_front_r, "rim_8", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 288, 0, 0);
		cube(g_wheel_front_r, "rim_9", Tile.DARK, -1, 6.2f, -2.849f, 2, 1.8f, 5.699f, 0, 0, 0, 324, 0, 0);
		cube(g_wheel_front_r, "spoke_0", Tile.GEAR, -0.5f, -0.6f, -6.8f, 1, 1.2f, 13.6f, 0, 0, 0, 0, 0, 0);
		cube(g_wheel_front_r, "spoke_1", Tile.GEAR, -0.5f, -0.6f, -6.8f, 1, 1.2f, 13.6f, 0, 0, 0, 45, 0, 0);
		cube(g_wheel_front_r, "spoke_2", Tile.GEAR, -0.5f, -0.6f, -6.8f, 1, 1.2f, 13.6f, 0, 0, 0, 90, 0, 0);
		cube(g_wheel_front_r, "spoke_3", Tile.GEAR, -0.5f, -0.6f, -6.8f, 1, 1.2f, 13.6f, 0, 0, 0, 135, 0, 0);
		cube(g_wheel_front_r, "hub", Tile.IRON, 0, 0, 0, 3, 3.6f, 3.6f, -2, -1.8f, -1.8f, 0, 0, 0);
		cube(g_wheel_back_l, "rim_0", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 0, 0, 0);
		cube(g_wheel_back_l, "rim_1", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 36, 0, 0);
		cube(g_wheel_back_l, "rim_2", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 72, 0, 0);
		cube(g_wheel_back_l, "rim_3", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 108, 0, 0);
		cube(g_wheel_back_l, "rim_4", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 144, 0, 0);
		cube(g_wheel_back_l, "rim_5", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 180, 0, 0);
		cube(g_wheel_back_l, "rim_6", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 216, 0, 0);
		cube(g_wheel_back_l, "rim_7", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 252, 0, 0);
		cube(g_wheel_back_l, "rim_8", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 288, 0, 0);
		cube(g_wheel_back_l, "rim_9", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 324, 0, 0);
		cube(g_wheel_back_l, "spoke_0", Tile.GEAR, -0.5f, -0.6f, -9.8f, 1, 1.2f, 19.6f, 0, 0, 0, 0, 0, 0);
		cube(g_wheel_back_l, "spoke_1", Tile.GEAR, -0.5f, -0.6f, -9.8f, 1, 1.2f, 19.6f, 0, 0, 0, 45, 0, 0);
		cube(g_wheel_back_l, "spoke_2", Tile.GEAR, -0.5f, -0.6f, -9.8f, 1, 1.2f, 19.6f, 0, 0, 0, 90, 0, 0);
		cube(g_wheel_back_l, "spoke_3", Tile.GEAR, -0.5f, -0.6f, -9.8f, 1, 1.2f, 19.6f, 0, 0, 0, 135, 0, 0);
		cube(g_wheel_back_l, "hub", Tile.IRON, 0, 0, 0, 3, 3.6f, 3.6f, -1, -1.8f, -1.8f, 0, 0, 0);
		cube(g_wheel_back_r, "rim_0", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 0, 0, 0);
		cube(g_wheel_back_r, "rim_1", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 36, 0, 0);
		cube(g_wheel_back_r, "rim_2", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 72, 0, 0);
		cube(g_wheel_back_r, "rim_3", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 108, 0, 0);
		cube(g_wheel_back_r, "rim_4", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 144, 0, 0);
		cube(g_wheel_back_r, "rim_5", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 180, 0, 0);
		cube(g_wheel_back_r, "rim_6", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 216, 0, 0);
		cube(g_wheel_back_r, "rim_7", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 252, 0, 0);
		cube(g_wheel_back_r, "rim_8", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 288, 0, 0);
		cube(g_wheel_back_r, "rim_9", Tile.DARK, -1, 9.2f, -3.824f, 2, 1.8f, 7.648f, 0, 0, 0, 324, 0, 0);
		cube(g_wheel_back_r, "spoke_0", Tile.GEAR, -0.5f, -0.6f, -9.8f, 1, 1.2f, 19.6f, 0, 0, 0, 0, 0, 0);
		cube(g_wheel_back_r, "spoke_1", Tile.GEAR, -0.5f, -0.6f, -9.8f, 1, 1.2f, 19.6f, 0, 0, 0, 45, 0, 0);
		cube(g_wheel_back_r, "spoke_2", Tile.GEAR, -0.5f, -0.6f, -9.8f, 1, 1.2f, 19.6f, 0, 0, 0, 90, 0, 0);
		cube(g_wheel_back_r, "spoke_3", Tile.GEAR, -0.5f, -0.6f, -9.8f, 1, 1.2f, 19.6f, 0, 0, 0, 135, 0, 0);
		cube(g_wheel_back_r, "hub", Tile.IRON, 0, 0, 0, 3, 3.6f, 3.6f, -2, -1.8f, -1.8f, 0, 0, 0);
		cube(g_cart, "canvas_l", Tile.CANVAS, 0, 0, 0, 1, 13, 62, 14, 29, -33, 0, 0, 0);
		cube(g_cart, "canvas_r", Tile.CANVAS, 0, 0, 0, 1, 13, 62, -15, 29, -33, 0, 0, 0);
		cube(g_cart, "canvas_top", Tile.CANVAS, -8, 0, -27, 16, 1, 54, 0, 48, 0, 0, 0, 0);
		cube(g_cart, "shoulder_l", Tile.CANVAS, 0, -1, -27, 8.5f, 1, 54, 8, 48, 0, 0, 0, -45);
		cube(g_cart, "shoulder_r", Tile.CANVAS, -8.5f, -1, -27, 8.5f, 1, 54, -8, 48, 0, 0, 0, 45);
		cube(g_cart, "canvas_top_front", Tile.CANVAS, -8, 0, 0, 16, 1, 3, 0, 48, 27, -9, 0, 0);
		cube(g_cart, "shoulder_l_front", Tile.CANVAS, 0, -1, 0, 8.5f, 1, 3, 8, 48, 27, 0, 0, -45);
		cube(g_cart, "shoulder_r_front", Tile.CANVAS, -8.5f, -1, 0, 8.5f, 1, 3, -8, 48, 27, 0, 0, 45);
		cube(g_cart, "canvas_top_back", Tile.CANVAS, -8, 0, -7, 16, 1, 7, 0, 48, -27, 9, 0, 0);
		cube(g_cart, "shoulder_l_back", Tile.CANVAS, 0, -1, -7, 8.5f, 1, 7, 8, 48, -27, 0, 0, -45);
		cube(g_cart, "shoulder_r_back", Tile.CANVAS, -8.5f, -1, -7, 8.5f, 1, 7, -8, 48, -27, 0, 0, 45);
		cube(g_cart, "bow_l_0", Tile.TENT, 0, 0, 0, 0.5f, 13, 1.2f, 15, 29, -26.6f, 0, 0, 0);
		cube(g_cart, "bow_r_0", Tile.TENT, 0, 0, 0, 0.5f, 13, 1.2f, -15.5f, 29, -26.6f, 0, 0, 0);
		cube(g_cart, "bow_top_0", Tile.TENT, 0, 0, 0, 16, 0.5f, 1.2f, -8, 49, -26.6f, 0, 0, 0);
		cube(g_cart, "bow_sl_0", Tile.TENT, 0, 0, -0.6f, 8.5f, 0.5f, 1.2f, 8, 48, -26, 0, 0, -45);
		cube(g_cart, "bow_sr_0", Tile.TENT, -8.5f, 0, -0.6f, 8.5f, 0.5f, 1.2f, -8, 48, -26, 0, 0, 45);
		cube(g_cart, "bow_l_1", Tile.TENT, 0, 0, 0, 0.5f, 13, 1.2f, 15, 29, -13.6f, 0, 0, 0);
		cube(g_cart, "bow_r_1", Tile.TENT, 0, 0, 0, 0.5f, 13, 1.2f, -15.5f, 29, -13.6f, 0, 0, 0);
		cube(g_cart, "bow_top_1", Tile.TENT, 0, 0, 0, 16, 0.5f, 1.2f, -8, 49, -13.6f, 0, 0, 0);
		cube(g_cart, "bow_sl_1", Tile.TENT, 0, 0, -0.6f, 8.5f, 0.5f, 1.2f, 8, 48, -13, 0, 0, -45);
		cube(g_cart, "bow_sr_1", Tile.TENT, -8.5f, 0, -0.6f, 8.5f, 0.5f, 1.2f, -8, 48, -13, 0, 0, 45);
		cube(g_cart, "bow_l_2", Tile.TENT, 0, 0, 0, 0.5f, 13, 1.2f, 15, 29, -0.6f, 0, 0, 0);
		cube(g_cart, "bow_r_2", Tile.TENT, 0, 0, 0, 0.5f, 13, 1.2f, -15.5f, 29, -0.6f, 0, 0, 0);
		cube(g_cart, "bow_top_2", Tile.TENT, 0, 0, 0, 16, 0.5f, 1.2f, -8, 49, -0.6f, 0, 0, 0);
		cube(g_cart, "bow_sl_2", Tile.TENT, 0, 0, -0.6f, 8.5f, 0.5f, 1.2f, 8, 48, 0, 0, 0, -45);
		cube(g_cart, "bow_sr_2", Tile.TENT, -8.5f, 0, -0.6f, 8.5f, 0.5f, 1.2f, -8, 48, 0, 0, 0, 45);
		cube(g_cart, "bow_l_3", Tile.TENT, 0, 0, 0, 0.5f, 13, 1.2f, 15, 29, 12.4f, 0, 0, 0);
		cube(g_cart, "bow_r_3", Tile.TENT, 0, 0, 0, 0.5f, 13, 1.2f, -15.5f, 29, 12.4f, 0, 0, 0);
		cube(g_cart, "bow_top_3", Tile.TENT, 0, 0, 0, 16, 0.5f, 1.2f, -8, 49, 12.4f, 0, 0, 0);
		cube(g_cart, "bow_sl_3", Tile.TENT, 0, 0, -0.6f, 8.5f, 0.5f, 1.2f, 8, 48, 13, 0, 0, -45);
		cube(g_cart, "bow_sr_3", Tile.TENT, -8.5f, 0, -0.6f, 8.5f, 0.5f, 1.2f, -8, 48, 13, 0, 0, 45);
		cube(g_cart, "bow_l_4", Tile.TENT, 0, 0, 0, 0.5f, 13, 1.2f, 15, 29, 25.4f, 0, 0, 0);
		cube(g_cart, "bow_r_4", Tile.TENT, 0, 0, 0, 0.5f, 13, 1.2f, -15.5f, 29, 25.4f, 0, 0, 0);
		cube(g_cart, "bow_top_4", Tile.TENT, 0, 0, 0, 16, 0.5f, 1.2f, -8, 49, 25.4f, 0, 0, 0);
		cube(g_cart, "bow_sl_4", Tile.TENT, 0, 0, -0.6f, 8.5f, 0.5f, 1.2f, 8, 48, 26, 0, 0, -45);
		cube(g_cart, "bow_sr_4", Tile.TENT, -8.5f, 0, -0.6f, 8.5f, 0.5f, 1.2f, -8, 48, 26, 0, 0, 45);
		cube(g_cart, "shade", Tile.DARK, 0, 0, 0, 26, 18, 1, -13, 29, 24, 0, 0, 0);
		cube(g_cart, "pucker", Tile.CANVAS, 0, 0, 0, 24, 14, 1, -12, 31, -33, 0, 0, 0);
		cube(g_cart, "pucker_hole", Tile.DARK, 0, 0, 0, 4, 4, 0.5f, -2, 36, -33.4f, 0, 0, 0);
		cube(g_cart, "seat", Tile.WOOD, 0, 0, 0, 22, 1.5f, 5, -11, 29, 32, 0, 0, 0);
		cube(g_cart, "seat_back", Tile.WOOD, 0, 0, 0, 22, 4, 1, -11, 30.5f, 31, 0, 0, 0);
		cube(g_cart, "seat_leg_l", Tile.DARK, 0, 0, 0, 1.5f, 5, 1.5f, 9, 24, 35, 0, 0, 0);
		cube(g_cart, "seat_leg_r", Tile.DARK, 0, 0, 0, 1.5f, 5, 1.5f, -10.5f, 24, 35, 0, 0, 0);
		cube(g_cart, "footboard", Tile.WOOD, -12, 0, 0, 24, 1, 6, 0, 22, 37, 18, 0, 0);
		cube(g_cart, "brake", Tile.DARK, -0.5f, 0, -0.5f, 1, 16, 1, -15.3f, 20, 30.5f, -15, 0, 0);
		cube(g_cart, "lantern_hook", Tile.IRON, 0, 0, 0, 0.6f, 6, 0.6f, 11, 34, 29, 0, 0, 0);
		cube(g_cart, "lantern", Tile.GOLD, 0, 0, 0, 2.4f, 3.2f, 2.4f, 10.1f, 30.5f, 28.1f, 0, 0, 0);
		cube(g_cart, "barrel", Tile.BARREL, 0, 0, 0, 6, 9, 6, 14.5f, 19, -9, 0, 0, 0);
		cube(g_cart, "barrel_hoop_a", Tile.IRON, 0, 0, 0, 6.6f, 1, 6.6f, 14.2f, 20.5f, -9.3f, 0, 0, 0);
		cube(g_cart, "barrel_hoop_b", Tile.IRON, 0, 0, 0, 6.6f, 1, 6.6f, 14.2f, 25.5f, -9.3f, 0, 0, 0);
		cube(g_cart, "toolbox", Tile.WOOD, 0, 0, 0, 5, 5, 12, -20, 21, -6, 0, 0, 0);
		cube(g_cart, "toolbox_lid", Tile.DARK, 0, 0, 0, 5.6f, 1, 12.6f, -20.3f, 26, -6.3f, 0, 0, 0);
		cube(g_cart, "trough", Tile.DARK, 0, 0, 0, 20, 3, 3, -10, 23, -36, 0, 0, 0);
		cube(g_cart, "bucket", Tile.IRON, 0, 0, 0, 4, 4, 4, 5, 16, -36, 0, 0, 0);
		cube(g_cart, "shaft_l", Tile.WOOD, 0, 0, 0, 1.5f, 1.5f, 26, 5, 13, 33, 0, 0, 0);
		cube(g_cart, "shaft_r", Tile.WOOD, 0, 0, 0, 1.5f, 1.5f, 26, -6.5f, 13, 33, 0, 0, 0);
		cube(g_cart, "crossbar", Tile.WOOD, 0, 0, 0, 13, 1.5f, 1.5f, -6.5f, 13, 58, 0, 0, 0);
		cube(g_camp, "stone_n", Tile.STONE, 0, 0, 0, 10, 2, 2, -5, 0, -57, 0, 0, 0);
		cube(g_camp, "stone_s", Tile.STONE, 0, 0, 0, 10, 2, 2, -5, 0, -65, 0, 0, 0);
		cube(g_camp, "stone_w", Tile.STONE, 0, 0, 0, 2, 2, 6, -5, 0, -63, 0, 0, 0);
		cube(g_camp, "stone_e", Tile.STONE, 0, 0, 0, 2, 2, 6, 3, 0, -63, 0, 0, 0);
		cube(g_camp, "log_a", Tile.DARK, -4, 0, -0.75f, 8, 1.5f, 1.5f, 0, 1, -60, 0, 45, 0);
		cube(g_camp, "log_b", Tile.DARK, -4, 0, -0.75f, 8, 1.5f, 1.5f, 0, 1, -60, 0, -45, 0);
		cube(g_camp, "fire", Tile.FIRE, 0, 0, 0, 4, 3, 4, -2, 2, -62, 0, 0, 0);
		cube(g_camp, "flame", Tile.FLAME, 0, 0, 0, 2, 5, 2, -1, 3, -61, 0, 0, 0);
		cube(g_camp, "tent_l", Tile.TENT, -0.5f, -27.2f, -14, 1, 27.2f, 28, 32, 22, -78, 0, 0, 36);
		cube(g_camp, "tent_r", Tile.TENT, -0.5f, -27.2f, -14, 1, 27.2f, 28, 32, 22, -78, 0, 0, -36);
		cube(g_camp, "ridge", Tile.DARK, 0, 0, 0, 1.5f, 1.5f, 30, 31.25f, 21.5f, -93, 0, 0, 0);
		cube(g_camp, "pole_front", Tile.DARK, 0, 0, 0, 1, 22, 1, 31.5f, 0, -64, 0, 0, 0);
		cube(g_camp, "pole_back", Tile.DARK, 0, 0, 0, 1, 22, 1, 31.5f, 0, -93, 0, 0, 0);
		cube(g_camp, "pennant", Tile.RED, 0, 0, 0, 0.3f, 3, 4, 31.85f, 23, -95, 0, 0, 0);
		cube(g_camp, "bedroll", Tile.RED, 0, 0, 0, 12, 1, 6, 26, 0, -88, 0, 0, 0);
		cube(g_camp, "pillow", Tile.CANVAS, 0, 0, 0, 4, 1, 3, 27, 1, -88, 0, 0, 0);
		cube(g_camp, "seat_log", Tile.DARK, 0, 0, 0, 8, 3, 4, -25, 0, -75, 0, 0, 0);
		cube(g_camp, "crate", Tile.CRATE, 0, 0, 0, 6, 6, 6, 12, 0, -80, 0, 0, 0);
		return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
	}
}
