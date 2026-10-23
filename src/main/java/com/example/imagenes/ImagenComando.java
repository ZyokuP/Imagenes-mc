package com.example.imagenes;

import com.example.imagenes.network.ModNetwork;
import com.example.imagenes.network.OcultarPaquete;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /imagen poner <archivo> [jugadores]       -> muestra la imagen (sin jugadores = todos)
 * /imagen quitar <archivo> [jugadores]      -> la quita
 * /imagen limpiar [jugadores]               -> quita todas
 * /imagen lista | recargar
 * /imagen config <archivo> [posicion|tamano|duracion|opacidad|margen] <valor>
 * Solo operadores.
 */
@Mod.EventBusSubscriber(modid = ImagenMod.MODID)
public class ImagenComando {

    @SubscribeEvent
    public static void registrar(RegisterCommandsEvent e) {
        LiteralArgumentBuilder<CommandSourceStack> raiz = Commands.literal("imagen")
                .requires(src -> src.hasPermission(2))
                .executes(c -> ayuda(c.getSource()));

        raiz.then(Commands.literal("poner")
                .then(archivoArg()
                        .executes(c -> poner(c.getSource(), arch(c), true, List.of()))
                        .then(Commands.argument("jugadores", EntityArgument.players())
                                .executes(c -> poner(c.getSource(), arch(c), false,
                                        EntityArgument.getPlayers(c, "jugadores"))))));

        raiz.then(Commands.literal("quitar")
                .then(archivoArg()
                        .executes(c -> quitar(c.getSource(), arch(c), true, List.of()))
                        .then(Commands.argument("jugadores", EntityArgument.players())
                                .executes(c -> quitar(c.getSource(), arch(c), false,
                                        EntityArgument.getPlayers(c, "jugadores"))))));

        raiz.then(Commands.literal("limpiar")
                .executes(c -> limpiar(c.getSource(), true, List.of()))
                .then(Commands.argument("jugadores", EntityArgument.players())
                        .executes(c -> limpiar(c.getSource(), false, EntityArgument.getPlayers(c, "jugadores")))));

        raiz.then(Commands.literal("lista").executes(c -> lista(c.getSource())));
        raiz.then(Commands.literal("recargar").executes(c -> recargar(c.getSource())));

        // ---- config
        RequiredArgumentBuilder<CommandSourceStack, String> cfg = archivoArg()
                .executes(c -> verConfig(c.getSource(), arch(c)));

        LiteralArgumentBuilder<CommandSourceStack> pos = Commands.literal("posicion");
        for (Posicion z : Posicion.values()) {
            pos.then(Commands.literal(z.nombre).executes(c ->
                    ajustar(c.getSource(), arch(c), cf -> cf.zona = z, "Posición: " + z.nombre)));
        }
        cfg.then(pos);

        for (String nombre : new String[]{"tamano", "tamaño"}) {
            cfg.then(Commands.literal(nombre)
                    .then(Commands.argument("porcentaje", IntegerArgumentType.integer(1, 100))
                            .executes(c -> {
                                int v = IntegerArgumentType.getInteger(c, "porcentaje");
                                return ajustar(c.getSource(), arch(c), cf -> cf.tamPct = v,
                                        "Tamaño: " + v + "% del ancho de la pantalla");
                            })));
        }

        cfg.then(Commands.literal("duracion")
                .then(Commands.literal("permanente").executes(c ->
                        ajustar(c.getSource(), arch(c), cf -> cf.duracionSeg = 0, "Duración: permanente")))
                .then(Commands.argument("tiempo", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                new String[]{"5s", "10s", "30s", "1m", "permanente"}, b))
                        .executes(c -> duracion(c.getSource(), arch(c), StringArgumentType.getString(c, "tiempo")))));

