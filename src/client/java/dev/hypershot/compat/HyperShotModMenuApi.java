package dev.hypershot.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.hypershot.ui.SettingsScreen;

/** Optional Mod Menu integration. Fabric only loads this entrypoint when Mod Menu requests it. */
public final class HyperShotModMenuApi implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SettingsScreen::new;
    }
}
