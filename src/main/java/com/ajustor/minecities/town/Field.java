package com.ajustor.minecities.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Un champ tracé par le maire avec le piquet de champ : un rectangle du territoire et la culture qu'on
 * y veut. Les fermiers y labourent la terre nue, y sèment cette culture (prise au grenier au besoin) et
 * la récoltent ; « au choix », ils sèment ce qu'ils ont.
 */
public record Field(UUID id, BlockPos min, BlockPos max, Crop crop) {
	/** Un côté de champ fait au plus tant de blocs. */
	public static final int MAX_SIDE = 32;
	/** Le champ suit le relief : on cherche le sol jusqu'à tant de blocs au-dessus et au-dessous des coins. */
	public static final int SLOPE = 3;

	public static final Codec<Field> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Field::id),
			BlockPos.CODEC.fieldOf("min").forGetter(Field::min),
			BlockPos.CODEC.fieldOf("max").forGetter(Field::max),
			Codec.STRING.optionalFieldOf("crop", Crop.ANY.serializedName())
					.xmap(s -> Crop.fromSerializedName(s).orElse(Crop.ANY), Crop::serializedName)
					.forGetter(Field::crop)
	).apply(i, Field::new));

	/** Le champ entre deux coins, dans n'importe quel ordre. */
	public static Field between(BlockPos a, BlockPos b, Crop crop) {
		return new Field(UUID.randomUUID(), new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
				Math.min(a.getZ(), b.getZ())), new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
				Math.max(a.getZ(), b.getZ())), crop);
	}

	public int width() {
		return max.getX() - min.getX() + 1;
	}

	public int depth() {
		return max.getZ() - min.getZ() + 1;
	}

	/** Ce bloc est-il dans le champ (en plan, relief compris) ? */
	public boolean contains(BlockPos pos) {
		return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getZ() >= min.getZ()
				&& pos.getZ() <= max.getZ() && pos.getY() >= min.getY() - SLOPE && pos.getY() <= max.getY() + SLOPE + 1;
	}

	public boolean overlaps(Field other) {
		return min.getX() <= other.max.getX() && other.min.getX() <= max.getX() && min.getZ() <= other.max.getZ()
				&& other.min.getZ() <= max.getZ();
	}

	public Field withCrop(Crop crop) {
		return new Field(id, min, max, crop);
	}

	/** Les cultures qu'on peut demander à un champ. */
	public enum Crop {
		/** Ce que le fermier a en poche. */
		ANY("any", null, null, Items.WHEAT_SEEDS),
		WHEAT("wheat", Items.WHEAT_SEEDS, Blocks.WHEAT, Items.WHEAT),
		CARROTS("carrots", Items.CARROT, Blocks.CARROTS, Items.CARROT),
		POTATOES("potatoes", Items.POTATO, Blocks.POTATOES, Items.POTATO),
		BEETROOTS("beetroots", Items.BEETROOT_SEEDS, Blocks.BEETROOTS, Items.BEETROOT),
		PUMPKINS("pumpkins", Items.PUMPKIN_SEEDS, Blocks.PUMPKIN_STEM, Items.PUMPKIN),
		MELONS("melons", Items.MELON_SEEDS, Blocks.MELON_STEM, Items.MELON_SLICE);

		private final String name;
		private final Item seed;
		private final Block plant;
		private final Item icon;

		Crop(String name, Item seed, Block plant, Item icon) {
			this.name = name;
			this.seed = seed;
			this.plant = plant;
			this.icon = icon;
		}

		public String serializedName() {
			return name;
		}

		/** Ce qui se sème (vide : au choix). */
		public Optional<Item> seed() {
			return Optional.ofNullable(seed);
		}

		/** Le plant que donne la semence (vide : au choix). */
		public Optional<Block> plant() {
			return Optional.ofNullable(plant);
		}

		/** L'objet qui la représente dans l'interface. */
		public Item icon() {
			return icon;
		}

		/** Une tige (citrouille, pastèque) : son fruit pousse à côté, il faut lui laisser la place. */
		public boolean stem() {
			return this == PUMPKINS || this == MELONS;
		}

		public static Optional<Crop> fromSerializedName(String name) {
			return Arrays.stream(values()).filter(c -> c.name.equals(name)).findFirst();
		}
	}
}
