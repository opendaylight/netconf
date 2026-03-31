/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import java.util.Collection;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;

final class RestDocgenUtil {
    private RestDocgenUtil() {
        // Hidden on purpose
    }

    static Collection<? extends DataSchemaNode> widthList(final DataNodeContainer node, final int width) {
        if (width > 0) {
            // limit children to width
            return node.getChildNodes().stream().limit(width).toList();
        }
        // width not applied - processing all children
        return node.getChildNodes();
    }
}
