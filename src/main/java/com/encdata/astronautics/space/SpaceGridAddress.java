package com.encdata.astronautics.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.math.BigInteger;
import java.util.Objects;

public record SpaceGridAddress(BigInteger x, BigInteger y, BigInteger z) {
    private static final int MAX_INDEX_DIGITS = 4096;
    private static final Codec<BigInteger> INDEX_CODEC = Codec.STRING.comapFlatMap(
            SpaceGridAddress::parseIndex,
            BigInteger::toString);

    public static final Codec<SpaceGridAddress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            INDEX_CODEC.fieldOf("x").forGetter(SpaceGridAddress::x),
            INDEX_CODEC.fieldOf("y").forGetter(SpaceGridAddress::y),
            INDEX_CODEC.fieldOf("z").forGetter(SpaceGridAddress::z)
    ).apply(instance, SpaceGridAddress::new));

    public static final SpaceGridAddress ORIGIN = new SpaceGridAddress(
            BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO);

    public SpaceGridAddress {
        Objects.requireNonNull(x);
        Objects.requireNonNull(y);
        Objects.requireNonNull(z);
    }

    public SpaceGridAddress offset(BigInteger x, BigInteger y, BigInteger z) {
        return new SpaceGridAddress(this.x.add(x), this.y.add(y), this.z.add(z));
    }

    private static DataResult<BigInteger> parseIndex(String value) {
        if (value.isEmpty() || value.length() > MAX_INDEX_DIGITS) {
            return DataResult.error(() -> "invalid space grid index length");
        }

        try {
            BigInteger index = new BigInteger(value);
            if (!index.toString().equals(value)) {
                return DataResult.error(() -> "non-canonical space grid index");
            }
            return DataResult.success(index);
        } catch (NumberFormatException exception) {
            return DataResult.error(() -> "invalid space grid index");
        }
    }
}
