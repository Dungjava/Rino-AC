package ac.rino.rinoac.predictionengine.movementtick;

import ac.rino.rinoac.player.RinoPlayer;
import ac.rino.rinoac.utils.data.packetentity.PacketEntityRideable;
import ac.rino.rinoac.utils.nmsutil.Collisions;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

public class MovementTickerRideable extends MovementTickerLivingVehicle {

    public MovementTickerRideable(RinoPlayer player) {
        super(player);

        // If the player has carrot/fungus on a stick, otherwise the player has no control
        float f = getSteeringSpeed();

        PacketEntityRideable boost = ((PacketEntityRideable) player.compensatedEntities.getSelf().getRiding());

        // Do stuff for boosting on a pig/strider
        if (boost.currentBoostTime++ < boost.boostTimeMax) {
            // I wonder how much fastmath actually affects boosting movement
            f += f * 1.15F * player.trigHandler.sin((float) boost.currentBoostTime / (float) boost.boostTimeMax * (float) Math.PI);
        }

        player.speed = f;

    }

    // Pig and Strider should implement this
    public float getSteeringSpeed() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void livingEntityTravel() {
        super.livingEntityTravel();
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_17)) Collisions.handleInsideBlocks(player);
    }
}