/*
 * Copyright (c) 2019 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.web;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.ServletException;
import org.opendaylight.aaa.web.WebContext;
import org.opendaylight.aaa.web.WebContextRegistration;
import org.opendaylight.aaa.web.WebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Beta
public abstract class AbstractWebRegistrar implements WebRegistrar, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractWebRegistrar.class);

    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final WebServer webServer;

    private volatile WebContextRegistration registraton;

    public AbstractWebRegistrar(WebServer webServer) {
        this.webServer = requireNonNull(webServer);
    }

    @Override
    public final void registerWithAuthentication() {
        register(true);
    }

    @Override
    public final void registerWithoutAuthentication() {
        register(false);
    }

    @Override
    public final void close() {
        if (registered.compareAndSet(true, false)) {
            if (registraton != null) {
                registraton.close();
            }
        }
    }

    private void register(boolean authenticate) {
        if (!registered.compareAndSet(false, true)) {
            LOG.warn("Web context has already been registered", new Exception("call site"));
            return;
        }

        final WebContext webContext = createWebContext(authenticate);

        try {
            registraton = webServer.registerWebContext(webContext);
        } catch (ServletException e) {
            throw new RuntimeException("Failed to register the web context", e);
        }
    }

    protected abstract WebContext createWebContext(boolean authenticate);
}
