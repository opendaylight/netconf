/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonFactoryBuilder;
import java.io.IOException;
import java.io.StringWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opendaylight.restconf.openapi.model.UiConfigEntity;

class UiConfigEntityTest {
    /**
     * Verifies that the validator is disabled unless a validator URL is configured.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
        "null, none",
        "'', none",
        "' ', none",
        "none, none",
        "https://validator.swagger.io/validator, https://validator.swagger.io/validator",
        "' https://validator.example.com/v ', https://validator.example.com/v"
    })
    void testValidatorUrl(final String configured, final String expected) throws IOException {
        final var writer = new StringWriter();
        try (var generator = new JsonFactoryBuilder().build().createGenerator(writer)) {
            new UiConfigEntity(configured).generate(generator);
        }
        assertEquals("{\"validatorUrl\":\"" + expected + "\"}", writer.toString());
    }
}
