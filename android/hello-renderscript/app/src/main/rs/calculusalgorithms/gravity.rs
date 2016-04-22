#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript.calculus)

rs_allocation pointPositions;
rs_allocation pointVelocities;

int pointCount;
float strongForceConstant;
float distanceMultiplier;
float forceMultiplier;

float2 __attribute__((kernel)) calcForces(float2 in, uint32_t x) {

    float2 otherPoint, delta, result = 0;
    float dist, strongF, weakF, force;
    // Calculate the force this particle has with all others
    for(int c = 0; c < pointCount; c++) {
        if(c != x) {

            otherPoint = rsGetElementAt_float2(pointPositions, c);
            delta = otherPoint - in;
            dist = length(delta) * distanceMultiplier;

            strongF = strongForceConstant / (dist * dist);
            weakF = 1 / dist;
            force = -strongF + weakF;

            result += (delta / dist) * force * forceMultiplier;
        }
    }

    return result;
}

float2 userForcePosition;

float2 __attribute__((kernel)) applyUserForce(float2 in, uint32_t x) {

    float2 otherPoint, delta, result = 0;
    float dist, strongF, weakF, force;

    delta = userForcePosition - in;
    dist = length(delta) * 128;
    force = 1 / (dist * dist);
    in += (delta / dist) * force;

    return in;
}

float particleMass;

float2 __attribute__((kernel)) applyForces(float2 in, uint32_t x) {
    return (in + particleMass * rsGetElementAt_float2(pointVelocities, x)) / (particleMass + 1);
}

float2 __attribute__((kernel)) move(float2 in, uint32_t x) {
    return in + rsGetElementAt_float2(pointPositions, x);
}

