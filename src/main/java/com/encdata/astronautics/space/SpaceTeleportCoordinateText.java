package com.encdata.astronautics.space;

import com.mojang.brigadier.StringReader;
import java.math.BigDecimal;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;

public final class SpaceTeleportCoordinateText {
    private static final ThreadLocal<ParseStart> START = new ThreadLocal<>();
    private static final ThreadLocal<Map<WorldCoordinates, Parsed>> PARSED =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private static final ThreadLocal<StringReader> READER = new ThreadLocal<>();

    private SpaceTeleportCoordinateText() {}

    public static void begin(StringReader reader, int cursor) {
        if (READER.get() != reader) {
            PARSED.get().clear();
            READER.set(reader);
        }
        START.set(new ParseStart(reader, cursor));
    }

    public static void capture(WorldCoordinates coordinates, StringReader reader, int cursor, boolean centerCorrect) {
        ParseStart start = START.get();
        START.remove();
        if (start == null || start.reader() != reader || cursor < start.cursor()) return;

        String[] values = reader.getString().substring(start.cursor(), cursor).trim().split("\\s+");
        if (values.length != 3) return;

        Map<WorldCoordinates, Parsed> parsed = PARSED.get();
        if (parsed.size() >= 8) parsed.clear();
        parsed.put(coordinates, new Parsed(coordinates, values, centerCorrect));
    }

    public static Parsed take(Coordinates coordinates) {
        return coordinates instanceof WorldCoordinates worldCoordinates
                ? PARSED.get().remove(worldCoordinates)
                : null;
    }

    public static void clear() {
        START.remove();
        PARSED.remove();
        READER.remove();
    }

    private record ParseStart(StringReader reader, int cursor) {}

    public record Parsed(WorldCoordinates coordinates, String[] values, boolean centerCorrect) {
        public Parsed {
            values = values.clone();
        }

        public BigDecimal value(int axis, double relativeOrigin) {
            String raw = values[axis];
            boolean relative = raw.startsWith("~");
            String number = relative ? raw.substring(1) : raw;
            BigDecimal value = number.isEmpty() ? BigDecimal.ZERO : new BigDecimal(number);
            if (relative) return BigDecimal.valueOf(relativeOrigin).add(value);
            if (centerCorrect && axis != 1 && !raw.contains(".")) {
                return value.add(BigDecimal.valueOf(0.5));
            }
            return value;
        }
    }
}
