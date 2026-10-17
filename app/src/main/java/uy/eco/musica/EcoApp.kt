package uy.eco.musica

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class EcoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(Http, Localization("es", "UY"), ContentCountry("UY"))
    }
}
