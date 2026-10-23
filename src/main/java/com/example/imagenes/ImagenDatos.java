package com.example.imagenes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Guarda en el mundo los ajustes de cada imagen y cuáles están fijas en pantalla (sobrevive a reinicios). */
public class ImagenDatos extends SavedData {
    private static final String NOMBRE = "imagenes_datos";

    /** Una imagen fija: la ve todo el que entre (todos) o solo ciertos jugadores. */
    public static final class Permanente {
        public final boolean todos;
        public final Set<UUID> jugadores;

        public Permanente(boolean todos, Set<UUID> jugadores) {
            this.todos = todos;
            this.jugadores = jugadores;
        }
    }

    public final Map<String, Config> configs = new LinkedHashMap<>();
    public final Map<String, Permanente> permanentes = new LinkedHashMap<>();

    public static ImagenDatos get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ImagenDatos::cargar, ImagenDatos::new, NOMBRE);
    }

    public Config config(String id) {
        return configs.computeIfAbsent(id, k -> new Config());
    }

    public static ImagenDatos cargar(CompoundTag tag) {
        ImagenDatos d = new ImagenDatos();

        ListTag cs = tag.getList("configs", Tag.TAG_COMPOUND);
        for (int i = 0; i < cs.size(); i++) {
            CompoundTag t = cs.getCompound(i);
            Config c = new Config();
            c.zona = Posicion.deIndice(t.getInt("zona"));
            c.tamPct = clamp(t.getInt("tam"), 1, 100);
            c.duracionSeg = Math.max(0, t.getInt("dur"));
            c.opacidad = clamp(t.getInt("op"), 0, 100);
            c.margen = clamp(t.getInt("margen"), 0, 200);
            d.configs.put(t.getString("id"), c);
        }

        ListTag ps = tag.getList("permanentes", Tag.TAG_COMPOUND);
        for (int i = 0; i < ps.size(); i++) {
            CompoundTag t = ps.getCompound(i);
            Set<UUID> js = new HashSet<>();
            ListTag lista = t.getList("jugadores", Tag.TAG_STRING);
            for (int j = 0; j < lista.size(); j++) {
                try {
                    js.add(UUID.fromString(lista.getString(j)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            d.permanentes.put(t.getString("id"), new Permanente(t.getBoolean("todos"), js));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag cs = new ListTag();
        for (Map.Entry<String, Config> e : configs.entrySet()) {
            Config c = e.getValue();
            CompoundTag t = new CompoundTag();
            t.putString("id", e.getKey());
            t.putInt("zona", c.zona.ordinal());
            t.putInt("tam", c.tamPct);
            t.putInt("dur", c.duracionSeg);
            t.putInt("op", c.opacidad);
            t.putInt("margen", c.margen);
            cs.add(t);
        }
        tag.put("configs", cs);

        ListTag ps = new ListTag();
        for (Map.Entry<String, Permanente> e : permanentes.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", e.getKey());
            t.putBoolean("todos", e.getValue().todos);
            ListTag lista = new ListTag();
            for (UUID u : e.getValue().jugadores) lista.add(StringTag.valueOf(u.toString()));
            t.put("jugadores", lista);
            ps.add(t);
        }
        tag.put("permanentes", ps);
        return tag;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
