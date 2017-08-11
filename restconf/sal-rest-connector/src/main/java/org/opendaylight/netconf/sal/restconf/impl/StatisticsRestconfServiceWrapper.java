/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.rest.api.RestconfService;

public class StatisticsRestconfServiceWrapper implements RestconfService {

    AtomicLong operationalGet = new AtomicLong();
    AtomicLong configGet = new AtomicLong();
    AtomicLong rpc = new AtomicLong();
    AtomicLong configPost = new AtomicLong();
    AtomicLong configPut = new AtomicLong();
    AtomicLong configDelete = new AtomicLong();
    AtomicLong successGetConfig = new AtomicLong();
    AtomicLong successGetOperational = new AtomicLong();
    AtomicLong successPost = new AtomicLong();
    AtomicLong successPut = new AtomicLong();
    AtomicLong successDelete = new AtomicLong();
    AtomicLong failureGetConfig = new AtomicLong();
    AtomicLong failureGetOperational = new AtomicLong();
    AtomicLong failurePost = new AtomicLong();
    AtomicLong failurePut = new AtomicLong();
    AtomicLong failureDelete = new AtomicLong();

    private static final StatisticsRestconfServiceWrapper INSTANCE =
            new StatisticsRestconfServiceWrapper(RestconfImpl.getInstance());

    final RestconfService delegate;

    private StatisticsRestconfServiceWrapper(final RestconfService delegate) {
        this.delegate = delegate;
    }

    public static StatisticsRestconfServiceWrapper getInstance() {
        return INSTANCE;
    }

    @Override
    public Object getRoot() {
        return this.delegate.getRoot();
    }

    @Override
    public NormalizedNodeContext getModules(final UriInfo uriInfo) {
        return this.delegate.getModules(uriInfo);
    }

    @Override
    public NormalizedNodeContext getModules(final String identifier, final UriInfo uriInfo) {
        return this.delegate.getModules(identifier, uriInfo);
    }

    @Override
    public NormalizedNodeContext getModule(final String identifier, final UriInfo uriInfo) {
        return this.delegate.getModule(identifier, uriInfo);
    }

    @Override
    public NormalizedNodeContext getOperations(final UriInfo uriInfo) {
        return this.delegate.getOperations(uriInfo);
    }

    @Override
    public NormalizedNodeContext getOperations(final String identifier, final UriInfo uriInfo) {
        return this.delegate.getOperations(identifier, uriInfo);
    }

    @Override
    public NormalizedNodeContext invokeRpc(final String identifier, final NormalizedNodeContext payload,
            final UriInfo uriInfo) {
        this.rpc.incrementAndGet();
        return this.delegate.invokeRpc(identifier, payload, uriInfo);
    }

    @Override
    public NormalizedNodeContext invokeRpc(final String identifier, final String noPayload, final UriInfo uriInfo) {
        this.rpc.incrementAndGet();
        return this.delegate.invokeRpc(identifier, noPayload, uriInfo);
    }

    @Override
    public NormalizedNodeContext readConfigurationData(final String identifier, final UriInfo uriInfo) {
        this.configGet.incrementAndGet();
        NormalizedNodeContext normalizedNodeContext = null;
        try {
            normalizedNodeContext = this.delegate.readConfigurationData(identifier, uriInfo);
            if (normalizedNodeContext.getData() != null) {
                this.successGetConfig.incrementAndGet();
            }
            else {
                this.failureGetConfig.incrementAndGet();
            }
        } catch (final Exception e) {
            this.failureGetConfig.incrementAndGet();
            throw e;
        }
        return normalizedNodeContext;
    }

    @Override
    public NormalizedNodeContext readOperationalData(final String identifier, final UriInfo uriInfo) {
        this.operationalGet.incrementAndGet();
        NormalizedNodeContext normalizedNodeContext = null;
        try {
            normalizedNodeContext = this.delegate.readOperationalData(identifier, uriInfo);
            if (normalizedNodeContext.getData() != null) {
                this.successGetOperational.incrementAndGet();
            }
            else {
                this.failureGetOperational.incrementAndGet();
            }
        } catch (final Exception e) {
            this.failureGetOperational.incrementAndGet();
            throw e;
        }
        return normalizedNodeContext;
    }

