package fr.ypwildlife.jeu;
import fr.ypwildlife.cerveau.Ressources;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Mob;
import java.util.*;
public final class Sauvegarde {
    public static final String CLE="tfc_wildlife_ai.v4",ANCIEN="tfc_wildlife_ai.remaster";
    public static boolean supporte(Mob m){var r=m.getPersistentData();return (!r.contains(CLE)||r.getCompound(CLE).getInt("schema")<=4)&&(!r.contains(ANCIEN)||r.getCompound(ANCIEN).getInt("schema")<=2);}
    public static Ressources lire(Mob m){var r=m.getPersistentData();var valeurs=new HashMap<String,Double>();
        boolean ancien=!r.contains(CLE)&&!r.contains(ANCIEN);var source=r.contains(CLE)?r.getCompound(CLE):r.contains(ANCIEN)?r.getCompound(ANCIEN):r;
        for(String k:List.of("satiety","stamina","fear")){String cle=ancien?"tfc_wildlife_ai."+k:k;if(source.contains(cle,Tag.TAG_ANY_NUMERIC))valeurs.put(k,source.getDouble(cle));}
        return Ressources.restaurer(valeurs,ancien);
    }
    public static void ecrire(Mob m,Ressources v){if(!supporte(m))return;var r=m.getPersistentData();var n=r.getCompound(CLE).copy();n.putInt("schema",4);n.putDouble("satiety",v.satiete());n.putDouble("stamina",v.endurance());n.putDouble("fear",v.peur());r.put(CLE,n);}
    public static UUID famille(Mob m){var n=m.getPersistentData().getCompound(CLE);return n.hasUUID("family")?n.getUUID("family"):null;}
    public static UUID parent(Mob m){var n=m.getPersistentData().getCompound(CLE);return n.hasUUID("parent")?n.getUUID("parent"):null;}
    public static void lier(Mob m,UUID famille,UUID parent,String source){if(!supporte(m))return;var n=m.getPersistentData().getCompound(CLE).copy();n.putInt("schema",4);n.putUUID("family",famille);if(parent!=null)n.putUUID("parent",parent);n.putString("familySource",source);m.getPersistentData().put(CLE,n);}
}
