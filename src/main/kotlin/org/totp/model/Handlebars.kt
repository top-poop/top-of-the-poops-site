import org.http4k.template.ViewModel


interface PageViewModel : ViewModel {
    override fun template(): String {
        return "pages/${javaClass.simpleName}"
    }
}

