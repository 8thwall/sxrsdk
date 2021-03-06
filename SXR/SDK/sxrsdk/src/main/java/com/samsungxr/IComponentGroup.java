package com.samsungxr;

/**
 * Interface for component groups
 * @param <T> class of component the group contains
 */
public interface IComponentGroup<T extends SXRComponent> extends Iterable<T>
{
    public void addChildComponent(T child);
    public void removeChildComponent(T child);
    public int getSize();
    public T getChildAt(int index);
}
