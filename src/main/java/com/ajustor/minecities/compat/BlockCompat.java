package com.ajustor.minecities.compat;

//? if >=26.3 {
/*import net.minecraft.world.level.block.Blocks;
*///?}
import net.minecraft.world.level.block.state.BlockState;

/** Les questions posées à un bloc dont la forme change d'une version de Minecraft à l'autre. */
public final class BlockCompat {
	private BlockCompat() {
	}

	/** Le bloc arrête qui marche dessus ou dedans : un sol, un mur (ni toile d'araignée ni pousse de bambou). */
	public static boolean blocksMotion(BlockState state) {
		//? if >=26.3 {
		/*return !state.is(Blocks.COBWEB) && !state.is(Blocks.BAMBOO_SAPLING) && state.isSolid();
		*///?} else {
		return state.blocksMotion();
		//?}
	}
}
