package com.ajustor.minecities.client.render;

import com.ajustor.minecities.town.Job;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.util.EnumMap;
import java.util.Map;

/**
 * Le couvre-chef de chaque métier, posé sur la tête du citoyen : chapeau de paille du fermier, toque
 * du cuisinier, bonnet du bûcheron, casquette de l'éleveur, bob du pêcheur, calot du bâtisseur,
 * casquette à visière du livreur, chapeau de fer du garde, chapeau à plume de l'archer, lunettes de
 * l'inventeur, bandeau à croix du médecin, calot du boulanger, calotte du forgeron, casque à lampe du
 * mineur…
 *
 * <p>Même squelette que le citoyen (il en suit les gestes) ; seuls les chapeaux, enfants de la tête,
 * ont des cubes. La texture (textures/entity/citizen/hats.png, générée par tools/textures/generate.py)
 * est faite de pavés d'une couleur : chaque cube prend la sienne.
 */
public class CitizenHatModel extends CitizenModel {
	/** La texture : 256 x 256, des pavés de 64 x 32. */
	public static final int TEXTURE_SIZE = 256;

	/** Les couleurs des pavés, dans l'ordre de la texture (voir tools/textures/generate.py). */
	enum Tile { STRAW, STRAW_DARK, WHITE, RED, BROWN, DENIM, ORANGE, BLUE, IRON, GREEN, GLASS, LEATHER, BLACK, GOLD }

	private final Map<Job, ModelPart> hats = new EnumMap<>(Job.class);

	public CitizenHatModel(ModelPart root) {
		super(root);
		for (Job job : Job.values()) {
			hats.put(job, head.getChild(name(job)));
		}
	}

	private static String name(Job job) {
		return "hat_" + job.serializedName();
	}

	@Override
	public void setupAnim(CitizenRenderer.State state) {
		super.setupAnim(state);
		hats.forEach((job, part) -> part.visible = job.ordinal() == state.job);
	}

	private static CubeListBuilder cube(Tile tile) {
		return CubeListBuilder.create().texOffs((tile.ordinal() % 4) * 64, (tile.ordinal() / 4) * 32);
	}

