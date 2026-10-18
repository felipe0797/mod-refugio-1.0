package com.felpz.refugio.ability;

import com.felpz.refugio.data.RefugioPlayerData;
import com.felpz.refugio.ui.RefugioUiService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/** Server-backed four-branch skill tree presented as a compact advancement-style grid. */
public final class RefugioAbilityMenu extends ChestMenu {
    private static final int SIZE = 54;
    private static final int[] BRANCH_COLUMNS = {1, 3, 5, 7};
    private static final int[] NODE_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34, 37, 39, 41, 43};

    private final ServerPlayer owner;
    private final SimpleContainer buttons;
    private int page;
    private String selectedSkill;
    private boolean details;

    private RefugioAbilityMenu(int id, Inventory inventory, ServerPlayer owner, SimpleContainer buttons) {
        super(MenuType.GENERIC_9x6, id, inventory, buttons, 6);
        this.owner = owner;
        this.buttons = buttons;
        render();
    }

    public static void open(ServerPlayer player) {
        RefugioUiService.openAbilities(player);
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner && player.isAlive();
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (player != owner) return;
        if (slotId >= 0 && slotId < SIZE) {
            if (input == ContainerInput.PICKUP) handleButton(slotId);
            return;
        }
        if (input == ContainerInput.QUICK_MOVE) return;
        super.clicked(slotId, button, input, player);
    }

    private void handleButton(int slot) {
        if (details) {
            if (slot == 45) {
                details = false;
                render();
            } else if (slot == 49) {
                purchaseSelected();
            } else if (slot == 51) {
                toggleSelected();
            } else if (slot == 53) {
                owner.closeContainer();
            }
            return;
        }
        if (slot == 45 && page > 0) {
            page--;
            render();
            return;
        }
        if (slot == 53 && page < 1) {
            page++;
            render();
            return;
        }
        for (int i = 0; i < NODE_SLOTS.length; i++) {
            if (NODE_SLOTS[i] != slot) continue;
            int row = i / 4;
            int branchIndex = i % 4;
            int skillIndex = page * 4 + row;
            List<RefugioAbilityData.Skill> branchSkills = RefugioAbilityData.skills(
                    RefugioAbilityData.Branch.values()[branchIndex]);
            if (skillIndex >= branchSkills.size()) return;
            selectedSkill = branchSkills.get(skillIndex).id();
            details = true;
            render();
            return;
        }
    }

    private void purchaseSelected() {
        if (selectedSkill == null) return;
        RefugioAbilityData.PurchaseResult result = RefugioAbilityData.buyNextRank(owner, selectedSkill);
        switch (result) {
            case FREE -> owner.sendSystemMessage(Component.translatable("message.refugio.ability.free_claimed",
                    Component.literal(RefugioAbilityData.skill(selectedSkill).name())));
            case PURCHASED -> owner.sendSystemMessage(Component.translatable("message.refugio.ability.rank_bought",
                    Component.literal(RefugioAbilityData.skill(selectedSkill).name()),
                    RefugioAbilityData.rank(owner, selectedSkill), RefugioPlayerData.getCoins(owner)));
            case NO_COINS -> owner.sendSystemMessage(Component.translatable("message.refugio.not_enough_coins",
                    RefugioAbilityData.costForRank(RefugioAbilityData.rank(owner, selectedSkill) + 1),
                    RefugioPlayerData.getCoins(owner)));
            case LOCKED -> owner.sendSystemMessage(Component.translatable("message.refugio.ability.locked"));
            case MAX_RANK -> owner.sendSystemMessage(Component.translatable("message.refugio.ability.max_rank"));
            case CHOOSE_FREE_FIRST -> owner.sendSystemMessage(Component.translatable("message.refugio.ability.choose_free_first"));
            case INVALID -> { return; }
        }
        render();
    }

    private void toggleSelected() {
        if (selectedSkill == null) return;
        boolean next = !RefugioAbilityData.isActive(owner, selectedSkill);
        if (RefugioAbilityData.setActive(owner, selectedSkill, next)) {
            owner.sendSystemMessage(Component.translatable(next ? "message.refugio.ability.enabled"
                    : "message.refugio.ability.disabled", Component.literal(RefugioAbilityData.skill(selectedSkill).name())));
        }
        render();
    }

    private void render() {
        fillBackground();
        if (details) renderDetails(); else renderTree();
        broadcastChanges();
    }

    private void renderTree() {
        RefugioAbilityData.Branch[] branches = RefugioAbilityData.Branch.values();
        for (int b = 0; b < branches.length; b++) {
            RefugioAbilityData.Branch branch = branches[b];
            setButton(BRANCH_COLUMNS[b], "minecraft:book", branch.displayName(), List.of(
                    Component.literal("Ramo de habilidades"),
                    Component.literal("Seu progresso é independente dos outros ramos.")));
            List<RefugioAbilityData.Skill> skills = RefugioAbilityData.skills(branch);
            for (int row = 0; row < 4; row++) {
                int index = page * 4 + row;
                if (index >= skills.size()) continue;
                RefugioAbilityData.Skill skill = skills.get(index);
                int slot = 10 + row * 9 + BRANCH_COLUMNS[b] - 1;
                int rank = RefugioAbilityData.rank(owner, skill.id());
                boolean unlocked = RefugioAbilityData.isUnlocked(owner, skill.id());
                String icon = unlocked ? skill.icon() : "minecraft:gray_stained_glass_pane";
                List<Component> lore = new ArrayList<>();
                lore.add(Component.literal(skill.description()));
                lore.add(Component.literal(unlocked ? "Disponível" : "Bloqueada — compre a habilidade anterior"));
                lore.add(Component.literal("Rank: " + rank + "/5"));
                if (rank > 0) lore.add(Component.literal(RefugioAbilityData.isActive(owner, skill.id()) ? "Ativa" : "Desativada"));
                else if (rank < 5) lore.add(Component.literal("Próximo rank: " + RefugioAbilityData.costForRank(rank + 1) + " moedas"));
                setButton(slot, icon, (unlocked ? "◇ " : "□ ") + skill.name(), lore);
            }
        }
        setButton(45, page == 0 ? "minecraft:gray_stained_glass_pane" : "minecraft:arrow",
                "gui.refugio.abilities.previous", List.of());
        setButton(49, "minecraft:gold_nugget", "gui.refugio.balance",
                List.of(Component.translatable("gui.refugio.balance_value", RefugioPlayerData.getCoins(owner))));
        setButton(53, page == 0 ? "minecraft:arrow" : "minecraft:gray_stained_glass_pane",
                "gui.refugio.abilities.next", List.of());
        setButton(48, "minecraft:paper", "gui.refugio.abilities.instructions", List.of(
                Component.literal("Selecione um nó para ver pré-requisitos, rank e custo."),
                Component.literal("Kael concede uma única habilidade inicial grátis.")));
    }

    private void renderDetails() {
        RefugioAbilityData.Skill skill = RefugioAbilityData.skill(selectedSkill);
        if (skill == null) {
            details = false;
            renderTree();
            return;
        }
        int rank = RefugioAbilityData.rank(owner, skill.id());
        boolean unlocked = RefugioAbilityData.isUnlocked(owner, skill.id());
        boolean active = RefugioAbilityData.isActive(owner, skill.id());
        setButton(22, skill.icon(), skill.name(), List.of(
                Component.literal(skill.description()),
                Component.literal("Ramo: " + skill.branch().displayName()),
                Component.literal("Rank atual: " + rank + "/5"),
                Component.literal(unlocked ? "Pré-requisito: liberada" : "Pré-requisito: compre o nó anterior"),
                Component.literal(rank < 5 ? "Próximo rank custa " + RefugioAbilityData.costForRank(rank + 1) + " moedas" : "Rank máximo atingido"),
                Component.literal("Saldo: " + RefugioPlayerData.getCoins(owner))));
        String purchaseTitle;
        String purchaseIcon;
        if (rank >= 5) {
            purchaseTitle = "Rank máximo";
            purchaseIcon = "minecraft:barrier";
        } else if (RefugioAbilityData.isFreeChoiceCandidate(owner, skill.id())) {
            purchaseTitle = "Escolher como habilidade grátis";
            purchaseIcon = "minecraft:emerald";
        } else {
            purchaseTitle = rank == 0 ? "Comprar habilidade" : "Comprar próximo rank";
            purchaseIcon = unlocked && RefugioAbilityData.hasFreeChoice(owner) ? "minecraft:gold_ingot" : "minecraft:barrier";
        }
        setButton(49, purchaseIcon, purchaseTitle, List.of(Component.literal(
                rank == 0 && RefugioAbilityData.isFreeChoiceCandidate(owner, skill.id())
                        ? "Presente único de Kael — rank 1" : "Custo: " + RefugioAbilityData.costForRank(rank + 1) + " moedas")));
        if (rank > 0) {
            setButton(51, active ? "minecraft:lime_dye" : "minecraft:gray_dye",
                    active ? "Desativar habilidade" : "Ativar habilidade",
                    List.of(Component.literal("Alternar não devolve moedas.")));
        } else {
            setButton(51, "minecraft:gray_stained_glass_pane", "Ainda não aprendida", List.of());
        }
        setButton(45, "minecraft:arrow", "gui.refugio.back", List.of());
        setButton(53, "minecraft:barrier", "gui.refugio.close", List.of());
    }

    private void setButton(int slot, String itemId, String name, List<Component> lore) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        if (!lore.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(lore));
        buttons.setItem(slot, stack);
    }

    private void fillBackground() {
        ItemStack filler = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:black_stained_glass_pane")));
        filler.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        for (int slot = 0; slot < SIZE; slot++) buttons.setItem(slot, filler.copy());
    }
}
