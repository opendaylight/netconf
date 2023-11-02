/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

class StartTimeParamTest {
    @Test
    void epochStartParamValueInstant() {
        assertInstant(Instant.EPOCH, "1970-01-01T00:00:00Z");
    }

    @Test
    void nowParamValueInstant() {
        final var now = Instant.now();
        assertInstant(now, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.ofInstant(now,
            ZoneId.systemDefault())));
    }

    private static void assertInstant(final Instant expected, final String value) {
        assertEquals(expected, StartTimeParam.forUriValue(value).paramValueInstant());
    }
}
