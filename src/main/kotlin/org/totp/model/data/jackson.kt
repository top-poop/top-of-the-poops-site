package org.totp.model.data

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.http4k.format.ConfigurableJackson
import org.http4k.format.asConfigurable
import org.http4k.format.value
import org.http4k.format.withStandardMappings


object TotpJson : ConfigurableJackson(
    KotlinModule.Builder().build()
        .asConfigurable()
        .withStandardMappings()
        .value(ConstituencyName)
        .value(CompanyName)
        .value(WaterwayName)
        .value(BathingName)
        .value(BeachName)

        .done()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .deactivateDefaultTyping()
)


