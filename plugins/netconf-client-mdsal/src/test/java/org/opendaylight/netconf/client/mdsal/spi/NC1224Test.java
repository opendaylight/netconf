/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

@ExtendWith(MockitoExtension.class)
class NC1224Test {
    private static final Absolute ONE = Absolute.of(QName.create("test", "one"));
    private static final Absolute TWO = Absolute.of(QName.create("test", "two"));

    private final NetconfDeviceNotificationService svc = new NetconfDeviceNotificationService();

    @Mock
    private DOMNotificationListener listener;

    @AfterEach
    void afterEach() {
        assertEquals(0, svc.size());
    }

    @Test
    void registerEmpty() {
        try (var reg = svc.registerNotificationListener(listener)) {
            assertEquals(0, svc.size());
        }
    }

    @Test
    void registerEmptyMap() {
        try (var reg = svc.registerNotificationListeners(Map.of())) {
            assertEquals(0, svc.size());
        }
    }

    @Test
    void registerOne() {
        try (var reg = svc.registerNotificationListener(listener, ONE)) {
            assertEquals(1, svc.size());
        }
    }

    @Test
    void registerOneMap() {
        try (var reg = svc.registerNotificationListeners(Map.of(ONE, listener))) {
            assertEquals(1, svc.size());
        }
    }

    @Test
    void registerOneTwo() {
        try (var reg = svc.registerNotificationListener(listener, ONE, TWO)) {
            assertEquals(2, svc.size());
        }
    }

    @Test
    void registerOneTwoMap() {
        try (var reg = svc.registerNotificationListeners(Map.of(ONE, listener, TWO, listener))) {
            assertEquals(2, svc.size());
        }
    }
}
