package com.example.imagenes.network;

import com.example.imagenes.Ofertas;
import com.example.imagenes.Transferencias;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: "no tengo esa imagen, envíamela". Solo se atiende si el jugador era destinatario. */
public class PedirPaquete {
    private final int ofertaId;

    public PedirPaquete(int ofertaId) {
        this.ofertaId = ofertaId;
    }

    public static void codificar(PedirPaquete m, FriendlyByteBuf b) {
        b.writeInt(m.ofertaId);
    }

    public static PedirPaquete decodificar(FriendlyByteBuf b) {
        return new PedirPaquete(b.readInt());
    }

    public static void manejar(PedirPaquete m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer jugador = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (jugador == null) return;
            Ofertas.Oferta oferta = Ofertas.obtener(m.ofertaId);
            if (oferta == null || !oferta.destinatarios().contains(jugador.getUUID())) return;
            Transferencias.iniciar(jugador, oferta);
        });
        ctx.get().setPacketHandled(true);
    }
}
