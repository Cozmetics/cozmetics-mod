package io.github.itsarcn.cozmetics.client.gui;

import io.github.itsarcn.cozmetics.catalog.CatalogItem;
import io.github.itsarcn.cozmetics.client.catalog.CosmeticCatalogManager;
import io.github.itsarcn.cozmetics.client.cosmetic.ClientCosmeticManager;
import io.github.itsarcn.cozmetics.client.download.CosmeticAssetDownloader;
import io.github.itsarcn.cozmetics.client.render.CosmeticModelBounds;
//? if >=1.21.10 {
import io.github.itsarcn.cozmetics.client.render.IsolatedCosmeticRenderState;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
//?}
import io.github.itsarcn.cozmetics.client.texture.CosmeticTextureManager;
import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticRegistry;
import io.github.itsarcn.cozmetics.network.HttpCosmeticsClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * Dedicated Cozmetics Catalogue Screen.
 *
 * Implements a native Minecraft visual style (per PiCapes reference), featuring:
 * - Category navigation tabs & real-time search
 * - Compact cosmetic cards with static lightweight PNG/WebP thumbnails
 * - Dedicated isolated 3D preview viewport with mouse drag orbit & zoom
 * - Instant equip toggling & automated zip asset downloading
 */
public class CosmeticCatalogScreen extends Screen {
	private final Screen parent;
	private final CosmeticCatalogManager catalogManager = CosmeticCatalogManager.getInstance();
	private final CosmeticAssetDownloader assetDownloader = CosmeticAssetDownloader.getInstance();

	private TextFieldWidget searchField;
	private CatalogListWidget listWidget;

	@Nullable
	private CosmeticCategory activeCategory = null; // null = "All"
	private String searchQuery = "";
	@Nullable
	private CatalogItem selectedItem = null;

	// 3D Preview Navigation (Pitch + Yaw rotation, Zoom)
	private float previewRotationYaw = 0.0f;
	private float previewRotationPitch = 0.0f;
	private float previewZoom = 1.0f;
	private boolean draggingPreview = false;
	private double lastMouseX = 0;
	private double lastMouseY = 0;

	// Viewport Layout Columns
	private int colLeftX, colLeftW;
	private int colMidX, colMidW;
	private int colRightX, colRightW;
	private int contentTop, contentBottom;

	// Preview Box boundaries
	private int previewLeft, previewRight, previewTop, previewBottom;

	public CosmeticCatalogScreen(@Nullable Screen parent) {
		super(Text.literal("Cozmetics Catalogue"));
		this.parent = parent;
	}

