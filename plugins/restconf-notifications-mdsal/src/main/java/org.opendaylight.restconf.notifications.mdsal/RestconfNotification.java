/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notifications.mdsal;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

public class RestconfNotification implements DOMNotification {
    private final ContainerNode content;

    RestconfNotification(final ContainerNode content) {
        this.content = requireNonNull(content);
    }

    @Override
    public @NonNull Absolute getType() {
        return Absolute.of(content.name().getNodeType());
    }

    @Override
    public @NonNull ContainerNode getBody() {
        return content;
    }
}
