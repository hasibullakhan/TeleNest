package com.awd.teledrive.ui.screens.auth

import android.content.Context
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.TimeZone

data class CountryDialCode(
    val isoCode: String,
    val name: String,
    val dialCode: String
) {
    val flagEmoji: String
        get() {
            val clean = isoCode.uppercase(Locale.US)
            if (clean.length != 2) return "🌐"
            val first = Character.codePointAt(clean, 0) - 0x41 + 0x1F1E6
            val second = Character.codePointAt(clean, 1) - 0x41 + 0x1F1E6
            return String(Character.toChars(first)) + String(Character.toChars(second))
        }
}

object CountryCodeRepository {

    // Complete world countries list sorted strictly A to Z by country name
    val ALL_COUNTRIES: List<CountryDialCode> = listOf(
        CountryDialCode("AF", "Afghanistan", "+93"),
        CountryDialCode("AL", "Albania", "+355"),
        CountryDialCode("DZ", "Algeria", "+213"),
        CountryDialCode("AS", "American Samoa", "+1684"),
        CountryDialCode("AD", "Andorra", "+376"),
        CountryDialCode("AO", "Angola", "+244"),
        CountryDialCode("AI", "Anguilla", "+1264"),
        CountryDialCode("AG", "Antigua and Barbuda", "+1268"),
        CountryDialCode("AR", "Argentina", "+54"),
        CountryDialCode("AM", "Armenia", "+374"),
        CountryDialCode("AW", "Aruba", "+297"),
        CountryDialCode("AU", "Australia", "+61"),
        CountryDialCode("AT", "Austria", "+43"),
        CountryDialCode("AZ", "Azerbaijan", "+994"),
        CountryDialCode("BS", "Bahamas", "+1242"),
        CountryDialCode("BH", "Bahrain", "+973"),
        CountryDialCode("BD", "Bangladesh", "+880"),
        CountryDialCode("BB", "Barbados", "+1246"),
        CountryDialCode("BY", "Belarus", "+375"),
        CountryDialCode("BE", "Belgium", "+32"),
        CountryDialCode("BZ", "Belize", "+501"),
        CountryDialCode("BJ", "Benin", "+229"),
        CountryDialCode("BM", "Bermuda", "+1441"),
        CountryDialCode("BT", "Bhutan", "+975"),
        CountryDialCode("BO", "Bolivia", "+591"),
        CountryDialCode("BA", "Bosnia and Herzegovina", "+387"),
        CountryDialCode("BW", "Botswana", "+267"),
        CountryDialCode("BR", "Brazil", "+55"),
        CountryDialCode("IO", "British Indian Ocean Territory", "+246"),
        CountryDialCode("VG", "British Virgin Islands", "+1284"),
        CountryDialCode("BN", "Brunei", "+673"),
        CountryDialCode("BG", "Bulgaria", "+359"),
        CountryDialCode("BF", "Burkina Faso", "+226"),
        CountryDialCode("BI", "Burundi", "+257"),
        CountryDialCode("KH", "Cambodia", "+855"),
        CountryDialCode("CM", "Cameroon", "+237"),
        CountryDialCode("CA", "Canada", "+1"),
        CountryDialCode("CV", "Cape Verde", "+238"),
        CountryDialCode("KY", "Cayman Islands", "+1345"),
        CountryDialCode("CF", "Central African Republic", "+236"),
        CountryDialCode("TD", "Chad", "+235"),
        CountryDialCode("CL", "Chile", "+56"),
        CountryDialCode("CN", "China", "+86"),
        CountryDialCode("CO", "Colombia", "+57"),
        CountryDialCode("KM", "Comoros", "+269"),
        CountryDialCode("CG", "Congo", "+242"),
        CountryDialCode("CD", "Congo (DRC)", "+243"),
        CountryDialCode("CK", "Cook Islands", "+682"),
        CountryDialCode("CR", "Costa Rica", "+506"),
        CountryDialCode("CI", "Côte d'Ivoire", "+225"),
        CountryDialCode("HR", "Croatia", "+385"),
        CountryDialCode("CU", "Cuba", "+53"),
        CountryDialCode("CW", "Curaçao", "+599"),
        CountryDialCode("CY", "Cyprus", "+357"),
        CountryDialCode("CZ", "Czech Republic", "+420"),
        CountryDialCode("DK", "Denmark", "+45"),
        CountryDialCode("DJ", "Djibouti", "+253"),
        CountryDialCode("DM", "Dominica", "+1767"),
        CountryDialCode("DO", "Dominican Republic", "+1"),
        CountryDialCode("EC", "Ecuador", "+593"),
        CountryDialCode("EG", "Egypt", "+20"),
        CountryDialCode("SV", "El Salvador", "+503"),
        CountryDialCode("GQ", "Equatorial Guinea", "+240"),
        CountryDialCode("ER", "Eritrea", "+291"),
        CountryDialCode("EE", "Estonia", "+372"),
        CountryDialCode("SZ", "Eswatini", "+268"),
        CountryDialCode("ET", "Ethiopia", "+251"),
        CountryDialCode("FK", "Falkland Islands", "+500"),
        CountryDialCode("FO", "Faroe Islands", "+298"),
        CountryDialCode("FJ", "Fiji", "+679"),
        CountryDialCode("FI", "Finland", "+358"),
        CountryDialCode("FR", "France", "+33"),
        CountryDialCode("GF", "French Guiana", "+594"),
        CountryDialCode("PF", "French Polynesia", "+689"),
        CountryDialCode("GA", "Gabon", "+241"),
        CountryDialCode("GM", "Gambia", "+220"),
        CountryDialCode("GE", "Georgia", "+995"),
        CountryDialCode("DE", "Germany", "+49"),
        CountryDialCode("GH", "Ghana", "+233"),
        CountryDialCode("GI", "Gibraltar", "+350"),
        CountryDialCode("GR", "Greece", "+30"),
        CountryDialCode("GL", "Greenland", "+299"),
        CountryDialCode("GD", "Grenada", "+1473"),
        CountryDialCode("GP", "Guadeloupe", "+590"),
        CountryDialCode("GU", "Guam", "+1671"),
        CountryDialCode("GT", "Guatemala", "+502"),
        CountryDialCode("GN", "Guinea", "+224"),
        CountryDialCode("GW", "Guinea-Bissau", "+245"),
        CountryDialCode("GY", "Guyana", "+592"),
        CountryDialCode("HT", "Haiti", "+509"),
        CountryDialCode("HN", "Honduras", "+504"),
        CountryDialCode("HK", "Hong Kong", "+852"),
        CountryDialCode("HU", "Hungary", "+36"),
        CountryDialCode("IS", "Iceland", "+354"),
        CountryDialCode("IN", "India", "+91"),
        CountryDialCode("ID", "Indonesia", "+62"),
        CountryDialCode("IR", "Iran", "+98"),
        CountryDialCode("IQ", "Iraq", "+964"),
        CountryDialCode("IE", "Ireland", "+353"),
        CountryDialCode("IL", "Israel", "+972"),
        CountryDialCode("IT", "Italy", "+39"),
        CountryDialCode("JM", "Jamaica", "+1876"),
        CountryDialCode("JP", "Japan", "+81"),
        CountryDialCode("JO", "Jordan", "+962"),
        CountryDialCode("KZ", "Kazakhstan", "+7"),
        CountryDialCode("KE", "Kenya", "+254"),
        CountryDialCode("KI", "Kiribati", "+686"),
        CountryDialCode("KW", "Kuwait", "+965"),
        CountryDialCode("KG", "Kyrgyzstan", "+996"),
        CountryDialCode("LA", "Laos", "+856"),
        CountryDialCode("LV", "Latvia", "+371"),
        CountryDialCode("LB", "Lebanon", "+961"),
        CountryDialCode("LS", "Lesotho", "+266"),
        CountryDialCode("LR", "Liberia", "+231"),
        CountryDialCode("LY", "Libya", "+218"),
        CountryDialCode("LI", "Liechtenstein", "+423"),
        CountryDialCode("LT", "Lithuania", "+370"),
        CountryDialCode("LU", "Luxembourg", "+352"),
        CountryDialCode("MO", "Macau", "+853"),
        CountryDialCode("MG", "Madagascar", "+261"),
        CountryDialCode("MW", "Malawi", "+265"),
        CountryDialCode("MY", "Malaysia", "+60"),
        CountryDialCode("MV", "Maldives", "+960"),
        CountryDialCode("ML", "Mali", "+223"),
        CountryDialCode("MT", "Malta", "+356"),
        CountryDialCode("MH", "Marshall Islands", "+692"),
        CountryDialCode("MQ", "Martinique", "+596"),
        CountryDialCode("MR", "Mauritania", "+222"),
        CountryDialCode("MU", "Mauritius", "+230"),
        CountryDialCode("MX", "Mexico", "+52"),
        CountryDialCode("FM", "Micronesia", "+691"),
        CountryDialCode("MD", "Moldova", "+373"),
        CountryDialCode("MC", "Monaco", "+377"),
        CountryDialCode("MN", "Mongolia", "+976"),
        CountryDialCode("ME", "Montenegro", "+382"),
        CountryDialCode("MS", "Montserrat", "+1664"),
        CountryDialCode("MA", "Morocco", "+212"),
        CountryDialCode("MZ", "Mozambique", "+258"),
        CountryDialCode("MM", "Myanmar", "+95"),
        CountryDialCode("NA", "Namibia", "+264"),
        CountryDialCode("NR", "Nauru", "+674"),
        CountryDialCode("NP", "Nepal", "+977"),
        CountryDialCode("NL", "Netherlands", "+31"),
        CountryDialCode("NC", "New Caledonia", "+687"),
        CountryDialCode("NZ", "New Zealand", "+64"),
        CountryDialCode("NI", "Nicaragua", "+505"),
        CountryDialCode("NE", "Niger", "+227"),
        CountryDialCode("NG", "Nigeria", "+234"),
        CountryDialCode("NU", "Niue", "+683"),
        CountryDialCode("KP", "North Korea", "+850"),
        CountryDialCode("MK", "North Macedonia", "+389"),
        CountryDialCode("MP", "Northern Mariana Islands", "+1670"),
        CountryDialCode("NO", "Norway", "+47"),
        CountryDialCode("OM", "Oman", "+968"),
        CountryDialCode("PK", "Pakistan", "+92"),
        CountryDialCode("PW", "Palau", "+680"),
        CountryDialCode("PS", "Palestine", "+970"),
        CountryDialCode("PA", "Panama", "+507"),
        CountryDialCode("PG", "Papua New Guinea", "+675"),
        CountryDialCode("PY", "Paraguay", "+595"),
        CountryDialCode("PE", "Peru", "+51"),
        CountryDialCode("PH", "Philippines", "+63"),
        CountryDialCode("PL", "Poland", "+48"),
        CountryDialCode("PT", "Portugal", "+351"),
        CountryDialCode("PR", "Puerto Rico", "+1"),
        CountryDialCode("QA", "Qatar", "+974"),
        CountryDialCode("RE", "Réunion", "+262"),
        CountryDialCode("RO", "Romania", "+40"),
        CountryDialCode("RU", "Russia", "+7"),
        CountryDialCode("RW", "Rwanda", "+250"),
        CountryDialCode("KN", "Saint Kitts and Nevis", "+1869"),
        CountryDialCode("LC", "Saint Lucia", "+1758"),
        CountryDialCode("VC", "Saint Vincent and the Grenadines", "+1784"),
        CountryDialCode("WS", "Samoa", "+685"),
        CountryDialCode("SM", "San Marino", "+378"),
        CountryDialCode("ST", "São Tomé and Príncipe", "+239"),
        CountryDialCode("SA", "Saudi Arabia", "+966"),
        CountryDialCode("SN", "Senegal", "+221"),
        CountryDialCode("RS", "Serbia", "+381"),
        CountryDialCode("SC", "Seychelles", "+248"),
        CountryDialCode("SL", "Sierra Leone", "+232"),
        CountryDialCode("SG", "Singapore", "+65"),
        CountryDialCode("SX", "Sint Maarten", "+1721"),
        CountryDialCode("SK", "Slovakia", "+421"),
        CountryDialCode("SI", "Slovenia", "+386"),
        CountryDialCode("SB", "Solomon Islands", "+677"),
        CountryDialCode("SO", "Somalia", "+252"),
        CountryDialCode("ZA", "South Africa", "+27"),
        CountryDialCode("KR", "South Korea", "+82"),
        CountryDialCode("SS", "South Sudan", "+211"),
        CountryDialCode("ES", "Spain", "+34"),
        CountryDialCode("LK", "Sri Lanka", "+94"),
        CountryDialCode("SD", "Sudan", "+249"),
        CountryDialCode("SR", "Suriname", "+597"),
        CountryDialCode("SE", "Sweden", "+46"),
        CountryDialCode("CH", "Switzerland", "+41"),
        CountryDialCode("SY", "Syria", "+963"),
        CountryDialCode("TW", "Taiwan", "+886"),
        CountryDialCode("TJ", "Tajikistan", "+992"),
        CountryDialCode("TZ", "Tanzania", "+255"),
        CountryDialCode("TH", "Thailand", "+66"),
        CountryDialCode("TL", "Timor-Leste", "+670"),
        CountryDialCode("TG", " Togo".trim(), "+228"),
        CountryDialCode("TK", "Tokelau", "+690"),
        CountryDialCode("TO", "Tonga", "+676"),
        CountryDialCode("TT", "Trinidad and Tobago", "+1868"),
        CountryDialCode("TN", "Tunisia", "+216"),
        CountryDialCode("TR", "Turkey", "+90"),
        CountryDialCode("TM", "Turkmenistan", "+993"),
        CountryDialCode("TC", "Turks and Caicos Islands", "+1649"),
        CountryDialCode("TV", "Tuvalu", "+688"),
        CountryDialCode("UG", "Uganda", "+256"),
        CountryDialCode("UA", "Ukraine", "+380"),
        CountryDialCode("AE", "United Arab Emirates", "+971"),
        CountryDialCode("GB", "United Kingdom", "+44"),
        CountryDialCode("US", "United States", "+1"),
        CountryDialCode("UY", "Uruguay", "+598"),
        CountryDialCode("UZ", "Uzbekistan", "+998"),
        CountryDialCode("VU", "Vanuatu", "+678"),
        CountryDialCode("VA", "Vatican City", "+39"),
        CountryDialCode("VE", "Venezuela", "+58"),
        CountryDialCode("VN", "Vietnam", "+84"),
        CountryDialCode("VI", "U.S. Virgin Islands", "+1340"),
        CountryDialCode("WF", "Wallis and Futuna", "+681"),
        CountryDialCode("YE", "Yemen", "+967"),
        CountryDialCode("ZM", "Zambia", "+260"),
        CountryDialCode("ZW", "Zimbabwe", "+263")
    ).sortedBy { it.name.lowercase(Locale.US) }

