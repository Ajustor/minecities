package com.ajustor.minecities;

import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.command.TownCommands;
import com.ajustor.minecities.guide.Guide;
import com.ajustor.minecities.network.TownNetwork;
import com.ajustor.minecities.registry.McBlockEntities;
import com.ajustor.minecities.registry.McBlocks;
import com.ajustor.minecities.registry.McEntities;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Alarms;
import com.ajustor.minecities.town.Borders;
import com.ajustor.minecities.town.Festivals;
import com.ajustor.minecities.town.Raids;
import com.ajustor.minecities.town.Trade;
import com.ajustor.minecities.town.TownService;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MineCities : des villes vivantes, à la manière de MineColonies mais dans l'esprit vanilla. On fonde
 * une ville en posant un hôtel de ville (on en devient le maire), on bâtit librement des maisons
 * qu'une plaque déclare, et des citoyens viennent s'y installer tant qu'il y a des lits et de quoi
 * manger. Le village devient ville, puis grande ville, puis mégalopole.
 */
public class MineCities implements ModInitializer {
	public static final String MOD_ID = "minecities";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		McConfig.load();
		McBlocks.register();
		McBlockEntities.register();
		McEntities.register();
		McItems.register();
		TownService.register();
		Blueprints.register();
		Raids.register();
		Festivals.register();
		Alarms.register();
		Borders.register();
		Trade.register();
		TownCommands.register();
		TownNetwork.register();
		Guide.register();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
