package ac.rino.rinoac.predictionengine.movementtick;

import ac.rino.rinoac.player.RinoPlayer;
import ac.rino.rinoac.utils.data.packetentity.PacketEntityHorse;
import ac.rino.rinoac.utils.nmsutil.Collisions;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.bukkit.util.Vector;

public class MovementTickerHorse extends MovementTickerLivingVehicle {

    public MovementTickerHorse(RinoPlayer player) {
        super(player);

        PacketEntityHorse horsePacket = (PacketEntityHorse) player.compensatedEntities.getSelf().getRiding();

        if (!horsePacket.hasSaddle) return;

        player.speed = horsePacket.getAttributeValue(Attributes.GENERIC_MOVEMENT_SPEED);

        // Setup player inputs
        float horizInput = player.vehicleData.vehicleHorizontal * 0.5F;
        float forwardsInput = player.vehicleData.vehicleForward;

        if (forwardsInput <= 0.0F) {
            forwardsInput *= 0.25F;
        }

        this.movementInput = new Vector(horizInput, 0, forwardsInput);
        if (movementInput.lengthSquared() > 1) movementInput.normalize();
    }

    @Override
    public void livingEntityAIStep() {
        super.livingEntityAIStep();
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_17)) Collisions.handleInsideBlocks(player);
    }
}
