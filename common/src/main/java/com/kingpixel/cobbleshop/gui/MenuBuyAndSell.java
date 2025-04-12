package com.kingpixel.cobbleshop.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.config.Config;
import com.kingpixel.cobbleshop.database.DataBaseFactory;
import com.kingpixel.cobbleshop.models.ActionShop;
import com.kingpixel.cobbleshop.models.Product;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.cobbleutils.api.EconomyApi;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.cobbleutils.util.UIUtils;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Stack;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:54
 */
public class MenuBuyAndSell {
  private final int rows;
  private final String titleBuy;
  private final String titleSell;
  private final int productSlot;
  private final ItemModel itemConfirm;
  private final ItemModel itemClose;
  private final ItemModel itemCancel;
  private final List<PanelsConfig> panels;

  public MenuBuyAndSell() {
    this.rows = 6;
    this.titleBuy = "&aBuy %amount%";
    this.titleSell = "&cSell %amount%";
    this.productSlot = 22;
    this.itemConfirm = new ItemModel(39, "minecraft:lime_stained_glass_pane", "&aConfirm", List.of(), 0);
    this.itemClose = new ItemModel(49, "minecraft:barrier", "&cClose", List.of(), 0);
    this.itemCancel = new ItemModel(41, "minecraft:red_stained_glass_pane", "&cCancel", List.of(), 0);
    this.panels = List.of(
      new PanelsConfig(new ItemModel("minecraft:gray_stained_glass_pane"), rows)
    );
  }

  public void open(ServerPlayerEntity player, Stack<Shop> stack, Product product, int amount, ActionShop actionShop,
                   ShopOptionsApi options, Config config, boolean withClose) {
    BigDecimal buyPrice = product.getBuyPrice(player, amount, stack.peek(), config);
    if (buyPrice.compareTo(BigDecimal.ZERO) > 0) {
      if (buyPrice.compareTo(product.getSellPrice(amount)) < 0) {
        PlayerUtils.sendMessage(
          player,
          CobbleShop.lang.getMessageBuyPriceLessThanSell(),
          CobbleShop.lang.getPrefix(),
          TypeMessage.CHAT
        );
        return;
      }
    }
    if (!product.isBuyable() && actionShop.equals(ActionShop.BUY)) return;
    if ((!product.isSellable() || !product.canSell(player, stack.peek(), options)) && actionShop.equals(ActionShop.SELL)) {
      PlayerUtils.sendMessage(
        player,
        CobbleShop.lang.getMessageNotSell(),
        CobbleShop.lang.getPrefix(),
        TypeMessage.CHAT
      );
      return;
    }


    ChestTemplate template = ChestTemplate
      .builder(rows)
      .build();

    PanelsConfig.applyConfig(template, panels);

    // Product Icon
    if (UIUtils.isInside(productSlot, rows)) {
      var economyUse = stack.peek().getEconomy();
      BigDecimal balance = EconomyApi.getBalance(player.getUuid(), economyUse);
      String playerBalance = EconomyApi.formatMoney(balance,
        economyUse);
      template.set(productSlot, product.getIcon(player, stack, actionShop, amount, options, config, withClose, playerBalance));
    }

    // Remove and Add Buttons
    int totalStack = product.getStack();

    if (totalStack != 1) {
      // Add and Remove 1
      putButton(player, stack, product, amount, actionShop, options, config, withClose, template, CobbleShop.lang.getAdd1(), 1, CobbleShop.lang.getRemove1());

      if (totalStack == 16) {
        putButton(player, stack, product, amount, actionShop, options, config, withClose, template, CobbleShop.lang.getAdd8(), 8, CobbleShop.lang.getRemove8());
      }

      if (totalStack == 64) {
        putButton(player, stack, product, amount, actionShop, options, config, withClose, template, CobbleShop.lang.getAdd16(), 16, CobbleShop.lang.getRemove16());
      }

      if (UIUtils.isInside(CobbleShop.lang.getAdd64().getSlot(), rows)) {
        template.set(CobbleShop.lang.getAdd64().getSlot(), CobbleShop.lang.getAdd64().getButton(action -> {
          open(player, stack, product, amount + 64, actionShop, options, config, withClose);
        }));
      }

      if (UIUtils.isInside(CobbleShop.lang.getRemove64().getSlot(), rows)) {
        template.set(CobbleShop.lang.getRemove64().getSlot(), CobbleShop.lang.getRemove64().getButton(action -> {
          open(player, stack, product, Math.max(amount - 64, 1), actionShop, options, config, withClose);
        }));
      }
    }

    // Extra Buttons
    if (UIUtils.isInside(itemCancel.getSlot(), rows)) {
      template.set(itemCancel.getSlot(), itemCancel.getButton(action -> {
        Config.manageOpenShop(player, options, config, null, stack, null, withClose);
      }));
    }

    if (UIUtils.isInside(itemClose.getSlot(), rows)) {
      template.set(itemClose.getSlot(), itemClose.getButton(action -> {
        Config.manageOpenShop(player, options, config, null, stack, null, withClose);
      }));
    }

    if (UIUtils.isInside(itemConfirm.getSlot(), rows)) {
      template.set(itemConfirm.getSlot(), itemConfirm.getButton(action -> {
        Shop shop = stack.peek();
        if (actionShop.equals(ActionShop.BUY)) {
          int max;
          if (product.getUuid() != null) {
            max = DataBaseFactory.INSTANCE.getUserInfo(player).getProductLimit(product);
          } else {
            max = amount;
          }
          shop.getType().buyProduct(player, product, shop, max, options, config, stack, withClose);
        } else {
          product.sell(player, shop, amount, product, options, config, stack, withClose);
        }
      }));
    }

    GooeyPage page = GooeyPage
      .builder()
      .template(template)
      .title(AdventureTranslator.toNative((actionShop.equals(ActionShop.BUY) ? titleBuy : titleSell).replace("%amount%", amount +
        "")))
      .build();

    UIManager.openUIForcefully(player, page);
  }

  private void putButton(ServerPlayerEntity player, Stack<Shop> stack, Product product, int amount, ActionShop actionShop, ShopOptionsApi options, Config config, boolean withClose, ChestTemplate template, ItemModel add1, int i, ItemModel remove1) {
    if (UIUtils.isInside(add1.getSlot(), rows)) {
      template.set(add1.getSlot(), add1.getButton(action -> {
        open(player, stack, product, amount + i, actionShop, options, config, withClose);
      }));
    }
    if (UIUtils.isInside(remove1.getSlot(), rows)) {
      template.set(remove1.getSlot(), remove1.getButton(action -> {
        if (amount > i) open(player, stack, product, Math.max(amount - i, 1), actionShop, options, config, withClose);
      }));
    }
  }
}
