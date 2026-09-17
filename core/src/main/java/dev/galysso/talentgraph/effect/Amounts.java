package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.exception.CodecException;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.ArraySchema;
import com.hypixel.hytale.codec.schema.config.NumberSchema;
import com.hypixel.hytale.codec.schema.config.Schema;
import org.bson.BsonArray;
import org.bson.BsonDouble;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * A numeric effect parameter, one value per rank: {@code 10} or
 * {@code [10, 20, 35]}. The last value repeats for higher ranks, as
 * {@code "Cost"} does. A value is the absolute amount at that rank, never
 * an increment: at rank 3 the example above gives 35.
 */
public final class Amounts {

    /** Accepts a number or an array of numbers; anything else is a file error. */
    public static final Codec<Amounts> CODEC = new Codec<>() {
        @Override
        public Amounts decode(@Nonnull BsonValue value, ExtraInfo extraInfo) {
            if (value.isNumber()) {
                return new Amounts(new double[] {value.asNumber().doubleValue()});
            }
            if (value.isArray()) {
                BsonArray array = value.asArray();
                double[] values = new double[array.size()];
                for (int i = 0; i < values.length; i++) {
                    BsonValue item = array.get(i);
                    if (!item.isNumber()) {
                        throw new CodecException("Expected a number at index " + i + ", got " + item.getBsonType());
                    }
                    values[i] = item.asNumber().doubleValue();
                }
                return new Amounts(values);
            }
            throw new CodecException("Expected a number or an array of numbers, got " + value.getBsonType());
        }

        @Override
        public BsonValue encode(Amounts amounts, ExtraInfo extraInfo) {
            BsonArray array = new BsonArray();
            for (double value : amounts.perRank) {
                array.add(new BsonDouble(value));
            }
            return array;
        }

        @Nonnull
        @Override
        public Schema toSchema(@Nonnull SchemaContext context) {
            return new ArraySchema(new NumberSchema());
        }
    };

    private final double[] perRank;

    public Amounts(double[] perRank) {
        this.perRank = perRank.clone();
    }

    /** {@return the value at a rank, the last one repeating; rank counts from 1} */
    public double at(int rank) {
        return perRank[Math.max(0, Math.min(rank, perRank.length) - 1)];
    }

    /** {@return how many ranks have their own value} */
    public int size() {
        return perRank.length;
    }

    public boolean isEmpty() {
        return perRank.length == 0;
    }

    /** {@return the values as written, one per rank} */
    public double[] values() {
        return perRank.clone();
    }

    @Override
    public String toString() {
        return Arrays.toString(perRank);
    }
}
