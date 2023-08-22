/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;

public final class CapabilityUtil {

    private static final @NonNull String MODULE_PARAM = "module";
    private static final @NonNull String REVISION_PARAM = "revision";
    private static final @NonNull String FEATURES_PARAM = "features";
    private static final @NonNull String DEVIATIONS_PARAM = "deviations";
    private static final @NonNull String EXI_CAPABILITY = "urn:ietf:params:netconf:capability:exi:1.0";
    private static final @NonNull String COMPRESSION_PARAM = "compression";
    private static final @NonNull String SCHEMAS_PARAM = "schemas";

    private CapabilityUtil() {
        // Hidden on purpose
    }

    public static Capability parse(final String urn) {
        // TODO add some validation for urn
        final var urnParts = urn.split("\\?");
        if (urnParts.length == 1) {
            final var simpleCapability = SimpleCapability.forURN(urn);
            if (simpleCapability != null) {
                return simpleCapability;
            } else if (urn.equals(EXI_CAPABILITY)) {
                return new ExiCapability(null, null);
            } else {
                return new YangModuleCapability(urn, null, null, null, null);
            }
        } else {
            final var parameters = parseParameters(urnParts[1]);
            if (urnParts[0].equals(EXI_CAPABILITY)) {
                return parseExiCapability(parameters);
            } else {
                return parseYangModuleCapability(urnParts[0], parameters);
            }
        }
    }

    private static Capability parseYangModuleCapability(final String moduleNamespace,
            final Map<String, String> parameters) {
        final String moduleName = parameters.getOrDefault(MODULE_PARAM, null);
        final String revision = parameters.getOrDefault(REVISION_PARAM, null);
        final List<String> features = parameters.containsKey(FEATURES_PARAM)
            ? Arrays.stream(parameters.get(FEATURES_PARAM).split(",")).toList() : null;
        final List<String> deviations = parameters.containsKey(DEVIATIONS_PARAM)
            ? Arrays.stream(parameters.get(DEVIATIONS_PARAM).split(",")).toList() : null;
        return new YangModuleCapability(moduleNamespace, moduleName, revision, features, deviations);
    }

    private static Capability parseExiCapability(final Map<String, String> parameters) {
        final Integer compression = parameters.containsKey(COMPRESSION_PARAM)
            ? Integer.parseInt(parameters.get(COMPRESSION_PARAM)) : null;
        final ExiCapability.Schemas schemas = parameters.containsKey(SCHEMAS_PARAM)
            ? getSchemas(parameters.get(SCHEMAS_PARAM)) : null;
        return new ExiCapability(compression, schemas);
    }

    private static ExiCapability.Schemas getSchemas(final String value) {
        return switch (value) {
            case "builtin" -> ExiCapability.Schemas.BUILTIN;
            case "base:1.1" -> ExiCapability.Schemas.BASE_1_1;
            default -> null;
        };
    }

    private static Map<String, String> parseParameters(final String parametersString) {
        final var parameters = parametersString.split("&");
        final var result = new HashMap<String, String>();
        for (final String parameter : parameters) {
            final var index = parameter.indexOf("=");
            result.put(parameter.substring(0, index), parameter.substring(index + 1));
        }
        return result;
    }
}
