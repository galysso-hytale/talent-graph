package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.exception.CodecException;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.ArraySchema;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.schema.config.StringSchema;
import org.bson.BsonArray;
import org.bson.BsonString;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@code "Type": "EntityEffect"} — keeps a Hytale {@code EntityEffect} on
 * the player for as long as the talent is held.
 *
 * <pre>{@code
 * { "Type": "EntityEffect", "Id": "MyPack_Warrior_Mark" }
 * { "Type": "EntityEffect", "Id": ["MyPack_Regen_1", "MyPack_Regen_2", "MyPack_Regen_3"] }
 * }</pre>
 *
 * <p>The effect itself is written in the admin's pack under
 * {@code Server/Entity/Effects/}, with everything an EntityEffect can carry.
 * It also serves as a flag: an {@code EffectCondition} interaction can test
 * for it, which is how an existing attack changes with a talent.</p>
 *
 * <p>{@code Id} takes one id, or one per rank like {@code "Amount"} does:
 * the last one repeats for higher ranks, and only the id of the current
 * rank is held, so a stronger regeneration replaces the weaker one instead
 * of stacking on it.</p>
 */
public final class EntityEffectLink extends TalentEffect {

    public static final String TYPE = "EntityEffect";

    /** Accepts a string or an array of strings; anything else is a file error. */
    static final Codec<String[]> IDS_CODEC = new Codec<>() {
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

    public static final BuilderCodec<EntityEffectLink> CODEC = BuilderCodec.builder(
                    EntityEffectLink.class, EntityEffectLink::new, BASE_CODEC)
            .append(new KeyedCodec<>("Id", IDS_CODEC, false), (e, v) -> e.ids = v, e -> e.ids).add()
            .build();

    private String[] ids;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    protected boolean check(EffectValidation v) {
        if (ids == null || ids.length == 0) {
            v.warn("\"Id\" is missing; the effect is ignored");
            return false;
        }
        if (ids.length > v.maxRank()) {
            v.warn("\"Id\" has " + ids.length + " values but \"MaxRank\" is " + v.maxRank()
                    + "; the extra values are never used");
        }
        boolean usable = true;
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            if (id == null || id.isBlank()) {
                v.warn("\"Id\" has an empty entry at index " + i + "; the effect is ignored");
                usable = false;
            } else if (!v.refs().hasEntityEffect(id)) {
                v.warn("Unknown entity effect \"" + id + "\" (no Server/Entity/Effects/" + id
                        + ".json in any pack); the effect is ignored");
                usable = false;
            }
        }
        return usable;
    }

    /** {@return the {@code EntityEffect} asset id held at a rank, the last one repeating; rank counts from 1} */
    public String idAt(int rank) {
        return ids[Math.max(0, Math.min(rank, ids.length) - 1)];
    }

    /** {@return every id the effect can hold, in rank order, without repeats} */
    public Set<String> ids() {
        return new LinkedHashSet<>(Arrays.asList(ids));
    }
}
