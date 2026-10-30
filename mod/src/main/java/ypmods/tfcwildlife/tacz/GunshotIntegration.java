package ypmods.tfcwildlife.tacz;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import ypmods.tfcwildlife.TFCWildlifeAI;
import ypmods.tfcwildlife.minecraft.WildlifeRuntime;

public final class GunshotIntegration {
    private final WildlifeRuntime runtime;
    public GunshotIntegration(WildlifeRuntime runtime) { this.runtime = runtime; }

    public void register() {
        for (String name : new String[]{"GunFirePostEvent", "GunFireEvent"}) {
            try {
                Class<?> type = Class.forName("com.tacz.guns.api.event.common." + name);
                if (!Event.class.isAssignableFrom(type)) throw new IllegalStateException("Événement TaCZ incompatible : " + name);
                Method shooter = type.getMethod("getShooter");
                addListener(type.asSubclass(Event.class), event -> {
                    try {
                        Object entity = shooter.invoke(event);
                        if (entity instanceof LivingEntity living && !living.level().isClientSide) runtime.gunshot(living);
                    } catch (IllegalAccessException | InvocationTargetException error) {
                        throw new IllegalStateException("Impossible de lire le tireur TaCZ", error);
                    }
                });
                if (name.equals("GunFireEvent")) TFCWildlifeAI.LOGGER.warn(
                    "TaCZ : événement préalable utilisé ; une tentative non annulée peut précéder l'échec d'un tir. Vérifier le hotfix-r3.");
                TFCWildlifeAI.LOGGER.info("Intégration sonore TaCZ : {}", name);
                return;
            } catch (ClassNotFoundException ignored) {
                // La variante suivante assure la compatibilité avec l'ancienne API.
            } catch (NoSuchMethodException error) {
                throw new IllegalStateException("API TaCZ sans getShooter()", error);
            }
        }
        throw new IllegalStateException("Aucun événement TaCZ compatible ; ne pas désactiver silencieusement les tirs.");
    }
    private static <T extends Event> void addListener(Class<T> type, Consumer<Event> consumer) {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, type, consumer::accept);
    }
}
