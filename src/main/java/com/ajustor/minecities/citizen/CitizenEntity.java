package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Names;
import com.ajustor.minecities.town.Resident;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.InteractGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Optional;
import java.util.UUID;

/**
 * Un citoyen. Il vit dans sa ville : il s'y promène le jour, dort dans son lit la nuit, mange quand
 * il a faim (dans les coffres de sa maison, sinon au grenier de l'hôtel de ville) et s'en va s'il y
 * meurt de faim trop longtemps. La ville tient son nom, sa maison et son lit ({@link Resident}) ;
 * lui garde sa faim et son apparence.
 */
public class CitizenEntity extends PathfinderMob {
	/** Le nombre de tenues : les peaux textures/entity/citizen/citizen_N.png. */
	public static final int VARIANTS = 8;
	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(CitizenEntity.class,
			EntityDataSerializers.INT);
	/** Le citoyen relit ce que la ville sait de lui toutes les 5 secondes. */
	private static final int SYNC_INTERVAL = 100;

	private UUID townId;
	private float hunger = Hunger.SATED;
	private int starvingTicks;
	/** Ce que la ville sait de lui, relu régulièrement (vide s'il n'a pas de ville). */
	private Optional<Resident> resident = Optional.empty();
	private final DoorKeeper doors = new DoorKeeper(this);

