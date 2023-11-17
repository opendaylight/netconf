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
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.ReadDataParams;
import org.opendaylight.restconf.nb.rfc8040.databind.OperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.server.spi.OperationOutput;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * An implementation of a RESTCONF server, implementing the
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3">RESTCONF API Resource</a>.
 */
@NonNullByDefault
public interface RestconfServer {
    /**
     * Delete a data resource.
     *
     * @param identifier resource identifier
     * @return A {@link RestconfFuture} of the operation
     */
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    RestconfFuture<Empty> dataDELETE(String identifier);

    /**
     * Return the content of the datastore.
     *
     * @param readParams {@link ReadDataParams} for this request
     * @return A {@link RestconfFuture} of the {@link NormalizedNodePayload} content
     */
    RestconfFuture<NormalizedNodePayload> dataGET(ReadDataParams readParams);

    /**
     * Return the content of a data resource.
     *
     * @param identifier resource identifier
     * @param readParams {@link ReadDataParams} for this request
     * @return A {@link RestconfFuture} of the {@link NormalizedNodePayload} content
     */
    RestconfFuture<NormalizedNodePayload> dataGET(String identifier, ReadDataParams readParams);

    /**
     * Return the set of supported RPCs supported by {@link #operationsPOST(URI, String, OperationInputBody)}.
     *
     * @return An {@link OperationsContent}
     */
    OperationsContent operationsGET();

    /*
     * Return the details about a particular operation supported by
     * {@link #operationsPOST(URI, String, OperationInputBody)}, as expressed in the
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#page-84>RFC8040<a> {@code container operations} statement.
     *
     * @param operation An operation
     * @return An {@link OperationsContent}, or {@code null} if {@code operation} does not point to an {@code rpc}
     */
    // FIXME: 'operation' should really be an ApiIdentifier with non-null module, but we also support ang-ext:mount,
    //        and hence it is a path right now
    // FIXME: use ApiPath instead of String
    @Nullable OperationsContent operationsGET(String operation);

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
}
