package io.github.itsarcn.cozmetics.client.gui;

import io.github.itsarcn.cozmetics.client.cosmetic.ClientCosmeticManager;
import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game Wardrobe screen for previewing, equipping, and managing cosmetics.
 */
public class CosmeticsScreen extends Screen {
	private final Screen parent;
	private CosmeticCategory activeCategory = CosmeticCategory.PET;
	private @Nullable Cosmetic selectedCosmetic = null;

	private CosmeticListWidget listWidget;
	private ButtonWidget equipButton;
	private ButtonWidget visibilityButton;
	private final List<ButtonWidget> categoryTabButtons = new ArrayList<>();

	private float previewRotation = 0.0f;
	private boolean draggingPreview = false;
	private double lastMouseX = 0;

	public CosmeticsScreen(@Nullable Screen parent) {
		super(Text.literal("Cozmetics Wardrobe"));
		this.parent = parent;
	}

	public static void open(@Nullable Screen parent) {
		MinecraftClient.getInstance().setScreen(new CosmeticsScreen(parent));
	}

	@Override
	protected void init() {
		this.categoryTabButtons.clear();

		// 1. Category Tabs at top (y = 20)
		List<CosmeticCategory> enabledCategories = new ArrayList<>();
		for (CosmeticCategory cat : CosmeticCategory.values()) {
			if (cat.isEnabled()) {
				enabledCategories.add(cat);
			}
		}

		int tabHeight = 18;
		int totalTabsWidth = enabledCategories.size() * 46;
		int startTabX = Math.max(10, (this.width - totalTabsWidth) / 2);

		for (int i = 0; i < enabledCategories.size(); i++) {
			CosmeticCategory cat = enabledCategories.get(i);
			int btnX = startTabX + i * 46;
			ButtonWidget tabBtn = ButtonWidget.builder(Text.literal(cat.getDisplayName()), btn -> selectCategory(cat))
					.dimensions(btnX, 22, 44, tabHeight)
					.build();
			this.categoryTabButtons.add(tabBtn);
			addDrawableChild(tabBtn);
		}

		// 2. Middle: Cosmetic List Widget
		int listX = 140;
		int listY = 46;
		int listWidth = Math.max(150, (this.width - 150) / 2);
		int listHeight = this.height - 85;

		this.listWidget = new CosmeticListWidget(this.client, listWidth, listHeight, listY, 28);
		this.listWidget.setDimensionsAndPosition(listWidth, listHeight, listX, listY);
		addDrawableChild(this.listWidget);

		// 3. Right: Action Controls (Equip/Unequip, Visibility toggle, Close)
		int rightPanelX = listX + listWidth + 12;
		int rightPanelWidth = Math.max(100, this.width - rightPanelX - 16);
		int actionsY = this.height - 76;

		this.equipButton = ButtonWidget.builder(Text.literal("Equip"), btn -> toggleEquipSelected())
				.dimensions(rightPanelX, actionsY, rightPanelWidth, 20)
				.build();
		addDrawableChild(this.equipButton);

		this.visibilityButton = ButtonWidget.builder(Text.literal("Visibility"), btn -> toggleCategoryVisibility())
				.dimensions(rightPanelX, actionsY + 24, rightPanelWidth, 20)
				.build();
		addDrawableChild(this.visibilityButton);

		ButtonWidget doneButton = ButtonWidget.builder(Text.literal("Done"), btn -> close())
				.dimensions(rightPanelX, actionsY + 48, rightPanelWidth, 20)
				.build();
		addDrawableChild(doneButton);

		// Populate active category
		selectCategory(this.activeCategory);
	}

	public void selectCategory(CosmeticCategory category) {
		this.activeCategory = category;
		for (ButtonWidget btn : this.categoryTabButtons) {
			btn.active = !btn.getMessage().getString().equalsIgnoreCase(category.getDisplayName());
		}

		this.listWidget.populateCategory(category);

		// Select current equipped if available, or first in category
		Cosmetic equipped = ClientCosmeticManager.getInstance().getEquipped(category);
		if (equipped != null) {
			setSelectedCosmetic(equipped);
		} else {
			List<Cosmetic> list = CosmeticRegistry.getByCategory(category);
			setSelectedCosmetic(!list.isEmpty() ? list.getFirst() : null);
		}
	}

