/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.common.DatabindPath.Action;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * A concrete implementation of RESTCONF server, as specified by
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.4.2">RFC8040 Invoke Operation Mode</a>, based on
 * {@code yang-data-api}.
 */
@NonNullByDefault
public interface ServerActionOperations {
    /**
     * Invoke an {@code action}.
     *
     * @param request {@link ServerRequest} for this request
     * @param path data path for this request
     * @param input action {@code input}
     */
    void invokeAction(ServerRequest<? super InvokeResult> request, Action path, ContainerNode input);
}
