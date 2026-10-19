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
		com.ajustor.minecities.client.render.GhostRenderer.register();
		Guide.opener = player -> Minecraft.getInstance().gui.setScreen(
				new BookViewScreen(new BookViewScreen.BookAccess(Guide.pages())));
		ClientPlayNetworking.registerGlobalReceiver(com.ajustor.minecities.network.StationSnapshot.TYPE, (payload, context) -> {
			// L'écran du même poste se rafraîchit ; sinon on l'ouvre.
			if (context.client().gui.screen() instanceof com.ajustor.minecities.client.screen.StationScreen open
					&& open.shows(payload)) {
				open.refresh(payload);
			} else {
				context.client().gui.setScreen(new com.ajustor.minecities.client.screen.StationScreen(payload));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(com.ajustor.minecities.network.PlanNetwork.PlanSnapshot.TYPE,
				(payload, context) -> context.client().gui.setScreen(
						new com.ajustor.minecities.client.screen.PlanScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(com.ajustor.minecities.network.BuildingSnapshot.TYPE, (payload, context) -> {
			// Une commande rafraîchit l'interface déjà ouverte ; sinon on l'ouvre.
			if (context.client().gui.screen() instanceof com.ajustor.minecities.client.screen.BuildingScreen open
					&& open.shows(payload)) {
				open.refresh(payload);
			} else {
				context.client().gui.setScreen(new com.ajustor.minecities.client.screen.BuildingScreen(payload));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(OpenTownPayload.TYPE, (payload, context) -> {
			// Une décision du maire rafraîchit l'écran déjà ouvert ; sinon on l'ouvre.
			if (context.client().gui.screen() instanceof TownHallScreen open && open.shows(payload.town())) {
				open.refresh(payload.town());
			} else if (context.client().gui.screen() instanceof com.ajustor.minecities.client.screen.TownMapScreen map
					&& map.shows(payload.town())) {
				map.refresh(payload.town());
			} else {
				context.client().gui.setScreen(new TownHallScreen(payload.town()));
			}
		});
	}
}