	public void setSelectedCosmetic(@Nullable Cosmetic cosmetic) {
		this.selectedCosmetic = cosmetic;
		this.listWidget.selectCosmetic(cosmetic);
		updateButtons();
	}

	private void updateButtons() {
		ClientCosmeticManager manager = ClientCosmeticManager.getInstance();
		boolean isVisible = manager.isCategoryVisible(this.activeCategory);
		this.visibilityButton.setMessage(Text.literal(isVisible ? "Category: Visible" : "Category: Hidden"));

		if (this.selectedCosmetic == null) {
			this.equipButton.setMessage(Text.literal("Equip"));
			this.equipButton.active = false;
		} else {
			Cosmetic equipped = manager.getEquipped(this.activeCategory);
			boolean isEquipped = equipped != null && equipped.getId().equals(this.selectedCosmetic.getId());
			this.equipButton.setMessage(Text.literal(isEquipped ? "Unequip" : "Equip"));
			this.equipButton.active = true;
		}
	}

	private void toggleEquipSelected() {
		if (this.selectedCosmetic == null) return;
		ClientCosmeticManager manager = ClientCosmeticManager.getInstance();
		Cosmetic equipped = manager.getEquipped(this.activeCategory);
		if (equipped != null && equipped.getId().equals(this.selectedCosmetic.getId())) {
			manager.unequip(this.activeCategory);
		} else {
			manager.equip(this.selectedCosmetic);
		}
		updateButtons();
	}

	private void toggleCategoryVisibility() {
		ClientCosmeticManager.getInstance().toggleCategoryVisible(this.activeCategory);
		updateButtons();
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// 1. Draw Title
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);

		// 2. Draw Player Character Preview on Left
		int previewLeft = 14;
		int previewTop = 46;
		int previewWidth = 114;
		int previewHeight = this.height - 85;

		// Preview background box
		context.fill(previewLeft, previewTop, previewLeft + previewWidth, previewTop + previewHeight, 0x55000000);
		context.drawStrokedRectangle(previewLeft, previewTop, previewWidth, previewHeight, 0x88AAAAAA);

		if (this.client != null && this.client.player != null) {
			int previewCenterX = previewLeft + previewWidth / 2;
			int previewBottomY = previewTop + previewHeight - 12;
			int previewSize = Math.max(30, Math.min(60, previewHeight / 4));

			// Auto rotate slowly if not dragging
			if (!this.draggingPreview) {
				this.previewRotation += delta * 0.8f;
			}

			// Render live player with cosmetics
			InventoryScreen.drawEntity(
					context,
					previewLeft + 4,
					previewTop + 4,
					previewLeft + previewWidth - 4,
					previewTop + previewHeight - 4,
					previewSize,
					0.0625f,
					(float) mouseX,
					(float) mouseY,
					this.client.player
			);
		}

		// Preview label
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Player Preview"), previewLeft + previewWidth / 2, previewTop + 6, 0xAAAAAA);

		// 3. Render cosmetic list & widgets
		super.render(context, mouseX, mouseY, delta);

		// 4. Render selected cosmetic details panel on right
		int listWidth = this.listWidget.getWidth();
		int rightPanelX = this.listWidget.getX() + listWidth + 12;
		int rightPanelTop = 46;

		context.drawTextWithShadow(this.textRenderer, Text.literal("§lCosmetic Details:"), rightPanelX, rightPanelTop, 0xFFAAFF);

