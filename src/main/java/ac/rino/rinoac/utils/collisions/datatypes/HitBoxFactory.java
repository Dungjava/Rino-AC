package ac.rino.rinoac.utils.collisions.datatypes;

import ac.rino.rinoac.player.RinoPlayer;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;

public interface HitBoxFactory {
    CollisionBox fetch(RinoPlayer player, StateType heldItem, ClientVersion version, WrappedBlockState block, int x, int y, int z);
}
