/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.dispatch.Futures;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import java.util.Set;
import org.opendaylight.controller.cluster.schema.provider.RemoteYangTextSourceProvider;
import org.opendaylight.controller.cluster.schema.provider.impl.YangTextSchemaSourceSerializationProxy;
import org.opendaylight.netconf.topology.singleton.messages.YangTextSchemaSourceRequest;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.impl.Promise;

public class ProxyYangTextSourceProvider implements RemoteYangTextSourceProvider {

    private final ActorRef masterRef;
    private final ExecutionContext executionContext;
    private final Timeout actorResponseWaitTime;

    public ProxyYangTextSourceProvider(final ActorRef masterRef, final ExecutionContext executionContext,
                                       final Timeout actorResponseWaitTime) {
        this.masterRef = masterRef;
        this.executionContext = executionContext;
        this.actorResponseWaitTime = actorResponseWaitTime;
    }

    @Override
    public Future<Set<SourceIdentifier>> getProvidedSources() {
        // NOOP
        return Futures.successful(Set.of());
    }

    @Override
    public Future<YangTextSchemaSourceSerializationProxy> getYangTextSchemaSource(
            final SourceIdentifier sourceIdentifier) {
        final var promise = new Promise.DefaultPromise<YangTextSchemaSourceSerializationProxy>();
        Patterns.ask(masterRef, new YangTextSchemaSourceRequest(sourceIdentifier), actorResponseWaitTime).onComplete(
            new OnComplete<>() {
                @Override
                public void onComplete(final Throwable failure, final Object success) {
                    if (failure == null) {
                        promise.success((YangTextSchemaSourceSerializationProxy) success);
                    } else {
                        promise.failure(failure);
                    }
                }
            }, executionContext);
        return promise.future();
    }
}
