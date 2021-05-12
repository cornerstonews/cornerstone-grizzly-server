package com.github.cornerstonews.grizzly;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.websocket.DeploymentException;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpHandlerRegistration;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.jersey.grizzly2.httpserver.CornerstoneGrizzlyHttpContainer;
import org.glassfish.jersey.internal.guava.ThreadFactoryBuilder;
import org.glassfish.jersey.process.JerseyProcessingUncaughtExceptionHandler;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spi.Container;
import org.glassfish.tyrus.container.grizzly.server.CornerstoneWebSocketAddOn;
import org.glassfish.tyrus.container.grizzly.server.GrizzlyServerContainer;
import org.glassfish.tyrus.core.DebugContext;
import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.core.Utils;
import org.glassfish.tyrus.core.cluster.ClusterContext;
import org.glassfish.tyrus.core.monitoring.ApplicationEventListener;
import org.glassfish.tyrus.server.TyrusServerContainer;
import org.glassfish.tyrus.spi.WebSocketEngine;

import com.github.cornerstonews.ssl.CornerstoneKeyStore;
import com.github.cornerstonews.ssl.CornerstoneSSLContext;

public class CornerstoneGrizzlyServer {

    private final static Logger LOGGER = LogManager.getLogger(CornerstoneGrizzlyServer.class);

    private static final String DEFAULT_CONTEXT_PATH = "/";

    private final Map<String, Object> properties;
    private final ResourceConfig jerseyWebserviceApplication;
    private final Set<Class<?>> endpointClasses;
    private final String bindServerIP;
    private volatile int bindServerPort;
    private final String contextPath;
    private final CornerstoneKeyStore keyStore;

    private HttpServer server;
    private volatile NetworkListener listener = null;
    private ServerConfiguration config;
    private CornerstoneTyrusServerContainer tyrusServerContainer;

    public CornerstoneGrizzlyServer() {
        this("localhost", 8888, null, "/", new HashMap<String, Object>(), null, new Class<?>[] {});
    }

    public CornerstoneGrizzlyServer(String bindIp, int bindPort, CornerstoneSSLContext sslContext, String contextPath, Map<String, Object> properties,
            ResourceConfig jerseyWebserviceApplication) {
        this(bindIp, bindPort, sslContext, contextPath, properties, jerseyWebserviceApplication, new Class<?>[] {});
    }

    public CornerstoneGrizzlyServer(String bindIp, int bindPort, CornerstoneSSLContext sslContext, String contextPath, Map<String, Object> properties,
            Class<?>... endpointClasses) {
        this(bindIp, bindPort, sslContext, contextPath, properties, null, new HashSet<Class<?>>(Arrays.asList(endpointClasses)));
    }

    public CornerstoneGrizzlyServer(String bindIp, int bindPort, CornerstoneSSLContext sslContext, String contextPath, Map<String, Object> properties,
            ResourceConfig jerseyWebserviceApplication, Class<?>... endpointClasses) {
        this(bindIp, bindPort, sslContext, contextPath, properties, jerseyWebserviceApplication, new HashSet<Class<?>>(Arrays.asList(endpointClasses)));
    }

    public CornerstoneGrizzlyServer(String bindIp, int bindPort, CornerstoneSSLContext sslContext, String contextPath, Map<String, Object> properties,
            ResourceConfig jerseyWebserviceApplication, Set<Class<?>> endpointClasses) {
        this.bindServerIP = bindIp == null ? NetworkListener.DEFAULT_NETWORK_HOST : bindIp;
        this.bindServerPort = (bindPort > 0) ? bindPort : (sslContext.getKeyStore() != null) ? Container.DEFAULT_HTTPS_PORT : Container.DEFAULT_HTTP_PORT;
        this.keyStore = sslContext == null ? null : sslContext.getKeyStore();
        this.contextPath = contextPath == null ? DEFAULT_CONTEXT_PATH : contextPath;
        this.properties = properties == null ? Collections.emptyMap() : new HashMap<String, Object>(properties);
        this.jerseyWebserviceApplication = jerseyWebserviceApplication;
        this.endpointClasses = endpointClasses;
    }

    public void stop() {
        if (this.server != null) {
            this.tyrusServerContainer.stop();
            this.server.shutdown();
            this.server = null;
            LOGGER.debug("Websocket Server Stopped.");
        }
    }

    public void start() throws DeploymentException {
        try {
            if (this.server == null) {
                this.listener = new NetworkListener("grizzly", bindServerIP, bindServerPort);

                if (this.keyStore != null) {
                    listener.setSecure(true);
                    listener.setSSLEngineConfig(getSSLEngineConfigurator());
                }

                server = new HttpServer();
                server.addListener(this.listener);
                this.config = server.getServerConfiguration();

                if (this.jerseyWebserviceApplication != null) {
                    this.listener.getTransport().getWorkerThreadPoolConfig().setThreadFactory(new ThreadFactoryBuilder().setNameFormat("grizzly-http-server-%d")
                            .setUncaughtExceptionHandler(new JerseyProcessingUncaughtExceptionHandler()).build());
                    config.addHttpHandler(CornerstoneGrizzlyHttpContainer.getGrizzlyHttpContainer(this.jerseyWebserviceApplication),
                            HttpHandlerRegistration.builder().contextPath(contextPath).build());
                }

                if (!this.endpointClasses.isEmpty()) {
                    this.createWebSocketEngine();
                    for (Class<?> clazz : endpointClasses) {
                        tyrusServerContainer.addEndpoint(clazz);
                    }

                }

                server.start();
                tyrusServerContainer.start(contextPath, bindServerPort);

//                LOGGER.info("WebSocket server started.");
//                LOGGER.info("WebSocket URLs start with " + this.keyStore == null ? "ws" : "wss" + "://" + this.serverIp + ":" + this.serverPort);

            }
        } catch (IOException e) {
            throw new DeploymentException(e.getMessage(), e);
        }
    }

