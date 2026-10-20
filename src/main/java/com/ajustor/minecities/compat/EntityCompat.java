package com.ajustor.minecities.compat;

//? if >=26.3 {
/*import net.minecraft.util.Prediction;
*///?}
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Les gestes des entités dont la forme change d'une version de Minecraft à l'autre. C'est ici, et
 * seulement ici (avec les autres classes de ce paquet), que vivent les conditions de version (voir
 * ARCHITECTURE.md) : le reste du mod appelle ces fonctions sans savoir quelle version tourne.
 */
public final class EntityCompat {
	private EntityCompat() {
	}

	/** Le joueur lâche un objet devant lui (côté serveur). */
	public static void drop(Player player, ItemStack stack) {
		//? if >=26.3 {
		/*player.drop(stack, false, Prediction.SERVER_ONLY);
		*///?} else {
		player.drop(stack, false);
		//?}
	}

	/** Remet un objet dans l'inventaire du joueur ; ce qui n'y tient pas tombe à ses pieds. */
	public static void placeBack(Player player, ItemStack stack) {
		//? if >=26.3 {
		/*player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		*///?} else {
		player.getInventory().placeItemBackInInventory(stack);
		//?}
	}

	/** Le bras frappe : l'animation de l'objet tenu, vue par les joueurs alentour. */
	public static void swing(LivingEntity entity, InteractionHand hand) {
		//? if >=26.3 {
		/*entity.swing(hand, entity.getItemInHand(hand).getAttackAnimation(), false);
		*///?} else {
		entity.swing(hand);
		//?}
	}

	/** L'entité ne peut plus être blessée (sauf par le vide ou une commande). */
	public static void invulnerable(net.minecraft.world.entity.Entity entity) {
		//? if >=26.3 {
		/*entity.setPermanentlyInvulnerable(true);
		*///?} else {
		entity.setInvulnerable(true);
		//?}
	}
}
