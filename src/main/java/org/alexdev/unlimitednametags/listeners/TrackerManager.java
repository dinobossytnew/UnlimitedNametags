package org.alexdev.unlimitednametags.listeners;

import com.github.retrooper.packetevents.PacketEvents;
import com.google.common.collect.*;
import lombok.Getter;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.data.ConcurrentSetMultimap;
import org.alexdev.unlimitednametags.packet.PacketNameTag;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TrackerManager {

    private final UnlimitedNameTags plugin;
    @Getter
    private final ConcurrentSetMultimap<UUID, UUID> trackedPlayers;

    public TrackerManager(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.trackedPlayers = new ConcurrentSetMultimap<>();
        loadTracker();
    }

    @SuppressWarnings({"deprecation"})
    private void loadTracker() {
        final boolean isPaper = plugin.isPaper();
        Bukkit.getOnlinePlayers().forEach(player -> {
            final Set<Player> currentTracked = (isPaper ? player.getTrackedPlayers() : player.getTrackedBy()).stream()
                            .filter(p -> Bukkit.getPlayer(p.getUniqueId()) != null)
                            .collect(ImmutableSet.toImmutableSet());
            trackedPlayers.putAll(player.getUniqueId(), currentTracked
                    .stream()
                    .map(Player::getUniqueId)
                    .collect(ImmutableSet.toImmutableSet()));
        });
    }

    public void onDisable() {
        trackedPlayers.clear();
    }

    @NotNull
    public Set<UUID> getTrackedPlayers(@NotNull UUID player) {
        return trackedPlayers.get(player);
    }

    public void handleAdd(@NotNull Player player, @NotNull Player target) {
        if (!target.isConnected()) {
            return;
        }

        // Check if it's a real player
        if (Bukkit.getPlayer(target.getUniqueId()) == null || Bukkit.getPlayer(player.getUniqueId()) == null) {
            return;
        }

        if (PacketEvents.getAPI().getPlayerManager().getUser(target) == null || PacketEvents.getAPI().getPlayerManager().getUser(player) == null) {
            return;
        }

        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            trackedPlayers.put(player.getUniqueId(), target.getUniqueId());

            final boolean isVanished = plugin.getVanishManager().isVanished(target);
            if (isVanished && !plugin.getVanishManager().canSee(player, target)) {
                return;
            }

            if (target.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                return;
            }

            final Optional<PacketNameTag> display = plugin.getNametagManager().getPacketDisplayText(target);

            if (display.isEmpty()) {
//                plugin.getLogger().warning("Display is empty for " + target.getName());
                return;
            }

            plugin.getNametagManager().updateDisplay(player, target);
        }, 3);

    }

    public void handleRemove(@NotNull Player player, @NotNull Player target) {
        plugin.getTaskScheduler().runTaskAsynchronously(() -> {
            trackedPlayers.remove(player.getUniqueId(), target.getUniqueId());

            plugin.getNametagManager().removeDisplay(player, target);
        });
    }

    public void handleQuit(@NotNull Player player) {
        plugin.getTaskScheduler().runTaskAsynchronously(() -> {
            trackedPlayers.removeAll(player.getUniqueId());
            trackedPlayers.values().remove(player.getUniqueId());
        });
    }

    public void forceUntrack(@NotNull Player player) {
        plugin.getTaskScheduler().runTaskAsynchronously(() -> {
            trackedPlayers.get(player.getUniqueId())
                    .stream()
                    .map(Bukkit::getPlayer)
                    .filter(Objects::nonNull)
                    .forEach(p -> handleRemove(p, player));
        });
    }

}