    fun findByIso(isoCode: String?): CountryDialCode? {
        if (isoCode.isNullOrBlank()) return null
        val clean = isoCode.trim().uppercase(Locale.US)
        return ALL_COUNTRIES.find { it.isoCode == clean }
    }

    /**
     * Instant synchronous country fallback before IP geolocation completes.
     * Checks SIM/Network country -> TimeZone -> Locale -> Bangladesh (+880).
     */
    fun getInitialDeviceCountry(context: Context): CountryDialCode {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val networkIso = tm?.networkCountryIso?.takeIf { it.length == 2 }
            findByIso(networkIso)?.let { return it }

            val simIso = tm?.simCountryIso?.takeIf { it.length == 2 }
            findByIso(simIso)?.let { return it }
        } catch (_: Exception) {}

        // TimeZone mapping for common regions when SIM is absent or Locale is generic en_US
        try {
            val tz = TimeZone.getDefault().id
            val tzIso = when {
                tz.equals("Asia/Dhaka", ignoreCase = true) || tz.equals("Asia/Dacca", ignoreCase = true) -> "BD"
                tz.equals("Asia/Kolkata", ignoreCase = true) || tz.equals("Asia/Calcutta", ignoreCase = true) -> "IN"
                tz.equals("Asia/Karachi", ignoreCase = true) -> "PK"
                tz.startsWith("Asia/Jakarta", ignoreCase = true) || tz.startsWith("Asia/Makassar", ignoreCase = true) -> "ID"
                tz.equals("Europe/Rome", ignoreCase = true) -> "IT"
                tz.equals("Europe/London", ignoreCase = true) -> "GB"
                tz.equals("Asia/Dubai", ignoreCase = true) -> "AE"
                tz.equals("Asia/Riyadh", ignoreCase = true) -> "SA"
                tz.equals("Asia/Kuala_Lumpur", ignoreCase = true) -> "MY"
                tz.equals("Asia/Singapore", ignoreCase = true) -> "SG"
                tz.equals("Asia/Kathmandu", ignoreCase = true) || tz.equals("Asia/Katmandu", ignoreCase = true) -> "NP"
                else -> null
            }
            findByIso(tzIso)?.let { return it }
        } catch (_: Exception) {}