    @Override
    public Response updateConfigurationData(final String identifier, final NormalizedNodeContext payload,
            final UriInfo uriInfo) {
        this.configPut.incrementAndGet();
        Response response = null;
        try {
            response = this.delegate.updateConfigurationData(identifier, payload, uriInfo);
            if (response.getStatus() == Status.OK.getStatusCode()) {
                this.successPut.incrementAndGet();
            }
            else {
                this.failurePut.incrementAndGet();
            }
        } catch (final Exception e) {
            this.failurePut.incrementAndGet();
            throw e;
        }
        return response;
    }

    @Override
    public Response createConfigurationData(final String identifier, final NormalizedNodeContext payload,
            final UriInfo uriInfo) {
        this.configPost.incrementAndGet();
        Response response = null;
        try {
            response = this.delegate.createConfigurationData(identifier, payload, uriInfo);
            if (response.getStatus() == Status.OK.getStatusCode()) {
                this.successPost.incrementAndGet();
            }
            else {
                this.failurePost.incrementAndGet();
            }
        } catch (final Exception e) {
            this.failurePost.incrementAndGet();
            throw e;
        }
        return response;
    }

    @Override
    public Response createConfigurationData(final NormalizedNodeContext payload, final UriInfo uriInfo) {
        this.configPost.incrementAndGet();
        Response response = null;
        try {
            response = this.delegate.createConfigurationData(payload, uriInfo);
            if (response.getStatus() == Status.OK.getStatusCode()) {
                this.successPost.incrementAndGet();
            }
            else {
                this.failurePost.incrementAndGet();
            }
        }catch (final Exception e) {
            this.failurePost.incrementAndGet();
            throw e;
        }
        return response;
    }

    @Override
    public Response deleteConfigurationData(final String identifier) {
        this.configDelete.incrementAndGet();
        Response response = null;
        try {
            response = this.delegate.deleteConfigurationData(identifier);
            if (response.getStatus() == Status.OK.getStatusCode()) {
                this.successDelete.incrementAndGet();
            }
            else {
                this.failureDelete.incrementAndGet();
            }
        } catch (final Exception e) {
            this.failureDelete.incrementAndGet();
            throw e;
        }
        return response;
    }

    @Override
    public NormalizedNodeContext subscribeToStream(final String identifier, final UriInfo uriInfo) {
        return this.delegate.subscribeToStream(identifier, uriInfo);
    }

    @Override
    public NormalizedNodeContext getAvailableStreams(final UriInfo uriInfo) {
        return this.delegate.getAvailableStreams(uriInfo);
    }

    @Override
    public Response patchConfigurationData(final String identifier, final PATCHContext payload,
            final UriInfo uriInfo) {
        return this.delegate.patchConfigurationData(identifier, payload, uriInfo);
    }

    @Override
    public Response patchConfigurationData(final PATCHContext payload, final UriInfo uriInfo) {
        return this.delegate.patchConfigurationData(payload, uriInfo);
    }

    public BigInteger getConfigDelete() {
        return BigInteger.valueOf(this.configDelete.get());
    }

    public BigInteger getConfigGet() {
        return BigInteger.valueOf(this.configGet.get());
    }

    public BigInteger getConfigPost() {
        return BigInteger.valueOf(this.configPost.get());
    }

    public BigInteger getConfigPut() {
        return BigInteger.valueOf(this.configPut.get());
    }

    public BigInteger getOperationalGet() {
        return BigInteger.valueOf(this.operationalGet.get());
    }

    public BigInteger getRpc() {
        return BigInteger.valueOf(this.rpc.get());
    }

    public BigInteger getSuccessGetConfig() {
        return BigInteger.valueOf(this.successGetConfig.get());
    }

    public BigInteger getSuccessGetOperational() {
        return BigInteger.valueOf(this.successGetOperational.get());
    }

    public BigInteger getSuccessPost() {
        return BigInteger.valueOf(this.successPost.get());
    }

    public BigInteger getSuccessPut() {
        return BigInteger.valueOf(this.successPut.get());
    }

    public BigInteger getSuccessDelete() {
        return BigInteger.valueOf(this.successDelete.get());
    }

    public BigInteger getFailureGetConfig() {
        return BigInteger.valueOf(this.failureGetConfig.get());
    }

    public BigInteger getFailureGetOperational() {
        return BigInteger.valueOf(this.failureGetOperational.get());
    }

    public BigInteger getFailurePost() {
        return BigInteger.valueOf(this.failurePost.get());
    }

    public BigInteger getFailurePut() {
        return BigInteger.valueOf(this.failurePut.get());
    }

    public BigInteger getFailureDelete() {
        return BigInteger.valueOf(this.failureDelete.get());
    }
}