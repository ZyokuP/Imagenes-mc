package com.example.imagenes;

import com.example.imagenes.network.ModNetwork;
import com.example.imagenes.network.MostrarPaquete;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Lógica del servidor: a quién se le muestra qué, y qué se guarda como permanente. */
public final class Servicio {
    public static final long MAX_BYTES = 20L * 1024 * 1024;

    private Servicio() {}

    public static List<ServerPlayer> destinatarios(MinecraftServer server, boolean todos, Set<UUID> uuids) {
        if (todos) return new ArrayList<>(server.getPlayerList().getPlayers());
        List<ServerPlayer> res = new ArrayList<>();
        for (UUID u : uuids) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) res.add(p);
        }
        return res;
    }

    /** null si el archivo es válido; si no, el motivo. */
    public static String problema(Path ruta) {
        try {
            long tam = Files.size(ruta);
            if (tam <= 0) return "El archivo está vacío.";
            if (tam > MAX_BYTES) return "La imagen pesa más de 20 MB.";
            return null;
        } catch (IOException ex) {
            return "No se pudo leer el archivo.";
        }
    }

    /**
     * Muestra la imagen con sus ajustes actuales. Si la duración es "permanente" queda guardada
     * (la verá quien entre después y sobrevive a reinicios); si no, se quita lo guardado.
     */
    public static int mostrar(MinecraftServer server, ImagenDatos datos, String id, Path ruta,
                              Config cfg, boolean todos, Set<UUID> uuids) {
        if (cfg.duracionSeg == 0) {
            datos.permanentes.put(id, new ImagenDatos.Permanente(todos, new HashSet<>(uuids)));
        } else {
            datos.permanentes.remove(id);
        }
        datos.setDirty();
        List<ServerPlayer> dest = destinatarios(server, todos, uuids);
        enviar(dest, id, ruta, cfg);
        return dest.size();
    }

    /** Ofrece la imagen a esos jugadores; ellos la descargan si no la tienen en caché. */
    public static boolean enviar(Collection<ServerPlayer> jugadores, String id, Path ruta, Config cfg) {
        if (jugadores.isEmpty()) return true;
        long tam;
        long fecha;
        try {
            tam = Files.size(ruta);
            fecha = Files.getLastModifiedTime(ruta).toMillis();
        } catch (IOException ex) {
            return false;
        }
        if (tam <= 0 || tam > MAX_BYTES) return false;

        String clave = clave(id, tam, fecha);
        Set<UUID> dest = new HashSet<>();
        for (ServerPlayer j : jugadores) dest.add(j.getUUID());

        Ofertas.Oferta oferta = Ofertas.crear(ruta, tam, clave, dest);
        MostrarPaquete paquete = new MostrarPaquete(id, oferta.id(), tam, clave,
                cfg.zona.ordinal(), cfg.tamPct, cfg.duracionSeg * 1000L, cfg.opacidad, cfg.margen);
        for (ServerPlayer j : jugadores) {
            ModNetwork.CANAL.send(PacketDistributor.PLAYER.with(() -> j), paquete);
        }
        return true;
    }

    /** Identifica la versión exacta del archivo; sirve para la caché de los clientes. */
    private static String clave(String nombre, long tam, long fecha) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] h = md.digest((nombre + "|" + tam + "|" + fecha).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException ex) {
            return Long.toHexString(tam) + Long.toHexString(fecha);
        }
    }
}
