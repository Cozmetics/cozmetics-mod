package io.github.itsarcn.cozmetics.client.gui;

import io.github.itsarcn.cozmetics.catalog.CatalogItem;
import io.github.itsarcn.cozmetics.client.catalog.CosmeticCatalogManager;
import io.github.itsarcn.cozmetics.client.cosmetic.ClientCosmeticManager;
import io.github.itsarcn.cozmetics.client.download.CosmeticAssetDownloader;
import io.github.itsarcn.cozmetics.client.render.IsolatedCosmeticRenderState;
import io.github.itsarcn.cozmetics.client.texture.CosmeticTextureManager;
import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
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
 * - Isolated 3D cosmetic-only preview sidebar with 360° mouse orbit and zoom
 * - On-demand ZIP package downloads with verification and local caching
 */
public class CosmeticCatalogScreen extends Screen {

	private final Screen parent;
	private final CosmeticCatalogManager catalogManager;
	private final CosmeticAssetDownloader assetDownloader;
	private final ClientCosmeticManager clientCosmeticManager;

	private TextFieldWidget searchBox;
	private CatalogListWidget listWidget;
	@Nullable private CosmeticCategory activeCategoryFilter = null; // null = ALL
	@Nullable private CatalogItem selectedItem = null;

	// 3D Orbital rotation & zoom for the isolated preview
	private float previewRotationYaw = 0.0f;
	private float previewRotationPitch = 0.0f;
	private float previewZoom = 1.0f;
	private boolean draggingPreview = false;
	private double lastMouseX = 0;
	private double lastMouseY = 0;

	private int previewLeft;
	private int previewTop;
	private int previewRight;
	private int previewBottom;

	private record CategoryButton(ButtonWidget button, @Nullable CosmeticCategory category, String displayName) {}
	private final List<CategoryButton> categoryButtons = new ArrayList<>();
	private ButtonWidget sidebarEquipButton;

	private int colLeftX;
	private int colLeftW;
	private int colMidX;
	private int colMidW;
	private int colRightX;
	private int colRightW;

	public CosmeticCatalogScreen(@Nullable Screen parent) {
		super(Text.literal("Cozmetics Catalogue"));
		this.parent = parent;
		this.catalogManager = CosmeticCatalogManager.getInstance();
		this.assetDownloader = CosmeticAssetDownloader.getInstance();
		this.clientCosmeticManager = ClientCosmeticManager.getInstance();
	}

