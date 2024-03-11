/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelHandler;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.shiro.ShiroException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.session.UnknownSessionException;
import org.apache.shiro.subject.Subject;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.shiro.web.env.AAAShiroWebEnvironment;
import org.opendaylight.netconf.transport.http.AbstractBasicAuthHandler;
import org.opendaylight.netconf.transport.http.AuthHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AAA services integration.
 * Replicates the behavior of org.opendaylight.aaa.authenticator.ODLAuthenticator with no usage of servlet API.
 */
@Singleton
@Component(immediate = true, service = AuthHandlerFactory.class, property = "type=restconf-server")
public final class AAAShiroAuthHandlerFactory implements AuthHandlerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(AAAShiroAuthHandlerFactory.class);
    final AAAShiroWebEnvironment env;

    @Activate
    @Inject
    public AAAShiroAuthHandlerFactory(@Reference final AAAShiroWebEnvironment env) {
        this.env = requireNonNull(env);
    }

    @Override
    public @NonNull ChannelHandler newAuthHandler() {
        return new AbstractBasicAuthHandler() {

            @Override
            protected boolean isAuthorized(final String uri, final String username, final String password) {
                final var upt = new UsernamePasswordToken();
                upt.setUsername(username);
                upt.setPassword(password.toCharArray());
                final var subject = new Subject.Builder(env.getSecurityManager()).buildSubject();
                try {
                    return login(subject, upt);
                } catch (UnknownSessionException e) {
                    LOG.debug("Couldn't log in {} - logging out and retrying...", upt, e);
                    logout(subject);
                    return login(subject, upt);
                }
            }
        };
    }

    private static void logout(final Subject subject) {
        try {
            subject.logout();
            final var session = subject.getSession(false);
            if (session != null) {
                session.stop();
            }
        } catch (ShiroException e) {
            LOG.debug("Couldn't log out {}", subject, e);
        }
    }

    private static boolean login(final Subject subject, final UsernamePasswordToken upt) {
        try {
            subject.login(upt);
        } catch (AuthenticationException e) {
            LOG.trace("Couldn't authenticate the subject: {}", subject, e);
            return false;
        }
        return true;
    }
}
