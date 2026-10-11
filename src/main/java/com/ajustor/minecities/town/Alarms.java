package com.ajustor.minecities.town;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.Optional;

/**
 * La cloche d'alarme (brief 06) : sonner une cloche vanilla sur le territoire donne l'alerte. Les
 * citoyens rentrent chez eux, les gardes accourent à l'hôtel de ville, l'inventeur fait ses golems,
 * et les monstres alentour sont repérés (ils luisent), comme les pillards au son de la cloche d'un
 * village. La cloche, sonnée à nouveau, lève l'alerte ; sinon elle tombe d'elle-même.
 */
public final class Alarms {
	/** L'alerte dure deux minutes si personne ne la lève. */
	public static final long DURATION = 2400L;
	/** Les monstres à tant de blocs de la cloche sont repérés. */
	private static final double SPOTTING = 48;

	private Alarms() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && level instanceof ServerLevel serverLevel
					&& player instanceof ServerPlayer serverPlayer && !player.isSpectator()
					&& level.getBlockState(hit.getBlockPos()).is(Blocks.BELL)) {
				ring(serverLevel, hit.getBlockPos(), serverPlayer);
			}
			// La cloche sonne quand même, comme d'habitude.
			return InteractionResult.PASS;
		});
	}

	/** Une cloche sonne : l'alerte est donnée sur le territoire où elle est, ou levée si elle l'était. */
	public static void ring(ServerLevel level, BlockPos bell, ServerPlayer player) {
		Optional<Town> found = Towns.get(level.getServer()).at(level.dimension(), bell);
		if (found.isEmpty()) {
			return;
		}
		Town town = found.get();
		long now = level.getGameTime();
		if (town.chronicle().alarmed(now)) {
			town.chronicle().alarm(0);
			tell(level, town, Component.translatable("message.minecities.alarm.over", town.name())
					.withStyle(ChatFormatting.GREEN));
		} else {
			town.chronicle().alarm(now + DURATION);
			tell(level, town, Component.translatable("message.minecities.alarm", player.getDisplayName(), town.name())
					.withStyle(ChatFormatting.RED));
			for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(bell).inflate(SPOTTING),
					m -> m instanceof Enemy && m.isAlive())) {
				mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200));
			}
		}
		Towns.get(level.getServer()).changed();
	}

	/** Dit aux joueurs du territoire, et au maire où qu'il soit. */
	private static void tell(ServerLevel level, Town town, Component message) {
		for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
			if (town.canManage(p.getUUID()) || town.contains(p.level().dimension(), p.blockPosition())) {
				p.sendSystemMessage(message);
			}
		}
	}
}
