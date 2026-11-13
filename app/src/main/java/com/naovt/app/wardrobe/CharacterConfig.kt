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
 * Qué asset lleva puesto Nao en cada ranura y en qué orden se dibujan. Es inmutable: cada cambio
 * crea una copia nueva. Un valor `null` significa "ranura vacía" (sin ropa interior, sin gorro...).
 *
 * [order] es el orden de dibujo de TODAS las ranuras, de atrás hacia adelante (el último queda encima).
 * Una ranura vacía conserva su lugar: al equiparle algo, aparece justo donde estaba. Por defecto es el
 * orden de [WardrobeSlot.z]; el Gestor de Capas del Armario lo cambia a mano.
 *
 * Esta clase guarda solo nombres de archivo; las imágenes se cargan aparte (ver [WardrobeBitmaps]).
 */
data class CharacterConfig(
    private val selection: Map<WardrobeSlot, String?> = emptyMap(),
    val order: List<WardrobeSlot> = WardrobeSlot.drawOrder,
) {

    /** Archivo equipado en [slot], o null si está vacía. */
    fun fileFor(slot: WardrobeSlot): String? = selection[slot]

    /** Ruta del asset equipado en [slot], o null si está vacía. */
    fun assetPathFor(slot: WardrobeSlot): String? =
        selection[slot]?.let { "$WARDROBE_ASSETS_ROOT/${slot.folder}/$it" }

    /** Copia con [fileName] equipado en [slot] (null la deja vacía). */
    fun with(slot: WardrobeSlot, fileName: String?): CharacterConfig =
        CharacterConfig(selection + (slot to fileName), order)

    /** Ranuras con algo equipado, de atrás hacia adelante (el orden real de dibujo). */
    fun visibleSlots(): List<WardrobeSlot> = order.filter { selection[it] != null }

    /** Rutas de las capas equipadas, de atrás hacia adelante. */
    fun layerPaths(): List<String> = order.mapNotNull { assetPathFor(it) }

    /** Copia con otro orden completo de dibujo (se completa y limpia con [normalizeOrder]). */
    fun withOrder(newOrder: List<WardrobeSlot>): CharacterConfig =
        CharacterConfig(selection, normalizeOrder(newOrder))

    /**
     * Copia donde las ranuras equipadas quedan en el orden [visibleBottomToTop] (de atrás hacia adelante).
     * Las ranuras vacías no se mueven de su lugar. Si la lista no contiene exactamente las ranuras
     * equipadas, no cambia nada.
     */
    fun withVisibleOrder(visibleBottomToTop: List<WardrobeSlot>): CharacterConfig {
        val visible = visibleSlots().toSet()
        if (visibleBottomToTop.size != visible.size || visibleBottomToTop.toSet() != visible) return this
        val next = visibleBottomToTop.iterator()
        return CharacterConfig(selection, order.map { if (it in visible) next.next() else it })
    }

    companion object {
        /**
         * Deja [saved] como una lista válida de todas las ranuras: quita repetidas, y las que falten
         * (por ejemplo una ranura nueva en una versión futura) se insertan según su [WardrobeSlot.z].
         */
        fun normalizeOrder(saved: List<WardrobeSlot>): List<WardrobeSlot> {
            val result = saved.distinct().toMutableList()
            for (slot in WardrobeSlot.drawOrder) {
                if (slot in result) continue
                val index = result.indexOfFirst { it.z > slot.z }
                if (index < 0) result.add(slot) else result.add(index, slot)
            }
            return result
        }

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
