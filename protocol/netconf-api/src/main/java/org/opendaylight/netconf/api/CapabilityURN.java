/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The contents of
 * <a href="https://www.iana.org/assignments/netconf-capability-urns/netconf-capability-urns.xhtml">
 * Network Configuration Protocol (NETCONF) Capability URNs</a> IANA registry as well as any other capabilities we
 * explicitly recognize.
 *
 * <p>
 * The basic concept of Capabilities is defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc6241#section-8">RFC6241 section 8</a>. While that section mentions
 * capabilities are identified by URIs in general, the "identification" part is done through URNs, which are extended
 * to URIs during negotiation by adding a query part where applicable.
 */
@NonNullByDefault
public final class CapabilityURN {
    /**
     * The base NETCONF capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc4741.html#section-8.1">RFC4741, section 8.1</a>.
     * @deprecated This capability identifies legacy NETCONF devices and has been superseded by {@link #BASE_1_1}, just
     *             as RFC6241 obsoletes RFC4741.
     */
    @Deprecated
    public static final String BASE = "urn:ietf:params:netconf:base:1.0";
    /**
     * The base NETCONF capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.1">RFC6241, section 8.1</a>.
     */
    public static final String BASE_1_1 = "urn:ietf:params:netconf:base:1.1";
    /**
     * The Candidate Configuration Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.3">RFC6241, section 8.3</a>.
     */
    public static final String CANDIDATE = "urn:ietf:params:netconf:capability:candidate:1.0";
    /**
     * The Candidate Configuration Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc4741.html#section-8.3">RFC4741, section 8.3</a>.
     * @deprecated This capability is superseded by {@link #CONFIRMED_COMMIT_1_1}.
     */
    @Deprecated
    public static final String CONFIRMED_COMMIT = "urn:ietf:params:netconf:capability:confirmed-commit:1.0";
    /**
     * The Rollback-on-Error Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.4">RFC6241, section 8.4</a>.
     */
    public static final String CONFIRMED_COMMIT_1_1 = "urn:ietf:params:netconf:capability:confirmed-commit:1.1";
    /**
     * The EXI Capability, as defined in
     * <a href="https://datatracker.ietf.org/doc/html/draft-varga-netconf-exi-capability-01#section-3">
     * draft-varga-netconf-exi-capability-01, section 3</a>. Note this is an expired IETF draft capability and subject
     * to change.
     */
    @Beta
    public static final String EXI = "urn:ietf:params:netconf:capability:exi:1.0";
    /**
     * The Interleave Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc5277.html#section-6">RFC5277, section 6</a>.
     */
    public static final String INTERLEAVE = "urn:ietf:params:netconf:capability:interleave:1.0";
    /**
     * The Validate Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc5277.html#section-3.1">RFC5277, section 3.1</a>.
     */
    public static final String NOTIFICATION = "urn:ietf:params:netconf:capability:notification:1.0";
    /**
     * The Partial Locking Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc5717.html#section-2">RFC5715, section 2</a>.
     */
    public static final String PARTIAL_LOCK = "urn:ietf:params:netconf:capability:partial-lock:1.0";
    /**
     * The Rollback-on-Error Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.5">RFC6241, section 8.5</a>.
     */
    public static final String ROLLBACK_ON_ERROR = "urn:ietf:params:netconf:capability:rollback-on-error:1.0";
    /**
     * The Distinct Startup Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.7">RFC6241, section 8.7</a>.
     */
    public static final String STARTUP = "urn:ietf:params:netconf:capability:startup:1.0";
    /**
     * The Time Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc7758.html#section-4">RFC7758, section 4</a>.
     */
    public static final String TIME = "urn:ietf:params:netconf:capability:time:1.0";
    /**
     * The URL Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.8">RFC6241, section 8.8</a>.
     */
    public static final String URL = "urn:ietf:params:netconf:capability:url:1.0";
    /**
     * The Validate Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc4741.html#section-8.6">RFC4741, section 8.6</a>.
     * @deprecated This capability is superseded by {@link #VALIDATE_1_1}.
     */
    @Deprecated
    public static final String VALIDATE = "urn:ietf:params:netconf:capability:validate:1.0";
    /**
     * The Validate Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.6">RFC6241, section 8.6</a>.
     */
    public static final String VALIDATE_1_1 = "urn:ietf:params:netconf:capability:validate:1.1";
    /**
     * The With-defaults Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6243.html#section-4">RFC6243, section 4</a>.
     */
    public static final String WITH_DEFAULTS = "urn:ietf:params:netconf:capability:with-defaults:1.0";
    /**
     * The With-defaults Capability, as augmented by
     * <a href="https://www.rfc-editor.org/rfc/rfc8526#section-3.1.1.2">RFC8526, section 3.1.1.2</a>.
     */
    public static final String WITH_OPERATIONAL_DEFAULTS =
        "urn:ietf:params:netconf:capability:with-operational-defaults:1.0";
    /**
     * The Writable-Running Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.2">RFC6241, section 8.2</a>.
     */
    public static final String WRITABLE_RUNNING = "urn:ietf:params:netconf:capability:writable-running:1.0";
    /**
     * The XPath Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.9">RFC6241, section 8.9</a>.
     */
    public static final String XPATH = "urn:ietf:params:netconf:capability:xpath:1.0";
    /**
     * The YANG Module Library Capability, as defined in
     * <a href="hhttps://www.rfc-editor.org/rfc/rfc7950.html#section-5.6.4">RFC7950, section 5.6.4</a> and further
     * specified by <a href="https://www.rfc-editor.org/rfc/rfc7895">RFC7895</a>. Note this applies to NETCONF endpoints
     * which DO NOT support Network Management Datastore Architecture as specified by
     * <a href="https://www.rfc-editor.org/rfc/rfc8342">RFC8342</a>.
     */
    public static final String YANG_LIBRARY = "urn:ietf:params:netconf:capability:yang-library:1.0";
    /**
     * The YANG Library Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8526.html#section-2">RFC8526, section 2</a> and further specified
     * by <a href="https://www.rfc-editor.org/rfc/rfc8525">RFC8525</a>. Note this applies to NETCONF endpoints
     * which DO support Network Management Datastore Architecture as specified by
     * <a href="https://www.rfc-editor.org/rfc/rfc8342">RFC8342</a>.
     */
    public static final String YANG_LIBRARY_1_1 = "urn:ietf:params:netconf:capability:yang-library:1.1";

    private CapabilityURN() {
        // Hidden on purpose
    }
}
