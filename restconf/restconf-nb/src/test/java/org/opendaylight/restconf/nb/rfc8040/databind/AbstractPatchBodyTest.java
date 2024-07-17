/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.collect.ImmutableMap;
import java.io.InputStream;
import java.text.ParseException;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy.StrategyAndPath;
import org.opendaylight.restconf.server.api.PatchBody;
import org.opendaylight.restconf.server.api.PatchContext;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.spi.AbstractInstanceIdentifierTest;
import org.opendaylight.restconf.server.spi.DefaultResourceContext;

@ExtendWith(MockitoExtension.class)
abstract class AbstractPatchBodyTest extends AbstractInstanceIdentifierTest {
    private final Function<InputStream, PatchBody> bodyConstructor;

    @Mock
    DOMDataBroker dataBroker;
    @Mock
    DOMMountPointService mountPointService;
    @Mock
    DOMMountPoint mountPoint;

    AbstractPatchBodyTest(final Function<InputStream, PatchBody> bodyConstructor) {
        this.bodyConstructor = requireNonNull(bodyConstructor);
    }

    @NonNull String mountPrefix() {
        return "";
    }

    @Nullable DOMMountPoint mountPoint() {
        return null;
    }

    static final void checkPatchContext(final PatchContext patchContext) {
        assertNotNull(patchContext.patchId());
        assertNotNull(patchContext.entities());
    }

    final @NonNull PatchContext parse(final String prefix, final String suffix, final String patchBody)
            throws ServerException {
        final String uriPath;
        if (prefix.isEmpty()) {
            uriPath = suffix;
        } else if (suffix.isEmpty()) {
            uriPath = prefix;
        } else {
            uriPath = prefix + '/' + suffix;
        }
        final ApiPath apiPath;
        try {
            apiPath = ApiPath.parse(uriPath);
        } catch (ParseException e) {
            throw new AssertionError(e);
        }

        final var strategy = new MdsalRestconfStrategy(IID_DATABIND, dataBroker, ImmutableMap.of(), null, null, null,
            mountPointService);
        final StrategyAndPath stratAndPath;
        try {
            stratAndPath = strategy.resolveStrategyPath(apiPath);
        } catch (ServerException e) {
            throw new AssertionError(e);
        }

        try (var body = bodyConstructor.apply(stringInputStream(patchBody))) {
            return body.toPatchContext(new DefaultResourceContext(stratAndPath.path()));
        }
    }
}
