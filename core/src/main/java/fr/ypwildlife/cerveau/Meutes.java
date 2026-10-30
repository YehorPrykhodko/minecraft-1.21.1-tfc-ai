package fr.ypwildlife.cerveau;
import java.util.*;
import static fr.ypwildlife.cerveau.Parametre.*;
/** Instantané collectif calculé avant tout arbitrage individuel. */
public final class Meutes {
    public record Membre(UUID id,ProfilEspece profil,Position position,boolean enfant,double sante,double peur,
                         int echecs,List<Observation> observations,UUID agresseur){}
    private record Lien(UUID a,UUID b){}
    private final Map<Lien,Long> liens=new HashMap<>();
    private final Map<String,Double> tirages=new HashMap<>();
    private final Map<String,Long> vus=new HashMap<>();
    public Map<UUID,Perception.Groupe> calculer(long tick,List<Membre> membres){
        var tries=membres.stream().sorted(Comparator.comparing(Membre::id)).toList();
        var adj=new HashMap<UUID,Set<UUID>>();for(var m:tries)adj.put(m.id(),new HashSet<>());
        var presents=adj.keySet();liens.keySet().removeIf(l->!presents.contains(l.a)||!presents.contains(l.b));
        for(int i=0;i<tries.size();i++)for(int j=i+1;j<tries.size();j++){
            var a=tries.get(i);var b=tries.get(j);if(!a.profil().id().equals(b.profil().id())||!a.profil().social())continue;
            var l=new Lien(a.id(),b.id());double d=a.position().distance(b.position());
            if(d<=a.profil().n(packJoinRadius)||(liens.containsKey(l)&&d<=a.profil().n(packBreakRadius)))liens.put(l,tick);
            if(liens.containsKey(l)&&tick-liens.get(l)<=a.profil().i(packBreakDelayTicks)){adj.get(a.id()).add(b.id());adj.get(b.id()).add(a.id());}else liens.remove(l);
        }
        var resultat=new HashMap<UUID,Perception.Groupe>();var visite=new HashSet<UUID>();
        for(var premier:tries){if(!visite.add(premier.id()))continue;
            var ids=new TreeSet<UUID>();var queue=new ArrayDeque<UUID>();queue.add(premier.id());
            while(!queue.isEmpty()){var id=queue.remove();ids.add(id);for(var v:adj.get(id))if(visite.add(v))queue.add(v);}
            var groupe=tries.stream().filter(m->ids.contains(m.id())).toList();
            var joueurs=new TreeMap<UUID,Observation>();for(var m:groupe)for(var o:m.observations())if(o.joueur()&&o.recente(tick,m.profil().i(visionMemoryTicks)))joueurs.merge(o.id(),o,(a,b)->a.confirmeAu()>=b.confirmeAu()?a:b);
            Observation cible=joueurs.values().stream().min(Comparator.comparingDouble(Observation::sante).thenComparing(Observation::id)).orElse(null);
            var aptes=groupe.stream().filter(m->veto(m,premier,cible).equals("eligible")).toList();
            UUID defense=groupe.stream().map(Membre::agresseur).filter(Objects::nonNull).sorted().findFirst().orElse(null);
            double chance=cible==null?0:Math.clamp((aptes.size()-1)*premier.profil().n(packConfidenceWeight)*(1-cible.sante()),0,.95);
            String cle=ids.first()+":"+(cible==null?"none":cible.id());vus.put(cle,tick);
            double tirage=tirages.computeIfAbsent(cle,k->new SplittableRandom(k.hashCode()*31L+tick).nextDouble());
            boolean chasse=premier.profil().famille()==ProfilEspece.Famille.HYENE&&joueurs.size()==1&&aptes.size()>=2
                &&cible.sante()<premier.profil().n(playerHpHuntThreshold)&&tirage<chance;
            for(var m:groupe){String veto=veto(m,premier,cible);
                resultat.put(m.id(),new Perception.Groupe(ids.first(),aptes.size(),joueurs.size(),cible==null?null:cible.id(),chasse&&veto.equals("eligible"),
                    veto.equals("eligible")?defense:null,veto,chance));}
        }
        vus.entrySet().removeIf(e->{if(tick-e.getValue()>1200){tirages.remove(e.getKey());return true;}return false;});
        return Map.copyOf(resultat);
    }
    private String veto(Membre m,Membre premier,Observation cible){
        if(m.enfant())return "cub";if(m.sante()<.4)return "injured";if(m.peur()>=m.profil().n(fearOverrideThreshold))return "panic";
        if(m.echecs()>=m.profil().i(retreatMaxAttempts))return "path_blocked";
        if(m.position().distance(premier.position())>m.profil().n(packAssistRadius))return "assist_distance";
        if(cible!=null&&m.observations().stream().noneMatch(o->o.id().equals(cible.id())))return "target_unconfirmed";
        return "eligible";
    }
}