	private static void drawStroke(DrawContext context, int x, int y, int w, int h, int color) {
		context.fill(x, y, x + w, y + 1, color);
		context.fill(x, y + h - 1, x + w, y + h, color);
		context.fill(x, y + 1, x + 1, y + h - 1, color);
		context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	@Override
	protected void init() {
		super.init();

		int margin = 10;
		int headerH = 26;
		int footerH = 34;

		this.contentTop = headerH;
		this.contentBottom = this.height - footerH;
		int contentH = this.contentBottom - this.contentTop;

		// 3-Column Native Minecraft Layout:
		// Left: 3D Preview (~34%)
		// Center: Cosmetic Cards List (~46%)
		// Right: Category Filter Buttons (~20%)
		int availableW = this.width - (margin * 4);
		this.colLeftW = (int) (availableW * 0.33f);
		this.colRightW = (int) (availableW * 0.20f);
		this.colMidW = availableW - this.colLeftW - this.colRightW;

		this.colLeftX = margin;
		this.colMidX = this.colLeftX + this.colLeftW + margin;
		this.colRightX = this.colMidX + this.colMidW + margin;

		this.previewLeft = this.colLeftX;
		this.previewRight = this.colLeftX + this.colLeftW;
		this.previewTop = this.contentTop + 2;
		this.previewBottom = this.contentTop + (int) (contentH * 0.72f);

		// 1. Center Search Field
		int searchH = 18;
		this.searchField = new TextFieldWidget(
				this.textRenderer,
				this.colMidX,
				this.contentTop,
				this.colMidW,
				searchH,
				Text.literal("Search cosmetics...")
		);
		this.searchField.setPlaceholder(Text.literal("§8Search cosmetics..."));
		this.searchField.setChangedListener(this::onSearchChanged);
		addDrawableChild(this.searchField);

		// 2. Center Cosmetic Cards List
		int listTop = this.contentTop + searchH + 4;
		int listH = this.contentBottom - listTop;
		this.listWidget = new CatalogListWidget(
				this.client,
				this.colMidW,
				listH,
				listTop,
				34
		);
		this.listWidget.setDimensionsAndPosition(this.colMidW, listH, this.colMidX, listTop);
		addDrawableChild(this.listWidget);

		// 3. Right Column: Category Filter Buttons
		int btnY = this.contentTop;
		int btnH = 18;
		int btnSpacing = 20;

		ButtonWidget allBtn = ButtonWidget.builder(
				Text.literal(this.activeCategory == null ? "§e▶ All" : "All"),
				btn -> selectCategory(null)
		).dimensions(this.colRightX, btnY, this.colRightW, btnH).build();
		addDrawableChild(allBtn);
		btnY += btnSpacing;

		for (CosmeticCategory cat : CosmeticCategory.values()) {
			boolean isActive = this.activeCategory == cat;
			String label = (isActive ? "§e▶ " : "") + cat.getDisplayName();
			ButtonWidget catBtn = ButtonWidget.builder(
					Text.literal(label),
					btn -> selectCategory(cat)
			).dimensions(this.colRightX, btnY, this.colRightW, btnH).build();
			addDrawableChild(catBtn);
			btnY += btnSpacing;
		}

		// 4. Bottom Footer Controls
		int footerY = this.height - 26;

		ButtonWidget backBtn = ButtonWidget.builder(Text.literal("Back"), btn -> close())
				.dimensions(margin, footerY, 80, 20)
				.build();
		addDrawableChild(backBtn);

		ButtonWidget refreshBtn = ButtonWidget.builder(Text.literal("↻ Refresh"), btn -> refreshCatalog())
				.dimensions(margin + 86, footerY, 80, 20)
				.build();
		addDrawableChild(refreshBtn);

		// Action button on bottom right: Equip / Unequip selected cosmetic
		ButtonWidget actionBtn = ButtonWidget.builder(
				Text.literal(getSelectedActionLabel()),
				btn -> toggleEquipSelected()
		).dimensions(this.width - margin - 120, footerY, 120, 20).build();
		addDrawableChild(actionBtn);

		// Populate catalogue items
		this.catalogManager.loadCachedCatalogue();
		updateCatalogFilter();

		// Auto-select first item if available and none selected
		if (this.selectedItem == null && !this.catalogManager.getCachedItems().isEmpty()) {
			selectItem(this.catalogManager.getCachedItems().get(0));
		} else if (this.catalogManager.getCachedItems().isEmpty()) {
			refreshCatalog();
		}
	}

	private String getSelectedActionLabel() {
		if (this.selectedItem == null) return "Equip";
		return isEquipped(this.selectedItem) ? "§cUnequip" : "§aEquip";
	}

	private boolean isEquipped(CatalogItem item) {
		return ClientCosmeticManager.getInstance().isCosmeticEquipped(item.id());
	}

	private void toggleEquipSelected() {
		if (this.selectedItem == null) return;

		if (isEquipped(this.selectedItem)) {
			ClientCosmeticManager.getInstance().unequip(this.selectedItem.category());
		} else {
			ClientCosmeticManager.getInstance().equip(this.selectedItem.id(), this.selectedItem.category());
		}

		if (this.listWidget != null) {
			this.listWidget.refresh();
		}
		clearAndInit();
	}

	private void selectCategory(@Nullable CosmeticCategory category) {
		this.activeCategory = category;
		clearAndInit();
	}

	private void onSearchChanged(String query) {
		this.searchQuery = query;
		updateCatalogFilter();
	}

	private void selectItem(CatalogItem item) {
		this.selectedItem = item;
		// Reset rotation and zoom on new selection
		this.previewRotationYaw = 0.0f;
		this.previewRotationPitch = 0.0f;
		this.previewZoom = 1.0f;

		// Ensure 3D asset package is downloaded asynchronously
		this.assetDownloader.downloadAndLoadPackageAsync(item);
	}

	private void refreshCatalog() {
		HttpCosmeticsClient.getInstance().syncCatalogue().whenComplete((count, ex) -> {
			if (this.client != null) {
				this.client.execute(() -> {
					this.catalogManager.loadCachedCatalogue();
					updateCatalogFilter();
					if (this.selectedItem == null && !this.catalogManager.getCachedItems().isEmpty()) {
						selectItem(this.catalogManager.getCachedItems().get(0));
					}
				});
			}
		});
		updateCatalogFilter();
	}

	private void updateCatalogFilter() {
		if (this.listWidget == null) return;

		List<CatalogItem> filtered = this.catalogManager.filter(this.activeCategory, this.searchQuery);
		this.listWidget.setItems(filtered);
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}

	//? if <=1.21.4 {
	/*
	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Prevent super.render() from triggering unwanted blur / darkening over UI text on <=1.21.4
	}
	*///?}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// 1. Dark ambient background tint
		context.fill(0, 0, this.width, this.height, 0xEE111115);

		// 2. Section Headers
		int headerY = 12;
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Preview"), this.colLeftX + this.colLeftW / 2, headerY, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Catalogue"), this.colMidX + this.colMidW / 2, headerY, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Categories"), this.colRightX + this.colRightW / 2, headerY, 0xFFFFFFFF);

