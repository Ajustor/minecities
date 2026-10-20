"""Génère les modèles des caravanes (MC-80) : le chariot bâché et son camp de nuit, et l'échoppe.

Usage : python tools/models/caravan.py [--blockbench DOSSIER]
Les modèles sont décrits ici une fois, en cubes de couleur (coordonnées de Blockbench : en pixels,
y vers le haut, le devant vers +z) ; le script écrit les classes Java des modèles
(src/client/.../render/CaravanCartModel.java et CaravanStallModel.java) et, avec --blockbench, des
scripts à coller dans la console de Blockbench (ou à passer au MCP) pour voir et capturer les modèles.
Modifier ce script puis le relancer plutôt que de retoucher le Java à la main.

La texture (textures/entity/caravan.png) est faite de pavés d'une couleur, générée par
tools/textures/generate.py dans l'ordre de TILES ci-dessous.
"""
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RENDER = ROOT / "src/client/java/com/ajustor/minecities/client/render"

# Les pavés de la texture, dans l'ordre (voir CARAVAN_TILES dans tools/textures/generate.py).
TILES = {
    "WOOD": "#8a5a2b", "DARK": "#5a3b22", "CANVAS": "#eee6d0", "RED": "#b3312c", "IRON": "#6f7378",
    "CRATE": "#b08447", "BARREL": "#7a4e26", "GOLD": "#f2c94a", "FIRE": "#f08a24", "FLAME": "#ffd34a",
    "STONE": "#8d8d8d", "TENT": "#c9a46a", "BLUE": "#3f5f8f", "GEAR": "#9a3b2a",
}
# La texture : des pavés de TILE_W x TILE_H, quatre par rang.
TILE_W, TILE_H = 256, 128
TEXTURE_W, TEXTURE_H = 4 * TILE_W, 4 * TILE_H


class Model:
    def __init__(self):
        self.groups = {}  # nom -> (parent, pivot)
        self.cubes = []

    def group(self, name, parent, pivot):
        self.groups[name] = (parent, pivot)

    def cube(self, group, name, tile, frm, size, rot=(0, 0, 0), pivot=None):
        assert tile in TILES, tile
        self.cubes.append(dict(group=group, name=name, tile=tile, frm=list(frm), size=list(size), rot=list(rot),
                               pivot=list(pivot if pivot is not None else frm)))


def wheel(m, parent, name, x, y, z, radius, side):
    """Une roue à rayons, groupée autour de son moyeu (elle tourne d'un bloc quand le chariot roule)."""
    m.group(name, parent, (x, y, z))
    segments = 10
    length = 2 * radius * math.tan(math.pi / segments) + 0.5
    for k in range(segments):
        m.cube(name, f"rim_{k}", "DARK", (x - 1, y + radius - 1.8, z - length / 2), (2, 1.8, length),
               (k * 360 / segments, 0, 0), (x, y, z))
    for k in range(4):
        m.cube(name, f"spoke_{k}", "GEAR", (x - 0.5, y - 0.6, z - radius + 1.2), (1, 1.2, 2 * radius - 2.4),
               (k * 45, 0, 0), (x, y, z))
    m.cube(name, "hub", "IRON", (x - 1.5 + side * 0.5, y - 1.8, z - 1.8), (3, 3.6, 3.6))


