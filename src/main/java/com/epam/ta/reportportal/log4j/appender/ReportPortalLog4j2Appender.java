/*
 * Copyright 2017 EPAM Systems
 *
 *
 * This file is part of EPAM Report Portal.
 * https://github.com/reportportal/logger-java-log4j
 *
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.epam.ta.reportportal.log4j.appender;

import com.epam.reportportal.message.ReportPortalMessage;
import com.epam.reportportal.message.TypeAwareByteSource;
import com.epam.reportportal.service.ReportPortal;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectMessage;
import rp.com.google.common.base.Charsets;
import rp.com.google.common.base.Function;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

import static com.epam.reportportal.utils.MimeTypeDetector.detect;
import static rp.com.google.common.io.Files.asByteSource;

/**
 * Log4j2 appender for report portal
 *
 * @author Dzmitry_Kavalets
 */
@Plugin(name = "ReportPortalLog4j2Appender", category = "Core", elementType = "appender", printObject = true)
public class ReportPortalLog4j2Appender extends AbstractAppender {

    protected ReportPortalLog4j2Appender(String name, Filter filter, Layout<? extends Serializable> layout) {
        super(name, filter, layout);
    }

    @PluginFactory
    public static ReportPortalLog4j2Appender createAppender(@PluginAttribute("name") String name,
            @PluginElement("filter") Filter filter,
            @PluginElement("layout") Layout<? extends Serializable> layout) {

        if (name == null) {
            LOGGER.error("No name provided for ReportPortalLog4j2Appender");
            return null;
        }

        if (layout == null) {
            LOGGER.error("No layout provided for ReportPortalLog4j2Appender");
            return null;
        }
        return new ReportPortalLog4j2Appender(name, filter, layout);
    }

    @Override
    public void append(final LogEvent logEvent) {

        final LogEvent event = logEvent.toImmutable();
        if (null == event.getMessage()) {
            return;
        }
        //make sure we are not logging themselves
        if (Util.isInternal(event.getLoggerName())) {
            return;
        }

        ReportPortal.emitLog(new Function<String, SaveLogRQ>() {
            @Override
            public SaveLogRQ apply(String itemId) {
                SaveLogRQ rq = new SaveLogRQ();
                rq.setTestItemId(itemId);
                rq.setLogTime(new Date(event.getTimeMillis()));
                rq.setLevel(event.getLevel().name());

                Message eventMessage = event.getMessage();

                TypeAwareByteSource byteSource = null;
                String message = "";

                try {
                    Object[] parameters = eventMessage.getParameters();
                    if (null != parameters && parameters.length > 0) {

                        Object objectMessage = eventMessage.getParameters()[0];

                        if (objectMessage instanceof ReportPortalMessage) {
                            ReportPortalMessage rpMessage = (ReportPortalMessage) objectMessage;
                            byteSource = rpMessage.getData();
                            message = rpMessage.getMessage();
                        } else if (objectMessage instanceof File) {
                            final File file = (File) event.getMessage();
                            byteSource = new TypeAwareByteSource(asByteSource(file), detect(file));
                            message = "File reported";

                        } else {
                            if (null != objectMessage) {
                                message = objectMessage.toString();
                            }
                        }

                    } else if (Util.MESSAGE_PARSER.supports(eventMessage.getFormattedMessage())) {
                        ReportPortalMessage rpMessage = Util.MESSAGE_PARSER.parse(eventMessage.getFormattedMessage());
                        message = rpMessage.getMessage();
                        byteSource = rpMessage.getData();
                    } else {
                        message = new String(getLayout().toByteArray(event), Charsets.UTF_8);
                    }

                    if (null != byteSource) {
                        SaveLogRQ.File file = new SaveLogRQ.File();
                        file.setName(UUID.randomUUID().toString());
                        file.setContentType(byteSource.getMediaType());
                        file.setContent(byteSource.read());

                        rq.setFile(file);
                    }
                } catch (IOException e) {
                    //skip an error. There is some issue with binary data reading
                }
                rq.setMessage(message);

                return rq;
            }
        });

    }

}
