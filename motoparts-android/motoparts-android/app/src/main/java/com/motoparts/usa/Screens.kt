package com.motoparts.usa

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage

fun money(usd: Double?, bdt: Double?, cur: String): String =
    if (cur == "BDT") (bdt?.let { "BDT %,.2f".format(it) } ?: "BDT price unavailable")
    else (usd?.let { "USD %,.2f".format(it) } ?: "Price unavailable")

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    val tabs = listOf(
        Triple("home", "Home", Icons.Default.Home),
        Triple("cart", "Cart", Icons.Default.ShoppingCart),
        Triple("orders", "Orders", Icons.Default.List),
        Triple("account", "Account", Icons.Default.Person)
    )
    val showBar = tabs.any { it.first == route }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { (r, label, icon) ->
                    NavigationBarItem(
                        selected = route == r,
                        onClick = { nav.navigate(r) { popUpTo("home"); launchSingleTop = true } },
                        icon = { Icon(icon, label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(vm, nav) }
            composable("brand/{id}") { BrandScreen(vm, nav, it.arguments!!.getString("id")!!.toInt()) }
            composable("product/{id}") { ProductScreen(vm, nav, it.arguments!!.getString("id")!!) }
            composable("cart") { CartScreen(vm, nav) }
            composable("checkout") { CheckoutScreen(vm, nav) }
            composable("orders") { OrdersScreen(vm, nav) }
            composable("order/{id}") { OrderScreen(vm, it.arguments!!.getString("id")!!) }
            composable("account") { AccountScreen(vm, nav) }
            composable("login") { LoginScreen(vm, nav) }
        }
    }
}

@Composable
fun ProductCard(p: ProductSummary, cur: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(10.dp)) {
            AsyncImage(
                model = p.image_url, contentDescription = p.name, contentScale = ContentScale.Crop,
                modifier = Modifier.size(92.dp).clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(p.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(money(p.price_usd, p.price_bdt, cur), color = Teal, fontWeight = FontWeight.Bold)
                if (p.price_is_estimated) Text("Estimated price", style = MaterialTheme.typography.bodySmall)
                Text(
                    "${p.condition.replaceFirstChar { it.uppercase() }} - ${p.seller_name}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    if (p.in_stock) "In stock (${p.stock})" else "Out of stock",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun CurrencyToggle(vm: AppViewModel) {
    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("USD", "BDT").forEach { c ->
            FilterChip(selected = vm.currency == c, onClick = { vm.setCurrency(c) }, label = { Text(c) })
        }
    }
}

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavHostController) {
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadHome() }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text("MotoParts USA", color = Teal, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    label = { Text("Search parts") }, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.search(q) }) { Text("Search") }
            }
            Spacer(Modifier.height(8.dp))
            CurrencyToggle(vm)
        }
        if (vm.brands.isNotEmpty()) item {
            Text("Motorcycle brands", Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.brands) { b -> AssistChip(onClick = { nav.navigate("brand/${b.id}") }, label = { Text(b.name) }) }
            }
        }
        if (vm.categories.isNotEmpty()) item {
            Text("Categories", Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.categories) { c -> AssistChip(onClick = { vm.search(null, c.id) }, label = { Text(c.name) }) }
            }
        }
        item { Text("Products", Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold) }
        if (vm.loading) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
        else if (vm.products.isEmpty()) item {
            Text("No products available yet.", Modifier.padding(12.dp))
        }
        items(vm.products) { p -> ProductCard(p, vm.currency) { nav.navigate("product/${p.id}") } }
    }
}

@Composable
fun BrandScreen(vm: AppViewModel, nav: NavHostController, brandId: Int) {
    var year by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<BikeModel?>(null) }
    LaunchedEffect(brandId) { vm.loadBrand(brandId) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TextButton(onClick = { nav.popBackStack() }) { Text("< Back") }
            Text("Select your motorcycle", Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = year, onValueChange = { year = it.filter(Char::isDigit).take(4) }, singleLine = true,
                label = { Text("Model year (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()
            )
        }
        if (vm.models.isEmpty() && !vm.loading) item { Text("No models added for this brand yet.", Modifier.padding(12.dp)) }
        items(vm.models) { m ->
            val range = if (m.year_to == null) "${m.year_from}+" else "${m.year_from}-${m.year_to}"
            TextButton(onClick = { selected = m; vm.productsForModel(m.id, year.toIntOrNull()) }) {
                Text(if (selected?.id == m.id) "> ${m.name} ($range)" else "${m.name} ($range)")
            }
        }
        if (selected != null) {
            item { Text("Parts for ${selected!!.name}", Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold) }
            if (vm.products.isEmpty() && !vm.loading) item { Text("No compatible parts listed yet.", Modifier.padding(12.dp)) }
            items(vm.products) { p -> ProductCard(p, vm.currency) { nav.navigate("product/${p.id}") } }
        }
    }
}

