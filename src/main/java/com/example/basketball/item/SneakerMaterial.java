package com.example.basketball.item;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Материал кроссовок. Текстуры слоёв лежат в assets/minecraft/textures/models/armor/basketball_<имя>_layer_1.png */
public enum SneakerMaterial implements ArmorMaterial {
    FROST("basketball_frost"), BLAZE("basketball_blaze"), ROYAL("basketball_royal");

    private final String name;

    SneakerMaterial(String name) { this.name = name; }

    @Override public int getDurability(ArmorItem.Type type) { return 260; }
    @Override public int getProtection(ArmorItem.Type type) { return 0; }
    @Override public int getEnchantability() { return 12; }
    @Override public SoundEvent getEquipSound() { return SoundEvents.ITEM_ARMOR_EQUIP_LEATHER; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.ofItems(Items.LEATHER); }
    @Override public String getName() { return name; }
    @Override public float getToughness() { return 0f; }
    @Override public float getKnockbackResistance() { return 0f; }
}
