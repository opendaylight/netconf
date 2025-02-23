/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.restconf.server.api.ChildBody.PrefixAndBody;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * Abstract base class for {@link ServerDataOperations}.
 */
public abstract class AbstractServerDataOperations implements ServerDataOperations {
    @Override
    public final void createData(final ServerRequest<? super CreateResourceResult> request, final Data path,
            final PrefixAndBody data) {
        createData(request, path, parentPath(path, data), data.body());
    }

    protected abstract void createData(ServerRequest<? super CreateResourceResult> request, Data path,
        YangInstanceIdentifier parentPath, NormalizedNode data);

    @Override
    public final void createData(final ServerRequest<? super CreateResourceResult> request, final Data path,
            final Insert insert, final PrefixAndBody data) {
        createData(request, path, insert, parentPath(path, data), data.body());
    }

    protected abstract void createData(ServerRequest<? super CreateResourceResult> request, Data path, Insert insert,
        YangInstanceIdentifier parentPath, NormalizedNode data);

    private static YangInstanceIdentifier parentPath(final Data path, final PrefixAndBody prefixAndBody) {
        var ret = path.instance();
        for (var arg : prefixAndBody.prefix()) {
            ret = ret.node(arg);
        }
        return ret;
    }
}
