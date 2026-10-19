package com.ajustor.minecities.client.screen;

import com.ajustor.minecities.network.PlanNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le coût des schémas, pour l'infobulle des plans (MC-76) : demandé au serveur au premier survol, gardé
 * dix secondes (les réserves changent), puis redemandé.
 */
public final class PlanCosts {
	private static final long FRESH = 10_000;
	private static final int GOOD = 0xFF2E7D32;
	private static final int BAD = 0xFFB0201A;
	private static final Map<String, PlanNetwork.CostPayload> COSTS = new HashMap<>();
	private static final Map<String, Long> ASKED = new HashMap<>();

	private PlanCosts() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PlanNetwork.CostPayload.TYPE,
				(payload, context) -> COSTS.put(payload.blueprint() + "|" + payload.style(), payload));
	}

	/** Les lignes de l'infobulle de ce schéma dans ce style ; le temps du calcul, une ligne d'attente. */
	public static List<Component> tooltip(String blueprint, String style) {
		String key = blueprint + "|" + style;
		long now = System.currentTimeMillis();
		Long asked = ASKED.get(key);
		if (asked == null || now - asked > FRESH) {
			ASKED.put(key, now);
			ClientPlayNetworking.send(new PlanNetwork.CostRequest(blueprint, style));
		}
		PlanNetwork.CostPayload cost = COSTS.get(key);
		List<Component> lines = new ArrayList<>();
		if (cost == null) {
			lines.add(Component.translatable("screen.minecities.plans.cost.loading"));
			return lines;
		}
		lines.add(cost.emeralds() > 0 ? Component.translatable("screen.minecities.plans.cost.emeralds", cost.emeralds())
				: Component.translatable("screen.minecities.plans.cost.free"));
		for (PlanNetwork.Material m : cost.materials()) {
			Identifier id = Identifier.tryParse(m.item());
			Component name = id == null ? Component.literal(m.item())
					: Component.translatable(BuiltInRegistries.ITEM.getValue(id).getDescriptionId());
			lines.add(Component.translatable("screen.minecities.plans.cost.material", m.needed(), name, m.stocked())
					.withColor(m.stocked() >= m.needed() ? GOOD : BAD));
		}
		return lines;
	}
}
