package com.naovt.app.data

/** Cada cuánto se repite una alerta. */
enum class AlertRepeat {
    /** Suena una sola vez. */
    None,

    /** Todos los días a la misma hora. */
    Daily,

    /** Una vez por semana, el mismo día de la semana y a la misma hora. */
    Weekly,

    /** De lunes a viernes a la misma hora. */
    Weekdays,
}
