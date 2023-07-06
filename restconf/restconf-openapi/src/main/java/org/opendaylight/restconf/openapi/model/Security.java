/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public record Security(List<Map<String, List<String>>> securities) implements List<Map<String, List<String>>> {

    @Override
    public int size() {
        return securities.size();
    }

    @Override
    public boolean isEmpty() {
        return securities.isEmpty();
    }

    @Override
    public boolean contains(Object element) {
        return securities.contains(element);
    }

    @Override
    public Iterator<Map<String, List<String>>> iterator() {
        return securities.iterator();
    }

    @Override
    public Object[] toArray() {
        return securities.toArray();
    }

    @Override
    public <T> T[] toArray(T[] array) {
        return securities.toArray(array);
    }

    @Override
    public boolean add(Map<String, List<String>> element) {
        return securities.add(element);
    }

    @Override
    public void add(int index, Map<String, List<String>> element) {
        securities.add(index, element);
    }

    @Override
    public boolean remove(Object element) {
        return securities.remove(element);
    }

    @Override
    public Map<String, List<String>> remove(int index) {
        return securities.remove(index);
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
        return securities.containsAll(collection);
    }

    @Override
    public boolean addAll(Collection<? extends Map<String, List<String>>> collection) {
        return securities.addAll(collection);
    }

    @Override
    public boolean addAll(int index, Collection<? extends Map<String, List<String>>> collection) {
        return securities.addAll(index, collection);
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
        return securities.removeAll(collection);
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
        return securities.retainAll(collection);
    }

    @Override
    public void clear() {
        securities.clear();
    }

    @Override
    public Map<String, List<String>> get(int index) {
        return securities.get(index);
    }

    @Override
    public Map<String, List<String>> set(int index, Map<String, List<String>> element) {
        return securities.set(index, element);
    }

    @Override
    public int indexOf(Object element) {
        return securities.indexOf(element);
    }

    @Override
    public int lastIndexOf(Object element) {
        return securities.lastIndexOf(element);
    }

    @Override
    public ListIterator<Map<String, List<String>>> listIterator() {
        return securities.listIterator();
    }

    @Override
    public ListIterator<Map<String, List<String>>> listIterator(int index) {
        return securities.listIterator(index);
    }

    @Override
    public List<Map<String, List<String>>> subList(int fromIndex, int toIndex) {
        return securities.subList(fromIndex, toIndex);
    }
}
