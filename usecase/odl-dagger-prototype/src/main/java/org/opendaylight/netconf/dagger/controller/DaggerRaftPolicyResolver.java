/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.controller;

import jakarta.inject.Singleton;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.raft.spi.AbstractRaftPolicyResolver;
import org.opendaylight.raft.spi.RaftPolicy;

@Singleton
@NonNullByDefault
// FIXME: Remove this when org.opendaylight.raft.spi module-info contains "uses org.opendaylight.raft.spi.RaftPolicy;"
public class DaggerRaftPolicyResolver extends AbstractRaftPolicyResolver {
    private final ServiceLoader<RaftPolicy> loader;

    public DaggerRaftPolicyResolver() {
        this.loader = ServiceLoader.load(RaftPolicy.class);
    }

    @Override
    protected Stream<RaftPolicy> streamPolicies() {
        return loader.stream().map(ServiceLoader.Provider::get);
    }
}
