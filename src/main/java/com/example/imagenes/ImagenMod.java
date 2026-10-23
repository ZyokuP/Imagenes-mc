package com.example.imagenes;

import com.example.imagenes.network.ModNetwork;
import net.minecraftforge.fml.common.Mod;

@Mod(ImagenMod.MODID)
public class ImagenMod {
    public static final String MODID = "imagenes";

    public ImagenMod() {
        ModNetwork.registrar();
    }
}
