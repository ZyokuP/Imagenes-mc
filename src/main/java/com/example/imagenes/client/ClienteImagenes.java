package com.example.imagenes.client;

import com.example.imagenes.Posicion;
import com.example.imagenes.network.ModNetwork;
import com.example.imagenes.network.PedirPaquete;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lado cliente: recibe las órdenes del servidor, descarga la imagen si hace falta (caché imagenes_cache/),
 * crea la textura y la dibuja en el HUD. No toca nada fuera de sus propias imágenes.
 */
public class ClienteImagenes {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int FADE_MS = 400;

    public record Datos(String id, int ofertaId, long tam, String clave,
                        int zona, int tamPct, long duracionMs, int opacidad, int margen) {}

    private static final class Textura {
        final String clave;
        final ResourceLocation loc;
        final int w;
        final int h;

        Textura(String clave, ResourceLocation loc, int w, int h) {
            this.clave = clave;
            this.loc = loc;
            this.w = w;
            this.h = h;
        }
    }

    private static final class Imagen {
        final Textura tex;
        final Posicion zona;
        final int tamPct;
        final int opacidad;
        final int margen;
        final long duracionMs;
        final long inicioNs;

        Imagen(Textura tex, Datos d) {
            this.tex = tex;
            this.zona = Posicion.deIndice(d.zona());
            this.tamPct = d.tamPct();
            this.opacidad = d.opacidad();
            this.margen = d.margen();
            this.duracionMs = d.duracionMs();
            this.inicioNs = System.nanoTime();
        }
    }

    private static final class Descarga {
        final Datos datos;
        final Path tmp;
        final Path destino;
        final OutputStream out;
        long recibido = 0;

        Descarga(Datos datos, Path tmp, Path destino, OutputStream out) {
            this.datos = datos;
            this.tmp = tmp;
            this.destino = destino;
            this.out = out;
        }
    }

    private static final Map<String, Imagen> ACTIVAS = new LinkedHashMap<>();
    private static final Map<String, Textura> TEXTURAS = new HashMap<>();
    private static final Map<Integer, Descarga> DESCARGAS = new HashMap<>();
    private static final Map<String, Long> PEDIDOS = new HashMap<>();
    private static long contador = 0;

    // ------------------------------------------------------------------ recepción

