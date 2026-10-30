package dev.egor201.physizen.core;

import java.util.HashSet;
import java.util.Set;

public final class QueryFilter {

    public final Set<String> ignore = new HashSet<>();
    public final Set<Integer> groups = new HashSet<>();
    public boolean sensors = false;
    public boolean terrain = true;
    public int limit = 64;
}