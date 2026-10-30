/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Swagger UI configuration, loaded by the explorer through Swagger UI {@code configUrl} option.
 */
@NonNullByDefault
public final class UiConfigEntity extends OpenApiEntity {
    private static final String VALIDATOR_DISABLED = "none";

    private final String validatorUrl;

    /**
     * Creates Swagger UI configuration.
     *
     * @param validatorUrl URL of the online validator, {@code null} or blank disables the validator
     */
    public UiConfigEntity(final @Nullable String validatorUrl) {
        this.validatorUrl = validatorUrl == null || validatorUrl.isBlank() ? VALIDATOR_DISABLED : validatorUrl.trim();
    }

    public String validatorUrl() {
        return validatorUrl;
    }

    @Override
    public void generate(final JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        generator.writeStringField("validatorUrl", validatorUrl);
        generator.writeEndObject();
    }
}
