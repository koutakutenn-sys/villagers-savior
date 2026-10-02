package dev.villagerssavior;

import net.minecraft.world.entity.player.Player;
import java.util.ArrayDeque;
import java.util.Deque;

/** Scoped to the actual block/shears call; proximity never attributes construction to a player. */
public final class CreatorContext {
    private static final ThreadLocal<Deque<Player>> PLAYERS = ThreadLocal.withInitial(ArrayDeque::new);
    private CreatorContext() {}
    public static void enter(Player player) { if (player != null) PLAYERS.get().push(player); }
    public static void exit(Player player) { if (player != null) { var stack = PLAYERS.get(); stack.pop(); if (stack.isEmpty()) PLAYERS.remove(); } }
    public static Player current() { return PLAYERS.get().peek(); }
}