def cart():
    m = Model()
    m.group("cart", None, (0, 0, 0))
    m.group("camp", None, (0, 0, 0))
    # La caisse bleue, au fond bombé : ses bouts remontent.
    m.cube("cart", "bed", "BLUE", (-14, 17, -30), (28, 2, 60))
    for side, x in (("l", 13), ("r", -14.5)):
        m.cube("cart", f"side_{side}", "BLUE", (x, 19, -24), (1.5, 10, 48))
        m.cube("cart", f"side_{side}_front", "BLUE", (x, 19, 24), (1.5, 10, 9), (-12, 0, 0), (x, 19, 24))
        m.cube("cart", f"side_{side}_back", "BLUE", (x, 19, -33), (1.5, 10, 9), (12, 0, 0), (x, 19, -24))
        m.cube("cart", f"trim_{side}", "DARK", (x - 0.2, 28.4, -24), (1.9, 1.2, 48))
        for i, z in enumerate((-18, -6, 6, 18)):
            m.cube("cart", f"post_{side}_{i}", "DARK", (x - 0.25, 19, z - 0.5), (2, 10, 1))
    m.cube("cart", "end_front", "BLUE", (-13, 21, 31), (26, 10, 1.5))
    m.cube("cart", "end_back", "BLUE", (-13, 21, -32.5), (26, 10, 1.5))
    # Le train rouge : essieux, sellettes, flèche.
    m.cube("cart", "axle_front", "GEAR", (-18, 7, 19), (36, 2, 2))
    m.cube("cart", "axle_back", "GEAR", (-18, 10, -20), (36, 2, 2))
    m.cube("cart", "bolster_front", "GEAR", (-13, 9, 18), (26, 8, 4))
    m.cube("cart", "bolster_back", "GEAR", (-13, 12, -21), (26, 5, 4))
    m.cube("cart", "reach", "GEAR", (-1, 12, -20), (2, 2, 40))
    # Les roues : plus grandes à l'arrière.
    wheel(m, "cart", "wheel_front_l", 17, 8, 20, 8, 1)
    wheel(m, "cart", "wheel_front_r", -17, 8, 20, 8, -1)
    wheel(m, "cart", "wheel_back_l", 17, 11, -19, 11, 1)
    wheel(m, "cart", "wheel_back_r", -17, 11, -19, 11, -1)
    # La bâche, sur ses arceaux, évasée aux deux bouts.
    # Le corps de la bâche, puis ses bouts : les côtés s'allongent, le haut se relève un peu (évasé).
    # Assez haute pour que le cocher, assis devant, ait la tête sous l'avancée.
    side = 21          # la hauteur des côtés, depuis le haut de la caisse (y 29)
    top = 29 + side + 6  # le haut de la bâche
    m.cube("cart", "canvas_l", "CANVAS", (14, 29, -33), (1, side, 62))
    m.cube("cart", "canvas_r", "CANVAS", (-15, 29, -33), (1, side, 62))
    for part, z0, length, rx, pz in (("", -27, 54, 0, 0), ("_front", 27, 5, -9, 27), ("_back", -34, 7, 9, -27)):
        m.cube("cart", f"canvas_top{part}", "CANVAS", (-8, top, z0), (16, 1, length), (rx, 0, 0), (0, top, pz))
        m.cube("cart", f"shoulder_l{part}", "CANVAS", (8, top - 1, z0), (8.5, 1, length), (0, 0, -45), (8, top, pz))
        m.cube("cart", f"shoulder_r{part}", "CANVAS", (-16.5, top - 1, z0), (8.5, 1, length), (0, 0, 45), (-8, top, pz))
    for i, z in enumerate((-26, -13, 0, 13, 26)):
        m.cube("cart", f"bow_l_{i}", "TENT", (15, 29, z - 0.6), (0.5, side, 1.2))
        m.cube("cart", f"bow_r_{i}", "TENT", (-15.5, 29, z - 0.6), (0.5, side, 1.2))
        m.cube("cart", f"bow_top_{i}", "TENT", (-8, top + 1, z - 0.6), (16, 0.5, 1.2))
        m.cube("cart", f"bow_sl_{i}", "TENT", (8, top, z - 0.6), (8.5, 0.5, 1.2), (0, 0, -45), (8, top, z))
        m.cube("cart", f"bow_sr_{i}", "TENT", (-16.5, top, z - 0.6), (8.5, 0.5, 1.2), (0, 0, 45), (-8, top, z))
    # L'ombre sous la bâche, vue par l'avant ; l'arrière est fermé, froncé.
    m.cube("cart", "shade", "DARK", (-13, 29, 24), (26, top - 30, 1))
    m.cube("cart", "pucker", "CANVAS", (-12, 31, -33), (24, top - 34, 1))
    m.cube("cart", "pucker_hole", "DARK", (-2, 40, -33.4), (4, 4, 0.5))
    # Le banc du cocher, son dossier, le marchepied ; le frein ; la lanterne.
    # Le banc du cocher, devant l'ouverture de la bâche, au-dessus du panneau avant.
    m.cube("cart", "seat", "WOOD", (-11, 29, 32), (22, 1.5, 5))
    m.cube("cart", "seat_back", "WOOD", (-11, 30.5, 31), (22, 4, 1))
    m.cube("cart", "seat_leg_l", "DARK", (9, 24, 35), (1.5, 5, 1.5))
    m.cube("cart", "seat_leg_r", "DARK", (-10.5, 24, 35), (1.5, 5, 1.5))
    m.cube("cart", "footboard", "WOOD", (-12, 22, 37), (24, 1, 6), (18, 0, 0), (0, 22, 37))
    m.cube("cart", "brake", "DARK", (-15.8, 20, 30), (1, 16, 1), (-15, 0, 0), (-15.3, 20, 30.5))
    m.cube("cart", "lantern_hook", "IRON", (11, 34, 29), (0.6, 6, 0.6))
    m.cube("cart", "lantern", "GOLD", (10.1, 30.5, 28.1), (2.4, 3.2, 2.4))
    # Le tonneau d'eau à gauche, le coffre à outils à droite, le seau et la mangeoire derrière.
    m.cube("cart", "barrel", "BARREL", (14.5, 19, -9), (6, 9, 6))
    m.cube("cart", "barrel_hoop_a", "IRON", (14.2, 20.5, -9.3), (6.6, 1, 6.6))
    m.cube("cart", "barrel_hoop_b", "IRON", (14.2, 25.5, -9.3), (6.6, 1, 6.6))
    m.cube("cart", "toolbox", "WOOD", (-20, 21, -6), (5, 5, 12))
    m.cube("cart", "toolbox_lid", "DARK", (-20.3, 26, -6.3), (5.6, 1, 12.6))
    m.cube("cart", "trough", "DARK", (-10, 23, -36), (20, 3, 3))
    m.cube("cart", "bucket", "IRON", (5, 16, -36), (4, 4, 4))
    # Les brancards, jusqu'au cheval.
    m.cube("cart", "shaft_l", "WOOD", (5, 13, 33), (1.5, 1.5, 26))
    m.cube("cart", "shaft_r", "WOOD", (-6.5, 13, 33), (1.5, 1.5, 26))
    m.cube("cart", "crossbar", "WOOD", (-6.5, 13, 58), (13, 1.5, 1.5))

    # Le camp de nuit, derrière le chariot : le feu en (0, FIRE_Z), la tente à gauche.
    fz = -60
    m.cube("camp", "stone_n", "STONE", (-5, 0, fz + 3), (10, 2, 2))
    m.cube("camp", "stone_s", "STONE", (-5, 0, fz - 5), (10, 2, 2))
    m.cube("camp", "stone_w", "STONE", (-5, 0, fz - 3), (2, 2, 6))
    m.cube("camp", "stone_e", "STONE", (3, 0, fz - 3), (2, 2, 6))
    m.cube("camp", "log_a", "DARK", (-4, 1, fz - 0.75), (8, 1.5, 1.5), (0, 45, 0), (0, 1, fz))
    m.cube("camp", "log_b", "DARK", (-4, 1, fz - 0.75), (8, 1.5, 1.5), (0, -45, 0), (0, 1, fz))
    m.cube("camp", "fire", "FIRE", (-2, 2, fz - 2), (4, 3, 4))
    m.cube("camp", "flame", "FLAME", (-1, 3, fz - 1), (2, 5, 2))
    tz = fz - 32
    m.cube("camp", "tent_l", "TENT", (31.5, -5.2, tz), (1, 27.2, 28), (0, 0, 36), (32, 22, tz + 14))
    m.cube("camp", "tent_r", "TENT", (31.5, -5.2, tz), (1, 27.2, 28), (0, 0, -36), (32, 22, tz + 14))
    m.cube("camp", "ridge", "DARK", (31.25, 21.5, tz - 1), (1.5, 1.5, 30))
    m.cube("camp", "pole_front", "DARK", (31.5, 0, tz + 28), (1, 22, 1))
    m.cube("camp", "pole_back", "DARK", (31.5, 0, tz - 1), (1, 22, 1))
    m.cube("camp", "pennant", "RED", (31.85, 23, tz - 3), (0.3, 3, 4))
    m.cube("camp", "bedroll", "RED", (26, 0, tz + 4), (12, 1, 6))
    m.cube("camp", "pillow", "CANVAS", (27, 1, tz + 4), (4, 1, 3))
    m.cube("camp", "seat_log", "DARK", (-25, 0, fz - 15), (8, 3, 4))
    m.cube("camp", "crate", "CRATE", (12, 0, fz - 20), (6, 6, 6))
    return m


