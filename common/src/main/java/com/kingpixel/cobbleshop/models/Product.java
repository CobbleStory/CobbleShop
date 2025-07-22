package com.kingpixel.cobbleshop.models;

import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.api.ShopApi;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.config.Config;
import com.kingpixel.cobbleshop.database.DataBaseFactory;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.ItemChance;
import com.kingpixel.cobbleutils.Model.Sound;
import com.kingpixel.cobbleutils.api.EconomyApi;
import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import lombok.Data;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Unit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:19
 */
@Data
public class Product {
    // Compra 1 por 1
    private Boolean oneByOne;
    // Numero de compras totales que se pueden hacer
    private UUID uuid;
    private Integer max;
    private Integer cooldown;
    // Optional fields for permissions
    private String canBuyPermission;
    private String notBuyPermission;
    // Optional fields for discounts
    private Float discount;
    // Optional fields for visual representation
    private String display;
    private String displayname;
    private List<String> lore;
    private Integer CustomModelData;
    // Essential fields
    private Integer slot;
    private String product;
    private BigDecimal buy;
    private BigDecimal sell;

    public Product() {
        product = "minecraft:stone";
        buy = BigDecimal.valueOf(9999999);
        sell = BigDecimal.valueOf(0);
    }

    public Product(boolean optional) {
        super();
        if (optional) {
            oneByOne = true;
            uuid = UUID.randomUUID();
            max = 1;
            cooldown = 60;
            canBuyPermission = "cobbleshop.dirt";
            notBuyPermission = "cobbleshop.dirt";
            discount = 10.0f;
            display = "minecraft:stone";
            displayname = "Custom Stone";
            lore = List.of("This is a custom stone", "You can use it to build");
            CustomModelData = 0;
            slot = 0;
        }
    }

    public void check(Shop shop) {
        // Limit Product
        if (product == null) product = "minecraft:stone";
        if (!shop.isAutoPlace()) {
            if (slot == null) slot = 0;
        }
        if (cooldown != null || max != null) {
            if (uuid == null) {
                uuid = UUID.randomUUID();
            }
            if (max == null) {
                max = 1;
            }
            if (cooldown == null) {
                cooldown = 60;
            }
        }
    }

    public GooeyButton getIcon(ServerPlayerEntity player, Stack<Shop> shop, ActionShop actionShop, int amount,
                               ShopOptionsApi options,
                               Config config, boolean withClose, String playerBalance) {
        Shop peek = shop.peek();
        String finalDisplay = this.display != null ? this.display : product;
        ItemChance itemChance = new ItemChance(finalDisplay, 0);
        String title = this.displayname != null ? this.displayname : itemChance.getTitle();
        List<String> lore = new ArrayList<>(CobbleShop.lang.getInfoProduct());


        lore.removeIf(s -> {
            if (s == null || s.isEmpty()) return false;
            boolean notSellable = sell == null || sell.compareTo(BigDecimal.ZERO) <= 0;
            boolean notBuyable = buy == null || buy.compareTo(BigDecimal.ZERO) <= 0;

            if (notSellable && (s.contains("%sell%") || s.contains("%removesell%"))) {
                return true;
            }
            if (notBuyable && (s.contains("%buy%") || s.contains("%removebuy%"))) {
                return true;
            }

            if (actionShop != null) {
                if (actionShop == ActionShop.BUY && (s.contains("%sell%") || s.contains("%removesell%"))) {
                    return true;
                }
                return actionShop == ActionShop.SELL && (s.contains("%buy%") || s.contains("%removebuy%"));
            }
            return false;
        });


        lore.replaceAll(s -> replace(player, s, shop.peek(), amount, config, playerBalance));
        boolean infoReplaced = false;

        if (this.lore != null) {
            for (int i = 0; i < lore.size(); i++) {
                String line = lore.get(i);
                if (line.contains("%info%")) {
                    lore.remove(i);
                    lore.addAll(i, this.lore);
                    infoReplaced = true;
                    break;
                }
            }
        }

        if (!infoReplaced) {
            lore.replaceAll(s -> s.contains("%info%") ? CobbleShop.lang.getNotExtraInfo() : s);
        } else {
            lore.removeIf(s -> s.contains("%info%"));
        }


        ItemStack itemStack = itemChance.getItemStack();
        if (amount == itemStack.getCount()) itemStack.setCount(amount);
        if (itemStack.getCount() == 0) itemStack.setCount(1);
        GooeyButton.Builder builder = GooeyButton.builder()
                .display(itemStack)
                .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(peek.getColorProduct() + title))
                .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
                .with(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);

