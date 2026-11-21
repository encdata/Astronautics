package com.encdata.astronautics.registry;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.content.items.OxygenTankItem;
import com.encdata.astronautics.content.items.SpaceSuitItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class AstronauticsItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Astronautics.MOD_ID);

    public static final Supplier<Item> NICKEL_DUST = register("nickel_dust", common());
    public static final Supplier<Item> NICKEL_INGOT = register("nickel_ingot", common());
    public static final Supplier<Item> NICKEL_NUGGET = register("nickel_nugget", common());
    public static final Supplier<Item> NICKEL_PLATE = register("nickel_plate", common());

    public static final Supplier<Item> TITANIUM_DUST = register("titanium_dust", uncommon());
    public static final Supplier<Item> TITANIUM_INGOT = register("titanium_ingot", uncommon());
    public static final Supplier<Item> TITANIUM_NUGGET = register("titanium_nugget", uncommon());
    public static final Supplier<Item> TITANIUM_PLATE = register("titanium_plate", uncommon());

    public static final Supplier<Item> URANIUM_DUST = register("uranium_dust", rare());
    public static final Supplier<Item> URANIUM_INGOT = register("uranium_ingot", rare());
    public static final Supplier<Item> URANIUM_NUGGET = register("uranium_nugget", rare());
    public static final Supplier<Item> URANIUM_PLATE = register("uranium_plate", rare());

    public static final Supplier<Item> EMPTY_OXYGEN_TANK = register("empty_oxygen_tank", uncommon());
    public static final Supplier<Item> OXYGEN_TANK = ITEMS.register("oxygen_tank",
            () -> new OxygenTankItem(uncommon()));
    public static final Supplier<Item> SPACE_SUIT_HELMET = ITEMS.register("space_suit_helmet",
            () -> new SpaceSuitItem(ArmorMaterials.IRON, ArmorItem.Type.HELMET, uncommon()));
    public static final Supplier<Item> SPACE_SUIT_CHESTPLATE = ITEMS.register("space_suit_chestplate",
            () -> new SpaceSuitItem(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE, uncommon()));
    public static final Supplier<Item> SPACE_SUIT_LEGGINGS = ITEMS.register("space_suit_leggings",
            () -> new SpaceSuitItem(ArmorMaterials.IRON, ArmorItem.Type.LEGGINGS, uncommon()));
    public static final Supplier<Item> SPACE_SUIT_BOOTS = ITEMS.register("space_suit_boots",
            () -> new SpaceSuitItem(ArmorMaterials.IRON, ArmorItem.Type.BOOTS, uncommon()));

    private AstronauticsItems() {}

    private static Supplier<Item> register(String name, Item.Properties properties) {
        return ITEMS.registerSimpleItem(name, properties);
    }

    private static Item.Properties common() {
        return new Item.Properties().rarity(Rarity.COMMON);
    }

    private static Item.Properties uncommon() {
        return new Item.Properties().rarity(Rarity.UNCOMMON);
    }

    private static Item.Properties rare() {
        return new Item.Properties().rarity(Rarity.RARE).fireResistant();
    }
}
