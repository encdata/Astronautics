package com.encdata.astronautics.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SpacePosition(SpaceGridAddress grid, double x, double y, double z) {
    public static final double GRID_SIZE = 6_000_000.0;
    public static final double GRID_HALF_SIZE = GRID_SIZE * 0.5;
    private static final BigDecimal GRID_SIZE_DECIMAL = BigDecimal.valueOf(GRID_SIZE);
    private static final BigDecimal GRID_HALF_SIZE_DECIMAL = BigDecimal.valueOf(GRID_HALF_SIZE);
    private static final Codec<Double> FINITE_DOUBLE = Codec.DOUBLE.flatXmap(
            value -> Double.isFinite(value)
                    ? DataResult.success(value)
                    : DataResult.error(() -> "space position must be finite"),
            value -> Double.isFinite(value)
                    ? DataResult.success(value)
                    : DataResult.error(() -> "space position must be finite"));

    public static final Codec<SpacePosition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SpaceGridAddress.CODEC.fieldOf("grid").forGetter(SpacePosition::grid),
            FINITE_DOUBLE.fieldOf("x").forGetter(SpacePosition::x),
            FINITE_DOUBLE.fieldOf("y").forGetter(SpacePosition::y),
            FINITE_DOUBLE.fieldOf("z").forGetter(SpacePosition::z)
    ).apply(instance, SpacePosition::new));

    public static final SpacePosition ORIGIN = new SpacePosition(SpaceGridAddress.ORIGIN, 0.0, 0.0, 0.0);

    public SpacePosition {
        Objects.requireNonNull(grid);
        if (!isCanonical(x) || !isCanonical(y) || !isCanonical(z)) {
            throw new IllegalArgumentException("space position must be finite and normalized");
        }
    }

    public static SpacePosition normalize(SpaceGridAddress grid, double x, double y, double z) {
        AxisPosition nx = normalizeAxis(grid.x(), x);
        AxisPosition ny = normalizeAxis(grid.y(), y);
        AxisPosition nz = normalizeAxis(grid.z(), z);
        return new SpacePosition(
                new SpaceGridAddress(nx.grid(), ny.grid(), nz.grid()),
                nx.local(), ny.local(), nz.local());
    }

    public static SpacePosition normalize(SpaceGridAddress grid, BigDecimal x, BigDecimal y, BigDecimal z) {
        AxisPosition nx = normalizeAxis(grid.x(), x);
        AxisPosition ny = normalizeAxis(grid.y(), y);
        AxisPosition nz = normalizeAxis(grid.z(), z);
        return new SpacePosition(
                new SpaceGridAddress(nx.grid(), ny.grid(), nz.grid()),
                nx.local(), ny.local(), nz.local());
    }

    public SpaceVec3 originFrame() {
        return new SpaceVec3(grid.x().doubleValue() * GRID_SIZE + x,
                grid.y().doubleValue() * GRID_SIZE + y,
                grid.z().doubleValue() * GRID_SIZE + z);
    }

    public SpacePosition add(SpaceVec3 displacement) {
        return normalize(grid, x + displacement.x(), y + displacement.y(), z + displacement.z());
    }

    public static Optional<SpacePosition> average(List<SpacePosition> positions, double maxSpread) {
        if (positions.isEmpty()) return Optional.empty();
        SpacePosition base = positions.getFirst();
        double x = 0.0, y = 0.0, z = 0.0;
        for (SpacePosition position : positions) {
            Optional<SpaceVec3> delta = base.deltaWithin(position, maxSpread);
            if (delta.isEmpty()) return Optional.empty();
            x += delta.get().x();
            y += delta.get().y();
            z += delta.get().z();
        }
        double count = positions.size();
        return Optional.of(base.add(new SpaceVec3(x / count, y / count, z / count)));
    }

    public String globalX() {
        return globalAxis(grid.x(), x);
    }

    public String globalY() {
        return globalAxis(grid.y(), y);
    }

    public String globalZ() {
        return globalAxis(grid.z(), z);
    }

    private static String globalAxis(BigInteger grid, double local) {
        return new BigDecimal(grid).multiply(GRID_SIZE_DECIMAL)
                .add(BigDecimal.valueOf(local)).stripTrailingZeros().toPlainString();
    }

    Optional<SpaceVec3> deltaWithin(SpacePosition other, double maxRange) {
        if (!Double.isFinite(maxRange) || maxRange < 0.0) return Optional.empty();

        BigInteger maxGridDelta = BigDecimal.valueOf(maxRange)
                .divideToIntegralValue(GRID_SIZE_DECIMAL)
                .toBigInteger()
                .add(BigInteger.ONE);
        BigInteger dxGrid = other.grid.x().subtract(grid.x());
        BigInteger dyGrid = other.grid.y().subtract(grid.y());
        BigInteger dzGrid = other.grid.z().subtract(grid.z());
        if (dxGrid.abs().compareTo(maxGridDelta) > 0
                || dyGrid.abs().compareTo(maxGridDelta) > 0
                || dzGrid.abs().compareTo(maxGridDelta) > 0
                || dxGrid.bitLength() > 53
                || dyGrid.bitLength() > 53
                || dzGrid.bitLength() > 53) {
            return Optional.empty();
        }

        double dx = dxGrid.doubleValue() * GRID_SIZE + other.x - x;
        double dy = dyGrid.doubleValue() * GRID_SIZE + other.y - y;
        double dz = dzGrid.doubleValue() * GRID_SIZE + other.z - z;
        if (Math.hypot(Math.hypot(dx, dy), dz) > maxRange) return Optional.empty();
        return Optional.of(new SpaceVec3(dx, dy, dz));
    }

    public RenderOffset renderOffsetFrom(SpacePosition observer, double renderRange) {
        if (!Double.isFinite(renderRange) || renderRange <= 0.0) {
            throw new IllegalArgumentException("render range must be finite and positive");
        }

        BigDecimal dx = axisDelta(observer.grid.x(), grid.x(), observer.x, x);
        BigDecimal dy = axisDelta(observer.grid.y(), grid.y(), observer.y, y);
        BigDecimal dz = axisDelta(observer.grid.z(), grid.z(), observer.z, z);
        BigDecimal largest = dx.abs().max(dy.abs()).max(dz.abs());
        if (largest.signum() == 0) return new RenderOffset(SpaceVec3.ZERO, 1.0);

        double ux = dx.divide(largest, MathContext.DECIMAL64).doubleValue();
        double uy = dy.divide(largest, MathContext.DECIMAL64).doubleValue();
        double uz = dz.divide(largest, MathContext.DECIMAL64).doubleValue();
        double unitLength = Math.sqrt(ux * ux + uy * uy + uz * uz);
        double distance;
        double scale;
        double projectedDistance;
        if (largest.compareTo(BigDecimal.valueOf(renderRange * 20.0)) > 0) {
            projectedDistance = renderRange;
            BigDecimal exactDistance = largest.multiply(BigDecimal.valueOf(unitLength));
            scale = BigDecimal.valueOf(renderRange).divide(exactDistance, MathContext.DECIMAL64).doubleValue();
        } else {
            distance = largest.doubleValue() * unitLength;
            double ratio = distance / renderRange;
            projectedDistance = renderRange * Math.tanh(ratio);
            scale = distance == 0.0 ? 1.0 : projectedDistance / distance;
        }

        return new RenderOffset(new SpaceVec3(
                ux / unitLength * projectedDistance,
                uy / unitLength * projectedDistance,
                uz / unitLength * projectedDistance), scale);
    }

    private static BigDecimal axisDelta(BigInteger fromGrid, BigInteger toGrid,
                                        double fromLocal, double toLocal) {
        return new BigDecimal(toGrid.subtract(fromGrid)).multiply(GRID_SIZE_DECIMAL)
                .add(BigDecimal.valueOf(toLocal).subtract(BigDecimal.valueOf(fromLocal)));
    }

    private static AxisPosition normalizeAxis(BigInteger grid, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("space position must be finite");
        }
        if (value >= -GRID_HALF_SIZE && value < GRID_HALF_SIZE) return new AxisPosition(grid, value + 0.0);

        BigDecimal shifted = BigDecimal.valueOf(value).add(GRID_HALF_SIZE_DECIMAL);
        BigDecimal[] quotientAndRemainder = shifted.divideAndRemainder(GRID_SIZE_DECIMAL);
        BigInteger carry = quotientAndRemainder[0].toBigInteger();
        BigDecimal remainder = quotientAndRemainder[1];
        if (remainder.signum() < 0) {
            carry = carry.subtract(BigInteger.ONE);
            remainder = remainder.add(GRID_SIZE_DECIMAL);
        }

        double local = remainder.subtract(GRID_HALF_SIZE_DECIMAL).doubleValue();
        if (local >= GRID_HALF_SIZE) {
            carry = carry.add(BigInteger.ONE);
            local = -GRID_HALF_SIZE;
        }
        return new AxisPosition(grid.add(carry), local);
    }

    private static AxisPosition normalizeAxis(BigInteger grid, BigDecimal value) {
        BigDecimal shifted = value.add(GRID_HALF_SIZE_DECIMAL);
        BigDecimal[] quotientAndRemainder = shifted.divideAndRemainder(GRID_SIZE_DECIMAL);
        BigInteger carry = quotientAndRemainder[0].toBigIntegerExact();
        BigDecimal remainder = quotientAndRemainder[1];
        if (remainder.signum() < 0) {
            carry = carry.subtract(BigInteger.ONE);
            remainder = remainder.add(GRID_SIZE_DECIMAL);
        }

        double local = remainder.subtract(GRID_HALF_SIZE_DECIMAL).doubleValue();
        if (local >= GRID_HALF_SIZE) {
            carry = carry.add(BigInteger.ONE);
            local = -GRID_HALF_SIZE;
        }
        return new AxisPosition(grid.add(carry), local);
    }

    private static boolean isCanonical(double value) {
        return Double.isFinite(value) && value >= -GRID_HALF_SIZE && value < GRID_HALF_SIZE;
    }

    private record AxisPosition(BigInteger grid, double local) {}

    public record RenderOffset(SpaceVec3 vector, double scale) {}
}
