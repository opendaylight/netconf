/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.ListInstance;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.Step;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.odlext.model.api.OpenDaylightExtensionsStatements;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.stmt.DataTreeEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.KeyEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.ListEffectiveStatement;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * An {@link ApiPath} which has been (partially) bound to an {@link EffectiveModelContext}. Since a request can cross
 * into a context that is nested within that context, there may be additional segments, accessible via
 * {@link #nextSegment(EffectiveModelContext)}.
 */
@Beta
public final class ApiSegment implements Immutable {
    private final @NonNull Inference inference;
    private final @NonNull YangInstanceIdentifier path;
    private final @NonNull ImmutableList<Step> remaining;

    private ApiSegment(final Inference inference, final YangInstanceIdentifier path,
            final ImmutableList<Step> remaining) {
        this.inference = requireNonNull(inference);
        this.path = requireNonNull(path);
        this.remaining = requireNonNull(remaining);
    }

    public @NonNull Inference inference() {
        return inference;
    }

    public @NonNull YangInstanceIdentifier path() {
        return path;
    }

    public boolean hasNextSegment() {
        return !remaining.isEmpty();
    }

    public @NonNull ApiSegment nextSegment(final EffectiveModelContext nextModelContext) throws InvalidPathException {
        if (hasNextSegment()) {
            return create(nextModelContext, remaining);
        } else {
            throw new NoSuchElementException("No further segments available");
        }
    }

    static ApiSegment create(final EffectiveModelContext modelContext, final ImmutableList<Step> steps)
            throws InvalidPathException {
        final SchemaInferenceStack stack = SchemaInferenceStack.of(modelContext);
        if (steps.isEmpty()) {
            return new ApiSegment(stack.toInference(), YangInstanceIdentifier.empty(), ImmutableList.of());
        }

        // First step is somewhat special
        Step step = steps.get(0);
        final String firstModule = step.module();
        if (firstModule == null) {
            throw new InvalidPathException("First step does not specify a module");
        }
        QNameModule currentModule = resolveModule(modelContext, firstModule);

        final var path = YangInstanceIdentifier.builder();
        int idx = 0;
        while (true) {
            final QName qname = step.identifier().bindTo(currentModule);

            // We need to stop at mount point statements
            if (qname.equals(OpenDaylightExtensionsStatements.MOUNT.getStatementName())) {
                // FIXME: lookup statement in current state to make sure we bind correctly
                return new ApiSegment(stack.toInference(), path.build(), steps.subList(idx + 1, steps.size()));
            }

            final DataTreeEffectiveStatement<?> schema = stack.enterDataTree(qname);
            final PathArgument arg;
            if (schema instanceof ListEffectiveStatement) {
                final Iterator<QName> keyIt = schema.findFirstEffectiveSubstatementArgument(KeyEffectiveStatement.class)
                    // FIXME: dedicated exception
                    .orElseThrow()
                    .iterator();
                final Iterator<String> valueIt = step instanceof ListInstance
                    ? ((ListInstance) step).keyValues().iterator() : Collections.emptyIterator();

                // FIXME: index predicates and create NodeIdentifierWithPredicates

            } else {
                // FIXME: LeafListEffectiveStatement
                // FIXME: all other cases
            }

            if (++idx == steps.size()) {
                return new ApiSegment(stack.toInference(), path.build(), ImmutableList.of());
            }

            step = steps.get(idx);
            final String module = step.module();
            if (module != null) {
                currentModule = resolveModule(modelContext, module);
            }
        }

    }

    private static QNameModule resolveModule(final EffectiveModelContext modelContext, final String moduleName)
            throws InvalidPathException {
        final Collection<? extends @NonNull Module> modules = modelContext.findModules(moduleName);
        if (modules.isEmpty()) {
            throw new InvalidPathException("Cannot find module " + moduleName);
        }
        return modules.iterator().next().getQNameModule();
    }
}
