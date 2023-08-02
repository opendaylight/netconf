/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.yanglib.writer;

import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module.ConformanceType.Implement;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module.ConformanceType.Import;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.datastores.rev180214.Operational;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesStateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.RevisionIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibrary;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.CommonLeafs;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.module.Deviation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.module.DeviationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.module.DeviationKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.set.parameters.module.SubmoduleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.DatastoreBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.ModuleSetBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.SchemaBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yangtools.yang.binding.util.BindingMap;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.FeatureDefinition;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleLike;

/**
 * Utility class responsible for building ietf-yang-library content.
 */
// TODO: current artifact is part of integration with YangLibrarySupport from MDSAL project,
//  it expected to be removed as extra once YangLibrarySupport is fully supporting required functionality.
//  https://jira.opendaylight.org/browse/MDSAL-833
//  https://jira.opendaylight.org/browse/MDSAL-835
final class YangLibraryContentBuilderUtil {
    private static final CommonLeafs.Revision EMPTY_REVISION = new CommonLeafs.Revision("");

    static final String DEFAULT_MODULE_SET_NAME = "ODL_modules";
    static final String DEFAULT_SCHEMA_NAME = "ODL_schema";

    private YangLibraryContentBuilderUtil() {
        // utility class
    }

    /**
     * Builds ietf-yang-library content based on model context.
     *
     * @param context effective model context
     * @param urlProvider optional schema source URL provider
     * @return content as YangLibrary object
     */
    static YangLibrary buildYangLibrary(final @NonNull EffectiveModelContext context,
            final @NonNull String contentId, final @Nullable YangLibrarySchemaSourceUrlProvider urlProvider) {
        final var deviationsMap = getDeviationsMap(context);
        return new YangLibraryBuilder()
            .setModuleSet(BindingMap.of(new ModuleSetBuilder()
                .setName(DEFAULT_MODULE_SET_NAME)
                .setModule(context.getModules().stream()
                    .map(module -> buildModule(module, deviationsMap, urlProvider))
                    .collect(BindingMap.toMap())
                )
                .build()))
            .setSchema(BindingMap.of(new SchemaBuilder()
                .setName(DEFAULT_SCHEMA_NAME)
                .setModuleSet(Set.of(DEFAULT_MODULE_SET_NAME))
                .build()))
            .setDatastore(BindingMap.of(new DatastoreBuilder()
                .setName(Operational.VALUE)
                .setSchema(DEFAULT_SCHEMA_NAME)
                .build()))
            .setContentId(contentId)
            .build();
    }

