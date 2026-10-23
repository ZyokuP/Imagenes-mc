package com.example.imagenes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = ImagenMod.MODID)
public class EventosServidor {

    @SubscribeEvent
    public static void alIniciar(ServerStartingEvent e) {
        Carpeta.crear();
    }

    @SubscribeEvent
    public static void alCerrar(ServerStoppedEvent e) {
        Transferencias.cancelarTodo();
        Ofertas.limpiar();
    }

    /** Al entrar, el jugador recibe las imágenes que están fijas en pantalla. */
    @SubscribeEvent
    public static void alEntrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer jugador)) return;
        MinecraftServer server = jugador.getServer();
        if (server == null) return;

        ImagenDatos datos = ImagenDatos.get(server);
        for (Map.Entry<String, ImagenDatos.Permanente> entrada : datos.permanentes.entrySet()) {
            ImagenDatos.Permanente p = entrada.getValue();
            if (!p.todos && !p.jugadores.contains(jugador.getUUID())) continue;
            Path ruta = Carpeta.resolver(entrada.getKey());
            if (ruta == null) continue;
            Servicio.enviar(List.of(jugador), entrada.getKey(), ruta, datos.config(entrada.getKey()));
        }
    }
}
