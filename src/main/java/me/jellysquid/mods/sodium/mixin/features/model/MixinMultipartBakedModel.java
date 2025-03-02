package me.jellysquid.mods.sodium.mixin.features.model;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.MultipartBakedModel;
import net.minecraft.util.math.Direction;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Mixin(MultipartBakedModel.class)
public class MixinMultipartBakedModel {
    private static final Supplier<Map<BlockState, List<BakedModel>>> STATE_CACHE_CREATOR = Reference2ReferenceOpenHashMap::new;
    private Map<BlockState, List<BakedModel>> stateCacheFast = STATE_CACHE_CREATOR.get();

    @Shadow
    @Final
    private List<Pair<Predicate<BlockState>, BakedModel>> components;

    /**
     * @author JellySquid
     * @reason Avoid expensive allocations and replace bitfield indirection
     */
    @Overwrite
    public List<BakedQuad> getQuads(BlockState state, Direction face, Random random) {
        if (state == null) {
            return Collections.emptyList();
        }

        // DashLoader fix as its using Unsafe.allocateInstance()
        if(this.stateCacheFast == null)  {
            this.stateCacheFast = STATE_CACHE_CREATOR.get();
        }

        List<BakedModel> models;

        // stateCacheFast will only change if DashLoader is present.
        // Which in that case it would still be final in practice as it would only be initialized once.

        // FIXME: Synchronization-hack because getQuads must be thread-safe
        // Vanilla is actually affected by the exact same issue safety issue, but crashes seem rare in practice
        synchronized (this.stateCacheFast) {
            models = this.stateCacheFast.get(state);

            if (models == null) {
                models = new ArrayList<>(this.components.size());

                for (Pair<Predicate<BlockState>, BakedModel> pair : this.components) {
                    if ((pair.getLeft()).test(state)) {
                        models.add(pair.getRight());
                    }
                }

                this.stateCacheFast.put(state, models);
            }
        }

        List<BakedQuad> list = new ArrayList<>();

        long seed = random.nextLong();

        for (BakedModel model : models) {
            random.setSeed(seed);

            list.addAll(model.getQuads(state, face, random));
        }

        return list;
    }

}
