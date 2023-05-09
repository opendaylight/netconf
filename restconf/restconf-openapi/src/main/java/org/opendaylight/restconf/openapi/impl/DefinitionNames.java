/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class DefinitionNames {

    private final HashMap<SchemaNode, String> discriminators;
    private final Set<String> names;

    public DefinitionNames() {
        names = new HashSet<>();
        discriminators = new HashMap<>();
    }

    private int pickDiscriminator(final List<String> clearNames, final int discriminator) {
        for (final String clearName : clearNames) {
            final String newName = clearName + discriminator;
            if (names.contains(newName)) {
                return pickDiscriminator(clearNames, discriminator + 1);
            }
        }
        return discriminator;
    }

    String pickDiscriminator(final SchemaNode node, final List<String> clearNames) {
        String discriminator = "";
        for (final String clearName: clearNames) {
            if (names.contains(clearName)) {
                discriminator = String.valueOf(pickDiscriminator(clearNames, 1));
            }
        }
        discriminators.put(node, discriminator);
        for (final String clearName : clearNames) {
            names.add(clearName + discriminator);
        }
        return discriminator;
    }

    void addUnlinkedName(final String name) {
        if (!names.contains(name)) {
            names.add(name);
        } else {
            throw new IllegalArgumentException(String.format("Definition name:%s already in use", name));
        }
    }

    boolean isListedNode(final SchemaNode node) {
        return discriminators.containsKey(node);
    }

    public String getDiscriminator(final SchemaNode node) {
        return discriminators.get(node);
    }

}
