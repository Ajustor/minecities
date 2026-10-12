package com.ajustor.minecities.compat.jade;

import com.ajustor.minecities.block.HousePlaqueBlock;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.compat.CitizenInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * L'intégration Jade, facultative : seul Jade charge cette classe (point d'entrée {@code jade} de
 * fabric.mod.json). Sans Jade, rien de ce paquet ne se charge. Il est rangé avec le code du client parce
 * que l'infobulle en dépend ; sur un serveur, Jade n'en appelle que {@link #register}, qui ne touche
 * qu'au relevé du citoyen.
 *
 * <ul>
 * <li>un citoyen : sa faim et s'il est logé, relevés par le serveur ({@link CitizenInfo}) ;</li>
 * <li>une plaque de maison : l'état de son dernier relevé, que le client connaît par l'état du bloc.</li>
 * </ul>
 */
@WailaPlugin
public class MineCitiesJadePlugin implements IWailaPlugin {
	static final Identifier CITIZEN = Identifier.fromNamespaceAndPath("minecities", "citizen");
	static final Identifier PLAQUE = Identifier.fromNamespaceAndPath("minecities", "house_plaque");

	@Override
	public void register(IWailaCommonRegistration registration) {
		registration.registerEntityDataProvider(Citizen.INSTANCE, CitizenEntity.class);
	}

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.registerEntityComponent(CitizenTooltip.INSTANCE, CitizenEntity.class);
		registration.registerBlockComponent(Plaque.INSTANCE, HousePlaqueBlock.class);
	}

	/** Le citoyen, côté serveur : il relève {@link CitizenInfo} et l'envoie. */
	static class Citizen implements StreamServerDataProvider<EntityAccessor, CitizenInfo> {
		static final Citizen INSTANCE = new Citizen();

		@Override
		public CitizenInfo streamData(EntityAccessor accessor) {
			return accessor.getEntity() instanceof CitizenEntity citizen ? CitizenInfo.of(citizen) : null;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, CitizenInfo> streamCodec() {
			return CitizenInfo.STREAM_CODEC.cast();
		}

		@Override
		public Identifier getUid() {
			return CITIZEN;
		}
	}

	/**
	 * Le citoyen, côté client : ce que le serveur a relevé, en deux lignes. Jade veut un fournisseur
	 * d'infobulle distinct du fournisseur de données ; il hérite de celui-ci pour relire ses données.
	 */
	static final class CitizenTooltip extends Citizen implements IEntityComponentProvider {
		static final CitizenTooltip INSTANCE = new CitizenTooltip();

		@Override
		public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
			decodeFromData(accessor).ifPresent(info -> info.lines().forEach(tooltip::add));
		}
	}

	/** La plaque de maison : l'état du bloc suffit, il est le même des deux côtés. */
	static final class Plaque implements IBlockComponentProvider {
		static final Plaque INSTANCE = new Plaque();

		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			if (accessor.getBlockState().hasProperty(HousePlaqueBlock.STATUS)) {
				tooltip.add(CitizenInfo.plaque(accessor.getBlockState().getValue(HousePlaqueBlock.STATUS)));
			}
		}

		@Override
		public Identifier getUid() {
			return PLAQUE;
		}
	}
}
