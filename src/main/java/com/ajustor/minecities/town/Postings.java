package com.ajustor.minecities.town;

import com.ajustor.minecities.citizen.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Renvoyer un citoyen à son poste (MC-19) : depuis l'interface, le maire (ou un adjoint) le fait
 * apparaître près de son poste de travail, sur une case sûre — un sol solide, deux cases libres, ni
 * eau ni lave, personne dessus. Son emploi ne change pas.
 */
public final class Postings {
	/** On cherche une place jusqu'à deux blocs autour du poste, un bloc plus haut ou plus bas. */
	private static final int RANGE = 2;

	private Postings() {
	}

	/** Le résultat : le citoyen est arrivé, ou pourquoi il n'est pas parti. */
	public record Outcome(boolean sent, Component message) {
	}

	/** Envoie ce citoyen de la ville à son poste. */
	public static Outcome send(ServerLevel level, Town town, UUID id) {
		Optional<Resident> resident = town.resident(id);
		if (resident.isEmpty()) {
			return new Outcome(false, Component.translatable("message.minecities.posting.unknown"));
		}
		Optional<Resident.Employment> job = resident.get().job();
		if (job.isEmpty()) {
			return new Outcome(false, Component.translatable("message.minecities.posting.jobless", resident.get().name()));
		}
		if (!(level.getEntity(id) instanceof CitizenEntity citizen) || !citizen.isAlive()) {
			return new Outcome(false, Component.translatable("message.minecities.locate.away", resident.get().name()));
		}
		BlockPos station = job.get().station();
		Optional<BlockPos> spot = spot(level, citizen, station);
		if (spot.isEmpty()) {
			return new Outcome(false, Component.translatable("message.minecities.posting.no_room", resident.get().name(),
					station.toShortString()));
		}
		if (citizen.isSleeping()) {
			citizen.stopSleeping();
		}
		level.sendParticles(ParticleTypes.PORTAL, citizen.getX(), citizen.getY() + 1, citizen.getZ(), 20, 0.3, 0.6, 0.3, 0.2);
		BlockPos to = spot.get();
		citizen.getNavigation().stop();
		citizen.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
		citizen.getLookControl().setLookAt(station.getX() + 0.5, station.getY() + 0.5, station.getZ() + 0.5);
		level.sendParticles(ParticleTypes.PORTAL, to.getX() + 0.5, to.getY() + 1, to.getZ() + 0.5, 20, 0.3, 0.6, 0.3, 0.2);
		level.playSound(null, to, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.5f, 1.2f);
		return new Outcome(true, Component.translatable("message.minecities.posting.sent", citizen.getName(),
				to.toShortString()));
	}

	/** La case sûre la plus proche du poste, s'il y en a une. */
	public static Optional<BlockPos> spot(ServerLevel level, CitizenEntity citizen, BlockPos station) {
		List<BlockPos> candidates = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(station.offset(-RANGE, -1, -RANGE), station.offset(RANGE, 1, RANGE))) {
			if (!pos.equals(station) && safe(level, citizen, pos)) {
				candidates.add(pos.immutable());
			}
		}
		return candidates.stream().min(Comparator.comparingDouble(p -> p.distSqr(station)));
	}

	/** Un sol solide dessous, deux cases sans collision ni liquide, et personne d'autre. */
	private static boolean safe(ServerLevel level, CitizenEntity citizen, BlockPos pos) {
		if (!level.isLoaded(pos) || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
			return false;
		}
		for (BlockPos p : new BlockPos[]{pos, pos.above()}) {
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty()) {
				return false;
			}
		}
		AABB box = citizen.getType().getDimensions().makeBoundingBox(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		return level.noCollision(citizen, box) && level.getEntities(citizen, box, e -> e.isAlive() && e.isPickable()).isEmpty();
	}
}
