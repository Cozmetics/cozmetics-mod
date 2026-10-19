package io.github.itsarcn.cozmetics.catalog;

import com.google.gson.JsonObject;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Metadata record for a single cosmetic item in the catalogue.
 *
 * Fully decoupled from internal 3D assets: updates to name, category, thumbnail,
 * author, or description take effect immediately upon catalogue sync without
 * requiring the mod to re-download the 3D asset package or be rebuilt.
 */
public record CatalogItem(
		Identifier id,
		String name,
		CosmeticCategory category,
		String version,
		@Nullable String hash,
		String packageUrl,
		@Nullable String thumbnailUrl,
		String description,
		String author
) {

	public static CatalogItem fromJson(JsonObject json) {
		String idStr = json.has("id") ? json.get("id").getAsString() : "";
		if (idStr.isBlank()) {
			throw new IllegalArgumentException("Catalogue item missing 'id'");
		}
		Identifier id = idStr.contains(":") ? Identifier.of(idStr) : Identifier.of("cozmetics", idStr);

		String name = json.has("name") ? json.get("name").getAsString() : id.getPath();
		String catStr = json.has("category") ? json.get("category").getAsString().toUpperCase() : "HEADWEAR";
		CosmeticCategory category;
		try {
			category = CosmeticCategory.valueOf(catStr);
		} catch (Exception ignored) {
			category = CosmeticCategory.HEADWEAR;
		}

		String version = json.has("version") ? json.get("version").getAsString() : "1.0.0";
		String hash = json.has("hash") && !json.get("hash").isJsonNull() ? json.get("hash").getAsString() : null;
		String packageUrl = json.has("package_url") ? json.get("package_url").getAsString() : "";
		String thumbUrl = json.has("thumbnail_url") && !json.get("thumbnail_url").isJsonNull()
				? json.get("thumbnail_url").getAsString() : null;

		String desc = json.has("description") ? json.get("description").getAsString() : "";
		String author = json.has("author") ? json.get("author").getAsString() : "Cozmetics";

		return new CatalogItem(id, name, category, version, hash, packageUrl, thumbUrl, desc, author);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("id", id.toString());
		json.addProperty("name", name);
		json.addProperty("category", category.name());
		json.addProperty("version", version);
		if (hash != null) json.addProperty("hash", hash);
		json.addProperty("package_url", packageUrl);
		if (thumbnailUrl != null) json.addProperty("thumbnail_url", thumbnailUrl);
		json.addProperty("description", description);
		json.addProperty("author", author);
		return json;
	}
}

