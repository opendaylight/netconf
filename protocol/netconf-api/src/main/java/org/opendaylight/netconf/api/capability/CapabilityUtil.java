/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.FeatureDefinition;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleLike;

public final class CapabilityUtil {

    private static final @NonNull String MODULE_PARAM = "module";
    private static final @NonNull String REVISION_PARAM = "revision";
    private static final @NonNull String FEATURES_PARAM = "features";
    private static final @NonNull String DEVIATIONS_PARAM = "deviations";
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

    public static List<YangModuleCapability> extractYangModuleCapabilities(EffectiveModelContext context) {
        final var result = new ArrayList<YangModuleCapability>();
        final var deviationsMap = buildDeviationsMap(context);
        for (final var module : context.getModules()) {
            final var moduleNamespace = module.getNamespace().toString();
            final var moduleName = module.getName();
            final var revision = module.getRevision().isPresent()
                ? module.getRevision().orElseThrow().toString() : null;
            final var features = getFeatures(module).orElse(null);
            final var deviations = getDeviations(module, deviationsMap).orElse(null);
            result.add(new YangModuleCapability(moduleNamespace, moduleName, revision, features, deviations));
        }
        return result;
    }

    private static String buildModuleKeyName(final ModuleLike module) {
        return module.getName() + module.getQNameModule().getRevision().map(revision -> "_" + revision).orElse("");
    }

    private static Optional<List<String>> getDeviations(final Module module,
        final Map<QNameModule, Set<Module>> deviationsMap) {
        final var deviationModules = deviationsMap.get(module.getQNameModule());
        if (deviationModules == null) {
            return Optional.empty();
        }
        return Optional.of(deviationModules.stream()
            .map(devModule -> buildModuleKeyName(devModule))
            .collect(Collectors.toUnmodifiableList()));
    }

    private static Map<QNameModule, Set<Module>> buildDeviationsMap(EffectiveModelContext context) {
        final var result = new HashMap<QNameModule, Set<Module>>();
        for (final var module : context.getModules()) {
            if (module.getDeviations() == null || module.getDeviations().isEmpty()) {
                continue;
            }
            for (final var deviation : module.getDeviations()) {
                final var targetQname = deviation.getTargetPath().lastNodeIdentifier().getModule();
                result.computeIfAbsent(targetQname, key -> new HashSet<>()).add(module);
            }
        }
        return ImmutableMap.copyOf(result);
    }

    private static Optional<List<String>> getFeatures(final ModuleLike module) {
        if (module.getFeatures() == null || module.getFeatures().isEmpty()) {
            return Optional.empty();
        }
        final var namespace = module.getQNameModule();
        final var features = module.getFeatures().stream()
            .map(FeatureDefinition::getQName)
            // ensure the features belong to same module
            .filter(featureName -> namespace.equals(featureName.getModule()))
            .map(featureName -> featureName.getLocalName())
            .collect(Collectors.toUnmodifiableList());
        return features.isEmpty() ? Optional.empty() : Optional.of(features);
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
