package com.kingpixel.cobbleshop.models;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.button.linked.LinkType;
import ca.landonjw.gooeylibs2.api.button.linked.LinkedPageButton;
import ca.landonjw.gooeylibs2.api.helpers.PaginationHelper;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.page.LinkedPage;
import ca.landonjw.gooeylibs2.api.template.slot.TemplateSlotDelegate;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.adapters.ShopType;
import com.kingpixel.cobbleshop.adapters.ShopTypePermanent;
import com.kingpixel.cobbleshop.api.ShopApi;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.config.Config;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.*;
import com.kingpixel.cobbleutils.api.EconomyApi;
import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.util.*;
import com.kingpixel.cobbleutils.util.economys.ImpactorEconomy;
import lombok.Data;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:19
 */
@Data
public class Shop {
  private String Path;
  // Essential fields
  private boolean autoPlace;
  private String id;
  private String title;
  private String currency;
  private EconomyUse economy;
  private String closeCommand;
  private String soundOpen = "block.chest.open";
  private String soundClose = "block.chest.close";
  private int rows;
  private float globalDiscount;
  private String colorProduct = "";
  private ShopType type;
  private Rectangle rectangle;
  private Map<String, Float> discounts;
  private ItemModel display;
  private ItemModel itemInfoShop;
  private ItemModel itemBalance;
  private List<SubShop> subShops;
  private List<Product> products;
  private ItemModel itemPrevious;
  private ItemModel itemClose;
  private ItemModel itemNext;
  private List<PanelsConfig> panels;

  public Shop() {
    this.autoPlace = true;
    this.id = "shop";
    this.title = "%shop%";
    this.currency = "impactor:dollars";
    this.closeCommand = "";
    this.rows = 6;
    this.globalDiscount = 0;
    this.type = new ShopTypePermanent();
    this.discounts = new HashMap<>();
    discounts.put("group.vip", 2.0f);
    this.rectangle = new Rectangle(1, 1, 4, 7);
    this.display = new ItemModel("");
    this.itemInfoShop = new ItemModel("");
    itemInfoShop.setSlot(51);
    this.itemBalance = new ItemModel("");
    itemBalance.setSlot(47);
    this.products = getDefaultProducts();
    this.itemPrevious = new ItemModel("");
    itemPrevious.setSlot(45);
    this.itemClose = new ItemModel("");
    itemClose.setSlot(49);
    this.itemNext = new ItemModel("");
    itemNext.setSlot(53);
    this.panels = List.of(
      new PanelsConfig(new ItemModel("minecraft:gray_stained_glass_pane"), rows)
    );
  }

  public Shop(String id, ShopType type) {
    this.autoPlace = true;
    this.id = id;
    this.title = "%shop%";
    this.currency = "impactor:dollars";
    this.closeCommand = "";
    this.soundOpen = "block.chest.open";
    this.soundClose = "block.chest.close";
    this.rows = 6;
    this.globalDiscount = 0;
    this.type = type;
    this.discounts = new HashMap<>();
    discounts.put("group.vip", 2.0f);
    this.subShops = new ArrayList<>();
    this.rectangle = new Rectangle(1, 1, 4, 7);
    this.display = new ItemModel("");
    this.itemInfoShop = new ItemModel("");
    itemInfoShop.setSlot(51);
    this.itemBalance = new ItemModel("");
    itemBalance.setSlot(47);
    this.products = getDefaultProducts();
    this.itemPrevious = new ItemModel("");
    itemPrevious.setSlot(45);
    this.itemClose = new ItemModel("");
    itemClose.setSlot(49);
    this.itemNext = new ItemModel("");
    itemNext.setSlot(53);
    this.panels = List.of(
      new PanelsConfig(new ItemModel("minecraft:gray_stained_glass_pane"), rows)
    );
  }

  private void write(ShopOptionsApi options) {
    CompletableFuture<Boolean> futureWrite = Utils.writeFileAsync(this.getPath(), this.getId() + ".json",
      CobbleShop.gson.toJson(this));
    if (!futureWrite.join()) {
      CobbleUtils.LOGGER.error("Error writing file: " + this.getPath() + this.getId() + ".json");
    }
    CobbleShop.load(options);
  }

