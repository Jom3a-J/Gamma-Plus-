package com.gammaplus.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenuApiImpl — Provides the config screen factory for Mod Menu integration.
 * When Mod Menu is installed, clicking the config button for GammaMod opens our settings screen.
 */
public class ModMenuApiImpl implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreens::create;
    }
}