    private static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104
        .module.set.parameters.@NonNull Module buildModule(final @NonNull Module module,
            final @NonNull  Map<QNameModule, Set<Module>> deviationsMap,
            final @Nullable YangLibrarySchemaSourceUrlProvider urlProvider) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library
            .rev190104.module.set.parameters.ModuleBuilder()
            .setName(buildModuleKeyName(module))
            .setRevision(buildRevision(module))
            .setNamespace(new Uri(module.getNamespace().toString()))
            .setFeature(buildFeatures(module).orElse(null))
            .setDeviation(buildDeviations(module, deviationsMap).orElse(null))
            .setLocation(buildSchemaSourceUrl(module, urlProvider).map(Set::of).orElse(null))
            .setSubmodule(module.getSubmodules().stream()
                .map(subModule -> new SubmoduleBuilder()
                    .setName(buildModuleKeyName(subModule))
                    .setRevision(buildRevision(subModule))
                    .setLocation(buildSchemaSourceUrl(subModule, urlProvider).map(Set::of).orElse(null))
                    .build())
                .collect(BindingMap.toMap()))
            .build();
    }

    /**
     * Builds ietf-yang-library legacy content based on model context.
     *
     * @param context effective model context
     * @param urlProvider optional schema source URL provider
     * @return content as ModulesState object
     * @deprecated due to model update via RFC 8525, the functionality serves backward compatibility.
     */
    @Deprecated
    static ModulesState buildModuleState(final @NonNull EffectiveModelContext context,
            final @NonNull String moduleSetId, final @Nullable YangLibrarySchemaSourceUrlProvider urlProvider) {
        final var deviationsMap = getDeviationsMap(context);
        return new ModulesStateBuilder()
            .setModule(context.getModules().stream()
                .map(module -> buildLegacyModule(module, deviationsMap, urlProvider))
                .collect(BindingMap.toMap()))
            .setModuleSetId(moduleSetId)
            .build();
    }

    private static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library
        .rev190104.module.list.@NonNull Module buildLegacyModule(final @NonNull Module module,
            final @NonNull  Map<QNameModule, Set<Module>> deviationsMap,
            final @Nullable YangLibrarySchemaSourceUrlProvider urlProvider) {

        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library
            .rev190104.module.list.ModuleBuilder()
            .setName(buildModuleKeyName(module))
            .setRevision(buildLegacyRevision(module))
            .setNamespace(new Uri(module.getNamespace().toString()))
            .setFeature(buildFeatures(module).orElse(null))
            .setSchema(buildSchemaSourceUrl(module, urlProvider).orElse(null))
            .setConformanceType(hasDeviations(module) ? Implement : Import)
            .setDeviation(buildLegacyDeviations(module, deviationsMap).orElse(null))
            .setSubmodule(module.getSubmodules().stream()
                .map(subModule -> new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library
                    .rev190104.module.list.module.SubmoduleBuilder()
                    .setName(buildModuleKeyName(subModule))
                    .setRevision(buildLegacyRevision(subModule))
                    .setSchema(buildSchemaSourceUrl(subModule, urlProvider).orElse(null))
                    .build())
                .collect(BindingMap.toMap()))
            .build();
    }

    private static RevisionIdentifier buildRevision(final ModuleLike module) {
        return module.getQNameModule().getRevision().map(rev -> new RevisionIdentifier(rev.toString())).orElse(null);
    }

    private static CommonLeafs.Revision buildLegacyRevision(final ModuleLike module) {
        return module.getQNameModule().getRevision()
            .map(rev -> new CommonLeafs.Revision(new RevisionIdentifier(rev.toString()))).orElse(EMPTY_REVISION);
    }

    private static YangIdentifier buildModuleKeyName(final ModuleLike module) {
        return new YangIdentifier(module.getName()
            + module.getQNameModule().getRevision().map(revision -> "_" + revision).orElse(""));
    }

    private static @NonNull Optional<Uri> buildSchemaSourceUrl(final @NonNull ModuleLike module,
            final @Nullable YangLibrarySchemaSourceUrlProvider urlProvider) {
        return urlProvider == null ? Optional.empty() :
            urlProvider.getSchemaSourceUrl(DEFAULT_MODULE_SET_NAME, module.getName(),
                module.getRevision().orElse(null));
    }

    private static Optional<Set<YangIdentifier>> buildFeatures(final ModuleLike module) {
        if (module.getFeatures() == null || module.getFeatures().isEmpty()) {
            return Optional.empty();
        }
        final var namespace = module.getQNameModule();
        final var features = module.getFeatures().stream()
            .map(FeatureDefinition::getQName)
            // ensure the features belong to same module
            .filter(featureName -> namespace.equals(featureName.getModule()))
            .map(featureName -> new YangIdentifier(featureName.getLocalName()))
            .collect(Collectors.toUnmodifiableSet());
        return features.isEmpty() ? Optional.empty() : Optional.of(features);
    }

    private static boolean hasDeviations(final Module module) {
        return module.getDeviations() != null && !module.getDeviations().isEmpty();
    }

    private static Optional<Set<YangIdentifier>> buildDeviations(final Module module,
            final Map<QNameModule, Set<Module>> deviationsMap) {
        final var deviationModules = deviationsMap.get(module.getQNameModule());
        if (deviationModules == null) {
            return Optional.empty();
        }
        return Optional.of(deviationModules.stream()
            .map(devModule -> new YangIdentifier(buildModuleKeyName(devModule)))
            .collect(ImmutableSet.toImmutableSet()));
    }

    private static Optional<Map<DeviationKey, Deviation>> buildLegacyDeviations(final Module module,
            final Map<QNameModule, Set<Module>> deviationsMap) {
        final var deviationModules = deviationsMap.get(module.getQNameModule());
        if (deviationModules == null) {
            return Optional.empty();
        }
        return Optional.of(deviationModules.stream()
            .map(devModule -> new DeviationBuilder()
                .setName(buildModuleKeyName(devModule))
                .setRevision(buildLegacyRevision(devModule))
                .build())
            .collect(BindingMap.toMap()));
    }

    private static @NonNull Map<QNameModule, Set<Module>> getDeviationsMap(final EffectiveModelContext context) {
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
}
