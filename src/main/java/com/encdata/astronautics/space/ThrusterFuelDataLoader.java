package com.encdata.astronautics.space;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.encdata.astronautics.Astronautics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ThrusterFuelDataLoader extends SimpleJsonResourceReloadListener {
    private static final String FUEL_DIRECTORY = "astronautics/fuels";
    private static final Gson GSON = new Gson();
    private static final Map<ResourceLocation, ThrusterFuelData> FUELS = new ConcurrentHashMap<>();
    private static volatile boolean reloadComplete;
    public static final ThrusterFuelDataLoader INSTANCE = new ThrusterFuelDataLoader();

    private ThrusterFuelDataLoader() {
        super(GSON, FUEL_DIRECTORY);
    }

    public static ThrusterFuelData get(ResourceLocation fluid) {
        return FUELS.getOrDefault(fluid, ThrusterFuelData.DEFAULT);
    }

    public static boolean isFuel(ResourceLocation fluid) {
        return FUELS.containsKey(fluid) || (!reloadComplete && isBuiltInFuel(fluid));
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, ThrusterFuelData> loaded = new HashMap<>();
        for (var entry : files.entrySet()) {
            try {
                var json = entry.getValue().getAsJsonObject();
                ResourceLocation fluid = ResourceLocation.parse(json.get("fluid").getAsString());
                ThrusterFuelData fuel = new ThrusterFuelData(
                        fluid,
                        json.get("efficiency").getAsDouble(),
                        json.get("thrust_multiplier").getAsDouble(),
                        json.get("fuel_consumption").getAsInt(),
                        json.get("required_thruster_tier").getAsInt(),
                        json.get("max_acceleration").getAsDouble(),
                        json.get("max_speed").getAsDouble()
                );
                validate(fuel);
                loaded.put(fluid, fuel);
            } catch (RuntimeException exception) {
                Astronautics.LOGGER.error("Could not load thruster fuel {}", entry.getKey(), exception);
            }
        }
        FUELS.clear();
        FUELS.putAll(loaded);
        reloadComplete = true;
        Astronautics.LOGGER.info("Loaded {} Astronautics thruster fuels", loaded.size());
    }

    private static void validate(ThrusterFuelData fuel) {
        if (!Double.isFinite(fuel.efficiency()) || fuel.efficiency() <= 0.0
                || !Double.isFinite(fuel.thrustMultiplier()) || fuel.thrustMultiplier() < 0.0
                || fuel.fuelConsumption() <= 0 || fuel.requiredThrusterTier() <= 0
                || !Double.isFinite(fuel.maxAcceleration()) || fuel.maxAcceleration() < 0.0
                || !Double.isFinite(fuel.maxSpeed()) || fuel.maxSpeed() < 0.0) {
            throw new IllegalArgumentException("Invalid thruster fuel values for " + fuel.fluid());
        }
    }

    private static boolean isBuiltInFuel(ResourceLocation fluid) {
        if (fluid == null || !fluid.getNamespace().equals(Astronautics.MOD_ID)) return false;
        return switch (fluid.getPath()) {
            case "bioethanol", "methanol", "gasoline", "kerosene", "hydrogen" -> true;
            default -> false;
        };
    }

    @Override
    public String getName() {
        return "Astronautics thruster fuels";
    }
}
