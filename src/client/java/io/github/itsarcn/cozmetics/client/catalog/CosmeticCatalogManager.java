package io.github.itsarcn.cozmetics.client.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.itsarcn.cozmetics.Cozmetics;
import io.github.itsarcn.cozmetics.cache.CosmeticCacheManager;
import io.github.itsarcn.cozmetics.catalog.CatalogItem;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticRegistry;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the live cosmetics catalogue on the client.
 *
 * Keeps local cached metadata for full offline resilience, performs asynchronous
 * updates when network is available, and provides real-time search and category filtering.
 */
public class CosmeticCatalogManager {

	public enum DownloadState {
		NOT_DOWNLOADED,
		DOWNLOADING,
		READY,
		FAILED
	}

	private static final CosmeticCatalogManager INSTANCE = new CosmeticCatalogManager();
	private final List<CatalogItem> items = new CopyOnWriteArrayList<>();
	private final Map<Identifier, DownloadState> itemStates = new ConcurrentHashMap<>();
	private final CosmeticCacheManager cacheManager;

	private CosmeticCatalogManager() {
		this.cacheManager = CosmeticCacheManager.getInstance();
		loadCachedCatalogue();
	}

	public static CosmeticCatalogManager getInstance() {
		return INSTANCE;
	}

	public List<CatalogItem> getAllItems() {
		return Collections.unmodifiableList(items);
	}

	public Optional<CatalogItem> getItem(Identifier id) {
		return items.stream().filter(it -> it.id().equals(id)).findFirst();
	}

	public DownloadState getDownloadState(Identifier id) {
		if (CosmeticRegistry.get(id).isPresent()) {
			return DownloadState.READY;
		}
		return itemStates.getOrDefault(id, DownloadState.NOT_DOWNLOADED);
	}

	public void setDownloadState(Identifier id, DownloadState state) {
		itemStates.put(id, state);
	}

	public void addItem(CatalogItem item) {
		items.removeIf(existing -> existing.id().equals(item.id()));
		items.add(item);
	}

	/**
	 * Returns items matching the specified category filter and search query.
	 */
	public List<CatalogItem> getFilteredItems(@Nullable CosmeticCategory category, String searchQuery) {
		String query = (searchQuery != null) ? searchQuery.trim().toLowerCase(Locale.ROOT) : "";

		List<CatalogItem> filtered = new ArrayList<>();
		for (CatalogItem item : items) {
			if (!item.category().isEnabled()) {
				continue;
			}
			if (category != null && item.category() != category) {
				continue;
			}
			if (!query.isEmpty()) {
				boolean nameMatch = item.name().toLowerCase(Locale.ROOT).contains(query);
				boolean descMatch = item.description().toLowerCase(Locale.ROOT).contains(query);
				boolean idMatch = item.id().toString().toLowerCase(Locale.ROOT).contains(query);
				if (!nameMatch && !descMatch && !idMatch) {
					continue;
				}
			}
			filtered.add(item);
		}
		return filtered;
	}

	/**
	 * Loads cached catalogue metadata from local disk for offline resilience.
	 */
	public synchronized void loadCachedCatalogue() {
		Path metaFile = cacheManager.getMetadataFile();
		if (!Files.exists(metaFile)) {
			return;
		}

		try {
			String content = Files.readString(metaFile, StandardCharsets.UTF_8);
			JsonArray array = JsonParser.parseString(content).getAsJsonArray();
			items.clear();
			for (JsonElement elem : array) {
				if (elem.isJsonObject()) {
					try {
						items.add(CatalogItem.fromJson(elem.getAsJsonObject()));
					} catch (Exception e) {
						Cozmetics.LOGGER.warn("Skipping corrupt catalogue entry: {}", elem);
					}
				}
			}
			Cozmetics.LOGGER.info("Loaded {} catalogue items from local cache", items.size());
		} catch (Exception e) {
			Cozmetics.LOGGER.warn("Failed to read cached catalogue metadata: {}", e.getMessage());
		}
	}

	/**
	 * Persists current catalogue metadata to local cache.
	 */
	public synchronized void saveCachedCatalogue() {
		try {
			JsonArray array = new JsonArray();
			for (CatalogItem item : items) {
				array.add(item.toJson());
			}
			Files.writeString(cacheManager.getMetadataFile(), array.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Cozmetics.LOGGER.warn("Failed to persist catalogue cache: {}", e.getMessage());
		}
	}

	/**
	 * Asynchronously synchronizes the catalogue with the remote backend API.
	 */
	public CompletableFuture<Integer> syncRemoteCatalogue(String apiUrl) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				HttpClient client = HttpClient.newBuilder()
						.connectTimeout(Duration.ofSeconds(6))
						.build();

				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(apiUrl))
						.timeout(Duration.ofSeconds(10))
						.GET()
						.build();

				HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				if (response.statusCode() == 200) {
					JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
					if (json.has("cosmetics") && json.get("cosmetics").isJsonArray()) {
						JsonArray arr = json.getAsJsonArray("cosmetics");
						List<CatalogItem> fresh = new ArrayList<>();
						for (JsonElement elem : arr) {
							if (elem.isJsonObject()) {
								try {
									fresh.add(CatalogItem.fromJson(elem.getAsJsonObject()));
								} catch (Exception ignored) {
								}
							}
						}

						items.clear();
						items.addAll(fresh);
						saveCachedCatalogue();
						Cozmetics.LOGGER.info("Synchronized {} catalogue items from remote backend", fresh.size());
						return fresh.size();
					}
				}
			} catch (Exception e) {
				Cozmetics.LOGGER.debug("Could not reach remote catalogue at {}: {}", apiUrl, e.getMessage());
			}
			return items.size();
		});
	}
}

