package ac.grim.grimac.predictionengine.predictions;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.predictionengine.movementTick.MovementTickerPlayer;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.PistonData;
import ac.grim.grimac.utils.data.VectorData;
import ac.grim.grimac.utils.enums.MoverType;
import ac.grim.grimac.utils.nmsImplementations.Collisions;
import org.bukkit.Bukkit;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class PredictionEngine {

    public static Vector transformInputsToVector(GrimPlayer player, Vector theoreticalInput) {
        float bestPossibleX;
        float bestPossibleZ;

        // We save the slow movement status as it's easier and takes less CPU than recalculating it with newly stored old values
        if (player.isSlowMovement) {
            bestPossibleX = Math.min(Math.max(-1, Math.round(theoreticalInput.getX() / 0.3)), 1) * 0.3f;
            bestPossibleZ = Math.min(Math.max(-1, Math.round(theoreticalInput.getZ() / 0.3)), 1) * 0.3f;
        } else {
            bestPossibleX = Math.min(Math.max(-1, Math.round(theoreticalInput.getX())), 1);
            bestPossibleZ = Math.min(Math.max(-1, Math.round(theoreticalInput.getZ())), 1);
        }

        Vector inputVector = new Vector(bestPossibleX, 0, bestPossibleZ);
        inputVector.multiply(0.98);

        if (inputVector.lengthSquared() > 1) inputVector.normalize();

        return inputVector;
    }

    // This is just the vanilla equation, which accepts invalid inputs greater than 1
    // We need it because of collision support when a player is using speed
    public Vector getMovementResultFromInput(GrimPlayer player, Vector inputVector, float f, float f2) {
        float f3 = player.trigHandler.sin(f2 * 0.017453292f);
        float f4 = player.trigHandler.cos(f2 * 0.017453292f);

        double xResult = inputVector.getX() * f4 - inputVector.getZ() * f3;
        double zResult = inputVector.getZ() * f4 + inputVector.getX() * f3;

        return new Vector(xResult * f, 0, zResult * f);
    }

    public void guessBestMovement(float speed, GrimPlayer player) {
        player.speed = speed;
        double bestInput = Double.MAX_VALUE;

        List<VectorData> possibleVelocities = multiplyPossibilitiesByInputs(player, fetchPossibleInputs(player), speed);

        // Run pistons before sorting as an optimization
        // We will calculate the distance to actual movement after each piston
        // Each piston does have to run in order
        for (PistonData data : player.compensatedWorld.pushingPistons) {
            if (data.thisTickPushingPlayer) {
                for (SimpleCollisionBox box : data.boxes) {
                    double stageOne = 0;
                    double stageTwo = 0;

                    switch (data.direction) {
                        case EAST:
                            stageOne = box.maxX - 0.49 - player.boundingBox.minX;
                            stageOne = Math.max(0, stageOne);

                            stageTwo = box.maxX + 0.01 - player.boundingBox.minX;
                            stageTwo = Math.max(0, stageTwo);
                            break;
                        case WEST:
                            stageOne = box.maxX + 0.49 - player.boundingBox.minX;
                            stageOne = Math.max(0, stageOne);

                            stageTwo = box.minX - 0.01 - player.boundingBox.maxX;
                            stageTwo = Math.min(0, stageTwo);
                            break;
                        case NORTH:
                            stageOne = box.maxX + 0.49 - player.boundingBox.minX;
                            stageOne = Math.max(0, stageOne);

                            stageTwo = box.minZ - 0.01 - player.boundingBox.maxZ;
                            stageTwo = Math.min(0, stageTwo);
                            break;
                        case SOUTH:
                            stageOne = box.maxX - 0.49 - player.boundingBox.minX;
                            stageOne = Math.max(0, stageOne);

                            stageTwo = box.maxZ + 0.01 - player.boundingBox.minZ;
                            stageTwo = Math.max(0, stageTwo);
                            break;
                    }

                    Bukkit.broadcastMessage("X is " + stageOne + " and " + stageTwo);

                }

                break;
            }
        }

        // This is an optimization - sort the inputs by the most likely first to stop running unneeded collisions
        possibleVelocities.sort((a, b) -> compareDistanceToActualMovement(a.vector, b.vector, player));
        possibleVelocities.sort(this::putVelocityExplosionsFirst);


        // Other checks will catch ground spoofing - determine if the player can make an input below 0.03
        player.couldSkipTick = false;
        if (player.onGround) {
            possibleVelocities.forEach((a) -> player.couldSkipTick = player.couldSkipTick || a.vector.getX() * a.vector.getX() + a.vector.getZ() * a.vector.getZ() < 9.0E-4D);
        } else {
            possibleVelocities.forEach((a) -> player.couldSkipTick = player.couldSkipTick || a.vector.getX() * a.vector.getX() + a.vector.getY() * a.vector.getY() + a.vector.getZ() + a.vector.getZ() < 9.0E-4D);
        }

        VectorData bestCollisionVel = null;

        for (VectorData clientVelAfterInput : possibleVelocities) {
            Vector backOff = Collisions.maybeBackOffFromEdge(clientVelAfterInput.vector, MoverType.SELF, player);
            Vector outputVel = Collisions.collide(player, backOff.getX(), backOff.getY(), backOff.getZ());
            double resultAccuracy = outputVel.distance(player.actualMovement);

            if (resultAccuracy < bestInput) {
                bestInput = resultAccuracy;
                player.clientVelocity = backOff.clone();
                bestCollisionVel = new VectorData(outputVel.clone(), clientVelAfterInput, VectorData.VectorType.BestVelPicked);

                // Optimization - Close enough, other inputs won't get closer
                // This works as velocity is ran first
                if (resultAccuracy < 0.01) break;
            }
        }

        new MovementTickerPlayer(player).move(MoverType.SELF, player.clientVelocity, bestCollisionVel.vector);
        player.predictedVelocity = bestCollisionVel;
        endOfTick(player, player.gravity, player.friction);
    }

    public int compareDistanceToActualMovement(Vector a, Vector b, GrimPlayer player) {
        double x = player.actualMovement.getX();
        double y = player.actualMovement.getY();
        double z = player.actualMovement.getZ();

        // Weight y distance heavily to avoid jumping when we shouldn't be jumping, as it affects later ticks.
        double distance1 = Math.pow(a.getX() - x, 2) + Math.pow(a.getY() - y, 2) * 5 + Math.pow(a.getZ() - z, 2);
        double distance2 = Math.pow(b.getX() - x, 2) + Math.pow(b.getY() - y, 2) * 5 + Math.pow(b.getZ() - z, 2);

        return Double.compare(distance1, distance2);
    }

    public int putVelocityExplosionsFirst(VectorData a, VectorData b) {
        int aScore = 0;
        int bScore = 0;
        if (a.hasVectorType(VectorData.VectorType.Explosion))
            aScore++;

        if (a.hasVectorType(VectorData.VectorType.Knockback))
            aScore++;

        if (b.hasVectorType(VectorData.VectorType.Explosion))
            bScore++;

        if (b.hasVectorType(VectorData.VectorType.Knockback))
            bScore++;

        return Integer.compare(aScore, bScore);
    }

    public void addJumpsToPossibilities(GrimPlayer player, Set<VectorData> existingVelocities) {
        if (player.lastTouchingWater || player.lastTouchingLava) {
            for (VectorData vector : new HashSet<>(existingVelocities)) {
                existingVelocities.add(new VectorData(vector.vector.clone().add(new Vector(0, 0.04, 0)), vector, VectorData.VectorType.Jump));
            }
        }
    }

    public void addAdditionToPossibleVectors(GrimPlayer player, Set<VectorData> existingVelocities) {
        for (VectorData vector : new HashSet<>(existingVelocities)) {
            if (player.knownExplosion != null) {
                existingVelocities.add(new VectorData(vector.vector.clone().add(player.knownExplosion.vector), vector, VectorData.VectorType.Explosion));
            }

            if (player.firstBreadExplosion != null) {
                existingVelocities.add(new VectorData(vector.vector.clone().add(player.firstBreadExplosion.vector), vector, VectorData.VectorType.Explosion));
            }

            // Tick order of player movements vs fireworks isn't constant
            // Meaning 2x the number of fireworks can fire at once
            int maxFireworks = player.compensatedFireworks.getMaxFireworksAppliedPossible() * 2;

            if (maxFireworks > 0) {
                Vector boostOne = vector.vector.clone();
                Vector boostTwo = vector.vector.clone();

                Vector currentLook = PredictionEngineElytra.getVectorForRotation(player, player.yRot, player.xRot);
                Vector lastLook = PredictionEngineElytra.getVectorForRotation(player, player.lastYRot, player.lastXRot);

                for (int i = 0; i < maxFireworks; i++) {
                    boostOne.add(new Vector(currentLook.getX() * 0.1 + (currentLook.getX() * 1.5 - boostOne.getX()) * 0.5, currentLook.getY() * 0.1 + (currentLook.getY() * 1.5 - boostOne.getY()) * 0.5, (currentLook.getZ() * 0.1 + (currentLook.getZ() * 1.5 - boostOne.getZ()) * 0.5)));
                    boostTwo.add(new Vector(lastLook.getX() * 0.1 + (lastLook.getX() * 1.5 - boostTwo.getX()) * 0.5, lastLook.getY() * 0.1 + (lastLook.getY() * 1.5 - boostTwo.getY()) * 0.5, (lastLook.getZ() * 0.1 + (lastLook.getZ() * 1.5 - boostTwo.getZ()) * 0.5)));
                }

                SimpleCollisionBox uncertainty = new SimpleCollisionBox(Math.min(boostOne.getX(), boostTwo.getX()), Math.min(boostOne.getY(), boostTwo.getY()),
                        Math.min(boostOne.getZ(), boostTwo.getZ()), Math.max(boostOne.getX(), boostTwo.getX()),
                        Math.max(boostOne.getY(), boostTwo.getY()), Math.max(boostOne.getZ(), boostTwo.getZ()));

                // There is also the possibility that no fireworks were fired as tick order isn't constant
                uncertainty.expandToCoordinate(vector.vector.getX(), vector.vector.getY(), vector.vector.getZ());

                // Calculate distance from center point to edges of uncertainty box
                player.uncertaintyHandler.fireworksX = (uncertainty.maxX - uncertainty.minX) / 2;
                player.uncertaintyHandler.fireworksY = (uncertainty.maxY - uncertainty.minY) / 2;
                player.uncertaintyHandler.fireworksZ = (uncertainty.maxZ - uncertainty.minZ) / 2;

                // Calculate the center point
                Vector mid = new Vector(uncertainty.maxX - uncertainty.minX, uncertainty.maxY - uncertainty.minY, uncertainty.maxZ - uncertainty.minZ);

                existingVelocities.add(vector.setVector(mid, VectorData.VectorType.Firework));
            }
        }
    }

    public List<VectorData> multiplyPossibilitiesByInputs(GrimPlayer player, Set<VectorData> possibleVectors, float speed) {
        List<VectorData> returnVectors = new ArrayList<>();
        loopVectors(player, possibleVectors, speed, returnVectors);

        // There is a bug where the player sends sprinting, thinks they are sprinting, server also thinks so, but they don't have sprinting speed
        // It mostly occurs when the player takes damage.
        // This isn't going to destroy predictions as sprinting uses 1/3 the number of inputs, now 2/3 with this hack
        // Meaning there is still a 1/3 improvement for sprinting players over non-sprinting
        // If a player in this glitched state lets go of moving forward, then become un-glitched
        if (player.isSprinting) {
            player.isSprinting = false;
            speed /= 1.3D;
            loopVectors(player, possibleVectors, speed, returnVectors);
            player.isSprinting = true;
        }

        return returnVectors;
    }

    private void loopVectors(GrimPlayer player, Set<VectorData> possibleVectors, float speed, List<VectorData> returnVectors) {
        // Stop omni-sprint
        // Optimization - Also cuts down scenarios by 2/3
        int zMin = player.isSprinting ? 1 : -1;

        for (VectorData possibleLastTickOutput : possibleVectors) {
            for (int x = -1; x <= 1; x++) {
                for (int z = zMin; z <= 1; z++) {
                    VectorData result = new VectorData(possibleLastTickOutput.vector.clone().add(getMovementResultFromInput(player, transformInputsToVector(player, new Vector(x, 0, z)), speed, player.xRot)), possibleLastTickOutput, VectorData.VectorType.InputResult);
                    result = result.setVector(result.vector.clone().multiply(player.stuckSpeedMultiplier), VectorData.VectorType.StuckMultiplier);
                    result = result.setVector(handleOnClimbable(result.vector.clone(), player), VectorData.VectorType.Climbable);
                    returnVectors.add(result);
                }
            }
        }
    }

    private void handleFireworkOffset(GrimPlayer player, Set<VectorData> possibleVectors) {
        for (VectorData data : possibleVectors) {
            Vector offsetVector = player.actualMovement.clone().subtract(data.vector);

            boolean xPositive = offsetVector.getX() > 0;
            boolean yPositive = offsetVector.getY() > 0;
            boolean zPositive = offsetVector.getZ() > 0;

            double xOffset = Math.abs(offsetVector.getX());
            double yOffset = Math.abs(offsetVector.getY());
            double zOffset = Math.abs(offsetVector.getZ());

            xOffset -= player.uncertaintyHandler.fireworksX;
            yOffset -= player.uncertaintyHandler.fireworksY;
            zOffset -= player.uncertaintyHandler.fireworksZ;

            xOffset = Math.abs(Math.max(xOffset, 0));
            yOffset = Math.abs(Math.max(yOffset, 0));
            zOffset = Math.abs(Math.max(zOffset, 0));

            offsetVector.subtract(new Vector(xOffset * (xPositive ? 1 : -1),
                    yOffset * (yPositive ? 1 : -1),
                    zOffset * (zPositive ? 1 : -1)));

            data.setVector(data.vector.add(offsetVector), VectorData.VectorType.Elytra);
        }
    }

    public Set<VectorData> fetchPossibleInputs(GrimPlayer player) {
        Set<VectorData> velocities = player.getPossibleVelocities();

        addAdditionToPossibleVectors(player, velocities);
        addJumpsToPossibilities(player, velocities);

        return velocities;
    }

    public Vector handleOnClimbable(Vector vector, GrimPlayer player) {
        return vector;
    }

    public void endOfTick(GrimPlayer player, double d, float friction) {
        player.clientVelocitySwimHop = null;
        if (canSwimHop(player)) {
            player.clientVelocitySwimHop = player.clientVelocity.clone().setY(0.3);
        }
    }

    public boolean canSwimHop(GrimPlayer player) {
        boolean canCollideHorizontally = !Collisions.isEmpty(player, player.boundingBox.copy().expand(0.1, -0.01, 0.1));
        boolean inWater = player.compensatedWorld.containsLiquid(player.boundingBox.copy().expand(0.1, 0.1, 0.1));

        // Vanilla system ->
        // Requirement 1 - The player must be in water or lava
        // Requirement 2 - The player must have X position + X movement, Y position + Y movement - Y position before tick + 0.6, Z position + Z movement have no collision
        // Requirement 3 - The player must have horizontal collision

        // Our system ->
        // Requirement 1 - The player must be within 0.1 blocks of water or lava (which is why this is base and not PredictionEngineWater/Lava)
        // Requirement 2 - The player must have something to collide with within 0.1 blocks

        // Why remove the empty check?  The real movement is hidden due to the horizontal collision
        // For example, a 1.14+ player can have a velocity of (10000, 0, 0) and if they are against a wall,
        // We only see the (0,0,0) velocity.
        // This means it is impossible to accurately create the requirement of no collision.
        // Oh well, I guess this could allow some Jesus bypasses next to a wall that has multiple blocks
        // But it's faster to swim anyways on 1.13+, and faster to just go on land in 1.12-

        return canCollideHorizontally && inWater;
    }
}