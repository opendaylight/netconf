/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.io.CharSource;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.SourceRepresentation;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.api.source.YinTextSource;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SubmoduleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.export.DeclaredStatementFormatter;
import org.opendaylight.yangtools.yang.model.export.YinExportUtils;

/**
 * A {@link ServerModulesOperations} implementation based on {@link YinExportUtils} and
 * {@link DeclaredStatementFormatter}.
 */
public final class ExportingServerModulesOperations implements ServerModulesOperations {
    private final EffectiveModelContext modelContext;

    public ExportingServerModulesOperations(final EffectiveModelContext modelContext) {
        this.modelContext = requireNonNull(modelContext);
    }

    @Override
    public void getModelSource(final ServerRequest<ModulesGetResult> request, final SourceIdentifier source,
            final Class<? extends SourceRepresentation> representation) {
        // TODO Auto-generated method stub
        if (YangTextSource.class.isAssignableFrom(representation)) {
            exportSource(request, source, YangCharSource::new, YangCharSource::new);
        } else if (YinTextSource.class.isAssignableFrom(representation)) {
            exportSource(request, source, YinCharSource.OfModule::new, YinCharSource.OfSubmodule::new);
        } else {
            request.failWith(new RequestException("Unsupported source representation " + representation.getName()));
        }
    }

    private void exportSource(final ServerRequest<ModulesGetResult> request, final SourceIdentifier source,
            final Function<ModuleEffectiveStatement, CharSource> moduleCtor,
            final BiFunction<ModuleEffectiveStatement, SubmoduleEffectiveStatement, CharSource> submoduleCtor) {
        // If the source identifies a module, things are easy
        final var name = source.name().getLocalName();
        final var optRevision = Optional.ofNullable(source.revision());
        final var optModule = modelContext.findModule(name, optRevision);
        if (optModule.isPresent()) {
            request.completeWith(new ModulesGetResult(
                moduleCtor.apply(optModule.orElseThrow().asEffectiveStatement())));
            return;
        }

        // The source could be a submodule, which we need to hunt down
        for (var module : modelContext.getModules()) {
            for (var submodule : module.getSubmodules()) {
                if (name.equals(submodule.getName()) && optRevision.equals(submodule.getRevision())) {
                    request.completeWith(new ModulesGetResult(submoduleCtor.apply(module.asEffectiveStatement(),
                        submodule.asEffectiveStatement())));
                    return;
                }
            }
        }

        final var sb = new StringBuilder().append("Source ").append(source.name().getLocalName());
        optRevision.ifPresent(rev -> sb.append('@').append(rev));
        sb.append(" not found");
        request.failWith(new RequestException(ErrorType.APPLICATION, ErrorTag.DATA_MISSING, sb.toString()));
    }
}
