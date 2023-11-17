/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Workaround for problems with invoking interface-private static methods in inner record classes. We deal with:
 * <ul>
 *   <li>Eclipse tries to import static such references, hence we need to prefix each use, and when we do</li>
 *   <li>SpotBugs ends up reporting UPM_UNCALLED_PRIVATE_METHOD</li>
 * </ul>
 */
final class OperationsGetResultHelper {
    private OperationsGetResultHelper() {
        // Hidden on purpose
    }

    static StringBuilder appendJSON(final StringBuilder sb, final String prefix, final QName rpc) {
        return sb.append('"').append(prefix).append(':').append(rpc.getLocalName()).append("\" : [null]");
    }

    static StringBuilder appendXML(final StringBuilder sb, final QName rpc) {
        return sb.append('<').append(rpc.getLocalName()).append(' ').append("xmlns=\"").append(rpc.getNamespace())
            .append("\"/>");
    }

    static String jsonPrefix(final EffectiveModelContext modelContext, final QNameModule namespace) {
        return modelContext.findModuleStatement(namespace).orElseThrow().argument().getLocalName();
    }
}
