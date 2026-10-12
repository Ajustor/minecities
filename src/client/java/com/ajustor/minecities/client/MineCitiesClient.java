package com.ajustor.minecities.client;

import com.ajustor.minecities.client.render.CitizenRenderer;
import com.ajustor.minecities.client.screen.TownHallScreen;
import com.ajustor.minecities.guide.Guide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import com.ajustor.minecities.network.OpenTownPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.ajustor.minecities.registry.McEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class MineCitiesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(McEntities.CITIZEN, CitizenRenderer::new);
		Guide.opener = player -> Minecraft.getInstance().gui.setScreen(
				new BookViewScreen(new BookViewScreen.BookAccess(Guide.pages())));
		ClientPlayNetworking.registerGlobalReceiver(OpenTownPayload.TYPE, (payload, context) -> {
			// Une décision du maire rafraîchit l'écran déjà ouvert ; sinon on l'ouvre.
			if (context.client().gui.screen() instanceof TownHallScreen open && open.shows(payload.town())) {
				open.refresh(payload.town());
			} else {
				context.client().gui.setScreen(new TownHallScreen(payload.town()));
			}
		});
	}
}