	@Override
	protected void init() {
		this.categoryButtons.clear();

		// Calculate 3-Column Layout:
		// Left: 3D Preview + Details + Equip button (~32% width)
		// Right: Category Selection column (~75px width)
		// Middle: Search Box + Scrollable Cards (remaining width)
		this.colLeftW = Math.max(130, Math.min(210, (int) (this.width * 0.32f)));
		this.colLeftX = 10;

		this.colRightW = 75;
		this.colRightX = this.width - this.colRightW - 10;

		this.colMidX = this.colLeftX + this.colLeftW + 8;
		this.colMidW = Math.max(120, this.colRightX - 8 - this.colMidX);

		int commonTop = 22;
		int commonBottom = this.height - 34;

		// 1. LEFT COLUMN: 3D Isolated Preview Box & Details & Equip button
		// Extends all the way down so its height matches the middle & right columns
		this.previewLeft = this.colLeftX;
		this.previewRight = this.colLeftX + this.colLeftW;
		this.previewTop = commonTop;

		int equipBtnH = 20;
		int equipBtnY = commonBottom - equipBtnH;
		int detailsH = 44;
		this.previewBottom = Math.max(this.previewTop + 60, equipBtnY - detailsH - 4);

		this.sidebarEquipButton = ButtonWidget.builder(
				Text.literal("Equip Cosmetic"),
				btn -> toggleEquipSelected()
		).dimensions(this.previewLeft + 2, equipBtnY, this.colLeftW - 4, equipBtnH).build();
		this.sidebarEquipButton.active = false;
		addDrawableChild(this.sidebarEquipButton);

		// 2. MIDDLE COLUMN: Search Box at top + Scrollable Catalog Cards
		this.searchBox = new TextFieldWidget(
				this.textRenderer,
				this.colMidX,
				commonTop,
				this.colMidW,
				18,
				Text.literal("Search")
		);
		this.searchBox.setPlaceholder(Text.literal("Search cosmetics..."));
		this.searchBox.setChangedListener(query -> refreshList());
		addDrawableChild(this.searchBox);

		int listTop = 44;
		int listHeight = Math.max(60, commonBottom - listTop);

		this.listWidget = new CatalogListWidget(
				this.client,
				this.colMidW,
				listHeight,
				listTop,
				34
		);
		this.listWidget.setDimensionsAndPosition(this.colMidW, listHeight, this.colMidX, listTop);
		addDrawableChild(this.listWidget);

		// 3. RIGHT COLUMN: Vertical Category Selection Stack
		int availableCatHeight = commonBottom - commonTop;
		int maxCatButtons = 9;
		int catBtnH = Math.max(16, Math.min(20, (availableCatHeight - ((maxCatButtons - 1) * 2)) / maxCatButtons));
		int catSpacing = catBtnH + 2;

		int currCatY = commonTop;
		ButtonWidget allBtn = ButtonWidget.builder(
				getCategoryTabText(null, "All"),
				btn -> selectCategory(null)
		).dimensions(this.colRightX, currCatY, this.colRightW, catBtnH).build();
		addDrawableChild(allBtn);
		this.categoryButtons.add(new CategoryButton(allBtn, null, "All"));
		currCatY += catSpacing;

		CosmeticCategory[] displayOrder = new CosmeticCategory[] {
				CosmeticCategory.PET,
				CosmeticCategory.WINGS,
				CosmeticCategory.AURA,
				CosmeticCategory.HEADWEAR,
				CosmeticCategory.FACE,
				CosmeticCategory.BACK,
				CosmeticCategory.BOOTS,
				CosmeticCategory.FISTS
		};

		for (CosmeticCategory cat : displayOrder) {
			if (!cat.isEnabled()) continue;
			if (currCatY + catBtnH > commonBottom + 2) break;
			String label = cat.getDisplayName();
			ButtonWidget catBtn = ButtonWidget.builder(
					getCategoryTabText(cat, label),
					btn -> selectCategory(cat)
			).dimensions(this.colRightX, currCatY, this.colRightW, catBtnH).build();
			addDrawableChild(catBtn);
			this.categoryButtons.add(new CategoryButton(catBtn, cat, label));
			currCatY += catSpacing;
		}

		// 4. BOTTOM CENTER: Done / Back Button
		ButtonWidget doneButton = ButtonWidget.builder(Text.literal("Done"), btn -> close())
				.dimensions(this.width / 2 - 50, this.height - 26, 100, 20)
				.build();
		addDrawableChild(doneButton);

		refreshList();
	}

	private Text getCategoryTabText(@Nullable CosmeticCategory cat, String label) {
		boolean active = (this.activeCategoryFilter == cat);
		return Text.literal(active ? "§e● " + label : label);
	}

	private void selectCategory(@Nullable CosmeticCategory cat) {
		this.activeCategoryFilter = cat;
		updateCategoryButtonLabels();
		refreshList();
	}

	private void updateCategoryButtonLabels() {
		for (CategoryButton catBtn : this.categoryButtons) {
			catBtn.button.setMessage(getCategoryTabText(catBtn.category, catBtn.displayName));
		}
	}

	private void refreshList() {
		if (this.listWidget == null) return;
		String query = (this.searchBox != null) ? this.searchBox.getText() : "";
		List<CatalogItem> filtered = this.catalogManager.getFilteredItems(this.activeCategoryFilter, query);
		this.listWidget.setItems(filtered);

		if (this.selectedItem == null && !filtered.isEmpty()) {
			selectItem(filtered.getFirst());
		} else if (this.selectedItem != null && !filtered.contains(this.selectedItem)) {
			selectItem(filtered.isEmpty() ? null : filtered.getFirst());
		}
	}

