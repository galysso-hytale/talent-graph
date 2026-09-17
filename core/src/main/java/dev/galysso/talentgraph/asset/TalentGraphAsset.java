package dev.galysso.talentgraph.asset;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import dev.galysso.talentgraph.effect.EquipmentBaseline;

import javax.annotation.Nullable;

/**
 * A talent graph as read from {@code Server/TalentGraph/Graphs/<id>.json}.
 * The file name is the asset id and becomes the graph's path; the graph's
 * namespace defaults to {@value #DEFAULT_NAMESPACE}.
 */
public final class TalentGraphAsset
        implements JsonAssetWithMap<String, DefaultAssetMap<String, TalentGraphAsset>> {

    public static final String DEFAULT_NAMESPACE = "talentgraph";

    public static final AssetBuilderCodec<String, TalentGraphAsset> CODEC = AssetBuilderCodec.builder(
                    TalentGraphAsset.class, TalentGraphAsset::new, Codec.STRING,
                    (a, id) -> a.id = id, a -> a.id,
                    (a, data) -> a.data = data, a -> a.data)
            .append(new KeyedCodec<>("Name", Codec.STRING), (a, v) -> a.name = v, a -> a.name).add()
            .append(new KeyedCodec<>("Namespace", Codec.STRING, false),
                    (a, v) -> a.namespace = v, a -> a.namespace).add()
            // Image drawn under the graph, which then sizes the canvas: a path
            // relative to the pack (see GraphLoader.resolveAsset).
            .append(new KeyedCodec<>("Background", Codec.STRING, false),
                    (a, v) -> a.background = v, a -> a.background).add()
            // What players cannot use until a talent allows it; see EquipmentBaseline.
            .append(new KeyedCodec<>("Equipment", EquipmentBaseline.CODEC, false),
                    (a, v) -> a.equipment = v, a -> a.equipment).add()
            .append(new KeyedCodec<>("Talents",
                            new ArrayCodec<>(TalentDefinition.CODEC, TalentDefinition[]::new)),
                    (a, v) -> a.talents = v, a -> a.talents).add()
            .build();

    private String id;
    private AssetExtraInfo.Data data;
    private String name;
    private String namespace = DEFAULT_NAMESPACE;
    @Nullable
    private String background;
    @Nullable
    private EquipmentBaseline equipment;
    private TalentDefinition[] talents = new TalentDefinition[0];

    @Override
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }

    @Nullable
    public String getBackground() {
        return background;
    }

    /** {@return the {@code "Equipment"} section, or null when absent} */
    @Nullable
    public EquipmentBaseline getEquipment() {
        return equipment;
    }

    public TalentDefinition[] getTalents() {
        return talents;
    }
}
