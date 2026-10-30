package id.mhk.invoice

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate

private class ItemRow(
    val view: LinearLayout,
    val desc: EditText,
    val qty: EditText,
    val price: EditText
)

class MainActivity : Activity() {

    private lateinit var store: Store
    private var backAction: (() -> Unit)? = null

    private val DARK = Color.parseColor("#B36B00")
    private val SOFT = Color.parseColor("#FFF4E0")
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        showHome()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val b = backAction
        if (b != null) b() else super.onBackPressed()
    }

    // ------------------------------------------------------------------
    // Helper UI
    // ------------------------------------------------------------------
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun screen(title: String, back: (() -> Unit)?, build: LinearLayout.() -> Unit) {
        backAction = back
        val sv = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(20), dp(16), dp(40))
        val header = TextView(this)
        header.text = title
        header.textSize = 22f
        header.setTypeface(null, Typeface.BOLD)
        header.setTextColor(DARK)
        header.setPadding(0, 0, 0, dp(12))
        col.addView(header)
        col.build()
        sv.addView(col)
        setContentView(sv)
    }

    private fun LinearLayout.label(t: String, size: Float = 13f) {
        val tv = TextView(context)
        tv.text = t
        tv.textSize = size
        tv.setTextColor(Color.DKGRAY)
        tv.setPadding(0, dp(10), 0, dp(2))
        addView(tv, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun LinearLayout.field(
        hint: String,
        value: String = "",
        type: Int = InputType.TYPE_CLASS_TEXT
    ): EditText {
        val e = EditText(context)
        e.inputType = type
        e.setText(value)
        e.hint = hint
        addView(e, LinearLayout.LayoutParams(MATCH, WRAP))
        return e
    }

    private fun LinearLayout.btn(t: String, filled: Boolean = true, onClick: () -> Unit): Button {
        val b = Button(context)
        b.text = t
        b.isAllCaps = false
        if (filled) {
            b.setBackgroundColor(DARK)
            b.setTextColor(Color.WHITE)
        }
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(8)
        addView(b, lp)
        return b
    }

    // ------------------------------------------------------------------
    // Layar: Beranda
    // ------------------------------------------------------------------
    private fun showHome() {
        screen("Invoice Generator", null) {
            val logo = ImageView(context)
            logo.setImageResource(R.drawable.logo)
            logo.adjustViewBounds = true
            val lp = LinearLayout.LayoutParams(dp(150), WRAP)
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = dp(8)
            addView(logo, lp)
            label(store.company.name, 16f)
            label("Invoice berikutnya: ${store.nextNumber}/${store.company.code}")
            btn("Buat invoice baru") { showNew() }
            btn("Riwayat invoice") { showHistory() }
            btn("Pengaturan perusahaan") { showSettings() }
        }
    }

    // ------------------------------------------------------------------
    // Layar: Invoice baru
    // ------------------------------------------------------------------
    private fun showNew() {
        val co = store.company
        var iso = LocalDate.now().toString()
        val rows = ArrayList<ItemRow>()

        screen("Invoice baru", { showHome() }) {
            label("Nama klien")
            val client = AutoCompleteTextView(context)
            client.setAdapter(
                ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, store.clients.toList())
            )
            client.threshold = 1
            client.hint = "mis. PT Halona Digital Solutions"
            client.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            addView(client, LinearLayout.LayoutParams(MATCH, WRAP))

            label("Nomor invoice (otomatis, bisa diubah)")
            val num = field("", store.nextNumber.toString(), InputType.TYPE_CLASS_NUMBER)

            label("Tanggal (ketuk untuk mengubah)")
            val dateBtn = Button(context)
            dateBtn.isAllCaps = false
            dateBtn.text = Fmt.date(iso)
            dateBtn.setOnClickListener {
                val d = LocalDate.parse(iso)
                DatePickerDialog(
                    this@MainActivity,
                    { _, y, m, day ->
                        iso = LocalDate.of(y, m + 1, day).toString()
                        dateBtn.text = Fmt.date(iso)
                    },
                    d.year, d.monthValue - 1, d.dayOfMonth
                ).show()
            }
            addView(dateBtn, LinearLayout.LayoutParams(MATCH, WRAP))

            label("Item")
            val itemsBox = LinearLayout(context)
            itemsBox.orientation = LinearLayout.VERTICAL
            addView(itemsBox, LinearLayout.LayoutParams(MATCH, WRAP))

            fun addRow() {
                val box = LinearLayout(this@MainActivity)
                box.orientation = LinearLayout.VERTICAL
                box.setBackgroundColor(SOFT)
                box.setPadding(dp(10), dp(6), dp(10), dp(6))
                val d = box.field("Deskripsi (mis. Jasa SEO Junior)", "",
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
                val q = box.field("Qty: angka (30) atau persen (10%)", "1")
                val p = box.field("Harga satuan / nilai dasar persen (Rp)")
                val row = ItemRow(box, d, q, p)
                box.btn("Hapus item", false) {
                    itemsBox.removeView(box)
                    rows.remove(row)
                }
                rows.add(row)
                val lp = LinearLayout.LayoutParams(MATCH, WRAP)
                lp.topMargin = dp(8)
                itemsBox.addView(box, lp)
            }
            addRow()
            btn("+ Tambah item", false) { addRow() }

            label("Pajak % (0 = tanpa pajak, 11 = PPN 11%)")
            val tax = field("", "0", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            label("Catatan (opsional)")
            val note = field("")

            fun collect(): Invoice? {
                val name = client.text.toString().trim()
                if (name.isEmpty()) {
                    toast("Nama klien wajib diisi")
                    return null
                }
                val n = num.text.toString().toIntOrNull()
                if (n == null) {
                    toast("Nomor invoice tidak valid")
                    return null
                }
                val items = ArrayList<Item>()
                for ((i, r) in rows.withIndex()) {
                    val desc = r.desc.text.toString().trim()
                    if (desc.isEmpty()) continue
                    val qty = Fmt.parseQty(r.qty.text.toString())
                    val price = Fmt.parseMoney(r.price.text.toString())
                    if (qty == null || price == null) {
                        toast("Item ${i + 1}: qty atau harga tidak valid")
                        return null
                    }
                    items.add(Item(desc, qty, price))
                }
                if (items.isEmpty()) {
                    toast("Minimal 1 item")
                    return null
                }
                val taxP = tax.text.toString().toDoubleOrNull() ?: 0.0
                val number = "$n/${co.code}/${iso.substring(2, 4)}"
                val inv = Invoice(number, iso, name, items, taxP, note.text.toString().trim(), 0.0, "")
                inv.total = Fmt.calc(inv).third
                return inv
            }

            btn("Lihat total", false) {
                val inv = collect()
                if (inv != null) {
                    val (s, t, g) = Fmt.calc(inv)
                    val taxText = if (inv.taxPercent > 0) Fmt.rp(t) else "-"
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Ringkasan ${inv.number}")
                        .setMessage("Sub total: ${Fmt.rp(s)}\nPajak: $taxText\nGRAND TOTAL: ${Fmt.rp(g)}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }

            btn("Buat PDF") {
                val inv = collect()
                if (inv != null) {
                    val uri = writePdf(inv)
                    if (uri == null) {
                        toast("Gagal menyimpan PDF")
                    } else {
                        inv.uri = uri.toString()
                        if (!store.clients.contains(inv.client)) store.clients.add(inv.client)
                        store.history.add(inv)
                        store.nextNumber = inv.number.substringBefore("/").toInt() + 1
                        store.save()
                        showResult(inv)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Layar: Hasil
    // ------------------------------------------------------------------
    private fun showResult(inv: Invoice) {
        screen("Invoice tersimpan", { showHome() }) {
            label("Nomor: ${inv.number}\nKlien: ${inv.client}\nTotal: ${Fmt.rp(inv.total)}", 16f)
            label("PDF tersimpan di folder Download/Invoice")
            btn("Buka PDF") { openPdf(inv) }
            btn("Bagikan (WhatsApp, email, dll.)") { sharePdf(inv) }
            btn("Buat invoice lagi", false) { showNew() }
            btn("Kembali ke menu", false) { showHome() }
        }
    }

    // ------------------------------------------------------------------
    // Layar: Riwayat
    // ------------------------------------------------------------------
    private fun showHistory() {
        screen("Riwayat invoice", { showHome() }) {
            if (store.history.isEmpty()) label("Belum ada invoice.")
            for (inv in store.history.reversed()) {
                val row = TextView(context)
                row.text = "${inv.number}  •  ${Fmt.date(inv.date)}\n${inv.client}\n${Fmt.rp(inv.total)}"
                row.textSize = 15f
                row.setTextColor(Color.BLACK)
                row.setPadding(dp(12), dp(10), dp(12), dp(10))
                row.setBackgroundColor(SOFT)
                row.setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(inv.number)
                        .setItems(arrayOf("Buka PDF", "Bagikan", "Hapus dari riwayat")) { _, which ->
                            if (which == 0) openPdf(inv)
                            else if (which == 1) sharePdf(inv)
                            else {
                                store.history.remove(inv)
                                store.save()
                                showHistory()
                            }
                        }
                        .show()
                }
                val lp = LinearLayout.LayoutParams(MATCH, WRAP)
                lp.topMargin = dp(8)
                addView(row, lp)
            }
        }
    }

    // ------------------------------------------------------------------
    // Layar: Pengaturan
    // ------------------------------------------------------------------
    private fun showSettings() {
        val c = store.company
        screen("Pengaturan", { showHome() }) {
            label("Nama perusahaan")
            val name = field("", c.name)
            label("Kode nomor invoice (mis. MHK)")
            val code = field("", c.code)
            label("Website")
            val web = field("", c.website)
            label("Telepon")
            val phone = field("", c.phone, InputType.TYPE_CLASS_PHONE)
            label("Alamat")
            val addr = field("", c.address)
            label("Bank")
            val bank = field("", c.bank)
            label("Atas nama rekening")
            val accName = field("", c.accountName)
            label("No. rekening")
            val accNo = field("", c.accountNo)
            label("Nomor invoice berikutnya")
            val next = field("", store.nextNumber.toString(), InputType.TYPE_CLASS_NUMBER)

            btn("Simpan") {
                store.company = Company(
                    name.text.toString().trim(), code.text.toString().trim(),
                    web.text.toString().trim(), phone.text.toString().trim(),
                    addr.text.toString().trim(), bank.text.toString().trim(),
                    accName.text.toString().trim(), accNo.text.toString().trim()
                )
                store.nextNumber = next.text.toString().toIntOrNull() ?: store.nextNumber
                store.save()
                toast("Tersimpan")
                showHome()
            }
        }
    }

    // ------------------------------------------------------------------
    // PDF: simpan, buka, bagikan
    // ------------------------------------------------------------------
    private fun writePdf(inv: Invoice): Uri? {
        return try {
            val doc = PdfMaker.build(this, inv, store.company)
            val numPart = inv.number.substringBefore("/")
            val safe = inv.client.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "INV-$numPart-$safe.pdf")
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Invoice")
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                doc.close()
                null
            } else {
                contentResolver.openOutputStream(uri)?.use { out -> doc.writeTo(out) }
                doc.close()
                uri
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Pakai file lama kalau masih ada; kalau sudah dihapus, buat ulang otomatis. */
    private fun ensureUri(inv: Invoice): Uri? {
        if (inv.uri.isNotEmpty()) {
            val u = Uri.parse(inv.uri)
            try {
                contentResolver.openInputStream(u)?.close()
                return u
            } catch (e: Exception) {
                // file hilang -> buat ulang
            }
        }
        val n = writePdf(inv)
        if (n != null) {
            inv.uri = n.toString()
            store.save()
        }
        return n
    }

    private fun openPdf(inv: Invoice) {
        val u = ensureUri(inv)
        if (u == null) {
            toast("PDF tidak bisa dibuka")
            return
        }
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(u, "application/pdf")
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(i)
        } catch (e: ActivityNotFoundException) {
            toast("Tidak ada aplikasi pembaca PDF. Buka manual dari Download/Invoice.")
        }
    }

    private fun sharePdf(inv: Invoice) {
        val u = ensureUri(inv)
        if (u == null) {
            toast("PDF tidak bisa dibagikan")
            return
        }
        val i = Intent(Intent.ACTION_SEND)
        i.type = "application/pdf"
        i.putExtra(Intent.EXTRA_STREAM, u)
        i.putExtra(Intent.EXTRA_SUBJECT, "Invoice ${inv.number}")
        i.clipData = ClipData.newRawUri("", u)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(i, "Bagikan invoice"))
    }
}
