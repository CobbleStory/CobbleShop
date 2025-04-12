package com.kingpixel.cobbleshop.config;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.adapters.*;
import com.kingpixel.cobbleshop.api.ShopApi;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleshop.models.SubShop;
import com.kingpixel.cobbleshop.models.TypeShop;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.cobbleutils.util.*;
import lombok.Data;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:27
 */
@Data
public class Config {
  private String Path;
  // Essential fields
  private boolean debug;
  private boolean saveTransactions;
  private String lang;
  private int rows;
  private String title;
  private DataBaseConfig dataBase;
  private Map<String, Float> discounts;
  private ItemModel itemClose;
  private List<String> commands;
  private List<PanelsConfig> panels;

  public Config() {
    this.debug = false;
    this.saveTransactions = true;
    this.lang = "en";
    this.rows = 6;
    this.title = "Shop";
    this.discounts = new HashMap<>();
    this.discounts.put("group.vip", 2.0f);
    this.dataBase = new DataBaseConfig();
    dataBase.setDatabase("cobbleshop");
    this.itemClose = new ItemModel(49, "minecraft:barrier", "&cClose", List.of(), 0);
    this.commands = new ArrayList<>();
    commands.add("shop");
    this.panels = List.of(
      new PanelsConfig(new ItemModel("minecraft:gray_stained_glass_pane"), rows)
    );
  }

  public Config readConfig(ShopOptionsApi options) {
    AtomicReference<Config> config = new AtomicReference<>(this);
    String path = options.getPath();
    File folder = Utils.getAbsolutePath(path);

    if (!folder.exists()) {
      folder.mkdirs();
    }
    CompletableFuture<Boolean> futureRead = Utils.readFileAsync(options.getPath(), "config.json", call -> {
      config.set(CobbleShop.gson.fromJson(call, Config.class));
      write(config.get(), options);
    });

    if (!futureRead.join()) write(config.get(), options);
    return config.get();
  }

  private void write(Config config, ShopOptionsApi options) {
    config.check();
    ShopApi.configs.put(options.getModId(), config);
    CompletableFuture<Boolean> futureWrite = Utils.writeFileAsync(options.getPath(), "config.json",
      CobbleShop.gson.toJson(config));
    if (!futureWrite.join()) {
      CobbleUtils.LOGGER.error("Error writing file: " + options.getPath() + "config.json");
    }
  }

  private void check() {
    if (commands == null || commands.isEmpty()) {
      commands = new ArrayList<>();
      commands.add("shop");
    }
  }

  public static void readShops(ShopOptionsApi options) {
    ShopApi.shops.getOrDefault(options.getModId(), new ArrayList<>()).clear();
    String path = options.getPath() + "shop/";
    File folder = Utils.getAbsolutePath(path);
    if (!folder.exists()) {
      folder.mkdirs();
      createDefaultShop(path);
    }
    List<Shop> shops = new ArrayList<>();
    readAllShops(folder.listFiles(), shops);
    ShopApi.shops.put(options.getModId(), shops);
  }

  private static void createDefaultShop(String path) {
    File folder = Utils.getAbsolutePath(path);
    if (!folder.exists()) {
      folder.mkdirs();
    }
    List<Shop> shops = new ArrayList<>();
    shops.add(new Shop(TypeShop.PERMANENT.name(), new ShopTypePermanent()));
    shops.add(new Shop(TypeShop.DYNAMIC.name(), new ShopTypeDynamic()));
    shops.add(new Shop(TypeShop.WEEKLY.name(), new ShopTypeWeekly()));
    shops.add(new Shop(TypeShop.DYNAMIC_WEEKLY.name(), new ShopTypeDynamicWeekly()));
    shops.add(new Shop(TypeShop.CALENDAR.name(), new ShopTypeCalendar()));
    shops.add(new Shop(TypeShop.DYNAMIC_CALENDAR.name(), new ShopTypeDynamicCalendar()));
    shops.add(new Shop("categorys", new ShopTypePermanent()));
    shops.getLast().setSubShops(List.of(
      new SubShop(10, TypeShop.PERMANENT.name()),
      new SubShop(11, TypeShop.DYNAMIC.name()),
      new SubShop(12, TypeShop.WEEKLY.name()),
      new SubShop(13, TypeShop.DYNAMIC_WEEKLY.name()),
      new SubShop(14, TypeShop.CALENDAR.name()),
      new SubShop(15, TypeShop.DYNAMIC_CALENDAR.name())
    ));
    shops.getLast().setProducts(new ArrayList<>());
    int i = 0;
    for (Shop shop : shops) {
      shop.getDisplay().setSlot(i++);
      shop.check();
      write(path, shop);
    }
  }