        val localeIso = Locale.getDefault().country
        if (!localeIso.equals("US", ignoreCase = true)) {
            findByIso(localeIso)?.let { return it }
        }

        // Default fallback to Bangladesh (+880) or US if explicitly matching
        return findByIso("BD") ?: ALL_COUNTRIES.first()
    }

    /**
     * Detects the user's real country from their public IP address.
     * Tries multiple lightweight HTTPS geolocation APIs with short timeouts.
     */
    suspend fun detectCountryFromIp(): CountryDialCode? = withContext(Dispatchers.IO) {
        val endpoints = listOf(
            "https://api.country.is/" to "country",
            "https://ipwho.is/" to "country_code",
            "https://ipapi.co/json/" to "country_code",
            "https://freeipapi.com/api/json" to "countryCode"
        )

        for ((urlStr, jsonKey) in endpoints) {
            try {
                val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "TeleNest-Android")
                }
                val code = connection.responseCode
                if (code in 200..299) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val iso = json.optString(jsonKey, "").trim()
                    val matched = findByIso(iso)
                    if (matched != null) {
                        connection.disconnect()
                        return@withContext matched
                    }
                }
                connection.disconnect()
            } catch (_: Exception) {
                // Continue to next fallback endpoint
            }
        }
        null
    }

    /**
     * Combines the selected country dial code (e.g. "+880") with the user's entered phone number,
     * smartly handling cases where the user included a leading '0' or already typed the country code.
     */
    fun formatFullPhoneNumber(selectedCountry: CountryDialCode, rawPhone: String): String {
        val trimmed = rawPhone.trim()
        if (trimmed.isEmpty()) return ""

        // Keep only digits and leading '+'
        val cleaned = buildString {
            trimmed.forEachIndexed { index, c ->
                if (c.isDigit() || (index == 0 && c == '+')) {
                    append(c)
                }
            }
        }
        if (cleaned.isEmpty() || cleaned == "+") return ""

        val normalized = if (cleaned.startsWith("00")) "+" + cleaned.removePrefix("00") else cleaned
        val dialDigits = selectedCountry.dialCode.removePrefix("+")
        val iso = selectedCountry.isoCode.uppercase(Locale.US)
        val keepLeadingZero = iso == "IT" || iso == "SM" || iso == "VA"

        // 1. If user explicitly typed a '+' prefix (or '00' international prefix):
        if (normalized.startsWith("+")) {
            val withoutPlus = normalized.removePrefix("+")
            if (!keepLeadingZero && withoutPlus.startsWith(dialDigits + "0") && withoutPlus.length >= dialDigits.length + 8) {
                return "+" + dialDigits + withoutPlus.removePrefix(dialDigits + "0")
            }
            return normalized
        }

        // 2. User did NOT type '+' — they entered the number inside the local box next to the selected country code.
        // Only treat it as an already-prefixed international number if the total length is strictly longer than
        // any valid local subscriber number (e.g. 8801780154179 has 13 digits = 3 dial digits + 10 local digits).
        // Never strip dialDigits on a 9 or 10 digit local number (such as Italian 3933815393 where '393' is the mobile prefix!).
        if (normalized.startsWith(dialDigits) && normalized.length >= dialDigits.length + 10 && normalized.length >= 12) {
            val rest = normalized.removePrefix(dialDigits).let {
                if (keepLeadingZero) it else it.trimStart('0')
            }
            if (rest.isNotEmpty()) {
                return "+$dialDigits$rest"
            }
        }

        // 3. Standard local number input (combine selectedCountry.dialCode + local subscriber number):
        // Strip leading trunk '0' (e.g. Bangladesh 01780154179 -> 1780154179 -> +8801780154179)
        val localDigits = if (keepLeadingZero) normalized else normalized.trimStart('0')
        if (localDigits.isEmpty()) return ""
        return "+$dialDigits$localDigits"
    }
}
