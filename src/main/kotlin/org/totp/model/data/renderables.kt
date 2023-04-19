package org.totp.model.data

import org.http4k.core.Uri

data class RenderableShellfishName(val name: ShellfisheryName, val slug: ShellfishSlug, val uri: Uri)

data class RenderableBathingName(val name: BathingName, val slug: BathingSlug, val uri: Uri)

data class RenderableCompany(val name: CompanyName, val slug: CompanySlug, val uri: Uri)

fun ShellfisheryName.toRenderable(): RenderableShellfishName {
    val slug = toSlug()
    return RenderableShellfishName(this, slug, slug.let { Uri.of("/shellfishery/$it") })
}

fun CompanyName.toRenderable(): RenderableCompany {
    val slug = this.toSlug()
    return RenderableCompany(this, slug, slug.let { Uri.of("/company/$it") })
}

fun BathingName.toRenderable(): RenderableBathingName {
    val slug = toSlug()
    return RenderableBathingName(this, slug, slug.let { Uri.of("/beach/$it") })
}