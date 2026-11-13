package com.naovt.app.wardrobe

/** Un asset equipable: un PNG de una carpeta de `assets/naomi/`. */
data class WardrobeItem(
    val slot: WardrobeSlot,
    /** Nombre del archivo, por ejemplo `vestido_maid.png`. Es lo que se guarda en la configuración. */
    val fileName: String,
    /** Ruta dentro de `assets/`, por ejemplo `naomi/Camisas/vestido_maid.png`. */
    val assetPath: String,
) {
    /** Nombre legible para el Armario: "vestido maid". */
    val label: String
        get() = fileName.substringBeforeLast('.').replace('_', ' ')
}

/**
 * Qué asset lleva puesto Nao en cada ranura. Es inmutable: cada cambio crea una copia nueva.
 * Un valor `null` significa "ranura vacía" (sin ropa interior, sin gorro...).
 *
 * Esta clase guarda solo nombres de archivo; las imágenes se cargan aparte (ver [WardrobeBitmaps]).
 */
data class CharacterConfig(private val selection: Map<WardrobeSlot, String?> = emptyMap()) {

    /** Archivo equipado en [slot], o null si está vacía. */
    fun fileFor(slot: WardrobeSlot): String? = selection[slot]

    /** Ruta del asset equipado en [slot], o null si está vacía. */
    fun assetPathFor(slot: WardrobeSlot): String? =
        selection[slot]?.let { "$WARDROBE_ASSETS_ROOT/${slot.folder}/$it" }

    /** Copia con [fileName] equipado en [slot] (null la deja vacía). */
    fun with(slot: WardrobeSlot, fileName: String?): CharacterConfig =
        CharacterConfig(selection + (slot to fileName))

    /** Rutas de las capas equipadas, de atrás hacia adelante. */
    fun layerPaths(): List<String> = WardrobeSlot.drawOrder.mapNotNull { assetPathFor(it) }

    companion object {
        /** Primer atuendo de Nao: vestido de maid. Orden de capas de la fase 18. */
        val Default = CharacterConfig(
            mapOf(
                WardrobeSlot.BackHair to "cabello_trasero_base.png",
                WardrobeSlot.Body to "nao_base.png",
                WardrobeSlot.Pants to "medias_y_tacones_negros.png",
                WardrobeSlot.LeftArm to "nao_brazo_izquierdo_maid.png",
                WardrobeSlot.RightArm to "nao_brazo_derecho_maid.png",
                WardrobeSlot.Shirt to "vestido_maid.png",
                WardrobeSlot.Head to "cabeza_base.png",
                WardrobeSlot.FrontHair to "cabello_frontal_base.png",
                WardrobeSlot.Mouth to "boca_base.png",
                WardrobeSlot.Eyes to "ojos_base.png",
            ),
        )
    }
}