		if (this.selectedCosmetic != null) {
			context.drawTextWithShadow(this.textRenderer, Text.literal("§f" + this.selectedCosmetic.getName()), rightPanelX, rightPanelTop + 16, 0xFFFFFF);
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7Category: §e" + this.selectedCosmetic.getCategory().getDisplayName()), rightPanelX, rightPanelTop + 30, 0xCCCCCC);
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7Type: " + (this.selectedCosmetic.isAnimated() ? "§bAnimated" : "§7Static")), rightPanelX, rightPanelTop + 44, 0xCCCCCC);
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7Slot: §a" + this.selectedCosmetic.getAttachmentSlot()), rightPanelX, rightPanelTop + 58, 0xCCCCCC);

			Cosmetic equipped = ClientCosmeticManager.getInstance().getEquipped(this.activeCategory);
			boolean isEquipped = equipped != null && equipped.getId().equals(this.selectedCosmetic.getId());
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7Status: " + (isEquipped ? "§a§lEquipped" : "§cUnequipped")), rightPanelX, rightPanelTop + 72, 0xFFFFFF);
		} else {
			context.drawTextWithShadow(this.textRenderer, Text.literal("§8No cosmetic selected"), rightPanelX, rightPanelTop + 20, 0x888888);
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean bl) {
		int previewLeft = 14;
		int previewTop = 46;
		int previewWidth = 114;
		int previewHeight = this.height - 85;

		if (click.x() >= previewLeft && click.x() <= previewLeft + previewWidth
				&& click.y() >= previewTop && click.y() <= previewTop + previewHeight) {
			this.draggingPreview = true;
			this.lastMouseX = click.x();
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
			this.previewRotation += (float) (click.x() - this.lastMouseX) * 1.5f;
			this.lastMouseX = click.x();
			return true;
		}
		return super.mouseDragged(click, deltaX, deltaY);
	}

	/**
	 * List widget displaying cosmetics of the active category.
	 */
	public class CosmeticListWidget extends AlwaysSelectedEntryListWidget<CosmeticListWidget.CosmeticEntry> {
		public CosmeticListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		public void populateCategory(CosmeticCategory category) {
			clearEntries();
			for (Cosmetic cosmetic : CosmeticRegistry.getByCategory(category)) {
				addEntry(new CosmeticEntry(cosmetic));
			}
		}

		public void selectCosmetic(@Nullable Cosmetic cosmetic) {
			if (cosmetic == null) {
				setSelected(null);
				return;
			}
			for (CosmeticEntry entry : children()) {
				if (entry.cosmetic.getId().equals(cosmetic.getId())) {
					setSelected(entry);
					return;
				}
			}
		}

		public class CosmeticEntry extends AlwaysSelectedEntryListWidget.Entry<CosmeticEntry> {
			private final Cosmetic cosmetic;

			public CosmeticEntry(Cosmetic cosmetic) {
				this.cosmetic = cosmetic;
			}

			@Override
			public Text getNarration() {
				return Text.literal(this.cosmetic.getName());
			}

			@Override
			public void render(DrawContext context, int y, int x, boolean hovered, float tickProgress) {
				int entryX = getContentX();
				int entryY = getContentY();
				int entryWidth = getContentWidth();
				int entryHeight = getContentHeight();

				boolean isSelected = CosmeticsScreen.this.selectedCosmetic != null
						&& CosmeticsScreen.this.selectedCosmetic.getId().equals(this.cosmetic.getId());

				if (isSelected) {
					context.fill(entryX - 2, entryY - 1, entryX + entryWidth + 2, entryY + entryHeight + 1, 0x80335577);
				} else if (hovered) {
					context.fill(entryX - 2, entryY - 1, entryX + entryWidth + 2, entryY + entryHeight + 1, 0x40333333);
				}

				boolean isEquipped = ClientCosmeticManager.getInstance().getEquipped(this.cosmetic.getCategory()) == this.cosmetic;

				// Name
				context.drawTextWithShadow(textRenderer, Text.literal(this.cosmetic.getName()), entryX + 2, entryY + 2, isSelected ? 0xFFFFA0 : 0xFFFFFF);

				// Badges
				String badge = this.cosmetic.isAnimated() ? "§b[ANIM]" : "§7[STATIC]";
				if (isEquipped) {
					badge += " §a[EQUIPPED]";
				}
				context.drawTextWithShadow(textRenderer, Text.literal(badge), entryX + 2, entryY + 14, 0xAAAAAA);
			}

			@Override
			public boolean mouseClicked(Click click, boolean bl) {
				CosmeticsScreen.this.setSelectedCosmetic(this.cosmetic);
				if (bl) { // Double click equips/unequips
					CosmeticsScreen.this.toggleEquipSelected();
				}
				return true;
			}
		}
	}
}
