/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.capability.ExiCapability;
import org.opendaylight.netconf.api.capability.ExiSchemas;
import org.opendaylight.netconf.api.capability.SimpleCapability;
import org.opendaylight.netconf.api.capability.YangModuleCapability;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.DeviationEffectiveStatement;

/**
 * Utility class used for simple creation and parsing Capability objects.
 */
@Beta
public final class CapabilityUtil {
    private static final String MODULE_PARAM = "module";
    private static final String REVISION_PARAM = "revision";
    private static final String FEATURES_PARAM = "features";
    private static final String DEVIATIONS_PARAM = "deviations";
    private static final String COMPRESSION_PARAM = "compression";
    private static final String SCHEMAS_PARAM = "schemas";

    private CapabilityUtil() {
        // Hidden on purpose
    }

    /**
     * This method tries to parse received urn string into {@link Capability} object. For urn string without parameters
     * we are trying to crate {@link Capability} object in following order:
     * <ul>
     *  <li>{@link SimpleCapability}</li>
     *  <li>{@link ExiCapability}</li>
     *  <li>{@link YangModuleCapability}</li>
     * </ul>
     * For parametrized urn we are trying to create {@link ExiCapability} and if we can't - then
     * {@link YangModuleCapability} object is created.
     *
     * @param urn string with info about capability
     * @return capability object parsed from urn
     */
    public static @NonNull Capability parse(final @NonNull String urn) {
        requireNonNull(urn);
        checkArgument(!urn.isEmpty(), "Urn is empty");
        final var urnParts = urn.split("\\?");
        if (urnParts.length == 1) {
            final var simpleCapability = SimpleCapability.forURN(urn);
            if (simpleCapability != null) {
                return simpleCapability;
            } else if (urn.equals(CapabilityURN.EXI)) {
                return new ExiCapability(null, null);
            } else {
                return new YangModuleCapability(urn, null, null, null, null);
            }
        } else {
            final var parameters = parseParameters(urnParts[1]);
            if (urnParts[0].equals(CapabilityURN.EXI)) {
                return parseExiCapability(parameters);
            } else {
                return parseYangModuleCapability(urnParts[0], parameters);
            }
        }
    }

    /**
     * Using provided {@link EffectiveModelContext} this method extracts information about all present modules, their
     * deviations and features and other data. This data then used for creation of list that contains
     * {@link YangModuleCapability} objects of each module.
     *
     * @param context {@link EffectiveModelContext} with capabilities to extract
     * @return immutable list of {@link YangModuleCapability} objects
     */
    public static @NonNull List<YangModuleCapability> extractYangModuleCapabilities(
            final @NonNull EffectiveModelContext context) {
        // construct back references from deviated module to the module defining the deviation
        final var builder = ImmutableListMultimap.<QNameModule, String>builder();
        for (final var module : context.getModuleStatements().values()) {
            module.streamEffectiveSubstatements(DeviationEffectiveStatement.class)
                .map(deviation -> deviation.argument().lastNodeIdentifier().getModule())
                .forEach(target -> builder.put(target, module.argument().getLocalName()));
        }
        final var deviations = builder.build();

        return context.getModuleStatements().values().stream()
            .map(module -> {
                final var namespace = module.localQNameModule();
                final var revision = namespace.revision();
                return new YangModuleCapability(
                    namespace.namespace().toString(),
                    module.argument().getLocalName(),
                    revision != null ? revision.toString() : null,
                    module.features().stream().map(feature -> feature.argument().getLocalName()).toList(),
                    deviations.get(namespace));
            })
            .collect(ImmutableList.toImmutableList());
    }

    private static Capability parseYangModuleCapability(final String moduleNamespace,
            final Map<String, String> parameters) {
        final var moduleName = parameters.getOrDefault(MODULE_PARAM, null);
        final var revision = parameters.getOrDefault(REVISION_PARAM, null);
        final var features = parameters.containsKey(FEATURES_PARAM)
            ? Arrays.stream(parameters.get(FEATURES_PARAM).split(",")).toList() : null;
        final var deviations = parameters.containsKey(DEVIATIONS_PARAM)
            ? Arrays.stream(parameters.get(DEVIATIONS_PARAM).split(",")).toList() : null;
        return new YangModuleCapability(moduleNamespace, moduleName, revision, features, deviations);
    }

    private static Capability parseExiCapability(final Map<String, String> parameters) {
        final var compression = parameters.containsKey(COMPRESSION_PARAM)
            ? Integer.parseInt(parameters.get(COMPRESSION_PARAM)) : null;
        final var schemas = parameters.containsKey(SCHEMAS_PARAM)
            ? getSchemas(parameters.get(SCHEMAS_PARAM)) : null;
        return new ExiCapability(compression, schemas);
    }

    private static ExiSchemas getSchemas(final String value) {
        return switch (value) {
            case "builtin" -> ExiSchemas.BUILTIN;
            case "base:1.1" -> ExiSchemas.BASE_1_1;
            case "dynamic" -> ExiSchemas.DYNAMIC;
            default -> null;
        };
    }

    private static Map<String, String> parseParameters(final String parametersString) {
        final var parameters = parametersString.split("&");
        final var result = new HashMap<String, String>();
        for (final var parameter : parameters) {
            final var index = parameter.indexOf("=");
            result.put(parameter.substring(0, index), parameter.substring(index + 1));
        }
        return result;
    }
}
