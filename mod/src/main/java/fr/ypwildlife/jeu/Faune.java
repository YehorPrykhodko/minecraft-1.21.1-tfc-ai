package fr.ypwildlife.jeu;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Faune.ID)
public final class Faune {
  public static final String ID = "tfc_wildlife_ai";
  public static final MondeFaune MONDE = new MondeFaune();

  public Faune(IEventBus bus, ModContainer conteneur) {
    conteneur.registerConfig(
        ModConfig.Type.SERVER, Configuration.SPEC, "tfc_wildlife_ai-v4-server.toml");
    bus.addListener(
        (ModConfigEvent.Loading e) -> {
          if (e.getConfig().getSpec() == Configuration.SPEC) Configuration.charger();
        });
    bus.addListener(
        (ModConfigEvent.Reloading e) -> {
          if (e.getConfig().getSpec() == Configuration.SPEC) Configuration.charger();
        });
    NeoForge.EVENT_BUS.addListener(MONDE::entree);
    NeoForge.EVENT_BUS.addListener(MONDE::sortie);
    NeoForge.EVENT_BUS.addListener(MONDE::tick);
    NeoForge.EVENT_BUS.addListener(MONDE::physique);
    NeoForge.EVENT_BUS.addListener(MONDE::degat);
    NeoForge.EVENT_BUS.addListener(MONDE::son);
    NeoForge.EVENT_BUS.addListener(MONDE::explosion);
    NeoForge.EVENT_BUS.addListener(MONDE::mort);
    NeoForge.EVENT_BUS.addListener(MONDE::decharger);
    NeoForge.EVENT_BUS.addListener(MONDE::arreter);
    NeoForge.EVENT_BUS.addListener(MONDE::demarrer);
    NeoForge.EVENT_BUS.addListener(
        net.neoforged.bus.api.EventPriority.LOWEST,
        false,
        com.tacz.guns.api.event.common.GunFireEvent.class,
        e -> {
          if (e.getLogicalSide() == net.neoforged.fml.LogicalSide.SERVER) MONDE.tir(e.getShooter());
        });
  }
}
