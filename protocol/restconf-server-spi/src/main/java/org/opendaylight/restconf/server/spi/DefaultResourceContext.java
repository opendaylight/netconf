/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.common.DatabindPath.Data;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.PatchBody.ResourceContext;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

/**
 * Default implementation of a {@link ResourceContext}.
 */
@NonNullByDefault
public final class DefaultResourceContext extends ResourceContext {
    public DefaultResourceContext(final Data path) {
        super(path);
    }

    @Override
    protected ResourceContext resolveRelative(final ApiPath apiPath) throws ServerException {
        // If subResource is empty just return this resource
        if (apiPath.isEmpty()) {
            return this;
        }

        final var normalizer = new ApiPathNormalizer(path.databind());
        final var urlPath = path.instance();
        if (urlPath.isEmpty()) {
            // URL indicates the datastore resource, let's just normalize targetPath
            return new DefaultResourceContext(normalizer.normalizeDataPath(apiPath));
        }

        // Defer to normalizeSteps(), faking things a bit. Then check the result.
        final var it = apiPath.steps().iterator();
        final var resolved = normalizer.normalizeSteps(path.inference().toSchemaInferenceStack(), path.schema(),
            urlPath.getPathArguments(), urlPath.getLastPathArgument().getNodeType().getModule(), it.next(), it);
        if (resolved instanceof Data dataPath) {
            return new DefaultResourceContext(dataPath);
        }
        throw new ServerException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
            "Sub-resource '%s' resolves to non-data %s", apiPath, resolved);
    }
}