def stall():
    m = Model()
    m.group("stall", None, (0, 0, 0))
    w = 28  # la demi-largeur du comptoir
    m.cube("stall", "counter", "WOOD", (-w, 0, -6), (2 * w, 14, 12))
    m.cube("stall", "counter_top", "DARK", (-w - 1, 14, -7), (2 * w + 2, 2, 14))
    m.cube("stall", "plinth", "DARK", (-w - 0.5, 0, 5.5), (2 * w + 1, 2, 1))
    for i, x in enumerate((-18, -6, 6, 18)):
        m.cube("stall", f"plank_{i}", "DARK", (x - 0.5, 2, 5.6), (1, 12, 0.6))
    # Des caisses ouvertes posées devant, pleines de marchandises (dessinées à part).
    m.cube("stall", "crate_front_l", "CRATE", (-w + 2, 0, 7), (10, 6, 7))
    m.cube("stall", "crate_front_r", "CRATE", (w - 12, 0, 7), (10, 6, 7))
    for side, x in (("l", w - 1), ("r", -w - 1)):
        m.cube("stall", f"post_front_{side}", "DARK", (x, 0, 8), (2, 38, 2))
        m.cube("stall", f"post_back_{side}", "DARK", (x, 0, -16), (2, 44, 2))
    # L'auvent rayé rouge et blanc, qui penche vers l'avant, et son lambrequin.
    strips = 8
    sw = (2 * w + 2) / strips
    for k in range(strips):
        tile = "RED" if k % 2 == 0 else "CANVAS"
        x = -w - 1 + k * sw
        m.cube("stall", f"awning_{k}", tile, (x, 44, -17), (sw, 1, 30), (13, 0, 0), (0, 44, -17))
        m.cube("stall", f"valance_{k}", tile, (x, 34.5, 12), (sw, 3, 0.6))
    # Derrière : une étagère à deux rangs, des caisses, un tonneau.
    m.cube("stall", "shelf_low", "WOOD", (-w + 1, 20, -15), (2 * w - 2, 1.5, 5))
    m.cube("stall", "shelf_high", "WOOD", (-w + 1, 29, -15), (2 * w - 2, 1.5, 5))
    m.cube("stall", "crate_a", "CRATE", (-w + 2, 0, -14), (9, 8, 7))
    m.cube("stall", "crate_b", "CRATE", (-w + 3, 8, -13), (6, 5, 5))
    m.cube("stall", "barrel", "BARREL", (w - 10, 0, -14), (7, 10, 7))
    m.cube("stall", "barrel_hoop", "IRON", (w - 10.3, 6, -14.3), (7.6, 1, 7.6))
    # L'enseigne, suspendue sous l'auvent.
    m.cube("stall", "sign_chain_l", "IRON", (-7, 31, 10.5), (0.6, 4, 0.6))
    m.cube("stall", "sign_chain_r", "IRON", (6.4, 31, 10.5), (0.6, 4, 0.6))
    m.cube("stall", "sign", "WOOD", (-9, 26, 10.2), (18, 5, 1))
    m.cube("stall", "sign_mark", "GOLD", (-2, 27.5, 11.1), (4, 2, 0.3))
    return m


