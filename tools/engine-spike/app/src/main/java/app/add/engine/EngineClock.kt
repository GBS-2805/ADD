package app.add.engine

/** Wall clock used by the shell when it labels the last autosave. */
object EngineClock {
    var nowMillis: () -> Long = { System.currentTimeMillis() }
}
