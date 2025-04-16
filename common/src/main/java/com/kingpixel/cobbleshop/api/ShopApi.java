package com.kingpixel.cobbleshop.api;

import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.command.CommandTree;
import com.kingpixel.cobbleshop.config.Config;
import com.kingpixel.cobbleshop.database.DataBaseFactory;
import com.kingpixel.cobbleshop.migrate.OldShop;
import com.kingpixel.cobbleshop.models.ActionShop;
import com.kingpixel.cobbleshop.models.Product;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleshop.models.SubShop;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.EconomyUse;
import com.kingpixel.cobbleutils.api.EconomyApi;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos Varas Alonso - 28/09/2024 20:15
 */
public class ShopApi {
  // ModId -> Config
  public static Map<String, Config> configs = new ConcurrentHashMap<>();
  // ModId -> List<Shop>
  public static Map<String, List<Shop>> shops = new ConcurrentHashMap<>();
  public static Map<Shop, List<Product>> sellProducts = new ConcurrentHashMap<>();

  public static void register(ShopOptionsApi options, CommandDispatcher<ServerCommandSource> dispatcher) {
    OldShop.migration();
    configs.remove(options.getModId());
    shops.remove(options.getModId());
    Config config = new Config().readConfig(options);
    configs.put(options.getModId(), config);
    options.setCommands(configs.get(options.getModId()).getCommands());
    CommandTree.register(options, dispatcher);
    CobbleShop.initSellProduct(options);
    Config.readShops(options);
    Config main = configs.get(CobbleShop.MOD_ID);
    if (main == null) {
      CobbleUtils.LOGGER.error(CobbleShop.MOD_ID, "Config not found for modId: " + options.getModId());
      return;
    }
    CobbleShop.lang.init(main);
  }


  public static List<Shop> getShops(ShopOptionsApi options) {
    return shops.get(options.getModId());
  }

  public static List<Shop> getShops(List<SubShop> subShops) {
    return shops.get(CobbleShop.MOD_ID).stream().filter(shop -> subShops.stream().anyMatch(subShop -> subShop.getIdShop().equals(shop.getId()))).toList();
  }

  public static Shop getShop(ShopOptionsApi options, String id) {
    return shops.get(options.getModId()).stream().filter(shop -> shop.getId().equals(id)).findFirst().orElse(null);
  }

  public static Config getConfig(ShopOptionsApi options) {
    return configs.get(options.getModId());
  }

  public static void sellAll(ServerPlayerEntity player, List<ItemStack> itemStacks, ShopOptionsApi options) {
    CompletableFuture.runAsync(() -> {
        Map<EconomyUse, BigDecimal> dataSell = new HashMap<>();

        sellProducts.forEach((shop, products) -> {
          products.stream()
            .filter(product -> product.canSell(player, shop, options))
            .filter(product -> product.getSell() != null && product.getSell().compareTo(BigDecimal.ZERO) > 0) // Verificar precio de venta
            .forEach(product -> {
              itemStacks.stream()
                .map(itemStack -> Product.sellProduct(shop, itemStack, product))
                .filter(price -> price.compareTo(BigDecimal.ZERO) > 0)
                .forEach(price -> {
                  dataSell.merge(shop.getEconomy(), price, BigDecimal::add);
                  DataBaseFactory.INSTANCE.addTransaction(player, shop, product, ActionShop.SELL, price.intValue(), product.getSellPrice(price.intValue()));
                });
            });
        });

        if (!dataSell.isEmpty()) {
          StringBuilder allSell = new StringBuilder();
          dataSell.forEach((economyUse, price) -> {
            allSell.append(CobbleShop.lang.getFormatSell()
                .replace("%price%", EconomyApi.formatMoney(price, economyUse)))
              .append("\n");
            EconomyApi.addMoney(player.getUuid(), price, economyUse);
          });

          PlayerUtils.sendMessage(player, CobbleShop.lang.getMessageSell().replace("%sell%", allSell.toString()), CobbleShop.lang.getPrefix(), TypeMessage.CHAT);
        } else {
          PlayerUtils.sendMessage(player, CobbleShop.lang.getMessageNotSell(), CobbleShop.lang.getPrefix(), TypeMessage.CHAT);
        }
      })
      .orTimeout(30, TimeUnit.SECONDS)
      .exceptionally(e -> {
        CobbleUtils.LOGGER.error(CobbleShop.MOD_ID, "Error selling items -> " + e);
        return null;
      });
  }

  public static Config getMainConfig() {
    return getConfig(ShopOptionsApi.builder()
      .modId(CobbleShop.MOD_ID).build());
  }


}
