/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.ChildBody;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPostBody;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.OperationInputBody;
import org.opendaylight.restconf.server.api.OptionsResult;
import org.opendaylight.restconf.server.api.PatchBody;
import org.opendaylight.restconf.server.api.ResourceBody;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.SourceRepresentation;

/**
 * RESTCONF server request execution strategy. This interface mirrors {@link RestconfServer}, except {@link ApiPath}s
 * do not contain {@code yang-ext:mount} steps, i.e. implementations assume a monolithic YANG world as envisioned by
 * IETF NETCONF WG.
 */
@Beta
public interface ServerStrategy {
    /**
     * Delete data from the configuration datastore. If the data does not exist, this operation will fail, as outlined
     * in <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.7">RFC8040 section 4.7</a>
     *
     * @param request {@link ServerRequest} for this request
     * @param apiPath Path to delete
     * @throws NullPointerException if {@code apiPath} is {@code null}
     */
    @NonNullByDefault
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    void dataDELETE(ServerRequest<Empty> request, ApiPath apiPath);

    /**
     * Return the content of the datastore.
     *
     * @param request {@link ServerRequest} for this request
     */
    void dataGET(ServerRequest<DataGetResult> request);

    /**
     * Return the content of a data resource.
     *
     * @param request {@link ServerRequest} for this request
     * @param path resource identifier
     */
    void dataGET(ServerRequest<DataGetResult> request, ApiPath path);

    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    void dataOPTIONS(final ServerRequest<OptionsResult> request);

    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    void dataOPTIONS(final ServerRequest<OptionsResult> request, ApiPath path);

    /**
     * Merge data into the configuration datastore, as outlined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040 section 4.6.1</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param body Data to merge
     * @throws NullPointerException if any argument is {@code null}
     */
    void dataPATCH(ServerRequest<DataPatchResult> request, ResourceBody body);

    /**
     * Merge data into the configuration datastore, as outlined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040 section 4.6.1</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param path Path to merge
     * @param body Data to merge
     * @throws NullPointerException if any argument is {@code null}
     */
    void dataPATCH(ServerRequest<DataPatchResult> request, ApiPath path, ResourceBody body);

    /**
     * Ordered list of edits that are applied to the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param body YANG Patch body
     */
    void dataPATCH(ServerRequest<DataYangPatchResult> request, PatchBody body);

    /**
     * Ordered list of edits that are applied to a resource in the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param path path to target
     * @param body YANG Patch body
     */
    void dataPATCH(ServerRequest<DataYangPatchResult> request, ApiPath path, PatchBody body);

    /**
     * Create a top-level resource, as described in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.4.1">RFC8040 section 4.4.1</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param body body of the POST request
     */
    void dataPOST(ServerRequest<? super CreateResourceResult> request, ChildBody body);

    /**
     * Create or invoke a operation, as described in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.4">RFC8040 section 4.4</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param path path to target
     * @param body body of the POST request
     */
    void dataPOST(ServerRequest<DataPostResult> request, ApiPath path, DataPostBody body);

    /**
     * Replace the datastore, as described in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.5">RFC8040 section 4.5</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param body data node for put to config DS
     */
    void dataPUT(ServerRequest<DataPutResult> request, ResourceBody body);

    /**
     * Create or replace a data store resource, as described in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.5">RFC8040 section 4.5</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param path resource path
     * @param body data node for put to config DS
     */
    void dataPUT(ServerRequest<DataPutResult> request, ApiPath path, ResourceBody body);

    /**
     * Return the set of supported RPCs supported by
     * {@link #operationsPOST(ServerRequest, URI, ApiPath, OperationInputBody)},
     * as expressed in the <a href="https://www.rfc-editor.org/rfc/rfc8040#page-84">ietf-restconf.yang</a>
     * {@code container operations} statement.
     *
     * @param request {@link ServerRequest} for this request
     */
    @NonNullByDefault
    void operationsGET(ServerRequest<FormattableBody> request);

    /*
     * Return the details about a particular operation supported by
     * {@link #operationsPOST(URI, String, OperationInputBody)}, as expressed in the
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#page-84">ietf-restconfig.yang</a>
     * {@code container operations} statement.
     *
     * @param request {@link ServerRequest} for this request
     * @param operation An operation
     */
    @NonNullByDefault
    void operationsGET(ServerRequest<FormattableBody> request, ApiPath operation);

    @NonNullByDefault
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    void operationsOPTIONS(final ServerRequest<OptionsResult> request, ApiPath operation);

    /**
     * Invoke an RPC operation, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.6">RFC8040 Operation Resource</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param restconfURI Base URI of the request, the absolute equivalent to {@code {+restconf}} URI with a trailing
     *                    slash
     * @param operation {@code <operation>} path, really an {@link ApiPath} to an {@code rpc}
     * @param body RPC operation
     */
    void operationsPOST(ServerRequest<InvokeResult> request, URI restconfURI, ApiPath operation,
        OperationInputBody body);

    void modulesGET(ServerRequest<ModulesGetResult> request, SourceIdentifier source,
        Class<? extends SourceRepresentation> representation);

    /**
     * Resolve any and all {@code yang-ext:mount} to the target {@link StrategyAndPath}.
     *
     * @param path {@link ApiPath} to resolve
     * @return A strategy and the remaining path
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws ServerException if an error occurs
     */
    StrategyAndPath resolveStrategy(ApiPath path) throws ServerException;

    /**
     * Result of a {@link ApiPath} lookup for the purposes of supporting {@code yang-ext:mount}-delimited mount points
     * with possible nesting.
     *
     * @param strategy the strategy to use
     * @param path the {@link ApiPath} tail to use with the strategy
     */
    @Beta
    record StrategyAndPath(ServerStrategy strategy, ApiPath path) {
        public StrategyAndPath {
            requireNonNull(strategy);
            requireNonNull(path);
        }
    }
}
