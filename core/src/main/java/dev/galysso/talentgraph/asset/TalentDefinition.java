package dev.galysso.talentgraph.asset;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;

/**
 * One talent as written in a graph JSON file. Plain data: validation and
 * conversion to the API happen in {@link TalentGraphAssets}.
 *
 * <pre>{@code
 * { "Id": "cleave", "Name": "Cleave", "MaxRank": 1, "Cost": [2],
 *   "Requires": ["toughness"], "Icon": "UI/Custom/Pages/TalentGraph/Icons/Cleave.png",
 *   "X": 320, "Y": 180 }
 * }</pre>
 */
public final class TalentDefinition {

    public static final BuilderCodec<TalentDefinition> CODEC = BuilderCodec.builder(
                    TalentDefinition.class, TalentDefinition::new)
            .append(new KeyedCodec<>("Id", Codec.STRING), (d, v) -> d.id = v, d -> d.id).add()
            .append(new KeyedCodec<>("Name", Codec.STRING), (d, v) -> d.name = v, d -> d.name).add()
            .append(new KeyedCodec<>("MaxRank", Codec.INTEGER, false),
                    (d, v) -> d.maxRank = v, d -> d.maxRank).add()
            // Cost per rank; the last value repeats for higher ranks.
            .append(new KeyedCodec<>("Cost", Codec.INT_ARRAY, false),
                    (d, v) -> d.cost = v, d -> d.cost).add()
            // Local ids ("toughness") or fully qualified ("talentgraph:warrior/toughness").
            .append(new KeyedCodec<>("Requires", Codec.STRING_ARRAY, false),
                    (d, v) -> d.requires = v, d -> d.requires).add()
            .append(new KeyedCodec<>("Icon", Codec.STRING, false),
                    (d, v) -> d.icon = v, d -> d.icon).add()
            // Canvas position in pixels. Both must be present for the graph to
            // use hand-placed nodes; otherwise the whole graph is auto-laid out.
            .append(new KeyedCodec<>("X", Codec.INTEGER, false), (d, v) -> d.x = v, d -> d.x).add()
            .append(new KeyedCodec<>("Y", Codec.INTEGER, false), (d, v) -> d.y = v, d -> d.y).add()
            .build();

    private String id;
    private String name;
    private int maxRank = 1;
    private int[] cost = {1};
    private String[] requires = new String[0];
    @Nullable
    private String icon;
    @Nullable
    private Integer x;
    @Nullable
    private Integer y;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getMaxRank() {
        return maxRank;
    }

    public int[] getCost() {
        return cost;
    }

    public String[] getRequires() {
        return requires;
    }

    @Nullable
    public String getIcon() {
        return icon;
    }

    @Nullable
    public Integer getX() {
        return x;
    }

    @Nullable
    public Integer getY() {
        return y;
    }

    boolean hasPosition() {
        return x != null && y != null;
    }
}
