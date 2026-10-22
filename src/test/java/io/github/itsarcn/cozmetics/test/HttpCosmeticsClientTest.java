package io.github.itsarcn.cozmetics.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.itsarcn.cozmetics.cosmetic.AttachmentSlot;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticRegistry;
import io.github.itsarcn.cozmetics.network.HttpCosmeticsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

public class HttpCosmeticsClientTest {

	@TempDir
	Path tempDir;

	private HttpCosmeticsClient client;

	@BeforeEach
	void setUp() {
		CosmeticRegistry.clear();
		client = new HttpCosmeticsClient("http://127.0.0.1:65432/api/v1", tempDir.resolve("cache"));
	}

	@Test
	void testClientInitializationAndCacheDir() {
		assertNotNull(client);
		assertEquals("http://127.0.0.1:65432/api/v1", client.getBaseUrl());
		assertTrue(Files.exists(client.getCacheDir()));
	}

	@Test
	void testDeviceLinkResponseRecord() {
		HttpCosmeticsClient.DeviceLinkResponse response =
				new HttpCosmeticsClient.DeviceLinkResponse("MC-123456", 600, "/link?code=MC-123456");

		assertEquals("MC-123456", response.code());
		assertEquals(600, response.expiresInSeconds());
		assertEquals("/link?code=MC-123456", response.verificationUrl());
	}

	@Test
	void testGracefulOfflineBehaviorOnSync() throws Exception {
		// Attempting sync to an unreachable port must complete exceptionally or return 0,
		// without throwing unhandled exceptions or hanging threads.
		var future = client.syncCatalogue();
		Integer result = future.get(3, TimeUnit.SECONDS);

		assertEquals(0, result);
		assertTrue(CosmeticRegistry.getAll().isEmpty());
	}

	@Test
	void testGracefulOfflineBehaviorOnEquipped() throws Exception {
		UUID randomUuid = UUID.randomUUID();
		var future = client.fetchEquipped(randomUuid);
		Map<CosmeticCategory, String> equipped = future.get(3, TimeUnit.SECONDS);

		assertNotNull(equipped);
		assertTrue(equipped.isEmpty());
	}

	@Test
	void testDeviceLinkRequestOfflineFailsGracefully() {
		UUID randomUuid = UUID.randomUUID();
		var future = client.requestDeviceLink("test_player", randomUuid);

		assertThrows(ExecutionException.class, () -> {
			future.get(3, TimeUnit.SECONDS);
		});
	}

	@Test
	void testApiUrlAndLocalhostToggle() {
		assertFalse(HttpCosmeticsClient.USE_LOCALHOST);
		assertEquals("http://localhost:3001", HttpCosmeticsClient.LOCALHOST_API_URL);
		assertEquals("https://cozmetics.arcn.online", HttpCosmeticsClient.PRODUCTION_API_URL);
		assertEquals("https://cozmetics.arcn.online", HttpCosmeticsClient.DEFAULT_API_BASE_URL);
	}
}

