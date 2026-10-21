package com.ajustor.minecities.client.mixin;

import com.ajustor.minecities.client.config.ModLanguage;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/**
 * La langue du mod, indépendante de celle du jeu : une fois les traductions du jeu réunies, celles du
 * mod dans la langue choisie passent par-dessus (voir {@link ModLanguage}).
 */
@Mixin(ClientLanguage.class)
abstract class ClientLanguageMixin {
	@Inject(method = "loadFrom", at = @At(value = "INVOKE", target = "Ljava/util/Map;copyOf(Ljava/util/Map;)Ljava/util/Map;"))
	private static void minecities$modLanguage(ResourceManager resources, List<String> languages, boolean rightToLeft,
			CallbackInfoReturnable<ClientLanguage> cir, @Local Map<String, String> storage) {
		ModLanguage.overlay(resources, storage);
	}
}
