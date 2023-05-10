/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.doc.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;

public final class JsonUtil {
    private JsonUtil() {

    }

    public static void addFields(final ObjectNode node, final Iterator<Map.Entry<String, JsonNode>> fields) {
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> field = fields.next();
            node.set(field.getKey(), field.getValue());
        }
    }
}
