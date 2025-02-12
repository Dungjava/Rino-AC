package ac.rino.rinoac.checks;

import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.api.events.FlagEvent;
import ac.rino.rinoac.RinoAPI;
import ac.rino.rinoac.player.RinoPlayer;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import github.scarsz.configuralize.DynamicConfig;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;

// Class from https://github.com/Tecnio/AntiCheatBase/blob/master/src/main/java/me/tecnio/anticheat/check/Check.java
@Getter
public class Check implements AbstractCheck {
    protected final RinoPlayer player;

    public double violations;
    private double decay;
    private double setbackVL;

    private String checkName;
    private String configName;
    private String alternativeName;
    private String description;

    private boolean experimental;
    @Setter
    private boolean isEnabled;
    private boolean exempted;

    public Check(final RinoPlayer player) {
        this.player = player;

        final Class<?> checkClass = this.getClass();

        if (checkClass.isAnnotationPresent(CheckData.class)) {
            final CheckData checkData = checkClass.getAnnotation(CheckData.class);
            this.checkName = checkData.name();
            this.configName = checkData.configName();
            // Fall back to check name
            if (this.configName.equals("DEFAULT")) this.configName = this.checkName;
            this.decay = checkData.decay();
            this.setbackVL = checkData.setback();
            this.alternativeName = checkData.alternativeName();
            this.experimental = checkData.experimental();
            this.description = checkData.description();
        }

        reload();
    }

    @Override
    public boolean isExperimental() {
        return experimental;
    }

    public boolean shouldModifyPackets() {
        return isEnabled && !player.disableGrim && !player.noModifyPacketPermission && !exempted;
    }

    public final boolean flagAndAlert(String verbose) {
        if (flag()) {
            alert(verbose);
            return true;
        }
        return false;
    }

    public final boolean flagAndAlert() {
        return flagAndAlert("");
    }

    public final boolean flag() {
        if (player.disableGrim || (experimental && !RinoAPI.INSTANCE.getConfigManager().isExperimentalChecks()) || exempted)
            return false; // Avoid calling event if disabled

        FlagEvent event = new FlagEvent(player, this);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;


        player.punishmentManager.handleViolation(this);

        violations++;
        return true;
    }

    public final boolean flagWithSetback() {
        if (flag()) {
            setbackIfAboveSetbackVL();
            return true;
        }
        return false;
    }

    public final void reward() {
        violations = Math.max(0, violations - decay);
    }

    public void reload() {
        decay = getConfig().getDoubleElse(configName + ".decay", decay);
        setbackVL = getConfig().getDoubleElse(configName + ".setbackvl", setbackVL);

        if (setbackVL == -1) setbackVL = Double.MAX_VALUE;

        exempted = player.bukkitPlayer != null && player.bukkitPlayer.hasPermission("grim.exempt." + checkName.toLowerCase());
    }

    public boolean alert(String verbose) {
        return player.punishmentManager.handleAlert(player, verbose, this);
    }

    public DynamicConfig getConfig() {
        return RinoAPI.INSTANCE.getConfigManager().getConfig();
    }

    public boolean setbackIfAboveSetbackVL() {
        if (getViolations() > setbackVL) {
            return player.getSetbackTeleportUtil().executeViolationSetback();
        }
        return false;
    }

    public boolean isAboveSetbackVl() {
        return getViolations() > setbackVL;
    }

    public String formatOffset(double offset) {
        return offset > 0.001 ? String.format("%.5f", offset) : String.format("%.2E", offset);
    }

    public boolean isTransaction(PacketTypeCommon packetType) {
        return packetType == PacketType.Play.Client.PONG ||
                packetType == PacketType.Play.Client.WINDOW_CONFIRMATION;
    }

}

