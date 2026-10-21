package io.github.itsarcn.cozmetics.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.itsarcn.cozmetics.Cozmetics;
import io.github.itsarcn.cozmetics.catalog.CatalogItem;
import io.github.itsarcn.cozmetics.client.catalog.CosmeticCatalogManager;
import io.github.itsarcn.cozmetics.cosmetic.AttachmentSlot;
import io.github.itsarcn.cozmetics.cosmetic.AttachmentTransform;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticDefinition;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-performance, non-blocking HTTP networking client for Cozmetics mod.
 *
 * Handles live catalogue discovery, player equipment synchronization,
 * device linking, and bounded local disk caching.
 *
 * Network calls run exclusively on daemon worker threads and never block
 * the Minecraft render thread.
 */
public class HttpCosmeticsClient {
	public static final boolean USE_LOCALHOST = true;
	public static final String PRODUCTION_API_URL = "https://catalog.cozmetics.arcn.online";
	public static final String LOCALHOST_API_URL = "http://localhost:3001";
	public static final String DEFAULT_API_BASE_URL = USE_LOCALHOST ? LOCALHOST_API_URL : PRODUCTION_API_URL;

	private static HttpCosmeticsClient instance;

	private final String baseUrl;
	private final HttpClient httpClient;
	private final ExecutorService executor;
	private final Path cacheDir;

	// In-memory cache of player equipment: UUID -> Map<Category, Cosmetic ID>
	private final Map<UUID, Map<CosmeticCategory, String>> equippedCache = new ConcurrentHashMap<>();

	// Records
	public record DeviceLinkResponse(String code, int expiresInSeconds, String verificationUrl) {}

	public static synchronized HttpCosmeticsClient getInstance() {
		if (instance == null) {
			String url = System.getProperty("cozmetics.api_url");
			if (url == null || url.isBlank()) {
				url = System.getenv("COZMETICS_API_URL");
			}
			if (url == null || url.isBlank()) {
				url = DEFAULT_API_BASE_URL;
			}

			Path cache;
			try {
				cache = FabricLoader.getInstance().getGameDir().resolve(".cozmetics-cache");
			} catch (Throwable t) {
				cache = Path.of("build/tmp/cozmetics-cache");
			}

			instance = new HttpCosmeticsClient(url, cache);
		}
		return instance;
	}

	public HttpCosmeticsClient(String baseUrl, Path cacheDir) {
		this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : DEFAULT_API_BASE_URL;
		this.cacheDir = cacheDir;
		try {
			Files.createDirectories(cacheDir);
		} catch (IOException ignored) {
		}

		this.executor = Executors.newFixedThreadPool(4, runnable -> {
			Thread thread = new Thread(runnable, "Cozmetics-Http-Worker");
			thread.setDaemon(true);
			return thread;
		});

		this.httpClient = HttpClient.newBuilder()
				.executor(this.executor)
				.connectTimeout(Duration.ofSeconds(5))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public Path getCacheDir() {
		return cacheDir;
	}

	/**
	 * Requests a device linking code for the specified Minecraft account.
	 *
	 * @param minecraftUsername Minecraft in-game username
	 * @param minecraftUuid Player unique UUID
	 * @return CompletableFuture with linking response containing code and URL
	 */
	public CompletableFuture<DeviceLinkResponse> requestDeviceLink(String minecraftUsername, UUID minecraftUuid) {
		JsonObject body = new JsonObject();
		body.addProperty("minecraftUsername", minecraftUsername);
		body.addProperty("minecraftUuid", minecraftUuid.toString());

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/mod/link/code"))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
				.build();

		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.thenApply(response -> {
					if (response.statusCode() >= 200 && response.statusCode() < 300) {
						JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
						String code = json.get("code").getAsString();
						int expiresInSeconds = json.has("expiresInSeconds") ? json.get("expiresInSeconds").getAsInt() : 600;
						String verificationUrl = json.has("verificationUrl") ? json.get("verificationUrl").getAsString() : "/link?code=" + code;
						return new DeviceLinkResponse(code, expiresInSeconds, verificationUrl);
					} else {
						throw new RuntimeException("API error: HTTP " + response.statusCode() + " - " + response.body());
					}
				});
	}

	/**
	 * Checks the confirmation status of a pending device linking code.
	 */
	public CompletableFuture<String> checkDeviceLinkStatus(String code) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/mod/link/status?code=" + code))
				.header("Accept", "application/json")
				.timeout(Duration.ofSeconds(5))
				.GET()
				.build();

		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.thenApply(response -> {
					if (response.statusCode() == 200) {
						JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
						return json.has("status") ? json.get("status").getAsString() : "unknown";
					}
					return "unknown";
				});
	}

