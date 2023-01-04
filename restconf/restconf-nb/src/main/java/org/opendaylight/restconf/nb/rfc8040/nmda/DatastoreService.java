/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.nmda;

import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.datastores.rev180214.Datastore;

/**
 * Primary API entry point. An NMDA implementation has exactly one global instance of this service. It mediates access
 * to all YANG-modeled services available to the implementation.
 *
 * <p>
 * The underlying model is that of datastore fragments, each of which encapulates access to the data store
 * at a particular subtree. Each access
 *
 * Baseline router for routing requests. This is organized in the usual NMDA fashion, i.e. we have datastores and those
 * provide further services, like RPC/Action invocation, datastore access etc.
 *
 * <p>
 * Access requires a {@link Principal} for authorisation purposes and any service handed out from this API is allowed to
 * throw a {@link SecurityException} in addition to its usual API contract to indicate that the {@code principal}'s
 * access has changed since the handle has been given out. Implementations are advised to perform an eager authorisation
 * check when services are handed out and perform a implementation-convenient re-check policy, so that any TOC-TOE races
 * conditions are reasonably mitigated.
 */
@NonNullByDefault
public interface DatastoreService {
    /**
     * Acquire access to a datastore root. This call is equivalent to requesting
     * {@link #fragmentFor(Principal, Datastore, ApiPath)} at {@link ApiPath#empty()}.
     *
     * @param <S> Datastore type
     * @param principal Principal for access authorization
     * @param datastore Datastore to access
     * @return A {@link FragmentServices} reference
     * @throws NullPointerException if any argument is {@code null}
     * @throws SecurityException if {@code principal} is not authorised to access the {@code datastore}
     */
    default <S extends Datastore> @Nullable FragmentServices<S> fragmentFor(final Principal principal,
            final S datastore) {
        return fragmentFor(principal, datastore, ApiPath.empty());
    }

    /**
     * Acquire access to a datastore path.
     *
     * @param <S> Datastore type
     * @param principal Principal for access authorization
     * @param datastore Datastore to access
     * @param path Datastore path
     * @return A {@link FragmentServices} reference
     * @throws NullPointerException if any argument is {@code null}
     * @throws SecurityException if {@code principal} is not authorised to access the {@code datastore}
     */
    <S extends Datastore> @Nullable FragmentServices<S> fragmentFor(Principal principal, S datastore, ApiPath path);
}
