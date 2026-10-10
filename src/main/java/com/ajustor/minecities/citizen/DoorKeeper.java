package com.ajustor.minecities.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Le citoyen ouvre les portes en bois de son chemin et les referme derrière lui, comme les
 * villageois. Le but vanilla d'ouverture des portes n'agit qu'après s'être cogné à la porte, ce
 * qui n'arrive pas toujours : le citoyen restait planté devant.
 */
final class DoorKeeper {
	/** On regarde la porte des deux prochains pas du chemin. */
	private static final int LOOKAHEAD = 2;
	/** Une porte est refermée quand le citoyen s'en est éloigné de plus de 2 blocs. */
	private static final double CLOSE_DISTANCE_SQR = 2.0 * 2.0;

	private final Mob mob;
	private final Set<BlockPos> opened = new HashSet<>();

	DoorKeeper(Mob mob) {
		this.mob = mob;
	}

	void tick() {
		Level level = mob.level();
		Path path = mob.getNavigation().getPath();
		if (path != null && !path.isDone()) {
			int end = Math.min(path.getNextNodeIndex() + LOOKAHEAD, path.getNodeCount());
			for (int i = Math.max(0, path.getNextNodeIndex() - 1); i < end; i++) {
				BlockPos pos = path.getNodePos(i);
				if (mob.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) < CLOSE_DISTANCE_SQR) {
					setOpen(level, pos, true);
				}
			}
		}
		for (Iterator<BlockPos> it = opened.iterator(); it.hasNext(); ) {
			BlockPos pos = it.next();
			if (mob.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > CLOSE_DISTANCE_SQR) {
				if (setOpen(level, pos, false) || !DoorBlock.isWoodenDoor(level, pos)) {
					it.remove();
				}
			}
		}
	}

	/** Ouvre ou ferme la porte en bois en {@code pos}, s'il y en a une. Renvoie vrai si c'est fait. */
	private boolean setOpen(Level level, BlockPos pos, boolean open) {
		BlockState state = level.getBlockState(pos);
		if (!DoorBlock.isWoodenDoor(state) || !(state.getBlock() instanceof DoorBlock door)) {
			return false;
		}
		if (door.isOpen(state) != open) {
			door.setOpen(mob, level, state, pos, open);
		}
		if (open) {
			opened.add(pos.immutable());
		}
		return true;
	}

	/** Referme tout ce qu'il a laissé ouvert (quand il quitte le monde). */
	void closeAll() {
		for (BlockPos pos : opened) {
			setOpen(mob.level(), pos, false);
		}
		opened.clear();
	}
}
