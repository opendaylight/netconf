/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.handlers;

import com.google.common.base.Preconditions;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Implementation of {@link SchemaContextHandler}
 *
 */
public class SchemaContextHandler implements SchemaContextListenerHandler {

    private SchemaContext context;

    @Override
    public void onGlobalContextUpdated(final SchemaContext context) {
        Preconditions.checkNotNull(context);
        this.context = null;
        this.context = context;
    }

    @Override
    public SchemaContext get() {
        return this.context;
    }
}
