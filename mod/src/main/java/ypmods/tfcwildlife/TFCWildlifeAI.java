package ypmods.tfcwildlife;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import ypmods.tfcwildlife.config.WildlifeConfig;
import ypmods.tfcwildlife.minecraft.WildlifeRuntime;
import ypmods.tfcwildlife.tacz.GunshotIntegration;

@Mod(TFCWildlifeAI.MOD_ID)
public final class TFCWildlifeAI {
    public static final String MOD_ID = "tfc_wildlife_ai";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TFCWildlifeAI(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, WildlifeConfig.SPEC);
        var runtime = new WildlifeRuntime();
        NeoForge.EVENT_BUS.addListener(runtime::onLevelTick);
        NeoForge.EVENT_BUS.addListener(runtime::onDamage);
        NeoForge.EVENT_BUS.addListener(runtime::onDeath);
        NeoForge.EVENT_BUS.addListener(runtime::onSound);
        NeoForge.EVENT_BUS.addListener(runtime::onExplosion);
        NeoForge.EVENT_BUS.addListener(runtime::onLeave);
        NeoForge.EVENT_BUS.addListener(runtime::onJoin);
        NeoForge.EVENT_BUS.addListener(runtime::onServerStopped);
        NeoForge.EVENT_BUS.addListener(runtime::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(runtime::onCommands);
        new GunshotIntegration(runtime).register();
    }
}
