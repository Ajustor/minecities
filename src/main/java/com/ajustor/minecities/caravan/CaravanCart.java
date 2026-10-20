package com.ajustor.minecities.caravan;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Le chariot d'une caravane (MC-80) : la part visible de sa cargaison, tirée par un cheval et conduit
 * par le marchand, assis sur le banc du cocher. Il n'existe que près d'un joueur ; c'est la caravane
 * ({@link Caravans}) qui le place, pas lui qui se déplace. La nuit, il devient un camp : tente et feu
 * de bois, derrière le chariot.
 */
public class CaravanCart extends Entity {
	/** Le feu du camp, dans le repère du chariot (en blocs : x vers sa gauche, z vers l'avant). */
	public static final double FIRE_X = 0;
	public static final double FIRE_Z = -3.75;
	/** Où s'assoit le marchand, près du feu. */
	public static final double SEAT_X = -1.3;
	public static final double SEAT_Z = -4.6;
	/**
	 * Le banc du cocher (voir CaravanCartModel), où le marchand conduit : un peu à gauche, à l'avant. Un
	 * citoyen assis a les hanches à trois quarts de bloc de ses pieds : on l'abaisse d'autant.
	 */
	private static final Vec3 DRIVER = new Vec3(4 / 16.0, 30.5 / 16.0 - 0.75, 34.5 / 16.0);

	/** Le chemin parcouru (côté client), pour faire tourner les roues. */
	private float rolled;
	private float rolledBefore;
	/** Où il était au tick d'avant (côté client) : la caravane le déplace entre deux ticks. */
	private double lastX = Double.NaN;
	private double lastZ;
	private static final EntityDataAccessor<Boolean> CAMPING = SynchedEntityData.defineId(CaravanCart.class,
			EntityDataSerializers.BOOLEAN);

	public CaravanCart(EntityType<?> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(CAMPING, false);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			// Les roues tournent d'autant qu'il a roulé, en avant ou en arrière.
			if (Double.isNaN(lastX)) {
				lastX = getX();
				lastZ = getZ();
			}
			double dx = getX() - lastX;
			double dz = getZ() - lastZ;
			lastX = getX();
			lastZ = getZ();
			double yaw = Math.toRadians(getYRot());
			double forward = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
			rolledBefore = rolled;
			rolled += (float) (Math.signum(forward) * Math.sqrt(dx * dx + dz * dz));
		}
	}

	/** Le chemin parcouru, à cet instant entre deux ticks (côté client). */
	public float rolled(float partialTicks) {
		return rolledBefore + (rolled - rolledBefore) * partialTicks;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().isEmpty();
	}

	/** Le marchand conduit, assis sur le banc du cocher. */
	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, net.minecraft.world.entity.EntityDimensions dimensions,
			float scale) {
		double yaw = Math.toRadians(getYRot());
		return new Vec3(DRIVER.x * Math.cos(yaw) - DRIVER.z * Math.sin(yaw), DRIVER.y,
				DRIVER.x * Math.sin(yaw) + DRIVER.z * Math.cos(yaw));
	}

	public boolean camping() {
		return entityData.get(CAMPING);
	}

	public void setCamping(boolean camping) {
		entityData.set(CAMPING, camping);
	}

	/** Un joueur fouille le chariot : possible seulement quand plus personne ne le défend. */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player instanceof ServerPlayer serverPlayer) {
			Caravans.loot(serverPlayer, this);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
