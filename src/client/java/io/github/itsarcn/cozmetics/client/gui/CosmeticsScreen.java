package io.github.itsarcn.cozmetics.client.gui;

import io.github.itsarcn.cozmetics.catalog.CatalogItem;
import io.github.itsarcn.cozmetics.client.catalog.CosmeticCatalogManager;
import io.github.itsarcn.cozmetics.client.cosmetic.ClientCosmeticManager;
import io.github.itsarcn.cozmetics.client.download.CosmeticAssetDownloader;
import io.github.itsarcn.cozmetics.client.texture.CosmeticTextureManager;
import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import net.minecraft.client.MinecraftClient;
//? if >=1.21.10 {
import io.github.itsarcn.cozmetics.client.render.IsolatedCosmeticRenderState;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Arm;
//?}
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Dedicated Cozmetics Wardrobe Screen.
 *
 * Focuses on a large, interactive 3D character preview displaying equipped cosmetics.
 * Equipped selection is managed externally (by website/backend); this screen solely
 * controls local rendering visibility (Show/Hide/Solo) for equipped cosmetics.
 */
public class CosmeticsScreen extends Screen {
	private final Screen parent;

	private EquippedCosmeticListWidget listWidget;

	// 3D Orbital Rotation (Yaw + Pitch) and Zoom
	private float previewRotationYaw = 0.0f;
	private float previewRotationPitch = 0.0f;
	private float previewZoom = 1.0f;
	private boolean draggingPreview = false;
	private double lastMouseX = 0;
	private double lastMouseY = 0;

	// Bounding coordinates of character preview box
	private int previewLeft;
	private int previewRight;
	private int previewTop;
	private int previewBottom;

	public CosmeticsScreen(@Nullable Screen parent) {
		super(Text.literal("Cozmetics"));
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

		// Responsive layout: 3D Preview takes ~60% of width on left, list & controls take ~40% on right
		int margin = 10;
		int headerHeight = 36;
		int footerHeight = 36;

		int previewAreaWidth = (int) (this.width * 0.58f) - (margin * 2);
		int rightPanelX = margin + previewAreaWidth + margin;
		int rightPanelWidth = this.width - rightPanelX - margin;

		this.previewLeft = margin;
		this.previewTop = headerHeight;
		this.previewRight = margin + previewAreaWidth;
		this.previewBottom = this.height - margin;

		// 1. Top Action Bar above the equipped cosmetics list: [ Show All ] and [ Hide All ]
		int controlButtonY = headerHeight;
		int controlButtonW = (rightPanelWidth - 6) / 2;

		ButtonWidget showAllBtn = ButtonWidget.builder(Text.literal("Show All"), btn -> showAll())
				.dimensions(rightPanelX, controlButtonY, controlButtonW, 18)
				.build();
		addDrawableChild(showAllBtn);

		ButtonWidget hideAllBtn = ButtonWidget.builder(Text.literal("Hide All"), btn -> hideAll())
				.dimensions(rightPanelX + controlButtonW + 6, controlButtonY, controlButtonW, 18)
				.build();
		addDrawableChild(hideAllBtn);

		// 2. Equipped Cosmetics List Widget
		int listTop = controlButtonY + 22;
		int doneButtonHeight = 20;
		int catalogButtonY = this.height - margin - doneButtonHeight - 24;
		int doneButtonY = this.height - margin - doneButtonHeight;
		int listHeight = catalogButtonY - listTop - 6;

		this.listWidget = new EquippedCosmeticListWidget(
				this.client,
				rightPanelWidth,
				listHeight,
				listTop,
				32
		);
		this.listWidget.setDimensionsAndPosition(rightPanelWidth, listHeight, rightPanelX, listTop);
		addDrawableChild(this.listWidget);
		this.listWidget.refresh();

		// 3. Action Buttons at bottom of panel: [ Browse Catalog ] and [ Done ]
		ButtonWidget catalogBtn = ButtonWidget.builder(Text.literal("§e★ Browse Catalog"), btn -> openCatalog())
				.dimensions(rightPanelX, catalogButtonY, rightPanelWidth, 20)
				.build();
		addDrawableChild(catalogBtn);

		ButtonWidget doneButton = ButtonWidget.builder(Text.literal("Done"), btn -> close())
				.dimensions(rightPanelX, doneButtonY, rightPanelWidth, doneButtonHeight)
				.build();
		addDrawableChild(doneButton);
	}