	public static LayerDefinition create() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
		PartDefinition root = mesh.getRoot();
		// Rien que la tête (sans cubes), pour y accrocher les chapeaux.
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		for (String limb : new String[]{"body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
			root.addOrReplaceChild(limb, CubeListBuilder.create(), PartPose.ZERO);
		}
		for (Job job : Job.values()) {
			PartDefinition hat = head.addOrReplaceChild(name(job), CubeListBuilder.create(), PartPose.ZERO);
			hat(job, hat);
		}
		return LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE);
	}

	/** Les cubes du chapeau de ce métier (la tête va de -4 à 4, de -8 à 0 en hauteur). */
	private static void hat(Job job, PartDefinition hat) {
		switch (job) {
			case FARMER -> {
				// Chapeau de paille à large bord.
				hat.addOrReplaceChild("brim", cube(Tile.STRAW).addBox(-7, -8.5f, -7, 14, 1, 14), PartPose.ZERO);
				hat.addOrReplaceChild("crown", cube(Tile.STRAW).addBox(-4.5f, -11, -4.5f, 9, 3, 9), PartPose.ZERO);
				hat.addOrReplaceChild("band", cube(Tile.STRAW_DARK).addBox(-4.6f, -9, -4.6f, 9.2f, 0.6f, 9.2f),
						PartPose.ZERO);
			}
			case COOK -> {
				// Toque blanche.
				hat.addOrReplaceChild("band", cube(Tile.WHITE).addBox(-4.5f, -10, -4.5f, 9, 2, 9), PartPose.ZERO);
				hat.addOrReplaceChild("puff", cube(Tile.WHITE).addBox(-5, -14, -5, 10, 4, 10), PartPose.ZERO);
			}
			case LUMBERJACK -> {
				// Bonnet de laine rouge à pompon.
				hat.addOrReplaceChild("cap", cube(Tile.RED).addBox(-4.5f, -10, -4.5f, 9, 3, 9), PartPose.ZERO);
				hat.addOrReplaceChild("fold", cube(Tile.WHITE).addBox(-4.7f, -8.2f, -4.7f, 9.4f, 1, 9.4f),
						PartPose.ZERO);
				hat.addOrReplaceChild("pompon", cube(Tile.WHITE).addBox(-1, -12, -1, 2, 2, 2), PartPose.ZERO);
			}
			case BREEDER -> {
				// Casquette de cuir.
				hat.addOrReplaceChild("cap", cube(Tile.BROWN).addBox(-4.5f, -10, -4.5f, 9, 2, 9), PartPose.ZERO);
				hat.addOrReplaceChild("visor", cube(Tile.BROWN).addBox(-4, -8.5f, -7.5f, 8, 0.5f, 3), PartPose.ZERO);
			}
			case FISHERMAN -> {
				// Bob de toile.
				hat.addOrReplaceChild("crown", cube(Tile.DENIM).addBox(-4.5f, -10.5f, -4.5f, 9, 3, 9), PartPose.ZERO);
				hat.addOrReplaceChild("brim", cube(Tile.DENIM).addBox(-6, -8, -6, 12, 0.5f, 12), PartPose.ZERO);
			}
			case BUILDER -> {
				// Calot orange à petit bord.
				hat.addOrReplaceChild("cap", cube(Tile.ORANGE).addBox(-4.5f, -10.5f, -4.5f, 9, 2.5f, 9),
						PartPose.ZERO);
				hat.addOrReplaceChild("brim", cube(Tile.ORANGE).addBox(-5.5f, -8.5f, -5.5f, 11, 0.5f, 11),
						PartPose.ZERO);
			}
			case COURIER -> {
				// Casquette bleue à visière, avec un insigne doré.
				hat.addOrReplaceChild("cap", cube(Tile.BLUE).addBox(-4.5f, -10, -4.5f, 9, 2, 9), PartPose.ZERO);
				hat.addOrReplaceChild("visor", cube(Tile.BLACK).addBox(-3.5f, -8.5f, -7, 7, 0.5f, 2.5f),
						PartPose.ZERO);
				hat.addOrReplaceChild("badge", cube(Tile.GOLD).addBox(-1, -9.8f, -4.8f, 2, 1.5f, 0.5f),
						PartPose.ZERO);
			}
			case GUARD -> {
				// Chapeau de fer.
				hat.addOrReplaceChild("crown", cube(Tile.IRON).addBox(-4.5f, -11, -4.5f, 9, 3, 9), PartPose.ZERO);
				hat.addOrReplaceChild("brim", cube(Tile.IRON).addBox(-6, -8.5f, -6, 12, 0.5f, 12), PartPose.ZERO);
			}
			case ARCHER -> {
				// Chapeau vert pointu, à plume rouge.
				hat.addOrReplaceChild("base", cube(Tile.GREEN).addBox(-4.5f, -10, -4.5f, 9, 2, 9), PartPose.ZERO);
				hat.addOrReplaceChild("tip", cube(Tile.GREEN).addBox(-3, -12, -3.5f, 6, 2, 7), PartPose.ZERO);
				hat.addOrReplaceChild("feather", cube(Tile.RED).addBox(3.5f, -14, -1, 0.5f, 5, 2),
						PartPose.rotation(0, 0, 0.3f));
			}
			case INVENTOR -> {
				// Lunettes relevées sur le front.
				hat.addOrReplaceChild("strap", cube(Tile.LEATHER).addBox(-4.4f, -7.5f, -4.4f, 8.8f, 1, 8.8f),
						PartPose.ZERO);
				hat.addOrReplaceChild("left", cube(Tile.GLASS).addBox(-3.5f, -8, -5, 3, 2, 1), PartPose.ZERO);
				hat.addOrReplaceChild("right", cube(Tile.GLASS).addBox(0.5f, -8, -5, 3, 2, 1), PartPose.ZERO);
			}
			case MERCHANT -> {
				// Haut chapeau de feutre à ruban doré.
				hat.addOrReplaceChild("crown", cube(Tile.BLUE).addBox(-4, -13, -4, 8, 5, 8), PartPose.ZERO);
				hat.addOrReplaceChild("ribbon", cube(Tile.GOLD).addBox(-4.2f, -9.5f, -4.2f, 8.4f, 1, 8.4f),
						PartPose.ZERO);
				hat.addOrReplaceChild("brim", cube(Tile.BLUE).addBox(-6, -8.5f, -6, 12, 0.5f, 12), PartPose.ZERO);
			}
			case BAKER -> {
				// Calot blanc de boulanger, un peu de farine en dessus.
				hat.addOrReplaceChild("band", cube(Tile.WHITE).addBox(-4.5f, -10, -4.5f, 9, 2, 9), PartPose.ZERO);
				hat.addOrReplaceChild("top", cube(Tile.WHITE).addBox(-5, -11, -5, 10, 1, 10),
						PartPose.rotation(0.08f, 0, 0.05f));
			}
			case SMITH -> {
				// Calotte de cuir et bandeau noirci par la forge.
				hat.addOrReplaceChild("cap", cube(Tile.LEATHER).addBox(-4.5f, -10, -4.5f, 9, 2.5f, 9), PartPose.ZERO);
				hat.addOrReplaceChild("band", cube(Tile.BLACK).addBox(-4.7f, -8.2f, -4.7f, 9.4f, 1, 9.4f),
						PartPose.ZERO);
			}
			case MINER -> {
				// Casque de cuir à bord, une lampe sur le front.
				hat.addOrReplaceChild("cap", cube(Tile.BROWN).addBox(-4.5f, -10.5f, -4.5f, 9, 2.5f, 9),
						PartPose.ZERO);
				hat.addOrReplaceChild("brim", cube(Tile.BROWN).addBox(-5.5f, -8.3f, -5.5f, 11, 0.5f, 11),
						PartPose.ZERO);
				hat.addOrReplaceChild("lamp", cube(Tile.GOLD).addBox(-1, -10, -5.2f, 2, 2, 1), PartPose.ZERO);
			}
			case DOCTOR -> {
				// Bandeau blanc à croix rouge.
				hat.addOrReplaceChild("band", cube(Tile.WHITE).addBox(-4.4f, -8, -4.4f, 8.8f, 1.5f, 8.8f),
						PartPose.ZERO);
				hat.addOrReplaceChild("cross_h", cube(Tile.RED).addBox(-1.5f, -7.6f, -4.6f, 3, 0.8f, 0.3f),
						PartPose.ZERO);
				hat.addOrReplaceChild("cross_v", cube(Tile.RED).addBox(-0.4f, -8.6f, -4.6f, 0.8f, 2.8f, 0.3f),
						PartPose.ZERO);
			}
		}
	}
}
