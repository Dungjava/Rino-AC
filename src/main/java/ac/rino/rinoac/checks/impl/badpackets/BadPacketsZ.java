package ac.rino.rinoac.checks.impl.badpackets;

import ac.rino.rinoac.checks.Check;
import ac.rino.rinoac.checks.CheckData;
import ac.rino.rinoac.checks.type.PacketCheck;
import ac.rino.rinoac.player.RinoPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

import static ac.rino.rinoac.events.packets.patch.ResyncWorldUtil.resyncPosition;
import static ac.rino.rinoac.utils.nmsutil.BlockBreakSpeed.getBlockDamage;

@CheckData(name = "BadPacketsZ", experimental = true)
public class BadPacketsZ extends Check implements PacketCheck {
    private final int exemptedY = player.getClientVersion().isOlderThan(ClientVersion.V_1_8) ? 255 : (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_14) ? -1 : 4095);
    private boolean lastBlockWasInstantBreak = false;
    private Vector3i lastBlock, lastCancelledBlock, lastLastBlock = null;
    public BadPacketsZ(final RinoPlayer player) {
        super(player);
    }

    // The client sometimes sends a wierd cancel packet
    private boolean shouldExempt(final Vector3i pos) {
        // lastLastBlock is always null when this happens, and lastBlock isn't
        if (lastLastBlock != null || lastBlock == null)
            return false;

        // on pre 1.14.4 clients, the YPos of this packet is always the same
        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_14_4) && pos.y != exemptedY)
            return false;

        // and if this block is not an instant break
        return player.getClientVersion().isOlderThan(ClientVersion.V_1_14_4) || getBlockDamage(player, pos) < 1;
    }

    private String formatted(Vector3i vec) {
        return vec == null ? "null" : vec.x + ", " + vec.y + ", " + vec.z;
    }

    public void handle(PacketReceiveEvent event, WrapperPlayClientPlayerDigging dig) {
        if (dig.getAction() == DiggingAction.START_DIGGING) {
            final Vector3i pos = dig.getBlockPosition();

            lastBlockWasInstantBreak = getBlockDamage(player, pos) >= 1;
            lastCancelledBlock = null;
            lastLastBlock = lastBlock;
            lastBlock = pos;
        }

        if (dig.getAction() == DiggingAction.CANCELLED_DIGGING) {
            final Vector3i pos = dig.getBlockPosition();

            if (shouldExempt(pos)) {
                lastCancelledBlock = null;
                lastLastBlock = null;
                lastBlock = null;
                return;
            }

            if (!pos.equals(lastBlock)) {
                // https://github.com/GrimAnticheat/Grim/issues/1512
                if (player.getClientVersion().isOlderThan(ClientVersion.V_1_14_4) || (!lastBlockWasInstantBreak && pos.equals(lastCancelledBlock))) {
                    if (flagAndAlert("action=CANCELLED_DIGGING" + ", last=" + formatted(lastBlock) + ", pos=" + formatted(pos))) {
                        if (shouldModifyPackets()) {
                            event.setCancelled(true);
                            player.onPacketCancel();
                            resyncPosition(player, pos);
                        }
                    }
                }
            }

            lastCancelledBlock = pos;
            lastLastBlock = null;
            lastBlock = null;
            return;
        }

        if (dig.getAction() == DiggingAction.FINISHED_DIGGING) {
            final Vector3i pos = dig.getBlockPosition();

            // when a player looks away from the mined block, they send a cancel, and if they look at it again, they don't send another start. (thanks mojang!)
            if (!pos.equals(lastCancelledBlock) && (!lastBlockWasInstantBreak || player.getClientVersion().isOlderThan(ClientVersion.V_1_14_4)) && !pos.equals(lastBlock)) {
                if (flagAndAlert("action=FINISHED_DIGGING" + ", last=" + formatted(lastBlock) + ", pos=" + formatted(pos))) {
                    if (shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                        resyncPosition(player, pos);
                    }
                }
            }

            lastCancelledBlock = null;

            // 1.14.4+ clients don't send another start break in protected regions
            if (player.getClientVersion().isOlderThan(ClientVersion.V_1_14_4)) {
                lastLastBlock = null;
                lastBlock = null;
            }
        }
    }
}