# Le Java

def f(v):
    text = ("%.3f" % v).rstrip("0").rstrip(".")
    if text in ("-0", ""):
        text = "0"
    return text + ("f" if "." in text else "")


def java_parts(m, root_var):
    """Les lignes Java qui bâtissent les groupes et les cubes de ce modèle."""
    lines = []
    for name, (parent, pivot) in m.groups.items():
        parent_var = root_var if parent is None else "g_" + parent
        ppivot = (0, 0, 0) if parent is None else m.groups[parent][1]
        offset = [pivot[i] - ppivot[i] for i in range(3)]
        lines.append(f'\t\tPartDefinition g_{name} = {parent_var}.addOrReplaceChild("{name}", CubeListBuilder.create(), '
                     f'PartPose.offset({f(offset[0])}, {f(offset[1])}, {f(offset[2])}));')
    for c in m.cubes:
        gp = m.groups[c["group"]][1]
        p = c["pivot"]
        args = [f(c["frm"][i] - p[i]) for i in range(3)] + [f(s) for s in c["size"]]
        offset = [f(p[i] - gp[i]) for i in range(3)]
        rot = [f(r) for r in c["rot"]]
        lines.append(f'\t\tcube(g_{c["group"]}, "{c["name"]}", Tile.{c["tile"]}, {", ".join(args)}, '
                     f'{", ".join(offset)}, {", ".join(rot)});')
    return "\n".join(lines)


