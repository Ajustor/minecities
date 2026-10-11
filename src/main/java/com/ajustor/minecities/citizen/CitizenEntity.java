package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Names;
import com.ajustor.minecities.town.Raids;
import com.ajustor.minecities.town.Resident;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
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
import net.minecraft.world.item.Item;
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
	/** Un enfant : plus petit, il ne travaille pas et mange moins. */
	private static final EntityDataAccessor<Boolean> CHILD = SynchedEntityData.defineId(CitizenEntity.class,
			EntityDataSerializers.BOOLEAN);
	/** Ce qu'il fait (voir {@link Action}) : le client l'anime en conséquence. */
	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(CitizenEntity.class,
			EntityDataSerializers.INT);
	/** Ce qu'il dit en ce moment : affiché en bulle au-dessus de sa tête. */
	private static final EntityDataAccessor<Optional<Component>> SPEECH = SynchedEntityData.defineId(
			CitizenEntity.class, EntityDataSerializers.OPTIONAL_COMPONENT);
	/** Son métier (rang dans {@link com.ajustor.minecities.town.Job}, -1 sans) : le client lui met son couvre-chef. */
	private static final EntityDataAccessor<Integer> JOB = SynchedEntityData.defineId(CitizenEntity.class,
			EntityDataSerializers.INT);
	/** Son humeur, de 0 à 10 ({@link Happiness}). */
	private static final EntityDataAccessor<Integer> MOOD = SynchedEntityData.defineId(CitizenEntity.class,
			EntityDataSerializers.INT);
	/** Ce qui ne va pas (voir {@link Trouble}) : une petite icône au-dessus de sa tête. */
	private static final EntityDataAccessor<Integer> TROUBLE = SynchedEntityData.defineId(CitizenEntity.class,
			EntityDataSerializers.INT);
	/** Une réplique reste affichée 4 secondes ; on l'entend (dans le chat) à 16 blocs. */
	private static final int SPEECH_TICKS = 80;
	private static final double EARSHOT = 16;
	/** Il ne répète pas la même chose avant 30 secondes. */
	private static final int REPEAT_DELAY = 600;
	/** Le citoyen relit ce que la ville sait de lui toutes les 5 secondes. */
	private static final int SYNC_INTERVAL = 100;
	public static final int INVENTORY_SIZE = 9;

	private UUID townId;
	private float hunger = Hunger.SATED;
	private int starvingTicks;
	/** Ses derniers repas. */
	private Diet diet = new Diet();
	/** Son humeur au dernier calcul, et ce qui l'explique. */
	private Happiness.Mood mood = new Happiness.Mood(Happiness.BASE, java.util.List.of());
	/** Depuis combien de temps il est au plus mal : au bout d'un jour, il s'en va. */
	private int miserableTicks;
	/** Ce que la ville sait de lui, relu régulièrement (vide s'il n'a pas de ville). */
	private Optional<Resident> resident = Optional.empty();
	private final DoorKeeper doors = new DoorKeeper(this);
	/** Là où un bâtisseur lui a demandé d'aller, pour lui laisser la place. */
	private BlockPos stepAside;
	private int stepAsideUntil;
	/** Ce qu'il porte : récoltes, ingrédients, semences. */
	private final NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
	/** Quand il s'est plaint pour la dernière fois, par motif : une fois par jour au plus. */
	private final java.util.Map<String, Long> complaints = new java.util.HashMap<>();
	/** Quand il a dit quoi pour la dernière fois, par réplique. */
	private final java.util.Map<String, Long> said = new java.util.HashMap<>();
	/** Ses besoins pas encore satisfaits, par motif. */
	private final java.util.Map<String, Need> needs = new java.util.LinkedHashMap<>();
	/** Un besoin non redit depuis une minute et quart est oublié (il a été satisfait). */
	private static final int NEED_MEMORY = 1500;
	private int speechTicks;
	private int actionTicks;
	/** Ce qu'il attend d'un livreur (un bâtisseur à court d'un matériau), et combien. */
	private Item wanted;
	private int wantedCount;

	public CitizenEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		getNavigation().setCanOpenDoors(true);
		// L'outil qu'il a en main lui vient de la ville : il le rend à sa mort.
		setDropChance(EquipmentSlot.MAINHAND, 2.0f);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20)
				.add(Attributes.MOVEMENT_SPEED, 0.5)
				.add(Attributes.FOLLOW_RANGE, 48)
				.add(Attributes.ATTACK_DAMAGE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(VARIANT, 0);
		builder.define(SPEECH, Optional.empty());
		builder.define(CHILD, false);
		builder.define(ACTION, 0);
		builder.define(MOOD, Happiness.BASE);
		builder.define(JOB, -1);
		builder.define(TROUBLE, 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(0, new StepAsideGoal(this));
		// Les défenseurs se battent ; les autres fuient et, pendant un raid, se mettent à l'abri.
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 0.9, true) {
			@Override
			public boolean canUse() {
				return isGuard() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return isGuard() && super.canContinueToUse();
			}
		});
		goalSelector.addGoal(1, new PanicGoal(this, 0.75) {
			@Override
			public boolean canUse() {
				return !isDefender() && super.canUse();
			}
		});
		goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Monster.class, 8.0f, 0.6, 0.75) {
			@Override
			public boolean canUse() {
				return !isDefender() && super.canUse();
			}
		});
		goalSelector.addGoal(2, new ShelterGoal(this));
		goalSelector.addGoal(2, new SleepInBedGoal(this));
		goalSelector.addGoal(3, new EatGoal(this));
		goalSelector.addGoal(3, new FestivalGoal(this));
		goalSelector.addGoal(4, new FarmerGoal(this));
		goalSelector.addGoal(4, new CookGoal(this));
		goalSelector.addGoal(4, new LumberjackGoal(this));
		goalSelector.addGoal(4, new BreederGoal(this));
		goalSelector.addGoal(4, new FishermanGoal(this));
		goalSelector.addGoal(4, new BuilderGoal(this));
		goalSelector.addGoal(4, new CourierGoal(this));
		goalSelector.addGoal(4, new GuardGoal(this));
		goalSelector.addGoal(4, new ArcherGoal(this));
		goalSelector.addGoal(4, new InventorGoal(this));
		goalSelector.addGoal(4, new DoctorGoal(this));
		goalSelector.addGoal(4, new MerchantGoal(this));
		// Les défenseurs prennent pour cible tout ce qui est hostile sur le territoire, et qui les frappe.
		targetSelector.addGoal(1, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				return isDefender() && super.canUse();
			}
		});
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
				(target, level) -> isDefender() && target instanceof Enemy && !(isGuard() && target instanceof Creeper)
						&& town().filter(t -> t.contains(level.dimension(), target.blockPosition())).isPresent()));
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

	/** Un bâtisseur lui demande de laisser la place : il va là, au plus pendant 10 secondes. */
	public void stepAside(BlockPos target) {
		stepAside = target;
		stepAsideUntil = tickCount + 200;
	}

	public Optional<BlockPos> stepAsideTarget() {
		if (stepAside != null && tickCount > stepAsideUntil) {
			stepAside = null;
		}
		return Optional.ofNullable(stepAside);
	}

	public void clearStepAside() {
		stepAside = null;
	}

	/** Il attend qu'un livreur lui apporte ce matériau. */
	public void request(Item item, int count) {
		wanted = item;
		wantedCount = count;
	}

	public Optional<Item> wanted() {
		return Optional.ofNullable(wanted);
	}

	public int wantedCount() {
		return wantedCount;
	}

	public void clearRequest() {
		wanted = null;
		wantedCount = 0;
	}

	/** Un garde ou un archer : il se bat, veille la nuit, ne fuit pas. */
	public boolean isDefender() {
		return job().filter(j -> j.job().defends()).isPresent();
	}

	public boolean isGuard() {
		return job().filter(j -> j.job() == com.ajustor.minecities.town.Job.GUARD).isPresent();
	}

	/** Son emploi, s'il en a un. */
	public Optional<Resident.Employment> job() {
		return resident.flatMap(Resident::job);
	}

	public NonNullList<ItemStack> inventory() {
		return inventory;
	}

	/** Met un objet dans son inventaire ; ce qui ne rentre pas tombe à ses pieds. */
	public void carry(ItemStack stack) {
		ItemStack rest = Stock.insert(inventory, stack);
		if (!rest.isEmpty() && level() instanceof ServerLevel serverLevel) {
			spawnAtLocation(serverLevel, rest);
		}
	}

	public boolean inventoryFull() {
		return inventory.stream().noneMatch(ItemStack::isEmpty);
	}

	/**
	 * Se dirige vers un bloc. S'il est plein (un coffre, un fumoir), vers la case libre la plus proche
	 * à côté : un chemin qui vise un bloc plein n'aboutit pas.
	 */
	public void walkTo(BlockPos target, double speed) {
		BlockPos spot = target;
		if (!level().getBlockState(target).getCollisionShape(level(), target).isEmpty()) {
			double best = Double.MAX_VALUE;
			for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				BlockPos side = target.relative(d);
				if (level().getBlockState(side).getCollisionShape(level(), side).isEmpty()) {
					double distance = distanceToSqr(side.getX() + 0.5, side.getY(), side.getZ() + 0.5);
					if (distance < best) {
						best = distance;
						spot = side;
					}
				}
			}
		}
		getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1, speed);
	}

	/** À portée de bras de ce bloc (3 blocs, comme un joueur). */
	public boolean reached(BlockPos target) {
		return distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) <= 3.0 * 3.0;
	}

	public boolean isChild() {
		return entityData.get(CHILD);
	}

	@Override
	public boolean isBaby() {
		return isChild();
	}

	/** Enfant (à moitié de la taille d'un adulte) ou adulte. */
	public void setChild(boolean child) {
		entityData.set(CHILD, child);
		var scale = getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(child ? 0.55 : 1.0);
		}
	}

	/** Ce qu'il fait en ce moment, pour l'animation. */
	public Action action() {
		return Action.values()[Math.floorMod(entityData.get(ACTION), Action.values().length)];
	}

	/** Annonce ce qu'il fait ; l'animation dure tant qu'on ne l'arrête pas (ou le temps donné). */
	public void act(Action action, int ticks) {
		entityData.set(ACTION, action.ordinal());
		actionTicks = ticks;
	}

	/** Ce qu'il dit en ce moment, pour la bulle. */
	public Optional<Component> speech() {
		return entityData.get(SPEECH);
	}

	/**
	 * Il le dit tout haut : une bulle au-dessus de sa tête, et une ligne dans le chat des joueurs à
	 * portée de voix. Une même réplique (même clé) n'est pas répétée avant 30 secondes.
	 */
	public void say(String key, Component line) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Long last = said.get(key);
		if (last != null && serverLevel.getGameTime() - last < REPEAT_DELAY) {
			return;
		}
		said.put(key, serverLevel.getGameTime());
		entityData.set(SPEECH, Optional.of(line));
		speechTicks = SPEECH_TICKS;
		if (action() == Action.NONE) {
			act(Action.TALK, 40);
		}
		Component chat = Component.translatable("chat.minecities.say", getName(), line).withStyle(ChatFormatting.GRAY);
		for (ServerPlayer player : serverLevel.players()) {
			if (player.distanceToSqr(this) <= EARSHOT * EARSHOT) {
				player.sendSystemMessage(chat);
			}
		}
	}

	/**
	 * Un souci (un outil, un matériau qui manque) : il le dit à qui est là, et le maire en est
	 * prévenu une fois par jour et par motif.
	 */
	public void complain(String key, Component message, Component line) {
		need(key, line);
		complain(key, message);
	}

	/** Prévient le maire d'un souci (un outil manquant…), une fois par jour et par motif. */
	public void complain(String key, Component message) {
		if (!(level() instanceof ServerLevel serverLevel) || townId == null) {
			return;
		}
		long now = serverLevel.getGameTime();
		Long last = complaints.get(key);
		if (last != null && now - last < 24000) {
			return;
		}
		complaints.put(key, now);
		town().ifPresent(t -> TownService.notify(serverLevel.getServer(), t, message));
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
		String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(food.getItem()).toString();
		// Toujours la même chose nourrit moins.
		setHunger(Hunger.eat(hunger, diet.nutrition(id, properties.nutrition())));
		diet.eaten(id);
		act(Action.EAT, 20);
		satisfied("hungry");
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
		if (speechTicks > 0 && --speechTicks == 0) {
			entityData.set(SPEECH, Optional.empty());
		}
		if (actionTicks > 0 && --actionTicks == 0) {
			entityData.set(ACTION, Action.NONE.ordinal());
		}
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
		drill();
		if (tickCount % 100 == 0) {
			updateMood(serverLevel);
		}
		// Un enfant mange deux fois moins.
		setHunger(Hunger.decay(hunger, isChild() ? 10 : 20));
		if (hunger <= 0) {
			starve(serverLevel);
		} else {
			starvingTicks = 0;
			if (hunger >= Hunger.WELL_FED && tickCount % 100 == 0 && getHealth() < getMaxHealth()) {
				heal(1);
			}
		}
	}

	/** Ce qui ne va pas chez lui, en bits, pour l'icône au-dessus de sa tête. */
	public static final class Trouble {
		public static final int HUNGRY = 1;
		public static final int HOMELESS = 2;
		public static final int UNHAPPY = 4;
		public static final int HURT = 8;

		private Trouble() {
		}
	}

	/** Sa ville est attaquée (un raid) ou a sonné l'alerte (la cloche). */
	public boolean alarmed() {
		return town().filter(t -> Raids.underAttack(t.id()) || t.chronicle().alarmed(level().getGameTime()))
				.isPresent();
	}

	/** Son métier, côté client : son rang, ou -1. */
	public int jobIndex() {
		return entityData.get(JOB);
	}

	/** Son humeur, de 0 à 10. */
	public int mood() {
		return entityData.get(MOOD);
	}

	/** Son humeur et ce qui l'explique, au dernier calcul (côté serveur). */
	public Happiness.Mood moodDetail() {
		return mood;
	}

	public Diet diet() {
		return diet;
	}

	public int trouble() {
		return entityData.get(TROUBLE);
	}

	/** A-t-il un besoin en souffrance dont la clé commence ainsi ? */
	public boolean hasNeed(String prefix) {
		long now = level().getGameTime();
		return needs.entrySet().stream().anyMatch(e -> e.getKey().startsWith(prefix)
				&& now - e.getValue().since() <= NEED_MEMORY);
	}

	/** Recalcule son humeur, la dit à la ville, et s'en va s'il est au plus mal depuis un jour. */
	private void updateMood(ServerLevel level) {
		Optional<Town> town = town();
		if (town.isEmpty() || resident.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		Resident r = resident.get();
		boolean danger = alarmed() || !level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(12, 4, 12),
				m -> m instanceof Enemy && m.isAlive()).isEmpty();
		Happiness.Factors factors = new Happiness.Factors(hunger, diet.meals().size(), diet.variety(),
				diet.hadDish(), Happiness.expectedVariety(town.get().tier().ordinal()), r.hasHome(), r.isChild(),
				r.job().isPresent(), hasNeed("tool"), r.spouse().isPresent(), getHealth() < getMaxHealth() / 2,
				town.get().chronicle().recentDeaths(now), danger, town.get().chronicle().rejoicing(now));
		mood = Happiness.of(factors);
		entityData.set(MOOD, mood.score());
		town.get().setMood(getUUID(), mood.score());
		int trouble = 0;
		if (hunger < Hunger.HUNGRY) {
			trouble |= Trouble.HUNGRY;
		}
		if (!r.hasHome()) {
			trouble |= Trouble.HOMELESS;
		}
		if (mood.score() <= Happiness.UNHAPPY) {
			trouble |= Trouble.UNHAPPY;
		}
		if (getHealth() < getMaxHealth() / 2) {
			trouble |= Trouble.HURT;
		}
		entityData.set(TROUBLE, trouble);
		if (mood.score() <= Happiness.MISERABLE) {
			miserableTicks += 100;
			if (miserableTicks == 100) {
				say("miserable", Component.translatable("speech.minecities.miserable"));
			}
			if (miserableTicks >= 24000 && com.ajustor.minecities.McConfig.get().miserableCitizensLeave) {
				leaveTown(level, Component.translatable("message.minecities.citizen.left_unhappy", getName()));
			}
		} else {
			miserableTicks = 0;
		}
	}

	/** L'entraînement des défenseurs : plus de vie, une peau plus dure, des coups plus lourds. */
	private static final Identifier DRILL = Identifier.fromNamespaceAndPath("minecities", "drill");

	private void drill() {
		boolean defender = isDefender();
		AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
		boolean recruit = defender && health != null && !health.hasModifier(DRILL);
		drill(Attributes.MAX_HEALTH, defender, 20);
		if (recruit) {
			// Une recrue prend son poste en pleine forme.
			setHealth(getMaxHealth());
		}
		drill(Attributes.ARMOR, defender, 6);
		drill(Attributes.ATTACK_DAMAGE, defender, 2);
		if (getHealth() > getMaxHealth()) {
			setHealth(getMaxHealth());
		}
	}

	private void drill(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			boolean on, double amount) {
		AttributeInstance instance = getAttribute(attribute);
		if (instance == null || instance.hasModifier(DRILL) == on) {
			return;
		}
		if (on) {
			instance.addPermanentModifier(new AttributeModifier(DRILL, amount, AttributeModifier.Operation.ADD_VALUE));
		} else {
			instance.removeModifier(DRILL);
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
		entityData.set(JOB, record.get().job().map(j -> j.job().ordinal()).orElse(-1));
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
		if (level() instanceof ServerLevel serverLevel) {
			inventory.forEach(stack -> spawnAtLocation(serverLevel, stack));
			inventory.clear();
		}
		if (level() instanceof ServerLevel serverLevel && townId != null) {
			// Une mort pèse sur toute la ville pendant un jour.
			town().ifPresent(t -> t.chronicle().death(serverLevel.getGameTime()));
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
		Component job = job().<Component>map(j -> Component.translatable(j.job().translationKey()))
				.orElse(Component.translatable("citizen.minecities.jobless"));
		player.sendSystemMessage(Component.translatable("citizen.minecities.status", getName(), town, job,
				Component.translatable(Hunger.state(hunger).translationKey()), home).withStyle(ChatFormatting.AQUA));
		// Son humeur, et ce qui la fait.
		MutableComponent why = Component.empty();
		for (Happiness.Factor factor : mood.factors()) {
			if (!why.getSiblings().isEmpty()) {
				why.append(", ");
			}
			why.append(Component.translatable(factor.translationKey()).withStyle(factor.delta() > 0
					? ChatFormatting.GREEN : ChatFormatting.RED));
		}
		player.sendSystemMessage(Component.translatable("citizen.minecities.mood", mood(), why)
				.withStyle(ChatFormatting.AQUA));
		// Ce qui lui manque encore, il le redit à qui vient le voir.
		long now = level().getGameTime();
		needs.values().removeIf(n -> now - n.since() > NEED_MEMORY);
		Component last = null;
		for (Need need : needs.values()) {
			player.sendSystemMessage(Component.translatable("chat.minecities.say", getName(), need.line())
					.withStyle(ChatFormatting.YELLOW));
			last = need.line();
		}
		if (last != null) {
			entityData.set(SPEECH, Optional.of(last));
			speechTicks = SPEECH_TICKS;
		}
	}

	/** Un besoin qu'il a exprimé, et quand il l'a redit pour la dernière fois. */
	private record Need(Component line, long since) {
	}

	/**
	 * Un besoin pas encore satisfait (un outil, un matériau, à manger) : il le dit, et il s'en
	 * souvient pour le redire à qui viendra le voir. Un besoin qu'il ne redit plus depuis plus d'une
	 * minute est tenu pour satisfait.
	 */
	public void need(String key, Component line) {
		if (level() instanceof ServerLevel serverLevel) {
			needs.put(key, new Need(line, serverLevel.getGameTime()));
		}
		say(key, line);
	}

	/** Le dernier besoin qu'il a exprimé et qui n'est pas encore satisfait. */
	public Optional<Component> lastNeed() {
		long now = level().getGameTime();
		return needs.values().stream().filter(n -> now - n.since() <= NEED_MEMORY)
				.max(java.util.Comparator.comparingLong(Need::since)).map(Need::line);
	}

	/** Ce besoin-là est satisfait. */
	public void satisfied(String key) {
		needs.remove(key);
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
		output.putBoolean("child", isChild());
		output.store("diet", com.mojang.serialization.Codec.STRING.listOf(), diet.meals());
		output.putInt("miserable", miserableTicks);
		output.putInt("mood", mood());
		ContainerHelper.saveAllItems(output.child("inventory"), inventory);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		townId = input.read("town", UUIDUtil.CODEC).orElse(null);
		hunger = input.getFloatOr("hunger", Hunger.SATED);
		starvingTicks = input.getIntOr("starving", 0);
		setVariant(input.getIntOr("variant", 0));
		setChild(input.getBooleanOr("child", false));
		diet = new Diet(input.read("diet", com.mojang.serialization.Codec.STRING.listOf()).orElse(java.util.List.of()));
		miserableTicks = input.getIntOr("miserable", 0);
		entityData.set(MOOD, input.getIntOr("mood", Happiness.BASE));
		inventory.clear();
		ContainerHelper.loadAllItems(input.childOrEmpty("inventory"), inventory);
	}
}
