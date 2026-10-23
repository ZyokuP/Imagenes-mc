package com.example.imagenes.network;

import com.example.imagenes.client.ClienteImagenes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: "muestra esta imagen así". Si el cliente no la tiene en caché, la pide. */
public class MostrarPaquete {
    private final String id;
    private final int ofertaId;
    private final long tam;
    private final String clave;
    private final int zona;
    private final int tamPct;
    private final long duracionMs;
    private final int opacidad;
    private final int margen;

    public MostrarPaquete(String id, int ofertaId, long tam, String clave,
                          int zona, int tamPct, long duracionMs, int opacidad, int margen) {
        this.id = id;
        this.ofertaId = ofertaId;
        this.tam = tam;
        this.clave = clave;
        this.zona = zona;
        this.tamPct = tamPct;
        this.duracionMs = duracionMs;
        this.opacidad = opacidad;
        this.margen = margen;
    }

    public static void codificar(MostrarPaquete m, FriendlyByteBuf b) {
        b.writeUtf(m.id, 256);
        b.writeInt(m.ofertaId);
        b.writeLong(m.tam);
        b.writeUtf(m.clave, 64);
        b.writeByte(m.zona);
        b.writeVarInt(m.tamPct);
        b.writeLong(m.duracionMs);
        b.writeVarInt(m.opacidad);
        b.writeVarInt(m.margen);
    }

    public static MostrarPaquete decodificar(FriendlyByteBuf b) {
        String id = b.readUtf(256);
        int ofertaId = b.readInt();
        long tam = b.readLong();
        String clave = b.readUtf(64);
        int zona = b.readByte();
        int tamPct = b.readVarInt();
        long duracionMs = b.readLong();
        int opacidad = b.readVarInt();
        int margen = b.readVarInt();
        return new MostrarPaquete(id, ofertaId, tam, clave, zona, tamPct, duracionMs, opacidad, margen);
    }

    public static void manejar(MostrarPaquete m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClienteImagenes.alRecibirMostrar(new ClienteImagenes.Datos(m.id, m.ofertaId, m.tam, m.clave,
                        m.zona, m.tamPct, m.duracionMs, m.opacidad, m.margen))));
        ctx.get().setPacketHandled(true);
    }
}
