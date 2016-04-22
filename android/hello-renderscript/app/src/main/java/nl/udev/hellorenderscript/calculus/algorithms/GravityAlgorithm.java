package nl.udev.hellorenderscript.calculus.algorithms;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Float2;
import android.renderscript.Short4;
import android.text.Html;

import nl.udev.hellorenderscript.calculus.AbstractCalculusAlgorithm;
import nl.udev.hellorenderscript.calculus.ScriptC_gravity;
import nl.udev.hellorenderscript.common.algoritm.parameter.IntegerParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.ParameterUser;
import nl.udev.hellorenderscript.common.algoritm.parameter.TouchPositionParameter;
import nl.udev.hellorenderscript.common.algoritm.parts.Plotting;
import nl.udev.hellorenderscript.common.algoritm.parts.RsUtils;
import nl.udev.hellorenderscript.video.ScriptC_utils;

/**
 * Simulation of two forces and particle mass.
 *
 * See #getDescription
 *
 * Created by ben on 22-4-16.
 */
public class GravityAlgorithm extends AbstractCalculusAlgorithm {

    private static final String TAG = "GravityAlgorithm";

    private static final int COUNT = 512;

    private ScriptC_utils rsUtils;
    private ScriptC_gravity rsGravity;

    private Plotting plotting;
    private Allocation pointPositions, pointVelocityVectors, pointForceVectors;
    Float2 tractorPosition;
    float strongForceConstant;
    float distanceMultiplier;
    float forceMultiplier;
    float particleMass;

    public GravityAlgorithm() {
        addParameter(new TouchPositionParameter("GravityPosition", new TouchHandler()));
        addParameter(new IntegerParameter("StrongForce", 1, 3000, 1700, new StrongForceParameter()));
        addParameter(new IntegerParameter("ForceAmplify", 1, 10000, 1000, new ForceAmplifyParameter()));
        addParameter(new IntegerParameter("Distance", 1, 10000, 4000, new DistanceParameter()));
        addParameter(new IntegerParameter("ParticleMass", 1, 1000, 200, new MassParameter()));
    }

    @Override
    public void cycle(Allocation displayBufferRgba) {

        rsGravity.set_pointCount(COUNT);
        rsGravity.set_strongForceConstant(strongForceConstant);
        rsGravity.set_distanceMultiplier(distanceMultiplier);
        rsGravity.set_forceMultiplier(forceMultiplier);
        rsGravity.set_particleMass(particleMass);

        rsGravity.set_pointPositions(pointPositions);
        rsGravity.set_pointVelocities(pointVelocityVectors);
        
        // Calculate the force on each point
        rsGravity.forEach_calcForces(pointPositions, pointForceVectors);

        // Apply force from the user if present
        Float2 userForce = tractorPosition;
        if(userForce != null) {
            rsGravity.set_userForcePosition(userForce);
            rsGravity.forEach_applyUserForce(pointForceVectors, pointForceVectors);
        }

        // Change the direction of the point by applying the force
        rsGravity.forEach_applyForces(pointForceVectors, pointVelocityVectors);

        // Move the points with the current velocity vector
        rsGravity.forEach_move(pointVelocityVectors, pointPositions);

        rsUtils.forEach_fadeUchar4(displayBufferRgba, displayBufferRgba);
        short c = 255;
        Short4 plotColor = new Short4(c, c, c, c);
        plotting.plot(pointPositions, COUNT, plotColor, displayBufferRgba, getResolution().getWidth(), getResolution().getHeight());
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public CharSequence getDescription() {
        return Html.fromHtml("Simulates two interacting forces and mass of particles." +
                        " Progressive algorithm, each step all forces between all particles are calculated." +
                        " Next, the forces are applied to the particle velocity taking mass into account." +
                        "<br>" +
                        "<br><b>StrongForce</b> repulses particles" +
                        "<br><b>WeakForce</b> attracts particles"
        );
    }

    @Override
    protected void initialize() {
        pointPositions = RsUtils.create1d(getRenderScript(), COUNT, Element.F32_2(getRenderScript()));
        pointVelocityVectors = RsUtils.create1d(getRenderScript(), COUNT, Element.F32_2(getRenderScript()));
        pointForceVectors = RsUtils.create1d(getRenderScript(), COUNT, Element.F32_2(getRenderScript()));

        rsUtils = new ScriptC_utils(getRenderScript());
        rsGravity = new ScriptC_gravity(getRenderScript());
        plotting = new Plotting(getRenderScript());

        pointPositions.copyFrom(createInitialPositions(4));

        rsUtils.forEach_clearFloat2(pointVelocityVectors);
    }

    @Override
    protected void unInitialize() {

        pointPositions.destroy();
        pointVelocityVectors.destroy();
        pointForceVectors.destroy();
        plotting.destroy();
        rsUtils.destroy();
        rsGravity.destroy();
    }

    private static float[] createInitialPositions(float maxDistance) {
        float positions[] = new float[COUNT * 2];

        for(int x = 0; x < COUNT; x++) {
            positions[x * 2] = (float) (Math.random() * maxDistance - maxDistance / 2);
            positions[x * 2 + 1] = (float) (Math.random() * maxDistance - maxDistance / 2);
        }

        return positions;
    }

    private class TouchHandler implements TouchPositionParameter.TouchHandler {

        @Override
        public void moved(float xp, float yp) {
            tractorPosition = new Float2(xp, yp);
        }

        @Override
        public void released() {
            tractorPosition = null;
        }
    }

    private class StrongForceParameter implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%4.0f", strongForceConstant);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            strongForceConstant = newValue;
        }
    }

    private class ForceAmplifyParameter implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%4.0f", forceMultiplier);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            forceMultiplier = newValue;
        }
    }

    private class DistanceParameter implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%4.0f", distanceMultiplier);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            distanceMultiplier = newValue;
        }
    }

    private class MassParameter implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%4.0f", particleMass);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            particleMass = newValue;
        }
    }
}
