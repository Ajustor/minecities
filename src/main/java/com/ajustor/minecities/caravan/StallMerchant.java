package com.ajustor.minecities.caravan;

import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Le marchand d'une échoppe, le temps d'un troc avec un joueur (l'écran des villageois) : chaque
 * échange retire un lot de l'étal et met les émeraudes à la caisse ({@link Stall#sell}).
 */
public final class StallMerchant implements Merchant {
	/** Au-delà de cette distance de l'échoppe, le troc s'arrête. */
	private static final double REACH = 8;

	private final MinecraftServer server;
	private final UUID stall;
	private final Vec3 at;
	private MerchantOffers offers;
	private Player trading;

	StallMerchant(MinecraftServer server, Stall stall) {
		this.server = server;
		this.stall = stall.id();
		this.at = net.minecraft.world.phys.Vec3.atBottomCenterOf(stall.pos());
		this.offers = stall.offers();
	}

	@Override
	public void setTradingPlayer(Player player) {
		trading = player;
	}

	@Override
	public Player getTradingPlayer() {
		return trading;
	}

	@Override
	public MerchantOffers getOffers() {
		return offers;
	}

	@Override
	public void overrideOffers(MerchantOffers offers) {
		this.offers = offers;
	}

	@Override
	public void notifyTrade(MerchantOffer offer) {
		offer.increaseUses();
		Caravans.get(server).sold(stall, offer.getResult().getItem());
	}

	@Override
	public void notifyTradeUpdated(ItemStack stack) {
	}

	@Override
	public int getVillagerXp() {
		return 0;
	}

	@Override
	public void overrideXp(int xp) {
	}

	@Override
	public boolean showProgressBar() {
		return false;
	}

	@Override
	public SoundEvent getNotifyTradeSound() {
		return SoundEvents.VILLAGER_YES;
	}

	@Override
	public boolean isClientSide() {
		return false;
	}

	@Override
	public boolean stillValid(Player player) {
		return trading == player && player.distanceToSqr(at) < REACH * REACH
				&& Caravans.get(server).stall(stall).isPresent();
	}
}
