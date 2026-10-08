package jp.co.ichika.salesledger

object GrossProfitUtil {
    fun materialRate(rawMaterial: String): Double? {
        val raw = rawMaterial.uppercase()
            .replace(" ", "")
            .replace("　", "")
            .replace("×", "X")
            .replace("・", "")
        if (raw.isBlank()) return null

        val wf = raw.contains("W")
        fun rate(abc: Double, wfRate: Double) = if (wf) wfRate else abc

        val medium = when {
            raw.contains("SKS200") || raw.contains("MM200") -> "SKS200"
            raw.contains("SKS180") || raw.contains("MM180") -> "SKS180"
            raw.contains("180") -> "180"
            raw.contains("160") -> "160"
            else -> ""
        }

        val outer = when {
            raw.startsWith("OPB6") || raw.startsWith("OPC6") -> "OPB6"
            raw.startsWith("OPC5") -> "OPC5"
            raw.startsWith("K7") -> "K7"
            raw.startsWith("K6") -> "K6"
            raw.startsWith("K5") -> "K5"
            raw.startsWith("C5") -> "C5"
            else -> return null
        }

        val inner = when {
            raw.contains("/OPB6") || raw.contains("/OPC6") -> "OPB6"
            raw.contains("/OPC5") -> "OPC5"
            raw.contains("/K7") -> "K7"
            raw.contains("/K6") -> "K6"
            raw.contains("/K5") -> "K5"
            raw.contains("/C5") -> "C5"
            outer == "OPB6" -> "K6"
            outer == "OPC5" -> "C5"
            else -> outer
        }

        return when ("$outer/$inner/$medium") {
            "C5/C5/" -> rate(65.0, 98.0)
            "C5/C5/160" -> rate(69.0, 102.0)
            "C5/C5/180" -> rate(72.5, 105.5)
            "C5/C5/SKS180" -> rate(77.0, 110.0)
            "C5/C5/SKS200" -> rate(80.0, 113.0)

            "OPC5/C5/" -> rate(70.0, 103.0)
            "OPC5/C5/160" -> rate(74.0, 107.0)
            "OPC5/C5/180" -> rate(77.5, 110.5)
            "OPC5/C5/SKS180" -> rate(82.0, 115.0)
            "OPC5/OPC5/" -> rate(75.0, 108.0)
            "OPC5/OPC5/160" -> rate(79.0, 112.0)
            "OPC5/OPC5/SKS180" -> rate(87.0, 120.0)

            "K5/K5/" -> rate(70.0, 103.0)
            "K5/K5/160" -> rate(74.0, 107.0)
            "K5/K5/180" -> rate(77.5, 110.5)
            "K5/K5/SKS180" -> rate(82.0, 115.0)
            "K5/K5/SKS200" -> rate(85.0, 118.0)

            "K6/K6/" -> rate(76.0, 109.0)
            "K6/K6/160" -> rate(80.0, 113.0)
            "K6/K6/180" -> rate(83.5, 116.5)
            "K6/K6/SKS180" -> rate(88.0, 121.0)
            "K6/K6/SKS200" -> rate(91.0, 124.0)

            "OPB6/K6/" -> rate(81.0, 114.0)
            "OPB6/K6/160" -> rate(85.0, 118.0)
            "OPB6/K6/180" -> rate(88.5, 121.5)
            "OPB6/K6/SKS180" -> rate(93.0, 126.0)
            "OPB6/K6/SKS200" -> rate(96.0, 129.0)
            "OPB6/OPB6/" -> rate(86.0, 119.0)
            "OPB6/OPB6/160" -> rate(90.0, 123.0)
            "OPB6/OPB6/180" -> rate(93.5, 126.5)
            "OPB6/OPB6/SKS180" -> rate(98.0, 131.0)
            "OPB6/OPB6/SKS200" -> rate(101.0, 134.0)

            "K7/K7/" -> rate(87.0, 120.0)
            "K7/K7/160" -> rate(91.0, 124.0)
            "K7/K7/180" -> rate(94.5, 127.5)
            "K7/K7/SKS180" -> rate(99.0, 132.0)
            "K7/K7/SKS200" -> rate(102.0, 135.0)
            else -> null
        }
    }

    fun normalizeName(value: String): String =
        value.uppercase()
            .replace(" ", "")
            .replace("　", "")
            .replace("（", "(")
            .replace("）", ")")
            .replace("・", "")
            .replace("-", "")
            .replace("－", "")
            .replace("_", "")
            .trim()
}
