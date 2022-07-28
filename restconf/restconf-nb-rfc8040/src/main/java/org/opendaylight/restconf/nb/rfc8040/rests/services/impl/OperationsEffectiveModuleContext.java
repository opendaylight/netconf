/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.spi.SimpleSchemaContext;

@Deprecated(forRemoval = true, since = "4.0.0")
final class OperationsEffectiveModuleContext extends SimpleSchemaContext implements EffectiveModelContext {
    private final ImmutableMap<QNameModule, ModuleEffectiveStatement> modules;

    OperationsEffectiveModuleContext(final Set<Module> modules) {
        super(modules);
        this.modules = modules.stream()
                .map(module -> {
                    verify(module instanceof ModuleEffectiveStatement, "Module %s is not an effective statement");
                    return (ModuleEffectiveStatement) module;
                })
                .collect(ImmutableMap.toImmutableMap(ModuleEffectiveStatement::localQNameModule, Function.identity()));
    }

    @Override
    public Map<QNameModule, ModuleEffectiveStatement> getModuleStatements() {
        return modules;
    }
}
