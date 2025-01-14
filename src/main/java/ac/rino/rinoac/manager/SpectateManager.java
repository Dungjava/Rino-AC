package ac.rino.rinoac.manager;

import ac.rino.rinoac.RinoAPI;
import ac.rino.rinoac.manager.init.Initable;
import ac.rino.rinoac.player.RinoPlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpectateManager implements Initable {

    private final Map<UUID, PreviousState> spectatingPlayers = new ConcurrentHashMap<>();
    private final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();
    private final Set<String> allowedWorlds = ConcurrentHashMap.newKeySet();

    private boolean checkWorld = false;

    @Override
    public void start() {
        allowedWorlds.clear();
        allowedWorlds.addAll(RinoAPI.INSTANCE.getConfigManager().getConfig().getStringListElse("spectators.allowed-worlds", new ArrayList<>()));
        checkWorld = !(allowedWorlds.isEmpty() || new ArrayList<>(allowedWorlds).get(0).isEmpty());
    }

    public boolean isSpectating(UUID uuid) {
        return spectatingPlayers.containsKey(uuid);
    }

    public boolean shouldHidePlayer(RinoPlayer receiver, WrapperPlayServerPlayerInfo.PlayerData playerData) {
        return playerData.getUser() != null
                && playerData.getUser().getUUID() != null
                && shouldHidePlayer(receiver, playerData.getUser().getUUID());
    }

    public boolean shouldHidePlayer(RinoPlayer receiver, UUID uuid) {
        return !Objects.equals(uuid, receiver.playerUUID) // don't hide to yourself
                && (spectatingPlayers.containsKey(uuid) || hiddenPlayers.contains(uuid)) //hide if you are a spectator
                && !(receiver.playerUUID != null && (spectatingPlayers.containsKey(receiver.playerUUID) || hiddenPlayers.contains(receiver.playerUUID))) // don't hide to other spectators
                && (!checkWorld || (receiver.bukkitPlayer != null && allowedWorlds.contains(receiver.bukkitPlayer.getWorld().getName()))); // hide if you are in a specific world
    }

    public boolean enable(Player player) {
        if (spectatingPlayers.containsKey(player.getUniqueId())) return false;
        spectatingPlayers.put(player.getUniqueId(), new PreviousState(player.getGameMode(), player.getLocation()));
        return true;
    }

    public void onLogin(Player player) {
        hiddenPlayers.add(player.getUniqueId());
    }

    public void onQuit(Player player) {
        hiddenPlayers.remove(player.getUniqueId());
        handlePlayerStopSpectating(player.getUniqueId());
    }

    //only call this synchronously
    public void disable(Player player, boolean teleportBack) {
        PreviousState previousState = spectatingPlayers.get(player.getUniqueId());
        if (previousState != null) {
            if (teleportBack) player.teleport(previousState.location);
            player.setGameMode(previousState.gameMode);
        }
        handlePlayerStopSpectating(player.getUniqueId());
    }

    public void handlePlayerStopSpectating(UUID uuid) {
        spectatingPlayers.remove(uuid);
    }

    private static class PreviousState {
        private final org.bukkit.GameMode gameMode;
        private final Location location;
        public PreviousState(org.bukkit.GameMode gameMode, Location location) {
            this.gameMode = gameMode;
            this.location = location;
        }
    }

}
