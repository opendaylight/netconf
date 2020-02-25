/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class DefinitionNames {
    private HashMap<SchemaNode, String> discriminators;
    private Set<String> names;

    public DefinitionNames() {
        names = new HashSet<>();
        discriminators = new HashMap<>();
    }

    private int pickDiscriminator(List<String> clearNames, int discriminator) {
        for (String clearName : clearNames) {
            String newName = clearName + discriminator;
            if (names.contains(newName)) {
                return pickDiscriminator(clearNames, discriminator + 1);
            }
        }
        return discriminator;
    }

    public String pickDiscriminator(SchemaNode node, List<String> clearNames) {
        String discriminator = "";
        for (String clearName: clearNames) {
            if (names.contains(clearName)) {
                discriminator = String.valueOf(pickDiscriminator(clearNames, 1));
            }
        }
        discriminators.put(node, discriminator);
        for (String clearName : clearNames) {
            names.add(clearName + discriminator);
        }
        return discriminator;
    }

    public void addUnlinkedName(String name) {
        if (!names.contains(name)) {
            names.add(name);
        } else {
            throw new IllegalArgumentException(String.format("Definition name:%s already in use", name));
        }
    }

    public boolean isListedNode(SchemaNode node) {
        return discriminators.containsKey(node);
    }

    public String getDiscriminator(SchemaNode node) {
        return discriminators.get(node);
    }
}