	public CitizenEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		getNavigation().setCanOpenDoors(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20)
				.add(Attributes.MOVEMENT_SPEED, 0.5)
				.add(Attributes.FOLLOW_RANGE, 48);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(VARIANT, 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new PanicGoal(this, 0.75));
		goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Monster.class, 8.0f, 0.6, 0.75));
		goalSelector.addGoal(2, new SleepInBedGoal(this));
		goalSelector.addGoal(3, new EatGoal(this));
		goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.6));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.5));
		goalSelector.addGoal(7, new InteractGoal(this, CitizenEntity.class, 6.0f, 0.03f));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	/** Prépare un nouveau venu, avant son entrée dans le monde. */
	public void joinTown(Town town, String name) {
		townId = town.id();
		setCustomName(Component.literal(name));
		setVariant(getRandom().nextInt(VARIANTS));
		setHomeTo(town.hall(), town.radius());
	}

	public int variant() {
		return entityData.get(VARIANT);
	}

	public void setVariant(int variant) {
		entityData.set(VARIANT, Math.floorMod(variant, VARIANTS));
	}

	public Optional<UUID> townId() {
		return Optional.ofNullable(townId);
	}

	public Optional<Town> town() {
		return level() instanceof ServerLevel serverLevel ? Towns.get(serverLevel.getServer()).byId(townId)
				: Optional.empty();
	}

	public Optional<Resident> resident() {
		return resident;
	}

	public float hunger() {
		return hunger;
	}

	public void setHunger(float hunger) {
		this.hunger = Math.clamp(hunger, 0, Hunger.MAX);
	}

	/** Mange un aliment : la faim se comble, et ça se voit et s'entend. */
	public void eat(ItemStack food) {
		FoodProperties properties = food.get(DataComponents.FOOD);
		if (properties == null) {
			return;
		}
		setHunger(Hunger.eat(hunger, properties.nutrition()));
		starvingTicks = 0;
		swing(InteractionHand.MAIN_HAND);
		playSound(SoundEvents.GENERIC_EAT.value(), 0.6f, 0.9f + getRandom().nextFloat() * 0.2f);
		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, food.getItem()), getX(), getEyeY() - 0.2,
					getZ(), 6, 0.15, 0.1, 0.15, 0.05);
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		doors.tick();
		if (tickCount % 20 != 0) {
			return;
		}
		if (tickCount % SYNC_INTERVAL == 0 && !syncWithTown(serverLevel)) {
			return;
		}
		if (resident.isEmpty()) {
			// Un vagabond (venu d'un œuf, ou d'une ville disparue) : il n'a pas faim de nos règles.
			return;
		}
		setHunger(Hunger.decay(hunger, 20));
		if (hunger <= 0) {
			starve(serverLevel);
		} else {
			starvingTicks = 0;
			if (hunger >= Hunger.WELL_FED && tickCount % 100 == 0 && getHealth() < getMaxHealth()) {
				heal(1);
			}
		}
	}

	private void starve(ServerLevel level) {
		starvingTicks += 20;
		addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, false, false));
		// Comme un joueur, il dépérit : un cœur toutes les {@link Hunger#STARVATION_DAMAGE_INTERVAL} ticks,
		// jusqu'à en mourir si personne ne le nourrit.
		if (starvingTicks % Hunger.STARVATION_DAMAGE_INTERVAL == 0) {
			hurtServer(level, damageSources().starve(), 2);
		}
	}

	/**
	 * Relit ce que la ville sait de lui. Sans ville (un œuf), il rejoint celle où il se trouve. Si sa
	 * ville a disparu ou l'a oublié, il s'en va. Renvoie faux s'il est parti.
	 */
	private boolean syncWithTown(ServerLevel level) {
		Towns towns = Towns.get(level.getServer());
		if (townId == null) {
			Optional<Town> here = towns.at(level.dimension(), blockPosition());
			if (here.isEmpty()) {
				return true;
			}
			Town town = here.get();
			String name = hasCustomName() ? getCustomName().getString()
					: Names.citizen(getRandom()::nextInt);
			joinTown(town, name);
			town.welcome(getUUID(), name, level.getGameTime());
			towns.changed();
			TownService.notify(level.getServer(), town,
					Component.translatable("message.minecities.citizen.arrived", name, town.name()));
		}
		Optional<Town> town = towns.byId(townId);
		Optional<Resident> record = town.flatMap(t -> t.resident(getUUID()));
		if (record.isEmpty()) {
			leaveTown(level, null);
			return false;
		}
		resident = record;
		setHomeTo(town.get().hall(), town.get().radius());
		return true;
	}

	/** Quitte la ville pour de bon (et le monde avec elle), en prévenant le maire si on le lui dit. */
	public void leaveTown(ServerLevel level, Component message) {
		if (townId != null && message != null) {
			TownService.farewell(level, townId, getUUID(), message);
		}
		level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 12, 0.3, 0.6, 0.3, 0.02);
		doors.closeAll();
		discard();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel serverLevel && townId != null) {
			TownService.farewell(serverLevel, townId, getUUID(),
					Component.translatable("message.minecities.citizen.died", getName()));
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (TownService.isFood(stack) && hunger < Hunger.MAX) {
			if (!level().isClientSide()) {
				eat(stack.copyWithCount(1));
				stack.consume(1, player);
			}
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
			describe(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	private void describe(ServerPlayer player) {
		Component home = resident.flatMap(Resident::home)
				.<Component>map(p -> Component.translatable("citizen.minecities.home", p.toShortString()))
				.orElse(Component.translatable("citizen.minecities.homeless"));
		Component town = town().<Component>map(t -> Component.literal(t.name()))
				.orElse(Component.translatable("citizen.minecities.no_town"));
		player.sendSystemMessage(Component.translatable("citizen.minecities.status", getName(), town,
				Component.translatable(Hunger.state(hunger).translationKey()), home).withStyle(ChatFormatting.AQUA));
	}

	public Optional<BlockPos> bed() {
		return resident.flatMap(Resident::bed);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (townId != null) {
			output.store("town", UUIDUtil.CODEC, townId);
		}
		output.putFloat("hunger", hunger);
		output.putInt("starving", starvingTicks);
		output.putInt("variant", variant());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		townId = input.read("town", UUIDUtil.CODEC).orElse(null);
		hunger = input.getFloatOr("hunger", Hunger.SATED);
		starvingTicks = input.getIntOr("starving", 0);
		setVariant(input.getIntOr("variant", 0));
	}
}
