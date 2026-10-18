package com.felpz.refugio.menu;

import com.felpz.refugio.data.RefugioPlayerData;
import com.felpz.refugio.quest.RefugioQuests;
import com.felpz.refugio.quest.RefugioSideQuests;
import com.felpz.refugio.shop.ShopCatalog;
import com.felpz.refugio.shop.ShopCatalog.Category;
import com.felpz.refugio.shop.ShopCatalog.EnchantmentSpec;
import com.felpz.refugio.shop.ShopCatalog.Offer;
import com.felpz.refugio.ui.RefugioUiService;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RefugioMenu extends ChestMenu {
    public enum AfterMode { SHOP, QUESTS, GARDEN_QUESTS, EXPEDITION_QUESTS }
    private enum View { SHOP_HOME, SUBCATEGORIES, CATEGORY, SEARCH, PRODUCT_DETAIL, SELL, SELL_CONFIRM, QUESTS, GARDEN_QUESTS, EXPEDITION_QUESTS }
    private static final int CONTAINER_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int[] SUBCATEGORY_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34, 37, 39, 41, 43};
    private static final int[] SELL_DEPOSIT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};
    private static final Map<UUID, RefugioMenu> PENDING_CUSTOM_QUANTITY = new ConcurrentHashMap<>();
    private static final Map<UUID, RefugioMenu> PENDING_CUSTOM_SELL_QUANTITY = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> ACTIVE_TRANSACTIONS = ConcurrentHashMap.newKeySet();

    private final ServerPlayer owner;
    private final SimpleContainer buttons;
    private final Map<Integer, ItemStack> originalSellStacks = new HashMap<>();
    private View view;
    private AfterMode afterMode;
    private int categoryIndex;
    private String selectedSubcategory;
    private String searchTerm = "";
    private int sortMode;
    private View previousView = View.CATEGORY;
    private Offer selectedOffer;
    private int selectedQuantity = 1;
    private int selectedInventorySlot = -1;
    private String selectedSellItemId;
    private ItemStack selectedSellSnapshot = ItemStack.EMPTY;
    private int selectedSellQuantity = 1;
    private int page;
    private boolean menuOpened;

    private RefugioMenu(int containerId, Inventory inventory, ServerPlayer owner, SimpleContainer buttons,
                        View view, AfterMode afterMode, int categoryIndex, int page) {
        super(MenuType.GENERIC_9x6, containerId, inventory, buttons, 6);
        this.owner = owner;
        this.buttons = buttons;
        this.view = view;
        this.afterMode = afterMode;
        this.categoryIndex = categoryIndex;
        this.page = page;
        render();
    }

    public static void openShop(ServerPlayer player) {
        open(player, View.SHOP_HOME, AfterMode.SHOP, Component.translatable("gui.refugio.shop_title"));
    }

    public static void openQuests(ServerPlayer player) {
        open(player, View.QUESTS, AfterMode.QUESTS, Component.translatable("gui.refugio.quest_title"));
    }

    public static void openSideQuests(ServerPlayer player, RefugioSideQuests.Track track) {
        AfterMode after = track == RefugioSideQuests.Track.GARDEN ? AfterMode.GARDEN_QUESTS : AfterMode.EXPEDITION_QUESTS;
        View target = track == RefugioSideQuests.Track.GARDEN ? View.GARDEN_QUESTS : View.EXPEDITION_QUESTS;
        String title = track == RefugioSideQuests.Track.GARDEN
                ? "gui.refugio.garden_quests_title" : "gui.refugio.expedition_quests_title";
        open(player, target, after, Component.translatable(title));
    }

    private static void open(ServerPlayer player, View initialView, AfterMode afterMode, Component title) {
        player.openMenu(new SimpleMenuProvider((id, inventory, openPlayer) ->
                new RefugioMenu(id, inventory, (ServerPlayer) openPlayer, new SimpleContainer(CONTAINER_SIZE),
                        initialView, afterMode, 0, 0), title));
        if (player.containerMenu instanceof RefugioMenu opened) {
            opened.onMenuOpened();
        }
    }

    public void onMenuOpened() {
        this.menuOpened = true;
        broadcastChanges();
        syncMenuState();
    }

    public boolean isShopView() {
        return view == View.SHOP_HOME || view == View.SUBCATEGORIES
                || view == View.CATEGORY || view == View.SEARCH || view == View.PRODUCT_DETAIL
                || view == View.SELL || view == View.SELL_CONFIRM;
    }

    public boolean isQuestView() {
        return view == View.QUESTS || view == View.GARDEN_QUESTS || view == View.EXPEDITION_QUESTS;
    }

    public void syncMenuState() {
        if (isShopView()) {
            syncShopState();
        } else if (isQuestView()) {
            syncQuestState();
        }
    }

    public void handleClientClick(int slotId, int mouseButton, boolean quickMove) {
        if (!stillValid(owner)) return;
        ContainerInput input = quickMove ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
        clicked(slotId, mouseButton, input, owner);
        broadcastChanges();
        syncMenuState();
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner && player.isAlive();
    }

    @Override
    public void removed(Player player) {
        if (view == View.SELL) returnDepositedItems();
        super.removed(player);
        if (player == owner) {
            RefugioUiService.sendShopClosed(owner, containerId);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        if (player != owner || view != View.SELL) {
            return ItemStack.EMPTY;
        }
        if (isSellDepositSlot(slot)) {
            ItemStack source = buttons.getItem(slot);
            if (source.isEmpty()) return ItemStack.EMPTY;
            ItemStack original = source.copy();
            if (!moveItemStackTo(source, CONTAINER_SIZE, CONTAINER_SIZE + 36, true)) {
                return ItemStack.EMPTY;
            }
            if (source.isEmpty()) buttons.setItem(slot, ItemStack.EMPTY);
            else getSlot(slot).setChanged();
            return original;
        }
        if (slot < CONTAINER_SIZE || slot >= CONTAINER_SIZE + 36) {
            return ItemStack.EMPTY;
        }
        ItemStack source = getSlot(slot).getItem();
        if (source.isEmpty()) return ItemStack.EMPTY;
        ItemStack original = source.copy();
        int remaining = source.getCount();
        for (int depositSlot : SELL_DEPOSIT_SLOTS) {
            ItemStack target = buttons.getItem(depositSlot);
            if (target.isEmpty() || !ItemStack.isSameItemSameComponents(target, source)) continue;
            int moved = Math.min(remaining, Math.max(0, target.getMaxStackSize() - target.getCount()));
            if (moved > 0) {
                target.grow(moved);
                remaining -= moved;
            }
            if (remaining == 0) break;
        }
        for (int depositSlot : SELL_DEPOSIT_SLOTS) {
            if (remaining == 0) break;
            if (!buttons.getItem(depositSlot).isEmpty()) continue;
            int moved = Math.min(remaining, source.getMaxStackSize());
            buttons.setItem(depositSlot, source.copyWithCount(moved));
            remaining -= moved;
        }
        if (remaining == original.getCount()) return ItemStack.EMPTY;
        source.setCount(remaining);
        if (source.isEmpty()) getSlot(slot).set(ItemStack.EMPTY);
        else getSlot(slot).setChanged();
        return original;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (player != owner) return;
        if (slotId >= 0 && slotId < CONTAINER_SIZE) {
            if (view == View.SELL && isSellDepositSlot(slotId)) {
                undecorateSellSlots();
                super.clicked(slotId, button, input, player);
                decorateSellSlots();
                renderSellSummary();
                broadcastChanges();
                syncShopState();
                return;
            }
            if (input == ContainerInput.PICKUP) handleButton(slotId, button);
            return;
        }
        if (view == View.SELL_CONFIRM && slotId >= CONTAINER_SIZE) {
            return;
        }
        if (view == View.SELL && input == ContainerInput.QUICK_MOVE
                && slotId >= CONTAINER_SIZE && slotId < CONTAINER_SIZE + 36) {
            undecorateSellSlots();
            super.clicked(slotId, button, input, player);
            decorateSellSlots();
            renderSellSummary();
            broadcastChanges();
            syncShopState();
            return;
        }
        if (input == ContainerInput.QUICK_MOVE) return;
        super.clicked(slotId, button, input, player);
        if (view == View.SELL) {
            broadcastChanges();
            syncShopState();
        }
    }

    private void handleButton(int slot, int mouseButton) {
        switch (view) {
            case SHOP_HOME -> clickHome(slot);
            case SUBCATEGORIES -> clickSubcategories(slot);
            case CATEGORY -> clickCategory(slot, mouseButton);
            case SEARCH -> clickSearch(slot);
            case PRODUCT_DETAIL -> clickProductDetail(slot);
            case SELL -> clickSellMenu(slot);
            case SELL_CONFIRM -> clickSellConfirm(slot);
            case QUESTS -> clickQuest(slot);
            case GARDEN_QUESTS -> clickSideQuest(slot, RefugioSideQuests.Track.GARDEN);
            case EXPEDITION_QUESTS -> clickSideQuest(slot, RefugioSideQuests.Track.EXPEDITION);
        }
    }

    private void clickHome(int slot) {
        int[] categorySlots = {10, 12, 14, 16, 28};
        for (int i = 0; i < categorySlots.length; i++) {
            if (slot != categorySlots[i]) continue;
            if (!RefugioQuests.isCategoryUnlocked(owner, i)) {
                owner.sendSystemMessage(Component.translatable("message.refugio.category_locked"));
                return;
            }
            categoryIndex = i;
            selectedSubcategory = null;
            page = 0;
            view = View.SUBCATEGORIES;
            render();
            return;
        }
        if (slot == 40) {
            openSellView();
        }
    }

    private void clickSubcategories(int slot) {
        List<String> subcategories = ShopCatalog.subcategories(categoryIndex);
        if (slot < PAGE_SIZE) {
            int displayIndex = java.util.Arrays.binarySearch(SUBCATEGORY_SLOTS, slot);
            if (displayIndex < 0) return;
            int index = page * SUBCATEGORY_SLOTS.length + displayIndex;
            if (index < 0 || index >= subcategories.size()) return;
            selectedSubcategory = subcategories.get(index);
            page = 0;
            view = View.CATEGORY;
        } else if (slot == 45) {
            view = View.SHOP_HOME;
            page = 0;
        } else if (slot == 46) {
            page = Math.max(0, page - 1);
        } else if (slot == 47) {
            requestSearch();
            return;
        } else if (slot == 52) {
            int last = Math.max(0, (subcategories.size() - 1) / SUBCATEGORY_SLOTS.length);
            page = Math.min(last, page + 1);
        } else if (slot == 53) {
            openSellView();
            return;
        }
        render();
    }

    private void clickCategory(int slot, int mouseButton) {
        if (slot < PAGE_SIZE) {
            List<Offer> offers = visibleOffers();
            int index = page * PAGE_SIZE + slot;
            if (index >= 0 && index < offers.size()) {
                selectedOffer = offers.get(index);
                selectedQuantity = 1;
                previousView = View.CATEGORY;
                view = View.PRODUCT_DETAIL;
                render();
            }
            return;
        }
        if (slot == 45) {
            view = View.SUBCATEGORIES;
            page = 0;
        } else if (slot == 46) {
            page = Math.max(0, page - 1);
        } else if (slot == 47) {
            requestSearch();
            return;
        } else if (slot == 50) {
            sortMode = (sortMode + 1) % 3;
            page = 0;
        } else if (slot == 52) {
            int last = Math.max(0, (ShopCatalog.offersInSubcategory(categoryIndex, selectedSubcategory).size() - 1) / PAGE_SIZE);
            page = Math.min(last, page + 1);
        } else if (slot == 53) {
            openSellView();
            return;
        }
        render();
    }

    private void clickSellMenu(int slot) {
        if (slot == 45) {
            returnDepositedItems();
            view = View.SHOP_HOME;
            render();
        } else if (slot == 46) {
            returnDepositedItems();
            render();
        } else if (slot == 53) {
            sellDepositedItems();
        }
    }

    private void openSellView() {
        originalSellStacks.clear();
        buttons.clearContent();
        page = 0;
        view = View.SELL;
        render();
    }

    private void clickQuest(int slot) {
        if (slot == 45) {
            view = View.SHOP_HOME;
            render();
            return;
        }
        if (slot == 53) {
            view = View.SHOP_HOME;
            render();
            return;
        }
        int[] questSlots = {10, 12, 14, 16, 28, 30, 32, 34};
        for (int i = 0; i < questSlots.length; i++) {
            if (slot != questSlots[i]) continue;
            int claimed = RefugioPlayerData.getClaimedQuestCount(owner);
            if (i == claimed && RefugioQuests.isAdvancementComplete(owner, i)) {
                if (!RefugioQuests.claimNext(owner)) {
                    owner.sendSystemMessage(Component.translatable("message.refugio.quest_not_ready"));
                }
            } else if (i == claimed) {
                owner.sendSystemMessage(Component.translatable("message.refugio.quest_not_ready"));
            }
            render();
            return;
        }
    }

    private void clickSideQuest(int slot, RefugioSideQuests.Track track) {
        if (slot == 45 || slot == 53) {
            owner.closeContainer();
            return;
        }
        int[] questSlots = {10, 12, 14, 16, 28, 30, 32, 34};
        for (int index = 0; index < questSlots.length; index++) {
            if (slot != questSlots[index]) continue;
            if (index == RefugioSideQuests.progress(owner, track)
                    && !RefugioSideQuests.claimNext(owner, track)) {
                owner.sendSystemMessage(Component.translatable("message.refugio.side_quest_not_ready"));
            }
            render();
            return;
        }
    }

    private void clickProductDetail(int slot) {
        if (slot == 45 || slot == 53) {
            selectedOffer = null;
            view = previousView;
            render();
            return;
        }
        if (slot == 10) selectedQuantity = 1;
        else if (slot == 12) selectedQuantity = 8;
        else if (slot == 14) selectedQuantity = 16;
        else if (slot == 16) selectedQuantity = 32;
        else if (slot == 28) selectedQuantity = 64;
        else if (slot == 30) {
            requestCustomQuantity();
            return;
        } else if (slot == 49) {
            buySelected();
            return;
        } else return;
        if (ShopCatalog.maxStackSize(selectedOffer) == 1 && selectedQuantity > 1) return;
        render();
    }

    private void requestCustomQuantity() {
        if (selectedOffer == null) return;
        PENDING_CUSTOM_QUANTITY.put(owner.getUUID(), this);
        owner.closeContainer();
        owner.sendSystemMessage(Component.translatable("message.refugio.custom_quantity_prompt"));
    }

    private void requestSearch() {
        PENDING_CUSTOM_QUANTITY.put(owner.getUUID(), this);
        owner.closeContainer();
        owner.sendSystemMessage(Component.translatable("message.refugio.search_prompt"));
    }

    public static boolean applySearch(ServerPlayer player, String query) {
        RefugioMenu previous = PENDING_CUSTOM_QUANTITY.remove(player.getUUID());
        if (previous == null || (previous.view != View.CATEGORY && previous.view != View.SUBCATEGORIES)) return false;
        String cleaned = query.strip().toLowerCase(java.util.Locale.ROOT);
        if (cleaned.length() > 48) cleaned = cleaned.substring(0, 48);
        final String searchQuery = cleaned;
        previous.searchTerm = cleaned;
        previous.page = 0;
        previous.view = View.SEARCH;
        player.openMenu(new SimpleMenuProvider((id, inventory, openPlayer) -> {
            RefugioMenu reopened = new RefugioMenu(id, inventory, (ServerPlayer) openPlayer,
                    new SimpleContainer(CONTAINER_SIZE), View.SEARCH, previous.afterMode,
                    previous.categoryIndex, 0);
            reopened.selectedSubcategory = previous.selectedSubcategory;
            reopened.searchTerm = searchQuery;
            reopened.sortMode = previous.sortMode;
            reopened.render();
            return reopened;
        }, Component.translatable("gui.refugio.search_results")));
        if (player.containerMenu instanceof RefugioMenu opened) {
            opened.onMenuOpened();
        }
        return true;
    }

    private List<Offer> visibleOffers() {
        String subcategory = view == View.SEARCH ? null : selectedSubcategory;
        List<Offer> offers = subcategory == null || subcategory.isBlank()
                ? ShopCatalog.offers(categoryIndex)
                : ShopCatalog.offersInSubcategory(categoryIndex, subcategory);
        if (view == View.SEARCH && !searchTerm.isBlank()) {
            String needle = searchTerm;
            offers = offers.stream().filter(offer -> offer.itemId().toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || new ItemStack(item(offer.itemId())).getHoverName().getString()
                    .toLowerCase(java.util.Locale.ROOT).contains(needle)).toList();
        }
        java.util.Comparator<Offer> comparator = switch (sortMode) {
            case 1 -> java.util.Comparator.comparing(offer -> new ItemStack(item(offer.itemId())).getHoverName().getString());
            case 2 -> java.util.Comparator.comparingInt(Offer::price);
            default -> java.util.Comparator.comparing(Offer::itemId);
        };
        return offers.stream().sorted(comparator).toList();
    }

    private void clickSearch(int slot) {
        if (slot < PAGE_SIZE) {
            List<Offer> offers = visibleOffers();
            int index = page * PAGE_SIZE + slot;
            if (index >= 0 && index < offers.size()) {
                selectedOffer = offers.get(index);
                selectedQuantity = 1;
                previousView = View.SEARCH;
                view = View.PRODUCT_DETAIL;
                render();
            }
            return;
        }
        if (slot == 45) { view = View.CATEGORY; page = 0; }
        else if (slot == 46) page = Math.max(0, page - 1);
        else if (slot == 47) { requestSearch(); return; }
        else if (slot == 50) { sortMode = (sortMode + 1) % 3; page = 0; }
        else if (slot == 52) page = Math.min(Math.max(0, (visibleOffers().size() - 1) / PAGE_SIZE), page + 1);
        else if (slot == 53) { openSellView(); return; }
        render();
    }

    public static boolean applyCustomQuantity(ServerPlayer player, int quantity) {
        RefugioMenu previous = PENDING_CUSTOM_QUANTITY.remove(player.getUUID());
        if (previous == null || previous.selectedOffer == null || quantity < 1 || quantity > 2304) return false;
        if (ShopCatalog.maxStackSize(previous.selectedOffer) == 1 && quantity > 1) {
            player.sendSystemMessage(Component.translatable("gui.refugio.quantity_unavailable"));
            return false;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, openPlayer) -> {
            RefugioMenu reopened = new RefugioMenu(id, inventory, (ServerPlayer) openPlayer,
                    new SimpleContainer(CONTAINER_SIZE), View.PRODUCT_DETAIL, previous.afterMode,
                    previous.categoryIndex, previous.page);
            reopened.selectedOffer = previous.selectedOffer;
            reopened.selectedQuantity = quantity;
            reopened.previousView = previous.previousView;
            reopened.selectedSubcategory = previous.selectedSubcategory;
            reopened.render();
            return reopened;
        }, Component.translatable("gui.refugio.shop_title")));
        if (player.containerMenu instanceof RefugioMenu opened) {
            opened.onMenuOpened();
        }
        return true;
    }

    public static boolean applyCustomSellQuantity(ServerPlayer player, int quantity) {
        RefugioMenu previous = PENDING_CUSTOM_SELL_QUANTITY.remove(player.getUUID());
        if (previous == null || previous.selectedInventorySlot < CONTAINER_SIZE || quantity < 1 || quantity > 2304) return false;
        ItemStack current = previous.getSlot(previous.selectedInventorySlot).getItem();
        if (!sameSellItem(previous, current) || quantity > current.getCount()) {
            player.sendSystemMessage(Component.translatable("message.refugio.sell_selection_changed"));
            return false;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, openPlayer) -> {
            RefugioMenu reopened = new RefugioMenu(id, inventory, (ServerPlayer) openPlayer,
                    new SimpleContainer(CONTAINER_SIZE), View.SELL_CONFIRM, previous.afterMode,
                    previous.categoryIndex, previous.page);
            reopened.selectedInventorySlot = previous.selectedInventorySlot;
            reopened.selectedSellItemId = previous.selectedSellItemId;
            reopened.selectedSellSnapshot = previous.selectedSellSnapshot.copy();
            reopened.selectedSellQuantity = quantity;
            reopened.render();
            return reopened;
        }, Component.translatable("gui.refugio.sell_title")));
        if (player.containerMenu instanceof RefugioMenu opened) {
            opened.onMenuOpened();
        }
        return true;
    }

    private void buySelected() {
        if (selectedOffer == null || !ACTIVE_TRANSACTIONS.add(owner.getUUID())) return;
        try {
            if (!RefugioQuests.isCategoryUnlocked(owner, categoryIndex)) {
                owner.sendSystemMessage(Component.translatable("message.refugio.category_locked"));
                return;
            }
            if (!RefugioPlayerData.hasAllShopCategoriesUnlocked(owner)
                    && RefugioPlayerData.getMode(owner) == RefugioPlayerData.MODE_PROGRESSION
                    && RefugioPlayerData.getClaimedQuestCount(owner) < ShopCatalog.requiredClaims(selectedOffer)) {
                owner.sendSystemMessage(Component.translatable("message.refugio.offer_locked",
                        ShopCatalog.requiredClaims(selectedOffer)));
                return;
            }
            if (ShopCatalog.maxStackSize(selectedOffer) == 1 && selectedQuantity != 1) {
                owner.sendSystemMessage(Component.translatable("gui.refugio.quantity_unavailable"));
                return;
            }
            ItemStack[] plannedInventory = simulatePurchase(selectedOffer, selectedQuantity);
            if (plannedInventory == null) {
                owner.sendSystemMessage(Component.translatable("message.refugio.inventory_full"));
                return;
            }
            long totalCostLong = ShopCatalog.totalPrice(selectedOffer, selectedQuantity);
            if (totalCostLong <= 0 || totalCostLong > Integer.MAX_VALUE) return;
            int totalCost = (int) totalCostLong;
            if (!RefugioPlayerData.spendCoins(owner, totalCost)) {
                owner.sendSystemMessage(Component.translatable("message.refugio.not_enough_coins", totalCost,
                        RefugioPlayerData.getCoins(owner)));
                return;
            }
            for (int i = 0; i < plannedInventory.length; i++) owner.getInventory().setItem(i, plannedInventory[i]);
            owner.sendSystemMessage(Component.translatable("message.refugio.purchase_complete", totalCost,
                    RefugioPlayerData.getCoins(owner)));
            selectedOffer = null;
            view = previousView;
            render();
        } finally {
            ACTIVE_TRANSACTIONS.remove(owner.getUUID());
        }
    }

    private ItemStack[] simulatePurchase(Offer offer, int quantity) {
        if (quantity < 1 || quantity > 2304) return null;
        Item item = item(offer.itemId());
        if (item == net.minecraft.world.item.Items.AIR) {
            owner.sendSystemMessage(Component.translatable("message.refugio.item_unavailable"));
            return null;
        }
        ItemStack prototype = new ItemStack(item);
        applyEnchantments(prototype, offer.enchantments());
        ItemStack[] planned = new ItemStack[36];
        for (int i = 0; i < planned.length; i++) planned[i] = owner.getInventory().getItem(i).copy();
        int remaining = quantity;
        for (ItemStack stack : planned) {
            if (remaining <= 0) break;
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, prototype)) continue;
            int capacity = Math.max(0, Math.min(stack.getMaxStackSize(), prototype.getMaxStackSize()) - stack.getCount());
            int moved = Math.min(remaining, capacity);
            if (moved > 0) {
                stack.grow(moved);
                remaining -= moved;
            }
        }
        for (int i = 0; i < planned.length && remaining > 0; i++) {
            if (!planned[i].isEmpty()) continue;
            int moved = Math.min(remaining, prototype.getMaxStackSize());
            planned[i] = prototype.copyWithCount(moved);
            remaining -= moved;
        }
        return remaining == 0 ? planned : null;
    }

    private void selectSellItem(int menuSlot) {
        ItemStack stack = getSlot(menuSlot).getItem();
        if (stack.isEmpty()) return;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (ShopCatalog.sellValue(itemId) <= 0 || hasQuestRewardMarker(stack)) {
            owner.sendSystemMessage(Component.translatable("message.refugio.item_not_sellable"));
            return;
        }
        selectedInventorySlot = menuSlot;
        selectedSellItemId = itemId;
        selectedSellSnapshot = stack.copy();
        selectedSellQuantity = 1;
        view = View.SELL_CONFIRM;
        render();
    }

    private void clickSellConfirm(int slot) {
        if (slot == 45 || slot == 53) {
            view = View.SELL;
            render();
            return;
        }
        ItemStack current = selectedInventorySlot >= CONTAINER_SIZE
                ? getSlot(selectedInventorySlot).getItem() : ItemStack.EMPTY;
        if (!sameSellItem(this, current)) {
            owner.sendSystemMessage(Component.translatable("message.refugio.sell_selection_changed"));
            clearSellSelection();
            view = View.SELL;
            render();
            return;
        }
        if (slot == 10) selectedSellQuantity = 1;
        else if (slot == 12) selectedSellQuantity = 8;
        else if (slot == 14) selectedSellQuantity = 16;
        else if (slot == 16) selectedSellQuantity = 32;
        else if (slot == 28) selectedSellQuantity = 64;
        else if (slot == 30) {
            PENDING_CUSTOM_SELL_QUANTITY.put(owner.getUUID(), this);
            owner.closeContainer();
            owner.sendSystemMessage(Component.translatable("message.refugio.custom_sell_quantity_prompt"));
            return;
        }
        else if (slot == 32) selectedSellQuantity = current.getCount();
        else if (slot == 49) {
            if (!ACTIVE_TRANSACTIONS.add(owner.getUUID())) return;
            try {
                ItemStack latest = getSlot(selectedInventorySlot).getItem();
                int amount = selectedSellQuantity;
                int unitPrice = ShopCatalog.sellValue(selectedSellItemId);
                long totalLong = (long) amount * unitPrice;
                if (!sameSellItem(this, latest) || amount < 1 || amount > latest.getCount()
                        || unitPrice <= 0 || totalLong > Integer.MAX_VALUE
                        || (long) RefugioPlayerData.getCoins(owner) + totalLong > Integer.MAX_VALUE) {
                    owner.sendSystemMessage(Component.translatable("message.refugio.sale_cannot_pay"));
                    return;
                }
                Component name = latest.getHoverName();
                latest.shrink(amount);
                getSlot(selectedInventorySlot).setChanged();
                RefugioPlayerData.addCoins(owner, (int) totalLong);
                owner.sendSystemMessage(Component.translatable("message.refugio.sale_complete", amount, name,
                        totalLong, RefugioPlayerData.getCoins(owner)));
                clearSellSelection();
                view = View.SELL;
                render();
            } finally {
                ACTIVE_TRANSACTIONS.remove(owner.getUUID());
            }
            return;
        } else return;
        selectedSellQuantity = Math.min(selectedSellQuantity, current.getCount());
        render();
    }

    private void clearSellSelection() {
        selectedInventorySlot = -1;
        selectedSellItemId = null;
        selectedSellSnapshot = ItemStack.EMPTY;
        selectedSellQuantity = 1;
    }

    private static boolean sameSellItem(RefugioMenu menu, ItemStack current) {
        return menu.selectedInventorySlot >= CONTAINER_SIZE && !current.isEmpty()
                && menu.selectedSellItemId != null
                && BuiltInRegistries.ITEM.getKey(current.getItem()).toString().equals(menu.selectedSellItemId)
                && ItemStack.isSameItemSameComponents(current, menu.selectedSellSnapshot)
                && !hasQuestRewardMarker(current);
    }

    private static boolean hasQuestRewardMarker(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return false;
        CompoundTag marker = new CompoundTag();
        marker.putBoolean("refugio_quest_reward", true);
        return data.matchedBy(marker);
    }

    private void render() {
        if (view != View.SELL) buttons.clearContent();
        switch (view) {
            case SHOP_HOME -> renderHome();
            case SUBCATEGORIES -> renderSubcategories();
            case CATEGORY -> renderCategory();
            case SEARCH -> renderSearch();
            case PRODUCT_DETAIL -> renderProductDetail();
            case SELL -> renderSell();
            case SELL_CONFIRM -> renderSellConfirm();
            case QUESTS -> renderQuests();
            case GARDEN_QUESTS -> renderSideQuests(RefugioSideQuests.Track.GARDEN);
            case EXPEDITION_QUESTS -> renderSideQuests(RefugioSideQuests.Track.EXPEDITION);
        }
        broadcastChanges();
        if (menuOpened) {
            syncMenuState();
        }
    }

    public void syncQuestState() {
        if (!isQuestView()) return;
        int coins = RefugioPlayerData.getCoins(owner);
        int[] slots = {10, 12, 14, 16, 28, 30, 32, 34};
        StringBuilder entries = new StringBuilder();
        int claimedOrProgress;
        int totalCount;

        if (view == View.QUESTS) {
            claimedOrProgress = RefugioPlayerData.getClaimedQuestCount(owner);
            totalCount = RefugioQuests.count();
            String[] campaignIcons = {
                    "minecraft:stone_pickaxe", "minecraft:iron_chestplate", "minecraft:obsidian",
                    "minecraft:flint_and_steel", "minecraft:blaze_rod", "minecraft:ender_eye",
                    "minecraft:end_portal_frame", "minecraft:dragon_head"
            };
            for (int i = 0; i < totalCount && i < slots.length; i++) {
                RefugioQuests.Mission m = RefugioQuests.mission(i);
                boolean complete = RefugioQuests.isAdvancementComplete(owner, i);
                int state = i < claimedOrProgress ? 0 : (i == claimedOrProgress && complete ? 1 : (i == claimedOrProgress ? 2 : 3));
                boolean rewardVisible = complete || i < claimedOrProgress;
                String iconId = i < campaignIcons.length ? campaignIcons[i] : m.rewardItem();
                String rewardSpec = rewardVisible ? (m.rewardItem() + "^" + m.rewardCount() + "^^0^") : "";
                if (!entries.isEmpty()) entries.append(';');
                entries.append(slots[i]).append('|')
                        .append(i).append('|')
                        .append(state).append('|')
                        .append(m.titleKey()).append('|')
                        .append(m.descriptionKey()).append('|')
                        .append(iconId).append('|')
                        .append(rewardVisible ? 1 : 0).append('|')
                        .append(rewardVisible ? m.coins() : 0).append('|')
                        .append("").append('|')
                        .append(rewardSpec);
            }
        } else {
            RefugioSideQuests.Track track = view == View.GARDEN_QUESTS
                    ? RefugioSideQuests.Track.GARDEN : RefugioSideQuests.Track.EXPEDITION;
            claimedOrProgress = RefugioSideQuests.progress(owner, track);
            totalCount = RefugioSideQuests.count(track);
            for (int i = 0; i < totalCount && i < slots.length; i++) {
                RefugioSideQuests.MissionView m = RefugioSideQuests.mission(track, i);
                boolean complete = RefugioSideQuests.isComplete(owner, track, i);
                int state = i < claimedOrProgress ? 0 : (i == claimedOrProgress && complete ? 1 : (i == claimedOrProgress ? 2 : 3));
                boolean rewardVisible = complete || i < claimedOrProgress;
                String iconId = RefugioSideQuests.objectiveIconId(track, i);
                String progressSpec = RefugioSideQuests.serializeProgressSpecs(owner, track, i);
                String rewardSpec = rewardVisible ? RefugioSideQuests.serializeRewardSpecs(track, i) : "";
                int rewardCoins = rewardVisible ? RefugioSideQuests.coins(track, i) : 0;
                if (!entries.isEmpty()) entries.append(';');
                entries.append(slots[i]).append('|')
                        .append(i).append('|')
                        .append(state).append('|')
                        .append(m.titleKey()).append('|')
                        .append(m.descriptionKey()).append('|')
                        .append(iconId).append('|')
                        .append(rewardVisible ? 1 : 0).append('|')
                        .append(rewardCoins).append('|')
                        .append(progressSpec).append('|')
                        .append(rewardSpec);
            }
        }

        String payload = "QUEST	"
                + containerId + "	"
                + view.name() + "	"
                + coins + "	"
                + claimedOrProgress + "	"
                + totalCount + "	"
                + entries;
        RefugioUiService.sendQuestState(owner, payload);
    }

    public void syncShopState() {
        if (!isShopView()) return;
        int coins = RefugioPlayerData.getCoins(owner);
        int totalItems = 0;
        int totalPages = 1;
        StringBuilder itemsBuilder = new StringBuilder();

        if (view == View.SUBCATEGORIES) {
            List<String> subcategories = ShopCatalog.subcategories(categoryIndex);
            totalItems = subcategories.size();
            totalPages = Math.max(1, (totalItems + SUBCATEGORY_SLOTS.length - 1) / SUBCATEGORY_SLOTS.length);
            int start = page * SUBCATEGORY_SLOTS.length;
            for (int i = 0; i < SUBCATEGORY_SLOTS.length && start + i < subcategories.size(); i++) {
                int slot = SUBCATEGORY_SLOTS[i];
                String key = subcategories.get(start + i);
                List<Offer> products = ShopCatalog.offersInSubcategory(categoryIndex, key);
                String icon = products.isEmpty() ? "minecraft:paper" : products.getFirst().itemId();
                if (!itemsBuilder.isEmpty()) itemsBuilder.append(';');
                itemsBuilder.append(slot).append('|').append(key).append('|')
                        .append(products.size()).append('|').append(icon);
            }
        } else if (view == View.CATEGORY || view == View.SEARCH) {
            List<Offer> offers = visibleOffers();
            totalItems = offers.size();
            totalPages = Math.max(1, (totalItems + PAGE_SIZE - 1) / PAGE_SIZE);
            int start = page * PAGE_SIZE;
            int claimed = RefugioPlayerData.getClaimedQuestCount(owner);
            boolean normalMode = RefugioPlayerData.getMode(owner) == RefugioPlayerData.MODE_PROGRESSION;
            for (int slot = 0; slot < PAGE_SIZE && start + slot < offers.size(); slot++) {
                Offer offer = offers.get(start + slot);
                int req = ShopCatalog.requiredClaims(offer);
                boolean locked = normalMode && !RefugioPlayerData.hasAllShopCategoriesUnlocked(owner) && claimed < req;
                if (!itemsBuilder.isEmpty()) itemsBuilder.append(';');
                itemsBuilder.append(slot).append('|').append(offer.itemId()).append('|')
                        .append(offer.amount()).append('|').append(offer.price()).append('|')
                        .append(req).append('|').append(locked ? 1 : 0).append('|')
                        .append(serializeEnchantmentSpecs(offer.enchantments()));
            }
        } else if (view == View.SELL) {
            for (int slot : SELL_DEPOSIT_SLOTS) {
                ItemStack stack = buttons.getItem(slot);
                if (stack.isEmpty()) continue;
                String id = itemId(stack);
                int unitPrice = sellable(stack) ? ShopCatalog.sellValue(id) : 0;
                if (!itemsBuilder.isEmpty()) itemsBuilder.append(';');
                itemsBuilder.append(slot).append('|').append(id).append('|')
                        .append(stack.getCount()).append('|').append(unitPrice);
            }
            for (int slot = CONTAINER_SIZE; slot < CONTAINER_SIZE + 36; slot++) {
                ItemStack stack = getSlot(slot).getItem();
                if (stack.isEmpty()) continue;
                String id = itemId(stack);
                int unitPrice = sellable(stack) ? ShopCatalog.sellValue(id) : 0;
                if (!itemsBuilder.isEmpty()) itemsBuilder.append(';');
                itemsBuilder.append(slot).append('|').append(id).append('|')
                        .append(stack.getCount()).append('|').append(unitPrice);
            }
        }

        long detailUnitPrice = 0L;
        int detailQty = 1;
        long detailTotal = 0L;
        int detailStackLimit = 64;
        int detailReqClaims = 0;
        boolean detailCanAction = false;
        String detailItemId = "minecraft:air";
        String detailEnchants = "";

        if (view == View.PRODUCT_DETAIL && selectedOffer != null) {
            detailItemId = selectedOffer.itemId();
            detailUnitPrice = selectedOffer.price();
            detailQty = selectedQuantity;
            detailTotal = ShopCatalog.totalPrice(selectedOffer, selectedQuantity);
            detailStackLimit = ShopCatalog.maxStackSize(selectedOffer);
            detailReqClaims = RefugioPlayerData.hasAllShopCategoriesUnlocked(owner)
                    ? 0 : ShopCatalog.requiredClaims(selectedOffer);
            detailEnchants = serializeEnchantmentSpecs(selectedOffer.enchantments());
            boolean unlocked = RefugioQuests.isCategoryUnlocked(owner, categoryIndex)
                    && (RefugioPlayerData.getMode(owner) != RefugioPlayerData.MODE_PROGRESSION
                    || RefugioPlayerData.getClaimedQuestCount(owner) >= detailReqClaims);
            detailCanAction = unlocked && coins >= detailTotal
                    && !(detailStackLimit == 1 && selectedQuantity != 1);
        } else if (view == View.SELL_CONFIRM) {
            ItemStack current = selectedInventorySlot >= CONTAINER_SIZE
                    ? getSlot(selectedInventorySlot).getItem() : ItemStack.EMPTY;
            detailItemId = selectedSellItemId != null ? selectedSellItemId : "minecraft:air";
            detailUnitPrice = selectedSellItemId != null ? ShopCatalog.sellValue(selectedSellItemId) : 0;
            detailQty = selectedSellQuantity;
            detailTotal = detailUnitPrice * detailQty;
            detailStackLimit = Math.max(1, current.getCount());
            detailCanAction = !current.isEmpty() && detailUnitPrice > 0 && detailQty >= 1 && detailQty <= current.getCount();
        }

        long sellTotal = 0L;
        int sellAccepted = 0;
        int sellRejected = 0;
        if (view == View.SELL) {
            sellTotal = depositedSellValue();
            for (int slot : SELL_DEPOSIT_SLOTS) {
                ItemStack stack = buttons.getItem(slot);
                if (stack.isEmpty()) continue;
                if (sellable(stack)) sellAccepted += stack.getCount();
                else sellRejected += stack.getCount();
            }
        }

        int catUnlockMask = 0;
        StringBuilder catCounts = new StringBuilder();
        StringBuilder catReqs = new StringBuilder();
        for (int i = 0; i < ShopCatalog.categoryCount(); i++) {
            if (RefugioQuests.isCategoryUnlocked(owner, i)) catUnlockMask |= (1 << i);
            if (i > 0) {
                catCounts.append(',');
                catReqs.append(',');
            }
            catCounts.append(ShopCatalog.category(i).offers().size());
            catReqs.append(RefugioQuests.claimsRequiredForCategory(i));
        }

        ItemStack carried = getCarried();
        String carriedId = carried.isEmpty() ? "" : itemId(carried);
        int carriedCount = carried.isEmpty() ? 0 : carried.getCount();

        String safeSubcat = selectedSubcategory == null ? "" : selectedSubcategory.replace('\t', ' ');
        String safeSearch = searchTerm == null ? "" : searchTerm.replace('\t', ' ');

        String payload = "SHOP\t"
                + containerId + "\t"
                + view.name() + "\t"
                + coins + "\t"
                + categoryIndex + "\t"
                + safeSubcat + "\t"
                + safeSearch + "\t"
                + sortMode + "\t"
                + page + "\t"
                + totalPages + "\t"
                + totalItems + "\t"
                + detailItemId + "\t"
                + detailUnitPrice + "\t"
                + detailQty + "\t"
                + detailTotal + "\t"
                + detailStackLimit + "\t"
                + detailReqClaims + "\t"
                + (detailCanAction ? 1 : 0) + "\t"
                + sellTotal + "\t"
                + sellAccepted + "\t"
                + sellRejected + "\t"
                + catUnlockMask + "\t"
                + catCounts + "\t"
                + catReqs + "\t"
                + carriedId + "\t"
                + carriedCount + "\t"
                + itemsBuilder + "\t"
                + detailEnchants;
        RefugioUiService.sendShopState(owner, payload);
    }

    private static String serializeEnchantmentSpecs(List<EnchantmentSpec> specs) {
        if (specs == null || specs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (EnchantmentSpec spec : specs) {
            if (!sb.isEmpty()) sb.append('~');
            sb.append(spec.id()).append('^').append(spec.level());
        }
        return sb.toString();
    }

    private void renderHome() {
        fillBackground();
        int[] categorySlots = {10, 12, 14, 16, 28};
        for (int i = 0; i < ShopCatalog.categoryCount(); i++) {
            Category category = ShopCatalog.category(i);
            boolean unlocked = RefugioQuests.isCategoryUnlocked(owner, i);
            String icon = unlocked ? category.iconItem() : "minecraft:barrier";
            List<Component> lore = new ArrayList<>();
            lore.add(Component.translatable("gui.refugio.category_count", category.offers().size()));
            if (!unlocked) {
                lore.add(Component.translatable("gui.refugio.category_requires",
                        RefugioQuests.claimsRequiredForCategory(i)));
            }
            setButton(categorySlots[i], icon, category.titleKey(), lore);
        }
        setButton(40, "minecraft:barrel", "gui.refugio.sell",
                List.of(Component.translatable("gui.refugio.sell_lore")));
        setButton(49, "minecraft:gold_nugget", "gui.refugio.balance",
                List.of(Component.translatable("gui.refugio.balance_value", RefugioPlayerData.getCoins(owner))));
    }

    private void renderCategory() {
        fillBackground();
        Category category = ShopCatalog.category(categoryIndex);
        List<Offer> offers = visibleOffers();
        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < offers.size(); slot++) {
            buttons.setItem(slot, offerStack(offers.get(start + slot)));
        }
        setButton(45, "minecraft:arrow", "gui.refugio.back", List.of());
        setButton(46, "minecraft:arrow", "gui.refugio.previous_page", List.of());
        setButton(47, "minecraft:spyglass", "gui.refugio.search", List.of(
                Component.translatable("gui.refugio.search_query", searchTerm.isBlank() ? "—" : searchTerm)));
        setButton(48, "minecraft:paper", category.titleKey(), List.of(
                Component.translatable("gui.refugio.page", page + 1, Math.max(1, (offers.size() + PAGE_SIZE - 1) / PAGE_SIZE))));
        setButton(49, "minecraft:gold_nugget", "gui.refugio.balance",
                List.of(Component.translatable("gui.refugio.balance_value", RefugioPlayerData.getCoins(owner))));
        setButton(50, "minecraft:comparator", "gui.refugio.sort", List.of(Component.translatable("gui.refugio.sort_mode." + sortMode)));
        setButton(52, "minecraft:arrow", "gui.refugio.next_page", List.of());
        setButton(53, "minecraft:barrel", "gui.refugio.sell", List.of());
    }

    private void renderSearch() {
        fillBackground();
        List<Offer> offers = visibleOffers();
        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < offers.size(); slot++) {
            buttons.setItem(slot, offerStack(offers.get(start + slot)));
        }
        setButton(45, "minecraft:arrow", "gui.refugio.back", List.of());
        setButton(46, "minecraft:arrow", "gui.refugio.previous_page", List.of());
        setButton(47, "minecraft:spyglass", "gui.refugio.search", List.of(
                Component.translatable("gui.refugio.search_query", searchTerm)));
        setButton(48, "minecraft:paper", "gui.refugio.search_results",
                List.of(Component.translatable("gui.refugio.search_count", offers.size()),
                        Component.translatable("gui.refugio.page", page + 1, Math.max(1, (offers.size() + PAGE_SIZE - 1) / PAGE_SIZE))));
        setButton(49, "minecraft:gold_nugget", "gui.refugio.balance",
                List.of(Component.translatable("gui.refugio.balance_value", RefugioPlayerData.getCoins(owner))));
        setButton(50, "minecraft:comparator", "gui.refugio.sort",
                List.of(Component.translatable("gui.refugio.sort_mode." + sortMode)));
        setButton(52, "minecraft:arrow", "gui.refugio.next_page", List.of());
        setButton(53, "minecraft:barrel", "gui.refugio.sell", List.of());
    }

    private void renderProductDetail() {
        fillBackground();
        if (selectedOffer == null) return;
        long total = ShopCatalog.totalPrice(selectedOffer, selectedQuantity);
        long remaining = (long) RefugioPlayerData.getCoins(owner) - total;
        ItemStack product = offerStack(selectedOffer);
        List<Component> detailLore = new ArrayList<>(describeEnchantmentLines(selectedOffer.enchantments()));
        detailLore.addAll(List.of(
                Component.translatable("gui.refugio.purchase_unit_price", selectedOffer.price()),
                Component.translatable("gui.refugio.purchase_quantity", selectedQuantity),
                Component.translatable("gui.refugio.purchase_total", total),
                Component.translatable("gui.refugio.balance_value", RefugioPlayerData.getCoins(owner)),
                Component.translatable("gui.refugio.purchase_remaining", Math.max(0L, remaining)),
                Component.translatable("gui.refugio.product_stack_size", ShopCatalog.maxStackSize(selectedOffer)),
                Component.translatable("gui.refugio.package_discount")));
        product.set(DataComponents.LORE, new ItemLore(detailLore));
        buttons.setItem(22, product);
        int[] slots = {10, 12, 14, 16, 28};
        int[] quantities = {1, 8, 16, 32, 64};
        int stackLimit = ShopCatalog.maxStackSize(selectedOffer);
        for (int i = 0; i < slots.length; i++) {
            boolean unavailable = stackLimit == 1 && quantities[i] > 1;
            String icon = unavailable ? "minecraft:barrier"
                    : selectedQuantity == quantities[i] ? "minecraft:lime_dye" : "minecraft:gold_nugget";
            setButton(slots[i], icon, unavailable ? "gui.refugio.quantity_unavailable" : "gui.refugio.quantity." + quantities[i],
                    List.of(Component.translatable("gui.refugio.purchase_total", ShopCatalog.totalPrice(selectedOffer, quantities[i]))));
        }
        setButton(30, "minecraft:paper", "gui.refugio.purchase_custom",
                List.of(Component.translatable("gui.refugio.purchase_custom_lore")));
        setButton(45, "minecraft:arrow", "gui.refugio.back", List.of());
        setButton(49, "minecraft:emerald_block", "gui.refugio.purchase_confirm", List.of());
        setButton(53, "minecraft:barrier", "gui.refugio.purchase_cancel", List.of());
    }

    private void renderSubcategories() {
        fillBackground();
        List<String> subcategories = ShopCatalog.subcategories(categoryIndex);
        int start = page * SUBCATEGORY_SLOTS.length;
        for (int index = 0; index < SUBCATEGORY_SLOTS.length && start + index < subcategories.size(); index++) {
            int slot = SUBCATEGORY_SLOTS[index];
            String key = subcategories.get(start + index);
            List<Offer> products = ShopCatalog.offersInSubcategory(categoryIndex, key);
            String icon = products.isEmpty() ? "minecraft:paper" : products.get(0).itemId();
            setButton(slot, icon, "gui.refugio.subcategory." + key,
                    List.of(Component.translatable("gui.refugio.category_count", products.size())));
        }
        Category category = ShopCatalog.category(categoryIndex);
        setButton(45, "minecraft:arrow", "gui.refugio.back", List.of());
        setButton(46, "minecraft:arrow", "gui.refugio.previous_page", List.of());
        setButton(47, "minecraft:spyglass", "gui.refugio.search", List.of(
                Component.translatable("gui.refugio.search_query", searchTerm.isBlank() ? "—" : searchTerm)));
        setButton(48, "minecraft:paper", category.titleKey(), List.of(
                Component.translatable("gui.refugio.page", page + 1,
                        Math.max(1, (subcategories.size() + SUBCATEGORY_SLOTS.length - 1) / SUBCATEGORY_SLOTS.length))));
        setButton(49, "minecraft:gold_nugget", "gui.refugio.balance",
                List.of(Component.translatable("gui.refugio.balance_value", RefugioPlayerData.getCoins(owner))));
        setButton(52, "minecraft:arrow", "gui.refugio.next_page", List.of());
        setButton(53, "minecraft:barrel", "gui.refugio.sell", List.of());
    }

    private void renderSell() {
        undecorateSellSlots();
        Map<Integer, ItemStack> deposited = new HashMap<>();
        for (int slot : SELL_DEPOSIT_SLOTS) {
            ItemStack stack = buttons.getItem(slot);
            if (!stack.isEmpty()) deposited.put(slot, stack.copy());
        }
        fillBackground();
        for (int slot : SELL_DEPOSIT_SLOTS) buttons.setItem(slot, ItemStack.EMPTY);
        for (var entry : deposited.entrySet()) buttons.setItem(entry.getKey(), entry.getValue());
        decorateSellSlots();

        setButton(4, "minecraft:barrel", "gui.refugio.sell_title", List.of(
                Component.translatable("gui.refugio.sell_instructions")));
        setButton(2, "minecraft:hopper", "gui.refugio.sell_deposit_hint", List.of(
                Component.translatable("gui.refugio.sell_deposit_slots")));
        setButton(45, "minecraft:arrow", "gui.refugio.back", List.of());
        setButton(46, "minecraft:chest", "gui.refugio.sell_return_items", List.of());
        setButton(48, "minecraft:gold_nugget", "gui.refugio.balance",
                List.of(Component.translatable("gui.refugio.balance_value", RefugioPlayerData.getCoins(owner))));
        renderSellSummary();
        ItemStack confirm = new ItemStack(item("minecraft:emerald_block"));
        confirm.set(DataComponents.CUSTOM_NAME,
                Component.translatable("gui.refugio.sell_confirm_amount", depositedSellValue()));
        confirm.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("gui.refugio.sell_confirm_hint"))));
        buttons.setItem(53, confirm);
    }

    private void renderSellSummary() {
        long total = depositedSellValue();
        int accepted = 0;
        int rejected = 0;
        for (int slot : SELL_DEPOSIT_SLOTS) {
            ItemStack stack = buttons.getItem(slot);
            if (stack.isEmpty()) continue;
            if (sellable(stack)) accepted += stack.getCount();
            else rejected += stack.getCount();
        }
        ItemStack totalIcon = new ItemStack(item("minecraft:emerald"));
        totalIcon.set(DataComponents.CUSTOM_NAME, Component.translatable("gui.refugio.sell_total_amount", total));
        totalIcon.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("gui.refugio.sell_total_value", total),
                Component.translatable("gui.refugio.sell_accepted_count", accepted),
                Component.translatable("gui.refugio.sell_rejected_count", rejected))));
        buttons.setItem(52, totalIcon);
        setButton(50, "minecraft:paper", "gui.refugio.sell_balance_after",
                List.of(Component.translatable("gui.refugio.sell_balance_after_value",
                        (long) RefugioPlayerData.getCoins(owner) + total)));
        setButton(51, "minecraft:emerald", "gui.refugio.sell_accepted_items", List.of(
                Component.translatable("gui.refugio.sell_accepted_count", accepted)));
        ItemStack confirm = new ItemStack(item("minecraft:emerald_block"));
        confirm.set(DataComponents.CUSTOM_NAME,
                Component.translatable("gui.refugio.sell_confirm_amount", total));
        confirm.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("gui.refugio.sell_confirm_hint"))));
        buttons.setItem(53, confirm);
    }

    private void decorateSellSlots() {
        for (int slot : SELL_DEPOSIT_SLOTS) {
            ItemStack stack = buttons.getItem(slot);
            if (stack.isEmpty()) continue;
            ItemStack original = stack.copy();
            originalSellStacks.put(slot, original);
            int unitPrice = sellable(stack) ? ShopCatalog.sellValue(itemId(stack)) : 0;
            long stackValue = (long) Math.max(0, unitPrice) * stack.getCount();
            List<Component> lore = new ArrayList<>();
            if (unitPrice > 0) {
                lore.add(Component.translatable("gui.refugio.sell_unit_price", unitPrice));
                lore.add(Component.translatable("gui.refugio.sell_pack_value", stack.getCount(), stackValue));
            } else {
                lore.add(Component.translatable("gui.refugio.sell_item_rejected"));
            }
            if (original.has(DataComponents.LORE)) lore.addAll(original.get(DataComponents.LORE).lines());
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
    }

    private void undecorateSellSlots() {
        for (int slot : SELL_DEPOSIT_SLOTS) {
            ItemStack original = originalSellStacks.remove(slot);
            if (original == null) continue;
            ItemStack current = buttons.getItem(slot);
            if (current.isEmpty()) continue;
            buttons.setItem(slot, original.copyWithCount(current.getCount()));
        }
    }

    private long depositedSellValue() {
        long total = 0L;
        for (int slot : SELL_DEPOSIT_SLOTS) {
            ItemStack stack = buttons.getItem(slot);
            if (sellable(stack)) total += (long) stack.getCount() * ShopCatalog.sellValue(itemId(stack));
        }
        return total;
    }

    private boolean sellable(ItemStack stack) {
        return !stack.isEmpty() && ShopCatalog.sellValue(itemId(stack)) > 0 && !hasQuestRewardMarker(stack);
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static boolean isSellDepositSlot(int slot) {
        for (int sellSlot : SELL_DEPOSIT_SLOTS) if (slot == sellSlot) return true;
        return false;
    }

    private void returnDepositedItems() {
        undecorateSellSlots();
        ItemStack carried = getCarried();
        if (!carried.isEmpty()) {
            ItemStack returnedCarried = carried.copy();
            if (!owner.getInventory().add(returnedCarried)) owner.drop(returnedCarried, false);
            setCarried(ItemStack.EMPTY);
        }
        for (int slot : SELL_DEPOSIT_SLOTS) {
            ItemStack stack = buttons.getItem(slot);
            if (stack.isEmpty()) continue;
            ItemStack returned = stack.copy();
            if (!owner.getInventory().add(returned)) owner.drop(returned, false);
            buttons.setItem(slot, ItemStack.EMPTY);
        }
        originalSellStacks.clear();
    }

    private void sellDepositedItems() {
        if (!ACTIVE_TRANSACTIONS.add(owner.getUUID())) return;
        try {
            undecorateSellSlots();
            long total = depositedSellValue();
            if (total <= 0L) {
                owner.sendSystemMessage(Component.translatable("message.refugio.sell_nothing_accepted"));
                decorateSellSlots();
                return;
            }
            if (total > Integer.MAX_VALUE || (long) RefugioPlayerData.getCoins(owner) + total > Integer.MAX_VALUE) {
                owner.sendSystemMessage(Component.translatable("message.refugio.sale_cannot_pay"));
                decorateSellSlots();
                return;
            }
            int itemCount = 0;
            for (int slot : SELL_DEPOSIT_SLOTS) {
                ItemStack stack = buttons.getItem(slot);
                if (!sellable(stack)) continue;
                itemCount += stack.getCount();
                buttons.setItem(slot, ItemStack.EMPTY);
            }
            RefugioPlayerData.addCoins(owner, (int) total);
            owner.sendSystemMessage(Component.translatable("message.refugio.batch_sale_complete", itemCount,
                    total, RefugioPlayerData.getCoins(owner)));
            render();
        } finally {
            ACTIVE_TRANSACTIONS.remove(owner.getUUID());
        }
    }

    private void renderSellConfirm() {
        fillBackground();
        ItemStack current = selectedInventorySlot >= CONTAINER_SIZE
                ? getSlot(selectedInventorySlot).getItem() : ItemStack.EMPTY;
        if (current.isEmpty()) return;
        int unitPrice = ShopCatalog.sellValue(selectedSellItemId);
        long total = (long) unitPrice * selectedSellQuantity;
        int remaining = Math.max(0, current.getCount() - selectedSellQuantity);
        ItemStack preview = current.copyWithCount(selectedSellQuantity);
        preview.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("gui.refugio.purchase_unit_price", unitPrice),
                Component.translatable("gui.refugio.purchase_quantity", selectedSellQuantity),
                Component.translatable("gui.refugio.purchase_total", total),
                Component.translatable("gui.refugio.sell_remaining_stack", remaining),
                Component.translatable("gui.refugio.balance_value", RefugioPlayerData.getCoins(owner)),
                Component.translatable("gui.refugio.purchase_remaining", (long) RefugioPlayerData.getCoins(owner) + total))));
        buttons.setItem(22, preview);
        int[] slots = {10, 12, 14, 16, 28};
        int[] quantities = {1, 8, 16, 32, 64};
        for (int i = 0; i < slots.length; i++) {
            setButton(slots[i], selectedSellQuantity == quantities[i] ? "minecraft:lime_dye" : "minecraft:gold_nugget",
                    "gui.refugio.quantity." + quantities[i],
                    List.of(Component.translatable("gui.refugio.purchase_total",
                            (long) unitPrice * Math.min(current.getCount(), quantities[i]))));
        }
        setButton(30, "minecraft:paper", "gui.refugio.sell_custom",
                List.of(Component.translatable("gui.refugio.sell_custom_lore", current.getCount())));
        setButton(32, "minecraft:barrel", "gui.refugio.sell_all_stack",
                List.of(Component.translatable("gui.refugio.sell_remaining_stack", current.getCount())));
        setButton(45, "minecraft:arrow", "gui.refugio.back", List.of());
        setButton(49, "minecraft:emerald", "gui.refugio.sell_confirm", List.of());
        setButton(53, "minecraft:barrier", "gui.refugio.purchase_cancel", List.of());
    }

    private void renderQuests() {
        fillBackground();
        int claimed = RefugioPlayerData.getClaimedQuestCount(owner);
        int[] slots = {10, 12, 14, 16, 28, 30, 32, 34};
        for (int i = 0; i < RefugioQuests.count(); i++) {
            RefugioQuests.Mission mission = RefugioQuests.mission(i);
            boolean complete = RefugioQuests.isAdvancementComplete(owner, i);
            String icon = i < claimed ? "minecraft:lime_dye"
                    : i == claimed && complete ? "minecraft:emerald"
                    : i > claimed ? "minecraft:barrier" : "minecraft:book";
            List<Component> lore = new ArrayList<>(List.of(Component.translatable(mission.descriptionKey())));
            if (complete || i < claimed) {
                lore.add(Component.translatable("gui.refugio.quest_reward", mission.coins(), mission.rewardCount(),
                        new ItemStack(item(mission.rewardItem())).getHoverName()));
            } else {
                lore.add(Component.translatable("gui.refugio.side_quest_reward_hidden"));
            }
            lore.add(Component.translatable(i < claimed ? "gui.refugio.quest_done"
                    : i == claimed && complete ? "gui.refugio.quest_claim_ready"
                    : i == claimed ? "gui.refugio.quest_in_progress" : "gui.refugio.quest_locked"));
            setButton(slots[i], icon, mission.titleKey(), lore);
        }
        setButton(45, "minecraft:arrow", "gui.refugio.back", List.of());
        setButton(49, "minecraft:gold_nugget", "gui.refugio.balance",
                List.of(Component.translatable("gui.refugio.balance_value", RefugioPlayerData.getCoins(owner))));
        setButton(53, "minecraft:chest", "gui.refugio.shop_title", List.of());
    }

    private void renderSideQuests(RefugioSideQuests.Track track) {
        fillBackground();
        int progress = RefugioSideQuests.progress(owner, track);
        int[] slots = {10, 12, 14, 16, 28, 30, 32, 34};
        for (int i = 0; i < RefugioSideQuests.count(track); i++) {
            RefugioSideQuests.MissionView mission = RefugioSideQuests.mission(track, i);
            boolean complete = RefugioSideQuests.isComplete(owner, track, i);
            String icon = i < progress ? "minecraft:lime_dye"
                    : i == progress && complete ? "minecraft:emerald"
                    : i > progress ? "minecraft:barrier" : "minecraft:book";
            List<Component> lore = new ArrayList<>();
            lore.add(Component.translatable(mission.descriptionKey()));
            lore.addAll(RefugioSideQuests.objectiveLore(owner, track, i));
            setButton(slots[i], icon, mission.titleKey(), lore);
        }
        setButton(4, "minecraft:book", track == RefugioSideQuests.Track.GARDEN
                ? "gui.refugio.garden_quests_title" : "gui.refugio.expedition_quests_title", List.of());
        setButton(45, "minecraft:arrow", "gui.refugio.close", List.of());
        setButton(49, "minecraft:gold_nugget", "gui.refugio.balance",
                List.of(Component.translatable("gui.refugio.balance_value", RefugioPlayerData.getCoins(owner))));
        setButton(53, "minecraft:barrier", "gui.refugio.close", List.of());
    }

    private ItemStack offerStack(Offer offer) {
        Item item = item(offer.itemId());
        if (item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, offer.amount());
        applyEnchantments(stack, offer.enchantments());
        List<Component> lore = new ArrayList<>(describeEnchantmentLines(offer.enchantments()));
        lore.addAll(List.of(
                Component.translatable("gui.refugio.offer_pack", offer.amount()),
                Component.translatable("gui.refugio.offer_price", offer.price()),
                Component.translatable("gui.refugio.offer_buy_hint"),
                Component.translatable("gui.refugio.offer_sell_hint")));
        if (offer.requiredClaims() > 0 && !RefugioPlayerData.hasAllShopCategoriesUnlocked(owner)) {
            lore.add(Component.translatable("gui.refugio.offer_requires", offer.requiredClaims()));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private List<Component> describeEnchantmentLines(List<EnchantmentSpec> enchantments) {
        if (enchantments == null || enchantments.isEmpty()) return List.of();
        var registry = owner.level().getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<Component> lines = new ArrayList<>(enchantments.size() + 1);
        lines.add(Component.translatable("gui.refugio.offer_enchantments_header"));
        for (EnchantmentSpec spec : enchantments) {
            ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(spec.id()));
            Holder<Enchantment> holder = registry.getOrThrow(key);
            lines.add(Component.literal("• ").append(Enchantment.getFullname(holder, spec.level())));
        }
        return lines;
    }

    private void applyEnchantments(ItemStack stack, List<EnchantmentSpec> enchantments) {
        if (enchantments.isEmpty()) return;
        var registry = owner.level().getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        boolean book = stack.is(net.minecraft.world.item.Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable stored = book
                ? new ItemEnchantments.Mutable(stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY))
                : null;
        for (EnchantmentSpec spec : enchantments) {
            ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(spec.id()));
            Holder<Enchantment> holder = registry.getOrThrow(key);
            if (book) stored.set(holder, spec.level());
            else stack.enchant(holder, spec.level());
        }
        if (book) stack.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
    }

    private void setButton(int slot, String itemId, String nameKey, List<Component> lore) {
        ItemStack stack = new ItemStack(item(itemId));
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(nameKey));
        if (!lore.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(lore));
        buttons.setItem(slot, stack);
    }

    private void fillBackground() {
        ItemStack filler = new ItemStack(item("minecraft:gray_stained_glass_pane"));
        filler.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        for (int slot = 0; slot < CONTAINER_SIZE; slot++) buttons.setItem(slot, filler.copy());
    }

    private static Item item(String itemId) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
    }
}
