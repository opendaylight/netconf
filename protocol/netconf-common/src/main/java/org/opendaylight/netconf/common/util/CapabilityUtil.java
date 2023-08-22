/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.capability.ExiCapability;
import org.opendaylight.netconf.api.capability.SimpleCapability;
import org.opendaylight.netconf.api.capability.YangModuleCapability;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.FeatureDefinition;

/**
 * Utility class used for simple creation and parsing Capability objects.
 */
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
        requireNonNull(context);
        final var modules = context.getModules();
        // Using LinkedList for optimization purposes. Array based collections are bad when size isn't predefined,
        // bc each time backing array needs expansion the resources are taken to create new array and move the data.
        final var list = new LinkedList<YangModuleCapability>();
        final var deviationsMap = buildDeviationsMap(context);
        for (final var module : modules) {
            final var moduleNamespace = module.getNamespace().toString();
            final var moduleName = module.getName();
            final var revision = module.getRevision().map(Revision::toString).orElse(null);
            final var namespace = module.getQNameModule();
            final var features = module.getFeatures().stream()
                .map(FeatureDefinition::getQName)
                // ensure the features belong to same module
                .filter(featureName -> namespace.equals(featureName.getModule()))
                .map(featureName -> featureName.getLocalName())
                .toList();
            final var deviations = deviationsMap.get(namespace);
            list.add(new YangModuleCapability(moduleNamespace, moduleName, revision, features, deviations));
        }
        return ImmutableList.copyOf(list);
    }

    private static Map<QNameModule, List<String>> buildDeviationsMap(final EffectiveModelContext context) {
        final var result = new HashMap<QNameModule, List<String>>();
        for (final var module : context.getModules()) {
            if (module.getDeviations() == null || module.getDeviations().isEmpty()) {
                continue;
            }
            for (final var deviation : module.getDeviations()) {
                final var targetQname = deviation.getTargetPath().lastNodeIdentifier().getModule();
                result.computeIfAbsent(targetQname, key -> new LinkedList<>()).add(module.getName());
            }
        }
        return ImmutableMap.copyOf(result);
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
        for (final var parameter : parameters) {
            final var index = parameter.indexOf("=");
            result.put(parameter.substring(0, index), parameter.substring(index + 1));
        }
        return result;
    }
}