    public static void alRecibirMostrar(Datos d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        cancelarDescargas(d.id());

        String ext = extension(d.id());
        if (ext == null) return;
        Path cache = FMLPaths.GAMEDIR.get().resolve("imagenes_cache");
        Path archivo = cache.resolve(d.clave() + "." + ext);
        try {
            Files.createDirectories(cache);
            if (Files.isRegularFile(archivo) && Files.size(archivo) == d.tam()) {
                mostrarDesdeArchivo(d, archivo);
                return;
            }
            Path tmp = cache.resolve(d.clave() + "-" + d.ofertaId() + ".part");
            OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp));
            DESCARGAS.put(d.ofertaId(), new Descarga(d, tmp, archivo, out));
        } catch (IOException ex) {
            LOG.warn("No se pudo preparar la descarga de {}", d.id(), ex);
            return;
        }
        ModNetwork.CANAL.sendToServer(new PedirPaquete(d.ofertaId()));
    }

    public static void alRecibirTrozo(int ofertaId, byte[] datos, boolean fin) {
        Descarga ds = DESCARGAS.get(ofertaId);
        if (ds == null) return;
        try {
            ds.out.write(datos);
            ds.recibido += datos.length;
            if (fin) {
                ds.out.close();
                DESCARGAS.remove(ofertaId);
                if (ds.recibido != ds.datos.tam()) {
                    Files.deleteIfExists(ds.tmp);
                    LOG.warn("Descarga incompleta de {}", ds.datos.id());
                    return;
                }
                Files.move(ds.tmp, ds.destino, StandardCopyOption.REPLACE_EXISTING);
                mostrarDesdeArchivo(ds.datos, ds.destino);
            }
        } catch (IOException ex) {
            LOG.warn("Error al guardar {}", ds.datos.id(), ex);
            cerrar(ds);
            DESCARGAS.remove(ofertaId);
        }
    }

    public static void ocultar(String id) {
        if (id.isEmpty()) {
            for (Descarga ds : DESCARGAS.values()) cerrar(ds);
            DESCARGAS.clear();
            PEDIDOS.clear();
            ACTIVAS.clear();
        } else {
            cancelarDescargas(id);
            PEDIDOS.remove(id);
            ACTIVAS.remove(id);
        }
        liberarNoUsadas();
    }

    public static void limpiarTodo() {
        ocultar("");
    }

    // ------------------------------------------------------------------ texturas

    private static void mostrarDesdeArchivo(Datos d, Path archivo) {
        Minecraft mc = Minecraft.getInstance();
        int alvo = anchoObjetivo(mc, d.tamPct());
        String claveTex = d.clave() + "@" + alvo;

        Textura existente = TEXTURAS.get(claveTex);
        if (existente != null) {
            activar(d, existente);
            return;
        }

        long token = ++contador;
        PEDIDOS.put(d.id(), token);
        Util.backgroundExecutor().execute(() -> {
            try {
                Decodificador.Resultado r = Decodificador.leer(archivo, alvo);
                mc.execute(() -> {
                    Long vigente = PEDIDOS.get(d.id());
                    if (vigente == null || vigente != token) return; // lo quitaron o lo reemplazaron mientras cargaba
                    PEDIDOS.remove(d.id());
                    Textura t = TEXTURAS.get(claveTex);
                    if (t == null) {
                        t = crearTextura(mc, claveTex, r);
                        TEXTURAS.put(claveTex, t);
                    }
                    activar(d, t);
                });
            } catch (Exception ex) {
                LOG.warn("No se pudo cargar la imagen {}", d.id(), ex);
            }
        });
    }

    private static Textura crearTextura(Minecraft mc, String claveTex, Decodificador.Resultado r) {
        NativeImage ni = new NativeImage(NativeImage.Format.RGBA, r.w(), r.h(), false);
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                int c = r.px()[y * r.w() + x];
                int a = c >>> 24;
                int rr = (c >> 16) & 255;
                int g = (c >> 8) & 255;
                int b = c & 255;
                ni.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | rr);
            }
        }
        DynamicTexture dt = new DynamicTexture(ni);
        dt.setFilter(true, false);
        ResourceLocation loc = mc.getTextureManager().register("imagenes_hud", dt);
        return new Textura(claveTex, loc, r.w(), r.h());
    }

    /** Ancho de textura que hace falta: ~2x lo que se verá en pantalla (buena calidad al reducir). */
    private static int anchoObjetivo(Minecraft mc, int tamPct) {
        double pixeles = mc.getWindow().getWidth() * tamPct / 100.0;
        int t = (int) (Math.ceil(pixeles * 2 / 128.0) * 128);
        return Math.max(128, Math.min(2048, t));
    }

    private static void activar(Datos d, Textura t) {
        ACTIVAS.put(d.id(), new Imagen(t, d));
        liberarNoUsadas();
    }

    private static void liberarNoUsadas() {
        Set<String> usadas = new HashSet<>();
        for (Imagen im : ACTIVAS.values()) usadas.add(im.tex.clave);
        Minecraft mc = Minecraft.getInstance();
        Iterator<Map.Entry<String, Textura>> it = TEXTURAS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Textura> e = it.next();
            if (!usadas.contains(e.getKey())) {
                mc.getTextureManager().release(e.getValue().loc);
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ dibujo

    public static void dibujar(GuiGraphics g, int sw, int sh) {
        if (ACTIVAS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return; // respeta F1

        long ahora = System.nanoTime();
        boolean expiro = false;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Iterator<Imagen> it = ACTIVAS.values().iterator();
        while (it.hasNext()) {
            Imagen im = it.next();
            long ms = (ahora - im.inicioNs) / 1_000_000L;
            float alfa = im.opacidad / 100f;
            if (im.duracionMs > 0) {
                if (ms >= im.duracionMs) {
                    it.remove();
                    expiro = true;
                    continue;
                }
                alfa *= Math.min(1f, (im.duracionMs - ms) / (float) FADE_MS);
            }
            alfa *= Math.min(1f, ms / (float) FADE_MS);

            double dw = sw * im.tamPct / 100.0;
            double dh = dw * im.tex.h / im.tex.w;
            if (dh > sh * 0.95) {
                dh = sh * 0.95;
                dw = dh * im.tex.w / im.tex.h;
            }
            int bw = Math.max(1, (int) Math.round(dw));
            int bh = Math.max(1, (int) Math.round(dh));

            int x = switch (im.zona.horizontal) {
                case 0 -> im.margen;
                case 2 -> sw - bw - im.margen;
                default -> (sw - bw) / 2;
            };
            int y = switch (im.zona.vertical) {
                case 0 -> im.margen;
                case 2 -> sh - bh - im.margen;
                default -> (sh - bh) / 2;
            };

            g.setColor(1f, 1f, 1f, alfa);
            g.blit(im.tex.loc, x, y, bw, bh, 0f, 0f, im.tex.w, im.tex.h, im.tex.w, im.tex.h);
        }

        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        if (expiro) liberarNoUsadas();
    }

    // ------------------------------------------------------------------ utilidades

    private static void cancelarDescargas(String id) {
        Iterator<Descarga> it = DESCARGAS.values().iterator();
        while (it.hasNext()) {
            Descarga ds = it.next();
            if (ds.datos.id().equals(id)) {
                cerrar(ds);
                it.remove();
            }
        }
    }

    private static void cerrar(Descarga ds) {
        try {
            ds.out.close();
        } catch (IOException ignored) {
        }
        try {
            Files.deleteIfExists(ds.tmp);
        } catch (IOException ignored) {
        }
    }

    private static String extension(String nombre) {
        int i = nombre.lastIndexOf('.');
        if (i < 0 || i == nombre.length() - 1) return null;
        String e = nombre.substring(i + 1).toLowerCase(Locale.ROOT);
        return e.matches("[a-z0-9]{1,5}") ? e : null;
    }
}
