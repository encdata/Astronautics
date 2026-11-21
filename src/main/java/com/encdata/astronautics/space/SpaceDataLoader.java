package com.encdata.astronautics.space;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.encdata.astronautics.Astronautics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SpaceDataLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    public static final SpaceDataLoader INSTANCE = new SpaceDataLoader();

    private SpaceDataLoader() {
        super(GSON, "astronautics/galaxies");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<Patch> patches = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            try {
                JsonObject document = entry.getValue().getAsJsonObject().deepCopy();
                ResourceLocation id = ResourceLocation.parse(requiredString(document, "id"));
                String mode = document.has("mode") ? document.get("mode").getAsString() : "replace";
                int priority = document.has("priority") ? document.get("priority").getAsInt() : 0;
                List<String> removals = readRemovals(document);
                document.remove("mode");
                document.remove("priority");
                document.remove("remove");
                if (!mode.equals("merge") && !mode.equals("replace")) {
                    throw new JsonParseException("Unknown galaxy patch mode: " + mode);
                }
                patches.add(new Patch(entry.getKey(), id, mode, priority, document, removals));
            } catch (RuntimeException exception) {
                Astronautics.LOGGER.error("Could not read galaxy patch {}", entry.getKey(), exception);
                return;
            }
        }

        patches.sort(Comparator.comparingInt(Patch::priority)
                .thenComparing(Patch::mode)
                .thenComparing(patch -> patch.path().toString()));
        Map<ResourceLocation, JsonObject> definitions = new HashMap<>();
        for (Patch patch : patches) {
            if (patch.mode().equals("replace")) {
                definitions.put(patch.id(), patch.document());
            } else {
                JsonObject existing = definitions.get(patch.id());
                if (existing == null) {
                    Astronautics.LOGGER.error("Cannot merge galaxy {} from {} because that galaxy is not defined",
                            patch.id(), patch.path());
                    return;
                }
                definitions.put(patch.id(), mergeObjects(existing, patch.document()));
            }

            JsonObject definition = definitions.get(patch.id());
            for (String id : patch.removals()) {
                if (!removeCelestial(definition, id)) {
                    Astronautics.LOGGER.warn("Could not remove celestial body {} from galaxy {} in {} because it was not found",
                            id, patch.id(), patch.path());
                }
            }
        }

        Map<ResourceLocation, GalaxyData> loaded = new HashMap<>();
        try {
            for (Map.Entry<ResourceLocation, JsonObject> entry : definitions.entrySet()) {
                GalaxyData galaxy = GalaxyData.decode(entry.getValue());
                if (!galaxy.id().equals(entry.getKey())) {
                    throw new IllegalArgumentException("Galaxy id changed while applying patches: " + entry.getKey());
                }
                loaded.put(galaxy.id(), galaxy);
            }
            SpaceCatalog.replace(loaded);
        } catch (RuntimeException exception) {
            Astronautics.LOGGER.error("Could not apply Astronautics galaxy data; keeping the previous catalog", exception);
            return;
        }

        Astronautics.syncUniverseToPlayers();
        Astronautics.LOGGER.info("Loaded {} Astronautics galaxies", loaded.size());
    }

    private static JsonObject mergeObjects(JsonObject base, JsonObject patch) {
        JsonObject result = base.deepCopy();
        for (Map.Entry<String, JsonElement> entry : patch.entrySet()) {
            String key = entry.getKey();
            JsonElement patchValue = entry.getValue();
            JsonElement baseValue = result.get(key);
            if (patchValue.isJsonObject() && baseValue != null && baseValue.isJsonObject()) {
                result.add(key, mergeObjects(baseValue.getAsJsonObject(), patchValue.getAsJsonObject()));
            } else if (patchValue.isJsonArray() && baseValue != null && baseValue.isJsonArray()
                    && (key.equals("stars") || key.equals("bodies"))) {
                result.add(key, mergeById(baseValue.getAsJsonArray(), patchValue.getAsJsonArray()));
            } else {
                result.add(key, patchValue.deepCopy());
            }
        }
        return result;
    }

    private static JsonArray mergeById(JsonArray base, JsonArray patch) {
        JsonArray result = base.deepCopy();
        Map<String, Integer> indexById = new HashMap<>();
        for (int i = 0; i < result.size(); i++) {
            JsonElement element = result.get(i);
            if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
                indexById.put(element.getAsJsonObject().get("id").getAsString(), i);
            }
        }

        for (JsonElement element : patch) {
            if (!element.isJsonObject() || !element.getAsJsonObject().has("id")) {
                throw new JsonParseException("Entries in stars and bodies arrays must have an id");
            }
            String id = element.getAsJsonObject().get("id").getAsString();
            Integer index = indexById.get(id);
            if (index == null) {
                indexById.put(id, result.size());
                result.add(element.deepCopy());
            } else {
                JsonElement existing = result.get(index);
                result.set(index, mergeObjects(existing.getAsJsonObject(), element.getAsJsonObject()));
            }
        }
        return result;
    }

    private static List<String> readRemovals(JsonObject document) {
        JsonElement value = document.get("remove");
        if (value == null) return List.of();
        if (!value.isJsonArray()) {
            throw new JsonParseException("Galaxy patch field 'remove' must be an array of resource locations");
        }

        List<String> removals = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("Galaxy patch removals must be resource location strings");
            }
            removals.add(ResourceLocation.parse(element.getAsString()).toString());
        }
        return List.copyOf(removals);
    }

    private static boolean removeCelestial(JsonObject galaxy, String id) {
        JsonElement stars = galaxy.get("stars");
        return stars != null && stars.isJsonArray() && removeFromArray(stars.getAsJsonArray(), id);
    }

    private static boolean removeFromArray(JsonArray celestials, String id) {
        boolean removed = false;
        for (int i = celestials.size() - 1; i >= 0; i--) {
            JsonElement element = celestials.get(i);
            if (!element.isJsonObject()) continue;
            JsonObject celestial = element.getAsJsonObject();
            JsonElement celestialId = celestial.get("id");
            if (celestialId != null && celestialId.isJsonPrimitive()
                    && celestialId.getAsString().equals(id)) {
                celestials.remove(i);
                removed = true;
                continue;
            }
            JsonElement bodies = celestial.get("bodies");
            if (bodies != null && bodies.isJsonArray()) {
                removed |= removeFromArray(bodies.getAsJsonArray(), id);
            }
        }
        return removed;
    }

    private static String requiredString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Galaxy patch requires string field '" + field + "'");
        }
        return value.getAsString();
    }

    @Override
    public String getName() {
        return "Astronautics galaxies";
    }

    private record Patch(ResourceLocation path, ResourceLocation id, String mode, int priority,
                         JsonObject document, List<String> removals) {}
}
