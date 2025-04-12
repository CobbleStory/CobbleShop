package com.kingpixel.cobbleshop.models;

import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.adapters.ShopType;
import com.kingpixel.cobbleshop.adapters.ShopTypeDynamic;
import com.kingpixel.cobbleshop.adapters.ShopTypeDynamicWeekly;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleutils.util.Utils;
import lombok.Data;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos Varas Alonso - 22/02/2025 4:11
 */
@Data
public class DataShop {
  // ModId -> ProductId -> DynamicProduct
  public Map<String, Map<String, DynamicProduct>> products;

  public DataShop() {
    products = new HashMap<>();
  }

  public void init() {
    File file = Utils.getAbsolutePath(CobbleShop.PATH_DATA);
    if (!file.exists()) {
      file.mkdirs();
    }

    CompletableFuture<Boolean> futureRead = Utils.readFileAsync(CobbleShop.PATH_DATA, "dataShop.json", call -> {
      CobbleShop.dataShop = CobbleShop.gsonWithOutSpaces.fromJson(call, DataShop.class);
      check();
      write();
    });

    if (!futureRead.join()) {
      CobbleShop.dataShop = this;
      write();
    }

  }

  public void write() {
    Utils.writeFileAsync(CobbleShop.PATH_DATA, "dataShop.json", CobbleShop.gsonWithOutSpaces.toJson(CobbleShop.dataShop));
  }

  public void check() {
    boolean changed = false;
    // Todo: Check if the shop exists


    // Todo: Check if the product exists
    if (changed) {
      write();
    }
  }

  public List<Product> updateDynamicProducts(Shop shop, ShopOptionsApi options, boolean force) {
    products.computeIfAbsent(options.getModId(), k -> new HashMap<>()).computeIfAbsent(shop.getId(), k -> new DynamicProduct());
    DynamicProduct dynamicProduct = products.get(options.getModId()).get(shop.getId());
    if (dynamicProduct.getTimeToUpdate() < System.currentTimeMillis() || dynamicProduct.getProducts().isEmpty()
      || dynamicProduct.getProducts().size() != getRotationProducts(shop) || force) {
      dynamicProduct.setTimeToUpdate(System.currentTimeMillis() + getCooldown(shop.getType()));
      List<Product> products = getNewProducts(shop, options);
      dynamicProduct.setProducts(products);
      write();
      CobbleShop.initSellProduct(options);
      return products;
    }
    return dynamicProduct.getProducts();
  }

  private int getRotationProducts(Shop shop) {
    ShopType shopType = shop.getType();
    if (shopType instanceof ShopTypeDynamic) {
      ShopTypeDynamic shopTypeDynamic = (ShopTypeDynamic) shopType;
      return shopTypeDynamic.getProductsRotation();
    } else if (shopType instanceof ShopTypeDynamicWeekly) {
      ShopTypeDynamicWeekly shopTypeDynamicWeekly = (ShopTypeDynamicWeekly) shopType;
      return shopTypeDynamicWeekly.getProductsRotation();
    }
    return 3;
  }

  public long getCooldown(ShopType shopType) {
    long cooldown = 30;
    if (shopType instanceof ShopTypeDynamic) {
      ShopTypeDynamic shopTypeDynamic = (ShopTypeDynamic) shopType;
      cooldown = shopTypeDynamic.getCooldown();
    } else if (shopType instanceof ShopTypeDynamicWeekly) {
      ShopTypeDynamicWeekly shopTypeDynamicWeekly = (ShopTypeDynamicWeekly) shopType;
      cooldown = shopTypeDynamicWeekly.getCooldown();
    }
    return TimeUnit.MINUTES.toMillis(cooldown);
  }

  public List<Product> getNewProducts(Shop shop, ShopOptionsApi options) {
    int productsRotation = getRotationProducts(shop);
    List<Product> products = new ArrayList<>(shop.getProducts());
    // Shuffle the list to get random products
    Collections.shuffle(products);
    // Obtain the number of products in rotation
    if (products.size() > productsRotation) {
      products = products.subList(0, productsRotation);
    }
    return products;
  }

  public long getActualCooldown(Shop shop, ShopOptionsApi shopOptionsApi) {
    return products
      .computeIfAbsent(shopOptionsApi.getModId(), k -> new HashMap<>())
      .computeIfAbsent(shop.getId(), k -> new DynamicProduct())
      .getTimeToUpdate();
  }
}
