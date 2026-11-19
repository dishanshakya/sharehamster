package dev.sharehub.nepse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

private val Ink = Color(0xFF111315)
private val Panel = Color(0xFF1B1D20)
private val Panel2 = Color(0xFF292C30)
private val Mint = Color(0xFF58B66F)
private val Muted = Color(0xFFA4A8AD)
private val Red = Color(0xFFE45D64)
private val White = Color(0xFFF3F4F5)
private val Blue = Color(0xFF2996E3)
private val Teal = Color(0xFF073436)

data class Stock(
    val symbol: String,
    val name: String = "",
    val price: Double = 0.0,
    val change: Double = 0.0,
    val pointChange: Double = 0.0,
    val tradedVolume: Double = 0.0,
    val turnover: Double = 0.0,
    val transactionCount: Int = 0,
)
data class Holding(val stock: Stock, val quantity: Int, val average: Double)
data class Portfolio(val id: String, val name: String, val holdings: List<Holding> = emptyList())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 52, 54)
        window.navigationBarColor = android.graphics.Color.rgb(17, 19, 21)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0
        setContent { ShareHubApp(getSharedPreferences("portfolio", MODE_PRIVATE)) }
    }
}

@Composable
fun ShareHubApp(preferences: android.content.SharedPreferences) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var stocks by remember { mutableStateOf(emptyList<Stock>()) }
    var indexHistory by remember { mutableStateOf(emptyList<Float>()) }
    var indexValue by remember { mutableStateOf("—") }
    var indexChange by remember { mutableStateOf("") }
    var indexPercent by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("Market data not loaded") }
    var loading by remember { mutableStateOf(false) }
    var portfolios by remember { mutableStateOf(loadPortfolios(preferences)) }
    var selectedPortfolioId by remember { mutableStateOf<String?>(null) }
    var addPortfolioDialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Stock?>(null) }
    var addStockPortfolioId by remember { mutableStateOf<String?>(null) }
    var tradeHolding by remember { mutableStateOf<Holding?>(null) }
    var tradePortfolioId by remember { mutableStateOf<String?>(null) }
    var tradeMode by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        try {
            val market = NepseApi.fetch()
            stocks = market.stocks
            val latestBySymbol = market.stocks.associateBy { it.symbol }
            portfolios = portfolios.map { portfolio ->
                portfolio.copy(holdings = portfolio.holdings.map { holding -> holding.copy(stock = latestBySymbol[holding.stock.symbol] ?: holding.stock) })
            }
            savePortfolios(preferences, portfolios)
            indexHistory = market.history
            indexValue = market.index
            indexChange = market.change
            indexPercent = market.percentChange
            summary = "Updated ${java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date())}"
        } catch (_: Exception) {
            summary = "Couldn’t connect · Showing saved portfolio"
        } finally { loading = false }
    }
    LaunchedEffect(Unit) { refresh() }
    BackHandler(enabled = selected != null || addStockPortfolioId != null || tab != 0 || selectedPortfolioId != null) {
        if (selected != null) selected = null
        else if (addStockPortfolioId != null) addStockPortfolioId = null
        else if (tab == 2 && selectedPortfolioId != null) selectedPortfolioId = null else tab = 0
    }

        MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Panel, primary = Mint, onBackground = White, onSurface = White)) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(Teal).padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("sharehamster", textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = White, letterSpacing = (-.5).sp)
                }
                when (tab) {
                    0 -> HomeScreen(indexValue, indexChange, indexPercent, summary, stocks, indexHistory, loading, ::refresh, { selected = it }, { tab = 1 })
                    1 -> ExploreScreen(search, { search = it }, stocks, loading, { selected = it }, ::refresh)
                    else -> if (addStockPortfolioId != null) {
                        val targetPortfolio = portfolios.firstOrNull { it.id == addStockPortfolioId }
                        if (targetPortfolio == null) {
                            addStockPortfolioId = null
                        } else {
                            AddStockScreen(
                                portfolio = targetPortfolio,
                                stocks = stocks,
                                loading = loading,
                                onBack = { addStockPortfolioId = null },
                                onRefresh = ::refresh,
                                onAddStock = { stock, quantity, price ->
                                    portfolios = portfolios.map { portfolio ->
                                        if (portfolio.id != targetPortfolio.id) portfolio else {
                                            val current = portfolio.holdings.firstOrNull { it.stock.symbol == stock.symbol }
                                            if (current == null) portfolio.copy(holdings = portfolio.holdings + Holding(stock, quantity, price))
                                            else {
                                                val total = current.quantity + quantity
                                                val average = (current.quantity * current.average + quantity * price) / total
                                                portfolio.copy(holdings = portfolio.holdings.map { holding ->
                                                    if (holding.stock.symbol == stock.symbol) holding.copy(stock = stock, quantity = total, average = average) else holding
                                                })
                                            }
                                        }
                                    }
                                    savePortfolios(preferences, portfolios)
                                    addStockPortfolioId = null
                                },
                            )
                        }
                    } else PortfolioScreen(
                        portfolios = portfolios,
                        selectedPortfolioId = selectedPortfolioId,
                        onSelectPortfolio = { selectedPortfolioId = it },
                        onBack = { selectedPortfolioId = null },
                        onAddPortfolio = { addPortfolioDialog = true },
                        onAddStock = { addStockPortfolioId = selectedPortfolioId },
                        onStock = { selected = it },
                        onTrade = { portfolioId, holding -> tradePortfolioId = portfolioId; tradeHolding = holding; tradeMode = "choose" },
                    )
                }
            }
            NavigationBar(Modifier.align(Alignment.BottomCenter), containerColor = Panel) {
                listOf("Home", "Explore", "Portfolio").forEachIndexed { i, label ->
                    val icon = when (i) { 0 -> Icons.Default.SpaceDashboard; 1 -> Icons.Default.Search; else -> Icons.Default.AccountBalanceWallet }
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Mint, selectedTextColor = Mint, unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = Panel2))
                }
            }
            selected?.let { stock ->
                StockDetailsScreen(stock, onBack = { selected = null })
            }

            if (addPortfolioDialog) {
                var name by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { addPortfolioDialog = false },
                    containerColor = Panel,
                    title = { Text("Create portfolio", color = White) },
                    text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Portfolio name") }, singleLine = true) },
                    confirmButton = { TextButton(onClick = {
                        val portfolioName = name.trim()
                        if (portfolioName.isNotEmpty()) {
                            val portfolio = Portfolio(System.currentTimeMillis().toString(), portfolioName)
                            portfolios = portfolios + portfolio
                            selectedPortfolioId = portfolio.id
                            savePortfolios(preferences, portfolios)
                            addPortfolioDialog = false
                        }
                    }) { Text("Create", color = Mint) } },
                    dismissButton = { TextButton(onClick = { addPortfolioDialog = false }) { Text("Cancel", color = Muted) } },
                )
            }
            tradeHolding?.let { holding ->
                val closeTrade = { tradeHolding = null; tradePortfolioId = null; tradeMode = null }
                if (tradeMode == "choose") {
                    AlertDialog(
                        onDismissRequest = closeTrade,
                        containerColor = Panel,
                        title = { Text(holding.stock.symbol, color = White) },
                        text = { Text("Choose an action for this holding", color = Muted) },
                        confirmButton = {
                            Column(horizontalAlignment = Alignment.End) {
                                TextButton(onClick = { tradeMode = "buy" }) { Text("Add shares", color = Mint) }
                                TextButton(onClick = { tradeMode = "sell" }) { Text("Sell shares", color = Red) }
                                TextButton(onClick = { tradeMode = "delete" }) { Text("Delete from portfolio", color = Red) }
                            }
                        },
                        dismissButton = { TextButton(onClick = closeTrade) { Text("Cancel", color = Muted) } },
                    )
                } else if (tradeMode == "delete") {
                    AlertDialog(
                        onDismissRequest = closeTrade,
                        containerColor = Panel,
                        title = { Text("Delete ${holding.stock.symbol}?", color = White) },
                        text = { Text("Remove all ${holding.quantity} shares of ${holding.stock.symbol} from this portfolio? This does not record a sale.", color = Muted) },
                        confirmButton = {
                            TextButton(onClick = {
                                val portfolioId = tradePortfolioId
                                if (portfolioId != null) {
                                    portfolios = portfolios.map { portfolio ->
                                        if (portfolio.id == portfolioId) portfolio.copy(holdings = portfolio.holdings.filterNot { it.stock.symbol == holding.stock.symbol }) else portfolio
                                    }
                                    savePortfolios(preferences, portfolios)
                                }
                                closeTrade()
                            }) { Text("Delete", color = Red) }
                        },
                        dismissButton = { TextButton(onClick = closeTrade) { Text("Cancel", color = Muted) } },
                    )
                } else {
                    val isSell = tradeMode == "sell"
                    var quantity by remember(holding.stock.symbol, tradeMode) { mutableStateOf("") }
                    var price by remember(holding.stock.symbol, tradeMode) { mutableStateOf(holding.stock.price.takeIf { it > 0 }?.toString() ?: "") }
                    AlertDialog(
                        onDismissRequest = closeTrade,
                        containerColor = Panel,
                        title = { Text(if (isSell) "Sell ${holding.stock.symbol}" else "Add ${holding.stock.symbol}", color = White) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("${holding.quantity} shares held · average cost Rs ${money(holding.average)}", color = Muted, fontSize = 12.sp)
                                OutlinedTextField(quantity, { quantity = it }, label = { Text(if (isSell) "Shares to sell" else "Additional shares") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                                OutlinedTextField(price, { price = it }, label = { Text(if (isSell) "Sell price (NPR/share)" else "Buy price (NPR/share)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                                if (isSell) Text("You can sell up to ${holding.quantity} shares.", color = Muted, fontSize = 11.sp)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val q = quantity.toIntOrNull() ?: 0
                                val p = price.toDoubleOrNull() ?: 0.0
                                val portfolioId = tradePortfolioId
                                if (q > 0 && p > 0 && portfolioId != null) {
                                    var validTrade = true
                                    portfolios = portfolios.map { portfolio ->
                                        if (portfolio.id != portfolioId) portfolio else {
                                            val current = portfolio.holdings.firstOrNull { it.stock.symbol == holding.stock.symbol }
                                            if (current == null || (isSell && q > current.quantity)) {
                                                validTrade = false
                                                portfolio
                                            } else if (isSell) {
                                                val remaining = current.quantity - q
                                                portfolio.copy(holdings = if (remaining == 0) portfolio.holdings.filterNot { it.stock.symbol == current.stock.symbol } else portfolio.holdings.map { if (it.stock.symbol == current.stock.symbol) it.copy(quantity = remaining) else it })
                                            } else {
                                                val total = current.quantity + q
                                                val average = (current.quantity * current.average + q * p) / total
                                                portfolio.copy(holdings = portfolio.holdings.map { if (it.stock.symbol == current.stock.symbol) it.copy(quantity = total, average = average) else it })
                                            }
                                        }
                                    }
                                    if (validTrade) {
                                        savePortfolios(preferences, portfolios)
                                        closeTrade()
                                    }
                                }
                            }) { Text(if (isSell) "Sell shares" else "Add shares", color = if (isSell) Red else Mint) }
                        },
                        dismissButton = { TextButton(onClick = closeTrade) { Text("Cancel", color = Muted) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun StockDetailsScreen(stock: Stock, onBack: () -> Unit) {
    var detail by remember(stock.symbol) { mutableStateOf<JSONObject?>(null) }
    var fundamentals by remember(stock.symbol) { mutableStateOf<JSONObject?>(null) }
    var history by remember(stock.symbol) { mutableStateOf(emptyList<Float>()) }
    var error by remember(stock.symbol) { mutableStateOf(false) }
    var loading by remember(stock.symbol) { mutableStateOf(true) }
    LaunchedEffect(stock.symbol) {
        try {
            val result = NepseApi.fetchStockDetails(stock.symbol)
            detail = result.optJSONObject("details")
            fundamentals = result.optJSONObject("fundamentals")
            val rows = result.optJSONArray("history") ?: JSONArray()
            history = (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.let { it.optDouble("closePrice", Double.NaN).toFloat().takeIf(Float::isFinite) } }
            error = detail == null && history.isEmpty()
        } catch (_: Exception) { error = true }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.fillMaxWidth().background(Teal).padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = White) }
            Text(stock.symbol, Modifier.weight(1f), color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.width(48.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(18.dp)) {
                    Text(stock.name.ifBlank { stock.symbol }, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(stock.symbol, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Rs ${money(stock.price)}", color = if (stock.change >= 0) Mint else Red, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text("${if (stock.pointChange >= 0) "+" else ""}${money(stock.pointChange)} (${String.format(Locale.US, "%.2f", stock.change)}%)", color = if (stock.change >= 0) Mint else Red)
                    Spacer(Modifier.height(12.dp))
                    DetailRow("Open", detail?.obj("securityDailyTradeDto")?.number("openPrice")?.let(::money) ?: "—", loading)
                    DetailRow("Previous close", detail?.obj("securityDailyTradeDto")?.number("previousClose")?.let(::money) ?: "—", loading)
                    DetailRow("High / Low", detail?.obj("securityDailyTradeDto")?.let { "${it.number("highPrice")?.let(::money) ?: "—"} / ${it.number("lowPrice")?.let(::money) ?: "—"}" } ?: "—", loading)
                    DetailRow("Turnover", turnoverLabel(stock.turnover))
                    DetailRow("Traded quantity", NumberFormat.getNumberInstance(Locale.US).format(stock.tradedVolume))
                    DetailRow("Trades", stock.transactionCount.takeIf { it > 0 }?.toString() ?: "—")
                    if (error) Text("Some company details could not be loaded.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)) {
                    Text("Fundamentals & valuation", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    val ttmEps = fundamentals?.number("ttm_eps")
                    val reportedEps = fundamentals?.number("reported_eps")
                    val pe = ttmEps?.takeIf { it > 0 }?.let { stock.price / it }
                    DetailRow("EPS (TTM)", ttmEps?.let { "Rs ${money(it)}" } ?: "—", loading)
                    DetailRow("Latest reported EPS", reportedEps?.let { "Rs ${money(it)}" } ?: "—", loading)
                    DetailRow("P/E ratio", pe?.let { String.format(Locale.US, "%.2f", it) } ?: "—", loading)
                    DetailRow("Book value per share", "—", loading)
                    DetailRow("P/B ratio", "—", loading)
                    DetailRow("EPS reported on", fundamentals?.optString("last_date")?.takeIf { !it.isNullOrBlank() && it != "null" } ?: "—", loading)
                    Text("EPS source: NepseAlpha. Book value data is not provided by the current source.", color = Muted, fontSize = 11.sp)
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)) {
                    Text("Price history", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    if (history.size > 1) IndexAreaChart(history, if (history.last() >= history.first()) Mint else Red)
                    else Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text(if (loading) "Loading price history…" else "Historical prices unavailable", color = Muted, fontSize = 13.sp)
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)) {
                    Text("Performance values", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (loading && detail == null) Text("Loading company details…", color = Muted, fontSize = 12.sp)
                    if (error) Text("Company details are temporarily unavailable. Market quote is still shown above.", color = Muted, fontSize = 12.sp)
                    DetailRow("52-week high", detail?.obj("securityDailyTradeDto")?.number("fiftyTwoWeekHigh")?.let { "Rs ${money(it)}" } ?: "—", loading)
                    DetailRow("52-week low", detail?.obj("securityDailyTradeDto")?.number("fiftyTwoWeekLow")?.let { "Rs ${money(it)}" } ?: "—", loading)
                    DetailRow("Face value", detail?.obj("security")?.number("faceValue")?.let { "Rs ${money(it)}" } ?: "—", loading)
                    DetailRow("Market capitalization", detail?.number("marketCapitalization")?.let { "Rs ${turnoverLabel(it)}" } ?: "—", loading)
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)) {
                    Text("General information", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    DetailRow("Sector", detail?.obj("security")?.obj("companyId")?.obj("sectorMaster")?.optString("sectorDescription")?.takeIf { !it.isNullOrBlank() } ?: "—", loading)
                    DetailRow("Listed shares", detail?.number("stockListedShares")?.let { NumberFormat.getNumberInstance(Locale.US).format(it) } ?: "—", loading)
                    DetailRow("Paid-up capital", detail?.number("paidUpCapital")?.let { "Rs ${turnoverLabel(it)}" } ?: "—", loading)
                    DetailRow("Promoter shares", detail?.number("promoterShares")?.let { NumberFormat.getNumberInstance(Locale.US).format(it) } ?: "—", loading)
                    DetailRow("Promoter ownership", detail?.number("promoterPercentage")?.let { "${money(it)}%" } ?: "—", loading)
                    DetailRow("Public shares", detail?.number("publicShares")?.let { NumberFormat.getNumberInstance(Locale.US).format(it) } ?: "—", loading)
                    DetailRow("Public ownership", detail?.number("publicPercentage")?.let { "${money(it)}%" } ?: "—", loading)
                    DetailRow("Listed on", detail?.obj("security")?.optString("listingDate")?.takeIf { !it.isNullOrBlank() && it != "null" } ?: "—", loading)
                    DetailRow("Status", detail?.obj("security")?.optString("activeStatus")?.takeIf { !it.isNullOrBlank() && it != "null" } ?: "—", loading)
                }
            }
        }
    }
}

private fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)
private fun JSONObject.number(key: String): Double? = if (isNull(key)) null else optDouble(key).takeIf(Double::isFinite)

@Composable
private fun DetailRow(label: String, value: String, loading: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Muted, fontSize = 13.sp)
        if (loading && value == "—") {
            CircularProgressIndicator(Modifier.size(14.dp), color = Muted, strokeWidth = 2.dp)
        } else {
            Text(value, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
    HorizontalDivider(color = Panel2)
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
private fun HomeScreen(index: String, change: String, percent: String, status: String, stocks: List<Stock>, history: List<Float>, loading: Boolean, refresh: suspend () -> Unit, onStock: (Stock) -> Unit, onExplore: () -> Unit) {
    val scope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val pullRefreshState = rememberPullRefreshState(refreshing = loading, onRefresh = { scope.launch { refresh() } })
    val gainers = stocks.filter { it.price > 0 && it.change > 0 }.sortedByDescending { it.change }.take(6)
    val losers = stocks.filter { it.price > 0 && it.change < 0 }.sortedBy { it.change }.take(6)
    var showLosers by remember { mutableStateOf(false) }
    var activityTab by remember { mutableIntStateOf(0) }
    val leaders = if (showLosers) losers else gainers
    val indexUp = change.trim().let { !it.startsWith("-") }
    val chartColor = if (indexUp) Mint else Red

    Box(Modifier.fillMaxSize().background(Ink).pullRefresh(pullRefreshState)) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Panel).padding(horizontal = 14.dp, vertical = 13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("NEPSE Index", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("$index  ${change.ifBlank { "—" }} (${percent.ifBlank { "—" }})", color = chartColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("INTRADAY", color = Muted, fontSize = 10.sp, letterSpacing = .8.sp)
                }
                Spacer(Modifier.height(9.dp))
                IndexAreaChart(history, chartColor)
                Row(Modifier.fillMaxWidth().padding(start = 34.dp, top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("10:30", "12:00", "1:30", "3:00").forEach { Text(it, color = Muted, fontSize = 9.sp) }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Panel).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth().clip(CircleShape).background(Color(0xFF414143)).padding(4.dp), horizontalArrangement = Arrangement.Center) {
                    listOf("Top Gainers", "Top Losers").forEachIndexed { i, title ->
                        Box(Modifier.weight(1f).clip(CircleShape).background(if (showLosers == (i == 1)) Color(0xFF145B24) else Color.Transparent).clickable { showLosers = i == 1 }.padding(vertical = 8.dp), Alignment.Center) { Text(title, color = White, fontSize = 14.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text("SYMBOL", Modifier.weight(1.4f), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("CH", Modifier.weight(.8f), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("CH %", Modifier.weight(.9f), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("LTP", Modifier.weight(.8f), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                if (leaders.isEmpty()) EmptyMarket(
                    loading = loading,
                    onRetry = { scope.launch { refresh() } },
                    unavailableTitle = "Live quotes are unavailable",
                    unavailableMessage = "Index data is available; no priced stocks were returned. Try again later.",
                )
                leaders.forEach { stock ->
                    val color = if (stock.change >= 0) Mint else Red
                    Row(Modifier.fillMaxWidth().clickable { onStock(stock) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stock.symbol, Modifier.weight(1.4f), color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(if (stock.pointChange > 0) money(stock.pointChange) else "${money(stock.pointChange)}", Modifier.weight(.8f), color = color, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("${String.format(Locale.US, "%.2f", stock.change)}%", Modifier.weight(.9f), color = color, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text(money(stock.price), Modifier.weight(.8f), color = White, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Panel).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth().clip(CircleShape).background(Color(0xFF414143)).padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Turnover", "Volume", "Transactions").forEachIndexed { i, title ->
                        Box(Modifier.weight(1f).clip(CircleShape).background(if (activityTab == i) Color(0xFF0879CE) else Color.Transparent).clickable { activityTab = i }.padding(vertical = 8.dp), Alignment.Center) { Text(title, color = White, fontSize = 13.sp, maxLines = 1) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text("SYMBOL", Modifier.weight(1.2f), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(if (activityTab == 0) "TURNOVER" else if (activityTab == 1) "VOLUME" else "TRADES", Modifier.weight(1f), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("LTP", Modifier.weight(.9f), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                val active = when (activityTab) {
                    0 -> stocks.filter { it.turnover > 0 }.sortedByDescending { it.turnover }.take(5)
                    1 -> stocks.filter { it.tradedVolume > 0 }.sortedByDescending { it.tradedVolume }.take(5)
                    else -> stocks.filter { it.transactionCount > 0 }.sortedByDescending { it.transactionCount }.take(5)
                }
                if (activityTab == 2 && active.isEmpty()) {
                    Text("Transaction counts are not included in the available API data.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                }
                active.forEach { stock ->
                    val color = if (stock.change >= 0) Mint else Red
                    Row(Modifier.fillMaxWidth().clickable { onStock(stock) }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stock.symbol, Modifier.weight(1.2f), color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        val activityValue = when (activityTab) {
                            0 -> turnoverLabel(stock.turnover)
                            1 -> NumberFormat.getNumberInstance(Locale.US).format(stock.tradedVolume)
                            else -> stock.transactionCount.takeIf { it > 0 }?.toString() ?: "—"
                        }
                        Text(activityValue, Modifier.weight(1f), color = White, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("${money(stock.price)} (${String.format(Locale.US, "%.2f", stock.change)}%)", Modifier.weight(.9f), color = color, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1)
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Panel).padding(16.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).background(Color(0xFF414143)).padding(5.dp)) {
                    Text("MARKET SUMMARY", Modifier.clip(CircleShape).background(Color(0xFF0879CE)).padding(horizontal = 18.dp, vertical = 8.dp), color = White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(14.dp))
                val totalTurnover = stocks.sumOf { it.turnover }
                val totalVolume = stocks.sumOf { it.tradedVolume }
                val totalTransactions = stocks.sumOf { it.transactionCount }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryTile("Total Turnover Rs", turnoverLabel(totalTurnover), Icons.Default.CurrencyRupee, Modifier.weight(1f))
                    SummaryTile("Total Traded Shares", NumberFormat.getNumberInstance(Locale.US).format(totalVolume), Icons.Default.ShowChart, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryTile("Total Transactions", totalTransactions.takeIf { it > 0 }?.let { NumberFormat.getNumberInstance(Locale.US).format(it) } ?: "—", Icons.Default.Assessment, Modifier.weight(1f))
                    SummaryTile("Total Scrips Traded", stocks.count { it.tradedVolume > 0 }.toString(), Icons.Default.Schedule, Modifier.weight(1f))
                }
                IconButton(onClick = { scope.launch { refresh() } }, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Default.Refresh, "Refresh", tint = Blue) }
            }
        }
    }
    PullRefreshIndicator(
        refreshing = loading,
        state = pullRefreshState,
        modifier = Modifier.align(Alignment.TopCenter),
        contentColor = Mint,
        backgroundColor = Panel,
    )
    }
}

@Composable
private fun SummaryTile(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Panel2).padding(12.dp)) {
        Icon(icon, null, tint = Blue, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value.ifBlank { "—" }, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun turnoverLabel(value: Double): String = when {
    value >= 10_000_000 -> String.format(Locale.US, "%.2f Cr", value / 10_000_000)
    value >= 100_000 -> String.format(Locale.US, "%.2f L", value / 100_000)
    value > 0 -> NumberFormat.getNumberInstance(Locale.US).format(value)
    else -> "—"
}

@Composable
private fun IndexPill(value: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 6.dp, vertical = 4.dp)) {
        Text(value, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun BreadthTile(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(7.dp)).background(color).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(count.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IndexAreaChart(values: List<Float>, trendColor: Color = Mint) {
    if (values.size < 2) {
        Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
            Text("Intraday index history is not available yet", color = Muted, fontSize = 11.sp)
        }
        return
    }
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val left = 34.dp.toPx()
        val right = 4.dp.toPx()
        val top = 6.dp.toPx()
        val bottom = 8.dp.toPx()
        val chartWidth = size.width - left - right
        val chartHeight = size.height - top - bottom
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: min + 1f
        val range = (max - min).takeIf { it > 0f } ?: 1f

        for (line in 0..4) {
            val y = top + chartHeight * line / 4f
                drawLine(Color(0xFF35393D), androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Offset(size.width - right, y), strokeWidth = 1.dp.toPx())
        }

        val points = values.mapIndexed { i, value ->
            androidx.compose.ui.geometry.Offset(
                x = left + chartWidth * i / (values.size - 1).toFloat(),
                y = top + chartHeight * (1f - (value - min) / range),
            )
        }
        val area = Path().apply {
            moveTo(points.first().x, top + chartHeight)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, top + chartHeight)
            close()
        }
        drawPath(area, trendColor.copy(alpha = .23f))
        val line = Path().apply {
            points.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
        }
        drawPath(line, trendColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        listOf(0, 2, 4).forEach { tick ->
            val label = NumberFormat.getNumberInstance(Locale.US).format(max - range * tick / 4f)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                0f,
                top + chartHeight * tick / 4f + 3.dp.toPx(),
            android.graphics.Paint().apply { color = android.graphics.Color.rgb(164, 168, 173); textSize = 8.sp.toPx() },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
private fun ExploreScreen(search: String, onSearch: (String) -> Unit, stocks: List<Stock>, loading: Boolean, onStock: (Stock) -> Unit, refresh: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val pullRefreshState = rememberPullRefreshState(refreshing = loading, onRefresh = { scope.launch { refresh() } })
    Box(Modifier.fillMaxSize().background(Ink).pullRefresh(pullRefreshState)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            item {
                Text("Explore", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Find companies listed on NEPSE", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp, bottom = 16.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                    BasicTextField(search, onSearch, modifier = Modifier.weight(1f), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 14.sp), decorationBox = { inner -> if (search.isEmpty()) Text("Search symbol or company", color = Muted, fontSize = 14.sp); inner() })
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text("Companies", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text(if (stocks.isNotEmpty()) "${stocks.size} stocks" else "", color = Muted, fontSize = 11.sp); IconButton(onClick = { scope.launch { refresh() } }) { Icon(Icons.Default.Refresh, null, tint = Mint) } }
            }
            if (stocks.isEmpty()) {
                item { EmptyMarket(loading) { scope.launch { refresh() } } }
            } else {
                items(stocks.filter { it.symbol.contains(search, true) || it.name.contains(search, true) }, key = { it.symbol }) { StockRow(it, onStock) }
            }
        }
        PullRefreshIndicator(
            refreshing = loading,
            modifier = Modifier.align(Alignment.TopCenter),
            state = pullRefreshState,
            contentColor = Mint,
            backgroundColor = Panel,
        )
    }
}

@Composable
private fun PortfolioScreen(
    portfolios: List<Portfolio>,
    selectedPortfolioId: String?,
    onSelectPortfolio: (String) -> Unit,
    onBack: () -> Unit,
    onAddPortfolio: () -> Unit,
    onAddStock: () -> Unit,
    onStock: (Stock) -> Unit,
    onTrade: (String, Holding) -> Unit,
) {
    val selectedPortfolio = portfolios.firstOrNull { it.id == selectedPortfolioId }
    if (selectedPortfolio != null) {
        PortfolioDetailScreen(selectedPortfolio, onBack, onAddStock, onStock, onTrade)
        return
    }
    val holdings = portfolios.flatMap { it.holdings }
    val invested = holdings.sumOf { it.quantity * it.average }
    val value = holdings.sumOf { it.quantity * it.stock.price }
    val pnl = if (holdings.any { it.stock.price > 0 }) value - invested else 0.0
    val returnPercent = if (invested > 0 && holdings.any { it.stock.price > 0 }) pnl / invested * 100 else 0.0
    val today = holdings.sumOf { it.quantity * it.stock.pointChange }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Portfolios", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("Manage your investments separately", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                IconButton(onClick = onAddPortfolio) { Icon(Icons.Default.Add, "Add portfolio", tint = Mint) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (pnl >= 0) Color(0xFF204A2D) else Color(0xFF552B2D)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ALL PORTFOLIOS SUMMARY", color = White.copy(alpha = .85f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${if (returnPercent >= 0) "+" else ""}${String.format(Locale.US, "%.2f", returnPercent)}%", color = if (returnPercent >= 0) Mint else Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(15.dp))
                Text("CURRENT VALUE", color = White.copy(alpha = .7f), fontSize = 10.sp, letterSpacing = .8.sp)
                Text("Rs ${money(value)}", color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp, bottom = 15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortfolioMetric("INVESTED", "Rs ${money(invested)}", Modifier.weight(1f))
                    PortfolioMetric("TOTAL P&L", if (holdings.any { it.stock.price > 0 }) "${if (pnl >= 0) "+" else "−"}Rs ${money(kotlin.math.abs(pnl))}" else "—", Modifier.weight(1f), if (pnl >= 0) Mint else Red)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortfolioMetric("TODAY'S P&L", if (holdings.any { it.stock.price > 0 }) "${if (today >= 0) "+" else "−"}Rs ${money(kotlin.math.abs(today))}" else "—", Modifier.weight(1f), if (today >= 0) Mint else Red)
                    PortfolioMetric("PORTFOLIOS", "${portfolios.size}", Modifier.weight(1f))
                }
                HorizontalDivider(color = White.copy(alpha = .25f), modifier = Modifier.padding(vertical = 13.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Total holdings", color = White.copy(alpha = .75f), fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${holdings.size} stocks · Rs ${money(invested)} invested", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Panel).padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PieChart, null, tint = Muted, modifier = Modifier.size(17.dp))
                Text("  TOTAL  |  ${portfolios.size} ${if (portfolios.size == 1) "Portfolio" else "Portfolios"}", color = White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onAddPortfolio, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Default.Add, null, tint = Mint, modifier = Modifier.size(18.dp))
                    Text(" ADD PORTFOLIO", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (portfolios.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = Mint, modifier = Modifier.size(29.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Create your first portfolio", color = White, fontWeight = FontWeight.SemiBold)
                    Text("Keep different investment strategies and holdings separate.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = onAddPortfolio) { Text("＋ Add portfolio", color = Mint) }
                }
            }
        } else {
            items(portfolios, key = { it.id }) { portfolio ->
                PortfolioListCard(portfolio, onClick = { onSelectPortfolio(portfolio.id) })
            }
        }
    }
}

@Composable
private fun AddStockScreen(
    portfolio: Portfolio,
    stocks: List<Stock>,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: suspend () -> Unit,
    onAddStock: (Stock, Int, Double) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var pendingStock by remember { mutableStateOf<Stock?>(null) }
    val matches = stocks.filter { it.symbol.contains(search, true) || it.name.contains(search, true) }

    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = White) }
            Column {
                Text("Add stock", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("To ${portfolio.name}", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { scope.launch { onRefresh() } }) { Icon(Icons.Default.Refresh, "Refresh stocks", tint = Mint) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(Panel).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 14.sp),
                decorationBox = { inner ->
                    if (search.isEmpty()) Text("Search symbol or company", color = Muted, fontSize = 14.sp)
                    inner()
                },
            )
        }
        if (stocks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyMarket(loading) { scope.launch { onRefresh() } }
            }
        } else if (matches.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No stocks match ‘$search’", color = Muted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(matches, key = { it.symbol }) { stock ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).clickable { pendingStock = stock }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(stock.symbol)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(stock.symbol, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(stock.name.ifBlank { "NEPSE listed company" }, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (stock.price > 0) "Rs ${money(stock.price)}" else "Price unavailable", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (portfolio.holdings.any { it.stock.symbol == stock.symbol }) Text("Already held · add more", color = Mint, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }

    pendingStock?.let { stock ->
        var quantity by remember(stock.symbol) { mutableStateOf("") }
        var price by remember(stock.symbol) { mutableStateOf(stock.price.takeIf { it > 0 }?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { pendingStock = null },
            containerColor = Panel,
            title = { Text("Add ${stock.symbol}", color = White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stock.name.ifBlank { stock.symbol }, color = Muted, fontSize = 12.sp)
                    OutlinedTextField(quantity, { quantity = it }, label = { Text("Shares") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(price, { price = it }, label = { Text("Buy price (NPR/share)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val count = quantity.toIntOrNull() ?: 0
                    val buyPrice = price.toDoubleOrNull() ?: 0.0
                    if (count > 0 && buyPrice > 0) onAddStock(stock, count, buyPrice)
                }) { Text("Add to portfolio", color = Mint) }
            },
            dismissButton = { TextButton(onClick = { pendingStock = null }) { Text("Cancel", color = Muted) } },
        )
    }
}

@Composable
private fun PortfolioListCard(portfolio: Portfolio, onClick: () -> Unit) {
    val invested = portfolio.holdings.sumOf { it.quantity * it.average }
    val value = portfolio.holdings.sumOf { it.quantity * it.stock.price }
    val gain = value - invested
    val gainColor = if (gain >= 0) Mint else Red
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().background(if (gain >= 0) Color(0xFF204A2D) else Color(0xFF552B2D)).padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(portfolio.name, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("View stocks  ›", color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PortfolioMetric("CURRENT VALUE", "Rs ${money(value)}", Modifier.weight(1f))
            PortfolioMetric("INVESTED", "Rs ${money(invested)}", Modifier.weight(1f))
            PortfolioMetric("${if (gain >= 0) "GAIN" else "LOSS"}", "${if (gain >= 0) "+" else "−"}Rs ${money(kotlin.math.abs(gain))}", Modifier.weight(1f), gainColor)
        }
        Text("${portfolio.holdings.size} stocks", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 15.dp, bottom = 14.dp))
    }
}

@Composable
private fun PortfolioDetailScreen(portfolio: Portfolio, onBack: () -> Unit, onAddStock: () -> Unit, onStock: (Stock) -> Unit, onTrade: (String, Holding) -> Unit) {
    val holdings = portfolio.holdings
    val invested = holdings.sumOf { it.quantity * it.average }
    val value = holdings.sumOf { it.quantity * it.stock.price }
    val pnl = value - invested
    val today = holdings.sumOf { it.quantity * it.stock.pointChange }
    val hasPrice = holdings.any { it.stock.price > 0 }
    val returnPercent = if (invested > 0 && hasPrice) pnl / invested * 100 else 0.0
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back to portfolios", tint = White) }
                Column(Modifier.weight(1f)) {
                    Text(portfolio.name, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${holdings.size} stocks", color = Muted, fontSize = 12.sp)
                }
                IconButton(onClick = onAddStock) { Icon(Icons.Default.Add, "Add stock", tint = Mint) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (pnl >= 0) Color(0xFF204A2D) else Color(0xFF552B2D)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${portfolio.name.uppercase(Locale.getDefault())} SUMMARY", color = White.copy(alpha = .85f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${if (returnPercent >= 0) "+" else ""}${String.format(Locale.US, "%.2f", returnPercent)}%", color = if (returnPercent >= 0) Mint else Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(14.dp))
                Text("CURRENT VALUE", color = White.copy(alpha = .7f), fontSize = 10.sp)
                Text("Rs ${money(value)}", color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp, bottom = 14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortfolioMetric("INVESTED", "Rs ${money(invested)}", Modifier.weight(1f))
                    PortfolioMetric("TOTAL P&L", if (hasPrice) "${if (pnl >= 0) "+" else "−"}Rs ${money(kotlin.math.abs(pnl))}" else "—", Modifier.weight(1f), if (pnl >= 0) Mint else Red)
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortfolioMetric("TODAY'S P&L", if (hasPrice) "${if (today >= 0) "+" else "−"}Rs ${money(kotlin.math.abs(today))}" else "—", Modifier.weight(1f), if (today >= 0) Mint else Red)
                    PortfolioMetric("STOCKS", "${holdings.size}", Modifier.weight(1f))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Panel).padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("HOLDINGS  |  ${holdings.size} stocks", color = White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onAddStock, contentPadding = PaddingValues(horizontal = 5.dp)) { Icon(Icons.Default.Add, null, tint = Mint); Text(" ADD STOCK", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }
        }
        if (holdings.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No stocks in ${portfolio.name} yet", color = White, fontWeight = FontWeight.SemiBold)
                    Text("Add stocks to track this portfolio's performance.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = onAddStock) { Text("Add stocks  →", color = Mint) }
                }
            }
        } else items(holdings, key = { it.stock.symbol }) { holding -> PortfolioHoldingCard(holding, onStock, onLongPress = { onTrade(portfolio.id, holding) }) }
    }
}

@Composable
private fun PortfolioMetric(label: String, value: String, modifier: Modifier = Modifier, color: Color = White) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .13f)).padding(10.dp)) {
        Text(label, color = White.copy(alpha = .7f), fontSize = 9.sp, letterSpacing = .4.sp)
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PortfolioHoldingCard(holding: Holding, onStock: (Stock) -> Unit, onLongPress: () -> Unit) {
    val stock = holding.stock
    val cost = holding.quantity * holding.average
    val current = holding.quantity * stock.price
    val gain = current - cost
    val hasPrice = stock.price > 0
    val gainColor = if (gain >= 0) Mint else Red
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Panel).combinedClickable(onClick = { onStock(stock) }, onLongClick = onLongPress)) {
        Row(Modifier.fillMaxWidth().background(if (stock.change >= 0) Color(0xFF28533B) else Color(0xFF663638)).padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(stock.symbol)
            Text(stock.symbol, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("LTP  ", color = White.copy(alpha = .75f), fontSize = 10.sp)
            Text(if (hasPrice) money(stock.price) else "—", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(9.dp))
            Text("${if (stock.change > 0) "+" else ""}${String.format(Locale.US, "%.2f", stock.change)}%", color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HoldingMetric("Current units", holding.quantity.toString(), Modifier.weight(1f))
                HoldingMetric("Avg. buy price", "Rs ${money(holding.average)}", Modifier.weight(1f))
                HoldingMetric("Investment", "Rs ${money(cost)}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HoldingMetric("Current value", if (hasPrice) "Rs ${money(current)}" else "—", Modifier.weight(1f))
                HoldingMetric("Total gain", if (hasPrice) "${if (gain >= 0) "+" else "−"}${money(kotlin.math.abs(gain))}" else "—", Modifier.weight(1f), gainColor)
                val daily = holding.quantity * stock.pointChange
                HoldingMetric("Today's P&L", if (hasPrice) "${if (daily >= 0) "+" else "−"}${money(kotlin.math.abs(daily))}" else "—", Modifier.weight(1f), if (daily >= 0) Mint else Red)
            }
            HorizontalDivider(color = White.copy(alpha = .12f), modifier = Modifier.padding(vertical = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cost basis", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text("Rs ${money(cost)}", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text("Unrealized P&L", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(if (hasPrice) "${if (gain >= 0) "+" else "−"}Rs ${money(kotlin.math.abs(gain))}" else "Price unavailable", color = if (hasPrice) gainColor else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HoldingMetric(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = White) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier, color: Color = White) { Column(modifier) { Text(label, color = Muted, fontSize = 9.sp, letterSpacing = .8.sp); Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp)) } }

@Composable
private fun StockRow(stock: Stock, onStock: (Stock) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onStock(stock) }.padding(vertical = 11.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(stock.symbol)
        Column(Modifier.weight(1f).padding(start = 11.dp)) { Text(stock.symbol, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(stock.name.ifBlank { "NEPSE listed company" }, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Column(horizontalAlignment = Alignment.End) { Text(if (stock.price > 0) "NPR ${money(stock.price)}" else "—", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); if (stock.price > 0) Text("${if (stock.change >= 0) "+" else ""}${String.format(Locale.US, "%.2f", stock.change)}%", color = if (stock.change >= 0) Mint else Red, fontSize = 11.sp) }
    }
}

@Composable
private fun Avatar(symbol: String) { Box(Modifier.size(39.dp).clip(RoundedCornerShape(13.dp)).background(Panel2), Alignment.Center) { Text(symbol.take(1), color = Mint, fontSize = 15.sp, fontWeight = FontWeight.Bold) } }

@Composable
private fun EmptyMarket(
    loading: Boolean,
    unavailableTitle: String = "Market data is unavailable",
    unavailableMessage: String = "Check your connection or try again later.",
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (loading) CircularProgressIndicator(color = Mint, modifier = Modifier.size(25.dp), strokeWidth = 2.dp) else Icon(Icons.Default.CloudOff, null, tint = Muted, modifier = Modifier.size(25.dp))
        Spacer(Modifier.height(10.dp)); Text(if (loading) "Loading market data…" else unavailableTitle, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(if (loading) "Fetching market data" else unavailableMessage, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        if (!loading) TextButton(onClick = onRetry) { Text("Try again", color = Mint) }
    }
}

@Composable
private fun Sparkline() {
    Canvas(Modifier.width(90.dp).height(33.dp)) {
        val points = listOf(.8f, .68f, .75f, .47f, .54f, .38f, .49f, .24f, .35f, .2f)
        val p = Path()
        points.forEachIndexed { i, y -> val x = size.width * i / (points.size - 1); val yy = size.height * y; if (i == 0) p.moveTo(x, yy) else p.lineTo(x, yy) }
        drawPath(p, Mint, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private data class Market(val index: String, val change: String, val percentChange: String, val stocks: List<Stock>, val history: List<Float>)

private object NepseApi {
    private const val MARKET_URL = "https://nepse-api-gamma.vercel.app/api/market"

    suspend fun fetchStockDetails(symbol: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL("https://nepse-api-gamma.vercel.app/api/stocks/${java.net.URLEncoder.encode(symbol, "UTF-8")}").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("Stock details unavailable")
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }

    suspend fun fetch(): Market = withContext(Dispatchers.IO) {
        val connection = (URL(MARKET_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Market API returned HTTP ${connection.responseCode}")
            }
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            parseMarketResponse(json)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseMarketResponse(json: String): Market {
        val response = JSONObject(json)
        val indices = response.optJSONArray("nepseIndex") ?: JSONArray()
        val nepseIndex = (0 until indices.length())
            .map { indices.optJSONObject(it) }
            .filterNotNull()
            .firstOrNull { it.optInt("id", -1) == 58 || it.optString("index").equals("NEPSE Index", true) }

        val liveMarket = response.optJSONArray("liveMarket") ?: JSONArray()
        val marketIsLive = liveMarket.length() > 0
        val index = nepseIndex?.let {
            (if (marketIsLive) it.number("currentValue") ?: it.number("close")
            else it.number("close") ?: it.number("currentValue"))?.let(::fmt)
        } ?: "—"
        val indexChange = nepseIndex?.let { it.number("change")?.let(::formatDelta) }.orEmpty()
        val indexPercent = nepseIndex?.let { it.number("perChange")?.let(::formatPercent) }.orEmpty()

        val quoteRows = if (marketIsLive) liveMarket else response.optJSONArray("previousMarket") ?: JSONArray()
        val stocks = if (quoteRows.length() > 0) {
            (0 until quoteRows.length()).mapNotNull { i ->
                val quote = quoteRows.optJSONObject(i) ?: return@mapNotNull null
                val symbol = quote.optString("symbol").trim()
                if (symbol.isBlank()) return@mapNotNull null
                val price = quote.number("lastTradedPrice")
                    ?: quote.number("closePrice")
                    ?: quote.number("lastUpdatedPrice")
                    ?: 0.0
                val previousClose = quote.number("previousClose")
                    ?: quote.number("previousDayClosePrice")
                    ?: 0.0
                val pointChange = price - previousClose
                val percentageChange = quote.number("percentageChange")
                    ?: if (previousClose > 0) pointChange / previousClose * 100 else 0.0
                Stock(
                    symbol = symbol,
                    name = quote.optString("securityName"),
                    price = price,
                    change = percentageChange,
                    pointChange = pointChange,
                    tradedVolume = quote.number("totalTradeQuantity") ?: quote.number("lastTradedVolume") ?: quote.number("totalTradedQuantity") ?: 0.0,
                    turnover = quote.number("totalTradeValue") ?: quote.number("totalTradedValue") ?: 0.0,
                    transactionCount = quote.number("totalTrades")?.toInt() ?: quote.number("totalTradeCount")?.toInt() ?: 0,
                )
            }
        } else {
            val securities = response.optJSONArray("securities") ?: JSONArray()
            (0 until securities.length()).mapNotNull { i ->
                val security = securities.optJSONObject(i) ?: return@mapNotNull null
                val symbol = security.optString("symbol").trim()
                if (symbol.isBlank()) return@mapNotNull null
                Stock(symbol = symbol, name = security.optString("securityName"))
            }
        }.distinctBy { it.symbol }

        val rawHistory = response.optJSONArray("nepseIndexHistory") ?: JSONArray()
        val history = (0 until rawHistory.length()).mapNotNull { i ->
            rawHistory.optJSONArray(i)?.optDouble(1)?.toFloat()?.takeIf(Float::isFinite)
        }

        if (index == "—" && stocks.isEmpty()) {
            throw IllegalStateException("Market API response contained no usable data")
        }
        return Market(index, indexChange, indexPercent, stocks, history)
    }

    private fun JSONObject.number(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf(Double::isFinite)

    private fun formatPercent(value: Double) =
        (if (value > 0) "+" else "") + String.format(Locale.US, "%.2f%%", value)

    private fun formatDelta(value: Double) =
        (if (value > 0) "+" else "") + String.format(Locale.US, "%.2f", value)

    private fun fmt(value: Double) = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }.format(value)
}

private fun money(value: Double): String = NumberFormat.getNumberInstance(Locale.US).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(value)
private fun loadPortfolios(prefs: android.content.SharedPreferences): List<Portfolio> = try {
    val saved = prefs.getString("portfolios", null)
    if (saved != null) {
        val array = JSONArray(saved)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val holdingsArray = obj.optJSONArray("holdings") ?: JSONArray()
            val holdings = (0 until holdingsArray.length()).mapNotNull { j -> holdingsArray.optJSONObject(j)?.toHolding() }
            Portfolio(obj.optString("id", "portfolio-$i"), obj.optString("name", "Portfolio ${i + 1}"), holdings)
        }
    } else {
        // Move existing single-portfolio data forward without losing saved holdings.
        val legacy = JSONArray(prefs.getString("holdings", "[]"))
        val holdings = (0 until legacy.length()).mapNotNull { i -> legacy.optJSONObject(i)?.toHolding() }
        if (holdings.isEmpty()) emptyList() else listOf(Portfolio("legacy", "My Portfolio", holdings)).also { savePortfolios(prefs, it) }
    }
} catch (_: Exception) { emptyList() }

private fun JSONObject.toHolding(): Holding? = try {
    val symbol = getString("symbol")
    Holding(Stock(symbol, optString("name"), optDouble("price")), getInt("quantity"), getDouble("average"))
} catch (_: Exception) { null }

private fun savePortfolios(prefs: android.content.SharedPreferences, portfolios: List<Portfolio>) {
    val array = JSONArray()
    portfolios.forEach { portfolio ->
        val holdings = JSONArray()
        portfolio.holdings.forEach { holding ->
            holdings.put(JSONObject().put("symbol", holding.stock.symbol).put("name", holding.stock.name).put("price", holding.stock.price).put("quantity", holding.quantity).put("average", holding.average))
        }
        array.put(JSONObject().put("id", portfolio.id).put("name", portfolio.name).put("holdings", holdings))
    }
    prefs.edit().putString("portfolios", array.toString()).apply()
}
