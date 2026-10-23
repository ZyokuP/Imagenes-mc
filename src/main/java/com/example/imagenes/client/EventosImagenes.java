package com.example.imagenes.client;

import com.example.imagenes.ImagenMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ImagenMod.MODID, value = Dist.CLIENT)
public class EventosImagenes {
    @SubscribeEvent
    public static void alSalir(ClientPlayerNetworkEvent.LoggingOut e) {
        ClienteImagenes.limpiarTodo();
    }
}