	/**
	 * Synchronizes the cosmetic catalogue from the backend API into the CosmeticRegistry.
	 *
	 * @return CompletableFuture with the number of successfully registered cosmetics
	 */
	public CompletableFuture<Integer> syncCatalogue() {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/mod/catalogue"))
				.header("Accept", "application/json")
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build();

		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.thenApply(response -> {
					if (response.statusCode() != 200) {
						Cozmetics.LOGGER.warn("Catalogue sync returned HTTP {}", response.statusCode());
						return 0;
					}

					JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
					if (!json.has("cosmetics") || !json.get("cosmetics").isJsonArray()) {
						return 0;
					}

					JsonArray array = json.getAsJsonArray("cosmetics");
					int count = 0;

					for (JsonElement elem : array) {
						if (!elem.isJsonObject()) continue;
						JsonObject item = elem.getAsJsonObject();

						try {
							CatalogItem catalogItem = CatalogItem.fromJson(item);
							if (!catalogItem.category().isEnabled()) {
								continue;
							}

							CosmeticCatalogManager.getInstance().addItem(catalogItem);
							count++;
						} catch (Exception e) {
							Cozmetics.LOGGER.warn("Failed to parse mod cosmetic: {}", elem, e);
						}
					}

					CosmeticCatalogManager.getInstance().saveCachedCatalogue();
					Cozmetics.LOGGER.info("Synchronized {} cosmetics from backend API.", count);
					return count;
				})
				.exceptionally(ex -> {
					Cozmetics.LOGGER.warn("Could not connect to Cozmetics API at {}: {}", baseUrl, ex.getMessage());
					return 0;
				});
	}

	/**
	 * Fetches equipped cosmetics for the specified player UUID.
	 *
	 * @param playerUuid Player UUID
	 * @return CompletableFuture mapping each category to its equipped cosmetic ID
	 */
	public CompletableFuture<Map<CosmeticCategory, String>> fetchEquipped(UUID playerUuid) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/mod/players/" + playerUuid.toString() + "/equipped"))
				.header("Accept", "application/json")
				.timeout(Duration.ofSeconds(5))
				.GET()
				.build();

		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.thenApply(response -> {
					Map<CosmeticCategory, String> result = new EnumMap<>(CosmeticCategory.class);
					if (response.statusCode() == 200) {
						JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
						if (json.has("equipped") && json.get("equipped").isJsonObject()) {
							JsonObject eq = json.getAsJsonObject("equipped");
							for (String key : eq.keySet()) {
								try {
									CosmeticCategory cat = CosmeticCategory.valueOf(key.toUpperCase());
									if (cat.isEnabled()) {
										result.put(cat, eq.get(key).getAsString());
									}
								} catch (Exception ignored) {
								}
							}
						}
						equippedCache.put(playerUuid, result);
					}
					return result;
				})
				.exceptionally(ex -> {
					Cozmetics.LOGGER.debug("Failed to fetch equipped cosmetics for {}: {}", playerUuid, ex.getMessage());
					return equippedCache.getOrDefault(playerUuid, Collections.emptyMap());
				});
	}

	/**
	 * Downloads an asset by relative URL, using bounded SHA-256 disk caching.
	 *
	 * @param relativeUrl Relative path from base URL
	 * @return CompletableFuture containing downloaded asset bytes
	 */
	public CompletableFuture<byte[]> downloadAsset(String relativeUrl) {
		String fullUrl = relativeUrl.startsWith("http") ? relativeUrl : baseUrl + (relativeUrl.startsWith("/") ? "" : "/") + relativeUrl;
		String cacheKey = hashString(fullUrl);
		Path cachedFile = cacheDir.resolve(cacheKey);

		if (Files.exists(cachedFile)) {
			try {
				byte[] data = Files.readAllBytes(cachedFile);
				return CompletableFuture.completedFuture(data);
			} catch (IOException ignored) {
			}
		}

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(fullUrl))
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build();

		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
				.thenApply(response -> {
					if (response.statusCode() == 200) {
						byte[] body = response.body();
						try {
							Files.write(cachedFile, body);
						} catch (IOException e) {
							Cozmetics.LOGGER.warn("Failed to write asset cache for {}", fullUrl, e);
						}
						return body;
					} else {
						throw new RuntimeException("Failed to download asset: HTTP " + response.statusCode());
					}
				});
	}

	private static String hashString(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			return Integer.toHexString(input.hashCode());
		}
	}
}