		// 3. Isolated 3D Preview Sidebar Container (Left column)
		int previewW = this.previewRight - this.previewLeft;
		int previewH = this.previewBottom - this.previewTop;

		// Subtle outline around isolated preview box
		context.fill(this.previewLeft, this.previewTop, this.previewRight, this.previewBottom, 0x44000000);
		drawStroke(context, this.previewLeft, this.previewTop, previewW, previewH, 0x33AAAAAA);

		// Render 3D Isolated Model
		renderIsolatedPreview(context, delta);

		// 4. Render Selected Item Metadata below 3D preview
		if (this.selectedItem != null) {
			int textX = this.previewLeft + 4;
			int textY = this.previewBottom + 4;

			String name = this.selectedItem.name();
			if (this.textRenderer.getWidth(name) > previewW - 8) {
				name = this.textRenderer.trimToWidth(name, previewW - 12) + "...";
			}
			context.drawTextWithShadow(this.textRenderer, Text.literal("§l" + name), textX, textY, 0xFFFFFFFF);
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7Category: §f" + this.selectedItem.category().getDisplayName()), textX, textY + 11, 0xFFAAAAAA);
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7By: §e" + this.selectedItem.author()), textX, textY + 22, 0xFF888888);

			if (!this.selectedItem.description().isBlank()) {
				String desc = this.selectedItem.description();
				if (this.textRenderer.getWidth(desc) > previewW - 8) {
					desc = this.textRenderer.trimToWidth(desc, previewW - 12) + "...";
				}
				context.drawTextWithShadow(this.textRenderer, Text.literal("§8" + desc), textX, textY + 33, 0xFF777777);
			}
		} else {
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7No cosmetic selected"), this.previewLeft + previewW / 2, this.previewTop + previewH / 2, 0xFF888888);
		}