	public void selectItem(@Nullable CatalogItem item) {
		this.selectedItem = item;
		updateSidebarEquipButton();

		if (item != null) {
			// Trigger on-demand package download and load if not yet in registry
			if (CosmeticRegistry.get(item.id()).isEmpty()) {
				catalogManager.setDownloadState(item.id(), CosmeticCatalogManager.DownloadState.DOWNLOADING);
				assetDownloader.downloadAndLoadPackageAsync(item).whenComplete((cosmetic, ex) -> {
					if (ex != null) {
						catalogManager.setDownloadState(item.id(), CosmeticCatalogManager.DownloadState.FAILED);
					} else {
						catalogManager.setDownloadState(item.id(), CosmeticCatalogManager.DownloadState.READY);
					}
					updateSidebarEquipButton();
				});
			}
		}
	}

	private void updateSidebarEquipButton() {
		if (this.sidebarEquipButton == null) return;
		if (this.selectedItem == null) {
			this.sidebarEquipButton.active = false;
			this.sidebarEquipButton.setMessage(Text.literal("Equip Cosmetic"));
			return;
		}

		boolean isEquipped = isEquipped(this.selectedItem);
		this.sidebarEquipButton.active = true;
		this.sidebarEquipButton.setMessage(Text.literal(isEquipped ? "Unequip Cosmetic" : "Equip Cosmetic"));
	}

	private boolean isEquipped(CatalogItem item) {
		Cosmetic equipped = clientCosmeticManager.getEquipped(item.category());
		return equipped != null && equipped.getId().equals(item.id());
	}

	private void toggleEquipSelected() {
		if (this.selectedItem == null) return;
		if (isEquipped(this.selectedItem)) {
			clientCosmeticManager.unequip(this.selectedItem.category());
		} else {
			Optional<Cosmetic> cosmeticOpt = CosmeticRegistry.get(this.selectedItem.id());
			if (cosmeticOpt.isPresent()) {
				clientCosmeticManager.equip(cosmeticOpt.get());
			} else {
				// Ensure downloaded first then equip
				assetDownloader.downloadAndLoadPackageAsync(this.selectedItem).thenAccept(cosmetic -> {
					if (this.client != null) {
						this.client.execute(() -> {
							clientCosmeticManager.equip(cosmetic);
							updateSidebarEquipButton();
						});
					}
				});
			}
		}
		updateSidebarEquipButton();
		if (this.listWidget != null) {
			this.listWidget.refresh();
		}
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// 1. Native Minecraft Screen Background (in-game transparent overlay or title panorama)
		super.render(context, mouseX, mouseY, delta);

		// 2. Title & Column Headers with native Minecraft styling
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFA0);