        cfg.then(Commands.literal("opacidad")
                .then(Commands.argument("porcentaje", IntegerArgumentType.integer(0, 100))
                        .executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "porcentaje");
                            return ajustar(c.getSource(), arch(c), cf -> cf.opacidad = v, "Opacidad: " + v + "%");
                        })));

        cfg.then(Commands.literal("margen")
                .then(Commands.argument("pixeles", IntegerArgumentType.integer(0, 200))
                        .executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "pixeles");
                            return ajustar(c.getSource(), arch(c), cf -> cf.margen = v, "Margen: " + v + " px");
                        })));

        raiz.then(Commands.literal("config").then(cfg));

        e.getDispatcher().register(raiz);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> archivoArg() {
        return Commands.argument("archivo", StringArgumentType.string())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Carpeta.sugerencias(), b));
    }

    private static String arch(CommandContext<CommandSourceStack> c) {
        return StringArgumentType.getString(c, "archivo");
    }

    // ------------------------------------------------------------------ comandos

    private static int ayuda(CommandSourceStack src) {
        String t = """
                === Imágenes en pantalla ===
                /imagen poner <archivo> [jugadores]  -> la muestra (sin jugadores = todos)
                /imagen quitar <archivo> [jugadores]  -> la quita
                /imagen limpiar [jugadores]  -> quita todas
                /imagen lista  |  /imagen recargar
                /imagen config <archivo>  -> ve sus ajustes
                /imagen config <archivo> posicion <zona>
                /imagen config <archivo> tamano <1-100>  (% del ancho de pantalla)
                /imagen config <archivo> duracion <10s | 5m | permanente>
                /imagen config <archivo> opacidad <0-100>
                /imagen config <archivo> margen <píxeles>
                Pon los archivos (png, jpg, gif, bmp) en la carpeta imagenes/ del servidor.
                Ejemplo logo fijo: config logo.png duracion permanente  y luego  poner logo.png""";
        src.sendSuccess(() -> Component.literal(t), false);
        return 1;
    }

    private static Path buscar(CommandSourceStack src, String nombre) {
        Path ruta = Carpeta.resolver(nombre);
        if (ruta == null) {
            src.sendFailure(Component.literal("No encuentro '" + nombre + "' en la carpeta imagenes/. Usa /imagen lista."));
        }
        return ruta;
    }

    private static int poner(CommandSourceStack src, String nombre, boolean todos, Collection<ServerPlayer> jugadores) {
        Path ruta = buscar(src, nombre);
        if (ruta == null) return 0;
        String problema = Servicio.problema(ruta);
        if (problema != null) {
            src.sendFailure(Component.literal(problema));
            return 0;
        }
        MinecraftServer server = src.getServer();
        ImagenDatos datos = ImagenDatos.get(server);
        String id = ruta.getFileName().toString();
        Config cfg = datos.config(id);

        Set<UUID> uuids = new HashSet<>();
        if (!todos) for (ServerPlayer j : jugadores) uuids.add(j.getUUID());

        int n = Servicio.mostrar(server, datos, id, ruta, cfg, todos, uuids);
        String duracion = cfg.duracionSeg == 0 ? "fija en pantalla" : "durante " + TiempoUtil.formatear(cfg.duracionSeg);
        src.sendSuccess(() -> Component.literal("Mostrando '" + id + "' a " + n + " jugador(es), " + duracion
                + (cfg.duracionSeg == 0 && todos ? " (también a quien entre después)." : ".")), true);
        return n;
    }

    private static int quitar(CommandSourceStack src, String nombre, boolean todos, Collection<ServerPlayer> jugadores) {
        MinecraftServer server = src.getServer();
        Path ruta = Carpeta.resolver(nombre);
        String id = ruta != null ? ruta.getFileName().toString() : nombre;
        ImagenDatos datos = ImagenDatos.get(server);

        Collection<ServerPlayer> objetivo = jugadores;
        if (todos) {
            datos.permanentes.remove(id);
            objetivo = server.getPlayerList().getPlayers();
        } else {
            ImagenDatos.Permanente p = datos.permanentes.get(id);
            if (p != null && !p.todos) {
                for (ServerPlayer j : jugadores) p.jugadores.remove(j.getUUID());
                if (p.jugadores.isEmpty()) datos.permanentes.remove(id);
            }
        }
        datos.setDirty();

        OcultarPaquete paquete = new OcultarPaquete(id);
        for (ServerPlayer j : objetivo) {
            Transferencias.cancelar(j.getUUID());
            ModNetwork.CANAL.send(PacketDistributor.PLAYER.with(() -> j), paquete);
        }
        int n = objetivo.size();
        src.sendSuccess(() -> Component.literal("Imagen '" + id + "' quitada para " + n + " jugador(es)."), true);
        return n;
    }

    private static int limpiar(CommandSourceStack src, boolean todos, Collection<ServerPlayer> jugadores) {
        MinecraftServer server = src.getServer();
        ImagenDatos datos = ImagenDatos.get(server);
        Collection<ServerPlayer> objetivo = jugadores;
        if (todos) {
            datos.permanentes.clear();
            objetivo = server.getPlayerList().getPlayers();
        } else {
            List<String> vacias = new ArrayList<>();
            for (Map.Entry<String, ImagenDatos.Permanente> e : datos.permanentes.entrySet()) {
                if (e.getValue().todos) continue;
                for (ServerPlayer j : jugadores) e.getValue().jugadores.remove(j.getUUID());
                if (e.getValue().jugadores.isEmpty()) vacias.add(e.getKey());
            }
            for (String k : vacias) datos.permanentes.remove(k);
        }
        datos.setDirty();

        OcultarPaquete paquete = new OcultarPaquete("");
        for (ServerPlayer j : objetivo) {
            Transferencias.cancelar(j.getUUID());
            ModNetwork.CANAL.send(PacketDistributor.PLAYER.with(() -> j), paquete);
        }
        int n = objetivo.size();
        src.sendSuccess(() -> Component.literal("Imágenes quitadas para " + n + " jugador(es)."), true);
        return n;
    }

    private static int lista(CommandSourceStack src) {
        Carpeta.crear();
        ImagenDatos datos = ImagenDatos.get(src.getServer());
        List<String> archivos = Carpeta.archivos();
        StringBuilder sb = new StringBuilder("Carpeta: ").append(Carpeta.dir().toAbsolutePath());
        sb.append("\nImágenes (").append(archivos.size()).append("):");
        for (String f : archivos) {
            Config c = datos.configs.getOrDefault(f, new Config());
            sb.append("\n- ").append(f).append("  [")
                    .append(c.duracionSeg == 0 ? "permanente" : TiempoUtil.formatear(c.duracionSeg))
                    .append(", ").append(c.zona.nombre).append(", ").append(c.tamPct).append("%]");
            if (datos.permanentes.containsKey(f)) sb.append("  << EN PANTALLA");
        }
        String texto = sb.toString();
        src.sendSuccess(() -> Component.literal(texto), false);
        return 1;
    }

    /** Vuelve a enviar las imágenes fijas (útil si cambiaste el archivo de la carpeta). */
    private static int recargar(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        ImagenDatos datos = ImagenDatos.get(server);
        int n = 0;
        for (Map.Entry<String, ImagenDatos.Permanente> e : new ArrayList<>(datos.permanentes.entrySet())) {
            Path ruta = Carpeta.resolver(e.getKey());
            if (ruta == null) continue;
            ImagenDatos.Permanente p = e.getValue();
            Servicio.mostrar(server, datos, e.getKey(), ruta, datos.config(e.getKey()), p.todos, p.jugadores);
            n++;
        }
        int total = n;
        src.sendSuccess(() -> Component.literal("Imágenes fijas reenviadas: " + total), true);
        return n;
    }

    private static int verConfig(CommandSourceStack src, String nombre) {
        Path ruta = buscar(src, nombre);
        if (ruta == null) return 0;
        String id = ruta.getFileName().toString();
        Config c = ImagenDatos.get(src.getServer()).config(id);
        String t = "Ajustes de " + id + ":\n"
                + "posicion: " + c.zona.nombre + "\n"
                + "tamano: " + c.tamPct + "% del ancho\n"
                + "duracion: " + (c.duracionSeg == 0 ? "permanente" : TiempoUtil.formatear(c.duracionSeg)) + "\n"
                + "opacidad: " + c.opacidad + "%\n"
                + "margen: " + c.margen + " px";
        src.sendSuccess(() -> Component.literal(t), false);
        return 1;
    }

    private static int duracion(CommandSourceStack src, String nombre, String texto) {
        long seg = TiempoUtil.parsear(texto);
        if (seg < 1 || seg > 86400) {
            src.sendFailure(Component.literal("Duración no válida. Ejemplos: 10s, 5m, 1h, permanente (máx. 24h)."));
            return 0;
        }
        return ajustar(src, nombre, cf -> cf.duracionSeg = (int) seg, "Duración: " + TiempoUtil.formatear(seg));
    }

    /** Cambia un ajuste y, si la imagen está fija en pantalla, la actualiza al instante. */
    private static int ajustar(CommandSourceStack src, String nombre, Consumer<Config> cambio, String mensaje) {
        Path ruta = buscar(src, nombre);
        if (ruta == null) return 0;
        MinecraftServer server = src.getServer();
        ImagenDatos datos = ImagenDatos.get(server);
        String id = ruta.getFileName().toString();
        Config cfg = datos.config(id);
        cambio.accept(cfg);
        datos.setDirty();

        ImagenDatos.Permanente p = datos.permanentes.get(id);
        boolean enPantalla = p != null;
        if (enPantalla) Servicio.mostrar(server, datos, id, ruta, cfg, p.todos, p.jugadores);

        src.sendSuccess(() -> Component.literal(mensaje + " (" + id + ")"
                + (enPantalla ? " - actualizado en pantalla." : ". Se aplica la próxima vez que la muestres.")), true);
        return 1;
    }
}
