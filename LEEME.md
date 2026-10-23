# Imágenes en Pantalla (Forge 1.20.1)

Muestra imágenes (png, jpg, gif, bmp) en la pantalla de los jugadores: un aviso temporal o el logo del
servidor fijo en una esquina. Comandos solo para operadores. No hace falta reiniciar el servidor.

## Uso básico
1. Arranca el servidor una vez: se crea la carpeta `imagenes/` en su raíz. Copia ahí tus archivos
   (puedes añadir más en cualquier momento con el servidor encendido).
2. `/imagen poner aviso.png`            -> la ven todos
3. `/imagen poner aviso.png Steve`      -> solo Steve (también @a, @p...)
4. `/imagen quitar aviso.png`  |  `/imagen limpiar`

## Logo fijo en una esquina (permanente)
```
/imagen config logo.png posicion arriba-derecha
/imagen config logo.png tamano 8
/imagen config logo.png duracion permanente
/imagen poner logo.png
```
Queda en pantalla para todos, también para quien entre después, y sobrevive a reinicios del servidor.

## Ajustes (por imagen)
- `posicion` arriba-izquierda, arriba-centro, arriba-derecha, izquierda, centro, derecha,
  abajo-izquierda, abajo-centro, abajo-derecha
- `tamano` 1-100 (% del ancho de la pantalla; el alto se ajusta solo)
- `duracion` 10s, 5m, 1h... o `permanente`
- `opacidad` 0-100
- `margen` separación del borde en píxeles
- `/imagen config logo.png` muestra los ajustes actuales
- Si cambias un ajuste de una imagen que ya está fija, se actualiza al instante.
- Si reemplazas el archivo por otro con el mismo nombre: `/imagen recargar`.

## Obtener el .jar sin instalar nada
1. Sube TODO el contenido de esta carpeta (incluida `.github`) a un repositorio de GitHub.
2. Pestaña Actions -> "Compilar mod" -> descarga **imagenes-jar** (dentro está el .jar).
3. Ponlo en `mods/` del servidor y de cada jugador.
