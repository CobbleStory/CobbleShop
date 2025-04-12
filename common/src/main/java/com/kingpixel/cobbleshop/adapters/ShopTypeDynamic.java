package com.kingpixel.cobbleshop.adapters;

import com.google.gson.*;
import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.models.Product;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleshop.models.TypeShop;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;

/**
 * Author: Carlos Varas Alonso - 21/02/2025 5:23
 */
@EqualsAndHashCode(callSuper = true) @Data
public class ShopTypeDynamic extends ShopType implements JsonSerializer<ShopTypeDynamic>, JsonDeserializer<ShopTypeDynamic> {
  public static ShopTypeDynamic INSTANCE = new ShopTypeDynamic();
  private int cooldown;
  private int productsRotation;

  public ShopTypeDynamic() {
    setTypeShop(TypeShop.DYNAMIC);
    cooldown = 30;
    productsRotation = 3;
  }

  public ShopTypeDynamic(int cooldown, int productsRotation) {
    setTypeShop(TypeShop.DYNAMIC);
    this.cooldown = cooldown;
    this.productsRotation = productsRotation;
  }

  @Override public void check() {
    setTypeShop(TypeShop.DYNAMIC);
    cooldown = Math.max(1, cooldown);
    productsRotation = Math.max(1, productsRotation);
  }

  @Override public List<Product> getProducts(Shop shop, ShopOptionsApi options) {
    return getDynamicProducts(shop, options);
  }

  public static List<Product> getDynamicProducts(Shop shop, ShopOptionsApi options) {
    return CobbleShop.dataShop.updateDynamicProducts(shop, options, false);
  }

  @Override public String replace(String text, Shop shop, ShopOptionsApi shopOptionsApi) {
    return text
      .replace("%cooldown%", PlayerUtils.getCooldown(
        new Date(CobbleShop.dataShop.getActualCooldown(shop, shopOptionsApi))
      ))
      .replace("%number%", String.valueOf(this.getProductsRotation()))
      .replace("%amountProducts%", String.valueOf(this.getProductsRotation()));
  }

  // JSON

  @Override public JsonElement serialize(ShopTypeDynamic src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("typeShop", src.getTypeShop().toString());
    jsonObject.addProperty("cooldown", src.getCooldown());
    jsonObject.addProperty("productsRotation", src.getProductsRotation());
    return jsonObject;
  }

  @Override
  public ShopTypeDynamic deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jsonObject = json.getAsJsonObject();

    JsonElement jsonCooldown = jsonObject.get("cooldown");
    int cooldown = (jsonCooldown != null) ? jsonCooldown.getAsInt() : 30; // Default value 30

    JsonElement jsonProductsRotation = jsonObject.get("productsRotation");
    int productsRotation = (jsonProductsRotation != null) ? jsonProductsRotation.getAsInt() : 3; // Default value 3

    ShopTypeDynamic shopTypeDynamic = new ShopTypeDynamic(cooldown, productsRotation);
    shopTypeDynamic.setTypeShop(TypeShop.valueOf(jsonObject.get("typeShop").getAsString()));

    return shopTypeDynamic;
  }
}