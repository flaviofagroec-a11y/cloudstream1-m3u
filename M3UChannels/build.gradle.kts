version = 1

cloudstream {
    description = "Canales desde una lista M3U configurada en la app"
    authors = listOf("Flavio")
    status = 3
    tvTypes = listOf("Live")
    requiresResources = false
    language = "es"
}

android {
    namespace = "com.flavio.m3u"
    buildFeatures {
        buildConfig = true
    }
}
