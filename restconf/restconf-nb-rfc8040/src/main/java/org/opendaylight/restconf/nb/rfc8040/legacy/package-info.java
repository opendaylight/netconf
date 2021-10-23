/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * Various legacy constructs used in RFC8040 northbound. This package holds classes which are needed to properly extend
 * restconf-common constructs without polluting them with things that are internal to this implementation and either are
 * not or cannot be assumed to be relevant to bierman02.
 *
 * <p>
 * These constructs are subject to future removal as we restructure how RESTCONF requests are wired to JAX-RS, bound to
 * the runtime environment (MD-SAL services and mount points).
 */
package org.opendaylight.restconf.nb.rfc8040.legacy;