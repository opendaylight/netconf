/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.subtree;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.yangtools.yang.common.XMLNamespace;

/**
 * A builder for {@link SubtreeFilter}.
 */
public final class ContainmentNodeBuilder extends SiblingSetBuilder<ContainmentNode> {
    private XMLNamespace namespace;
    private String name;

    ContainmentNodeBuilder(final @NonNull DatabindContext databind) {
        super(databind);
    }

    @Nullable XMLNamespace namespace() {
        return namespace;
    }

    @Nullable String name() {
        return name;
    }

    @Override
    public ContainmentNode build() {
        return new ContainmentNode(this);
    }
}