	private void openCatalog() {
		if (this.client != null) {
			this.client.setScreen(new CosmeticCatalogScreen(this));
		}
	}

	private void showAll() {
		ClientCosmeticManager.getInstance().showAll();
		if (this.listWidget != null) {
			this.listWidget.refresh();
		}
	}

	private void hideAll() {
		ClientCosmeticManager.getInstance().hideAll();
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
		// 1. Draw Title and Subtitle with solid alpha (0xFF)
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFA0);
		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("Drag to orbit 360° • Scroll to zoom • Click cosmetic to toggle"),
				this.width / 2,
				20,
				0xFF888888
		);

		// 2. Draw Large Player Character Preview Area
		int previewWidth = this.previewRight - this.previewLeft;
		int previewHeight = this.previewBottom - this.previewTop;

		// Preview background container
		context.fill(this.previewLeft, this.previewTop, this.previewRight, this.previewBottom, 0x55000000);
		drawStroke(context, this.previewLeft, this.previewTop, previewWidth, previewHeight, 0x44AAAAAA);

		// Preview badges & hints (solid alpha 0xFF)
		context.drawTextWithShadow(
				this.textRenderer,
				Text.literal("Player Preview"),
				this.previewLeft + 8,
				this.previewTop + 8,
				0xFFBBBBBB
		);
		context.drawTextWithShadow(
				this.textRenderer,
				Text.literal("◀ 360° Orbit (Drag to rotate) ▶"),
				this.previewLeft + 8,
				this.previewBottom - 16,
				0xFF666666
		);

		// Render Player Character & Equipped Animated Cosmetics
		renderPlayerPreview(context, delta);

		// 3. Render Child Widgets (Equipped Cosmetics List and Buttons)
		super.render(context, mouseX, mouseY, delta);

		// 4. Render graceful empty state indicator when no cosmetics are equipped
		if (ClientCosmeticManager.getInstance().getAllEquipped().isEmpty()) {
			int rightPanelX = this.previewRight + 10;
			int rightPanelWidth = this.width - rightPanelX - 14;
			int centerY = (this.previewTop + this.previewBottom) / 2 - 12;
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7No cosmetics equipped"), rightPanelX + rightPanelWidth / 2, centerY, 0xFFAAAAAA);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§8Items will appear here"), rightPanelX + rightPanelWidth / 2, centerY + 14, 0xFF666666);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§8when equipped from your account"), rightPanelX + rightPanelWidth / 2, centerY + 26, 0xFF555555);
		}
	}

	//? if >=1.21.10 {
	private void renderPlayerPreview(DrawContext context, float delta) {
		PlayerEntityRenderState state = getOrCreateRenderState(delta);
		if (state == null) {
			return;
		}

		int previewHeight = this.previewBottom - this.previewTop;
		int baseSize = (int) (previewHeight / 3.25f);
		int previewSize = Math.max(25, Math.min(300, (int) (baseSize * this.previewZoom)));

		// Full 360-degree rotation: Pitch (vertical tilt) + Yaw (horizontal orbit)
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		rotation.rotateX((float) Math.toRadians(this.previewRotationPitch));
		rotation.rotateY((float) Math.toRadians(this.previewRotationYaw));

		Vector3f offset = new Vector3f(0.0F, state.height / 2.0F - 0.05F, 0.0F);

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
	}

	private PlayerEntityRenderState getOrCreateRenderState(float delta) {
		PlayerEntityRenderState state = null;

		if (this.client != null && this.client.player != null) {
			try {
				var renderer = this.client.getEntityRenderDispatcher().getRenderer(this.client.player);
				if (renderer != null) {
					state = (PlayerEntityRenderState) renderer.getAndUpdateRenderState(this.client.player, delta);
				}
			} catch (Exception ignored) {
				state = null;
			}
		}

		if (state == null) {
			state = new PlayerEntityRenderState();
			SkinTextures skin = null;
			try {
				if (this.client != null && this.client.getGameProfile() != null) {
					skin = this.client.getSkinProvider().supplySkinTextures(this.client.getGameProfile(), true).get();
				}
			} catch (Exception ignored) {
			}
			if (skin == null) {
				skin = DefaultSkinHelper.getSteve();
			}
			state.skinTextures = skin;
			state.width = 0.6f;
			state.height = 1.8f;
			state.baseScale = 1.0f;
			state.standingEyeHeight = 1.62f;
			state.hatVisible = true;
			state.jacketVisible = true;
			state.leftPantsLegVisible = true;
			state.rightPantsLegVisible = true;
			state.leftSleeveVisible = true;
			state.rightSleeveVisible = true;
			state.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
			state.rightArmPose = BipedEntityModel.ArmPose.EMPTY;
			state.preferredArm = Arm.RIGHT;
		}

		// Ensure age advances smoothly in real time even when singleplayer is paused in GUI
		state.age = (System.currentTimeMillis() % 100000000L) / 50.0f;

		// Stable preview pose
		state.bodyYaw = 180.0f;
		state.relativeHeadYaw = 0.0f;
		state.pitch = 0.0f;
		state.light = 0x00F000F0;
		state.shadowPieces.clear();
		state.outlineColor = 0;
		return state;
	}

	@Override
	public boolean mouseClicked(Click click, boolean bl) {
		if (click.x() >= this.previewLeft && click.x() <= this.previewRight
				&& click.y() >= this.previewTop && click.y() <= this.previewBottom) {
			if (bl) { // double click resets zoom and orbit
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
	private void renderPlayerPreview(DrawContext context, float delta) {
		if (this.client == null || this.client.player == null) {
			return;
		}

		int previewWidth = this.previewRight - this.previewLeft;
		int previewHeight = this.previewBottom - this.previewTop;
		int centerX = this.previewLeft + previewWidth / 2;
		int centerY = this.previewBottom - 25;
		int size = (int) (previewHeight / 3.0f * this.previewZoom);

		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		rotation.rotateX((float) Math.toRadians(this.previewRotationPitch));
		rotation.rotateY((float) Math.toRadians(this.previewRotationYaw));

		net.minecraft.client.gui.screen.ingame.InventoryScreen.drawEntity(
				context,
				(float) centerX,
				(float) centerY,
				(float) size,
				new Vector3f(0.0F, 0.0F, 0.0F),
				rotation,
				new Quaternionf(),
				this.client.player
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
			this.previewZoom = (float) Math.max(0.35, Math.min(4.5, this.previewZoom + verticalAmount * 0.12));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	/**
	 * Compact list widget displaying currently equipped cosmetics with direct toggles & thumbnails.
	 */
	public class EquippedCosmeticListWidget extends AlwaysSelectedEntryListWidget<EquippedCosmeticListWidget.Entry> {
		public EquippedCosmeticListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		public void refresh() {
			clearEntries();
			List<Cosmetic> equipped = ClientCosmeticManager.getInstance().getAllEquipped();
			for (Cosmetic cosmetic : equipped) {
				addEntry(new Entry(cosmetic));
			}
		}

		public class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
			private final Cosmetic cosmetic;

			public Entry(Cosmetic cosmetic) {
				this.cosmetic = cosmetic;
				Optional<CatalogItem> catalogItem = CosmeticCatalogManager.getInstance().getItem(cosmetic.getId());
				catalogItem.ifPresent(item -> CosmeticAssetDownloader.getInstance().loadThumbnailAsync(item));
			}

			@Override
			public Text getNarration() {
				return Text.literal(this.cosmetic.getName());
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

				boolean isSelected = EquippedCosmeticListWidget.this.getSelectedOrNull() == this;
				boolean isVisible = ClientCosmeticManager.getInstance().isCosmeticVisible(this.cosmetic.getId());

				// Row hover and active highlights
				if (isSelected) {
					context.fill(entryX, entryY, entryX + entryWidth, entryY + entryHeight, 0x40335577);
				} else if (hovered) {
					context.fill(entryX, entryY, entryX + entryWidth, entryY + entryHeight, 0x25FFFFFF);
				}

				// 1. Interactive Checkbox at [entryX + 4, entryY + 8], size 14x14
				int checkX = entryX + 4;
				int checkY = entryY + 8;
				int checkSize = 14;

				context.fill(checkX, checkY, checkX + checkSize, checkY + checkSize, isVisible ? 0xFF1E5E22 : 0xFF242424);
				drawStroke(context, checkX, checkY, checkSize, checkSize, isVisible ? 0xFF44FF44 : 0xFF555555);
				if (isVisible) {
					context.drawCenteredTextWithShadow(textRenderer, Text.literal("✔"), checkX + 7, checkY + 3, 0xFF44FF44);
				} else {
					context.drawCenteredTextWithShadow(textRenderer, Text.literal("—"), checkX + 7, checkY + 3, 0xFF777777);
				}

				// 2. 2D Thumbnail box from backend at [entryX + 22, entryY + 4], size 24x24
				int thumbX = entryX + 22;
				int thumbY = entryY + 4;
				int thumbSize = 24;

				context.fill(thumbX, thumbY, thumbX + thumbSize, thumbY + thumbSize, 0x66111111);
				drawStroke(context, thumbX, thumbY, thumbSize, thumbSize, isVisible ? 0x8855FF55 : 0x44888888);

				Identifier thumbId = Identifier.of(this.cosmetic.getId().getNamespace(), "textures/thumbnails/" + this.cosmetic.getId().getPath());
				if (CosmeticTextureManager.getInstance().hasTexture(thumbId)) {
					//? if >=1.21.10 {
					context.drawTexture(
							RenderPipelines.GUI_TEXTURED,
							thumbId,
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
							thumbId,
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

				// 3. Dedicated "Solo" Button at far right
				int soloW = 32;
				int soloH = 16;
				int soloX = entryX + entryWidth - soloW - 4;
				int soloY = entryY + 8;

				context.fill(soloX, soloY, soloX + soloW, soloY + soloH, 0xAA223344);
				drawStroke(context, soloX, soloY, soloW, soloH, 0xAA5588AA);
				context.drawCenteredTextWithShadow(textRenderer, Text.literal("Solo"), soloX + soloW / 2, soloY + 4, 0xFF99CCEE);

				// 4. Cosmetic Name beside thumbnail (solid alpha 0xFF)
				int textX = thumbX + thumbSize + 6;
				context.drawTextWithShadow(
						textRenderer,
						Text.literal(this.cosmetic.getName()),
						textX,
						entryY + 4,
						isVisible ? 0xFFFFFFFF : 0xFF888888
				);

				// 5. Category tag + Visibility indicator (solid alpha 0xFF)
				String status = isVisible ? "§a● Shown" : "§c○ Hidden";
				String categoryTag = "§7[" + this.cosmetic.getCategory().getDisplayName() + "] ";
				context.drawTextWithShadow(
						textRenderer,
						Text.literal(categoryTag + status),
						textX,
						entryY + 16,
						isVisible ? 0xFFAAAAAA : 0xFF666666
				);
			}

			//? if >=1.21.10 {
			@Override
			public boolean mouseClicked(Click click, boolean bl) {
				int entryX = getX();
				int entryY = getY();
				int entryWidth = getWidth();
				int soloW = 32;
				int soloH = 16;
				int soloX = entryX + entryWidth - soloW - 4;
				int soloY = entryY + 8;

				// If clicked Solo button or double-clicked the row -> Solo this cosmetic!
				if (bl || (click.x() >= soloX && click.x() <= soloX + soloW
						&& click.y() >= soloY && click.y() <= soloY + soloH)) {
					ClientCosmeticManager.getInstance().solo(this.cosmetic.getId());
					EquippedCosmeticListWidget.this.setSelected(this);
					return true;
				}

				// Otherwise, clicking ANYWHERE on the row (checkbox, icon, text) toggles visibility directly!
				ClientCosmeticManager.getInstance().toggleCosmeticVisible(this.cosmetic.getId());
				EquippedCosmeticListWidget.this.setSelected(this);
				return true;
			}
			//?} else {
			/*
			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				int entryX = getX();
				int entryY = getY();
				int entryWidth = getWidth();
				int soloW = 32;
				int soloH = 16;
				int soloX = entryX + entryWidth - soloW - 4;
				int soloY = entryY + 8;

				if (mouseX >= soloX && mouseX <= soloX + soloW && mouseY >= soloY && mouseY <= soloY + soloH) {
					ClientCosmeticManager.getInstance().solo(this.cosmetic.getId());
					EquippedCosmeticListWidget.this.setSelected(this);
					return true;
				}

				ClientCosmeticManager.getInstance().toggleCosmeticVisible(this.cosmetic.getId());
				EquippedCosmeticListWidget.this.setSelected(this);
				return true;
			}
			*///?}
		}
	}
}
