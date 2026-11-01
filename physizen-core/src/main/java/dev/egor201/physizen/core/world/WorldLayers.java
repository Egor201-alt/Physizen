package dev.egor201.physizen.core.world;

import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable;
import com.github.stephengold.joltjni.ObjVsBpFilter;
import com.github.stephengold.joltjni.ObjectLayerPairFilterTable;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.model.BodyDef;
import dev.egor201.physizen.core.model.MotionKind;

final class WorldLayers {

    static final int GROUPS = 16;
    static final int LAYER_STATIC = 0;
    private static final int BP_STATIC = 0;
    private static final int BP_MOVING = 1;

    final ObjectLayerPairFilterTable pairFilter;
    final BroadPhaseLayerInterfaceTable layerMap;
    final ObjVsBpFilter objVsBp;

    WorldLayers() {
        int objLayers = 1 + GROUPS;
        pairFilter = new ObjectLayerPairFilterTable(objLayers);
        for (int g = 0; g < GROUPS; g++) {
            pairFilter.enableCollision(LAYER_STATIC, g + 1);
            for (int h = g; h < GROUPS; h++) {
                pairFilter.enableCollision(g + 1, h + 1);
            }
        }
        layerMap = new BroadPhaseLayerInterfaceTable(objLayers, 2);
        layerMap.mapObjectToBroadPhaseLayer(LAYER_STATIC, BP_STATIC);
        for (int g = 0; g < GROUPS; g++) {
            layerMap.mapObjectToBroadPhaseLayer(g + 1, BP_MOVING);
        }
        objVsBp = new ObjVsBpFilter(objLayers, 2);
    }

    static void checkGroup(int group) {
        if (group < 0 || group >= GROUPS) {
            throw new PhysException("Group must be between 0 and " + (GROUPS - 1) + ".");
        }
    }

    static int layerFor(MotionKind type, boolean sensor, int group) {
        if (type == MotionKind.STATIC && !sensor) {
            return LAYER_STATIC;
        }
        return group + 1;
    }

    static int layerFor(BodyDef def) {
        return layerFor(def.type, def.sensor, def.group);
    }

    void setCollision(int a, int b, boolean enabled) {
        if (enabled) {
            pairFilter.enableCollision(a + 1, b + 1);
        }
        else {
            pairFilter.disableCollision(a + 1, b + 1);
        }
    }
}