HEADER = """package com.ajustor.minecities.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

// Généré par tools/models/caravan.py : modifier le script et le relancer, pas ce fichier.
"""

CUBE_HELPER = """
	/** Un cube de cette couleur : sa boîte (depuis son pivot), son pivot (depuis le groupe), sa rotation en degrés. */
	static void cube(PartDefinition part, String name, Tile tile, float x, float y, float z, float w, float h, float d,
			float px, float py, float pz, float rx, float ry, float rz) {
		CubeListBuilder box = CubeListBuilder.create().texOffs((tile.ordinal() % 4) * TILE_WIDTH, (tile.ordinal() / 4) * TILE_HEIGHT)
				.addBox(x, y, z, w, h, d);
		part.addOrReplaceChild(name, box, PartPose.offsetAndRotation(px, py, pz, (float) Math.toRadians(rx),
				(float) Math.toRadians(ry), (float) Math.toRadians(rz)));
	}
"""


def write_cart(m):
    tiles = ", ".join(TILES)
    text = HEADER + f"""
/**
 * Le chariot d'une caravane (MC-80), à la façon des chariots bâchés de l'Ouest américain : caisse bleue
 * au fond bombé, train rouge, grandes roues à rayons (plus grandes à l'arrière), bâche blanche sur ses
 * arceaux, évasée aux deux bouts ; le banc du cocher (le marchand y conduit), le frein, une lanterne,
 * le tonneau d'eau, le coffre à outils, la mangeoire et le seau ; les brancards du cheval. La nuit,
 * le camp s'y ajoute derrière : feu entouré de pierres (là où
 * {{@link com.ajustor.minecities.caravan.CaravanCart#FIRE_Z}} fait monter la fumée), tente et couche,
 * bûche où s'assoit le marchand, une caisse déchargée.
 *
 * <p>Les coordonnées sont celles de Blockbench (en pixels, y vers le haut, l'avant vers +z) : le
 * modèle est rendu sans le retournement des modèles vanilla. Les roues tournent quand il roule. La
 * texture (textures/entity/caravan.png) est faite de pavés d'une couleur ; chaque cube prend le sien.
 */
public class CaravanCartModel extends EntityModel<CaravanCartRenderer.State> {{
	/** La texture : {TEXTURE_W} x {TEXTURE_H}, des pavés de {TILE_W} x {TILE_H}. */
	public static final int TEXTURE_WIDTH = {TEXTURE_W};
	public static final int TEXTURE_HEIGHT = {TEXTURE_H};
	static final int TILE_WIDTH = {TILE_W};
	static final int TILE_HEIGHT = {TILE_H};
	/** Le rayon des roues, en blocs : elles tournent d'autant que le chariot roule. */
	private static final float FRONT_RADIUS = 8 / 16f;
	private static final float BACK_RADIUS = 11 / 16f;

	/** Les couleurs des pavés, dans l'ordre de la texture (voir tools/textures/generate.py). */
	enum Tile {{ {tiles} }}

	private final ModelPart cart;
	private final ModelPart camp;
	private final ModelPart fire;
	private final ModelPart flame;
	private final ModelPart[] front;
	private final ModelPart[] back;
	/** Ce modèle ne montre que le feu du camp (dessiné à pleine lumière), ou tout le reste. */
	private final boolean glow;

	public CaravanCartModel(ModelPart root, boolean glow) {{
		super(root, RenderTypes::entityCutout);
		cart = root.getChild("cart");
		camp = root.getChild("camp");
		fire = camp.getChild("fire");
		flame = camp.getChild("flame");
		front = new ModelPart[]{{cart.getChild("wheel_front_l"), cart.getChild("wheel_front_r")}};
		back = new ModelPart[]{{cart.getChild("wheel_back_l"), cart.getChild("wheel_back_r")}};
		this.glow = glow;
	}}

	@Override
	public void setupAnim(CaravanCartRenderer.State state) {{
		super.setupAnim(state);
		camp.visible = state.camping;
		cart.visible = !glow;
		camp.getAllParts().forEach(part -> part.skipDraw = glow);
		camp.skipDraw = true;
		fire.skipDraw = !glow;
		flame.skipDraw = !glow;
		for (ModelPart wheel : front) {{
			wheel.xRot = state.rolled / FRONT_RADIUS;
		}}
		for (ModelPart wheel : back) {{
			wheel.xRot = state.rolled / BACK_RADIUS;
		}}
	}}
{CUBE_HELPER}
	public static LayerDefinition create() {{
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
{java_parts(m, "root")}
		return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
	}}
}}
"""
    (RENDER / "CaravanCartModel.java").write_text(text, encoding="utf-8", newline="\n")


