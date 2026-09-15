/*
 * Teragrep performance test application for RELP (rlp_10)
 * Copyright (C) 2026 Suomen Kanuuna Oy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 * Additional permission under GNU Affero General Public License version 3
 * section 7
 *
 * If you modify this Program, or any covered work, by linking or combining it
 * with other code, such other code is not for that reason alone subject to any
 * of the requirements of the GNU Affero GPL version 3 as long as this Program
 * is the same Program as licensed from Suomen Kanuuna Oy without any additional
 * modifications.
 *
 * Supplemented terms under GNU Affero General Public License version 3
 * section 7
 *
 * Origin of the software must be attributed to Suomen Kanuuna Oy. Any modified
 * versions must be marked as "Modified version of" The Program.
 *
 * Names of the licensors and authors may not be used for publicity purposes.
 *
 * No rights are granted for use of trade names, trademarks, or service marks
 * which are in The Program if any.
 *
 * Licensee must indemnify licensors and authors for any liability that these
 * contractual assumptions impose on licensors and authors.
 *
 * To the extent this program is licensed as part of the Commercial versions of
 * Teragrep, the applicable Commercial License may apply to this file if you as
 * a licensee so wish it.
 */
package com.teragrep.rlp_10.report;

import com.codahale.metrics.MetricRegistry;
import com.teragrep.rlp_10.config.PrometheusConfig;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.exporter.MetricsServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrometheusMetricsReport implements MetricsReport {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrometheusMetricsReport.class);
    private final MetricRegistry registry;
    private final Server prometheusMetricsServer;

    public PrometheusMetricsReport(final MetricRegistry registry) {
        this(registry, new PrometheusConfig());
    }

    public PrometheusMetricsReport(final MetricRegistry registry, final PrometheusConfig prometheusConfiguration) {
        this.registry = registry;
        this.prometheusMetricsServer = new Server(prometheusConfiguration.port());
    }

    @Override
    public void start() {
        CollectorRegistry.defaultRegistry.register(new DropwizardExports(registry));

        final ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        prometheusMetricsServer.setHandler(context);

        final MetricsServlet metricsServlet = new MetricsServlet();
        final ServletHolder servletHolder = new ServletHolder(metricsServlet);
        context.addServlet(servletHolder, "/metrics");
        try {
            prometheusMetricsServer.start();
        }
        //CHECKSTYLE:OFF
        catch (final Exception e) {
            LOGGER.error("Failed to start Prometheus reporting server!");
        }
        //CHECKSTYLE:ON
    }

    @Override
    public void stop() {
        try {
            prometheusMetricsServer.stop();
        }
        //CHECKSTYLE:OFF
        catch (final Exception e) {
            LOGGER.error("Failed to stop Prometheus reporting server!");
        }
        //CHECKSTYLE:ON
    }
}