    private void createWebSocketEngine() {
        final Integer incomingBufferSize = Utils.getProperty(this.properties, TyrusWebSocketEngine.INCOMING_BUFFER_SIZE, Integer.class);
        final ClusterContext clusterContext = Utils.getProperty(this.properties, ClusterContext.CLUSTER_CONTEXT, ClusterContext.class);
        final ApplicationEventListener applicationEventListener = Utils.getProperty(this.properties, ApplicationEventListener.APPLICATION_EVENT_LISTENER,
                ApplicationEventListener.class);
        final Integer maxSessionsPerApp = Utils.getProperty(this.properties, TyrusWebSocketEngine.MAX_SESSIONS_PER_APP, Integer.class);
        final Integer maxSessionsPerRemoteAddr = Utils.getProperty(this.properties, TyrusWebSocketEngine.MAX_SESSIONS_PER_REMOTE_ADDR, Integer.class);
        final Boolean parallelBroadcastEnabled = Utils.getProperty(this.properties, TyrusWebSocketEngine.PARALLEL_BROADCAST_ENABLED, Boolean.class);
        final DebugContext.TracingType tracingType = Utils.getProperty(this.properties, TyrusWebSocketEngine.TRACING_TYPE, DebugContext.TracingType.class,
                DebugContext.TracingType.OFF);
        final DebugContext.TracingThreshold tracingThreshold = Utils.getProperty(this.properties, TyrusWebSocketEngine.TRACING_THRESHOLD,
                DebugContext.TracingThreshold.class, DebugContext.TracingThreshold.TRACE);

        this.tyrusServerContainer = new CornerstoneTyrusServerContainer((Set<Class<?>>) null) {

            private final WebSocketEngine engine = TyrusWebSocketEngine.builder(this).incomingBufferSize(incomingBufferSize).clusterContext(clusterContext)
                    .applicationEventListener(applicationEventListener).maxSessionsPerApp(maxSessionsPerApp).maxSessionsPerRemoteAddr(maxSessionsPerRemoteAddr)
                    .parallelBroadcastEnabled(parallelBroadcastEnabled).tracingType(tracingType).tracingThreshold(tracingThreshold).build();

            @Override
            public WebSocketEngine getWebSocketEngine() {
                return engine;
            }

            @Override
            public void register(Class<?> endpointClass) throws DeploymentException {
                engine.register(endpointClass, contextPath);
            }

            @Override
            public void register(ServerEndpointConfig serverEndpointConfig) throws DeploymentException {
                engine.register(serverEndpointConfig, contextPath);
            }

            @Override
            public void start(final String rootPath, int port) throws IOException, DeploymentException {
                super.start(rootPath, port);
            }

            public void configureHandler() {
                ThreadPoolConfig workerThreadPoolConfig = Utils.getProperty(properties, GrizzlyServerContainer.WORKER_THREAD_POOL_CONFIG,
                        ThreadPoolConfig.class);
                ThreadPoolConfig selectorThreadPoolConfig = Utils.getProperty(properties, GrizzlyServerContainer.SELECTOR_THREAD_POOL_CONFIG,
                        ThreadPoolConfig.class);

                // TYRUS-287: configurable server thread pools
                if (workerThreadPoolConfig != null || selectorThreadPoolConfig != null) {
                    TCPNIOTransportBuilder transportBuilder = TCPNIOTransportBuilder.newInstance();
                    if (workerThreadPoolConfig != null) {
                        transportBuilder.setWorkerThreadPoolConfig(workerThreadPoolConfig);
                    }
                    if (selectorThreadPoolConfig != null) {
                        transportBuilder.setSelectorThreadPoolConfig(selectorThreadPoolConfig);
                    }
                    transportBuilder.setIOStrategy(WorkerThreadIOStrategy.getInstance());
                    listener.setTransport(transportBuilder.build());
                } else {
                    // if no configuration is set, just update IO Strategy to worker thread strat.
                    listener.getTransport().setIOStrategy(WorkerThreadIOStrategy.getInstance());
                }

//              server.getListener("grizzly").getKeepAlive().setIdleTimeoutInSeconds(-1);
                listener.registerAddOn(new CornerstoneWebSocketAddOn(this, contextPath));
            }
        };
        
        this.tyrusServerContainer.configureHandler();
    }

    private abstract class CornerstoneTyrusServerContainer extends TyrusServerContainer {

        public CornerstoneTyrusServerContainer(Set<Class<?>> classes) {
            super(classes);
        }

        public abstract void configureHandler();

    }

    private SSLEngineConfigurator getSSLEngineConfigurator() {
        // Grizzly ssl configuration
        SSLContextConfigurator sslContext = new SSLContextConfigurator();

        // set up security context
        sslContext.setKeyStoreFile(keyStore.getKeystore()); // contains server keypair
        sslContext.setKeyStorePass(keyStore.getKeystorePassword());
//        sslContext.setTrustStoreFile(sslProperties.get("truststore")); // contains client certificate
//        sslContext.setTrustStorePass(sslProperties.get("truststorePassword"));

        return new SSLEngineConfigurator(sslContext).setClientMode(false).setNeedClientAuth(false);
    }

    public static void main(String[] args) throws DeploymentException, IOException {
        WebsocketServer websocketServer = new WebsocketServer();
        websocketServer.start();
    }

}
