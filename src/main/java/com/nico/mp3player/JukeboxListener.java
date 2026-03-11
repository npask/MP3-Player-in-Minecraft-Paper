package com.nico.mp3player;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JukeboxListener implements Listener {

    private final Mp3player plugin;
    private final Map<String, DiscData> discMap = new HashMap<>(); // alle verfügbaren Discs
    private final Map<UUID, ItemStack> activeDiscs = new HashMap<>(); // gerade spielende Disc pro Spieler
    private final Map<UUID, Map<String, DiscData>> storage = new HashMap<>(); // gespeicherte Discs pro Spieler
    private final File savedFile = new File("plugins/mp3player/savedDiscs.json");
    private final Gson gson = new Gson();

    public JukeboxListener(Mp3player plugin) {
        this.plugin = plugin;
        loadDiscYml();
        loadSavedDiscs();
    }

    // --- Lädt Custom Discs ---
    private void loadDiscYml() {
        try {
            File discFile = new File("plugins/CustomJukebox/disc.json");
            if (!discFile.exists()) return;

            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> json = gson.fromJson(new FileReader(discFile), type);

            if (json.containsKey("discs")) {
                Object discsObj = json.get("discs");
                String discsJson = gson.toJson(discsObj);
                Type discType = new TypeToken<Map<String, DiscData>>() {}.getType();
                Map<String, DiscData> discs = gson.fromJson(discsJson, discType);
                discMap.putAll(discs);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // --- Gespeicherte Discs laden ---
    private void loadSavedDiscs() {
        try {
            if (!savedFile.exists()) return;
            Type type = new TypeToken<Map<String, Map<String, DiscData>>>() {}.getType();
            Map<String, Map<String, DiscData>> saved = gson.fromJson(new FileReader(savedFile), type);
            if (saved != null) {
                for (Map.Entry<String, Map<String, DiscData>> entry : saved.entrySet()) {
                    storage.put(UUID.fromString(entry.getKey()), entry.getValue());
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // --- Gespeicherte Discs speichern ---
    private void saveDiscs() {
        try (FileWriter writer = new FileWriter(savedFile)) {
            Map<String, Map<String, DiscData>> toSave = new HashMap<>();
            for (Map.Entry<UUID, Map<String, DiscData>> e : storage.entrySet()) {
                toSave.put(e.getKey().toString(), e.getValue());
            }
            gson.toJson(toSave, writer);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // --- Rechtsklick auf MP3-Player öffnet GUI ---
    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!e.getAction().name().startsWith("RIGHT_CLICK")) return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.CARROT_ON_A_STICK) return;

        e.setCancelled(true);
        openJukeboxGUI(e.getPlayer());
    }

    // --- GUI zeigt gespeicherte Discs und aktiven Song ---
    private void openJukeboxGUI(Player player) {
        String title = "§6MP3-Player";
        if (activeDiscs.containsKey(player.getUniqueId())) {
            ItemStack disc = activeDiscs.get(player.getUniqueId());
            title += " §a▶ " + disc.getItemMeta().getDisplayName();
        }

        Inventory gui = Bukkit.createInventory(null, 9, title);

        Map<String, DiscData> playerStorage = storage.getOrDefault(player.getUniqueId(), new HashMap<>());
        int index = 0;
        for (DiscData disc : playerStorage.values()) {
            if (index >= gui.getSize()) break;
            ItemStack discItem = new ItemStack(Material.valueOf(disc.type)); // Standard-Item
            ItemMeta meta = discItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(disc.displayName.replace("&", "§"));
                meta.setCustomModelData(disc.customModelData);
                discItem.setItemMeta(meta);
            }
            gui.setItem(index++, discItem);
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!e.getView().getTitle().startsWith("§6MP3-Player")) return;

        Player player = (Player) e.getPlayer();
        UUID uuid = player.getUniqueId();

        Map<String, DiscData> playerStorage = new HashMap<>();

        for (ItemStack item : e.getInventory().getContents()) {
            if (item == null) continue;
            if (!item.getType().toString().startsWith("MUSIC_DISC_")) continue;

            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            DiscData disc = findCustomDisc(item);
            if (disc != null) {
                // Custom Disc aus disc.json
                playerStorage.put(disc.displayName, disc);
            } else {
                // Normale Disc oder CustomDisc ohne Eintrag
                DiscData newDisc = new DiscData();
                newDisc.displayName = meta.hasDisplayName() ? meta.getDisplayName() : item.getType().toString();
                newDisc.type = item.getType().toString();
                newDisc.customModelData = meta.hasCustomModelData() ? meta.getCustomModelData() : 0;
                // Fallback-Werte für Sound/Kategorie falls nicht bekannt
                newDisc.sound = null;
                newDisc.author = null;
                newDisc.category = null;
                newDisc.durationTicks = 0;
                newDisc.fragmentCount = 0;
                playerStorage.put(newDisc.displayName, newDisc);
            }
        }

        // In unser Storage übernehmen und speichern
        storage.put(uuid, playerStorage);
        saveDiscs();
    }

    // --- Klick im GUI ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().startsWith("§6MP3-Player")) return;
        Player player = (Player) e.getWhoClicked();
        ItemStack clicked = e.getCurrentItem();

        if (clicked == null) return;

        if (!e.isRightClick() || !clicked.getType().toString().startsWith("MUSIC_DISC_")) return;

        e.setCancelled(true);
        UUID uuid = player.getUniqueId();
        DiscData data = findCustomDisc(clicked);

        // Stoppen, falls schon spielend
        if (activeDiscs.containsKey(uuid) && activeDiscs.get(uuid).equals(clicked)) {
            player.stopSound(data.sound);
            activeDiscs.remove(uuid);
            player.sendActionBar("§c⏹ Stopped: §e" + clicked.getItemMeta().getDisplayName());
        } else {
            // Abspielen
            player.stopSound(SoundCategory.MUSIC);
            player.stopSound(SoundCategory.MASTER);
            player.stopSound(SoundCategory.RECORDS);
            player.stopSound(SoundCategory.BLOCKS);
            player.stopSound(SoundCategory.NEUTRAL);
            player.stopSound(SoundCategory.VOICE);
            if (data != null && data.sound != null) {
                player.playSound(player, data.sound, 1f, 1f);
                player.sendActionBar("§a▶ Playing: §e" + data.displayName);
            } else {
                player.playSound(player.getLocation(), Sound.valueOf(clicked.getType().toString()), 1f, 1f);
                player.sendActionBar("§a▶ Playing: §e" + clicked.getItemMeta().getDisplayName());
            }
            activeDiscs.put(uuid, clicked);
        }

        // GUI aktualisieren
        openJukeboxGUI(player);

        // Speichern
        saveDiscs();
    }

    // --- Disc finden ---
    public DiscData findCustomDisc(ItemStack item) {
        if (item.getItemMeta() == null) return null;
        Integer cmd = item.getItemMeta().getCustomModelData();
        String displayName = item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : null;

        for (DiscData disc : discMap.values()) {
            if ((cmd != null && cmd.equals(disc.customModelData)) ||
                    (displayName != null && displayName.equals(disc.displayName))) {
                return disc;
            }
        }
        return null;
    }

    private static class DiscData {
        public String displayName;
        public String author;
        public String sound;
        public String type;
        public long durationTicks;
        public int fragmentCount;
        public String category;
        public int customModelData;
    }
}