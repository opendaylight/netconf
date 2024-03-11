/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.shiro.ShiroException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.session.UnknownSessionException;
import org.apache.shiro.subject.Subject;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.aaa.shiro.web.env.AAAShiroWebEnvironment;
import org.opendaylight.netconf.transport.http.AbstractBasicAuthHandler;
import org.opendaylight.netconf.transport.http.AuthHandler;
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
@Component(immediate = true)
public final class AAAShiroPrincipalService implements PrincipalService {
    private static final Logger LOG = LoggerFactory.getLogger(AAAShiroPrincipalService.class);
    private static final String PRINCIPAL_UUID_HEADER = "x-restconf-principal-uuid";
    private final SecurityManager securityManager;
    private final Map<UUID, Principal> principalMap = new ConcurrentHashMap<>();

    @Activate
    public AAAShiroPrincipalService(@Reference final AAAShiroWebEnvironment env) {
        requireNonNull(env);
        this.securityManager = requireNonNull(env.getSecurityManager());
    }

    @Inject
    public AAAShiroPrincipalService(final SecurityManager securityManager) {
        this.securityManager = requireNonNull(securityManager);
    }

    @Override
    public @Nullable Principal acquirePrincipal(final FullHttpRequest request) {
        final var uuidStr = request.headers().get(PRINCIPAL_UUID_HEADER);
        UUID uuid = null;
        try {
            uuid = uuidStr == null ? null : UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            // ignore invalid value
        }
        return uuid == null ? null : principalMap.remove(uuid);
    }

    @Override
    public AuthHandler<?> create() {
        return new AbstractBasicAuthHandler<Subject>() {
            @Override
            public Subject authenticate(final HttpRequest request) {
                final var subject = super.authenticate(request);
                if (subject != null && subject.getPrincipal() instanceof Principal principal) {
                    final var uuid = UUID.randomUUID();
                    principalMap.put(uuid, principal);
                    request.headers().set(PRINCIPAL_UUID_HEADER, uuid);
                } else {
                    // ensure header is absent
                    request.headers().remove(PRINCIPAL_UUID_HEADER);
                }
                return subject;
            }

            @Override
            protected Subject authenticate(final String username, final String password) {
                final var upt = new UsernamePasswordToken();
                upt.setUsername(username);
                upt.setPassword(password.toCharArray());
                final var subject = new Subject.Builder(securityManager).buildSubject();
                try {
                    return login(subject, upt);
                } catch (UnknownSessionException e) {
                    LOG.debug("Could not log in {} - logging out and retrying...", upt, e);
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
            LOG.debug("Could not log out {}", subject, e);
        }
    }

    private static Subject login(final Subject subject, final UsernamePasswordToken upt) {
        try {
            subject.login(upt);
        } catch (AuthenticationException e) {
            LOG.trace("Could not authenticate the subject: {}", subject, e);
            return null;
        }
        return subject;
    }
}
