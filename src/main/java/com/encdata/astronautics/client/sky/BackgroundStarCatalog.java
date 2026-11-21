package com.encdata.astronautics.client.sky;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public final class BackgroundStarCatalog {
    public static final int COUNT = 9000;
    public static final int STRIDE = 9;
    private static final CompletableFuture<BackgroundStarCatalog> GENERATION =
            CompletableFuture.supplyAsync(BackgroundStarCatalog::new);
    private final float[] data = new float[COUNT * STRIDE];
    public final int brightStars;
    public final int galacticStars;

    private BackgroundStarCatalog() {
        Random random = new Random(10842L);
        int bright = 0;
        for (int i = 0; i < COUNT; i++) {

            double longitude = random.nextDouble() * Math.PI * 2;
            double latitude = Math.asin(random.nextDouble() * 2 - 1);
            double x = Math.cos(latitude) * Math.cos(longitude);
            double y = Math.sin(latitude);
            double z = Math.cos(latitude) * Math.sin(longitude);
            int p = i * STRIDE;
            data[p] = (float) x;
            data[p + 1] = (float) y;
            data[p + 2] = (float) z;
            double tier = random.nextDouble();
            float magnitude = (float) (tier < .70 ? 5.6 + random.nextDouble() * 1.9
                    : tier < .90 ? 4.4 + random.nextDouble() * 1.2
                    : tier < .98 ? 2.6 + random.nextDouble() * 1.8
                    : tier < .998 ? .8 + random.nextDouble() * 1.8
                    : -1.2 + random.nextDouble() * 2);
            if (magnitude < 2.6f) bright++;
            double t = random.nextDouble();
            double kelvin = t < .06 ? 11000 + random.nextDouble() * 9000
                    : t < .32 ? 6500 + random.nextDouble() * 3500
                    : t < .78 ? 4500 + random.nextDouble() * 2000
                    : 2900 + random.nextDouble() * 1600;
            temperatureColor(kelvin, data, p + 3);
            data[p + 6] = magnitude;
            data[p + 7] = magnitude > 5.6 ? .8f : magnitude > 4.4 ? 1f
                    : magnitude > 2.6 ? 1.15f : magnitude > .8 ? 1.5f : 2f;
            data[p + 8] = random.nextFloat() * 6.2831853f;
        }
        brightStars = bright;
        galacticStars = 0;
    }

    public static BackgroundStarCatalog ready() {
        return GENERATION.getNow(null);
    }

    private static void temperatureColor(double kelvin, float[] output, int offset) {
        double t = kelvin / 100;
        double r = t <= 66 ? 255 : 329.698727 * Math.pow(t - 60, -.13320476);
        double g = t <= 66 ? 99.470802 * Math.log(t) - 161.119568
                : 288.12217 * Math.pow(t - 60, -.07551485);
        double b = t >= 66 ? 255 : t <= 19 ? 0 : 138.517731 * Math.log(t - 10) - 305.044793;
        output[offset] = (float) (.55 + .45 * Math.clamp(r / 255, 0, 1));
        output[offset + 1] = (float) (.55 + .45 * Math.clamp(g / 255, 0, 1));
        output[offset + 2] = (float) (.55 + .45 * Math.clamp(b / 255, 0, 1));
    }

    float[] uploadData() { return data; }
}
