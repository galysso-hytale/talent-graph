package dev.galysso.talentgraph.asset;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

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
            .append(new KeyedCodec<>("Talents",
                            new ArrayCodec<>(TalentDefinition.CODEC, TalentDefinition[]::new)),
                    (a, v) -> a.talents = v, a -> a.talents).add()
            .build();

    private String id;
    private AssetExtraInfo.Data data;
    private String name;
    private String namespace = DEFAULT_NAMESPACE;
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

    public TalentDefinition[] getTalents() {
        return talents;
    }
}
