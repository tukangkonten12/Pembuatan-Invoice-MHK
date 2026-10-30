package id.mhk.invoice

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

data class Company(
    var name: String,
    var code: String,
    var website: String,
    var phone: String,
    var address: String,
    var bank: String,
    var accountName: String,
    var accountNo: String
)

data class Item(val desc: String, val qty: String, val price: Double)

data class Invoice(
    val number: String,
    val date: String,          // format ISO: 2026-10-30
    val client: String,
    val items: List<Item>,
    val taxPercent: Double,
    val note: String,
    var total: Double,
    var uri: String
)

object Fmt {
    val BULAN = arrayOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli",
        "Agustus", "September", "Oktober", "November", "Desember"
    )

    fun rp(n: Double): String =
        "Rp. " + String.format(Locale.US, "%,d", Math.round(n)).replace(',', '.')

    fun date(iso: String): String {
        val d = LocalDate.parse(iso)
        return "${d.dayOfMonth} ${BULAN[d.monthValue - 1]} ${d.year}"
    }

    /** "4.000.000" | "4000000" | "Rp 4jt" | "1,5jt" | "500rb" -> Double (null jika tidak valid) */
    fun parseMoney(s0: String): Double? {
        var s = s0.trim().lowercase().replace("rp.", "").replace("rp", "").replace(" ", "")
        var mult = 1.0
        when {
            s.endsWith("jt") -> { mult = 1_000_000.0; s = s.dropLast(2).replace(",", ".") }
            s.endsWith("rb") -> { mult = 1_000.0; s = s.dropLast(2).replace(",", ".") }
            s.endsWith("k") -> { mult = 1_000.0; s = s.dropLast(1).replace(",", ".") }
            else -> s = s.replace(".", "").replace(",", ".")
        }
        val v = s.toDoubleOrNull() ?: return null
        return v * mult
    }

    /** "30" -> "30" ; "10%" -> "10%" (persen dihitung dari harga/dasar) ; tidak valid -> null */
    fun parseQty(s0: String): String? {
        val s = s0.trim().replace(",", ".")
        if (s.isEmpty()) return null
        if (s.endsWith("%")) {
            return if (s.dropLast(1).toDoubleOrNull() != null) s else null
        }
        val v = s.toDoubleOrNull() ?: return null
        return if (v == Math.floor(v)) v.toLong().toString() else v.toString()
    }

    fun itemTotal(i: Item): Double =
        if (i.qty.endsWith("%")) i.price * i.qty.dropLast(1).toDouble() / 100.0
        else i.price * i.qty.toDouble()

    /** Mengembalikan (subtotal, pajak, grand total) */
    fun calc(inv: Invoice): Triple<Double, Double, Double> {
        val sub = inv.items.sumOf { itemTotal(it) }
        val tax = sub * inv.taxPercent / 100.0
        return Triple(sub, tax, sub + tax)
    }
}

/** Penyimpanan data di HP (offline), tersimpan di SharedPreferences. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("invoice_data", Context.MODE_PRIVATE)

    var company = Company(
        "PT. Memudahkan Hidup Kamu", "MHK", "mhk.co.id", "0811-2653-576",
        "Soekarno Hatta No.12, Puri Asri", "BCA", "MEMUDAHKAN HIDUP KAMU PT", "833-1455555"
    )
    var nextNumber = 378
    val clients = mutableListOf<String>()
    val history = mutableListOf<Invoice>()

    init { load() }

    private fun load() {
        val s = sp.getString("data", null) ?: return
        try {
            val o = JSONObject(s)
            o.optJSONObject("company")?.let { c ->
                company = Company(
                    c.optString("name", company.name), c.optString("code", company.code),
                    c.optString("website", company.website), c.optString("phone", company.phone),
                    c.optString("address", company.address), c.optString("bank", company.bank),
                    c.optString("accountName", company.accountName),
                    c.optString("accountNo", company.accountNo)
                )
            }
            nextNumber = o.optInt("next", nextNumber)
            o.optJSONArray("clients")?.let { a ->
                for (i in 0 until a.length()) clients.add(a.getString(i))
            }
            o.optJSONArray("history")?.let { a ->
                for (i in 0 until a.length()) {
                    val h = a.getJSONObject(i)
                    val ia = h.getJSONArray("items")
                    val items = ArrayList<Item>()
                    for (k in 0 until ia.length()) {
                        val x = ia.getJSONObject(k)
                        items.add(Item(x.getString("desc"), x.getString("qty"), x.getDouble("price")))
                    }
                    history.add(
                        Invoice(
                            h.getString("number"), h.getString("date"), h.getString("client"),
                            items, h.optDouble("tax", 0.0), h.optString("note", ""),
                            h.optDouble("total", 0.0), h.optString("uri", "")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // data rusak -> pakai pengaturan awal
        }
    }

    fun save() {
        val o = JSONObject()
        val c = JSONObject()
        c.put("name", company.name)
        c.put("code", company.code)
        c.put("website", company.website)
        c.put("phone", company.phone)
        c.put("address", company.address)
        c.put("bank", company.bank)
        c.put("accountName", company.accountName)
        c.put("accountNo", company.accountNo)
        o.put("company", c)
        o.put("next", nextNumber)
        o.put("clients", JSONArray(clients))
        val ha = JSONArray()
        for (h in history) {
            val ho = JSONObject()
            ho.put("number", h.number)
            ho.put("date", h.date)
            ho.put("client", h.client)
            ho.put("tax", h.taxPercent)
            ho.put("note", h.note)
            ho.put("total", h.total)
            ho.put("uri", h.uri)
            val ia = JSONArray()
            for (i in h.items) {
                val io = JSONObject()
                io.put("desc", i.desc)
                io.put("qty", i.qty)
                io.put("price", i.price)
                ia.put(io)
            }
            ho.put("items", ia)
            ha.put(ho)
        }
        o.put("history", ha)
        sp.edit().putString("data", o.toString()).apply()
    }
}