def write_stall(m):
    text = HEADER + f"""
/**
 * L'échoppe d'une caravane (MC-80) : un long comptoir de planches sous un auvent rayé rouge et blanc,
 * deux caisses ouvertes devant, une enseigne suspendue, et derrière le marchand une étagère à deux
 * rangs, des caisses et un tonneau. Les marchandises (sur le comptoir, dans les caisses, sur
 * l'étagère) sont dessinées à part, de vrais objets ({{@link CaravanStallRenderer}}).
 *
 * <p>Mêmes conventions que {{@link CaravanCartModel}} (coordonnées de Blockbench, le devant vers +z, où
 * se tient le client), et même texture.
 */
public class CaravanStallModel extends EntityModel<CaravanStallRenderer.State> {{
	static final int TILE_WIDTH = CaravanCartModel.TILE_WIDTH;
	static final int TILE_HEIGHT = CaravanCartModel.TILE_HEIGHT;

	public CaravanStallModel(ModelPart root) {{
		super(root, RenderTypes::entityCutout);
	}}

	private static void cube(PartDefinition part, String name, CaravanCartModel.Tile tile, float x, float y, float z,
			float w, float h, float d, float px, float py, float pz, float rx, float ry, float rz) {{
		CaravanCartModel.cube(part, name, tile, x, y, z, w, h, d, px, py, pz, rx, ry, rz);
	}}

	public static LayerDefinition create() {{
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
{java_parts(m, "root").replace("Tile.", "CaravanCartModel.Tile.")}
		return LayerDefinition.create(mesh, CaravanCartModel.TEXTURE_WIDTH, CaravanCartModel.TEXTURE_HEIGHT);
	}}
}}
"""
    (RENDER / "CaravanStallModel.java").write_text(text, encoding="utf-8", newline="\n")


# Blockbench

def blockbench(m):
    """Un script pour la console de Blockbench : vide le projet ouvert et y bâtit le modèle."""
    data = dict(tiles=TILES, groups=m.groups, cubes=m.cubes)
    return """(()=>{const D=%s;
Outliner.root.slice().forEach(n=>n.remove&&n.remove(false));Texture.all.slice().forEach(t=>t.remove(true));
const tex={};for(const [k,col] of Object.entries(D.tiles)){const cv=document.createElement('canvas');cv.width=cv.height=16;
const g=cv.getContext('2d');g.fillStyle=col;g.fillRect(0,0,16,16);for(let i=0;i<40;i++){g.fillStyle='rgba(0,0,0,0.08)';g.fillRect((i*7)%%16,(i*11)%%16,1,1);}
tex[k]=new Texture({name:k.toLowerCase()}).fromDataURL(cv.toDataURL()).add(false);}
const groups={};for(const [n,[parent,pivot]] of Object.entries(D.groups)){groups[n]=new Group({name:n,origin:pivot});
if(parent)groups[n].addTo(groups[parent]);groups[n].init();}
for(const c of D.cubes){const to=c.frm.map((v,i)=>v+c.size[i]);const cube=new Cube({name:c.name,from:c.frm,to:to,origin:c.pivot,rotation:c.rot}).addTo(groups[c.group]).init();cube.applyTexture(tex[c.tile],true);}
Canvas.updateAll();return D.cubes.length;})()""" % json.dumps(data)


def main():
    models = {"cart": cart(), "stall": stall()}
    write_cart(models["cart"])
    write_stall(models["stall"])
    if "--blockbench" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--blockbench") + 1])
        out.mkdir(parents=True, exist_ok=True)
        for name, m in models.items():
            (out / f"{name}.js").write_text(blockbench(m), encoding="utf-8")
    print("modèles écrits :", ", ".join(f"{n} ({len(m.cubes)} cubes)" for n, m in models.items()))


if __name__ == "__main__":
    main()
