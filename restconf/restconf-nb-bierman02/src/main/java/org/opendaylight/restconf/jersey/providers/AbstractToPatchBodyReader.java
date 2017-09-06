/*
 * Copyright (c) 2017 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.jersey.providers;

import org.opendaylight.netconf.sal.restconf.impl.PatchContext;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;

/**
 * Common superclass for readers producing {@link PatchContext}.
 *
 * @author Robert Varga
 */
abstract class AbstractToPatchBodyReader extends AbstractIdentifierAwareJaxRsProvider<PatchContext> {

    @Override
    protected final PatchContext emptyBody(final InstanceIdentifierContext<?> path) {
        return new PatchContext(path, null, null);
    }
}
