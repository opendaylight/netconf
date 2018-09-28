/*
 * Copyright (c) 2018 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * RFC6470 notification blaster. The idea is to generate {@code netconf-config-change} notifications, and service
 * get-config requests for the elements reported therein.
 *
 * <p>
 * For purposes of simplification, this tool serves a static datastore content for the root {@code get-config} and
 * produces a single, pre-configured notification. When it fires off a notification, it then awaits a get-config,
 * to which it replies with a pre-configured content and generates another notification.
 *
 * <p>
 * This allows us to saturate, but not overload a client responding to those notifications.
 */
package org.opendaylight.netconf.test.tool.notif;