package io.github.itsarcn.cozmetics.test;

import org.junit.jupiter.api.Test;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.util.Iterator;

public class WebPMetadataTest {

	@Test
	void testMetadata() throws Exception {
		File animWebp = new File("assets-desgins/aura/beenTakenCorruptedAura.webp");
		try (ImageInputStream in = ImageIO.createImageInputStream(animWebp)) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			ImageReader reader = readers.next();
			reader.setInput(in);
			int numImages = reader.getNumImages(true);
			System.out.println("Num images: " + numImages);

			IIOMetadata streamMeta = reader.getStreamMetadata();
			if (streamMeta != null) {
				printNode(streamMeta.getAsTree(streamMeta.getNativeMetadataFormatName()), 0);
			}

			for (int i = 0; i < numImages; i++) {
				System.out.println("--- Frame " + i + " ---");
				IIOMetadata meta = reader.getImageMetadata(i);
				if (meta != null) {
					String format = meta.getNativeMetadataFormatName();
					System.out.println("Format: " + format);
					if (format != null) {
						printNode(meta.getAsTree(format), 0);
					}
				}
			}
		}
	}

	private void printNode(Node node, int indent) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < indent; i++) sb.append("  ");
		sb.append("<").append(node.getNodeName());
		NamedNodeMap attrs = node.getAttributes();
		if (attrs != null) {
			for (int i = 0; i < attrs.getLength(); i++) {
				Node attr = attrs.item(i);
				sb.append(" ").append(attr.getNodeName()).append("=\"").append(attr.getNodeValue()).append("\"");
			}
		}
		sb.append(">");
		System.out.println(sb.toString());

		for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
			printNode(child, indent + 1);
		}
	}
}

