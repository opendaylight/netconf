/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.context;

import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;

import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveStatementInference;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

public class InstanceIdentifierContext {
    private final YangInstanceIdentifier instanceIdentifier;
    private final EffectiveStatementInference inference;
    private final DOMMountPoint mountPoint;

    public InstanceIdentifierContext(final EffectiveStatementInference inference,
            final YangInstanceIdentifier instanceIdentifier, final DOMMountPoint mountPoint) {
        this.inference = requireNonNull(inference);
        this.instanceIdentifier = instanceIdentifier;
        this.mountPoint = mountPoint;
    }

    public YangInstanceIdentifier getInstanceIdentifier() {
        return instanceIdentifier;
    }

    public SchemaNode getSchemaNode() {
        final var stack = SchemaInferenceStack.ofInference(inference);
        if (stack.isEmpty()) {
            return inference.getEffectiveModelContext();
        }
        final var current = stack.currentStatement();
        verify(current instanceof SchemaNode, "Unexpected statement %s", current);
        return (SchemaNode) current;
    }

    public DOMMountPoint getMountPoint() {
        return mountPoint;
    }

    public EffectiveModelContext getSchemaContext() {
        return inference.getEffectiveModelContext();
    }
}
