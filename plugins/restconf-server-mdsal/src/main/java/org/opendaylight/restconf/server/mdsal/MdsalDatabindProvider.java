/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static com.google.common.base.Verify.verifyNotNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.common.DatabindProvider;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * A {@link DatabindProvider} tracking a {@link DOMSchemaService}.
 */
@Singleton
@Component(service = { DatabindProvider.class, MdsalDatabindProvider.class })
public final class MdsalDatabindProvider implements DatabindProvider, AutoCloseable {
    private static final VarHandle CURRENT_DATABIND;

    static {
        try {
            CURRENT_DATABIND = MethodHandles.lookup()
                .findVarHandle(MdsalDatabindProvider.class, "currentDatabind", DatabindContext.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @Nullable YangTextSourceExtension sourceProvider;
    private final Registration reg;

    @SuppressFBWarnings(value = "URF_UNREAD_FIELD", justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    private volatile DatabindContext currentDatabind;

    @Inject
    @Activate
    public MdsalDatabindProvider(@Reference final DOMSchemaService schemaService) {
        sourceProvider = schemaService.extension(YangTextSourceExtension.class);
        currentDatabind = DatabindContext.ofModel(schemaService.getGlobalContext());
        reg = schemaService.registerSchemaContextListener(this::onModelContextUpdated);
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        reg.close();
    }

    @Override
    public DatabindContext currentDatabind() {
        return verifyNotNull((@NonNull DatabindContext) CURRENT_DATABIND.getAcquire(this));
    }

    @Nullable YangTextSourceExtension sourceProvider() {
        return sourceProvider;
    }

    private void onModelContextUpdated(final EffectiveModelContext newModelContext) {
        final var local = currentDatabind;
        if (!newModelContext.equals(local.modelContext())) {
            CURRENT_DATABIND.setRelease(this, DatabindContext.ofModel(newModelContext));
        }
    }
}
