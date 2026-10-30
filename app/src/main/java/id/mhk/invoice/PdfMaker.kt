package id.mhk.invoice

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument

/** Membuat PDF invoice A4 (595 x 842 pt) memakai PdfDocument bawaan Android. */
object PdfMaker {
    private val DARK = Color.parseColor("#B36B00")
    private val LIGHT = Color.parseColor("#ECA84E")
    private val GREY = Color.parseColor("#A6A6A6")
    private val TEXT = Color.parseColor("#222222")
    private val BROWN = Color.parseColor("#3A2200")

    private fun paint(color: Int, size: Float = 10f, bold: Boolean = false, italic: Boolean = false): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.textSize = size
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        p.typeface = Typeface.create(Typeface.SANS_SERIF, style)
        return p
    }

    private fun stroke(color: Int, w: Float): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.strokeWidth = w
        p.style = Paint.Style.STROKE
        return p
    }

    private fun fill(color: Int): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.style = Paint.Style.FILL
        return p
    }

    private fun Canvas.text(s: String, x: Float, y: Float, p: Paint, align: Paint.Align = Paint.Align.LEFT) {
        p.textAlign = align
        drawText(s, x, y, p)
    }

    private fun wrap(text: String, p: Paint, maxW: Float): List<String> {
        val lines = ArrayList<String>()
        var cur = ""
        for (w in text.split(" ")) {
            val t = if (cur.isEmpty()) w else "$cur $w"
            if (cur.isEmpty() || p.measureText(t) <= maxW) {
                cur = t
            } else {
                lines.add(cur)
                cur = w
            }
        }
        if (cur.isNotEmpty()) lines.add(cur)
        return if (lines.isEmpty()) listOf("") else lines
    }

    fun build(ctx: Context, inv: Invoice, co: Company): PdfDocument {
        val doc = PdfDocument()
        val LM = 58f
        val RM = 595f - 58f
        val cols = floatArrayOf(LM, LM + 30f, LM + 200f, LM + 280f, LM + 380f, RM)

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        var c = page.canvas

        fun footer(cv: Canvas) {
            cv.drawLine(LM, 752f, RM, 752f, stroke(GREY, 1.2f))
            val dk = stroke(DARK, 3f)
            cv.drawLine(LM, 752f, LM + 60f, 752f, dk)
            cv.drawLine(RM - 60f, 752f, RM, 752f, dk)
            val ic = stroke(LIGHT, 1.3f)
            val tp = paint(TEXT, 9.5f)
            cv.drawCircle(140f, 775f, 6.5f, ic)
            cv.drawCircle(140f, 775f, 2f, ic)
            cv.text(co.phone, 154f, 778f, tp)
            cv.drawCircle(320f, 775f, 6.5f, ic)
            cv.drawCircle(320f, 775f, 2f, ic)
            cv.text(co.address, 334f, 778f, tp)
        }

        fun newPage() {
            footer(c)
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
            c = page.canvas
        }

        // ---- Kepala: logo + nama + INVOICE ----
        var nameX = 104f
        val logo = BitmapFactory.decodeResource(ctx.resources, R.drawable.logo)
        if (logo != null) {
            val lh = 38f
            val lw = lh * logo.width / logo.height
            val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            c.drawBitmap(logo, null, RectF(LM, 62f, LM + lw, 100f), bp)
            nameX = LM + lw + 10f
        }
        c.text(co.name, nameX, 92f, paint(Color.BLACK, 15f, bold = true))
        c.text("INVOICE", RM, 84f, paint(LIGHT, 32f, bold = true), Paint.Align.RIGHT)
        c.text(co.website, RM, 104f, paint(TEXT, 9f), Paint.Align.RIGHT)
        c.drawLine(LM, 113f, RM, 113f, stroke(GREY, 1.2f))
        c.drawLine(LM, 113f, LM + 60f, 113f, stroke(DARK, 3f))

        // ---- Penerima & nomor ----
        c.text("Invoice to :", LM, 172f, paint(TEXT, 10f))
        c.text(inv.client.uppercase(), LM, 192f, paint(TEXT, 11.5f, bold = true))
        c.text("Invoice: ${inv.number}", RM, 172f, paint(TEXT, 11.5f, bold = true), Paint.Align.RIGHT)
        c.text(Fmt.date(inv.date), RM, 192f, paint(TEXT, 11f), Paint.Align.RIGHT)

        // ---- Tabel ----
        val top = 290f
        c.drawRect(LM, top, RM, top + 22f, fill(DARK))
        val heads = listOf("NO", "DESCRIPTION", "QTY", "PRICE", "TOTAL")
        val hp = paint(Color.WHITE, 10f, bold = true)
        for (i in heads.indices) {
            c.text(heads[i], (cols[i] + cols[i + 1]) / 2f, top + 15f, hp, Paint.Align.CENTER)
        }

        var y = top + 22f
        val rows = inv.items
        val nRows = maxOf(8, rows.size)
        for (idx in 0 until nRows) {
            val item = if (idx < rows.size) rows[idx] else null
            val filled = idx % 2 == 1
            val bodyP = paint(if (filled) BROWN else TEXT, 9.5f)
            val lines = if (item == null) listOf("") else wrap(item.desc, bodyP, cols[2] - cols[1] - 12f)
            val h = maxOf(19f, 12f * lines.size + 7f)

            if (y + h > 690f) {
                newPage()
                y = 50f
            }
            if (filled) {
                c.drawRect(LM, y, RM, y + h, fill(LIGHT))
            } else {
                val gl = stroke(GREY, 1.4f)
                for (k in 1..4) c.drawLine(cols[k], y, cols[k], y + h, gl)
            }
            if (item != null) {
                c.text((idx + 1).toString(), (cols[0] + cols[1]) / 2f, y + 13f, bodyP, Paint.Align.CENTER)
                for (k in lines.indices) {
                    c.text(lines[k], cols[1] + 6f, y + 13f + 12f * k, bodyP, Paint.Align.LEFT)
                }
                c.text(item.qty, (cols[2] + cols[3]) / 2f, y + 13f, bodyP, Paint.Align.CENTER)
                c.text(Fmt.rp(item.price), (cols[3] + cols[4]) / 2f, y + 13f, bodyP, Paint.Align.CENTER)
                c.text(Fmt.rp(Fmt.itemTotal(item)), (cols[4] + cols[5]) / 2f, y + 13f, bodyP, Paint.Align.CENTER)
            }
            y += h
        }

        // ---- Total ----
        val (sub, taxAmt, grand) = Fmt.calc(inv)
        if (y > 600f) {
            newPage()
            y = 50f
        }
        y += 22f
        val tp = inv.taxPercent
        val tpText = if (tp == Math.floor(tp)) tp.toLong().toString() else tp.toString()
        val taxLabel = if (tp > 0) "Tax ($tpText%) :" else "Tax :"
        val np = paint(TEXT, 11f)
        c.text("Sub Total :", cols[3] + 68f, y, np, Paint.Align.RIGHT)
        c.text(Fmt.rp(sub), RM - 8f, y, np, Paint.Align.RIGHT)
        c.text(taxLabel, cols[3] + 68f, y + 24f, np, Paint.Align.RIGHT)
        c.text(if (tp > 0) Fmt.rp(taxAmt) else "-", RM - 8f, y + 24f, np, Paint.Align.RIGHT)

        val by = y + 46f
        c.drawRect(LM, by, LM + 135f, by + 20f, fill(DARK))
        c.drawRect(cols[3] - 30f, by, RM, by + 20f, fill(DARK))
        val wp = paint(Color.WHITE, 10f, bold = true)
        c.text("PAYMENT METHOD :", LM + 4f, by + 14f, wp, Paint.Align.LEFT)
        c.text("GRAND TOTAL :", cols[3] - 22f, by + 14f, wp, Paint.Align.LEFT)
        c.text("RP. " + Fmt.rp(grand).substring(4), RM - 8f, by + 14f, wp, Paint.Align.RIGHT)

        val bp2 = paint(TEXT, 10f)
        val bank = listOf("Bank" to co.bank, "Account" to co.accountName, "No. Rekening" to co.accountNo)
        for (k in bank.indices) {
            val yy = by + 44f + 22f * k
            c.text(bank[k].first, LM + 2f, yy, bp2, Paint.Align.LEFT)
            c.text(": " + bank[k].second, LM + 85f, yy, bp2, Paint.Align.LEFT)
        }

        if (inv.note.isNotEmpty()) {
            val np2 = paint(TEXT, 9f, italic = true)
            val nl = wrap("Catatan: " + inv.note, np2, RM - LM)
            for (k in nl.indices) {
                c.text(nl[k], LM + 2f, by + 118f + 12f * k, np2, Paint.Align.LEFT)
            }
        }

        footer(c)
        doc.finishPage(page)
        return doc
    }
}
