package ypmods.tfcwildlife.core;

import java.util.Optional;
import java.util.Set;

public enum Species {
    BEAR, HYENA, WOLF, FELINE, PREY, RAMMER;

    private static final Set<String> BEARS = Set.of("grizzly_bear", "polar_bear", "black_bear");
    private static final Set<String> CATS = Set.of("cougar", "panther", "lion", "tiger", "sabertooth");
    private static final Set<String> PREYS = Set.of("deer", "caribou", "gazelle", "bongo",
        "rat", "jerboa", "lemming", "mongoose", "grouse", "pheasant", "turkey", "peafowl");
    private static final Set<String> RAMMERS = Set.of("boar", "moose", "wildebeest", "bison");

    public static Optional<Species> fromId(String id) {
        if (!id.startsWith("tfc:")) return Optional.empty();
        String path = id.substring(4);
        if (BEARS.contains(path)) return Optional.of(BEAR);
        if (path.equals("hyena")) return Optional.of(HYENA);
        if (path.equals("wolf") || path.equals("direwolf")) return Optional.of(WOLF);
        if (CATS.contains(path)) return Optional.of(FELINE);
        if (RAMMERS.contains(path)) return Optional.of(RAMMER);
        return PREYS.contains(path) ? Optional.of(PREY) : Optional.empty();
    }
    public boolean predator() { return this == BEAR || this == HYENA || this == WOLF || this == FELINE; }
    public boolean social() { return this == HYENA || this == WOLF; }
}
