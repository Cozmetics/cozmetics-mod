package io.github.itsarcn.cozmetics.client.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.itsarcn.cozmetics.client.gui.CosmeticsScreen;

/**
 * Optional Mod Menu integration.
 * Provides entry point to open CozmeticsScreen from Mod Menu's mod list.
 */
public class CozmeticsModMenuImpl implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return CosmeticsScreen::new;
	}
}