  private Product from(ItemChance itemChance) {
    Product product = new Product();
    product.setProduct(itemChance.getItem());
    product.setDisplay(itemChance.getDisplay());
    return product;
  }

  private List<Product> getDefaultProducts() {
    List<ItemChance> itemChances = ItemChance.defaultItemChances();
    List<Product> products = new ArrayList<>();
    for (ItemChance itemChance : itemChances) {
      products.add(from(itemChance));
    }
    products.add(new Product(true));
    return products;
  }

  public void check() {
    if (subShops == null) subShops = new ArrayList<>();
    if (economy == null) economy = new EconomyUse(ImpactorEconomy.IDENTIFY, currency);
    products.forEach(product -> product.check(this));
    type.check();
  }

  public String getPermission(ShopOptionsApi options) {
    String modId = options.getModId().equals(CobbleShop.MOD_ID) ? CobbleShop.MOD_ID :
      options.getModId() + ".shop";
    return modId + ".shops." + id;
  }

  public void open(ServerPlayerEntity player, ShopOptionsApi options, Config config, int position, Stack<Shop> shop, boolean withClose) {
    CompletableFuture.runAsync(() -> {
        if (!PermissionApi.hasPermission(player, getPermission(options), 4)) {
          PlayerUtils.sendMessage(
            player,
            CobbleShop.lang.getMessageNotHavePermission()
              .replace("%shop%", title)
              .replace("%permission%", getPermission(options)),
            CobbleShop.lang.getPrefix(),
            TypeMessage.CHAT
          );
          return;
        }
        ChestTemplate template = ChestTemplate
          .builder(rows)
          .build();

        PanelsConfig.applyConfig(template, this.getPanels(), rows);
        BigDecimal balance = EconomyApi.getBalance(player.getUuid(), economy);
        String playerBalance = EconomyApi.formatMoney(balance, economy);
        int totalSlots = rectangle.getLength() * rectangle.getWidth();
        List<Product> products = type.getProducts(this, options);
        int totalProducts = products.size();
        int totalSubShops = subShops.size();
        boolean hasEnoughtButtons = subShops.isEmpty() ? totalProducts > totalSlots : totalSubShops > totalSlots;
        List<Button> buttons = new ArrayList<>();
        if (subShops.isEmpty()) {
          // Products
          if (hasEnoughtButtons || autoPlace) {
            for (Product product : products) {
              if (!product.hasErrors())
                buttons.add(product.getIcon(player, shop, null, 1, options, config, withClose, playerBalance));
            }
          } else {
            for (Product product : products) {
              Integer slot = product.getSlot();
              if (slot == null) {
                CobbleUtils.LOGGER.error(options.getModId(), "Slot is null -> " + product.getProduct());
                continue;
              }
              TemplateSlotDelegate templateSlotDelegate = template.getSlot(slot);
              if (templateSlotDelegate != null) {
                if (UIUtils.isInside(slot, rows)) template.set(slot, product.getIcon(player, shop, null, 1, options,
                  config, withClose, playerBalance));
              } else {
                CobbleUtils.LOGGER.error(options.getModId(),
                  "Slot has a product or button -> " + slot + " Product -> " + product.getProduct());
                PlayerUtils.sendMessage(
                  player,
                  "Slot has a product or button -> " + slot + " Product -> " + product.getProduct(),
                  CobbleShop.lang.getPrefix(),
                  TypeMessage.CHAT
                );
              }
            }
          }
        } else {
          // Categories
          if (autoPlace) {
            subShops.forEach(subShop -> {
              buttons.add(getSubShopButton(options, subShop, player, config, shop, withClose));
            });
          } else {
            subShops.forEach(subShop -> {
              if (UIUtils.isInside(subShop.getSlot(), rows)) {
                template.set(subShop.getSlot(), getSubShopButton(options, subShop, player, config, shop, withClose));
              }
            });
          }
        }

        if (UIUtils.isInside(itemInfoShop.getSlot(), rows)) {
          template.set(itemInfoShop.getSlot(), CobbleShop.lang.getInfoShopType().getShopType(
            this,
            options,
            getItemInfoShop()
          ));
        }


        if (UIUtils.isInside(this.itemBalance.getSlot(), rows)) {
          ItemModel itemBalance = CobbleShop.lang.getGlobalItemBalance(this.itemBalance);
          String format = EconomyApi.formatMoney(balance, economy);
          String nameBalance = itemBalance.getDisplayname()
            .replace("%balance%", format)
            .replace("%currency%", economy.getCurrency())
            .replace("%amount%", format);
          List<String> lore = new ArrayList<>(itemBalance.getLore());
          lore.replaceAll(s -> s
            .replace("%balance%", format)
            .replace("%currency%", economy.getCurrency())
            .replace("%amount%", format));
          template.set(this.itemBalance.getSlot(), itemBalance.getButton(1,
            nameBalance,
            lore,
            action -> {

            }));
        }


        if (UIUtils.isInside(this.itemClose.getSlot(), rows) && withClose) {
          ItemModel itemClose = CobbleShop.lang.getGlobalItemClose(this.itemClose);
          Button closeButton = itemClose.getButton(1, action -> {
            if (!this.getCloseCommand().isEmpty()) {
              PlayerUtils.executeCommand(
                this.getCloseCommand(),
                player
              );
              return;
            }
            Config.manageOpenShop(player, options, config, null, shop, this, withClose);
          });
          template.set(this.itemClose.getSlot(), closeButton);
        }

        if (hasEnoughtButtons) {
          if (UIUtils.isInside(this.itemPrevious.getSlot(), rows)) {
            ItemModel previous = CobbleShop.lang.getGlobalItemPrevious(this.itemPrevious);
            template.set(this.itemPrevious.getSlot(), LinkedPageButton.builder()
              .display(previous.getItemStack())
              .linkType(LinkType.Previous)
              .build());
          }

          if (UIUtils.isInside(this.itemNext.getSlot(), rows)) {
            ItemModel next = CobbleShop.lang.getGlobalItemNext(this.itemNext);
            template.set(this.itemNext.getSlot(), LinkedPageButton.builder()
              .display(next.getItemStack())
              .linkType(LinkType.Next)
              .build());
          }

        }


        GooeyPage page;

        title = title.replace("%shop%", id);

        if (hasEnoughtButtons || autoPlace) {
          rectangle.apply(template);

          LinkedPage.Builder linkedPage = LinkedPage.builder()
            .template(template)
            .onOpen(action -> {
              new Sound(soundOpen).playSoundPlayer(action.getPlayer());
            })
            .title(AdventureTranslator.toNative(title));

          page = PaginationHelper.createPagesFromPlaceholders(template, buttons, linkedPage);
        } else {
          page = GooeyPage.builder()
            .template(template)
            .onOpen(action -> {
              new Sound(soundOpen).playSoundPlayer(player);
            })
            .build();
          page.setTitle(AdventureTranslator.toNative(title));
        }


        UIManager.openUIForcefully(player, page);
      })
      .orTimeout(5, TimeUnit.SECONDS)
      .exceptionally(e -> {
        e.printStackTrace();
        PlayerUtils.sendMessage(
          player,
          "Error opening shop",
          CobbleShop.lang.getPrefix(),
          TypeMessage.CHAT
        );
        return null;
      });


  }

  private GooeyButton getSubShopButton(ShopOptionsApi options, SubShop subShop, ServerPlayerEntity player,
                                       Config config, Stack<Shop> shop, boolean withClose) {
    Shop category = ShopApi.getShop(options, subShop.getIdShop());
    if (category == null) {
      CobbleUtils.LOGGER.info(CobbleShop.MOD_ID, "Not Found the shop with id -> " + subShop.getIdShop());
      return null;
    }
    ItemModel display = CobbleShop.lang.getGlobalDisplay(category.getDisplay());
    List<String> lore = new ArrayList<>(display.getLore());
    return display.getButton(
      1,
      display.getDisplayname().replace("%shop%", category.getId()),
      lore,
      action -> Config.manageOpenShop(player, options, config, category, shop, this, withClose)
    );
  }

}
