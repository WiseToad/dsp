package com.groupstp.dsp.domain.utils.collections;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

public class SpecificTypeSetProxy<S, E extends S> implements Set<E> {

    private ChangeableSetProxy<S> set;
    private int changeCount;
    private Class<E> elementClass;
    private Set<E> filteredSet;

    public SpecificTypeSetProxy(@NotNull ChangeableSetProxy<S> set, @NotNull Class<E> elementClass) {
        this.set = set;
        this.changeCount = set.getChangeCount() - 1;
        this.elementClass = elementClass;
    }

    @Override
    public int size() {
        updateFilteredSet();
        return filteredSet.size();
    }

    @Override
    public boolean isEmpty() {
        var isEmpty = set.stream()
            .noneMatch(element -> elementClass.isAssignableFrom(element.getClass()));
        if(isEmpty) {
            clearFilteredSet();
        }
        return isEmpty;
    }

    @Override
    public boolean contains(Object object) {
        return elementClass.isAssignableFrom(object.getClass())
            && set.contains(object);
    }

    @NotNull
    @Override
    public Iterator<E> iterator() {
        updateFilteredSet();
        return filteredSet.iterator();
    }

    @NotNull
    @Override
    public Object[] toArray() {
        updateFilteredSet();
        return filteredSet.toArray();
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] array) {
        updateFilteredSet();
        return filteredSet.toArray(array);
    }

    @Override
    public boolean add(E element) {
        return set.add(element);
    }

    @Override
    public boolean remove(Object object) {
        return elementClass.isAssignableFrom(object.getClass())
            && set.remove(object);
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> collection) {
        return collection.stream()
            .allMatch(e -> elementClass.isAssignableFrom(e.getClass()))
            && set.containsAll(collection);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> collection) {
        return set.addAll(collection);
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> collection) {
        throw new UnsupportedOperationException("retainAll");
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> collection) {
        return set.removeAll(filterByElementClass(collection));
    }

    @Override
    public void clear() {
        set.removeIf(e -> elementClass.isAssignableFrom(e.getClass()));
        clearFilteredSet();
    }

    private void clearFilteredSet() {
        if(set.getChangeCount() > changeCount) {
            filteredSet.clear();
            changeCount = set.getChangeCount();
        }
    }

    private void updateFilteredSet() {
        if(set.getChangeCount() > changeCount) {
            filteredSet = filterByElementClass(set);
            changeCount = set.getChangeCount();
        }
    }

    @SuppressWarnings("unchecked")
    private Set<E> filterByElementClass(@NotNull Collection<?> collection) {
        return (Set<E>)collection.stream()
            .filter(element -> elementClass.isAssignableFrom(element.getClass()))
            .collect(Collectors.toSet());
    }
}
