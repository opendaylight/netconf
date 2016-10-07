/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import java.util.Set;
import javax.annotation.Nonnull;
import org.opendaylight.controller.cluster.schema.provider.RemoteYangTextSourceProvider;
import org.opendaylight.controller.cluster.schema.provider.impl.YangTextSchemaSourceSerializationProxy;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.YangTextSchemaSourceRequest;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import scala.concurrent.Future;
import scala.concurrent.impl.Promise;

public class ProxyYangTextSourceProvider implements RemoteYangTextSourceProvider {

    private final ActorRef masterRef;
    private final ActorContext actorContext;

    public ProxyYangTextSourceProvider(ActorRef masterRef, ActorContext actorContext) {
        this.masterRef = masterRef;
        this.actorContext = actorContext;
    }

    @Override
    public Future<Set<SourceIdentifier>> getProvidedSources() {
        return null;
    }

    @Override
    public Future<YangTextSchemaSourceSerializationProxy> getYangTextSchemaSource(
            @Nonnull final SourceIdentifier sourceIdentifier) {

        Future<Object> scalaFuture = Patterns.ask(masterRef,
                new YangTextSchemaSourceRequest(sourceIdentifier), NetconfTopologyUtils.TIMEOUT);

        final Promise.DefaultPromise<YangTextSchemaSourceSerializationProxy> promise = new Promise.DefaultPromise<>();

        scalaFuture.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable failure, Object success) throws Throwable {
                if (failure != null) {
                    promise.failure(failure);
                    return;
                }
                if (success instanceof Throwable) {
                    promise.failure((Throwable) success);
                    return;
                }
                promise.success((YangTextSchemaSourceSerializationProxy) success);
            }
        }, actorContext.dispatcher());

        return promise.future();

    }
}
