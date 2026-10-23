package com.example.imagenes;

/** Ajustes de una imagen. Se guardan en el mundo, por nombre de archivo. */
public class Config {
    public Posicion zona = Posicion.ARRIBA_IZQUIERDA;
    /** Ancho en % del ancho de la pantalla (1-100). */
    public int tamPct = 10;
    /** Segundos en pantalla; 0 = permanente. */
    public int duracionSeg = 10;
    /** 0-100 */
    public int opacidad = 100;
    /** Separación del borde, en píxeles de la interfaz. */
    public int margen = 4;
}
