package emissary.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.ws.rs.core.Response;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.collect.Sets;
import emissary.client.response.Agent;
import emissary.client.response.AgentsResponseEntity;
import emissary.client.response.Config;
import emissary.client.response.ConfigsResponseEntity;
import emissary.client.response.MapResponseEntity;
import emissary.client.response.PlacesResponseEntity;
import emissary.command.ServerCommand;
import emissary.config.ConfigUtil;
import emissary.core.EmissaryException;
import emissary.core.MetricsManager;
import emissary.core.Namespace;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.pool.AgentPool;
import emissary.pool.MobileAgentFactory;
import emissary.server.EmissaryServer;
import emissary.server.mvc.EndpointTestBase;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmissaryApiTest extends EndpointTestBase {

    @BeforeEach
    public void setup() {
        EmissaryServer mockServer = mock(EmissaryServer.class);
        EmissaryNode node = spy(new EmissaryNode());
        ServerCommand srvCmd = mock(ServerCommand.class);

        doReturn("localhost").when(node).getNodeName();
        doReturn(8001).when(node).getNodePort();
        when(mockServer.getNode()).thenReturn(node);
        when(mockServer.getServerCommand()).thenReturn(srvCmd);
        when(srvCmd.getConfig()).thenReturn(Paths.get("/path/to/project/config"));
        when(srvCmd.getProjectBase()).thenReturn(Paths.get("/path/to/project"));
        when(srvCmd.getOutputDir()).thenReturn(Paths.get("/path/to/project/output"));
        when(srvCmd.getBinDir()).thenReturn(Paths.get("/path/to/project/bin"));
        when(srvCmd.getHost()).thenReturn("localhost");
        when(srvCmd.getPort()).thenReturn(8001);
        when(srvCmd.getScheme()).thenReturn("https");

        Namespace.bind("EmissaryServer", mockServer);
    }

    @AfterEach
    public void cleanup() {
        Namespace.clear();
    }

    @Test
    void agents() {
        Agent[] expectedAgents = {
                new Agent("MobileAgent-00", "Idle"),
                new Agent("MobileAgent-01", "Idle"),
                new Agent("MobileAgent-02", "Idle"),
                new Agent("MobileAgent-03", "Idle"),
                new Agent("MobileAgent-04", "Idle"),
                new Agent("MobileAgent-05", "Idle"),
                new Agent("MobileAgent-06", "Idle"),
                new Agent("MobileAgent-07", "PHASE.FORM.Place"),
                new Agent("MobileAgent-08", "Idle"),
                new Agent("MobileAgent-09", "Idle")
        };
        AgentPool pool = new AgentPool(new MobileAgentFactory("emissary.core.HDMobileAgent"), 10);
        Namespace.bind("MobileAgent-07", "PHASE.FORM.Place");
        Namespace.bind("AgentPool", pool);
        try (Response response = target("agents").request().get()) {
            assertEquals(200, response.getStatus());
            AgentsResponseEntity entity = response.readEntity(AgentsResponseEntity.class);
            assertTrue(entity.getErrors().isEmpty());
            assertTrue(entity.getCluster().isEmpty());
            assertEquals("localhost:8001", entity.getLocal().getHost());
            assertIterableEquals(Arrays.asList(expectedAgents), entity.getLocal().getAgents());
        } finally {
            Namespace.unbind("MobileAgent-07");
            Namespace.unbind("AgentPool");
        }
    }

    @Test
    void pool() {
        AgentPool pool = new AgentPool(new MobileAgentFactory("emissary.core.HDMobileAgent"), 10);
        Namespace.bind("AgentPool", pool);
        try (Response response = target("pool").request().get()) {
            assertEquals(200, response.getStatus());
            MapResponseEntity entity = response.readEntity(MapResponseEntity.class);
            assertTrue(entity.getErrors().isEmpty());
            assertFalse(entity.getResponse().isEmpty());
            assertEquals("Poolsize active/idle: 0/10", entity.getResponse().get("localhost:8001"));
        } finally {
            Namespace.unbind("AgentPool");
        }
    }

    @Test
    void places() {
        Namespace.bind("PickupPlace", "pickupPlace");
        Namespace.bind("PickupClient", "pickupClient");
        Namespace.bind("ThisWontBeAdded", "miss");
        Namespace.bind("ProcessingPlace", "processingPlace");
        try (Response response = target("places").request().get()) {
            assertEquals(200, response.getStatus());
            PlacesResponseEntity entity = response.readEntity(PlacesResponseEntity.class);
            assertEquals("localhost:8001", entity.getLocal().getHost());
            assertEquals(3, entity.getLocal().getPlaces().size());
            assertEquals(Sets.newHashSet("pickupClient", "pickupPlace", "processingPlace"), entity.getLocal().getPlaces());
            assertEquals(0, entity.getErrors().size());
            assertTrue(CollectionUtils.isEmpty(entity.getCluster()));
        } finally {
            Namespace.unbind("PickupPlace");
            Namespace.unbind("PickupClient");
            Namespace.unbind("ThisWontBeAdded");
            Namespace.unbind("ProcessingPlace");
        }
    }

    @Test
    void placesNoPlacesClientsBound() {
        try (Response response = target("places").request().get()) {
            assertEquals(200, response.getStatus());
            PlacesResponseEntity entity = response.readEntity(PlacesResponseEntity.class);
            assertEquals("localhost:8001", entity.getLocal().getHost());
            assertEquals(0, entity.getLocal().getPlaces().size());
            assertEquals(0, entity.getErrors().size());
            assertTrue(CollectionUtils.isEmpty(entity.getCluster()));
        }
    }

    @Test
    void placesNoServerBound() {
        Namespace.unbind("EmissaryServer");
        try (Response response = target("places").request().get()) {
            assertEquals(200, response.getStatus());
            PlacesResponseEntity entity = response.readEntity(PlacesResponseEntity.class);
            assertTrue(CollectionUtils.isEmpty(entity.getLocal().getPlaces()));
            assertTrue(CollectionUtils.isEmpty(entity.getCluster()));
            assertEquals(1, entity.getErrors().size());
            assertEquals(Collections.singleton("Problem finding the emissary server or places in the namespace: Not found: EmissaryServer"),
                    entity.getErrors());
        }
    }

    @Test
    void version() {
        try (Response response = target("version").request().get()) {
            assertEquals(200, response.getStatus());
            MapResponseEntity entity = response.readEntity(MapResponseEntity.class);
            assertTrue(entity.getErrors().isEmpty());
            assertFalse(entity.getResponse().isEmpty());
            assertEquals(new emissary.util.Version().getVersion(), entity.getResponse().get("localhost:8001"));
        }
    }

    @Test
    void getEnvJson() {
        try (Response response = target("env").request().get()) {
            assertEquals(200, response.getStatus());
            MapResponseEntity entity = response.readEntity(MapResponseEntity.class);
            assertTrue(entity.getErrors().isEmpty());
            assertFalse(entity.getResponse().isEmpty());
            assertEquals("/path/to/project/config", entity.getResponse().get("CONFIG_DIR"));
            assertEquals("/path/to/project", entity.getResponse().get("PROJECT_BASE"));
            assertEquals("/path/to/project/output", entity.getResponse().get("OUTPUT_ROOT"));
            assertEquals("/path/to/project/bin", entity.getResponse().get("BIN_DIR"));
            assertEquals("localhost", entity.getResponse().get("HOST"));
            assertEquals("8001", entity.getResponse().get("PORT"));
            assertEquals("https", entity.getResponse().get("SCHEME"));
        }
    }

    @Test
    void getEnvForBash() {
        try (Response response = target("env.sh").request().get()) {
            assertEquals(200, response.getStatus());
            String entity = response.readEntity(String.class);
            assertTrue(StringUtils.isNotEmpty(entity));
            assertTrue(entity.contains("export CONFIG_DIR=\"/path/to/project/config\""));
            assertTrue(entity.contains("export PROJECT_BASE=\"/path/to/project\""));
            assertTrue(entity.contains("export OUTPUT_ROOT=\"/path/to/project/output\""));
            assertTrue(entity.contains("export BIN_DIR=\"/path/to/project/bin\""));
            assertTrue(entity.contains("export HOST=\"localhost\""));
            assertTrue(entity.contains("export PORT=\"8001\""));
            assertTrue(entity.contains("export SCHEME=\"https\""));
        }
    }

    @Test
    void metrics() {
        MetricsAction metrics = new MetricsAction();
        MetricsManager manager = mock(MetricsManager.class);
        MetricRegistry registry = mock(MetricRegistry.class);

        Counter metric = new Counter();
        metric.inc(100);

        final SortedMap<String, Metric> results = new TreeMap<>();
        results.put("testing", metric);

        when(registry.getMetrics()).thenReturn(results);
        when(manager.getMetricRegistry()).thenReturn(registry);

        Namespace.bind("MetricsManager", manager);
        try (Response response = metrics.clusterAgents()) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            Map<String, Metric> metricsMap = ((MetricRegistry) response.getEntity()).getMetrics();
            assertEquals(100, ((Counter) metricsMap.get("testing")).getCount());
        } finally {
            Namespace.unbind("MetricsManager");
        }
    }

    @Test
    void healthcheck() {
        HealthCheckAction health = new HealthCheckAction();
        HealthCheckRegistry registry = mock(HealthCheckRegistry.class);
        MetricsManager manager = mock(MetricsManager.class);
        final SortedMap<String, HealthCheck.Result> results = new TreeMap<>();
        results.put("testing", HealthCheck.Result.healthy("Okay"));

        when(manager.getHealthCheckRegistry()).thenReturn(registry);
        when(registry.runHealthChecks()).thenReturn(results);

        Namespace.bind("MetricsManager", manager);
        try (Response response = health.clusterAgents()) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            assertTrue(response.getEntity().toString().contains("isHealthy=true"));
            assertTrue(response.getEntity().toString().contains("message=Okay"));
        } finally {
            Namespace.unbind("MetricsManager");
        }
    }

    @Test
    void apiutils() throws Exception {

        String PEER = "*.*.*.http://somehost:8001/DirectoryPlace";
        Set<String> PEERS = Collections.singleton(PEER);

        DirectoryPlace mockDirectory = mock(DirectoryPlace.class);
        when(mockDirectory.getPeerDirectories()).thenReturn(PEERS);

        Namespace.bind("DirectoryPlace", mockDirectory);
        try {
            Set<String> results = ApiUtils.lookupPeers();
            assertEquals(PEERS, results);

            String result = ApiUtils.stripPeerString(PEER);
            assertEquals("http://somehost:8001/", result);

            assertThrows(IndexOutOfBoundsException.class, () -> ApiUtils.stripPeerString("*.*.http://throwsException:8001"));

            String hostAndPort = ApiUtils.getHostAndPort();
            assertEquals("localhost:8001", hostAndPort);

            Namespace.unbind("EmissaryServer");
            result = ApiUtils.getHostAndPort();
            assertEquals("Namespace lookup error, host unknown", result);

            Namespace.unbind("DirectoryPlace");
            assertThrows(EmissaryException.class, ApiUtils::lookupPeers);
        } finally {
            Namespace.unbind("DirectoryPlace");
        }
    }

    @Test
    void getConfigs() {
        String flavor = "CLUSTER";
        String place = "emissary.place.sample.ToLowerPlace";
        String expected = place + ConfigUtil.CONFIG_FILE_ENDING;
        String expectedFlavored = place + "-" + flavor + ConfigUtil.CONFIG_FILE_ENDING;

        try (Response response = target("configuration/" + place).request().get()) {
            assertEquals(200, response.getStatus());
            ConfigsResponseEntity entity = response.readEntity(ConfigsResponseEntity.class);
            assertTrue(entity.getErrors().isEmpty());

            List<Config> list = entity.getLocal().getConfigs();
            assertFalse(list.isEmpty());

            Config config = list.get(0);
            assertEquals(flavor, config.getFlavors().get(0));
            assertEquals(expected, config.getConfigs().get(0));
            assertEquals(expectedFlavored, config.getConfigs().get(1));
        }
    }

    @Test
    void getConfigsDetailed() {
        String flavor = "CLUSTER";
        String place = "emissary.place.sample.ToLowerPlace";
        String expected = place + ConfigUtil.CONFIG_FILE_ENDING;
        String expectedFlavored = place + "-" + flavor + ConfigUtil.CONFIG_FILE_ENDING;

        try (Response response = target("configuration/detailed/" + place).request().get()) {
            assertEquals(200, response.getStatus());
            ConfigsResponseEntity entity = response.readEntity(ConfigsResponseEntity.class);
            assertTrue(entity.getErrors().isEmpty());

            List<Config> list = entity.getLocal().getConfigs();
            assertEquals(3, list.size());

            assertTrue(list.get(0).getFlavors().isEmpty());
            assertEquals(expected, list.get(0).getConfigs().get(0));

            assertEquals(flavor, list.get(1).getFlavors().get(0));
            assertEquals(expectedFlavored, list.get(1).getConfigs().get(0));

            assertEquals(flavor, list.get(2).getFlavors().get(0));
            assertEquals(expected, list.get(2).getConfigs().get(0));
            assertEquals(expectedFlavored, list.get(2).getConfigs().get(1));
        }
    }
}
