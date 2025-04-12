package com.kingpixel.cobbleshop.command;

import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.adapters.*;
import com.kingpixel.cobbleshop.api.ShopApi;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.config.Config;
import com.kingpixel.cobbleshop.gui.edit.MenuEdit;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleshop.models.TypeShop;
import com.kingpixel.cobbleutils.api.PermissionApi;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Stack;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:10
 */
public class CommandTree {
  public static void register(ShopOptionsApi options, CommandDispatcher<ServerCommandSource> dispatcher) {
    LiteralArgumentBuilder<ServerCommandSource> base;

    for (String command : options.getCommands()) {
      if (options.getModId().equals(CobbleShop.MOD_ID)) {
        base = build(CommandManager.literal(command), options);
      } else {
        base = CommandManager.literal(command)
          .then(
            build(CommandManager.literal("shop"), options)
          );
      }

      dispatcher.register(base);
    }

    // Sell
    dispatcher.register(
      CommandManager.literal("sell")
        .requires(source -> PermissionApi.hasPermission(source, "cobbleshop.sell.base", 2))
        .then(
          CommandManager.literal("hand")
            .executes(context -> {
              if (!context.getSource().isExecutedByPlayer()) return 0;
              ServerPlayerEntity player = context.getSource().getPlayer();
              if (player == null) return 0;
              PlayerInventory inventory = player.getInventory();
              if (inventory == null) return 0;
              ShopApi.sellAll(context.getSource().getPlayer(), List.of(inventory.getMainHandStack()), options);
              return 1;
            }).then(
              CommandManager.argument("player", EntityArgumentType.player())
                .requires(source -> PermissionApi.hasPermission(source, "cobbleshop.sell.other", 2))
                .executes(context -> {
                  if (!context.getSource().isExecutedByPlayer()) return 0;
                  ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                  ShopApi.sellAll(player, List.of(player.getMainHandStack()), options);
                  return 1;
                })
            )
        ).then(
          CommandManager.literal("all")
            .executes(context -> {
              if (!context.getSource().isExecutedByPlayer()) return 0;
              ServerPlayerEntity player = context.getSource().getPlayer();
              if (player == null) return 0;
              PlayerInventory inventory = player.getInventory();
              if (inventory == null) return 0;
              ShopApi.sellAll(context.getSource().getPlayer(), inventory.main, options);
              return 1;
            }).then(
              CommandManager.argument("player", EntityArgumentType.player())
                .requires(source -> PermissionApi.hasPermission(source, "cobbleshop.sell.other", 2))
                .executes(context -> {
                  if (!context.getSource().isExecutedByPlayer()) return 0;
                  ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                  ShopApi.sellAll(player, player.getInventory().main, options);
                  return 1;
                })
            )
        )
    );


  }

