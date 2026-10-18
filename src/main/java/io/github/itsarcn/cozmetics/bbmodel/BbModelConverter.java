package io.github.itsarcn.cozmetics.bbmodel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.itsarcn.cozmetics.Cozmetics;

import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Utility tool for converting Blockbench (.bbmodel) files into GeckoLib-ready
 * Bedrock geometry (.geo.json), animation (.animation.json), and extracted texture (.png) files.
 *
 * Can be used as a CLI utility or invoked programmatically during development/asset pipelines.
 */
public final class BbModelConverter {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private BbModelConverter() {}

	public record ConversionResult(
			boolean success,
			String modelName,
			Path geometryPath,
			Path animationPath,
			Map<String, Path> texturePaths
	) {}

	/**
	 * Converts a .bbmodel file on disk into Bedrock geometry, animation, and textures in the output directory.
	 */
	public static ConversionResult convert(Path bbmodelFile, Path outputDirectory) throws Exception {
		if (!Files.exists(bbmodelFile)) {
			throw new IllegalArgumentException("Input .bbmodel file does not exist: " + bbmodelFile);
		}

		Files.createDirectories(outputDirectory);

		BbModel model;
		try (InputStream in = Files.newInputStream(bbmodelFile)) {
			model = BbModelParser.parse(in);
		}

		String baseName = model.getName();

		// 1. Write Geometry JSON
		JsonObject geoJson = BbModelAdapter.toBedrockGeometry(model);
		Path geoPath = outputDirectory.resolve(baseName + ".geo.json");
		try (Writer writer = Files.newBufferedWriter(geoPath)) {
			GSON.toJson(geoJson, writer);
		}

		// 2. Write Animations JSON
		JsonObject animJson = BbModelAdapter.toBedrockAnimations(model);
		Path animPath = outputDirectory.resolve(baseName + ".animation.json");
		try (Writer writer = Files.newBufferedWriter(animPath)) {
			GSON.toJson(animJson, writer);
		}

		// 3. Extract and write embedded textures
		Map<String, Path> textureMap = new java.util.HashMap<>();
		Map<String, byte[]> textures = BbModelAdapter.extractEmbeddedTextures(model);
		for (Map.Entry<String, byte[]> entry : textures.entrySet()) {
			String texFileName = entry.getKey().endsWith(".png") ? entry.getKey() : entry.getKey() + ".png";
			Path texPath = outputDirectory.resolve(texFileName);
			Files.write(texPath, entry.getValue());
			textureMap.put(entry.getKey(), texPath);
		}

		Cozmetics.LOGGER.info("Converted .bbmodel '{}' -> geometry: {}, animations: {}, textures: {}",
				bbmodelFile.getFileName(), geoPath.getFileName(), animPath.getFileName(), textureMap.size());

		return new ConversionResult(true, baseName, geoPath, animPath, textureMap);
	}
}

