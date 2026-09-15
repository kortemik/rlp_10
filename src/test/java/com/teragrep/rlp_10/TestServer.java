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
package com.teragrep.rlp_10;

import com.teragrep.net_01.channel.socket.PlainFactory;
import com.teragrep.net_01.eventloop.EventLoop;
import com.teragrep.net_01.eventloop.EventLoopFactory;
import com.teragrep.rlp_03.frame.FrameDelegationClockFactory;
import com.teragrep.rlp_03.frame.delegate.DefaultFrameDelegate;
import com.teragrep.net_01.server.ServerFactory;
import com.teragrep.rlp_10.config.*;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * These are a copy from rlp_03 test suite
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestServer.class);

    private EventLoop eventLoop;

    private ExecutorService executorService;

    private final AtomicLong atomicLong = new AtomicLong();

    @BeforeAll
    public void init() {
        final SocketAddressConfig socketAddressConfig = new SocketAddressConfig();

        final EventLoopFactory eventLoopFactory = new EventLoopFactory();
        Assertions.assertDoesNotThrow(() -> eventLoop = eventLoopFactory.create());

        executorService = Executors.newVirtualThreadPerTaskExecutor();

        executorService.submit(eventLoop);

        final ServerFactory serverFactory = new ServerFactory(
                eventLoop,
                executorService,
                new PlainFactory(),
                new FrameDelegationClockFactory(() -> new DefaultFrameDelegate((frame) -> atomicLong.incrementAndGet()))
        );
        Assertions.assertDoesNotThrow(() -> serverFactory.create(socketAddressConfig.port()));
    }

    @AfterAll
    public void cleanup() {
        eventLoop.stop();
        executorService.shutdown();
    }

    @AfterEach
    public void clearMessageList() {
        // clear received list
        atomicLong.set(0);
    }

    @Test
    public void testMessageCount() {
        final int clients = 50;
        final long messageCount = 300000;
        final int retryTransmissionCount = 3;
        final int retryConnectionCount = 3;
        final InitiatorConfig initiatorConfig = new InitiatorConfig(
                clients,
                retryTransmissionCount,
                retryConnectionCount
        );
        final MetricsConfig metricsConfiguration = new MetricsConfig(10000);
        final PrometheusConfig prometheusConfiguration = new PrometheusConfig(8080);
        final TimeoutConfig timeoutConfiguration = new TimeoutConfig();
        final TransportConfig transportConfiguration = new TransportConfig();
        final RecordStreamConfig recordStreamConfig = new RecordStreamConfig(messageCount);
        final ReportConfig reportConfig = new ReportConfig(1000, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
        final SocketAddressConfig socketAddressConfig = new SocketAddressConfig();
        final DelayConfig delayConfig = new DelayConfig();
        final SyslogConfig syslogConfig = new SyslogConfig();
        final Benchmark benchmark = new Benchmark(
                initiatorConfig,
                metricsConfiguration,
                prometheusConfiguration,
                timeoutConfiguration,
                transportConfiguration,
                recordStreamConfig,
                reportConfig,
                socketAddressConfig,
                delayConfig,
                syslogConfig
        );
        benchmark.startBenchmark();
        Assertions.assertTrue(atomicLong.get() != 0);
        Assertions.assertEquals(messageCount, atomicLong.get());
    }

    @Test
    public void testPrometheusServer() throws InterruptedException {
        final int clients = 50;
        final int messageCount = 20000;
        final int retryTransmissionCount = 3;
        final int retryConnectionCount = 3;
        final InitiatorConfig initiatorConfig = new InitiatorConfig(
                clients,
                retryTransmissionCount,
                retryConnectionCount
        );
        final MetricsConfig metricsConfiguration = new MetricsConfig(10000);
        final PrometheusConfig prometheusConfiguration = new PrometheusConfig(8080);
        final TimeoutConfig timeoutConfiguration = new TimeoutConfig();
        final TransportConfig transportConfiguration = new TransportConfig();
        final RecordStreamConfig recordStreamConfig = new RecordStreamConfig(messageCount);
        final ReportConfig reportConfig = new ReportConfig(1000, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
        final SocketAddressConfig socketAddressConfig = new SocketAddressConfig();
        final DelayConfig delayConfig = new DelayConfig();
        final SyslogConfig syslogConfig = new SyslogConfig();
        final Benchmark benchmark = new Benchmark(
                initiatorConfig,
                metricsConfiguration,
                prometheusConfiguration,
                timeoutConfiguration,
                transportConfiguration,
                recordStreamConfig,
                reportConfig,
                socketAddressConfig,
                delayConfig,
                syslogConfig
        );
        final Thread benchMarkThread = new Thread(() -> benchmark.startBenchmark());
        benchMarkThread.start();
        final HttpClient client = HttpClient.newHttpClient();
        final HttpRequest request = HttpRequest
                .newBuilder()
                .uri(URI.create("http://localhost:" + prometheusConfiguration.port() + "/metrics"))
                .GET()
                .build();

        // send a GET request to prometheus URL. Expect to receive a response containing each ot the metrics.
        final HttpResponse<String> response = Assertions
                .assertDoesNotThrow(() -> client.send(request, HttpResponse.BodyHandlers.ofString()));

        benchMarkThread.join();
        // assert that HTTP response contains information about each metric in prometheus format
        Assertions
                .assertTrue(
                        response
                                .body()
                                .contains(
                                        "# HELP connects Generated from Dropwizard metric import (metric=connects, type=com.codahale.metrics.Counter)"
                                )
                );
        Assertions
                .assertTrue(
                        response
                                .body()
                                .contains(
                                        "# HELP disconnects Generated from Dropwizard metric import (metric=disconnects, type=com.codahale.metrics.Counter)"
                                )
                );
        Assertions
                .assertTrue(
                        response
                                .body()
                                .contains(
                                        "# HELP records Generated from Dropwizard metric import (metric=records, type=com.codahale.metrics.Counter)"
                                )
                );
        Assertions
                .assertTrue(
                        response
                                .body()
                                .contains(
                                        "# HELP connectLatency Generated from Dropwizard metric import (metric=connectLatency, type=com.codahale.metrics.Timer)"
                                )
                );
        Assertions
                .assertTrue(
                        response
                                .body()
                                .contains(
                                        "# HELP transactionLatency Generated from Dropwizard metric import (metric=transactionLatency, type=com.codahale.metrics.Timer)"
                                )
                );
        Assertions
                .assertTrue(
                        response
                                .body()
                                .contains(
                                        "# HELP transmitLatency Generated from Dropwizard metric import (metric=transmitLatency, type=com.codahale.metrics.Timer)"
                                )
                );
        Assertions
                .assertTrue(
                        response
                                .body()
                                .contains(
                                        "# HELP receiveLatency Generated from Dropwizard metric import (metric=receiveLatency, type=com.codahale.metrics.Timer)"
                                )
                );
        Assertions
                .assertTrue(
                        response
                                .body()
                                .contains(
                                        "# HELP retriedConnects Generated from Dropwizard metric import (metric=retriedConnects, type=com.codahale.metrics.Counter)"
                                )
                );
        Assertions
                .assertTrue(
                        response
                                .body()
                                .contains(
                                        "# HELP resends Generated from Dropwizard metric import (metric=resends, type=com.codahale.metrics.Counter)"
                                )
                );

        // each metric should have proper type
        Assertions.assertTrue(response.body().contains("# TYPE connects gauge"));
        Assertions.assertTrue(response.body().contains("# TYPE disconnects gauge"));
        Assertions.assertTrue(response.body().contains("# TYPE records gauge"));
        Assertions.assertTrue(response.body().contains("# TYPE connectLatency summary"));
        Assertions.assertTrue(response.body().contains("# TYPE transactionLatency summary"));
        Assertions.assertTrue(response.body().contains("# TYPE transmitLatency summary"));
        Assertions.assertTrue(response.body().contains("# TYPE receiveLatency summary"));
        Assertions.assertTrue(response.body().contains("# TYPE retriedConnects gauge"));
        Assertions.assertTrue(response.body().contains("# TYPE resends gauge"));
    }

}
