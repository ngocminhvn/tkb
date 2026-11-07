package com.sv.tnu.schools

import com.sv.tnu.schools.tnut.TnutSchoolProvider

/** Only TNUT is enabled until Room cache is isolated per school/account. */
object SchoolRegistry {
    private val providers: Map<String, SchoolProvider> =
        listOf<SchoolProvider>(TnutSchoolProvider).associateBy { it.info.id }

    fun forId(id: String): SchoolProvider =
        requireNotNull(providers[id]) { "Unsupported school provider: $id" }

    fun supportedSchools(): List<SchoolInfo> = providers.values.map { it.info }
}
