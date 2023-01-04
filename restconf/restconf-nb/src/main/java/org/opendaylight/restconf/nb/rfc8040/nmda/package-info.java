/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * Interfaces related to routing requests in a RESTCONF setting. The concepts here are distilled from the combination
 * of RFC6241, RFC7950, RFC8528 and RFC8342.
 *
 * <p>
 * The organization assumes a weakly-consistent implementation of a Network Management Datastore Architecture endpoint.
 * The primary entrypoint is {@link DatastoreService}, which allows access to services bound to a YANG-modeled
 * datastore.
 *
 * <p>
 * The concept of the datastore is central to all the other concepts, as there is an implicit assumption that RFC7950
 * {@code action} invocations operate on, and {@code notifications} originate from, the {@code operational} datastore.
 * We generalize this assumption to mean that, in general, each datastore provides a set of services -- some of them are
 * synchronous data read and modification (e.g. read/write/delete/merge/patch), others are more general (e.g.
 * {@code action} invocation, RFC8639 subscriptions).
 *
 * <p>
 * Not all capabilities of this API have a direct reflection in any RFC, it is just that is convenient to think of them
 * as being organized this way, for the sake of allowing downstream protocols (e.g. NETCONF without NMDA) to pick and
 * choose the granularity at which they provide access. This is important, i.a., for administrative console functions,
 * where capabilities beyond well-defined protocols are required.
 */
package org.opendaylight.restconf.nb.rfc8040.nmda;
