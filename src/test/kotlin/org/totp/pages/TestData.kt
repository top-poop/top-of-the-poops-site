package org.totp.pages

import org.totp.model.data.BeachRank
import org.totp.model.data.CompanyName
import org.totp.model.data.RiverRank
import org.totp.model.data.WaterwayName
import java.time.Duration


val aBeach = BeachRank(
    1,
    "beach",
    CompanyName.of("company"),
    10,
    Duration.ofHours(1),
    DeltaValue.of(10),
    Duration.ofSeconds(11)
)


fun aRiver(rank: Int) = RiverRank(
    rank,
    WaterwayName("river-$rank"),
    CompanyName("company"),
    10 * rank,
    Duration.ofHours(1L * rank),
    DeltaValue.of(10 * rank),
    Duration.ofHours(15L * rank)
)