@Composable
fun ZoomDialog(url: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier.fillMaxWidth().height(420.dp).background(Color.Black).pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset += pan
                }
            }
        ) {
            AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y
                )
            )
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) { Text("Close") }
        }
    }
}

@Composable
fun ProductScreen(vm: AppViewModel, nav: NavHostController, id: String) {
    LaunchedEffect(id) { vm.loadProduct(id) }
    val d = vm.detail
    if (d == null || d.id != id) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var zoomUrl by remember { mutableStateOf<String?>(null) }
    zoomUrl?.let { ZoomDialog(it) { zoomUrl = null } }
    val summary = ProductSummary(
        d.id, d.name, d.sku, d.part_brand, d.condition, d.price_usd, d.price_bdt, d.price_is_estimated,
        d.stock, d.seller_name, d.category, d.images.firstOrNull()?.url, d.rating, d.stock > 0
    )
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TextButton(onClick = { nav.popBackStack() }) { Text("< Back") }
            if (d.images.isNotEmpty()) {
                val pager = rememberPagerState { d.images.size }
                HorizontalPager(pager, Modifier.fillMaxWidth().height(280.dp)) { i ->
                    AsyncImage(
                        model = d.images[i].url, contentDescription = d.name, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clickable { zoomUrl = d.images[i].url }
                    )
                }
                Text("Tap image to zoom (${d.images.size} photo${if (d.images.size > 1) "s, swipe" else ""})",
                    Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.padding(12.dp)) {
                Text(d.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(money(d.price_usd, d.price_bdt, vm.currency), color = Teal,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (d.price_is_estimated) Text("Estimated price, not a verified market price", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                CurrencyToggle(vm)
                Spacer(Modifier.height(8.dp))
                Text("SKU: ${d.sku}")
                d.part_brand?.let { Text("Brand: $it") }
                Text("Type: ${d.condition.replaceFirstChar { c -> c.uppercase() }}")
                Text("Category: ${d.category}")
                Text("Seller: ${d.seller_name}")
                Text(if (d.stock > 0) "In stock: ${d.stock}" else "Out of stock")
                Text(
                    if (d.rating != null) "Rating: ${d.rating} (${d.review_count ?: 0} reviews)" else "No reviews yet"
                )
                d.shipping_info?.let { Text("Shipping: $it") }
                d.description?.let { Spacer(Modifier.height(8.dp)); Text(it) }
                Spacer(Modifier.height(8.dp))
                Text("Compatibility", fontWeight = FontWeight.SemiBold)
                if (d.compatibility.isEmpty()) Text("No compatibility information provided by the seller.")
                d.compatibility.forEach {
                    val yrs = if (it.year_to == null) "${it.year_from}+" else "${it.year_from}-${it.year_to}"
                    Text("${it.brand} ${it.model} ($yrs)")
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = d.stock > 0, onClick = { vm.addToCart(summary); vm.message = "Added to cart" }) {
                        Text("Add to cart")
                    }
                    OutlinedButton(enabled = d.stock > 0, onClick = { vm.addToCart(summary); nav.navigate("cart") }) {
                        Text("Buy now")
                    }
                }
            }
        }
    }
}

@Composable
fun CartScreen(vm: AppViewModel, nav: NavHostController) {
    val cur = vm.currency
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text("Cart", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(12.dp))
            CurrencyToggle(vm)
        }
        if (vm.cart.isEmpty()) item { Text("Your cart is empty.", Modifier.padding(12.dp)) }
        items(vm.cart) { line ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(line.product.name, fontWeight = FontWeight.SemiBold)
                    Text(money(line.product.price_usd, line.product.price_bdt, cur), color = Teal)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { vm.setQty(line.product.id, line.qty - 1) }) { Text("-") }
                        Text("  ${line.qty}  ")
                        OutlinedButton(onClick = { vm.setQty(line.product.id, line.qty + 1) }) { Text("+") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { vm.setQty(line.product.id, 0) }) { Text("Remove") }
                    }
                }
            }
        }
        if (vm.cart.isNotEmpty()) item {
            val missing = cur == "BDT" && vm.cart.any { it.product.price_bdt == null }
            val sub = vm.cart.sumOf { (if (cur == "BDT") it.product.price_bdt else it.product.price_usd)?.times(it.qty) ?: 0.0 }
            Column(Modifier.padding(12.dp)) {
                Text("Subtotal: $cur %,.2f".format(sub), fontWeight = FontWeight.Bold)
                if (missing) Text("Some items have no BDT price; the subtotal excludes them.", style = MaterialTheme.typography.bodySmall)
                Text("Shipping is calculated by the store for your address when you place the order.",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { nav.navigate(if (vm.loggedIn) "checkout" else "login") }) { Text("Checkout") }
            }
        }
    }
}