        return builder
                .onClick(action -> {
                    try {
                        ActionShop shopAction;
                        switch (action.getClickType()) {
                            case LEFT_CLICK, SHIFT_LEFT_CLICK -> shopAction = ActionShop.BUY;
                            case RIGHT_CLICK, SHIFT_RIGHT_CLICK -> shopAction = ActionShop.SELL;
                            default -> shopAction = ActionShop.BUY;
                        }

                        // Need Permissions
                        if (havePermission(player, peek)) {
                            new Sound(peek.getSoundOpen()).playSoundPlayer(player);
                            if (DataBaseFactory.INSTANCE.canBuy(player, this)) {
                                CobbleShop.lang.getMenuBuyAndSell().open(player, shop, this, amount, shopAction, options,
                                        config, withClose);
                            } else {
                                PlayerUtils.sendMessage(
                                        player,
                                        CobbleShop.lang.getMessageYouCantBuyNow()
                                                .replace("%limit%", String.valueOf(max))
                                                .replace("%time%", PlayerUtils.getCooldown(new Date(DataBaseFactory.INSTANCE.getProductCooldown(player, this)))),
                                        CobbleShop.lang.getPrefix(),
                                        TypeMessage.CHAT
                                );
                            }

                        } else {
                            PlayerUtils.sendMessage(
                                    player,
                                    CobbleShop.lang.getMessageNotBuyPermission(),
                                    CobbleShop.lang.getPrefix(),
                                    TypeMessage.CHAT
                            );
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .build();
    }

    public boolean havePermission(ServerPlayerEntity player, Shop shop) {
        if (this.notBuyPermission != null && PermissionApi.hasPermission(player, this.notBuyPermission, 4))
            return false;
        return canBuyPermission == null || PermissionApi.hasPermission(player, canBuyPermission, 4);
    }

    private float getEspecialDiscount(Map<String, Float> discounts, ServerPlayerEntity player, float result) {
        if (discounts != null && !discounts.isEmpty()) {
            for (Map.Entry<String, Float> entry : discounts.entrySet()) {
                if (PermissionApi.hasPermission(player, entry.getKey(), 4)) {
                    if (result < entry.getValue()) result = entry.getValue();
                }
            }
        }
        return result;
    }

    private float getDiscount(ServerPlayerEntity player, Shop shop, Config config) {
        float result = 0.0f;
        result = getEspecialDiscount(shop.getDiscounts(), player, result);
        result = getEspecialDiscount(config.getDiscounts(), player, result);
        if (shop.getGlobalDiscount() <= 0f) {
            float a = discount != null ? discount : 0f;
            if (a > result) result = a;
        } else {
            if (shop.getGlobalDiscount() > result) result = shop.getGlobalDiscount();
        }
        return result;
    }

    public BigDecimal getBuyPrice(ServerPlayerEntity player, int amount, Shop shop, Config config) {
        BigDecimal totalBuy = buy.multiply(BigDecimal.valueOf(amount));
        totalBuy = totalBuy.subtract(totalBuy.multiply(BigDecimal.valueOf(getDiscount(player, shop, config) / 100.0)));
        return totalBuy.setScale(5, RoundingMode.UNNECESSARY);
    }

    public BigDecimal getSellPrice(int amount) {
        BigDecimal copySell = sell.setScale(5, RoundingMode.UNNECESSARY);
        return copySell.multiply(BigDecimal.valueOf(amount));
    }

    public BigDecimal getSellPricePerUnit(ItemStack itemStack) {
        if (itemStack == null) {
            itemStack = getItemStack();
        }
        int amount = itemStack.getCount();
        if (amount == 1) {
            return sell;
        } else {
            return sell.divide(BigDecimal.valueOf(amount), 5, RoundingMode.UNNECESSARY);
        }
    }

    private String replace(ServerPlayerEntity player, String s, Shop shop, int amount, Config config, String playerBalance) {
        if (s == null || s.isEmpty()) return "";
        var economy = shop.getEconomy();


        if (s.contains("%buy%")) {
            BigDecimal buyPrice = getBuyPrice(player, amount, shop, config);
            s = s.replace("%buy%", EconomyApi.formatMoney(buyPrice, economy));
        }
        if (s.contains("%sell%")) {
            BigDecimal sellPrice = getSellPrice(amount);
            s = s.replace("%sell%", EconomyApi.formatMoney(sellPrice, economy));
        }
        if (s.contains("%amount%")) {
            s = s.replace("%amount%", String.valueOf(amount));
        }
        if (s.contains("%pack%")) {
            s = s.replace("%pack%", String.valueOf(getItemStack().getCount()));
        }
        if (s.contains("%discount%")) {
            float discount = getDiscount(player, shop, config);
            s = s.replace("%discount%", discount > 0f ? discount + "%" : "");
        }
        if (s.contains("%removebuy%")) {
            s = s.replace("%removebuy%", "");
        }
        if (s.contains("%removesell%")) {
            s = s.replace("%removesell%", "");
        }

        s = s.replace("%balance%", playerBalance == null ? "" : playerBalance);

        return s;
    }


    public boolean isSellable() {
        return sell != null && sell.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isBuyable() {
        return buy != null && buy.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean buy(ServerPlayerEntity player, Shop shop, int amount, ShopOptionsApi options, Config config,
                       Stack<Shop> stack, boolean withClose) {
        boolean result = false;
        ItemChance itemChance = new ItemChance(product, 0);
        ItemStack itemStack = itemChance.getItemStack();
        itemStack.setCount(amount);
        // TODO: Check if the inventory has space
        int emptySlots = 0;
        var inventoryMain = player.getInventory().main;
        for (ItemStack item : inventoryMain)
            if (item.isEmpty()) emptySlots++;

        if (!product.startsWith("command:") && !product.startsWith("pokemon:") && !product.contains("|")) {
            int maxStack = itemStack.getMaxCount();
            int slotsNeeded = (int) Math.ceil((double) amount / maxStack);
            if (emptySlots < slotsNeeded) {
                PlayerUtils.sendMessage(
                        player,
                        CobbleShop.lang.getMessageNotEnoughSpace()
                                .replace("%amount%", String.valueOf(amount))
                                .replace("%slots%", String.valueOf(slotsNeeded)),
                        CobbleShop.lang.getPrefix(),
                        TypeMessage.CHAT
                );
                return false;
            }
        }

        BigDecimal totalBuy = getBuyPrice(player, amount, shop, config);
        if (EconomyApi.hasEnoughMoney(player.getUuid(), totalBuy, shop.getEconomy(), true)) {
            ItemChance.giveReward(player, itemChance, amount);
            result = true;
        }
        if (!result) {
            PlayerUtils.sendMessage(
                    player,
                    CobbleShop.lang.getMessageNotEnoughMoney()
                            .replace("%product%", itemChance.getTitle())
                            .replace("%amount%", String.valueOf(amount))
                            .replace("%pack%", itemChance.getItemStack().getCount() + "")
                            .replace("%price%", EconomyApi.formatMoney(totalBuy, shop.getEconomy())),
                    CobbleShop.lang.getPrefix(),
                    TypeMessage.CHAT
            );
        } else {
            if (getUuid() != null)
                DataBaseFactory.INSTANCE.addProductLimit(player, shop, this, amount);
            if (ShopApi.getMainConfig().isSaveTransactions())
                DataBaseFactory.INSTANCE.addTransaction(player, shop, this, ActionShop.BUY, amount, totalBuy);

        }
        Config.manageOpenShop(player, options, config, null, stack, null, withClose);
        return result;
    }

    public boolean hasErrors() {
        if (buy != null && sell != null && buy.compareTo(BigDecimal.ZERO) > 0 && sell.compareTo(BigDecimal.ZERO) > 0) {
            if (buy.compareTo(sell) < 0) {
                CobbleUtils.LOGGER.error("The sell price is lower than the buy price -> " + product);
                return true;
            }
        }
        return false;
    }

    public int getStack() {
        if (product.startsWith("command:") || product.startsWith("pokemon:") || (oneByOne != null && oneByOne))
            return 1;

        return new ItemChance(product, 0).getItemStack().getMaxCount();
    }

    public boolean canSell(ServerPlayerEntity player, Shop shop, ShopOptionsApi options) {
        if (getSell() == null || getSell().compareTo(BigDecimal.ZERO) <= 0) return false;
        if (player != null) {
            ItemStack itemStack = getItemStack();
            if (itemStack.isEmpty()) return false;
            BigDecimal buyPrice = getBuyPrice(player, 1, shop, ShopApi.getConfig(options));
            BigDecimal sellPricePerUnit = getSellPricePerUnit(itemStack);

            boolean canSell = buyPrice.compareTo(BigDecimal.ZERO) <= 0 || buyPrice.compareTo(sellPricePerUnit) >= 0;
            return canSell;
        }

        return !product.startsWith("command:") && !product.startsWith("pokemon:") && sell.compareTo(BigDecimal.ZERO) > 0 && !product.contains("|");
    }

    public ItemStack getItemStack() {
        return new ItemChance(product, 0).getItemStack();
    }

    public void sell(ServerPlayerEntity player, Shop shop, int amount, Product product, ShopOptionsApi options, Config config,
                     Stack<Shop> stack, boolean withClose) {
        ItemStack productItemStack = product.getItemStack();
        BigDecimal sellPricePerUnit = product.getSellPricePerUnit(productItemStack);
        BigDecimal total = BigDecimal.ZERO;
        var playerInventory = player.getInventory();
        int remainingAmount = amount;
        int selled = 0;

        for (ItemStack itemStack : playerInventory.main) {
            if (areEquals(itemStack, productItemStack)) {
                int stackCount = itemStack.getCount();
                if (stackCount >= remainingAmount) {
                    itemStack.decrement(remainingAmount);
                    selled += remainingAmount;
                    total = total.add(sellPricePerUnit.multiply(BigDecimal.valueOf(remainingAmount)));
                    remainingAmount = 0;
                    break;
                } else {
                    remainingAmount -= stackCount;
                    selled += stackCount;
                    itemStack.decrement(stackCount);
                    total = total.add(sellPricePerUnit.multiply(BigDecimal.valueOf(stackCount)));
                }
            }
            if (remainingAmount <= 0) break;
        }

        EconomyApi.addMoney(player.getUuid(), total, shop.getEconomy());

        PlayerUtils.sendMessage(
                player,
                CobbleShop.lang.getMessageSimpleSell()
                        .replace("%product%", productItemStack.getName().getString())
                        .replace("%amount%", String.valueOf(selled))
                        .replace("%price%", EconomyApi.formatMoney(total, shop.getEconomy())),
                CobbleShop.lang.getPrefix(),
                TypeMessage.CHAT
        );
        if (ShopApi.getMainConfig().isSaveTransactions()) {
            DataBaseFactory.INSTANCE.addTransaction(player, shop, product, ActionShop.SELL, selled, total);
        }
        Config.manageOpenShop(player, options, config, null, stack, null, withClose);
    }

    public static BigDecimal sellProduct(Shop shop, ItemStack itemStack, Product product) {
        ItemStack itemProduct = product.getItemStack();
        if (areEquals(itemStack, itemProduct)) {
            int itemStackCount = itemStack.getCount();
            int itemProductCount = itemProduct.getCount();

            // Evitar división por cero
            if (itemProductCount <= 0) return BigDecimal.ZERO;

            // Calcular el precio total de venta basado en la cantidad
            BigDecimal totalSellPrice = product.getSellPrice(itemStackCount);

            // Ajustar el precio basado en la cantidad del producto con escala y redondeo
            BigDecimal adjustedPrice = totalSellPrice.divide(BigDecimal.valueOf(itemProductCount), 5, RoundingMode.UNNECESSARY);
            if (adjustedPrice.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

            itemStack.decrement(itemStackCount);
            return adjustedPrice;
        }
        return BigDecimal.ZERO;
    }

    public static boolean areEquals(ItemStack itemStack, ItemStack itemProduct) {
        boolean ItemEqual = ItemStack.areItemsEqual(itemStack, itemProduct);
        var a = itemStack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        int aNumber = a == null ? 0 : a.value();
        var b = itemProduct.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        int bNumber = b == null ? 0 : b.value();
        boolean CustomModelDataEqual = aNumber == bNumber;
        return ItemEqual && CustomModelDataEqual;
    }

    public int getSellableAmount(ServerPlayerEntity player) {
        ItemStack productStack = this.getItemStack();
        int total = 0;

        for (ItemStack item : player.getInventory().main) {
            if (!item.isEmpty() && areEquals(item, productStack)) total += item.getCount();
        }

        return total;
    }

    @Data
    public static class SellProduct {
        private String currency;
        private BigDecimal price;

        public SellProduct(String currency, BigDecimal price) {
            this.currency = currency;
            this.price = price;
        }
    }
}
