package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

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

    public static final BuilderCodec<EntityEffectLink> CODEC = BuilderCodec.builder(
                    EntityEffectLink.class, EntityEffectLink::new, BASE_CODEC)
            .append(new KeyedCodec<>("Id", Ids.CODEC, false), (e, v) -> e.ids = v, e -> e.ids).add()
            .build();

    private String[] ids;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    protected boolean check(EffectValidation v) {
        return Ids.check(v, "Id", "entity effect", "Server/Entity/Effects/", ids, v.refs()::hasEntityEffect);
    }

    /** {@return the {@code EntityEffect} asset id held at a rank, the last one repeating; rank counts from 1} */
    public String idAt(int rank) {
        return Ids.at(ids, rank);
    }

    /** {@return every id the effect can hold, in rank order, without repeats} */
    public Set<String> ids() {
        return new LinkedHashSet<>(Arrays.asList(ids));
    }
}
