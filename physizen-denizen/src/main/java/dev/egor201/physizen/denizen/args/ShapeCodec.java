package dev.egor201.physizen.denizen.args;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.objects.core.QuaternionTag;
import dev.egor201.physizen.core.model.ShapeSpec;

public final class ShapeCodec {

    private ShapeCodec() {
    }

    public static MapTag toMap(ShapeSpec spec) {
        MapTag map = new MapTag();
        map.putObject("type", new ElementTag(spec.kind.name().toLowerCase()));
        switch (spec.kind) {
            case BOX:
                map.putObject("size", new ElementTag(spec.sizeX + "," + spec.sizeY + "," + spec.sizeZ));
                break;
            case SPHERE:
                map.putObject("radius", new ElementTag(spec.radius));
                break;
            case CAPSULE:
                map.putObject("radius", new ElementTag(spec.radius));
                map.putObject("height", new ElementTag(spec.height));
                break;
            case CYLINDER:
                map.putObject("radius", new ElementTag(spec.radius));
                map.putObject("height", new ElementTag(spec.height));
                break;
            case CONE:
                map.putObject("radius", new ElementTag(spec.radius));
                map.putObject("height", new ElementTag(spec.height));
                map.putObject("segments", new ElementTag(spec.segments));
                break;
            case HULL:
            case MESH:
                if (spec.sourceFile != null) {
                    map.putObject("file", new ElementTag(spec.sourceFile));
                    map.putObject("scale", new ElementTag(spec.sourceScale));
                }
                else if (spec.vertices != null) {
                    ListTag points = new ListTag();
                    for (int i = 0; i + 2 < spec.vertices.length; i += 3) {
                        points.add(spec.vertices[i] + "," + spec.vertices[i + 1] + "," + spec.vertices[i + 2]);
                    }
                    map.putObject("points", points);
                }
                if (spec.indices != null && spec.kind == ShapeSpec.Kind.MESH) {
                    ListTag triangles = new ListTag();
                    for (int idx : spec.indices) {
                        triangles.add(String.valueOf(idx));
                    }
                    map.putObject("triangles", triangles);
                }
                break;
            case COMPOUND:
                ListTag list = new ListTag();
                for (ShapeSpec.Child child : spec.children) {
                    MapTag childMap = toMap(child.shape);
                    childMap.putObject("offset", new ElementTag(child.offset[0] + "," + child.offset[1] + "," + child.offset[2]));
                    childMap.putObject("rotation", new QuaternionTag(child.rotation[0], child.rotation[1], child.rotation[2], child.rotation[3]));
                    list.addObject(childMap);
                }
                map.putObject("children", list);
                break;
        }
        return map;
    }
}