@Composable
fun CheckoutScreen(vm: AppViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var line1 by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var postal by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadAddresses() }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TextButton(onClick = { nav.popBackStack() }) { Text("< Back") }
            Text("Delivery address", Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
        }
        items(vm.addresses) { a ->
            Row(Modifier.fillMaxWidth().clickable { selected = a.id }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == a.id, onClick = { selected = a.id })
                Text("${a.full_name}, ${a.line1}, ${a.city} ${a.country}")
            }
        }
        item {
            TextButton(onClick = { adding = !adding }) { Text(if (adding) "Cancel new address" else "Add new address") }
            if (adding) Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(line1, { line1 = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(city, { city = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(postal, { postal = it }, label = { Text("Postal code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(country, { country = it.take(2).uppercase() }, label = { Text("Country code (US, GB, CA, BD)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    vm.addAddress(AddressReq(name, phone, line1, city, postal.ifBlank { null }, country))
                    adding = false
                }) { Text("Save address") }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.padding(12.dp)) {
                Text("${vm.cart.sumOf { it.qty }} item(s) in cart, paying in ${vm.currency}")
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = selected != null && vm.cart.isNotEmpty() && !vm.loading,
                    onClick = {
                        vm.placeOrder(selected!!) { link ->
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                        }
                        nav.navigate("orders") { popUpTo("home") }
                    }
                ) { Text("Place order and pay") }
            }
        }
    }
}

@Composable
fun OrdersScreen(vm: AppViewModel, nav: NavHostController) {
    LaunchedEffect(vm.userName) { if (vm.loggedIn) vm.loadOrders() }
    Column(Modifier.fillMaxSize()) {
        Text("My orders", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(12.dp))
        if (!vm.loggedIn) {
            Button(onClick = { nav.navigate("login") }, modifier = Modifier.padding(12.dp)) { Text("Log in to see orders") }
            return
        }
        if (vm.orders.isEmpty() && !vm.loading) Text("No orders yet.", Modifier.padding(12.dp))
        LazyColumn {
            items(vm.orders) { o ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable { nav.navigate("order/${o.id}") }) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Order ${o.id.take(8)}", fontWeight = FontWeight.SemiBold)
                        Text("Status: ${o.status.replace('_', ' ')}", color = Teal)
                        Text("Total: USD ${o.total_usd} (${o.currency} order)")
                    }
                }
            }
        }
    }
}

@Composable
fun OrderScreen(vm: AppViewModel, id: String) {
    val ctx = LocalContext.current
    LaunchedEffect(id) { vm.loadOrder(id) }
    val o = vm.order
    if (o == null || o.id != id) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        item {
            Text("Order ${o.id.take(8)}", style = MaterialTheme.typography.titleLarge)
            Text("Status: ${o.status.replace('_', ' ')}", color = Teal, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        items(o.items) { it ->
            Text("${it.quantity} x ${it.product_name} (SKU ${it.sku}) - USD ${it.unit_price_usd} - ${it.status}")
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Subtotal: USD ${o.subtotal_usd}")
            Text("Shipping: USD ${o.shipping_usd}")
            Text("Total: USD ${o.total_usd}", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (o.status == "pending") Button(onClick = {
                    vm.payOrder(o.id) { link -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                }) { Text("Pay now") }
                if (o.status == "pending" || o.status == "confirmed")
                    OutlinedButton(onClick = { vm.cancelOrder(o.id) }) { Text("Cancel order") }
            }
        }
    }
}

@Composable
fun AccountScreen(vm: AppViewModel, nav: NavHostController) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Account", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        if (vm.loggedIn) {
            Text("Signed in as ${vm.userName ?: ""}")
            Spacer(Modifier.height(8.dp))
            Text("Currency")
            CurrencyToggle(vm)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { vm.logout() }) { Text("Log out") }
        } else {
            Text("Log in to place and track orders.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = { nav.navigate("login") }) { Text("Log in or register") }
        }
    }
}

@Composable
fun LoginScreen(vm: AppViewModel, nav: NavHostController) {
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { nav.popBackStack() }) { Text("< Back") }
        Text(if (register) "Create account" else "Log in", style = MaterialTheme.typography.headlineSmall)
        if (register) OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pw, { pw = it }, label = { Text("Password (8+ characters)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button(enabled = !vm.loading, onClick = {
            val done = { nav.popBackStack(); Unit }
            if (register) vm.register(name, email, pw, done) else vm.login(email, pw, done)
        }) { Text(if (register) "Register" else "Log in") }
        TextButton(onClick = { register = !register }) {
            Text(if (register) "Already have an account? Log in" else "New here? Create an account")
        }
    }
}
