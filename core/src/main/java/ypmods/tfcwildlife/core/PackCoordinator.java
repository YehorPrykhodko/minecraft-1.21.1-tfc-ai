package ypmods.tfcwildlife.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PackCoordinator {
    public record Member(UUID id, String speciesId, Point position, boolean adult, boolean alive,
                         boolean safe, Optional<Perception.Threat> observedPlayer, Optional<Perception.Threat> attacker) {
        public Member(UUID id, String speciesId, Point position, boolean adult, boolean alive,
                      boolean safe, Optional<Perception.Threat> observedPlayer) {
            this(id, speciesId, position, adult, alive, safe, observedPlayer, Optional.empty());
        }
    }
    private record Edge(UUID a, UUID b) {}
    private record Hunt(Perception.Threat target, long until, boolean defending) {}
    private final Map<Edge, Long> edges = new HashMap<>();
    private final Map<UUID, Hunt> hunts = new HashMap<>();
    private long lastTick = Long.MIN_VALUE;

    public void invalidateTarget(UUID target) { hunts.entrySet().removeIf(e -> e.getValue().target.id().equals(target)); }

    public Map<UUID, Perception.Social> coordinate(long tick, List<Member> observations, Settings cfg) {
        if (tick <= lastTick) throw new IllegalStateException("Un seul instantané de meute par tick");
        lastTick = tick;
        var adults = observations.stream().filter(m -> m.alive && m.adult)
            .sorted(Comparator.comparing(Member::id)).toList();
        Set<UUID> alive = new HashSet<>();
        adults.forEach(m -> alive.add(m.id));
        edges.entrySet().removeIf(e -> !alive.contains(e.getKey().a) || !alive.contains(e.getKey().b)
            || tick - e.getValue() > cfg.packGrace());
        Map<UUID, Set<UUID>> graph = new HashMap<>();
        adults.forEach(m -> graph.put(m.id, new HashSet<>()));
        for (int i = 0; i < adults.size(); i++) {
            Member a = adults.get(i);
            for (int j = i + 1; j < adults.size(); j++) {
                Member b = adults.get(j);
                if (!a.speciesId.equals(b.speciesId)) continue;
                Edge edge = new Edge(a.id, b.id);
                double radius = edges.containsKey(edge) ? cfg.packExitRadius() : cfg.packEnterRadius();
                if (a.position.distanceSquared(b.position) <= radius * radius) edges.put(edge, tick);
                if (edges.containsKey(edge)) {
                    graph.get(a.id).add(b.id);
                    graph.get(b.id).add(a.id);
                }
            }
        }
        Map<UUID, Member> index = new HashMap<>();
        adults.forEach(m -> index.put(m.id, m));
        Map<UUID, Perception.Social> result = new HashMap<>();
        Set<UUID> visited = new HashSet<>(), activePacks = new HashSet<>();
        for (Member first : adults) {
            if (!visited.add(first.id)) continue;
            var queue = new ArrayList<UUID>();
            queue.add(first.id);
            for (int i = 0; i < queue.size(); i++) {
                for (UUID peer : graph.get(queue.get(i))) if (visited.add(peer)) queue.add(peer);
            }
            if (queue.size() < 2) { result.put(first.id, Perception.Social.solo()); continue; }
            UUID pack = queue.stream().min(UUID::compareTo).orElseThrow();
            activePacks.add(pack);
            var members = queue.stream().map(index::get).toList();
            // Deux adultes aptes doivent réellement pouvoir rejoindre la même cible.
            var visible = members.stream().flatMap(m -> m.observedPlayer.stream())
                .filter(t -> t.player() && t.attackable() && t.visible() && t.observedTick() == tick)
                .sorted(Comparator.comparing(Perception.Threat::id)).toList();
            Hunt hunt = hunts.get(pack);
            if (hunt != null) {
                Optional<Perception.Threat> updated = visible.stream().filter(t -> t.id().equals(huntTarget(pack))).findFirst();
                if (updated.isPresent()) hunt = new Hunt(updated.get(), hunt.until, hunt.defending);
                if (hunt.until <= tick || tick - hunt.target.observedTick() > cfg.memoryTicks() || !hunt.target.attackable()) hunt = null;
            }
            var attacker = members.stream().flatMap(m -> m.attacker.stream())
                .filter(t -> t.attackable() && tick - t.observedTick() <= cfg.memoryTicks())
                .sorted(Comparator.comparing(Perception.Threat::id)).findFirst();
            if (attacker.isPresent() && (hunt == null || !hunt.defending)) {
                hunt = new Hunt(attacker.get(), tick + cfg.combatHold(), true);
            }
            if (hunt == null) {
                for (Perception.Threat player : visible) {
                    long capable = members.stream().filter(m -> m.safe && m.position.distanceSquared(player.position()) <= 32 * 32).count();
                    if (capable >= 2 && player.hp() < cfg.packPlayerHp()) {
                        hunt = new Hunt(player, tick + cfg.combatHold(), false);
                        break;
                    }
                }
            }
            if (hunt != null) hunts.put(pack, hunt); else hunts.remove(pack);
            for (Member member : members) {
                boolean participates = hunt != null && member.safe
                    && member.position.distanceSquared(hunt.target.position()) <= 40 * 40;
                result.put(member.id, new Perception.Social(pack, members.size(), participates,
                    participates ? Optional.of(hunt.target) : Optional.empty(), hunt == null ? 0 : hunt.until,
                    hunt != null && hunt.defending));
            }
        }
        hunts.keySet().retainAll(activePacks);
        return Map.copyOf(result);
    }

    private UUID huntTarget(UUID pack) { return hunts.get(pack).target.id(); }
}