  private static void write(String path, Shop shop) {
    String finalPath = path == null ? shop.getPath() : path;
    if (finalPath == null) return;
    CompletableFuture<Boolean> futureWrite = Utils.writeFileAsync(finalPath, shop.getId() + ".json", CobbleShop.gson.toJson(shop));
    if (!futureWrite.join()) {
      CobbleUtils.LOGGER.error("Error writing file: " + finalPath + shop.getId() + ".json");
    }
  }

  private static void readAllShops(File[] files, List<Shop> shops) {
    if (files == null) return;
    for (File file : files) {
      if (file.isDirectory()) {
        readAllShops(file.listFiles(), shops);
      } else {
        if (file.getName().endsWith(".json")) {
          Shop shop = null;
          try {
            String path = file.getPath();
            shop = CobbleShop.gson.fromJson(Utils.readFileSync(file), Shop.class);
            shop.setId(file.getName().replace(".json", ""));
            shop.check();
            shop.setPath(null);
            Utils.writeFileSync(file, CobbleShop.gson.toJson(shop));
            shop.setPath(path);
          } catch (IOException e) {
            e.printStackTrace();
          }
          shops.add(shop);
        }
      }
    }
  }

  public void open(ServerPlayerEntity player, ShopOptionsApi options) {
    ChestTemplate template = ChestTemplate
      .builder(rows)
      .build();


    PanelsConfig.applyConfig(template, panels, rows);

    List<Shop> shops = ShopApi.getShops(options);
    applyShops(shops, player, options, this, template, true);


    if (UIUtils.isInside(this.itemClose.getSlot(), rows)) {
      ItemModel close = CobbleShop.lang.getGlobalItemClose(itemClose);
      GooeyButton closeButton = close.getButton(1, action -> UIManager.closeUI(action.getPlayer()));
      template.set(itemClose.getSlot(), closeButton);
    }


    GooeyPage page = GooeyPage
      .builder()
      .template(template)
      .title(AdventureTranslator.toNative(title))
      .build();

    UIManager.openUIForcefully(player, page);
  }

  public static void applyShops(List<Shop> shops, ServerPlayerEntity player, ShopOptionsApi options, Config config,
                                ChestTemplate template, boolean withClose) {
    for (Shop shop : shops) {
      if (UIUtils.isInside(shop.getDisplay().getSlot(), config.getRows())) {
        ItemModel display = CobbleShop.lang.getGlobalDisplay(shop.getDisplay());
        List<String> lore = new ArrayList<>(display.getLore());
        lore.replaceAll(s -> shop.getType().replace(s, shop, options));
        GooeyButton button = display.getButton(
          1,
          display.getDisplayname().replace("%shop%", shop.getId()),
          lore,
          action -> Config.manageOpenShop(action.getPlayer(), options, config, shop, new Stack<>(), shop, withClose)
        );
        template.set(shop.getDisplay().getSlot(), button);
      }
    }
  }

  private static boolean canOpen(ServerPlayerEntity player, Shop shop) {
    boolean canopen = shop.getType().isOpen();
    if (!canopen) {
      PlayerUtils.sendMessage(
        player,
        CobbleShop.lang.getMessageShopNotOpen()
          .replace("%shop%", shop.getId()),
        CobbleShop.lang.getPrefix(),
        TypeMessage.CHAT
      );
    }
    return canopen;
  }

  public static void manageOpenShop(ServerPlayerEntity player, ShopOptionsApi options, Config config, Shop add,
                                    Stack<Shop> stack, Shop actual, boolean withClose) {

    Shop shop;
    if (stack == null) stack = new Stack<>();
    if (actual == null) {
      shop = stack.peek();
      shop.open(player, options, config, 0, stack, withClose);
    } else if (add != null) {
      if (canOpen(player, add)) {
        stack.push(add);
        add.open(player, options, config, 0, stack, withClose);
      }
    } else if (stack.isEmpty()) {
      config.open(player, options);
    } else {
      shop = stack.pop();
      if (actual.equals(shop)) {
        if (stack.isEmpty()) {
          config.open(player, options);
          return;
        }
        shop = stack.pop();
      }
      shop.open(player, options, config, 0, stack, withClose);
    }

  }

  public void createShop(ShopOptionsApi options, Shop shop) {
    shop.check();
    write(options.getPathShop(), shop);
    CobbleShop.load(options);
  }
}