		// Column section labels
		context.drawTextWithShadow(this.textRenderer, Text.literal("§7Preview"), this.colLeftX + 2, 10, 0xFFAAAAAA);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Catalogue"), this.colMidX + this.colMidW / 2, 10, 0xFFAAAAAA);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§7Categories"), this.colRightX + 2, 10, 0xFFAAAAAA);

		// 3. Isolated 3D Preview Sidebar Container (Left column)
		int previewW = this.previewRight - this.previewLeft;
		int previewH = this.previewBottom - this.previewTop;

		// Subtle outline around isolated preview box
		context.fill(this.previewLeft, this.previewTop, this.previewRight, this.previewBottom, 0x44000000);
		context.drawStrokedRectangle(this.previewLeft, this.previewTop, previewW, previewH, 0x33AAAAAA);

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
	}

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

		int baseSize = Math.max(30, (int) (previewH / 2.2f));
		int previewSize = Math.max(20, Math.min(350, (int) (baseSize * this.previewZoom)));

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
			this.previewRotationYaw += (float) (click.x() - this.lastMouseX) * 0.4f;
			this.previewRotationPitch = Math.max(-85.0f, Math.min(85.0f, this.previewRotationPitch + (float) (click.y() - this.lastMouseY) * 0.4f));
			this.lastMouseX = click.x();
			this.lastMouseY = click.y();
			return true;
		}
		return super.mouseDragged(click, deltaX, deltaY);
	}

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
			private IsolatedCosmeticRenderState thumbnailRenderState;

			public Entry(CatalogItem item) {
				this.item = item;
				// Ensure cosmetic package is loaded into runtime registry so 3D thumbnail can render immediately
				if (CosmeticRegistry.get(item.id()).isEmpty()) {
					assetDownloader.downloadAndLoadPackageAsync(item);
				}
			}

			public CatalogItem getItem() {
				return item;
			}

			@Override
			public Text getNarration() {
				return Text.literal(this.item.name());
			}

			@Override
			public void render(DrawContext context, int y, int x, boolean hovered, float tickProgress) {
				int entryX = getX();
				int entryY = getY();
				int entryWidth = getWidth();
				int entryHeight = getHeight();

				boolean isSelected = (selectedItem != null && selectedItem.id().equals(this.item.id()));
				boolean isEquipped = isEquipped(this.item);

				// Selection & Hover background highlight
				if (isSelected) {
					context.fill(entryX, entryY, entryX + entryWidth, entryY + entryHeight, 0x40335577);
					context.drawStrokedRectangle(entryX, entryY, entryWidth, entryHeight, 0x5566AACC);
				} else if (hovered) {
					context.fill(entryX, entryY, entryX + entryWidth, entryY + entryHeight, 0x20FFFFFF);
				}

				// 1. Rendered 3D Thumbnail box (26x26)
				int thumbX = entryX + 4;
				int thumbY = entryY + 4;
				int thumbSize = 26;

				context.fill(thumbX, thumbY, thumbX + thumbSize, thumbY + thumbSize, 0x66111111);
				context.drawStrokedRectangle(thumbX, thumbY, thumbSize, thumbSize, isEquipped ? 0x8844FF44 : (isSelected ? 0x8866AACC : 0x44888888));

				Optional<Cosmetic> cosmeticOpt = CosmeticRegistry.get(this.item.id());
				if (cosmeticOpt.isPresent()) {
					if (this.thumbnailRenderState == null || !this.thumbnailRenderState.getCosmetic().getId().equals(this.item.id())) {
						this.thumbnailRenderState = new IsolatedCosmeticRenderState(cosmeticOpt.get(), true);
					}

					int listTop = listWidget.getY();
					int listBottom = listWidget.getY() + listWidget.getHeight();
					int clipMinY = Math.max(listTop + 1, thumbY + 1);
					int clipMaxY = Math.min(listBottom - 1, thumbY + thumbSize - 1);

					if (clipMinY < clipMaxY && thumbY + thumbSize > listTop && thumbY < listBottom) {
						Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
						rotation.rotateX((float) Math.toRadians(12.0f));
						rotation.rotateY((float) Math.toRadians(35.0f));

						Vector3f offset = this.thumbnailRenderState.getIsolatedPreviewOffset();

						context.addEntity(
								this.thumbnailRenderState,
								11.5f,
								offset,
								rotation,
								new Quaternionf(),
								thumbX + 1,
								clipMinY,
								thumbX + thumbSize - 1,
								clipMaxY
						);
					}
				} else {
					context.drawCenteredTextWithShadow(textRenderer, Text.literal("§83D"), thumbX + thumbSize / 2, thumbY + thumbSize / 2 - 4, 0xFF666666);
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
				context.drawStrokedRectangle(btnX, btnY, btnW, btnH, btnBorder);
				context.drawCenteredTextWithShadow(
						textRenderer,
						Text.literal(isEquipped ? "Off" : "Equip"),
						btnX + btnW / 2,
						btnY + 4,
						isEquipped ? 0xFFFF8888 : 0xFF88FF88
				);
			}

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
		}
	}
}

