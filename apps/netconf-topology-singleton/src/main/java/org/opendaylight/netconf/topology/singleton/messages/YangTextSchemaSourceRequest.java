/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages;

import java.io.Serial;
import java.io.Serializable;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;

/**
 * Slave sends message to master when tries to resolve schema with particular sourceIdentifier (proxy call).
 * Master responds with resolved schema source.
 */
public class YangTextSchemaSourceRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final SourceIdentifier sourceIdentifier;

    public YangTextSchemaSourceRequest(final SourceIdentifier sourceIdentifier) {
        this.sourceIdentifier = sourceIdentifier;
    }

    public SourceIdentifier getSourceIdentifier() {
        return sourceIdentifier;
    }

    @Override
    public String toString() {
        return "YangTextSchemaSourceRequest [sourceIdentifier=" + sourceIdentifier + "]";
    }
}