		// 5. Render Child Widgets (search, catalog list, category buttons, footers)
		super.render(context, mouseX, mouseY, delta);
	}

	//? if >=1.21.10 {
	private void renderIsolatedPreview(DrawContext context, float delta) {
		int previewW = this.previewRight - this.previewLeft;
		int previewH = this.previewBottom - this.previewTop;

		if (this.selectedItem == null) {
			return;
		}

		Optional<Cosmetic> cosmeticOpt = CosmeticRegistry.get(this.selectedItem.id());
		if (cosmeticOpt.isEmpty()) {
			CosmeticCatalogManager.DownloadState state = catalogManager.getDownloadState(this.selectedItem.id());
			String statusText = switch (state) {
				case DOWNLOADING -> "§eDownloading 3D Model...";
				case FAILED -> "§cFailed to load model";
				default -> "§7Click to load preview";
			};
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusText), this.previewLeft + previewW / 2, this.previewTop + previewH / 2, 0xFFAAAAAA);
			return;
		}

		Cosmetic cosmetic = cosmeticOpt.get();
		IsolatedCosmeticRenderState state = new IsolatedCosmeticRenderState(cosmetic, false);
		CosmeticModelBounds bounds = CosmeticModelBounds.get(cosmetic);

		float minDimension = Math.min(previewW, previewH);
		float targetPixels = minDimension * 0.65f;
		float effectiveSpan = Math.max(0.45f, bounds.maxSpan());
		int baseSize = (int) Math.max(25, Math.min(160, targetPixels / effectiveSpan));
		int previewSize = (int) Math.max(20, Math.min(240, (int) (baseSize * this.previewZoom)));

		// 360° Drag Rotation (Pitch + Yaw)
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		rotation.rotateX((float) Math.toRadians(this.previewRotationPitch));
		rotation.rotateY((float) Math.toRadians(this.previewRotationYaw));

		Vector3f offset = state.getIsolatedPreviewOffset();

		// Render through Minecraft's official 3D GUI pipeline with scissor clipping
		context.addEntity(
				state,
				(float) previewSize,
				offset,
				rotation,
				new Quaternionf(),
				this.previewLeft + 2,
				this.previewTop + 2,
				this.previewRight - 2,
				this.previewBottom - 2
		);

		// Orbit hint
		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("§8◀ Drag to Rotate 360° • Scroll Zoom ▶"),
				this.previewLeft + previewW / 2,
				this.previewBottom - 12,
				0xFF555555
		);
	}

	@Override
	public boolean mouseClicked(Click click, boolean bl) {
		if (click.x() >= this.previewLeft && click.x() <= this.previewRight
				&& click.y() >= this.previewTop && click.y() <= this.previewBottom) {
			if (bl) { // double click resets zoom and orientation
				this.previewZoom = 1.0f;
				this.previewRotationYaw = 0.0f;
				this.previewRotationPitch = 0.0f;
				return true;
			}
			this.draggingPreview = true;
			this.lastMouseX = click.x();
			this.lastMouseY = click.y();
			return true;
		}
		return super.mouseClicked(click, bl);
	}

	@Override
	public boolean mouseReleased(Click click) {
		this.draggingPreview = false;
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseDragged(Click click, double deltaX, double deltaY) {
		if (this.draggingPreview) {
			// Responsive, smooth rotation sensitivity (1.35f yaw, 0.90f pitch)
			this.previewRotationYaw += (float) (click.x() - this.lastMouseX) * 1.35f;
			this.previewRotationPitch = Math.max(-85.0f, Math.min(85.0f, this.previewRotationPitch + (float) (click.y() - this.lastMouseY) * 0.90f));
			this.lastMouseX = click.x();
			this.lastMouseY = click.y();
			return true;
		}
		return super.mouseDragged(click, deltaX, deltaY);
	}
	//?} else {
	/*
	private void renderIsolatedPreview(DrawContext context, float delta) {
		int previewW = this.previewRight - this.previewLeft;
		int previewH = this.previewBottom - this.previewTop;

		if (this.selectedItem == null) {
			return;
		}

		Optional<Cosmetic> cosmeticOpt = CosmeticRegistry.get(this.selectedItem.id());
		if (cosmeticOpt.isEmpty()) {
			CosmeticCatalogManager.DownloadState state = catalogManager.getDownloadState(this.selectedItem.id());
			String statusText = switch (state) {
				case DOWNLOADING -> "§eDownloading 3D Model...";
				case FAILED -> "§cFailed to load model";
				default -> "§7Click to load preview";
			};
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusText), this.previewLeft + previewW / 2, this.previewTop + previewH / 2, 0xFFAAAAAA);
			return;
		}

		Cosmetic cosmetic = cosmeticOpt.get();
		int centerX = this.previewLeft + previewW / 2;
		int centerY = this.previewTop + previewH / 2;
		int size = (int) (Math.min(previewW, previewH) * 0.45f * this.previewZoom);

		context.enableScissor(this.previewLeft + 2, this.previewTop + 2, this.previewRight - 2, this.previewBottom - 2);
		try {
			net.minecraft.client.util.math.MatrixStack matrices = context.getMatrices();
			matrices.push();
			matrices.translate(centerX, centerY, 150.0);
			matrices.scale(size, size, -size);
			Quaternionf rotation = new Quaternionf();
			rotation.rotateX((float) Math.toRadians(this.previewRotationPitch));
			rotation.rotateY((float) Math.toRadians(this.previewRotationYaw));
			matrices.multiply(rotation);

			io.github.itsarcn.cozmetics.client.render.dynamic.DynamicCosmeticRenderer renderer =
					io.github.itsarcn.cozmetics.client.render.dynamic.CosmeticRenderManager.getInstance().getRenderer(cosmetic);
			renderer.renderCosmetic(matrices, context.getVertexConsumers(), null, 0x00F000F0, null, null);
			context.getVertexConsumers().draw();
			matrices.pop();
		} finally {
			context.disableScissor();
		}

		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("§8◀ Drag to Rotate 360° • Scroll Zoom ▶"),
				this.previewLeft + previewW / 2,
				this.previewBottom - 12,
				0xFF555555
		);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseX >= this.previewLeft && mouseX <= this.previewRight
				&& mouseY >= this.previewTop && mouseY <= this.previewBottom) {
			if (button == 0) {
				this.draggingPreview = true;
				this.lastMouseX = mouseX;
				this.lastMouseY = mouseY;
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		this.draggingPreview = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.draggingPreview) {
			this.previewRotationYaw += (float) (mouseX - this.lastMouseX) * 1.35f;
			this.previewRotationPitch = Math.max(-85.0f, Math.min(85.0f, this.previewRotationPitch + (float) (mouseY - this.lastMouseY) * 0.90f));
			this.lastMouseX = mouseX;
			this.lastMouseY = mouseY;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}
	*///?}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX >= this.previewLeft && mouseX <= this.previewRight
				&& mouseY >= this.previewTop && mouseY <= this.previewBottom) {
			this.previewZoom = (float) Math.max(0.35f, Math.min(5.5f, this.previewZoom + (float) verticalAmount * 0.15f));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	/**
	 * Scrollable list of compact cosmetic cards.
	 */
	public class CatalogListWidget extends AlwaysSelectedEntryListWidget<CatalogListWidget.Entry> {

		public CatalogListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		public void setItems(List<CatalogItem> items) {
			clearEntries();
			for (CatalogItem item : items) {
				addEntry(new Entry(item));
			}
		}

		public void refresh() {
			// Trigger re-render of rows
		}

		public class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
			private final CatalogItem item;

			public Entry(CatalogItem item) {
				this.item = item;
				// Pull static/rendered thumbnail from backend
				assetDownloader.loadThumbnailAsync(item);
			}

			public CatalogItem getItem() {
				return item;
			}

			@Override
			public Text getNarration() {
				return Text.literal(this.item.name());
			}

			//? if >=1.21.10 {
			@Override
			public void render(DrawContext context, int y, int x, boolean hovered, float tickProgress) {
				int entryX = getX();
				int entryY = getY();
				int entryWidth = getWidth();
				int entryHeight = getHeight();
			//?} else {
			/*@Override
			public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickProgress) {
				int entryX = x;
				int entryY = y;
			*///?}

				boolean isSelected = (selectedItem != null && selectedItem.id().equals(this.item.id()));
				boolean isEquipped = isEquipped(this.item);

				// Selection & Hover background highlight
				if (isSelected) {
					context.fill(entryX, entryY, entryX + entryWidth, entryY + entryHeight, 0x40335577);
					drawStroke(context, entryX, entryY, entryWidth, entryHeight, 0x5566AACC);
				} else if (hovered) {
					context.fill(entryX, entryY, entryX + entryWidth, entryY + entryHeight, 0x20FFFFFF);
				}

				// 1. 2D Thumbnail box from backend (26x26)
				int thumbX = entryX + 4;
				int thumbY = entryY + 4;
				int thumbSize = 26;

				context.fill(thumbX, thumbY, thumbX + thumbSize, thumbY + thumbSize, 0x66111111);
				drawStroke(context, thumbX, thumbY, thumbSize, thumbSize, isEquipped ? 0x8844FF44 : (isSelected ? 0x8866AACC : 0x44888888));

				Identifier thumbId = Identifier.of(this.item.id().getNamespace(), "textures/thumbnails/" + this.item.id().getPath());
				if (CosmeticTextureManager.getInstance().hasTexture(thumbId)) {
					Identifier activeThumb = CosmeticTextureManager.getInstance().getTexturePath(thumbId, System.currentTimeMillis());
					//? if >=1.21.10 {
					context.drawTexture(
							RenderPipelines.GUI_TEXTURED,
							activeThumb,
							thumbX + 1,
							thumbY + 1,
							0.0F,
							0.0F,
							thumbSize - 2,
							thumbSize - 2,
							thumbSize - 2,
							thumbSize - 2
					);
					//?} else {
					/*context.drawTexture(
							activeThumb,
							thumbX + 1,
							thumbY + 1,
							0.0F,
							0.0F,
							thumbSize - 2,
							thumbSize - 2,
							thumbSize - 2,
							thumbSize - 2
					);*///?}
				} else {
					context.drawCenteredTextWithShadow(textRenderer, Text.literal("§8..."), thumbX + thumbSize / 2, thumbY + thumbSize / 2 - 4, 0xFF666666);
				}

				// 2. Cosmetic Name (with safe width trimming)
				int textX = thumbX + thumbSize + 6;
				int btnW = 34;
				int btnH = 16;
				int btnX = entryX + entryWidth - btnW - 4;
				int btnY = entryY + 9;

				int maxTextW = Math.max(30, btnX - textX - 4);
				String displayName = this.item.name();
				if (textRenderer.getWidth(displayName) > maxTextW) {
					displayName = textRenderer.trimToWidth(displayName, maxTextW - 6) + "...";
				}
				context.drawTextWithShadow(
						textRenderer,
						Text.literal(displayName),
						textX,
						entryY + 5,
						isSelected ? 0xFFFFFFA0 : 0xFFFFFFFF
				);

				// 3. Category & Status (with safe width trimming)
				String catTag = "[" + this.item.category().getDisplayName() + "] ";
				String status = isEquipped ? "§a✔" : (CosmeticRegistry.get(this.item.id()).isPresent() ? "§7●" : "§b☁");
				String statusLine = "§7" + catTag + status;
				if (textRenderer.getWidth(statusLine) > maxTextW) {
					statusLine = textRenderer.trimToWidth(statusLine, maxTextW);
				}
				context.drawTextWithShadow(
						textRenderer,
						Text.literal(statusLine),
						textX,
						entryY + 17,
						0xFFAAAAAA
				);

				// 4. Compact Equip button at far right
				int btnBg = isEquipped ? 0xAA442222 : 0xAA224422;
				int btnBorder = isEquipped ? 0xAAFF5555 : 0xAA55FF55;
				context.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
				drawStroke(context, btnX, btnY, btnW, btnH, btnBorder);
				context.drawCenteredTextWithShadow(
						textRenderer,
						Text.literal(isEquipped ? "Off" : "Equip"),
						btnX + btnW / 2,
						btnY + 4,
						isEquipped ? 0xFFFF8888 : 0xFF88FF88
				);
			}

			//? if >=1.21.10 {
			@Override
			public boolean mouseClicked(Click click, boolean bl) {
				int entryX = getX();
				int entryY = getY();
				int entryWidth = getWidth();
				int btnW = 34;
				int btnH = 16;
				int btnX = entryX + entryWidth - btnW - 4;
				int btnY = entryY + 9;

				// Clicking Equip button toggles equip directly
				if (click.x() >= btnX && click.x() <= btnX + btnW && click.y() >= btnY && click.y() <= btnY + btnH) {
					selectItem(this.item);
					toggleEquipSelected();
					return true;
				}

				// Clicking anywhere else on row selects it for preview
				selectItem(this.item);
				return true;
			}
			//?} else {
			/*
			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				int index = CatalogListWidget.this.children().indexOf(this);
				int entryX = CatalogListWidget.this.getRowLeft();
				int entryY = CatalogListWidget.this.getRowTop(index);
				int entryWidth = CatalogListWidget.this.getRowWidth();
				int btnW = 34;
				int btnH = 16;
				int btnX = entryX + entryWidth - btnW - 4;
				int btnY = entryY + 9;

				if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
					selectItem(this.item);
					toggleEquipSelected();
					return true;
				}

				selectItem(this.item);
				return true;
			}
			*///?}
		}
	}
}
