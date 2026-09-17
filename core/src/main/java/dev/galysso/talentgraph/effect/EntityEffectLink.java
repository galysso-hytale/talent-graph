package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * {@code "Type": "EntityEffect"} — keeps a Hytale {@code EntityEffect} on
 * the player for as long as the talent is held.
 *
 * <pre>{@code
 * { "Type": "EntityEffect", "Id": "MyPack_Warrior_Mark" }
 * }</pre>
 *
 * <p>The effect itself is written in the admin's pack under
 * {@code Server/Entity/Effects/}, with everything an EntityEffect can carry.
 * It also serves as a flag: an {@code EffectCondition} interaction can test
 * for it, which is how an existing attack changes with a talent.</p>
 */
public final class EntityEffectLink extends TalentEffect {

    public static final String TYPE = "EntityEffect";

    public static final BuilderCodec<EntityEffectLink> CODEC = BuilderCodec.builder(
                    EntityEffectLink.class, EntityEffectLink::new, BASE_CODEC)
            .append(new KeyedCodec<>("Id", Codec.STRING, false), (e, v) -> e.id = v, e -> e.id).add()
            .build();

    private String id;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    protected boolean check(EffectValidation v) {
        if (!v.present("Id", id)) {
            return false;
        }
        if (!v.refs().hasEntityEffect(id)) {
            v.warn("Unknown entity effect \"" + id + "\" (no Server/Entity/Effects/" + id
                    + ".json in any pack); the effect is ignored");
            return false;
        }
        return true;
    }

    /** {@return the {@code EntityEffect} asset id} */
    public String id() {
        return id;
    }
}
