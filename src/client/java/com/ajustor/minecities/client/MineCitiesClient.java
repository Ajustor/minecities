package com.ajustor.minecities.client;

import com.ajustor.minecities.client.render.CitizenRenderer;
import com.ajustor.minecities.registry.McEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class MineCitiesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(McEntities.CITIZEN, CitizenRenderer::new);
	}
}
