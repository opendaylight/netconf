/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.restconf.nb.rfc8040.OSGiNorthbound;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yangtools.yang.common.Revision;

class RestconfSchemaSourceUrlProviderTest {

    public static final String BASE_PATH = "rests";

    @Test
    @DisplayName("Unsupported module-set name.")
    void unsupportedModuleSet() {
        final var urlProvider = new RestconfSchemaSourceUrlProvider(new TestOSGiConfiguration());
        final var result = urlProvider.getSchemaSourceUrl("some-module-set", "module", null);
        assertTrue(result.isEmpty());
    }

    @ParameterizedTest(name = "Supported module-set name. URL: {2}")
    @MethodSource
    void getSchemaSourceUrl(final String moduleName, final Revision revision, final Uri expected) {
        final var urlProvider = new RestconfSchemaSourceUrlProvider(new TestOSGiConfiguration());
        final var result = urlProvider.getSchemaSourceUrl("ODL_modules", moduleName, revision);
        assertEquals(Optional.of(expected), result);
    }

    private static List<Arguments> getSchemaSourceUrl() {
        return List.of(
            Arguments.of("odl-module", Revision.of("2023-02-23"),
                new Uri("/rests/modules/odl-module?revision=2023-02-23")),
            Arguments.of("module-no-revision", null, new Uri("/rests/modules/module-no-revision"))
        );
    }

    private final class TestOSGiConfiguration implements OSGiNorthbound.Configuration {

        @Override
        public int maximum$_$fragment$_$length() {
            return 0;
        }

        @Override
        public int heartbeat$_$interval() {
            return 0;
        }

        @Override
        public int idle$_$timeout() {
            return 0;
        }

        @Override
        public String ping$_$executor$_$name$_$prefix() {
            return null;
        }

        @Override
        public int max$_$thread$_$count() {
            return 0;
        }

        @Override
        public boolean use$_$sse() {
            return true;
        }

        @Override
        public String base$_$path() {
            return "rests";
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return null;
        }
    }
}
