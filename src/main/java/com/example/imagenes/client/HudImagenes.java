package com.example.imagenes.client;

import com.example.imagenes.ImagenMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Se dibuja justo debajo del chat: el chat y los textos siempre quedan por encima de las imágenes. */
@Mod.EventBusSubscriber(modid = ImagenMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class HudImagenes {

    @SubscribeEvent
    public static void registrar(RegisterGuiOverlaysEvent e) {
        e.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(), "imagenes",
                (gui, g, parcial, ancho, alto) -> ClienteImagenes.dibujar(g, ancho, alto));
    }
}