  private static LiteralArgumentBuilder<ServerCommandSource> build(LiteralArgumentBuilder<ServerCommandSource> base, ShopOptionsApi options) {
    String modId = options.getModId().equals(CobbleShop.MOD_ID) ? CobbleShop.MOD_ID :
      options.getModId() + ".shop";
    base
      .requires(source -> PermissionApi.hasPermission(source, List.of(modId + ".base",
        modId + ".admin"), 2))
      .executes(context -> {
        if (!context.getSource().isExecutedByPlayer()) return 0;
        ShopApi.getConfig(options).open(context.getSource().getPlayer(), options);
        return 1;
      }).then(
        CommandManager.literal("reload")
          .requires(source -> PermissionApi.hasPermission(source, List.of(modId + ".reload", modId + ".admin"), 2))
          .executes(context -> {
            if (!context.getSource().isExecutedByPlayer()) return 0;
            CobbleShop.load(options);
            return 1;
          })
      ).then(
        CommandManager.literal("other")
          .requires(source -> PermissionApi.hasPermission(source, List.of(modId + ".other",
            modId + ".admin"), 2))
          .then(
            CommandManager.argument("player", EntityArgumentType.players())
              .executes(context -> {
                var players = EntityArgumentType.getPlayers(context, "player");
                if (players.isEmpty()) return 0;
                var config = ShopApi.getConfig(options);
                for (ServerPlayerEntity player : players) {
                  config.open(player, options);
                }
                return 1;
              }).then(
                CommandManager.argument("IdShop", StringArgumentType.string())
                  .suggests((context, builder) -> {
                    for (Shop shop : ShopApi.getShops(options)) {
                      builder.suggest(shop.getId());
                    }
                    return builder.buildFuture();
                  })
                  .executes(context -> {
                    openShop(context, options, true);
                    return 1;
                  }).then(
                    CommandManager.argument("WithClose", BoolArgumentType.bool())
                      .executes(context -> {
                        boolean withClose = BoolArgumentType.getBool(context, "WithClose");
                        openShop(context, options, withClose);
                        return 1;
                      })
                  )
              )
          )
      ).then(
        CommandManager.literal("edit")
          .requires(source -> PermissionApi.hasPermission(source, List.of(modId + ".edit", modId + ".admin"), 2))
          .then(
            CommandManager.argument("shop", StringArgumentType.string())
              .suggests((commandContext, suggestionsBuilder) -> {
                for (Shop shop : ShopApi.getShops(options)) {
                  suggestionsBuilder.suggest(shop.getId());
                }
                return suggestionsBuilder.buildFuture();
              })
              .executes(context -> {
                if (!context.getSource().isExecutedByPlayer()) return 0;
                String idShop = StringArgumentType.getString(context, "shop");
                var shop = ShopApi.getShop(options, idShop);
                if (shop == null) return 0;
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player == null) return 0;
                player.sendMessage(
                  Text.literal("This command is in development, please configure it in the json file"), false
                );
                MenuEdit.open(context.getSource().getPlayer(), shop, options);
                return 1;
              })
          )
      ).then(
        CommandManager.literal("create")
          .requires(source -> PermissionApi.hasPermission(source, List.of(modId + ".create", modId + ".admin"), 2))
          .then(
            CommandManager.argument("shop", StringArgumentType.string())
              .then(
                CommandManager.argument("type", StringArgumentType.string())
                  .suggests((commandContext, suggestionsBuilder) -> {
                    for (TypeShop value : TypeShop.values()) {
                      suggestionsBuilder.suggest(value.name());
                    }
                    return suggestionsBuilder.buildFuture();
                  })
                  .executes(context -> {
                    if (!context.getSource().isExecutedByPlayer()) return 0;
                    String id = StringArgumentType.getString(context, "shop");
                    boolean exist = ShopApi.getShops(options).stream().anyMatch(shop -> shop.getId().equals(id));
                    if (exist) {
                      context.getSource().sendMessage(
                        Text.literal("The shop already exists")
                      );
                      return 0;
                    }
                    Shop shop = new Shop(id, new ShopTypePermanent());
                    shop.setType(ShopType.get(StringArgumentType.getString(context, "type")));
                    ShopApi.getConfig(options).createShop(options, shop);
                    return 1;
                  })
              )
          )
      ).then(
        CommandManager.literal("update")
          .requires(source -> PermissionApi.hasPermission(source, List.of(modId + ".update", modId + ".admin"), 2))
          .then(
            CommandManager.argument("shop", StringArgumentType.string())
              .suggests((context, builder) -> {
                for (Shop shop : ShopApi.getShops(options)) {
                  if (shop.getType() instanceof ShopTypeDynamic) {
                    builder.suggest(shop.getId());
                  } else if (shop.getType() instanceof ShopTypeDynamicWeekly) {
                    builder.suggest(shop.getId());
                  } else if (shop.getType() instanceof ShopTypeDynamicCalendar) {
                    builder.suggest(shop.getId());
                  }
                }
                return builder.buildFuture();
              })
              .executes(context -> {
                Shop shop = ShopApi.getShop(options, StringArgumentType.getString(context, "shop"));
                CobbleShop.dataShop.updateDynamicProducts(shop, options, true);
                context.getSource().sendMessage(
                  Text.literal(
                    "The shop " + shop.getId() + " has been updated, please open the shop to see the changes"
                  )
                );
                return 1;
              })
          )
      );

    return base;
  }

  private static void openShop(CommandContext<ServerCommandSource> context, ShopOptionsApi options, boolean withClose) {
    try {
      var players = EntityArgumentType.getPlayers(context, "player");
      String s = StringArgumentType.getString(context, "IdShop");
      Config config = ShopApi.getConfig(options);
      Shop shop = ShopApi.getShop(options, s);
      if (shop == null) {
        for (ServerPlayerEntity player : players) {
          config.open(player, options);
        }
      } else {
        Stack<Shop> stack = new Stack<>();
        stack.push(shop);
        for (ServerPlayerEntity player : players) {
          shop.open(
            player,
            options,
            config,
            0,
            stack,
            withClose
          );
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }


}
