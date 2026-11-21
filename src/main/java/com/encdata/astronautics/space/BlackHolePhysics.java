package com.encdata.astronautics.space;

public final class BlackHolePhysics {
    public static final double GRAVITATIONAL_CONSTANT = 6.67430e-11;
    public static final double SPEED_OF_LIGHT = 299_792_458.0;
    public static final double SOLAR_MASS_KG = 1.98847e30;
    public static final double SOLAR_MASS_PER_YEAR_KG = 6.30108e22;
    public static final double METRES_PER_KILOMETRE = 1_000.0;
    private static final double SCHWARZSCHILD_FACTOR = 2.0;
    private static final double CRITICAL_IMPACT_FACTOR = 1.5 * Math.sqrt(3.0);
    private static final double MAX_ABS_SPIN = 0.998;
    private static final double MIN_MASS_SOLAR = 1.0e-6;
    private static final double MIN_RADIUS_KM = 1.0;
    private static final double STEFAN_BOLTZMANN_CONSTANT = 5.670374419e-8;

    private BlackHolePhysics() {}

    public static double schwarzschildRadiusKm(double massSolar) {
        double mass = Math.max(massSolar, 0.0) * SOLAR_MASS_KG;
        return SCHWARZSCHILD_FACTOR * GRAVITATIONAL_CONSTANT * mass
                / (SPEED_OF_LIGHT * SPEED_OF_LIGHT) / METRES_PER_KILOMETRE;
    }

    public static double iscoRadiusSchwarzschild(double spin) {
        double a = Math.clamp(spin, -MAX_ABS_SPIN, MAX_ABS_SPIN);
        double sign = a < 0.0 ? -1.0 : 1.0;
        double abs = Math.abs(a);
        double z1 = 1.0 + Math.cbrt(1.0 - abs * abs)
                * (Math.cbrt(1.0 + abs) + Math.cbrt(1.0 - abs));
        double z2 = Math.sqrt(3.0 * abs * abs + z1 * z1);
        double iscoRadiusSchwarzschildUnits = 3.0 + z2
                - sign * Math.sqrt(Math.max(0.0, (3.0 - z1) * (3.0 + z1 + 2.0 * z2)));
        return iscoRadiusSchwarzschildUnits * 0.5;
    }

    public static double innerDiskRadiusKm(double massSolar, double spin) {
        return schwarzschildRadiusKm(massSolar) * iscoRadiusSchwarzschild(spin);
    }

    public static double criticalImpactRadiusKm(double massSolar) {
        return CRITICAL_IMPACT_FACTOR * schwarzschildRadiusKm(massSolar);
    }

    public static double diskTemperatureKelvin(double massSolar, double spin,
                                               double accretionRateSolarYear, double radiusKm) {
        double mass = Math.max(massSolar, MIN_MASS_SOLAR) * SOLAR_MASS_KG;
        double accretion = Math.max(accretionRateSolarYear, 0.0) * SOLAR_MASS_PER_YEAR_KG;
        double radius = Math.max(radiusKm, MIN_RADIUS_KM) * METRES_PER_KILOMETRE;
        double inner = Math.max(innerDiskRadiusKm(massSolar, spin), MIN_RADIUS_KM) * METRES_PER_KILOMETRE;
        double factor = Math.max(0.0, 1.0 - Math.sqrt(Math.min(inner / radius, 1.0)));
        return Math.pow(Math.max(3.0 * GRAVITATIONAL_CONSTANT * mass * accretion
                * factor / (8.0 * Math.PI * STEFAN_BOLTZMANN_CONSTANT * radius * radius * radius), 0.0), 0.25);
    }
}
