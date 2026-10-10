package com.google.android.flipendo.collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public abstract class ImmutableCollectors {
    public static <T> Collector<T, ?, ImmutableSet<T>> toImmutableSet() {
        return Collectors.collectingAndThen(
                Collectors.toCollection(HashSet::new), ImmutableSet::copyOf);
    }

    public static <T> Collector<T, ?, ImmutableList<T>> toImmutableList() {
        return Collectors.collectingAndThen(
                Collectors.toCollection(ArrayList::new), ImmutableList::copyOf);
    }
}
