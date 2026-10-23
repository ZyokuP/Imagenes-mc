package com.example.imagenes.network;

import com.example.imagenes.client.ClienteImagenes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: quitar una imagen (id vacío = quitar todas). */
public class OcultarPaquete {
    private final String id;

    public OcultarPaquete(String id) {
        this.id = id;
    }

    public static void codificar(OcultarPaquete m, FriendlyByteBuf b) {
        b.writeUtf(m.id, 256);
    }

    public static OcultarPaquete decodificar(FriendlyByteBuf b) {
        return new OcultarPaquete(b.readUtf(256));
    }

    public static void manejar(OcultarPaquete m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClienteImagenes.ocultar(m.id)));
        ctx.get().setPacketHandled(true);
    }
}
