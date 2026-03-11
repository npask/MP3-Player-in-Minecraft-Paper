package com.nico.mp3player;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;

public final class Mp3player extends JavaPlugin {

    // Map<PlayerUUID, DiscType>
    public HashMap<UUID, String> activeDiscs = new HashMap<>();

    private NamespacedKey jukeboxKey;

    @Override
    public void onEnable() {
        // Listener registrieren
        getServer().getPluginManager().registerEvents(new JukeboxListener(this), this);
        loadJukeboxState();

        // Custom Crafting-Rezept registrieren
        jukeboxKey = new NamespacedKey(this, "custom_jukebox");
        registerCraftingRecipe();
    }

    @Override
    public void onDisable() {
        saveJukeboxState();

        // Rezept beim Disable entfernen
        Bukkit.removeRecipe(jukeboxKey);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if(!player.isOp()){
            player.sendMessage("§4Du hast keine Rechte dazu!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("givejukebox")) {
            player.getInventory().addItem(createCustomJukebox());
            player.sendMessage("§6Du hast eine Custom Jukebox erhalten!");
            return true;
        }
        return false;
    }

    // --- Custom Jukebox Item ---
    public ItemStack createCustomJukebox() {
        ItemStack jukebox = new ItemStack(Material.CARROT_ON_A_STICK);
        ItemMeta meta = jukebox.getItemMeta();
        meta.setDisplayName("§6MP3-Player");
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(new NamespacedKey(this, "mp3player"), PersistentDataType.STRING, "mp3player");
        meta.setItemModel(new NamespacedKey("minecraft", "mp3player"));
        jukebox.setItemMeta(meta);
        return jukebox;
    }

    // --- Crafting-Rezept registrieren ---
    private void registerCraftingRecipe() {
        ItemStack result = createCustomJukebox();
        ShapedRecipe recipe = new ShapedRecipe(jukeboxKey, result);
        recipe.shape("TGT", "NJN", "NRN");
        recipe.setIngredient('N', Material.IRON_NUGGET);
        recipe.setIngredient('R', Material.REDSTONE);
        recipe.setIngredient('J', Material.JUKEBOX);
        recipe.setIngredient('T', Material.REDSTONE_TORCH);
        recipe.setIngredient('G', Material.GLASS_PANE);
        Bukkit.addRecipe(recipe);
    }

    // --- Status speichern ---
    public void saveJukeboxState() {
        activeDiscs.forEach((uuid, disc) -> getConfig().set(uuid.toString(), disc));
        saveConfig();
    }

    // --- Status laden ---
    public void loadJukeboxState() {
        getConfig().getKeys(false).forEach(key -> {
            UUID uuid = UUID.fromString(key);
            String disc = getConfig().getString(key);
            if (disc != null) activeDiscs.put(uuid, disc);
        });
    }
}