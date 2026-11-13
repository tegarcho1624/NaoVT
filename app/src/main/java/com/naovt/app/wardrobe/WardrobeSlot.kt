package com.naovt.app.wardrobe

import androidx.annotation.StringRes
import com.naovt.app.R

/**
 * Una "ranura" del personaje: un lugar donde se equipa UN asset (ojos, boca, camisa...).
 *
 * - [folder]: carpeta dentro de `assets/naomi/` de donde salen sus imágenes (el nombre de la carpeta
 *   del ZIP de arte). Así los archivos se agrupan solos por categoría.
 * - [nameFilter]: si dos ranuras comparten carpeta (los brazos izquierdo y derecho viven juntos en
 *   `Brazos`), solo cuentan los archivos cuyo nombre contiene este texto.
 * - [z]: orden de dibujo, de atrás (1) hacia adelante (mayor). ES EL ÚNICO LUGAR donde se define.
 * - [optional]: si puede quedar vacía (el Armario muestra un recuadro "Ninguno").
 * - [backdrop]: assets que se dibujan debajo de la miniatura para que se entienda (unos ojos sueltos
 *   no se ven bien sin la cabeza).
 * - [inTabs]: si tiene pestaña en el Armario.
 *
 * El orden de las entradas de este enum es el orden de las pestañas.
 */
enum class WardrobeSlot(
    val folder: String,
    @StringRes val label: Int,
    val z: Int,
    val optional: Boolean = true,
    val nameFilter: String? = null,
    val backdrop: List<String> = emptyList(),
    val inTabs: Boolean = true,
) {
    Eyes("Ojos", R.string.wardrobe_cat_eyes, z = 14, optional = false, backdrop = listOf(HEAD)),
    Mouth("Bocas", R.string.wardrobe_cat_mouths, z = 13, optional = false, backdrop = listOf(HEAD)),
    FrontHair("Cabellos_Frontales", R.string.wardrobe_cat_front_hair, z = 11, backdrop = listOf(HEAD)),
    BackHair("Cabellos_Traseros", R.string.wardrobe_cat_back_hair, z = 1),
    Head("Cabezas", R.string.wardrobe_cat_heads, z = 10, optional = false),
    Hat("Gorros", R.string.wardrobe_cat_hats, z = 12, backdrop = listOf(HEAD)),
    Shirt("Camisas", R.string.wardrobe_cat_shirts, z = 9),
    Pants("Pantalones", R.string.wardrobe_cat_pants, z = 5),
    Panties("Bragas", R.string.wardrobe_cat_panties, z = 4),
    Bra("Sostenes", R.string.wardrobe_cat_bras, z = 6),
    Chest("Pecho", R.string.wardrobe_cat_chest, z = 3),
    LeftArm("Brazos", R.string.wardrobe_cat_left_arm, z = 7, nameFilter = "izquierdo"),
    RightArm("Brazos", R.string.wardrobe_cat_right_arm, z = 8, nameFilter = "derecho"),
    Body("Base", R.string.wardrobe_cat_body, z = 2, optional = false, inTabs = false);

    companion object {
        /** Todas las ranuras de atrás hacia adelante (el orden real de dibujo). */
        val drawOrder: List<WardrobeSlot> = entries.sortedBy { it.z }

        /** Ranuras con pestaña en el Armario, en orden de pestañas. */
        val tabs: List<WardrobeSlot> = entries.filter { it.inTabs }
    }
}

/** Carpeta raíz de las imágenes dentro de `assets/`. */
const val WARDROBE_ASSETS_ROOT = "naomi"

// La cabeza base sirve de fondo en las miniaturas de ojos, bocas, cabello frontal y gorros.
private const val HEAD = "$WARDROBE_ASSETS_ROOT/Cabezas/cabeza_base.png"
