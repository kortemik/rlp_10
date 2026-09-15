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

import com.teragrep.net_01.channel.socket.TLSFactory;
import com.teragrep.net_01.eventloop.EventLoop;
import com.teragrep.net_01.eventloop.EventLoopFactory;
import com.teragrep.net_01.server.ServerFactory;
import com.teragrep.rlp_03.frame.FrameDelegationClockFactory;
import com.teragrep.rlp_03.frame.delegate.DefaultFrameDelegate;
import com.teragrep.rlp_10.config.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class TLSServerEnabledTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestTLSServer.class);

    private EventLoop eventLoop;

    private ExecutorService executorService;

    @Test
    @EnabledIfSystemProperty(
            named = "runServer",
            matches = "true"
    )
    public void runServer() {
        final SocketAddressConfig socketAddressConfig = new SocketAddressConfig();

        final EventLoopFactory eventLoopFactory = new EventLoopFactory();
        Assertions.assertDoesNotThrow(() -> eventLoop = eventLoopFactory.create());

        executorService = Executors.newVirtualThreadPerTaskExecutor();

        executorService.submit(eventLoop);

        final TransportConfig transportConfiguration = new TransportConfig(
                true,
                Path.of("src/test/resources/tls/keystore-server.jks"),
                Path.of("src/test/resources/tls/truststore.jks"),
                "changeit",
                "changeit",
                "TLSv1.3"
        );

        final SSLContext sslContext = Assertions
                .assertDoesNotThrow(() -> SSLContext.getInstance(transportConfiguration.protocol()));
        final KeyStore ks = Assertions.assertDoesNotThrow(() -> KeyStore.getInstance("JKS"));

        final File file = new File(transportConfiguration.keyStorePath().toUri());
        final FileInputStream fileInputStream = Assertions.assertDoesNotThrow(() -> new FileInputStream(file));
        Assertions
                .assertDoesNotThrow(
                        () -> ks.load(fileInputStream, transportConfiguration.keyStorePassword().toCharArray())
                );
        final TrustManagerFactory tmf = Assertions
                .assertDoesNotThrow(() -> TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()));
        Assertions.assertDoesNotThrow(() -> tmf.init(ks));
        final KeyManagerFactory kmf = Assertions
                .assertDoesNotThrow(() -> KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()));
        Assertions.assertDoesNotThrow(() -> kmf.init(ks, transportConfiguration.keyStorePassword().toCharArray()));
        Assertions.assertDoesNotThrow(() -> sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null));
        Assertions.assertDoesNotThrow(fileInputStream::close);

        final Function<SSLContext, SSLEngine> sslEngineFunction = context -> {
            final SSLEngine engine = context.createSSLEngine();
            engine.setUseClientMode(false);
            return engine;
        };

        final ServerFactory serverFactory = new ServerFactory(
                eventLoop,
                executorService,
                new TLSFactory(sslContext, sslEngineFunction),
                new FrameDelegationClockFactory(() -> new DefaultFrameDelegate((frame) -> {
                    System.out.println(new String(frame.relpFrame().payload().toBytes(), StandardCharsets.UTF_8));
                    //frame.relpFrame().close();
                }))
        );
        Assertions.assertDoesNotThrow(() -> serverFactory.create(socketAddressConfig.port()));

        Assertions.assertDoesNotThrow(() -> Thread.sleep(Long.MAX_VALUE));
    }
}
