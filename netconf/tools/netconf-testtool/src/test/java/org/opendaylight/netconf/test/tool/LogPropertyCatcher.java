/*
 * Copyright (C) 2019 Ericsson Software Technology AB. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool;

import static org.slf4j.Logger.ROOT_LOGGER_NAME;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.rules.ExternalResource;
import org.slf4j.LoggerFactory;

/**
 * JUnit Rule that captures a pattern-matching property from log messages. Every time a log messages matches a given
 * pattern, the last capturing group will be saved, and can be later retrieved
 * via {@link LogPropertyCatcher#getLastValue()}.
 *
 * @see <a href="https://github.com/junit-team/junit4/wiki/Rules">Rules · junit-team/junit4 Wiki</a>
 */
class LogPropertyCatcher extends ExternalResource {

    private final ListAppender appender;

    LogPropertyCatcher(Pattern pattern) {
        this.appender = new ListAppender(pattern);
    }

    @Override
    protected void before() {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(ROOT_LOGGER_NAME);
        appender.clear();
        rootLogger.addAppender(appender);
        appender.start();
    }

    @Override
    protected void after() {
        appender.stop();
        Logger rootLogger = (Logger) LoggerFactory.getLogger(ROOT_LOGGER_NAME);
        rootLogger.detachAppender(appender);
    }

    /**
     * Retrieves the last captured property.
     *
     * @return The last value captured, or Optional.empty() if no log messages matched the pattern.
     */
    Optional<String> getLastValue() {
        return Optional.ofNullable(appender.lastValue);
    }

    private static final class ListAppender extends AppenderBase<ILoggingEvent> {

        private final Pattern pattern;

        private String lastValue = null;

        private ListAppender(Pattern pattern) {
            this.pattern = pattern;
        }

        protected void append(ILoggingEvent evt) {
            String msg = evt.getFormattedMessage();
            Matcher matcher = pattern.matcher(msg);
            if (matcher.find()) {
                lastValue = matcher.group(matcher.groupCount());
            }
        }

        void clear() {
            this.lastValue = null;
        }
    }
}
