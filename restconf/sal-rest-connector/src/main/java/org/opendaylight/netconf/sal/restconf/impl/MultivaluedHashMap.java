/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.ws.rs.core.MultivaluedMap;

/**
 * A hash table based implementation of {@link MultivaluedMap} interface.
 *
 * @author Thomas Pantelis
 */
class MultivaluedHashMap<K, V> implements MultivaluedMap<K, V> {
    private final Map<K, List<V>> store = new HashMap<>();

    @Override
    public final void putSingle(K key, V value) {
        List<V> values = getValues(key);

        values.clear();
        if (value != null) {
            values.add(value);
        }
    }

    @Override
    public void add(K key, V value) {
        List<V> values = getValues(key);

        if (value != null) {
            values.add(value);
        }
    }

    @Override
    public void addAll(K key, V... newValues) {
        Objects.requireNonNull(newValues, "Supplied array of values must not be null.");

        if (newValues.length == 0) {
            return;
        }

        List<V> values = getValues(key);
        for (V value : newValues) {
            if (value != null) {
                values.add(value);
            }
        }
    }

    @Override
    public void addAll(K key, List<V> valueList) {
        Objects.requireNonNull(valueList, "Supplied list of values must not be null.");

        if (valueList.isEmpty()) {
            return;
        }

        List<V> values = getValues(key);
        for (V value : valueList) {
            if (value != null) {
                values.add(value);
            }
        }
    }

    @Override
    public void addFirst(K key, V value) {
        List<V> values = getValues(key);

        if (value != null) {
            values.add(0, value);
        }
    }

    @Override
    public V getFirst(K key) {
        List<V> values = store.get(key);
        if (values != null && values.size() > 0) {
            return values.get(0);
        } else {
            return null;
        }
    }

    @Override
    public boolean equalsIgnoreValueOrder(MultivaluedMap<K, V> omap) {
        if (this == omap) {
            return true;
        }
        if (!keySet().equals(omap.keySet())) {
            return false;
        }
        for (Entry<K, List<V>> e : entrySet()) {
            List<V> olist = omap.get(e.getKey());
            if (e.getValue().size() != olist.size()) {
                return false;
            }
            for (V v : e.getValue()) {
                if (!olist.contains(v)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public Collection<List<V>> values() {
        return store.values();
    }

    @Override
    public int size() {
        return store.size();
    }

    @Override
    public List<V> remove(Object key) {
        return store.remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends List<V>> map) {
        store.putAll(map);
    }

    @Override
    public List<V> put(K key, List<V> value) {
        return store.put(key, value);
    }

    @Override
    public Set<K> keySet() {
        return store.keySet();
    }

    @Override
    public boolean isEmpty() {
        return store.isEmpty();
    }

    @Override
    public List<V> get(Object key) {
        return store.get(key);
    }

    @Override
    public Set<Entry<K, List<V>>> entrySet() {
        return store.entrySet();
    }

    @Override
    public boolean containsValue(Object value) {
        return store.containsValue(value);
    }

    @Override
    public boolean containsKey(Object key) {
        return store.containsKey(key);
    }

    @Override
    public void clear() {
        store.clear();
    }

    private List<V> getValues(K key) {
        List<V> list = store.get(key);
        if (list == null) {
            list = new LinkedList<>();
            store.put(key, list);
        }

        return list;
    }
}
