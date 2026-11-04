package jp.local.liveexpedition

import java.time.*

object SubscriptionBilling {
    fun active(item: Item) =
        item.kind == "subscription" && item["status"] == "契約中" && item["archived"] != "true"

    private fun anchor(s: Item) = LocalDate.parse(s["lastBill"].ifEmpty { s["date"] })

    fun day(s: Item) = s["billDay"].toIntOrNull() ?: anchor(s).dayOfMonth

    fun month(s: Item) = s["billMonth"].toIntOrNull() ?: anchor(s).monthValue

    fun next(s: Item, today: LocalDate = LocalDate.now()): LocalDate? {
        if (!active(s)) return null
        val manual = s["manualNext"].takeIf(String::isNotEmpty)?.let(LocalDate::parse)
        if (manual != null && manual >= today) return allowed(s, manual)
        val last =
            s["lastBill"].takeIf(String::isNotEmpty)?.let(LocalDate::parse)
                ?: s["date"].takeIf { s["paid"].isNotEmpty() }?.let(LocalDate::parse)
        val start = if (last != null && last >= today) last.plusDays(1) else today
        var target =
            if (s["cycle"] == "年額") CardDates.clamped(YearMonth.of(start.year, month(s)), day(s))
            else CardDates.clamped(YearMonth.from(start), day(s))
        if (target < start)
            target =
                if (s["cycle"] == "年額")
                    CardDates.clamped(YearMonth.of(start.year + 1, month(s)), day(s))
                else CardDates.clamped(YearMonth.from(start).plusMonths(1), day(s))
        return allowed(s, target)
    }

    private fun allowed(s: Item, date: LocalDate): LocalDate? =
        if (s["cancelOn"].isNotEmpty() && date > LocalDate.parse(s["cancelOn"])) null else date

    fun occurrence(s: Item, target: YearMonth, today: LocalDate = LocalDate.now()): LocalDate? {
        if (!active(s)) return null
        val manual = s["manualNext"].takeIf(String::isNotEmpty)?.let(LocalDate::parse)
        val date =
            if (manual != null && YearMonth.from(manual) == target) manual
            else {
                if (
                    manual != null &&
                        target >= YearMonth.from(today) &&
                        target < YearMonth.from(manual)
                )
                    return null
                if (s["cycle"] == "年額" && target.monthValue != month(s)) return null
                CardDates.clamped(target, day(s))
            }
        if (s["registeredOn"].isNotEmpty() && date < LocalDate.parse(s["registeredOn"])) return null
        return allowed(s, date)
    }

    fun forecast(all: List<Item>, month: YearMonth, today: LocalDate = LocalDate.now()) =
        all.filter { occurrence(it, month, today) != null }.sumOf { it.amount }

    fun normalize(s: Item, today: LocalDate = LocalDate.now()): Item {
        if (s.kind != "subscription") return s
        val base =
            s.copy(
                data =
                    s.data +
                        mapOf("billDay" to day(s).toString(), "billMonth" to month(s).toString())
            )
        return next(base, today)?.let { base.copy(data = base.data + ("date" to it.toString())) }
            ?: base
    }

    fun cancellationDue(all: List<Item>, today: LocalDate) =
        all.firstOrNull {
            active(it) &&
                it["cancelOn"].isNotEmpty() &&
                LocalDate.parse(it["cancelOn"]) < today &&
                it["cancelDeferred"] != today.toString()
        }
}
