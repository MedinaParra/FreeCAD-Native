package com.medinaparra.freecadandroid.io

import java.io.File

data class FreeCadArchiveContent(
    val displayName: String,
    val documentName: String,
    val brepFiles: List<File>,
    val summary: String
)

object FreeCadArchiveReader
