package com.medinaparra.freecadandroid.backend

import com.medinaparra.freecadandroid.model.SceneMesh

/**
 * CadBackend defines the abstraction layer for CAD kernel/operation execution.
 */
interface CadBackend {
    val capabilities: BackendCapabilities

    fun createDemoScene(): SceneMesh
}
