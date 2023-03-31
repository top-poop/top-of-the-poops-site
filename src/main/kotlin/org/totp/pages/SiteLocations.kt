package org.totp.pages

import org.http4k.core.Uri
import org.totp.model.data.CompanyName
import org.totp.model.data.WaterwayName

object SiteLocations {

    fun waterwayUriFor(waterway: WaterwayName, company: CompanyName): Uri {

        val waterwaySlug = WaterwaySlug.from(waterway)
        val companySlug = CompanySlug.from(company)

        return Uri.of("/waterway/$companySlug/$waterwaySlug")
    }
}