/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.services.modules;

import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.rest.api.connector.RestSchemaController;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestconfModul {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfModul.class);

    protected final RestSchemaController restSchemaController;

    public RestconfModul(final RestSchemaController restSchemaController) {
        this.restSchemaController = restSchemaController;
    }

    protected Module getRestconfModule() {
        final Module restconfModule = this.restSchemaController.getRestconfModule();
        if (restconfModule == null) {
            LOG.debug("ietf-restconf module was not found.");
            throw new RestconfDocumentedException("ietf-restconf module was not found.", ErrorType.APPLICATION,
                    ErrorTag.OPERATION_NOT_SUPPORTED);
        }
        return restconfModule;
    }
}
