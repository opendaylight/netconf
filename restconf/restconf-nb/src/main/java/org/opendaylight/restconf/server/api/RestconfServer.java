/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.databind.OperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.OperationsContent;
import org.opendaylight.restconf.server.spi.OperationOutput;

/**
 * An implementation of a RESTCONF server, implementing the
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3">RESTCONF API Resource</a>.
 */
@NonNullByDefault
public interface RestconfServer {
    /**
     * Return the revision of {@code ietf-yang-library} module implemented by this server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.3">RFC8040 {+restconf}/yang-library-version</a>.
     *
     * @return A {@code yang-library-version} element
     */
    // FIXME: this is a simple coning-variadic return, similar to how OperationsContent is handled use a common
    //        construct for both cases
    // FIXME: RestconfFuture if we transition to being used by restconf-client implementation
    NormalizedNodePayload yangLibraryVersionGET();

    /**
     * Return the set of supported RPCs supported by {@link #operationsPOST(URI, String, OperationInputBody)}.
     *
     * @param contentType Formatting type
     * @return A formatted string
     */
    String operationsGET(OperationsContent contentType);

    /*
     * Return the details about a particular operation supported by
     * {@link #operationsPOST(URI, String, OperationInputBody)}, as expressed in the
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#page-84>RFC8040<a> {@code container operations} statement.
     *
     * @param contentType Formatting type
     * @param operation An operation
     * @return A formatted string
     */
    // FIXME: 'operation' should really be an ApiIdentifier with non-null module, but we also support ang-ext:mount,
    //        and hence it is a path right now
    // FIXME: use ApiPath instead of String
    String operationsGET(OperationsContent contentType, String operation);

    /**
     * Invoke an RPC operation, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.6">RFC8040 Operation Resource</a>.
     *
     * @param restconfURI Base URI of the request
     * @param operation {@code <operation>} path, really an {@link ApiPath} to an {@code rpc}
     * @param body RPC operation
     * @return A {@link RestconfFuture} of the {@link OperationOutput operation result}
     */
    // FIXME: 'operation' should really be an ApiIdentifier with non-null module, but we also support ang-ext:mount,
    //        and hence it is a path right now
    // FIXME: use ApiPath instead of String
    RestconfFuture<OperationOutput> operationsPOST(URI restconfURI, String operation, OperationInputBody body);
}
