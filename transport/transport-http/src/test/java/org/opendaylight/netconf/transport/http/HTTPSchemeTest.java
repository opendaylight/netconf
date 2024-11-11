/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HTTPSchemeTest {
    @ParameterizedTest
    @MethodSource
    void hostUriOfValid(final String expected, final HTTPScheme scheme, final String host) throws Exception {
        assertEquals(URI.create(expected), scheme.hostUriOf(host));
    }

    private static List<Arguments> hostUriOfValid() {
        return List.of(
            Arguments.of("http://foo", HTTPScheme.HTTP, "foo"),
            Arguments.of("https://bar:1234", HTTPScheme.HTTPS, "bar:1234"));
    }

    @Test
    void hostUriOfInvalidPort() {
        final var ex = assertThrows(URISyntaxException.class, () -> HTTPScheme.HTTP.hostUriOf("foo:abc"));
        assertEquals("Illegal character in port number at index 11: http://foo:abc", ex.getMessage());
    }

    @Test
    void hostUriOfInvalidHostname() {
        final var ex = assertThrows(URISyntaxException.class, () -> HTTPScheme.HTTP.hostUriOf("--"));
        assertEquals("Illegal character in hostname at index 7: http://--", ex.getMessage());
    }

    @Test
    void hostUriOfWithUser() {
        final var ex = assertThrows(URISyntaxException.class, () -> HTTPScheme.HTTP.hostUriOf("user@host"));
        assertEquals("Host contains userinfo: user@host", ex.getMessage());
    }
}
