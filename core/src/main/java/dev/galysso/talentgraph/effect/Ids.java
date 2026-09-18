package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.exception.CodecException;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.ArraySchema;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.schema.config.StringSchema;
import org.bson.BsonArray;
import org.bson.BsonString;
import org.bson.BsonValue;

import javax.annotation.Nonnull;

/**
 * An asset id given once, or once per rank, the way {@code "Amount"} takes
 * numbers: {@code "Id": "X"} and {@code "Id": ["X_1", "X_2"]} both read as
 * an array, and the last id repeats for higher ranks. Shared by
 * {@link EntityEffectLink} and {@link AbilityEffect}.
 */
final class Ids {

    /** Accepts a string or an array of strings; anything else is a file error. */
    static final Codec<String[]> CODEC = new Codec<>() {
        @Override
        public String[] decode(@Nonnull BsonValue value, ExtraInfo extraInfo) {
            if (value.isString()) {
                return new String[] {value.asString().getValue()};
            }
            if (value.isArray()) {
                BsonArray array = value.asArray();
                String[] ids = new String[array.size()];
                for (int i = 0; i < ids.length; i++) {
                    BsonValue item = array.get(i);
                    if (!item.isString()) {
                        throw new CodecException("Expected a string at index " + i + ", got " + item.getBsonType());
                    }
                    ids[i] = item.asString().getValue();
                }
                return ids;
            }
            throw new CodecException("Expected a string or an array of strings, got " + value.getBsonType());
        }

        @Override
        public BsonValue encode(String[] ids, ExtraInfo extraInfo) {
            BsonArray array = new BsonArray();
            for (String id : ids) {
                array.add(new BsonString(id));
            }
            return array;
        }

        @Nonnull
        @Override
        public Schema toSchema(@Nonnull SchemaContext context) {
            return new ArraySchema(new StringSchema());
        }
    };

    private Ids() {
    }

    /** {@return the id held at a rank, the last one repeating; rank counts from 1} */
    static String at(String[] ids, int rank) {
        return ids[Math.max(0, Math.min(rank, ids.length) - 1)];
    }

    /**
     * Checks a per-rank id list: present, no blank entry, each id known.
     *
     * @param field  the field name, for messages
     * @param what   what the ids name, for messages ("entity effect")
     * @param folder where such assets live, for messages ("Server/Entity/Effects/")
     * @param known  whether an id is loaded
     * @return false when the list is unusable and the effect must be dropped
     */
    static boolean check(EffectValidation v, String field, String what, String folder, String[] ids,
                         java.util.function.Predicate<String> known) {
        if (ids == null || ids.length == 0) {
            v.warn("\"" + field + "\" is missing; the effect is ignored");
            return false;
        }
        if (ids.length > v.maxRank()) {
            v.warn("\"" + field + "\" has " + ids.length + " values but \"MaxRank\" is " + v.maxRank()
                    + "; the extra values are never used");
        }
        boolean usable = true;
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            if (id == null || id.isBlank()) {
                v.warn("\"" + field + "\" has an empty entry at index " + i + "; the effect is ignored");
                usable = false;
            } else if (!known.test(id)) {
                v.warn("Unknown " + what + " \"" + id + "\" (no " + folder + id
                        + ".json in any pack); the effect is ignored");
                usable = false;
            }
        }
        return usable;
    }
}
