package com.ajustor.minecities.client.render;

import com.ajustor.minecities.network.GhostPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le fantôme des bâtiments proposés (brief 09) : les blocs du plan, dessinés avec leur texture mais
 * en transparence et teintés de bleu, là où le bâtiment s'élèvera. Les blocs déjà en place ne se
 * dessinent pas.
 */
public final class GhostRenderer {
	/** Au-delà, on ne dessine pas le fantôme. */
	private static final double VIEW = 96;
	private static final int RED = 170;
	private static final int GREEN = 205;
	private static final int BLUE = 255;
	private static final int ALPHA = 130;
	private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
			Direction.WEST, Direction.EAST, null};

	/** Les fantômes affichés, par joueur qui les propose. */
	private static final Map<UUID, List<GhostPayload.Block>> GHOSTS = new ConcurrentHashMap<>();

	private GhostRenderer() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(GhostPayload.TYPE, (payload, context) -> {
			if (payload.blocks().isEmpty()) {
				GHOSTS.remove(payload.owner());
			} else {
				GHOSTS.put(payload.owner(), List.copyOf(payload.blocks()));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GHOSTS.clear());
		LevelRenderEvents.COLLECT_SUBMITS.register(GhostRenderer::submit);
	}

	/** Combien de fantômes sont affichés (pour les scènes). */
	public static int count() {
		return GHOSTS.size();
	}

	private static void submit(LevelRenderContext context) {
		if (GHOSTS.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		List<GhostPayload.Block> blocks = new ArrayList<>();
		for (List<GhostPayload.Block> ghost : GHOSTS.values()) {
			for (GhostPayload.Block block : ghost) {
				BlockPos pos = block.pos();
				if (camera.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= VIEW * VIEW
						&& !level.getBlockState(pos).equals(block.state())) {
					blocks.add(block);
				}
			}
		}
		if (blocks.isEmpty()) {
			return;
		}
		BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
		PoseStack poses = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poses, Sheets.translucentBlockItemSheet(),
				(pose, consumer) -> {
					RandomSource random = RandomSource.create();
					List<BlockStateModelPart> parts = new ArrayList<>();
					for (GhostPayload.Block block : blocks) {
						parts.clear();
						random.setSeed(block.pos().asLong());
						models.get(block.state()).collectParts(random, parts);
						float x = (float) (block.pos().getX() - camera.x);
						float y = (float) (block.pos().getY() - camera.y);
						float z = (float) (block.pos().getZ() - camera.z);
						for (BlockStateModelPart part : parts) {
							for (Direction side : SIDES) {
								for (BakedQuad quad : part.getQuads(side)) {
									quad(pose, consumer, quad, x, y, z);
								}
							}
						}
					}
				});
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer consumer, BakedQuad quad, float x, float y, float z) {
		Direction facing = quad.direction();
		for (int i = 0; i < 4; i++) {
			Vector3fc corner = quad.position(i);
			long uv = quad.packedUV(i);
			consumer.addVertex(pose, x + corner.x(), y + corner.y(), z + corner.z())
					.setColor(RED, GREEN, BLUE, ALPHA)
					.setUv(UVPair.unpackU(uv), UVPair.unpackV(uv))
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(LightCoordsUtil.FULL_BRIGHT)
					.setNormal(pose, facing.getStepX(), facing.getStepY(), facing.getStepZ());
		}
	}
}
