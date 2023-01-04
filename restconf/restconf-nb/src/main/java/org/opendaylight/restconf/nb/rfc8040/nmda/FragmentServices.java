/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.nmda;

import static com.google.common.base.Verify.verifyNotNull;

import java.util.Optional;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.datastores.rev180214.Datastore;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;

/**
 * Access to services offered by a particular part of a part of a YANG-modeled datastore.
 */
@NonNullByDefault
public interface FragmentServices<S extends Datastore> extends DatabindProvider {
    /**
     * Return the {@link Datastore} this object is accessing.
     *
     * @return A {@link Datastore} instance.
     */
    Datastore dataStore();

    /**
     * Return the root path which is accessible through this object. Note that due to the differences in
     * {@code data tree} representation between RFC6241/RFC7950/RFC8040, this path does <b>NOT</b> match the data node
     * which may be provided. As a notable example, {@link ChoiceNode}s and {@link AugmentationNode}s both form inferred
     * nodes and such access case, this method will return the {@code data tree}-implied parent
     * {@link YangInstanceIdentifier}.
     *
     * @return Root accessible path.
     */
    YangInstanceIdentifier rootPath();

    // FIXME: action/RPC invocation mapping (note RFC8528 translation of RPCs into actions)
    // FIXME: notification/DTCL/RFC8639 subscription: I think this boil down to providing an SPI to register RPC/action
    //        implementations outside of DOMRpcService
    // FIXME: this ends up being a registry similar to what we have in DOMMountPoint:getService()
    // FIXME: null if access is not granted
    <T extends FragmentService> @Nullable T service(Class<T> serviceClass);

    default <T extends FragmentService> Optional<T> findService(final Class<T> serviceClass) {
        return Optional.ofNullable(service(serviceClass));
    }

    default <T extends FragmentService> T getService(final Class<T> serviceClass) {
        return verifyNotNull(service(serviceClass));
    }
}
