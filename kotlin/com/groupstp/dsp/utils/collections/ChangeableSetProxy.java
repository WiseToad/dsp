package com.groupstp.dsp.domain.utils.collections;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Supplier;

public class ChangeableSetProxy<E> implements Set<E> {

    private Supplier<Set<E>> setSupplier;
    private int changeCount = 0;

    public ChangeableSetProxy(Supplier<Set<E>> setSupplier) {
        this.setSupplier = setSupplier;
    }

    public int getChangeCount() {
        return changeCount;
    }

    @Override
    public int size() {
        return setSupplier.get().size();
    }

    @Override
    public boolean isEmpty() {
        return setSupplier.get().isEmpty();
    }

    @Override
    public boolean contains(Object object) {
        return setSupplier.get().contains(object);
    }

    @NotNull
    @Override
    public Iterator<E> iterator() {
        return setSupplier.get().iterator();
    }

    @NotNull
    @Override
    public Object[] toArray() {
        return setSupplier.get().toArray();
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] array) {
        return setSupplier.get().toArray(array);
    }

    @Override
    public boolean add(E element) {
        var isChanged = setSupplier.get().add(element);
        if(isChanged) {
            changeCount++;
        }
        return isChanged;
    }

    @Override
    public boolean remove(Object object) {
        var isChanged = setSupplier.get().remove(object);
        if(isChanged) {
            changeCount++;
        }
        return isChanged;
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> collection) {
        return setSupplier.get().containsAll(collection);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> collection) {
        var isChanged = setSupplier.get().addAll(collection);
        if(isChanged) {
            changeCount++;
        }
        return isChanged;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> collection) {
        var isChanged = setSupplier.get().retainAll(collection);
        if(isChanged) {
            changeCount++;
        }
        return isChanged;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> collection) {
        var isChanged = setSupplier.get().removeAll(collection);
        if(isChanged) {
            changeCount++;
        }
        return isChanged;
    }

    @Override
    public void clear() {
        setSupplier.get().clear();
        changeCount++;
    }

    @Override
    public boolean equals(Object object) {
        return setSupplier.get().equals(object);
    }

    @Override
    public int hashCode() {
        return setSupplier.get().hashCode();
    }
}
