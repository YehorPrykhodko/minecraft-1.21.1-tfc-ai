package fr.ypwildlife.jeu;
import com.google.common.collect.ImmutableList;
import net.dries007.tfc.common.entities.ai.TFCBrain;
import net.dries007.tfc.common.entities.ai.predator.PredatorBehaviors;
import net.dries007.tfc.common.entities.predator.Predator;
import net.minecraft.world.entity.ai.*;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
public final class CerveauTfc {
    private static final java.util.Set<Brain<?>> CONFIGURES=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public static void synchroniser(Predator animal,boolean gerer){
        var cerveau=animal.getBrain();
        if(gerer&&!CONFIGURES.contains(cerveau))configurer(cerveau);
        if(!gerer&&CONFIGURES.remove(cerveau)){
            cerveau.stopAll((net.minecraft.server.level.ServerLevel)animal.level(),animal);cerveau.removeAllBehaviors();
            if(animal instanceof net.dries007.tfc.common.entities.predator.AmphibiousPredator)net.dries007.tfc.common.entities.ai.predator.AmphibiousPredatorAi.makeBrain(cerveau,animal);
            else if(animal instanceof net.dries007.tfc.common.entities.ai.predator.PackPredator)net.dries007.tfc.common.entities.ai.predator.PackPredatorAi.makeBrain(cerveau,animal);
            else net.dries007.tfc.common.entities.ai.predator.PredatorAi.makeBrain(cerveau,animal);
        }
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    public static void configurer(Brain<?> brut){
        Brain<Predator> b=(Brain)brut;
        // Conserver les senseurs, mémoires et horaires TFC; retirer uniquement les listes d'actions concurrentes.
        b.removeAllBehaviors();CONFIGURES.add(b);
        b.addActivity(Activity.CORE,0,ImmutableList.of(new CountDownCooldownTicks(TFCBrain.WAKEUP_TICKS.get()),new CountDownCooldownTicks(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS)));
        b.addActivity(TFCBrain.HUNT.get(),10,ImmutableList.of(PredatorBehaviors.tickScheduleAndWake()));
        b.addActivity(Activity.REST,10,ImmutableList.of(PredatorBehaviors.startSleeping(),PredatorBehaviors.tickScheduleAndWake(),PredatorBehaviors.wakeFromDisturbance()));
        b.addActivity(Activity.FIGHT,10,ImmutableList.of(PredatorBehaviors.tickScheduleAndWake()));
        b.addActivity(Activity.AVOID,10,ImmutableList.of(PredatorBehaviors.tickScheduleAndWake()));
    }
}
